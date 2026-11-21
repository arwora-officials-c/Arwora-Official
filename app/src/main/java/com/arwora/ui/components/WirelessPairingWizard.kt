package com.arwora.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.arwora.service.AdbPairingNotificationService
import com.arwora.service.AdbPairingNotifier
import com.arwora.service.WifiAutoPairMonitor
import com.arwora.utils.NativeAdbConnectionState
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.PairingStatus
import com.arwora.utils.SoundEffectManager
import com.arwora.utils.WirelessPairingManager
import com.arwora.utils.WirelessPairingMode
import kotlinx.coroutines.launch

@Composable
fun WirelessPairingWizard(
    modifier: Modifier = Modifier,
    onPairingSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val selectedMode by WirelessPairingManager.selectedMode.collectAsStateWithLifecycle()
    val pairingStatus by WirelessPairingManager.status.collectAsStateWithLifecycle()
    val discoveredPort by WirelessPairingManager.discoveredPort.collectAsStateWithLifecycle()
    val capturedCode by WirelessPairingManager.lastCapturedCode.collectAsStateWithLifecycle()
    val hasNotificationAccess by WirelessPairingManager.notificationAccessGranted.collectAsStateWithLifecycle()
    val autoPairOnWifiEnabled by WirelessPairingManager.autoPairOnWifiEnabled.collectAsStateWithLifecycle()
    val savedHost by WirelessPairingManager.savedHost.collectAsStateWithLifecycle()
    val isWifiConnected by WirelessPairingManager.isWifiConnected.collectAsStateWithLifecycle()
    val lastWifiEventSummary by WirelessPairingManager.lastWifiEventSummary.collectAsStateWithLifecycle()
    val isNativeAdbConnected by NativeAdbManager.isConnected.collectAsStateWithLifecycle()
    val isWirelessDebuggingEnabled by NativeAdbManager.isWirelessDebuggingEnabled.collectAsStateWithLifecycle()
    val adbConnectionState by NativeAdbManager.connectionState.collectAsStateWithLifecycle()
    val requiresDevOptionsGuidance by NativeAdbManager.requiresDevOptionsGuidance.collectAsStateWithLifecycle()
    val adbStatusMessage by NativeAdbManager.statusMessage.collectAsStateWithLifecycle()
    val activeWifiSsid by NativeAdbManager.activeWifiSsid.collectAsStateWithLifecycle()
    val isCurrentWifiTrusted by NativeAdbManager.isCurrentWifiTrusted.collectAsStateWithLifecycle()

    var manualHostInput by remember { mutableStateOf(savedHost) }
    var manualCodeInput by remember { mutableStateOf("") }
    var manualPortInput by remember { mutableStateOf("") }

    val launchNotificationPairingFlow = {
        AdbPairingNotificationService.start(context)
        Toast.makeText(
            context,
            "Notification Pairing active. Tap 'Pair device with pairing code' and reply in the notification shade.",
            Toast.LENGTH_LONG
        ).show()
        openDeveloperOptions(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchNotificationPairingFlow()
        } else {
            Toast.makeText(
                context,
                "Notification permission denied. Grant notifications to use Direct Reply or enter details manually below.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Synchronize persisted preferences and connectivity state on mount
    LaunchedEffect(Unit) {
        NativeAdbManager.init(context)
        WirelessPairingManager.init(context)
        WifiAutoPairMonitor.syncWithPreference(context)
    }

    LaunchedEffect(savedHost) {
        if (savedHost.isNotBlank()) {
            manualHostInput = savedHost
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
                        onStartNotificationPairing = {
                            SoundEffectManager.playClick()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !AdbPairingNotifier.hasNotificationPermission(context)
                            ) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                launchNotificationPairingFlow()
                            }
                        },
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
                        activeWifiSsid = activeWifiSsid,
                        isWifiTrusted = isCurrentWifiTrusted,
                        isAdbConnected = isNativeAdbConnected,
                        isWirelessDebuggingEnabled = isWirelessDebuggingEnabled,
                        lastEventSummary = lastWifiEventSummary ?: adbStatusMessage,
                        isBusy = pairingStatus is PairingStatus.PairingInProgress ||
                            adbConnectionState == NativeAdbConnectionState.CONNECTING,
                        showDevOptionsGuidance = !isNativeAdbConnected &&
                            !isWirelessDebuggingEnabled &&
                            (requiresDevOptionsGuidance || pairingStatus is PairingStatus.Failed),
                        onToggleEnabled = { checked ->
                            SoundEffectManager.playClick()
                            WirelessPairingManager.setAutoPairOnWifiEnabled(context, checked)
                        },
                        onConnectNow = {
                            SoundEffectManager.playClick()
                            coroutineScope.launch {
                                WirelessPairingManager.connectUnifiedWirelessAdb(context)
                            }
                        },
                        onOpenDevOptions = {
                            SoundEffectManager.playClick()
                            openDeveloperOptions(context)
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
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Wireless ADB Pairing",
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
                ExpressiveWavyLinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .testTag("wireless_pairing_linear_progress"),
                    progress = activeProgress,
                    height = 10.dp,
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
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
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
    onStartNotificationPairing: () -> Unit,
    onOpenDevOptions: () -> Unit,
    onPairClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pairing_card_manual"),
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                text = "1. Tap 'Start Notification Pairing' below to start mDNS port discovery and open Developer Options.\n2. Enable Wireless Debugging, tap 'Pair device with pairing code', and enter the 6-digit code directly inside the notification shade (or use manual fallback below).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onStartNotificationPairing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("manual_start_notification_pairing_button"),
                shape = ArworaDesignTokens.PillShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Notification Pairing", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    SoundEffectManager.playClick()
                    onOpenDevOptions()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("manual_open_dev_options_button"),
                shape = ArworaDesignTokens.PillShape
            ) {
                Icon(Icons.Default.DeveloperMode, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Developer Options", fontWeight = FontWeight.SemiBold)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 2.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
            )

            Text(
                text = "Manual Input Fallback",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )

            OutlinedTextField(
                value = hostInput,
                onValueChange = onHostChange,
                label = { Text("Device Host / IP", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                placeholder = { Text("127.0.0.1") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_pairing_host_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = onCodeChange,
                    label = {
                        Text(
                            text = "6-Digit Code",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    placeholder = { Text("123456") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1.5f)
                        .testTag("manual_pairing_code_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = portInput,
                    onValueChange = onPortChange,
                    label = {
                        Text(
                            text = "Port",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
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
                shape = ArworaDesignTokens.PillShape,
                enabled = !isPairing &&
                    WirelessPairingManager.isValidManualPairingInput(hostInput, portInput.toIntOrNull(), codeInput),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (isPairing) {
                    ExpressiveCompactLoadingIndicator(
                        size = 18.dp,
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
            enter = ArworaMotionTokens.standardExpandTransition(),
            exit = ArworaMotionTokens.standardCollapseTransition()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wireless_notification_access_warning_card"),
                shape = ArworaDesignTokens.CardShape,
                colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
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
                        shape = ArworaDesignTokens.PillShape
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
            shape = ArworaDesignTokens.CardShape,
            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
            border = ArworaDesignTokens.cardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
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
                        shape = ArworaDesignTokens.PillShape
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
    activeWifiSsid: String?,
    isWifiTrusted: Boolean,
    isAdbConnected: Boolean,
    isWirelessDebuggingEnabled: Boolean,
    lastEventSummary: String?,
    isBusy: Boolean,
    showDevOptionsGuidance: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onConnectNow: () -> Unit,
    onOpenDevOptions: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pairing_card_wifi_auto"),
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
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
                            text = "Automatically discover _adb-tls-connect._tcp on Wi-Fi and verify a real Wireless ADB TLS stream.",
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = (if (isWifiConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.12f),
                    border = BorderStroke(
                        1.dp,
                        (if (isWifiConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.outline).copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (isWifiConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isWifiConnected) {
                                activeWifiSsid?.let { "$it${if (isWifiTrusted) " • Trusted" else ""}" }
                                    ?: if (isWifiTrusted) "Wi-Fi Trusted" else "Wi-Fi Online"
                            } else {
                                "Wi-Fi Offline"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isWifiConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isAdbConnected || isWirelessDebuggingEnabled) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF10B981).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("wifi_auto_adb_status_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Text(
                                text = if (isAdbConnected) "Active / Connected" else "Active / Ready",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
            }

            if (isBusy) {
                ExpressiveWavyLinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wifi_auto_wavy_progress"),
                    height = 10.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (showDevOptionsGuidance) {
                Surface(
                    shape = ArworaDesignTokens.InnerTileShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wifi_auto_dev_options_guidance")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = NativeAdbManager.ERROR_WIRELESS_DEBUGGING_OFF,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        OutlinedButton(
                            onClick = onOpenDevOptions,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("wifi_auto_open_dev_options_button"),
                            shape = ArworaDesignTokens.PillShape
                        ) {
                            Icon(Icons.Default.DeveloperMode, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Developer Options", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else if (!lastEventSummary.isNullOrBlank()) {
                Surface(
                    shape = ArworaDesignTokens.InnerTileShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    border = ArworaDesignTokens.innerTileBorder(),
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
                            tint = if (isAdbConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
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
                onClick = onConnectNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("wifi_auto_save_trigger_button"),
                shape = ArworaDesignTokens.PillShape,
                enabled = !isBusy
            ) {
                Text(
                    text = if (isBusy) "Connecting..." else "Connect Now",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun openDeveloperOptions(context: Context) {
    if (!NativeAdbManager.openWirelessDebuggingSettings(context)) {
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
                    shape = ArworaDesignTokens.PillShape,
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
                    shape = ArworaDesignTokens.PillShape
                ) {
                    Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
