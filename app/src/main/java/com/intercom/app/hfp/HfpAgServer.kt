package com.intercom.app.hfp

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.media.AudioManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.util.UUID

/**
 * A custom HFP Audio Gateway that accepts connections from intercom devices in
 * "universal intercom" mode.
 *
 * Accepts connections over **both** Bluetooth RFCOMM (production) and TCP (testing via
 * `adb forward`). The AT command handler is identical for both; only the transport
 * carrying it differs. A TCP connection skips SCO audio setup since there is no
 * Bluetooth link underneath.
 *
 * **Every AT command is logged** so we can reverse-engineer vendor-specific extensions.
 */
class HfpAgServer(private val context: Context) {

    companion object {
        private const val TAG = "HfpAgServer"

        val HFP_AG_UUID: UUID = UUID.fromString("00001112-0000-1000-8000-00805f9b34fb")

        private const val SDP_NAME = "RiderIntercom AG"
        /** TCP port for testing from the Mac via `adb forward tcp:9222 tcp:9222`. */
        const val TCP_TEST_PORT = 9222

        // AG feature bits (HFP 1.8 §4.2.1)
        private const val AG_FEATURES = (1 shl 2) or (1 shl 3) or (1 shl 9)  // 524

        private const val CODEC_CVSD = 1
        private const val CODEC_MSBC = 2
        private const val KEEPALIVE_MS = 4000L
    }

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val audioManager = context.getSystemService(AudioManager::class.java)!!
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var btServerSocket: BluetoothServerSocket? = null
    private var tcpServerSocket: ServerSocket? = null
    private var output: OutputStream? = null
    private var btAcceptJob: Job? = null
    private var tcpAcceptJob: Job? = null
    private var readJob: Job? = null
    private var keepaliveJob: Job? = null

    @Volatile private var running = false
    @Volatile private var slcEstablished = false
    @Volatile private var selectedCodec = CODEC_CVSD
    @Volatile private var peerFeatures = 0
    @Volatile private var isTcpSession = false

    private val _state = MutableStateFlow(AgState.Idle)
    val state: StateFlow<AgState> = _state.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice.asStateFlow()

    private val _atLog = MutableStateFlow<List<String>>(emptyList())
    val atLog: StateFlow<List<String>> = _atLog.asStateFlow()

    /** Name shown in the UI for the connected peer. */
    private val _peerName = MutableStateFlow<String?>(null)
    val peerName: StateFlow<String?> = _peerName.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        _state.value = AgState.Listening
        Log.i(TAG, "starting HFP AG server (BT + TCP:$TCP_TEST_PORT)")
        logAt("=== SERVER STARTING ===")

        // Bluetooth RFCOMM listener
        btAcceptJob = scope.launch {
            try {
                btServerSocket = adapter?.listenUsingRfcommWithServiceRecord(SDP_NAME, HFP_AG_UUID)
                Log.i(TAG, "RFCOMM listening")
                logAt(">>> RFCOMM listening")
                while (isActive && running) {
                    val socket = btServerSocket?.accept() ?: break
                    val name = runCatching { socket.remoteDevice.name }.getOrDefault("unknown")
                    handleNewSession(
                        input = socket.inputStream,
                        output = socket.outputStream,
                        peerLabel = "$name (${socket.remoteDevice.address})",
                        btDevice = socket.remoteDevice,
                        tcp = false,
                    )
                }
            } catch (e: IOException) {
                if (running) Log.e(TAG, "RFCOMM accept failed: ${e.message}")
            }
        }

        // TCP listener for Mac-side testing
        tcpAcceptJob = scope.launch {
            try {
                tcpServerSocket = ServerSocket(TCP_TEST_PORT)
                Log.i(TAG, "TCP listening on :$TCP_TEST_PORT")
                logAt(">>> TCP listening on :$TCP_TEST_PORT")
                while (isActive && running) {
                    val socket = tcpServerSocket?.accept() ?: break
                    socket.tcpNoDelay = true
                    val addr = socket.remoteSocketAddress.toString()
                    handleNewSession(
                        input = socket.getInputStream(),
                        output = socket.getOutputStream(),
                        peerLabel = "TCP $addr",
                        btDevice = null,
                        tcp = true,
                    )
                }
            } catch (e: IOException) {
                if (running) Log.e(TAG, "TCP accept failed: ${e.message}")
            }
        }
    }

    private fun handleNewSession(
        input: InputStream,
        output: OutputStream,
        peerLabel: String,
        btDevice: BluetoothDevice?,
        tcp: Boolean,
    ) {
        // Tear down any existing session
        keepaliveJob?.cancel()
        readJob?.cancel()
        this.output = null

        this.output = output
        this.isTcpSession = tcp
        _connectedDevice.value = btDevice
        _peerName.value = peerLabel
        _state.value = AgState.SLCSetup
        slcEstablished = false
        selectedCodec = CODEC_CVSD
        peerFeatures = 0

        Log.i(TAG, "HF connected: $peerLabel (${if (tcp) "TCP" else "BT"})")
        logAt(">>> CONNECTED: $peerLabel (${if (tcp) "TCP" else "BT"})")

        readJob = scope.launch { readLoop(input) }
    }

    private fun readLoop(input: InputStream) {
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
        try {
            while (running) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                logAt("<<< $line")
                Log.i(TAG, "HF->AG: $line")
                handleAtCommand(line.trim())
            }
        } catch (e: IOException) {
            if (running) Log.w(TAG, "read error: ${e.message}")
        } finally {
            onDisconnected()
        }
    }

    // ---------------------------------------------------------------- AT handler

    private fun handleAtCommand(cmd: String) {
        when {
            cmd.startsWith("AT+BRSF=") -> {
                peerFeatures = cmd.substringAfter("=").trim().toIntOrNull() ?: 0
                Log.i(TAG, "peer features: $peerFeatures (${decodeHfFeatures(peerFeatures)})")
                logAt("    features: ${decodeHfFeatures(peerFeatures)}")
                respond("+BRSF: $AG_FEATURES")
                respond("OK")
            }

            cmd.startsWith("AT+BAC=") -> {
                val codecs = cmd.substringAfter("=").split(",").mapNotNull { it.trim().toIntOrNull() }
                Log.i(TAG, "peer codecs: $codecs")
                selectedCodec = if (CODEC_MSBC in codecs) CODEC_MSBC else CODEC_CVSD
                respond("OK")
            }

            cmd == "AT+CIND=?" -> {
                respond("+CIND: " +
                    "(\"service\",(0,1))," +
                    "(\"call\",(0,1))," +
                    "(\"callsetup\",(0-3))," +
                    "(\"callheld\",(0-2))," +
                    "(\"signal\",(0-5))," +
                    "(\"roam\",(0,1))," +
                    "(\"battchg\",(0-5))")
                respond("OK")
            }

            cmd == "AT+CIND?" -> {
                respond("+CIND: 1,0,0,0,5,0,5")
                respond("OK")
            }

            cmd.startsWith("AT+CMER=") -> {
                respond("OK")
                if ((peerFeatures and (1 shl 4)) != 0 && selectedCodec == CODEC_MSBC) {
                    respond("+BCS: $selectedCodec")
                }
            }

            cmd.startsWith("AT+CHLD=?") -> {
                respond("+CHLD: (0,1,2,3)")
                respond("OK")

                slcEstablished = true
                _state.value = AgState.SLCReady
                Log.i(TAG, "SLC established! codec=$selectedCodec")
                logAt("=== SLC ESTABLISHED (codec=${if (selectedCodec == CODEC_MSBC) "mSBC" else "CVSD"}) ===")

                simulateActiveCall()
            }

            cmd.startsWith("AT+BCS=") -> {
                val codec = cmd.substringAfter("=").trim().toIntOrNull() ?: CODEC_CVSD
                selectedCodec = codec
                Log.i(TAG, "codec confirmed: ${if (codec == CODEC_MSBC) "mSBC" else "CVSD"}")
                respond("OK")
            }

            cmd.startsWith("AT+BIND=?") -> { respond("+BIND: (1,2)"); respond("OK") }
            cmd.startsWith("AT+BIND?") -> { respond("+BIND: 1,1"); respond("+BIND: 2,1"); respond("OK") }
            cmd.startsWith("AT+BIND=") -> respond("OK")

            cmd.startsWith("AT+VGS=") -> { Log.i(TAG, "speaker vol: ${cmd.substringAfter("=")}"); respond("OK") }
            cmd.startsWith("AT+VGM=") -> { Log.i(TAG, "mic vol: ${cmd.substringAfter("=")}"); respond("OK") }

            cmd == "ATA" -> { Log.i(TAG, "HF answered"); respond("OK"); if (!isTcpSession) openScoAudio() }
            cmd.startsWith("AT+CHUP") -> { Log.i(TAG, "HF hung up"); respond("OK"); _state.value = AgState.SLCReady }

            cmd.startsWith("AT+CLCC") -> {
                if (slcEstablished) respond("+CLCC: 1,0,0,0,0")
                respond("OK")
            }

            cmd == "AT+NREC=0" -> { Log.i(TAG, "HF disabled NREC"); respond("OK") }

            cmd.startsWith("AT+XAPL=") -> { respond("+XAPL=iPhone,7"); respond("OK") }
            cmd.startsWith("AT+IPHONEACCEV=") -> respond("OK")
            cmd.startsWith("AT+CLIP=") -> respond("OK")
            cmd.startsWith("AT+CCWA=") -> respond("OK")
            cmd.startsWith("AT+CMEE=") -> respond("OK")
            cmd.startsWith("AT+COPS") -> { respond("+COPS: 0,0,\"RiderIntercom\""); respond("OK") }
            cmd.startsWith("AT+CNUM") -> respond("OK")

            cmd.startsWith("AT") -> {
                Log.w(TAG, "UNKNOWN AT: $cmd")
                logAt("??? UNKNOWN: $cmd")
                respond("OK")
            }

            else -> {
                Log.w(TAG, "non-AT data: $cmd")
                logAt("??? non-AT: $cmd")
            }
        }
    }

    // ---------------------------------------------------------------- call simulation

    private fun simulateActiveCall() {
        respond("RING")
        respond("+CLIP: \"RiderIntercom\",129,,,,\"Rider\"")

        scope.launch {
            delay(500)
            respond("+CIEV: 2,1")   // call = active
            respond("+CIEV: 3,0")   // callsetup = idle
            Log.i(TAG, "simulated active call")
            logAt("=== CALL ACTIVE (simulated) ===")

            if (!isTcpSession) openScoAudio()
            startKeepalive()
        }
    }

    private fun openScoAudio() {
        _state.value = AgState.AudioOpen
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.startBluetoothSco()
        audioManager.isBluetoothScoOn = true
        Log.i(TAG, "SCO audio requested")
        logAt("=== SCO OPEN REQUESTED ===")
    }

    private fun startKeepalive() {
        keepaliveJob?.cancel()
        keepaliveJob = scope.launch {
            while (isActive && running && slcEstablished) {
                delay(KEEPALIVE_MS)
                respond("+CIEV: 5,5")
            }
        }
    }

    // ---------------------------------------------------------------- IO

    @Synchronized
    private fun respond(response: String) {
        try {
            val out = output ?: return
            out.write("\r\n$response\r\n".toByteArray(Charsets.UTF_8))
            out.flush()
            Log.d(TAG, "AG->HF: $response")
            logAt(">>> $response")
        } catch (e: IOException) {
            Log.w(TAG, "send failed: ${e.message}")
        }
    }

    private fun onDisconnected() {
        Log.i(TAG, "HF disconnected")
        logAt(">>> DISCONNECTED")
        keepaliveJob?.cancel()
        readJob?.cancel()
        output = null
        _connectedDevice.value = null
        _peerName.value = null
        slcEstablished = false

        if (!isTcpSession) {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }

        _state.value = if (running) AgState.Listening else AgState.Idle
    }

    fun stop() {
        running = false
        keepaliveJob?.cancel()
        readJob?.cancel()
        btAcceptJob?.cancel()
        tcpAcceptJob?.cancel()
        runCatching { tcpServerSocket?.close() }
        runCatching { btServerSocket?.close() }
        tcpServerSocket = null
        btServerSocket = null
        output = null
        _connectedDevice.value = null
        _peerName.value = null
        slcEstablished = false

        if (!isTcpSession) {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }

        _state.value = AgState.Idle
    }

    private fun logAt(line: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date())
        _atLog.value = (_atLog.value + "$ts $line").takeLast(200)
    }

    private fun decodeHfFeatures(f: Int): String {
        val parts = mutableListOf<String>()
        if (f and (1 shl 0) != 0) parts += "ECNR"
        if (f and (1 shl 1) != 0) parts += "3Way"
        if (f and (1 shl 2) != 0) parts += "CLI"
        if (f and (1 shl 3) != 0) parts += "VoiceRec"
        if (f and (1 shl 4) != 0) parts += "RemoteVol"
        if (f and (1 shl 5) != 0) parts += "EnhancedStatus"
        if (f and (1 shl 6) != 0) parts += "EnhancedCtl"
        if (f and (1 shl 7) != 0) parts += "CodecNeg"
        if (f and (1 shl 8) != 0) parts += "HFIndicators"
        if (f and (1 shl 9) != 0) parts += "eSCO_S4"
        return parts.joinToString(", ").ifEmpty { "none" }
    }
}

enum class AgState {
    Idle,
    Listening,
    SLCSetup,
    SLCReady,
    AudioOpen,
}
