package com.arwora.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.utils.PairingStatus
import com.arwora.utils.SoundEffectManager
import com.arwora.utils.WirelessPairingManager
import kotlinx.coroutines.delay

@Composable
fun WirelessPairingWizard(
    modifier: Modifier = Modifier,
    onPairingSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val pairingStatus by WirelessPairingManager.status.collectAsState()
    val discoveredPort by WirelessPairingManager.discoveredPort.collectAsState()
    val capturedCode by WirelessPairingManager.lastCapturedCode.collectAsState()

    var hasNotificationAccess by remember {
        mutableStateOf(WirelessPairingManager.isNotificationAccessGranted(context))
    }

    var manualCodeInput by remember { mutableStateOf("") }
    var manualPortInput by remember { mutableStateOf("") }
    var isManualExpanded by remember { mutableStateOf(false) }

    // Synchronize inputs when captured automatically
    LaunchedEffect(capturedCode) {
        if (!capturedCode.isNullOrBlank()) {
            manualCodeInput = capturedCode!!
        }
    }
    LaunchedEffect(discoveredPort) {
        if (discoveredPort != null) {
            manualPortInput = discoveredPort.toString()
        }
    }

    // Periodically re-verify notification access when resuming
    LaunchedEffect(Unit) {
        while (true) {
            hasNotificationAccess = WirelessPairingManager.isNotificationAccessGranted(context)
            delay(2000L)
        }
    }

    // Notify caller when successfully paired
    LaunchedEffect(pairingStatus) {
        if (pairingStatus is PairingStatus.Success) {
            onPairingSuccess()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Notification Access Missing Warning Card
        if (!hasNotificationAccess) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notification Access Required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "To automatically intercept the 6-digit wireless debugging code from the system notification, please grant Notification Listener permission.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            SoundEffectManager.playClick()
                            WirelessPairingManager.openNotificationAccessSettings(context)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Grant Notification Access", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Real-time Status Indicator Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pairing Status",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Status Badge with pulsing indicator
                    val statusText: String
                    val statusColor: Color
                    when (pairingStatus) {
                        is PairingStatus.Idle -> {
                            statusText = "Idle"
                            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        is PairingStatus.Listening -> {
                            statusText = "Listening..."
                            statusColor = Color(0xFF2196F3)
                        }
                        is PairingStatus.CodeCaptured -> {
                            statusText = "Code Captured"
                            statusColor = Color(0xFFFF9800)
                        }
                        is PairingStatus.PairingInProgress -> {
                            statusText = "Pairing..."
                            statusColor = Color(0xFFFF9800)
                        }
                        is PairingStatus.Success -> {
                            statusText = "Paired"
                            statusColor = Color(0xFF10B981)
                        }
                        is PairingStatus.Failed -> {
                            statusText = "Failed"
                            statusColor = MaterialTheme.colorScheme.error
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }

                // Status Description
                val statusDescription = when (val s = pairingStatus) {
                    is PairingStatus.Idle -> "Ready to pair. Follow the 3 steps below to pair your device locally."
                    is PairingStatus.Listening -> "Listening for pairing notification... Now tap 'Pair device with pairing code' in Developer Options."
                    is PairingStatus.CodeCaptured -> "Captured pairing code: ${s.code}${if (s.port != null) " (Port: ${s.port})" else ""}"
                    is PairingStatus.PairingInProgress -> "Connecting to ADB daemon on ${s.host}:${s.port} and performing pairing handshake..."
                    is PairingStatus.Success -> s.message
                    is PairingStatus.Failed -> s.error
                }

                Text(
                    text = statusDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (pairingStatus is PairingStatus.Listening) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Step-by-Step UI Wizard Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Automated Pairing Wizard",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Step 1
                WizardStepItem(
                    stepNumber = "1",
                    title = "Enable Developer Options",
                    description = "Ensure Developer Options and USB Debugging are active on your device.",
                    actionLabel = "Open Dev Options",
                    onAction = {
                        SoundEffectManager.playClick()
                        try {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open Developer Options", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Step 2
                WizardStepItem(
                    stepNumber = "2",
                    title = "Turn On Wireless Debugging",
                    description = "Connect to Wi-Fi and toggle 'Wireless Debugging' to ON inside Developer Options.",
                    actionLabel = "Wireless Settings",
                    onAction = {
                        SoundEffectManager.playClick()
                        try {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open Developer Options", Toast.LENGTH_SHORT).show()
                        }
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Step 3
                WizardStepItem(
                    stepNumber = "3",
                    title = "Tap 'Pair device with pairing code'",
                    description = "Start listening here, then tap the pairing code option in Settings. Arwora will capture the 6 digits automatically.",
                    actionLabel = if (pairingStatus is PairingStatus.Listening) "Stop Listening" else "Start Auto-Pairing",
                    isPrimaryAction = true,
                    onAction = {
                        SoundEffectManager.playClick()
                        if (pairingStatus is PairingStatus.Listening) {
                            WirelessPairingManager.stopListening()
                        } else {
                            WirelessPairingManager.startListening(context)
                        }
                    }
                )
            }
        }

        // Manual Fallback Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Manual Pairing Fallback",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Enter code and port manually if notification was missed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { isManualExpanded = !isManualExpanded }) {
                        Icon(
                            imageVector = if (isManualExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isManualExpanded) "Collapse" else "Expand"
                        )
                    }
                }

                AnimatedVisibility(visible = isManualExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = manualCodeInput,
                                onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) manualCodeInput = it },
                                label = { Text("6-Digit Code") },
                                placeholder = { Text("123456") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = manualPortInput,
                                onValueChange = { if (it.length <= 5 && it.all { c -> c.isDigit() }) manualPortInput = it },
                                label = { Text("Port") },
                                placeholder = { Text("37491") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Button(
                            onClick = {
                                SoundEffectManager.playClick()
                                val code = manualCodeInput.trim()
                                val port = manualPortInput.toIntOrNull()
                                if (code.length == 6 && port != null && port in 1024..65535) {
                                    WirelessPairingManager.executePairing(context, "127.0.0.1", port, code)
                                } else {
                                    Toast.makeText(context, "Please enter a valid 6-digit code and port (1024-65535)", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = manualCodeInput.length == 6 && (manualPortInput.toIntOrNull() ?: 0) in 1024..65535,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Pair Manually", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardStepItem(
    stepNumber: String,
    title: String,
    description: String,
    actionLabel: String,
    isPrimaryAction: Boolean = false,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Step Number Badge
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isPrimaryAction) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isPrimaryAction) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))

            if (isPrimaryAction) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
