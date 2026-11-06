package com.intercom.app.hfp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges the app's audio pipeline to a Bluetooth HF device (like the BluArmor C30)
 * through the phone's built-in HFP Audio Gateway.
 *
 * The phone is already an HFP AG — this class gives us explicit control over the SCO
 * channel and keeps the connection alive. Normally Android opens SCO only for phone
 * calls; [startVoiceRecognition] is the public API trick that opens it without one.
 *
 * Audio tradeoff: SCO is 8kHz CVSD or 16kHz mSBC — mono either way. While SCO is
 * open, A2DP (stereo music) is suspended on that device. This is the fundamental
 * limitation of Classic BT; there is no workaround.
 */
class HfpBridge(private val context: Context) {

    companion object {
        private const val TAG = "HfpBridge"
    }

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val audioManager = context.getSystemService(AudioManager::class.java)!!

    private var proxy: BluetoothHeadset? = null
    @Volatile private var proxyReady = false

    private val _state = MutableStateFlow(BridgeState.Idle)
    val state: StateFlow<BridgeState> = _state.asStateFlow()

    private val _scoConnected = MutableStateFlow(false)
    val scoConnected: StateFlow<Boolean> = _scoConnected.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private var scoReceiver: BroadcastReceiver? = null
    private var hfpReceiver: BroadcastReceiver? = null

    fun init() {
        adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
        registerReceivers()
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        @SuppressLint("MissingPermission")
        override fun onServiceConnected(profile: Int, p: BluetoothProfile) {
            proxy = p as BluetoothHeadset
            proxyReady = true
            Log.i(TAG, "HFP AG proxy ready")

            val connected = p.connectedDevices
            if (connected.isNotEmpty()) {
                val dev = connected[0]
                _connectedDevice.value = dev
                _state.value = BridgeState.Connected
                Log.i(TAG, "already connected to ${dev.address}")
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            proxy = null
            proxyReady = false
            _state.value = BridgeState.Idle
            Log.w(TAG, "HFP AG proxy disconnected")
        }
    }

    private fun registerReceivers() {
        scoReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val scoState = intent.getIntExtra(
                    AudioManager.EXTRA_SCO_AUDIO_STATE,
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED,
                )
                val connected = scoState == AudioManager.SCO_AUDIO_STATE_CONNECTED
                _scoConnected.value = connected
                Log.i(TAG, "SCO ${if (connected) "connected" else "disconnected"} (state=$scoState)")
                if (connected) _state.value = BridgeState.ScoOpen
            }
        }
        context.registerReceiver(
            scoReceiver,
            IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),
        )

        hfpReceiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(ctx: Context, intent: Intent) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val profileState = intent.getIntExtra(
                    BluetoothProfile.EXTRA_STATE,
                    BluetoothProfile.STATE_DISCONNECTED,
                )
                Log.i(TAG, "HFP state=${profileStateName(profileState)} device=${device?.address}")
                when (profileState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        _connectedDevice.value = device
                        _state.value = BridgeState.Connected
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (device?.address == _connectedDevice.value?.address) {
                            _connectedDevice.value = null
                            _scoConnected.value = false
                            _state.value = BridgeState.Disconnected
                        }
                    }
                }
            }
        }
        context.registerReceiver(
            hfpReceiver,
            IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED),
        )
    }

    /** List HF devices the phone's AG is connected to right now. */
    @SuppressLint("MissingPermission")
    fun getConnectedDevices(): List<BluetoothDevice> {
        return proxy?.connectedDevices ?: emptyList()
    }

    /**
     * Open SCO audio to the connected HF device using the voice-recognition trick.
     *
     * This tells the remote device that the phone wants to capture voice for an
     * assistant, which opens the SCO channel without needing an actual phone call.
     * The channel stays open until [stopSco] or the remote disconnects.
     */
    @SuppressLint("MissingPermission")
    fun startSco(device: BluetoothDevice? = null): Boolean {
        val dev = device ?: _connectedDevice.value ?: getConnectedDevices().firstOrNull()
        if (dev == null) {
            Log.w(TAG, "no HF device connected")
            return false
        }

        val p = proxy
        if (p == null) {
            Log.w(TAG, "proxy not ready")
            return false
        }

        _state.value = BridgeState.OpeningSco

        val started = p.startVoiceRecognition(dev)
        Log.i(TAG, "startVoiceRecognition(${dev.address}): $started")

        if (!started) {
            // Fallback: use AudioManager directly
            Log.i(TAG, "falling back to AudioManager.startBluetoothSco()")
        }

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true

        return true
    }

    /** Close the SCO channel and restore normal audio routing. */
    @SuppressLint("MissingPermission")
    fun stopSco() {
        proxy?.connectedDevices?.forEach { proxy?.stopVoiceRecognition(it) }
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
        audioManager.mode = AudioManager.MODE_NORMAL
        _scoConnected.value = false
        if (_connectedDevice.value != null) _state.value = BridgeState.Connected
    }

    /**
     * Connect to an HF device via the system's HFP AG.
     *
     * Uses reflection because [BluetoothHeadset.connect] is hidden. Falls back to
     * suggesting manual pairing if reflection fails.
     */
    @SuppressLint("MissingPermission")
    fun connectDevice(device: BluetoothDevice): Boolean {
        val p = proxy ?: return false
        return try {
            val method = p.javaClass.getMethod("connect", BluetoothDevice::class.java)
            val result = method.invoke(p, device) as Boolean
            Log.i(TAG, "connect(${device.address}) via reflection: $result")
            if (result) _state.value = BridgeState.Connecting
            result
        } catch (e: Exception) {
            Log.w(TAG, "connect() reflection failed: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice(device: BluetoothDevice? = null): Boolean {
        val dev = device ?: _connectedDevice.value ?: return false
        val p = proxy ?: return false
        stopSco()
        return try {
            val method = p.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
            method.invoke(p, dev) as Boolean
        } catch (e: Exception) {
            Log.w(TAG, "disconnect() reflection failed: ${e.message}")
            false
        }
    }

    fun release() {
        stopSco()
        scoReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        hfpReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        proxy?.let { adapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
        proxy = null
    }

    private fun profileStateName(s: Int) = when (s) {
        BluetoothProfile.STATE_DISCONNECTED -> "DISCONNECTED"
        BluetoothProfile.STATE_CONNECTING -> "CONNECTING"
        BluetoothProfile.STATE_CONNECTED -> "CONNECTED"
        BluetoothProfile.STATE_DISCONNECTING -> "DISCONNECTING"
        else -> "UNKNOWN($s)"
    }
}

enum class BridgeState {
    Idle,
    Connecting,
    Connected,
    OpeningSco,
    ScoOpen,
    Disconnected,
}
