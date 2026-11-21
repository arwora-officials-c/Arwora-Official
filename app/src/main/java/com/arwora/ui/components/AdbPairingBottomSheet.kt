package com.arwora.ui.components

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.service.AdbPairingNotificationService
import com.arwora.service.AdbPairingNotifier
import com.arwora.utils.NativeAdbConnectionState
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.PairingStatus
import com.arwora.utils.SoundEffectManager
import com.arwora.utils.WirelessPairingManager
import kotlinx.coroutines.launch

enum class AdbPairingSheetTab {
    AUTOMATIC,
    MANUAL
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbPairingBottomSheet(
    visible: Boolean,
    initialTab: AdbPairingSheetTab = AdbPairingSheetTab.AUTOMATIC,
    onDismissRequest: () -> Unit,
    onPairingComplete: () -> Unit = {}
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("adb_pairing_bottom_sheet"),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        AdbPairingSheetContent(
            initialTab = initialTab,
            onDismissRequest = onDismissRequest,
            onPairingComplete = onPairingComplete
        )
    }
}

@Composable
fun AdbPairingSheetContent(
    initialTab: AdbPairingSheetTab = AdbPairingSheetTab.AUTOMATIC,
    onDismissRequest: () -> Unit,
    onPairingComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val pairingStatus by WirelessPairingManager.status.collectAsStateWithLifecycle()
    val discoveredPort by WirelessPairingManager.discoveredPort.collectAsStateWithLifecycle()
    val capturedCode by WirelessPairingManager.lastCapturedCode.collectAsStateWithLifecycle()
    val savedHost by WirelessPairingManager.savedHost.collectAsStateWithLifecycle()
    val isAdbConnected by NativeAdbManager.isConnected.collectAsStateWithLifecycle()
    val adbConnectionState by NativeAdbManager.connectionState.collectAsStateWithLifecycle()
    val isSessionForgotten by NativeAdbManager.isSessionForgotten.collectAsStateWithLifecycle()
    val adbStatusMessage by NativeAdbManager.statusMessage.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    var hostInput by rememberSaveable { mutableStateOf(savedHost.ifBlank { "127.0.0.1" }) }
    var codeInput by rememberSaveable { mutableStateOf(capturedCode.orEmpty()) }
    var portInput by rememberSaveable { mutableStateOf(discoveredPort?.toString().orEmpty()) }

    LaunchedEffect(savedHost) {
        if (savedHost.isNotBlank()) hostInput = savedHost
    }
    LaunchedEffect(capturedCode) {
        if (!capturedCode.isNullOrBlank()) codeInput = capturedCode!!
    }
    LaunchedEffect(discoveredPort) {
        if (discoveredPort != null) portInput = discoveredPort.toString()
    }
    LaunchedEffect(pairingStatus) {
        if (pairingStatus is PairingStatus.Success) {
            onPairingComplete()
        }
    }

    val startAutomaticDiscoveryFlow = {
        AdbPairingNotificationService.start(context)
        WirelessPairingManager.startAutomaticPairing(context)
        NativeAdbManager.openWirelessDebuggingSettings(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Proceed with mDNS discovery & Developer Options regardless of notification grant
        startAutomaticDiscoveryFlow()
    }

    val isBusy = pairingStatus is PairingStatus.Listening ||
        pairingStatus is PairingStatus.PairingInProgress ||
        adbConnectionState == NativeAdbConnectionState.CONNECTING

    val statusBadgeColor = when {
        isAdbConnected -> Color(0xFF10B981)
        isSessionForgotten -> MaterialTheme.colorScheme.error
        isBusy -> MaterialTheme.colorScheme.primary
        pairingStatus is PairingStatus.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusBadgeLabel = when {
        isAdbConnected -> "Connected"
        isSessionForgotten -> "Keys Reset Needed"
        pairingStatus is PairingStatus.PairingInProgress -> "Pairing"
        pairingStatus is PairingStatus.Listening || adbConnectionState == NativeAdbConnectionState.CONNECTING -> "Scanning"
        pairingStatus is PairingStatus.Failed -> "Retry"
        else -> "Ready"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header Row: "Pair with device" + Status Pill + Close
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Pair with device",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = ArworaDesignTokens.PillShape,
                    color = statusBadgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, statusBadgeColor.copy(alpha = 0.35f)),
                    modifier = Modifier.testTag("adb_pairing_sheet_status_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusBadgeColor)
                        )
                        Text(
                            text = statusBadgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusBadgeColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            IconButton(
                onClick = {
                    SoundEffectManager.playClick()
                    onDismissRequest()
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("adb_pairing_sheet_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Pair with device dialog"
                )
            }
        }

        // Exact Shizuku-style pairing explanation copy
        Text(
            text = "Pair without typing a code:\n1. Tap Developer options below. The app opens Wireless debugging and the pairing dialog itself, reads the code and port, and starts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
            modifier = Modifier.testTag("adb_pairing_sheet_description")
        )

        // Two-mode pill selector: Automatic vs Manual
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isAuto = selectedTab == AdbPairingSheetTab.AUTOMATIC
            Button(
                onClick = {
                    SoundEffectManager.playClick()
                    selectedTab = AdbPairingSheetTab.AUTOMATIC
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("adb_pairing_tab_automatic"),
                shape = ArworaDesignTokens.PillShape,
                colors = if (isAuto) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(
                    text = "Automatic",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val isManual = selectedTab == AdbPairingSheetTab.MANUAL
            Button(
                onClick = {
                    SoundEffectManager.playClick()
                    selectedTab = AdbPairingSheetTab.MANUAL
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("adb_pairing_tab_manual"),
                shape = ArworaDesignTokens.PillShape,
                colors = if (isManual) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(
                    text = "Manual",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isBusy) {
            val linearProgress = (pairingStatus as? PairingStatus.Listening)?.progress
            ExpressiveWavyLinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("adb_pairing_sheet_progress"),
                progress = linearProgress,
                height = 8.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(120)) },
            label = "adb_pairing_sheet_tab_content"
        ) { tab ->
            when (tab) {
                AdbPairingSheetTab.AUTOMATIC -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("adb_pairing_automatic_panel"),
                        shape = ArworaDesignTokens.CardShape,
                        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                        border = ArworaDesignTokens.cardBorder(),
                        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiFind,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Automatic mDNS Pairing & Connect",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Scans _adb-tls-pairing._tcp & _adb-tls-connect._tcp using key '${NativeAdbManager.CLIENT_IDENTITY}'.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            val statusDetail = when (val s = pairingStatus) {
                                is PairingStatus.Listening -> s.stageMessage
                                is PairingStatus.PairingInProgress -> s.stageMessage
                                is PairingStatus.CodeCaptured -> "Captured code ${s.code}${s.port?.let { " on port $it" } ?: ""}"
                                is PairingStatus.Success -> s.message
                                is PairingStatus.Failed -> s.error
                                is PairingStatus.Idle -> adbStatusMessage
                            }

                            Surface(
                                shape = ArworaDesignTokens.InnerTileShape,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = ArworaDesignTokens.innerTileBorder(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = statusDetail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (pairingStatus is PairingStatus.Failed || isSessionForgotten) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        !AdbPairingNotifier.hasNotificationPermission(context)
                                    ) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        startAutomaticDiscoveryFlow()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("adb_pairing_sheet_dev_options_button"),
                                shape = ArworaDesignTokens.PillShape
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeveloperMode,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Developer Options",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    if (isBusy) {
                                        WirelessPairingManager.cancelAutomaticPairing()
                                    } else {
                                        coroutineScope.launch {
                                            val (ok, msg) = WirelessPairingManager.connectUnifiedWirelessAdb(context)
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            if (ok) {
                                                onPairingComplete()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("adb_pairing_auto_connect_button"),
                                shape = ArworaDesignTokens.PillShape
                            ) {
                                Text(
                                    text = if (isBusy) "Cancel Scan" else "Start Auto Connect",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                AdbPairingSheetTab.MANUAL -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("adb_pairing_manual_panel"),
                        shape = ArworaDesignTokens.CardShape,
                        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                        border = ArworaDesignTokens.cardBorder(),
                        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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
                                    text = "Manual Pairing Code & Port",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedTextField(
                                value = hostInput,
                                onValueChange = { hostInput = it },
                                label = { Text("IP Address / Host", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                placeholder = { Text("127.0.0.1") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("adb_pairing_sheet_host_input"),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = codeInput,
                                    onValueChange = { raw ->
                                        if (raw.length <= 6 && raw.all { it.isDigit() }) {
                                            codeInput = raw
                                        }
                                    },
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
                                        .testTag("adb_pairing_sheet_code_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                OutlinedTextField(
                                    value = portInput,
                                    onValueChange = { raw ->
                                        if (raw.length <= 5 && raw.all { it.isDigit() }) {
                                            portInput = raw
                                        }
                                    },
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
                                        .testTag("adb_pairing_sheet_port_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    val port = portInput.toIntOrNull()
                                    if (WirelessPairingManager.isValidManualPairingInput(hostInput, port, codeInput)) {
                                        WirelessPairingManager.executePairing(
                                            context = context,
                                            host = hostInput.trim(),
                                            port = port!!,
                                            code = codeInput.trim()
                                        )
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Enter valid host, 6-digit code, and port (1024-65535)",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                enabled = !isBusy && WirelessPairingManager.isValidManualPairingInput(
                                    hostInput,
                                    portInput.toIntOrNull(),
                                    codeInput
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("adb_pairing_manual_submit_button"),
                                shape = ArworaDesignTokens.PillShape
                            ) {
                                Text(
                                    text = if (isBusy) "Pairing..." else "Pair & Connect",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

        // Clean Forget & Regenerate RSA Keypair action for re-pairing
        OutlinedButton(
            onClick = {
                SoundEffectManager.playClick()
                NativeAdbManager.forgetPairedDeviceAndReset(context)
                codeInput = ""
                portInput = ""
                Toast.makeText(
                    context,
                    "Cleared old ADB keys & generated a fresh pairing keypair.",
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("adb_pairing_sheet_reset_keys_button"),
            shape = ArworaDesignTokens.PillShape
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pair New Device",
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
