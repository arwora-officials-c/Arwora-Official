package com.arwora.feature.system.connect.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arwora.feature.system.connect.data.ArworaConnectManager
import com.arwora.feature.system.connect.data.ConnectNetworkUtils
import com.arwora.feature.system.connect.model.FileTransferRecord
import com.arwora.feature.system.connect.model.PeerConnectionState
import com.arwora.feature.system.connect.model.PeerUiState
import com.arwora.feature.system.connect.model.TransferDirection
import com.arwora.feature.system.connect.model.TransferStatus
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.utils.SoundEffectManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArworaConnectScreen(
    onBack: () -> Unit,
    vm: ArworaConnectViewModel = viewModel()
) {
    val context = LocalContext.current
    val identity by vm.localIdentity.collectAsStateWithLifecycle()
    val peers by vm.peers.collectAsStateWithLifecycle()
    val transfers by vm.transfers.collectAsStateWithLifecycle()
    val isServiceRunning by vm.isServiceRunning.collectAsStateWithLifecycle()
    val autoReconnect by vm.autoReconnectEnabled.collectAsStateWithLifecycle()
    val messageRetentionPolicy by vm.messageRetentionPolicy.collectAsStateWithLifecycle()
    val lastRetentionDuration by vm.lastNonOffRetentionPolicy.collectAsStateWithLifecycle()
    val isPairingBusy by vm.isPairingBusy.collectAsStateWithLifecycle()
    val isScanningLan by vm.isScanningLan.collectAsStateWithLifecycle()
    val discoveredHosts by vm.discoveredLanHosts.collectAsStateWithLifecycle()
    val nearbyPeers by vm.nearbyPeers.collectAsStateWithLifecycle()
    val isNearbyScanning by vm.isNearbyScanning.collectAsStateWithLifecycle()
    val connectingNearbyIds by vm.connectingNearbyIds.collectAsStateWithLifecycle()
    val chatMessagesByPeer by vm.chatMessagesByPeer.collectAsStateWithLifecycle()
    val unreadChatCounts by vm.unreadChatCounts.collectAsStateWithLifecycle()
    val activeChatPeerId by vm.activeChatPeerId.collectAsStateWithLifecycle()
    val isSendingChatImage by vm.isSendingChatImage.collectAsStateWithLifecycle()
    val statusMessage by vm.statusMessage.collectAsStateWithLifecycle()
    val actionFeedback by vm.actionFeedback.collectAsStateWithLifecycle()

    var showQrModal by remember { mutableStateOf(false) }
    var showScannerDialog by remember { mutableStateOf(false) }
    var showPairCodeDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var pendingDisconnectPeer by remember { mutableStateOf<PeerUiState?>(null) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            vm.sendFiles(uris)
        }
    }

    LaunchedEffect(Unit) {
        if (!identity.isServerRunning) {
            vm.startServerAndMonitor()
        }
    }

    LaunchedEffect(actionFeedback) {
        val msg = actionFeedback
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            vm.clearFeedback()
        }
    }

    BackHandler(
        enabled = activeChatPeerId != null ||
            showQrModal ||
            showScannerDialog ||
            showPairCodeDialog ||
            showRenameDialog ||
            showSettingsDialog ||
            pendingDisconnectPeer != null
    ) {
        vm.closeChatRoom()
        showQrModal = false
        showScannerDialog = false
        showPairCodeDialog = false
        showRenameDialog = false
        showSettingsDialog = false
        pendingDisconnectPeer = null
    }

    val activeOnline = identity.isServerRunning || isServiceRunning
    val connectedPeers = peers.filter { it.connectionState == PeerConnectionState.CONNECTED }
    val visibleNearbyPeers = remember(nearbyPeers, connectedPeers) {
        val connectedIds = connectedPeers.map { it.peer.deviceId }.toSet()
        nearbyPeers.filterNot { it.deviceId in connectedIds }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Arwora Connect",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ArworaPillBadge(
                            text = if (activeOnline) "${connectedPeers.size} Live" else "Standby",
                            tint = if (connectedPeers.isNotEmpty()) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                            showDot = true
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            onBack()
                        },
                        modifier = Modifier.testTag("connect_hub_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            vm.probeReconnectNow()
                        },
                        modifier = Modifier.testTag("connect_hub_probe_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Probe Trusted Peers")
                    }
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            showSettingsDialog = true
                        },
                        modifier = Modifier.testTag("connect_hub_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Arwora Connect Settings")
                    }
                    Switch(
                        checked = activeOnline,
                        onCheckedChange = { enable ->
                            SoundEffectManager.playClick()
                            if (enable) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    !ArworaConnectManager.hasNotificationPermission(context)
                                ) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                vm.startServerAndMonitor()
                            } else {
                                vm.stopServerAndMonitor()
                            }
                        },
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .testTag("connect_hub_server_switch")
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .testTag("connect_hub_list")
        ) {
            // 0. Zero-Config Nearby Devices Radar Banner & List
            item {
                NearbyDevicesRadarCard(
                    nearbyPeers = visibleNearbyPeers,
                    isScanning = isNearbyScanning,
                    isHubOnline = activeOnline,
                    connectingPeerIds = connectingNearbyIds,
                    onOneTapConnect = { peer -> vm.connectToNearbyPeer(peer) }
                )
            }

            // 1. My Device Identity & Pairing Card
            item {
                Card(
                    shape = ArworaDesignTokens.CardShape,
                    colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                    border = ArworaDesignTokens.cardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ArworaTonalIconBadge(
                                icon = Icons.Default.Hub,
                                tint = MaterialTheme.colorScheme.primary,
                                size = 44.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = identity.deviceName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("connect_hub_device_name")
                                )
                                Text(
                                    text = "IP ${identity.localIp}:${identity.listeningPort} • Fingerprint ${identity.certFingerprint.take(12)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    showRenameDialog = true
                                },
                                modifier = Modifier.testTag("connect_hub_rename_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename Device")
                            }
                        }

                        // Pairing Code & Auto-Reconnect Row
                        Surface(
                            shape = ArworaDesignTokens.InnerTileShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                            border = ArworaDesignTokens.innerTileBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "6-Digit Pairing Code",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = identity.pairingCode,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("connect_hub_pairing_code")
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    IconButton(
                                        onClick = {
                                            SoundEffectManager.playClick()
                                            vm.regeneratePairingCode()
                                        }
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Regenerate Code")
                                    }
                                    IconButton(
                                        onClick = {
                                            SoundEffectManager.playClick()
                                            showQrModal = true
                                        },
                                        modifier = Modifier.testTag("connect_hub_show_qr_button")
                                    ) {
                                        Icon(Icons.Default.QrCode2, contentDescription = "Show QR Code", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        // Pairing Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    showScannerDialog = true
                                },
                                shape = ArworaDesignTokens.PillShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("connect_hub_scan_qr_button")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan QR", fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    showPairCodeDialog = true
                                },
                                shape = ArworaDesignTokens.PillShape,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("connect_hub_enter_code_button")
                            ) {
                                Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pair Code", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Auto-Reconnect Background Toggle
                        Surface(
                            shape = ArworaDesignTokens.InnerTileShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
                            border = ArworaDesignTokens.innerTileBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Auto-Reconnect Trusted Peers", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Silently restore connections when paired peers return on Wi-Fi",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = autoReconnect,
                                    onCheckedChange = { vm.setAutoReconnect(it) },
                                    modifier = Modifier.testTag("connect_hub_auto_reconnect_switch")
                                )
                            }
                        }
                    }
                }
            }

            // 1b. Arwora Connect Settings Configuration Panel (P2P Message Auto-Delete)
            item {
                ArworaConnectSettingsPanel(
                    policy = messageRetentionPolicy,
                    lastSelectedDuration = lastRetentionDuration,
                    onSelectPolicy = { policy -> vm.setMessageRetentionPolicy(policy) },
                    modifier = Modifier.testTag("connect_settings_panel_card")
                )
            }

            // 2. Send / Receive Action Card
            item {
                val selectedConnected = connectedPeers.filter { it.isSelectedForSend }
                Card(
                    shape = ArworaDesignTokens.CardShape,
                    colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                    border = ArworaDesignTokens.cardBorder(),
                    elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Send & Receive Hub", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    text = when {
                                        selectedConnected.isNotEmpty() ->
                                            "Ready to stream to ${selectedConnected.size} selected peer(s)"
                                        connectedPeers.isNotEmpty() ->
                                            "Select at least one connected peer below to send files"
                                        else ->
                                            "Pair a device via QR or 6-digit code to enable trusted auto-accept"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ArworaPillBadge(
                                text = "Auto-Accept",
                                tint = Color(0xFF10B981)
                            )
                        }

                        if (!statusMessage.isNullOrBlank()) {
                            Text(
                                text = statusMessage!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = {
                                SoundEffectManager.playClick()
                                multiFilePicker.launch(arrayOf("*/*"))
                            },
                            enabled = selectedConnected.isNotEmpty(),
                            shape = ArworaDesignTokens.PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("connect_hub_send_files_button")
                        ) {
                            Text("Select Files to Send", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Connected & Trusted Peers Section
            item {
                Text(
                    text = "Paired & Connected Devices (${peers.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            if (peers.isEmpty()) {
                item {
                    Card(
                        shape = ArworaDesignTokens.CardShape,
                        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                        border = ArworaDesignTokens.cardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                            Text("No Trusted Peers Paired", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Scan another device's QR code or enter their 6-digit pairing code to establish encrypted auto-accept sessions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(peers, key = { it.peer.deviceId }) { peerUi ->
                    PeerSessionCard(
                        peerUi = peerUi,
                        unreadChatCount = unreadChatCounts[peerUi.peer.deviceId] ?: 0,
                        onToggleSelect = { vm.togglePeerSelection(peerUi.peer.deviceId) },
                        onOpenChat = { vm.openChatRoom(peerUi.peer.deviceId) },
                        onDisconnect = { pendingDisconnectPeer = peerUi },
                        onReconnect = { vm.reconnectPeer(peerUi.peer.deviceId) },
                        onForget = { vm.forgetPeer(peerUi.peer.deviceId) }
                    )
                }
            }

            // 4. Transfer Activity Feed
            if (transfers.isNotEmpty()) {
                item {
                    Text(
                        text = "Transfer Activity (${transfers.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
                    )
                }
                items(transfers, key = { it.id }) { transfer ->
                    TransferActivityCard(transfer = transfer)
                }
            }
        }
    }

    if (showQrModal) {
        ShowPairingQrDialog(
            payload = vm.buildQrPayload(),
            onRegenerateCode = { vm.regeneratePairingCode() },
            onDismiss = { showQrModal = false }
        )
    }

    if (showScannerDialog) {
        ConnectQrScannerDialog(
            onQrDetected = { raw ->
                showScannerDialog = false
                vm.pairWithQrRaw(raw)
            },
            onDismiss = { showScannerDialog = false }
        )
    }

    if (showPairCodeDialog) {
        PairByCodeDialog(
            initialIpHint = identity.localIp,
            defaultPort = identity.listeningPort,
            discoveredHosts = discoveredHosts,
            isScanningLan = isScanningLan,
            isBusy = isPairingBusy,
            onScanLan = { vm.scanLanForPeers() },
            onPair = { ip, port, code ->
                vm.pairWithCode(ip, port, code) { ok, _ ->
                    if (ok) showPairCodeDialog = false
                }
            },
            onDismiss = { showPairCodeDialog = false }
        )
    }

    if (showRenameDialog) {
        RenameLocalDeviceDialog(
            currentName = identity.deviceName,
            onConfirm = { newName ->
                showRenameDialog = false
                vm.renameLocalDevice(newName)
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showSettingsDialog) {
        ArworaConnectSettingsDialog(
            policy = messageRetentionPolicy,
            lastSelectedDuration = lastRetentionDuration,
            autoReconnectEnabled = autoReconnect,
            onSelectPolicy = { policy -> vm.setMessageRetentionPolicy(policy) },
            onToggleAutoReconnect = { enabled -> vm.setAutoReconnect(enabled) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    pendingDisconnectPeer?.let { targetPeer ->
        DisconnectPeerConfirmationDialog(
            peerName = targetPeer.peer.deviceName,
            onConfirm = {
                val peerId = targetPeer.peer.deviceId
                pendingDisconnectPeer = null
                vm.disconnectPeer(peerId)
            },
            onDismiss = { pendingDisconnectPeer = null }
        )
    }

    activeChatPeerId?.let { chatPeerId ->
        val targetPeerUi = peers.firstOrNull { it.peer.deviceId == chatPeerId }
        if (targetPeerUi != null) {
            ArworaConnectChatDialog(
                peerUi = targetPeerUi,
                messages = chatMessagesByPeer[chatPeerId].orEmpty(),
                localDeviceId = identity.deviceId,
                isSendingImage = isSendingChatImage,
                retentionPolicy = messageRetentionPolicy,
                onSendText = { text -> vm.sendChatText(chatPeerId, text) },
                onSendImageUri = { uri -> vm.sendChatImage(chatPeerId, uri) },
                onSendVoiceNote = { audioBase64, durationMs, waveform ->
                    vm.sendChatVoice(chatPeerId, audioBase64, durationMs, waveform)
                },
                onToggleReaction = { msgId, emoji -> vm.toggleChatReaction(chatPeerId, msgId, emoji) },
                onDismiss = { vm.closeChatRoom() }
            )
        }
    }
}

@Composable
private fun PeerSessionCard(
    peerUi: PeerUiState,
    unreadChatCount: Int,
    onToggleSelect: () -> Unit,
    onOpenChat: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    onForget: () -> Unit
) {
    val isConnected = peerUi.connectionState == PeerConnectionState.CONNECTED
    val isReconnecting = peerUi.connectionState == PeerConnectionState.RECONNECTING
    val statusColor = when (peerUi.connectionState) {
        PeerConnectionState.CONNECTED -> Color(0xFF10B981)
        PeerConnectionState.RECONNECTING -> Color(0xFFF59E0B)
        PeerConnectionState.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = if (isConnected) BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)) else ArworaDesignTokens.cardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connect_peer_card_${peerUi.peer.deviceId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = peerUi.isSelectedForSend,
                    onCheckedChange = { onToggleSelect() },
                    enabled = isConnected
                )
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peerUi.peer.deviceName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${peerUi.peer.lastKnownIp}:${peerUi.peer.port} • FP ${peerUi.peer.certFingerprint.take(10)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ArworaPillBadge(
                    text = when {
                        isConnected -> "${peerUi.signalQuality.label} (${peerUi.rttMs}ms)"
                        isReconnecting -> "Reconnecting"
                        else -> "Offline"
                    },
                    tint = statusColor,
                    showDot = true
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (peerUi.peer.trustedAutoAccept) "Trusted Cert • Auto-Accept On" else "Manual Accept",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                IconButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        onForget()
                    },
                    modifier = Modifier.testTag("connect_forget_peer_${peerUi.peer.deviceId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Forget Trusted Peer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isConnected) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            onOpenChat()
                        },
                        shape = ArworaDesignTokens.PillShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("connect_chat_peer_${peerUi.peer.deviceId}")
                    ) {
                        Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (unreadChatCount > 0) "Chat Room ($unreadChatCount)" else "Chat Room",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            onDisconnect()
                        },
                        shape = ArworaDesignTokens.PillShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("connect_disconnect_peer_${peerUi.peer.deviceId}")
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Disconnect",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.testTag("connect_disconnect_label_${peerUi.peer.deviceId}")
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        SoundEffectManager.playClick()
                        onReconnect()
                    },
                    shape = ArworaDesignTokens.PillShape,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("connect_reconnect_peer_${peerUi.peer.deviceId}")
                ) {
                    Text(
                        text = "Reconnect",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferActivityCard(transfer: FileTransferRecord) {
    val isIncoming = transfer.direction == TransferDirection.INCOMING
    val statusColor = when (transfer.status) {
        TransferStatus.COMPLETED -> Color(0xFF10B981)
        TransferStatus.IN_PROGRESS, TransferStatus.QUEUED -> MaterialTheme.colorScheme.primary
        TransferStatus.FAILED -> MaterialTheme.colorScheme.error
    }

    Card(
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("connect_transfer_card_${transfer.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaTonalIconBadge(
                    icon = if (isIncoming) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                    tint = statusColor,
                    size = 40.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transfer.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${if (isIncoming) "From" else "To"} ${transfer.peerDeviceName} • ${ConnectNetworkUtils.formatBytes(transfer.transferredBytes)} / ${ConnectNetworkUtils.formatBytes(transfer.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ArworaPillBadge(
                    text = when {
                        transfer.status == TransferStatus.COMPLETED && isIncoming && transfer.autoAccepted -> "Auto-Accepted"
                        transfer.status == TransferStatus.COMPLETED -> "Done"
                        transfer.status == TransferStatus.FAILED -> "Failed"
                        else -> "${(transfer.progressFraction * 100).toInt()}%"
                    },
                    tint = statusColor
                )
            }

            if (transfer.status == TransferStatus.IN_PROGRESS || transfer.status == TransferStatus.QUEUED) {
                LinearProgressIndicator(
                    progress = { transfer.progressFraction },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!transfer.savedFilePath.isNullOrBlank()) {
                Text(
                    text = "Saved: ${transfer.savedFilePath}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!transfer.errorMessage.isNullOrBlank()) {
                Text(
                    text = transfer.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
