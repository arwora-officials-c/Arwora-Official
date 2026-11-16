package com.arwora.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.service.WifiAutoPairMonitor
import com.arwora.utils.PairingStatus
import com.arwora.utils.SoundEffectManager
import com.arwora.utils.WirelessPairingManager
import com.arwora.utils.WirelessPairingMode

@Composable
fun WirelessPairingWizard(
    modifier: Modifier = Modifier,
    onPairingSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val selectedMode by WirelessPairingManager.selectedMode.collectAsStateWithLifecycle()
    val pairingStatus by WirelessPairingManager.status.collectAsStateWithLifecycle()
    val discoveredPort by WirelessPairingManager.discoveredPort.collectAsStateWithLifecycle()
    val capturedCode by WirelessPairingManager.lastCapturedCode.collectAsStateWithLifecycle()
    val hasNotificationAccess by WirelessPairingManager.notificationAccessGranted.collectAsStateWithLifecycle()
    val autoPairOnWifiEnabled by WirelessPairingManager.autoPairOnWifiEnabled.collectAsStateWithLifecycle()
    val savedHost by WirelessPairingManager.savedHost.collectAsStateWithLifecycle()
    val savedConnectPort by WirelessPairingManager.savedConnectPort.collectAsStateWithLifecycle()
    val isWifiConnected by WirelessPairingManager.isWifiConnected.collectAsStateWithLifecycle()
    val lastWifiEventSummary by WirelessPairingManager.lastWifiEventSummary.collectAsStateWithLifecycle()

    var manualHostInput by remember { mutableStateOf(savedHost) }
    var manualCodeInput by remember { mutableStateOf("") }
    var manualPortInput by remember { mutableStateOf("") }
    var wifiTargetHostInput by remember { mutableStateOf(savedHost) }
    var wifiTargetPortInput by remember { mutableStateOf(savedConnectPort?.toString() ?: "") }

    // Synchronize persisted preferences and connectivity state on mount
    LaunchedEffect(Unit) {
        WirelessPairingManager.init(context)
        WifiAutoPairMonitor.syncWithPreference(context)
    }

    LaunchedEffect(savedHost) {
        if (savedHost.isNotBlank()) {
            manualHostInput = savedHost
            wifiTargetHostInput = savedHost
        }
    }
    LaunchedEffect(savedConnectPort) {
        if (savedConnectPort != null) {
            wifiTargetPortInput = savedConnectPort.toString()
        }
    }
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

    // Re-evaluate notification access & Wi-Fi state cleanly on ON_RESUME (no polling loop)
    DisposableEffect(lifecycleOwner, context) {
        WirelessPairingManager.checkNotificationPermissionStatus(context)
        WifiAutoPairMonitor.updateCurrentWifiState(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                WirelessPairingManager.checkNotificationPermissionStatus(context)
                WifiAutoPairMonitor.updateCurrentWifiState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Notify caller when successfully paired
    LaunchedEffect(pairingStatus) {
        if (pairingStatus is PairingStatus.Success) {
            onPairingSuccess()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wireless_pairing_root"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Real-time Status Indicator Card with Google Play Linear Progress
        PairingStatusBannerCard(
            selectedMode = selectedMode,
            pairingStatus = pairingStatus,
            hasNotificationAccess = hasNotificationAccess,
            isWifiConnected = isWifiConnected
        )

        // 2. Three Pairing Mode Selector Tabs/Cards
        PairingModeSelectorCard(
            selectedMode = selectedMode,
            onSelectMode = { mode ->
                SoundEffectManager.playClick()
                WirelessPairingManager.selectPairingMode(context, mode)
            }
        )

        // 3. Active Mode Content Card
        AnimatedContent(
            targetState = selectedMode,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(150))
            },
            label = "pairing_mode_content"
        ) { mode ->
            when (mode) {
                WirelessPairingMode.NOTIFICATION_MANUAL -> {
                    ManualNotificationPairingCard(
                        hostInput = manualHostInput,
                        onHostChange = { manualHostInput = it },
                        codeInput = manualCodeInput,
                        onCodeChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) manualCodeInput = it },
                        portInput = manualPortInput,
                        onPortChange = { if (it.length <= 5 && it.all { c -> c.isDigit() }) manualPortInput = it },
                        isPairing = pairingStatus is PairingStatus.PairingInProgress,
                        onOpenDevOptions = { openDeveloperOptions(context) },
                        onPairClick = {
                            SoundEffectManager.playClick()
                            val port = manualPortInput.toIntOrNull()
                            if (WirelessPairingManager.isValidManualPairingInput(manualHostInput, port, manualCodeInput)) {
                                WirelessPairingManager.executePairing(
                                    context = context,
                                    host = manualHostInput.trim(),
                                    port = port!!,
                                    code = manualCodeInput.trim()
                                )
                            } else {
                                Toast.makeText(
                                    context,
                                    "Enter valid host, 6-digit code, and port (1024-65535)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }

                WirelessPairingMode.AUTOMATIC_WIZARD -> {
                    AutomaticWizardPairingCard(
                        pairingStatus = pairingStatus,
                        hasNotificationAccess = hasNotificationAccess,
                        discoveredPort = discoveredPort,
                        onGrantNotificationAccess = {
                            SoundEffectManager.playClick()
                            WirelessPairingManager.openNotificationAccessSettings(context)
                        },
                        onOpenDevOptions = { openDeveloperOptions(context) },
                        onToggleListening = {
                            SoundEffectManager.playClick()
                            if (pairingStatus is PairingStatus.Listening || pairingStatus is PairingStatus.PairingInProgress) {
                                WirelessPairingManager.cancelAutomaticPairing()
                            } else {
                                WirelessPairingManager.startAutomaticPairing(context)
                            }
                        },
                        onFallbackToManual = {
                            SoundEffectManager.playClick()
                            WirelessPairingManager.cancelAutomaticPairing()
                            WirelessPairingManager.selectPairingMode(context, WirelessPairingMode.NOTIFICATION_MANUAL)
                        }
                    )
                }

                WirelessPairingMode.AUTO_PAIR_ON_WIFI -> {
                    AutoPairOnWifiCard(
                        enabled = autoPairOnWifiEnabled,
                        isWifiConnected = isWifiConnected,
                        hostInput = wifiTargetHostInput,
                        onHostChange = { wifiTargetHostInput = it },
                        portInput = wifiTargetPortInput,
                        onPortChange = { if (it.length <= 5 && it.all { c -> c.isDigit() }) wifiTargetPortInput = it },
                        lastEventSummary = lastWifiEventSummary,
                        isBusy = pairingStatus is PairingStatus.PairingInProgress,
                        onToggleEnabled = { checked ->
                            SoundEffectManager.playClick()
                            WirelessPairingManager.setAutoPairOnWifiEnabled(context, checked)
                        },
                        onSaveAndTriggerNow = {
                            SoundEffectManager.playClick()
                            val parsedPort = wifiTargetPortInput.toIntOrNull()
                            if (wifiTargetPortInput.isNotBlank() && (parsedPort == null || parsedPort !in 1024..65535)) {
                                Toast.makeText(context, "Port must be between 1024 and 65535", Toast.LENGTH_SHORT).show()
                                return@AutoPairOnWifiCard
                            }
                            WirelessPairingManager.saveTargetInfo(context, wifiTargetHostInput, parsedPort)
                            if (WifiAutoPairMonitor.isWifiCurrentlyConnected(context)) {
                                WirelessPairingManager.onWifiNetworkConnected(context, forceManualTrigger = true)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Target saved. Auto-pair will trigger when Wi-Fi connects.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PairingStatusBannerCard(
    selectedMode: WirelessPairingMode,
    pairingStatus: PairingStatus,
    hasNotificationAccess: Boolean,
    isWifiConnected: Boolean
) {
    val isBusy = pairingStatus is PairingStatus.Listening || pairingStatus is PairingStatus.PairingInProgress
    val statusText: String
    val statusColor: Color
    when (pairingStatus) {
        is PairingStatus.Idle -> {
            statusText = "Ready"
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
        }
        is PairingStatus.Listening -> {
            statusText = "Scanning..."
            statusColor = Color(0xFF2196F3)
        }
        is PairingStatus.CodeCaptured -> {
            statusText = "Code Captured"
            statusColor = Color(0xFFF59E0B)
        }
        is PairingStatus.PairingInProgress -> {
            statusText = "Pairing..."
            statusColor = MaterialTheme.colorScheme.primary
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wireless ADB & Shizuku Pairing",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Mode: ${selectedMode.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                    modifier = Modifier.testTag("wireless_pairing_status_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }

            val statusDescription = when (val s = pairingStatus) {
                is PairingStatus.Idle -> selectedMode.subtitle
                is PairingStatus.Listening -> s.stageMessage
                is PairingStatus.CodeCaptured -> "Captured pairing code: ${s.code}${if (s.port != null) " (Port: ${s.port})" else ""}"
                is PairingStatus.PairingInProgress -> s.stageMessage
                is PairingStatus.Success -> s.message
                is PairingStatus.Failed -> s.error
            }

            Text(
                text = statusDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusMiniPill(
                    icon = if (hasNotificationAccess) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                    label = if (hasNotificationAccess) "Listener Ready" else "Manual / No Listener",
                    activeColor = if (hasNotificationAccess) Color(0xFF10B981) else Color(0xFFF59E0B)
                )
                StatusMiniPill(
                    icon = if (isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    label = if (isWifiConnected) "Wi-Fi Online" else "Wi-Fi Offline",
                    activeColor = if (isWifiConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isBusy,
                enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                val activeProgress = (pairingStatus as? PairingStatus.Listening)?.progress
                GooglePlayLinearProgressBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .testTag("wireless_pairing_linear_progress"),
                    progress = activeProgress,
                    height = 5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun StatusMiniPill(
    icon: ImageVector,
    label: String,
    activeColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = activeColor.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, activeColor.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = activeColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = activeColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PairingModeSelectorCard(
    selectedMode: WirelessPairingMode,
    onSelectMode: (WirelessPairingMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Select Pairing Mode",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WirelessPairingMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    val modeIcon = when (mode) {
                        WirelessPairingMode.NOTIFICATION_MANUAL -> Icons.Default.Pin
                        WirelessPairingMode.AUTOMATIC_WIZARD -> Icons.Default.AutoFixHigh
                        WirelessPairingMode.AUTO_PAIR_ON_WIFI -> Icons.Default.WifiTethering
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectMode(mode) }
                            .testTag("pairing_mode_tab_${mode.name.lowercase()}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = modeIcon,
                                contentDescription = mode.title,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = mode.shortLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualNotificationPairingCard(
    hostInput: String,
    onHostChange: (String) -> Unit,
    codeInput: String,
    onCodeChange: (String) -> Unit,
    portInput: String,
    onPortChange: (String) -> Unit,
    isPairing: Boolean,
    onOpenDevOptions: () -> Unit,
    onPairClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pairing_card_manual"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Pin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Mode 1: Notification Pairing Code",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "1. Enable Wireless Debugging in Developer Options and tap 'Pair device with pairing code'.\n2. Enter the 6-digit code & port from the system notification below and tap Pair.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = {
                    SoundEffectManager.playClick()
                    onOpenDevOptions()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("manual_open_dev_options_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.DeveloperMode, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Developer Options", fontWeight = FontWeight.SemiBold)
            }

            OutlinedTextField(
                value = hostInput,
                onValueChange = onHostChange,
                label = { Text("Device Host / IP") },
                placeholder = { Text("127.0.0.1") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_pairing_host_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = onCodeChange,
                    label = { Text("6-Digit Code") },
                    placeholder = { Text("123456") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("manual_pairing_code_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = portInput,
                    onValueChange = onPortChange,
                    label = { Text("Port") },
                    placeholder = { Text("37491") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("manual_pairing_port_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Button(
                onClick = onPairClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("wireless_pair_manually_button"),
                shape = RoundedCornerShape(12.dp),
                enabled = !isPairing &&
                    WirelessPairingManager.isValidManualPairingInput(hostInput, portInput.toIntOrNull(), codeInput),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isPairing) {
                    GooglePlayCompactLinearProgress(
                        width = 28.dp,
                        height = 4.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pairing...", fontWeight = FontWeight.Bold)
                } else {
                    Text("Pair", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AutomaticWizardPairingCard(
    pairingStatus: PairingStatus,
    hasNotificationAccess: Boolean,
    discoveredPort: Int?,
    onGrantNotificationAccess: () -> Unit,
    onOpenDevOptions: () -> Unit,
    onToggleListening: () -> Unit,
    onFallbackToManual: () -> Unit
) {
    val isListening = pairingStatus is PairingStatus.Listening
    val isPairing = pairingStatus is PairingStatus.PairingInProgress
    val isBusy = isListening || isPairing
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AnimatedVisibility(
            visible = !hasNotificationAccess,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wireless_notification_access_warning_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
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
                        text = "Grant Notification Listener access so Arwora can automatically intercept the 6-digit pairing code and complete the handshake.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onGrantNotificationAccess,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("wireless_grant_notification_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Grant Notification Access", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("pairing_card_automatic_wizard"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mode 2: Automatic Pairing Wizard",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (discoveredPort != null) {
                        StatusMiniPill(
                            icon = Icons.Default.Radar,
                            label = "Port $discoveredPort",
                            activeColor = Color(0xFF10B981)
                        )
                    }
                }

                WizardStepItem(
                    stepNumber = "1",
                    title = "Enable Developer Options",
                    description = "Ensure Developer Options and USB/Wireless Debugging are active.",
                    actionLabel = "Open Dev Options",
                    onAction = {
                        SoundEffectManager.playClick()
                        onOpenDevOptions()
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                WizardStepItem(
                    stepNumber = "2",
                    title = "One-Tap mDNS & Local Port Scan (10s Timeout)",
                    description = "Scans local ADB ports & mDNS broadcasts, captures your pairing notification, and connects securely.",
                    actionLabel = when {
                        isBusy -> "Cancel Auto-Pairing"
                        pairingStatus is PairingStatus.Failed -> "Retry Auto-Pairing"
                        else -> "Start Auto-Pairing"
                    },
                    isPrimaryAction = true,
                    onAction = onToggleListening
                )

                if (pairingStatus is PairingStatus.Failed && pairingStatus.canFallbackToManual) {
                    OutlinedButton(
                        onClick = onFallbackToManual,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auto_wizard_fallback_manual_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Switch to Manual Code Entry", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoPairOnWifiCard(
    enabled: Boolean,
    isWifiConnected: Boolean,
    hostInput: String,
    onHostChange: (String) -> Unit,
    portInput: String,
    onPortChange: (String) -> Unit,
    lastEventSummary: String?,
    isBusy: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSaveAndTriggerNow: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pairing_card_wifi_auto"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Mode 3: Auto-Pair on Any Wi-Fi",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automatically attempt pairing or ADB re-connect whenever any Wi-Fi connects.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                ArworaAnimatedSwitch(
                    checked = enabled,
                    onCheckedChange = onToggleEnabled,
                    modifier = Modifier.testTag("wifi_auto_pair_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            Text(
                text = "Cached Wireless Debugging Target",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hostInput,
                    onValueChange = onHostChange,
                    label = { Text("Target Host") },
                    placeholder = { Text("127.0.0.1") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.3f)
                        .testTag("wifi_auto_host_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = portInput,
                    onValueChange = onPortChange,
                    label = { Text("ADB Port (Opt.)") },
                    placeholder = { Text("Auto mDNS") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wifi_auto_port_input"),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (!lastEventSummary.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiFind,
                            contentDescription = null,
                            tint = if (isWifiConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = lastEventSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = onSaveAndTriggerNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("wifi_auto_save_trigger_button"),
                shape = RoundedCornerShape(12.dp),
                enabled = !isBusy
            ) {
                if (isBusy) {
                    GooglePlayCompactLinearProgress(
                        width = 28.dp,
                        height = 4.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting...", fontWeight = FontWeight.Bold)
                } else {
                    Text(
                        if (isWifiConnected) "Save Target & Re-Connect Now" else "Save Target Config",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun openDeveloperOptions(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
        Toast.makeText(context, "Could not open Developer Options", Toast.LENGTH_SHORT).show()
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("auto_wizard_primary_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
