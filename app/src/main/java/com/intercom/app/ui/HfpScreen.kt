package com.intercom.app.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intercom.app.hfp.AgState
import com.intercom.app.hfp.BridgeState
import com.intercom.app.hfp.HfpAgServer
import com.intercom.app.hfp.HfpBridge

/**
 * Debug screen for testing HFP Audio Gateway connectivity with intercom devices.
 *
 * Shows two panels:
 * 1. System HFP bridge — uses the phone's built-in AG to connect to a paired HF device
 * 2. Custom HFP AG server — accepts connections from devices in "universal intercom" mode
 *
 * The AT command log lets us see exactly what the BluArmor C30 sends and where the
 * handshake breaks, so we can fix it.
 */
@SuppressLint("MissingPermission")
@Composable
fun HfpScreen(
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val bridge = remember { HfpBridge(ctx).also { it.init() } }
    val agServer = remember { HfpAgServer(ctx) }

    DisposableEffect(Unit) {
        onDispose {
            bridge.release()
            agServer.stop()
        }
    }

    val bridgeState by bridge.state.collectAsState()
    val scoConnected by bridge.scoConnected.collectAsState()
    val bridgeDevice by bridge.connectedDevice.collectAsState()

    val agState by agServer.state.collectAsState()
    val agDevice by agServer.connectedDevice.collectAsState()
    val atLog by agServer.atLog.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                "HFP Intercom Bridge",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---- Section 1: System HFP Bridge ----
            item {
                SectionCard("System HFP Bridge") {
                    StatusRow("State", bridgeState.name)
                    StatusRow("SCO Audio", if (scoConnected) "Connected" else "Disconnected")
                    bridgeDevice?.let { dev ->
                        StatusRow("Device", "${dev.name ?: "unknown"} (${dev.address})")
                    }

                    Spacer(Modifier.height(8.dp))

                    val connectedDevices = remember(bridgeState) { bridge.getConnectedDevices() }
                    if (connectedDevices.isNotEmpty()) {
                        Text(
                            "Connected HF devices:",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        connectedDevices.forEach { dev ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${dev.name ?: dev.address}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                )
                                Row {
                                    SmallButton(
                                        if (scoConnected) "Stop SCO" else "Start SCO",
                                        onClick = {
                                            if (scoConnected) bridge.stopSco()
                                            else bridge.startSco(dev)
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            "No HF device connected via system HFP.\nPair the C30 in phone mode first.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // ---- Section 2: Custom HFP AG Server ----
            item {
                SectionCard("Custom HFP AG Server") {
                    StatusRow("State", agState.name)
                    agDevice?.let { dev ->
                        StatusRow("Device", "${dev.name ?: "unknown"} (${dev.address})")
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton(
                            text = if (agState == AgState.Idle) "Start Listening" else "Stop",
                            onClick = {
                                if (agState == AgState.Idle) agServer.start()
                                else agServer.stop()
                            },
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        when (agState) {
                            AgState.Idle -> "Start the server, then put the C30 in universal intercom mode and pair."
                            AgState.Listening -> "Waiting for an HF device to connect..."
                            AgState.SLCSetup -> "AT command exchange in progress..."
                            AgState.SLCReady -> "Service Level Connection established!"
                            AgState.AudioOpen -> "SCO audio channel open — voice active."
                        },
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                    )
                }
            }

            // ---- Section 3: AT Command Log ----
            item {
                SectionCard("AT Command Log") {
                    if (atLog.isEmpty()) {
                        Text(
                            "No AT commands yet. Start the AG server and connect a device.",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // AT log entries as individual items for efficient scrolling
            items(atLog.reversed()) { entry ->
                val isIncoming = entry.contains("<<<")
                val isOutgoing = entry.contains(">>>")
                val isSpecial = entry.contains("===") || entry.contains("???")
                val color = when {
                    isSpecial -> Color(0xFFFFD600)
                    isIncoming -> Color(0xFF4FC3F7)
                    isOutgoing -> Color(0xFF81C784)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                }
                Text(
                    entry,
                    color = color,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
                )
            }

            // ---- Section 4: Bonded devices (for quick reference) ----
            item {
                val bm = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val bonded = runCatching { bm.adapter.bondedDevices.toList() }.getOrDefault(emptyList())
                SectionCard("Bonded Devices") {
                    if (bonded.isEmpty()) {
                        Text(
                            "No bonded devices",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                        )
                    }
                    bonded.forEach { dev ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    dev.name ?: dev.address,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                )
                                Text(
                                    dev.address,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                )
                            }
                            SmallButton("Connect AG") {
                                bridge.connectDevice(dev)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), fontSize = 12.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
    ) {
        Text(text, fontSize = 11.sp)
    }
}
