package com.arwora.feature.chat.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arwora.feature.chat.data.ChatEncryptedDatabase
import com.arwora.feature.chat.model.ChatHubTab
import com.arwora.feature.chat.model.ChatThread
import com.arwora.feature.chat.model.MessageDeliveryStatus
import com.arwora.feature.chat.model.NearChatPeer
import com.arwora.feature.chat.model.NearDiscoverySource
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.ui.components.PinAuthDialog
import com.arwora.ui.components.PinSetupDialog
import com.arwora.utils.AppLockerManager
import com.arwora.utils.SoundEffectManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHubScreen(
    onBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenAppLocker: () -> Unit,
    vm: ChatHubViewModel = viewModel()
) {
    val context = LocalContext.current
    val activeTab by vm.activeTab.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val filteredThreads by vm.filteredThreads.collectAsStateWithLifecycle()
    val allThreads by vm.allThreads.collectAsStateWithLifecycle()
    val globalMatchedMessages by vm.globalMatchedMessages.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isVaultUnlocked by vm.isVaultUnlocked.collectAsStateWithLifecycle()
    val nearPeers by vm.nearPeers.collectAsStateWithLifecycle()
    val isNearScanning by vm.isNearScanning.collectAsStateWithLifecycle()
    val radarStatus by vm.radarStatus.collectAsStateWithLifecycle()
    val retentionPolicy by vm.retentionPolicy.collectAsStateWithLifecycle()
    val lastRetentionDuration by vm.lastRetentionDuration.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val hasAppPin by AppLockerManager.hasPin.collectAsStateWithLifecycle()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var pendingAuthTarget by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showPinSetupForVault by remember { mutableStateOf(false) }

    val nearbyPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        vm.startNearDiscovery()
    }

    LaunchedEffect(Unit) {
        AppLockerManager.init(context)
    }

    LaunchedEffect(feedback) {
        val msg = feedback
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            vm.clearFeedback()
        }
    }

    BackHandler(enabled = showSettingsDialog || showNewChatDialog || pendingAuthTarget != null || showPinSetupForVault) {
        showSettingsDialog = false
        showNewChatDialog = false
        pendingAuthTarget = null
        showPinSetupForVault = false
    }

    fun requestProtectedAccess(onGranted: () -> Unit) {
        if (!hasAppPin) {
            onGranted()
        } else {
            pendingAuthTarget = onGranted
        }
    }

    fun handleThreadClick(thread: ChatThread) {
        SoundEffectManager.playClick()
        if (thread.isLocked || thread.isHidden) {
            requestProtectedAccess { onOpenThread(thread.id) }
        } else {
            onOpenThread(thread.id)
        }
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
                            text = "Chat Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (settings.ghostModeEnabled) {
                            ArworaPillBadge(
                                text = "Ghost",
                                tint = Color(0xFF8B5CF6),
                                showDot = true,
                                modifier = Modifier.testTag("chat_hub_ghost_badge")
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            onBack()
                        },
                        modifier = Modifier.testTag("chat_hub_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            vm.updateSettings { it.copy(ghostModeEnabled = !it.ghostModeEnabled) }
                        },
                        modifier = Modifier.testTag("chat_hub_ghost_toggle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Ghost Mode",
                            tint = if (settings.ghostModeEnabled) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            showSettingsDialog = true
                        },
                        modifier = Modifier.testTag("chat_hub_settings_btn")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Chat Hub Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    SoundEffectManager.playClick()
                    showNewChatDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Chat", fontWeight = FontWeight.Bold) },
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("chat_hub_new_fab")
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Global Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.updateSearchQuery(it) },
                placeholder = { Text("Search chats & messages...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { vm.updateSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = ArworaDesignTokens.CardShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_hub_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Hub Tabs: Chats, Archived, Near Chat, Vault
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChatHubTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    FilterChip(
                        selected = selected,
                        onClick = {
                            SoundEffectManager.playClick()
                            if (tab == ChatHubTab.VAULT && !isVaultUnlocked) {
                                if (!hasAppPin) {
                                    vm.setVaultUnlocked(true)
                                    vm.selectTab(ChatHubTab.VAULT)
                                } else {
                                    requestProtectedAccess {
                                        vm.setVaultUnlocked(true)
                                        vm.selectTab(ChatHubTab.VAULT)
                                    }
                                }
                            } else {
                                vm.selectTab(tab)
                            }
                        },
                        label = {
                            Text(
                                text = when (tab) {
                                    ChatHubTab.VAULT -> if (isVaultUnlocked) "Vault (Open)" else "Vault"
                                    ChatHubTab.NEAR_CHAT -> "Near Chat (${nearPeers.size})"
                                    else -> tab.title
                                },
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        },
                        leadingIcon = when (tab) {
                            ChatHubTab.CHATS -> ({ Icon(Icons.Default.Forum, null, Modifier.size(16.dp)) })
                            ChatHubTab.ARCHIVED -> ({ Icon(Icons.Default.Archive, null, Modifier.size(16.dp)) })
                            ChatHubTab.NEAR_CHAT -> ({ Icon(Icons.Default.Radar, null, Modifier.size(16.dp)) })
                            ChatHubTab.VAULT -> ({
                                Icon(
                                    if (isVaultUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    null,
                                    Modifier.size(16.dp)
                                )
                            })
                        },
                        modifier = Modifier.testTag("chat_hub_tab_${tab.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("chat_hub_list"),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Hidden Vault lock / PIN bar when inside Vault tab
                if (activeTab == ChatHubTab.VAULT) {
                    item {
                        Card(
                            shape = ArworaDesignTokens.CardShape,
                            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                            border = ArworaDesignTokens.cardBorder(),
                            elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp()),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Hidden Chat Vault",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (hasAppPin) "Protected by App Locker PIN & Biometric" else "Set a 4-digit PIN to lock this vault",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (hasAppPin) {
                                    OutlinedButton(
                                        onClick = {
                                            vm.setVaultUnlocked(false)
                                            vm.selectTab(ChatHubTab.CHATS)
                                        },
                                        shape = ArworaDesignTokens.PillShape,
                                        modifier = Modifier.testTag("chat_vault_lock_now_btn")
                                    ) {
                                        Text("Lock Vault", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = { showPinSetupForVault = true },
                                        shape = ArworaDesignTokens.PillShape,
                                        modifier = Modifier.testTag("chat_vault_setup_pin_btn")
                                    ) {
                                        Text("Set Vault PIN", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Near Chat Offline P2P Radar Section
                if (activeTab == ChatHubTab.NEAR_CHAT) {
                    item {
                        NearChatRadarCard(
                            isScanning = isNearScanning,
                            radarStatus = radarStatus,
                            onScanToggle = {
                                if (isNearScanning) {
                                    vm.stopNearDiscovery()
                                } else {
                                    val missing = vm.missingNearbyPermissions()
                                    if (missing.isNotEmpty()) {
                                        nearbyPermissionsLauncher.launch(missing.toTypedArray())
                                    } else {
                                        vm.startNearDiscovery()
                                    }
                                }
                            }
                        )
                    }

                    if (nearPeers.isEmpty()) {
                        item {
                            ChatEmptyCard(
                                title = "No Nearby Devices Yet",
                                subtitle = "Scan nearby to detect devices over Wi-Fi Direct, Bluetooth LE, or local LAN sockets with zero internet."
                            )
                        }
                    } else {
                        items(nearPeers, key = { "near_${it.id}" }) { peer ->
                            NearPeerCard(
                                peer = peer,
                                onConnectClick = {
                                    SoundEffectManager.playClick()
                                    vm.connectAndOpenNearPeer(peer) { thread ->
                                        onOpenThread(thread.id)
                                    }
                                }
                            )
                        }
                    }
                }

                // Global message content search matches
                if (searchQuery.isNotBlank() && globalMatchedMessages.isNotEmpty()) {
                    item {
                        Text(
                            text = "Matching Messages (${globalMatchedMessages.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(globalMatchedMessages.take(8), key = { "gmsg_${it.id}" }) { msg ->
                        val parent = allThreads.firstOrNull { it.id == msg.threadId }
                        Card(
                            onClick = {
                                parent?.let { handleThreadClick(it) } ?: onOpenThread(msg.threadId)
                            },
                            shape = ArworaDesignTokens.InnerTileShape,
                            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                            border = ArworaDesignTokens.innerTileBorder(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = parent?.title ?: msg.senderName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = msg.displaySummary(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Conversations List
                if (activeTab != ChatHubTab.NEAR_CHAT) {
                    if (filteredThreads.isEmpty()) {
                        item {
                            ChatEmptyCard(
                                title = when (activeTab) {
                                    ChatHubTab.ARCHIVED -> "No Archived Chats"
                                    ChatHubTab.VAULT -> "Hidden Vault Empty"
                                    else -> "No Conversations Yet"
                                },
                                subtitle = when (activeTab) {
                                    ChatHubTab.ARCHIVED -> "Archive conversations from a chat menu to keep your inbox clean."
                                    ChatHubTab.VAULT -> "Hide sensitive chats inside the Vault to keep them protected behind your PIN."
                                    else -> "Tap New Chat to start an encrypted 1-on-1 or Group chat, or open Near Chat for offline P2P."
                                }
                            )
                        }
                    } else {
                        items(filteredThreads, key = { it.id }) { thread ->
                            ChatThreadListCard(
                                thread = thread,
                                onClick = { handleThreadClick(thread) },
                                onToggleLock = {
                                    requestProtectedAccess { vm.toggleThreadLock(thread.id) }
                                },
                                onToggleHidden = {
                                    requestProtectedAccess { vm.toggleThreadHidden(thread.id) }
                                },
                                onToggleArchive = { vm.toggleThreadArchive(thread.id) },
                                onToggleMute = { vm.toggleThreadMute(thread.id) },
                                onDelete = { vm.deleteThread(thread.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        ChatHubSettingsDialog(
            settings = settings,
            retentionPolicy = retentionPolicy,
            lastRetentionDuration = lastRetentionDuration,
            isSqlCipherActive = ChatEncryptedDatabase.isSqlCipherActive,
            isBatteryOrDataSaverActive = vm.isLowBatteryOrDataSaverActive(),
            isDndActiveNow = vm.isWithinDndNow(),
            onUpdateSettings = { vm.updateSettings(it) },
            onSelectRetentionPolicy = { vm.setRetentionPolicy(it) },
            onOpenVaultSecurity = {
                showSettingsDialog = false
                onOpenAppLocker()
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showNewChatDialog) {
        NewChatOrGroupDialog(
            onCreateDirect = { name ->
                showNewChatDialog = false
                vm.createDirectChat(name) { onOpenThread(it.id) }
            },
            onCreateGroup = { title, members ->
                showNewChatDialog = false
                vm.createGroupChat(title, members) { onOpenThread(it.id) }
            },
            onDismiss = { showNewChatDialog = false }
        )
    }

    pendingAuthTarget?.let { action ->
        PinAuthDialog(
            title = "Protected Chat Access",
            subtitle = "Authenticate with PIN or Biometric to continue",
            onDismiss = { pendingAuthTarget = null },
            onSuccess = {
                val runnable = action
                pendingAuthTarget = null
                runnable()
            }
        )
    }

    if (showPinSetupForVault) {
        PinSetupDialog(
            onDismiss = { showPinSetupForVault = false },
            onPinSet = {
                showPinSetupForVault = false
                vm.setVaultUnlocked(true)
                Toast.makeText(context, "Vault PIN configured", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun NearChatRadarCard(
    isScanning: Boolean,
    radarStatus: String,
    onScanToggle: () -> Unit
) {
    Card(
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArworaTonalIconBadge(
                icon = Icons.Default.WifiTethering,
                tint = if (isScanning) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Near Chat (Offline P2P)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = radarStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onScanToggle,
                shape = ArworaDesignTokens.PillShape,
                modifier = Modifier.testTag("near_chat_scan_btn")
            ) {
                Text(
                    text = if (isScanning) "Stop" else "Scan Nearby",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun NearPeerCard(
    peer: NearChatPeer,
    onConnectClick: () -> Unit
) {
    Card(
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp()),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("near_peer_card_${peer.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArworaTonalIconBadge(
                icon = when (peer.source) {
                    NearDiscoverySource.BLUETOOTH_LE -> Icons.Default.BluetoothSearching
                    NearDiscoverySource.WIFI_DIRECT -> Icons.Default.WifiTethering
                    NearDiscoverySource.WIFI_NSD -> Icons.Default.Radar
                },
                tint = if (peer.isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(peer.source.label)
                        peer.hostIp?.let { append(" • $it") }
                        peer.rssiDbm?.let { append(" • ${it}dBm") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onConnectClick,
                shape = ArworaDesignTokens.PillShape,
                modifier = Modifier.testTag("near_peer_connect_${peer.id}")
            ) {
                Text(
                    text = if (peer.isConnected) "Open Chat" else "Connect",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ChatThreadListCard(
    thread: ChatThread,
    onClick: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleMute: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Card(
        onClick = onClick,
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp()),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chat_thread_card_${thread.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.size(46.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (thread.isGroup) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "Group Chat",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = thread.title.take(1).uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (thread.isOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = thread.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (thread.isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (thread.isMuted) {
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = "Muted",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = when {
                        thread.isTyping -> "typing..."
                        thread.isLocked -> "Locked conversation • Tap to unlock"
                        thread.lastMessagePreview.isNotBlank() -> thread.lastMessagePreview
                        thread.isGroup -> "${thread.participantNames.size} members"
                        else -> "Tap to start chatting"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (thread.isTyping) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (thread.isTyping) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = timeFormat.format(Date(thread.lastMessageTimeMillis)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (thread.unreadCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = thread.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("chat_thread_menu_${thread.id}")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Thread Options", modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (thread.isLocked) "Unlock Chat" else "Lock Chat") },
                                leadingIcon = { Icon(Icons.Default.Lock, null) },
                                onClick = {
                                    menuExpanded = false
                                    onToggleLock()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (thread.isHidden) "Unhide from Vault" else "Hide to Vault") },
                                leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
                                onClick = {
                                    menuExpanded = false
                                    onToggleHidden()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (thread.isArchived) "Unarchive" else "Archive") },
                                leadingIcon = { Icon(Icons.Default.Archive, null) },
                                onClick = {
                                    menuExpanded = false
                                    onToggleArchive()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (thread.isMuted) "Unmute" else "Mute") },
                                leadingIcon = { Icon(Icons.Default.NotificationsOff, null) },
                                onClick = {
                                    menuExpanded = false
                                    onToggleMute()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Chat", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatEmptyCard(title: String, subtitle: String) {
    Card(
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp()),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Forum,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
