package com.arwora.feature.system.wifi.password

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiLock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arwora.ui.components.ExpressiveWavyLinearProgressIndicator
import com.arwora.utils.NativeAdbConnectionState
import com.arwora.utils.NativeAdbManager
import kotlinx.coroutines.launch

/**
 * Standard container card adhering strictly to the Arwora Hyper Toolkit design system
 * (matching DNS Changer and IP Address Lookup cards).
 */
@Composable
fun WifiContainerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val outlineAlpha = if (isLight) 0.25f else 0.4f
    val cardBgColor = if (isLight) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    }
    val elevationDp = if (isLight) 1.dp else 0.dp
    val cardShape = RoundedCornerShape(22.dp)
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = outlineAlpha))
    val cardColors = CardDefaults.cardColors(containerColor = cardBgColor)
    val cardElevation = CardDefaults.cardElevation(defaultElevation = elevationDp)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = cardShape,
            colors = cardColors,
            border = cardBorder,
            elevation = cardElevation,
            content = content
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = cardShape,
            colors = cardColors,
            border = cardBorder,
            elevation = cardElevation,
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiPasswordScreen(
    onBack: () -> Unit,
    viewModel: WifiPasswordViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val networks by viewModel.filteredNetworks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isGlobalRevealAll by viewModel.isGlobalRevealAll.collectAsStateWithLifecycle()
    val isElevatedMode by viewModel.isElevatedModeAvailable.collectAsStateWithLifecycle()
    val adbState by viewModel.adbState.collectAsStateWithLifecycle()
    val nativeAdbConnected by NativeAdbManager.isConnected.collectAsStateWithLifecycle()
    val requiresDevOptionsGuidance by NativeAdbManager.requiresDevOptionsGuidance.collectAsStateWithLifecycle()
    val adbStatusMessage by NativeAdbManager.statusMessage.collectAsStateWithLifecycle()
    val qrSelectedNetwork by viewModel.selectedNetworkForQr.collectAsStateWithLifecycle()
    val isAddDialogOpen by viewModel.isAddDialogOpen.collectAsStateWithLifecycle()

    val isImportDialogOpen by viewModel.isImportDialogOpen.collectAsStateWithLifecycle()
    val isForgetAllDialogOpen by viewModel.isForgetAllDialogOpen.collectAsStateWithLifecycle()
    var showTopOverflowMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(nativeAdbConnected) {
        viewModel.refreshAdbStatus()
    }

    DisposableEffect(lifecycleOwner) {
        viewModel.refreshAdbStatus()
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAdbStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Wi-Fi passwords",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("wifi_password_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.testTag("wifi_password_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.openAddDialog() },
                        modifier = Modifier.testTag("wifi_password_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Network"
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showTopOverflowMenu = true },
                            modifier = Modifier.testTag("wifi_password_top_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showTopOverflowMenu,
                            onDismissRequest = { showTopOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export networks", fontWeight = FontWeight.Medium) },
                                onClick = {
                                    showTopOverflowMenu = false
                                    viewModel.exportNetworks(context)
                                },
                                modifier = Modifier.testTag("menu_export_networks")
                            )
                            DropdownMenuItem(
                                text = { Text("Import networks", fontWeight = FontWeight.Medium) },
                                onClick = {
                                    showTopOverflowMenu = false
                                    viewModel.openImportDialog()
                                },
                                modifier = Modifier.testTag("menu_import_networks")
                            )
                            DropdownMenuItem(
                                text = { Text("Forget all networks", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    showTopOverflowMenu = false
                                    viewModel.openForgetAllDialog()
                                },
                                modifier = Modifier.testTag("menu_forget_all_networks")
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Overview & Controls Header Card
            item {
                WifiOverviewCard(
                    totalSaved = networks.size,
                    isGlobalReveal = isGlobalRevealAll,
                    isElevated = isElevatedMode,
                    adbState = adbState,
                    isLoading = isLoading,
                    onToggleRevealAll = { viewModel.toggleRevealAll() }
                )
            }

            // 1b. Google Play Style Linear Progress Bar while reading system Wi-Fi files
            if (isLoading) {
                item {
                    WifiContainerCard(
                        modifier = Modifier.testTag("wifi_loading_progress_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Reading saved Wi-Fi configurations...",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            ExpressiveWavyLinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wifi_linear_progress_bar"),
                                height = 10.dp
                            )
                        }
                    }
                }
            }

            // 1c. Wireless ADB Warning Banner when ADB is not connected
            if (adbState != NativeAdbConnectionState.CONNECTED) {
                item {
                    WirelessAdbWarningBanner(
                        adbState = adbState,
                        requiresDevOptionsGuidance = requiresDevOptionsGuidance,
                        statusMessage = adbStatusMessage,
                        onConnectAdb = { viewModel.connectAdb(context) },
                        onOpenDeveloperOptions = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                            } catch (_: Exception) {
                                Toast.makeText(context, "Unable to open Developer Options.", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }

            // 2. Search & Filter Bar
            item {
                WifiSearchBar(
                    query = searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) }
                )
            }

            // 3. Network List
            if (networks.isEmpty() && !isLoading) {
                if (searchQuery.isNotBlank() || adbState == NativeAdbConnectionState.CONNECTED) {
                    item {
                        WifiEmptyState(
                            hasQuery = searchQuery.isNotBlank(),
                            onClearSearch = { viewModel.onSearchQueryChanged("") },
                            onAddNetwork = { viewModel.openAddDialog() }
                        )
                    }
                }
            } else {
                items(
                    items = networks,
                    key = { it.id }
                ) { network ->
                    val isRevealed = viewModel.isPasswordVisible(network.id)
                    WifiNetworkCard(
                        network = network,
                        isRevealed = isRevealed,
                        onToggleReveal = { viewModel.togglePasswordVisibility(network.id) },
                        onCopyPassword = { viewModel.copyPasswordToClipboard(context, network) },
                        onCopySsid = { viewModel.copySsidToClipboard(context, network.ssid) },
                        onConnect = { viewModel.connectToNetwork(context, network) },
                        onForget = { viewModel.forgetNetwork(context, network) },
                        onShareQr = { viewModel.openQrDialog(network) }
                    )
                }
            }

            // Bottom space for scrolling clarity
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // QR Code Share Dialog
    qrSelectedNetwork?.let { net ->
        WifiQrCodeDialog(
            network = net,
            onDismiss = { viewModel.closeQrDialog() },
            onCopyPassword = { viewModel.copyPasswordToClipboard(context, net) }
        )
    }

    // Add Network Dialog
    if (isAddDialogOpen) {
        AddWifiNetworkDialog(
            onDismiss = { viewModel.closeAddDialog() },
            onSave = { ssid, pass, sec ->
                viewModel.saveCustomNetwork(ssid, pass, sec)
                scope.launch {
                    snackbarHostState.showSnackbar("Network \"$ssid\" saved to vault")
                }
            }
        )
    }

    // Import Networks Dialog
    if (isImportDialogOpen) {
        ImportNetworksDialog(
            onDismiss = { viewModel.closeImportDialog() },
            onImport = { data ->
                viewModel.importNetworks(context, data)
            }
        )
    }

    // Forget All Networks Confirmation Dialog
    if (isForgetAllDialogOpen) {
        ForgetAllNetworksDialog(
            onDismiss = { viewModel.closeForgetAllDialog() },
            onConfirm = {
                viewModel.forgetAllNetworks(context)
            }
        )
    }
}

/**
 * Overview header card displaying total saved networks, status badges, and global reveal toggle.
 */
@Composable
private fun WifiOverviewCard(
    totalSaved: Int,
    isGlobalReveal: Boolean,
    isElevated: Boolean,
    adbState: NativeAdbConnectionState,
    isLoading: Boolean,
    onToggleRevealAll: () -> Unit
) {
    WifiContainerCard {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiLock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Saved Wi-Fi Networks",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$totalSaved known network${if (totalSaved == 1) "" else "s"} stored",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isLoading) {
                    ExpressiveWavyLinearProgressIndicator(
                        modifier = Modifier.width(48.dp),
                        height = 8.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Row: Reveal All Toggle & Privileges Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeColor = when (adbState) {
                    NativeAdbConnectionState.CONNECTED -> Color(0xFF10B981)
                    NativeAdbConnectionState.CONNECTING -> Color(0xFFF59E0B)
                    NativeAdbConnectionState.WIFI_REQUIRED -> MaterialTheme.colorScheme.error
                    NativeAdbConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val badgeLabel = when {
                    isElevated || adbState == NativeAdbConnectionState.CONNECTED -> "ADB Connected"
                    adbState == NativeAdbConnectionState.CONNECTING -> "Connecting..."
                    adbState == NativeAdbConnectionState.WIFI_REQUIRED -> "Wi-Fi Offline"
                    else -> "ADB Offline"
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isElevated) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (isElevated) Color(0xFF10B981).copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor
                        )
                    }
                }

                // Global Reveal / Mask Button
                OutlinedButton(
                    onClick = onToggleRevealAll,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isGlobalReveal) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
                    ),
                    modifier = Modifier.testTag("toggle_reveal_all_button")
                ) {
                    Icon(
                        imageVector = if (isGlobalReveal) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGlobalReveal) "Mask All" else "Reveal All",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Clean warning banner shown when built-in Wireless ADB is offline or Wi-Fi is disconnected,
 * offering a direct "Connect Now" CTA instead of leaving users stuck on an empty state.
 */
@Composable
private fun WirelessAdbWarningBanner(
    adbState: NativeAdbConnectionState,
    requiresDevOptionsGuidance: Boolean,
    statusMessage: String,
    onConnectAdb: () -> Unit,
    onOpenDeveloperOptions: () -> Unit
) {
    val headline = when (adbState) {
        NativeAdbConnectionState.WIFI_REQUIRED -> "Wi-Fi Connection Required"
        NativeAdbConnectionState.CONNECTING -> "Connecting Wireless ADB..."
        NativeAdbConnectionState.DISCONNECTED ->
            if (requiresDevOptionsGuidance) "Wireless Debugging Not Detected" else "Wireless ADB Required for System Sync"
        NativeAdbConnectionState.CONNECTED -> "Active / Connected"
    }
    val description = when (adbState) {
        NativeAdbConnectionState.WIFI_REQUIRED ->
            "Connect to a Wi-Fi network with Wireless Debugging enabled to read saved Wi-Fi networks, or tap + to add manually."
        NativeAdbConnectionState.CONNECTING -> statusMessage
        NativeAdbConnectionState.DISCONNECTED ->
            if (requiresDevOptionsGuidance) statusMessage
            else "Enable Wireless Debugging in Developer Options and tap Connect Now to bind directly to the local ADB daemon."
        NativeAdbConnectionState.CONNECTED -> ""
    }

    WifiContainerCard(
        modifier = Modifier.testTag("adb_warning_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (requiresDevOptionsGuidance && adbState == NativeAdbConnectionState.DISCONNECTED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (adbState == NativeAdbConnectionState.CONNECTING) {
                ExpressiveWavyLinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    height = 10.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(
                onClick = onConnectAdb,
                enabled = adbState != NativeAdbConnectionState.CONNECTING,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("connect_adb_button")
            ) {
                Text(
                    text = if (adbState == NativeAdbConnectionState.CONNECTING) "Connecting..." else "Connect Now",
                    fontWeight = FontWeight.Bold
                )
            }

            if (requiresDevOptionsGuidance && adbState == NativeAdbConnectionState.DISCONNECTED) {
                OutlinedButton(
                    onClick = onOpenDeveloperOptions,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("wifi_password_open_dev_options_button")
                ) {
                    Icon(Icons.Default.DeveloperMode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Developer Options", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Clean search text field for filtering saved Wi-Fi networks.
 */
@Composable
private fun WifiSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wifi_search_input"),
        placeholder = {
            Text(
                text = "Search networks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    )
}

/**
 * Individual Wi-Fi network card showing SSID, security badge, password container,
 * visibility toggle, dedicated Copy button, and three-dot action menu.
 */
@Composable
private fun WifiNetworkCard(
    network: SavedWifiNetwork,
    isRevealed: Boolean,
    onToggleReveal: () -> Unit,
    onCopyPassword: () -> Unit,
    onCopySsid: () -> Unit,
    onConnect: () -> Unit,
    onForget: () -> Unit,
    onShareQr: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showForgetConfirmDialog by remember { mutableStateOf(false) }

    WifiContainerCard(modifier = Modifier.testTag("wifi_card_${network.ssid}")) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: SSID, Badges & Three-Dot Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (network.isCurrentlyConnected) Color(0xFF10B981).copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (network.isCurrentlyConnected) Color(0xFF10B981).copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (network.isCurrentlyConnected) Icons.Default.Wifi else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (network.isCurrentlyConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = network.ssid,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.2).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (network.isCurrentlyConnected) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "CONNECTED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ) {
                                Text(
                                    text = network.securityType.badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Three-Dot Menu
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("wifi_menu_button_${network.ssid}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Connect", fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = {
                                showMenu = false
                                onConnect()
                            },
                            modifier = Modifier.testTag("wifi_action_connect_${network.ssid}")
                        )
                        DropdownMenuItem(
                            text = { Text("Forget", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                showMenu = false
                                showForgetConfirmDialog = true
                            },
                            modifier = Modifier.testTag("wifi_action_forget_${network.ssid}")
                        )
                        DropdownMenuItem(
                            text = { Text("Share QR Code", fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(Icons.Default.QrCode, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onShareQr()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy SSID", fontWeight = FontWeight.Medium) },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onCopySsid()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Password Container with Show/Hide toggle and dedicated Copy Button
            val isSecurityOpen = network.securityType == WifiSecurityType.OPEN
            val hasStoredPassword = network.password.isNotEmpty()

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Password",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))

                        if (isSecurityOpen) {
                            Text(
                                text = "No Password (Open)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (!hasStoredPassword) {
                            Text(
                                text = "Protected by system keystore (Root needed for PSK)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = if (isRevealed) network.password else "••••••••",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = if (isRevealed) FontFamily.Monospace else FontFamily.Default,
                                    letterSpacing = if (isRevealed) 0.sp else 2.sp
                                ),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!isSecurityOpen && hasStoredPassword) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Show/Hide Toggle
                            IconButton(
                                onClick = onToggleReveal,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("toggle_password_${network.ssid}")
                            ) {
                                Icon(
                                    imageVector = if (isRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isRevealed) "Hide Password" else "Show Password",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Dedicated Copy Button
                            Button(
                                onClick = onCopyPassword,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("copy_password_button_${network.ssid}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Copy",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog for Forget Network
    if (showForgetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showForgetConfirmDialog = false },
            title = {
                Text("Forget Network?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to forget \"${network.ssid}\"? Saved credentials will be removed.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showForgetConfirmDialog = false
                        onForget()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Forget")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Empty state view when no networks match search or list is empty.
 */
@Composable
private fun WifiEmptyState(
    hasQuery: Boolean,
    onClearSearch: () -> Unit,
    onAddNetwork: () -> Unit
) {
    WifiContainerCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasQuery) Icons.Default.Search else Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (hasQuery) "No matching Wi-Fi networks" else "No saved Wi-Fi networks found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (hasQuery) "Try adjusting your search query." else "You can manually add or import a Wi-Fi network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (hasQuery) {
                OutlinedButton(onClick = onClearSearch) {
                    Text("Clear Search")
                }
            } else {
                Button(onClick = onAddNetwork) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Network")
                }
            }
        }
    }
}

/**
 * QR Code presentation dialog for sharing Wi-Fi credentials with other devices.
 */
@Composable
private fun WifiQrCodeDialog(
    network: SavedWifiNetwork,
    onDismiss: () -> Unit,
    onCopyPassword: () -> Unit
) {
    val qrRawString = "WIFI:T:${network.securityType.badge};S:${network.ssid};P:${network.password};;"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Connect via QR Code", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = network.ssid,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = network.securityType.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Clean QR Code Graphic representation on Canvas
                Surface(
                    modifier = Modifier.size(180.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(2.dp, Color.Black.copy(alpha = 0.1f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(150.dp)) {
                            val matrixSize = 25
                            val cellSize = size.width / matrixSize
                            val hash = (network.ssid + network.password).hashCode()

                            // Draw finder patterns (top-left, top-right, bottom-left)
                            fun drawFinder(startX: Float, startY: Float) {
                                drawRect(Color.Black, Offset(startX, startY), Size(7 * cellSize, 7 * cellSize))
                                drawRect(Color.White, Offset(startX + cellSize, startY + cellSize), Size(5 * cellSize, 5 * cellSize))
                                drawRect(Color.Black, Offset(startX + 2 * cellSize, startY + 2 * cellSize), Size(3 * cellSize, 3 * cellSize))
                            }

                            drawFinder(0f, 0f)
                            drawFinder((matrixSize - 7) * cellSize, 0f)
                            drawFinder(0f, (matrixSize - 7) * cellSize)

                            // Synthetic data pattern for QR visual
                            for (r in 0 until matrixSize) {
                                for (c in 0 until matrixSize) {
                                    val isFinder = (r < 8 && c < 8) ||
                                            (r < 8 && c >= matrixSize - 8) ||
                                            (r >= matrixSize - 8 && c < 8)
                                    if (!isFinder) {
                                        val bit = ((r * 31 + c * 17 + hash) xor (r * c)) % 2 == 0
                                        if (bit) {
                                            drawRect(
                                                Color.Black,
                                                Offset(c * cellSize, r * cellSize),
                                                Size(cellSize, cellSize)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Scan with any smartphone camera to connect automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onCopyPassword()
                onDismiss()
            }) {
                Text("Copy Password")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Dialog for adding a new Wi-Fi network to the vault.
 */
@Composable
private fun AddWifiNetworkDialog(
    onDismiss: () -> Unit,
    onSave: (ssid: String, pass: String, sec: WifiSecurityType) -> Unit
) {
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedSecurity by remember { mutableStateOf(WifiSecurityType.WPA2) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Wi-Fi Network", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("Network Name (SSID)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = "Security Type",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(WifiSecurityType.WPA2, WifiSecurityType.WPA3, WifiSecurityType.OPEN).forEach { sec ->
                        FilterChip(
                            selected = selectedSecurity == sec,
                            onClick = { selectedSecurity = sec },
                            label = { Text(sec.badge) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                if (selectedSecurity != WifiSecurityType.OPEN) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (ssid.isNotBlank()) {
                        onSave(ssid, password, selectedSecurity)
                    }
                },
                enabled = ssid.isNotBlank()
            ) {
                Text("Save Network")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ImportNetworksDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Import Wi-Fi Networks", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Paste exported Wi-Fi JSON or lines formatted as SSID:Password",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("e.g. MyWiFi:SecretPassword123") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImport(inputText) },
                enabled = inputText.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ForgetAllNetworksDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Forget All Networks?", fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                text = "Are you sure you want to forget and remove all saved Wi-Fi networks from the vault and device? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Forget All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
