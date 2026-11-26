package com.arwora.feature.system.connect.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.arwora.feature.system.connect.data.ArworaConnectManager
import com.arwora.feature.system.connect.model.PeerConnectionState
import com.arwora.feature.system.connect.model.PeerUiState
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.launch

/**
 * System Tab card for Arwora Connect offline multi-device P2P file sharing hub.
 * Matches [ArworaDesignTokens] 1.dp outline, card shape, and minimal bold CTA rules.
 */
@Composable
fun ArworaConnectCard(
    onOpenConnectHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember(context) { ArworaConnectManager.getEngine(context) }

    val identity by engine.localIdentity.collectAsStateWithLifecycle()
    val peers by engine.peers.collectAsStateWithLifecycle()
    val nearbyPeers by engine.nearbyPeers.collectAsStateWithLifecycle()
    val chatMessagesByPeer by engine.chatMessagesByPeer.collectAsStateWithLifecycle()
    val unreadChatCounts by engine.unreadChatCounts.collectAsStateWithLifecycle()
    val isServiceRunning by ArworaConnectManager.isServiceRunning.collectAsStateWithLifecycle()
    val retentionPolicy by ArworaConnectManager.messageRetentionPolicy.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        ArworaConnectManager.syncPreferences(context)
    }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var pendingDisconnectPeer by remember { mutableStateOf<PeerUiState?>(null) }
    var activeChatPeerId by remember { mutableStateOf<String?>(null) }
    var isSendingChatImage by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    val multiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val count = engine.broadcastFilesToSelectedPeers(uris)
                Toast.makeText(
                    context,
                    if (count > 0) "Sent $count transfer(s)" else "Connect at least one peer to send",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val connectedCount = peers.count { it.connectionState == PeerConnectionState.CONNECTED }
    val activeOnline = identity.isServerRunning || isServiceRunning
    val badgeColor = when {
        connectedCount > 0 -> Color(0xFF10B981)
        activeOnline -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val badgeText = when {
        connectedCount > 0 -> "$connectedCount Connected"
        activeOnline -> "Online"
        else -> "Standby"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("arwora_connect_card"),
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
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaTonalIconBadge(
                    icon = Icons.Default.Hub,
                    tint = if (activeOnline) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    size = 46.dp,
                    contentDescription = "Arwora Connect"
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Arwora Connect",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (nearbyPeers.isNotEmpty()) {
                            "${nearbyPeers.size} nearby device(s) discovered on local Wi-Fi"
                        } else {
                            "Offline P2P file sharing, live chat & zero-config discovery"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ArworaPillBadge(
                    text = badgeText,
                    tint = badgeColor,
                    showDot = true,
                    modifier = Modifier.testTag("arwora_connect_status_badge")
                )
            }

            // My Device Identity + P2P Socket Switch
            Surface(
                shape = ArworaDesignTokens.InnerTileShape,
                color = if (activeOnline) {
                    Color(0xFF10B981).copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                },
                border = if (activeOnline) {
                    BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.30f))
                } else {
                    ArworaDesignTokens.innerTileBorder()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = identity.deviceName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("connect_card_device_name")
                            )
                            IconButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    showRenameDialog = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("connect_card_rename_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename Device",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = "${identity.localIp}:${identity.listeningPort} • Code ${identity.pairingCode}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            scope.launch {
                                if (!identity.isServerRunning) engine.startServer()
                                showQrDialog = true
                            }
                        },
                        modifier = Modifier.testTag("connect_card_qr_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = "Show Pairing QR Code",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
                                ArworaConnectManager.startHubService(context)
                            } else {
                                ArworaConnectManager.stopHubService(context)
                                scope.launch { engine.stopServer() }
                            }
                        },
                        modifier = Modifier.testTag("arwora_connect_master_switch")
                    )
                }
            }

            // Connected & Trusted Peers Quick List
            if (peers.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    peers.take(3).forEach { item ->
                        val isConnected = item.connectionState == PeerConnectionState.CONNECTED
                        Surface(
                            shape = ArworaDesignTokens.InnerTileShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
                            border = ArworaDesignTokens.innerTileBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = item.peer.deviceName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isConnected) {
                                            "${item.signalQuality.label} (${item.rttMs}ms) • Trusted Auto-Accept"
                                        } else {
                                            "${item.peer.lastKnownIp} • Offline"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isConnected) {
                                    val unread = unreadChatCounts[item.peer.deviceId] ?: 0
                                    IconButton(
                                        onClick = {
                                            SoundEffectManager.playClick()
                                            activeChatPeerId = item.peer.deviceId
                                            engine.chatManager.setActiveChatPeer(item.peer.deviceId)
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("connect_card_chat_${item.peer.deviceId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Forum,
                                            contentDescription = if (unread > 0) "Chat Room ($unread unread)" else "Open Chat Room",
                                            tint = if (unread > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            SoundEffectManager.playClick()
                                            pendingDisconnectPeer = item
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("connect_card_disconnect_${item.peer.deviceId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LinkOff,
                                            contentDescription = "Disconnect",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Disconnect",
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.testTag("connect_card_disconnect_label_${item.peer.deviceId}")
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            SoundEffectManager.playClick()
                                            scope.launch { engine.reconnectPeer(item.peer.deviceId) }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reconnect Peer",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Primary Action Buttons (Clean bold text without sparkle/emoji icons per AGENTS.md)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        if (connectedCount > 0) {
                            multiFilePicker.launch(arrayOf("*/*"))
                        } else {
                            scope.launch {
                                if (!identity.isServerRunning) engine.startServer()
                                showQrDialog = true
                            }
                        }
                    },
                    shape = ArworaDesignTokens.PillShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("connect_card_primary_action_button")
                ) {
                    Text(
                        text = if (connectedCount > 0) "Send Files" else "Pair Device",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = {
                        SoundEffectManager.playClick()
                        onOpenConnectHub()
                    },
                    shape = ArworaDesignTokens.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("connect_card_open_hub_button")
                ) {
                    Text(
                        text = "Open Connect Hub",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameLocalDeviceDialog(
            currentName = identity.deviceName,
            onConfirm = { newName ->
                showRenameDialog = false
                scope.launch { engine.updateDeviceName(newName) }
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showQrDialog) {
        ShowPairingQrDialog(
            payload = engine.buildQrPayload(),
            onRegenerateCode = { engine.regeneratePairingCode() },
            onDismiss = { showQrDialog = false }
        )
    }

    pendingDisconnectPeer?.let { targetPeer ->
        DisconnectPeerConfirmationDialog(
            peerName = targetPeer.peer.deviceName,
            onConfirm = {
                val peerId = targetPeer.peer.deviceId
                pendingDisconnectPeer = null
                if (activeChatPeerId == peerId) {
                    activeChatPeerId = null
                    engine.chatManager.setActiveChatPeer(null)
                }
                engine.disconnectPeer(peerId, userInitiated = true)
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
                retentionPolicy = retentionPolicy,
                onSendText = { text ->
                    scope.launch {
                        engine.sendChatText(chatPeerId, text)
                    }
                },
                onSendImageUri = { uri ->
                    scope.launch {
                        isSendingChatImage = true
                        engine.sendChatImage(chatPeerId, uri)
                        isSendingChatImage = false
                    }
                },
                onSendVoiceNote = { audioBase64, durationMs, waveform ->
                    scope.launch {
                        engine.sendChatVoice(chatPeerId, audioBase64, durationMs, waveform)
                    }
                },
                onToggleReaction = { msgId, emoji ->
                    scope.launch {
                        engine.toggleChatReaction(chatPeerId, msgId, emoji)
                    }
                },
                onDismiss = {
                    activeChatPeerId = null
                    engine.chatManager.setActiveChatPeer(null)
                }
            )
        }
    }
}
