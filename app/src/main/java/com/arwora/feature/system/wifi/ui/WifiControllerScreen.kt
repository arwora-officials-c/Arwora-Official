package com.arwora.feature.system.wifi.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arwora.feature.system.wifi.DeviceInfo
import com.arwora.feature.system.wifi.WifiControllerViewModel
import com.arwora.feature.system.wifi.WifiSectionTab
import com.arwora.feature.system.wifi.resolve.MulticastHelper
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiControllerScreen(
    onBack: () -> Unit,
    viewModel: WifiControllerViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val networkInfo by viewModel.networkInfo.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanMode by viewModel.scanMode.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val statusText by viewModel.statusText.collectAsStateWithLifecycle()
    val speedTestState by viewModel.speedTestState.collectAsStateWithLifecycle()

    val isSpeedTesting = remember(speedTestState.phase) {
        speedTestState.phase != com.arwora.feature.system.wifi.speedtest.SpeedTestPhase.IDLE && 
        speedTestState.phase != com.arwora.feature.system.wifi.speedtest.SpeedTestPhase.COMPLETED
    }

    // Store & diagnostics states
    val speedHistory by viewModel.wifiStore.speedHistory.collectAsStateWithLifecycle()
    val blockedMacs by viewModel.wifiStore.blockedMacs.collectAsStateWithLifecycle()
    val signalInfo by viewModel.signalInfo.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val recommendedChannel by viewModel.recommendedChannel.collectAsStateWithLifecycle()
    val gatewayPingResult by viewModel.gatewayPingResult.collectAsStateWithLifecycle()
    val isPingingGateway by viewModel.isPingingGateway.collectAsStateWithLifecycle()
    val scannedNetworks by viewModel.scannedNetworks.collectAsStateWithLifecycle()
    val isScanningNetworks by viewModel.isScanningNetworks.collectAsStateWithLifecycle()

    var selectedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var editingDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var currentTab by remember { mutableStateOf(WifiSectionTab.DEVICES) }

    var hasPermissions by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (hasPermissions) viewModel.refreshNetworkInfo()
    }

    LaunchedEffect(viewModel.snackbarMessage) {
        viewModel.snackbarMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(Unit) {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = perms.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing) {
            permissionLauncher.launch(perms)
        } else {
            hasPermissions = true
            viewModel.refreshNetworkInfo()
            // Automatically start scan on open as per requirements
            viewModel.startScan()
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab == WifiSectionTab.SCANNER) {
            viewModel.startPeriodicWifiScan()
        } else {
            viewModel.stopPeriodicWifiScan()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopScan()
            viewModel.stopPeriodicWifiScan()
            MulticastHelper.releaseMdnsLock()
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Copied $label: $text")
        }
    }

    val isConnected = networkInfo.isConnected
    val statusColor = if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.error

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp), // Reduced vertical padding
                    verticalAlignment = Alignment.CenterVertically // Changed from Top to CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Return",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp) // Adjusted padding
                    ) {
                        Text(
                            text = "Wi‑Fi Controller",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Manage devices, speed, signal, and router health",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. HERO OVERVIEW CARD
            item(key = "hero_overview") {
                HeroOverviewCard(
                    networkInfo = networkInfo,
                    signalInfo = signalInfo,
                    deviceCount = devices.size,
                    statusColor = statusColor
                )
            }

            // 2. QUICK ACTION ROW
            item(key = "quick_actions") {
                QuickActionRow(
                    isScanning = isScanning,
                    isSpeedTesting = isSpeedTesting,
                    onScan = { 
                        SoundEffectManager.playClick()
                        if (isScanning) viewModel.stopScan() else viewModel.startScan() 
                    },
                    onSpeedTest = {
                        SoundEffectManager.playClick()
                        viewModel.startSpeedTest()
                    },
                    onRefresh = {
                        SoundEffectManager.playClick()
                        viewModel.refreshNetworkInfo()
                    }
                )
            }

            // 3. TABS (Pills)
            item(key = "tabs") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WifiSectionTab.entries.filter { it != WifiSectionTab.ALL && it != WifiSectionTab.OVERVIEW }.forEach { tab ->
                        val selected = currentTab == tab
                        Surface(
                            onClick = {
                                SoundEffectManager.playClick()
                                currentTab = tab
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        ) {
                            Text(
                                text = tab.label,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }

            // 4. MAIN CONTENT SECTIONS
            when (currentTab) {
                WifiSectionTab.DEVICES -> {
                    item { SectionHeader("Connected Devices", "Active hosts on subnet") }
                    
                    if (isScanning) {
                        item {
                            LinearProgressIndicator(
                                progress = { scanProgress?.let { it.first.toFloat() / it.second.toFloat() } ?: 0f },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    if (devices.isEmpty() && !isScanning) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Devices,
                                title = "No Devices Discovered",
                                subtitle = statusText
                            )
                        }
                    } else {
                        items(
                            items = devices,
                            key = { "device_${it.mac}_${it.ip}" }
                        ) { device ->
                            ModernDeviceListCard(
                                device = device,
                                hostIp = networkInfo.localIp,
                                onEditName = { editingDevice = device },
                                onPing = { viewModel.pingDevice(device.ip) },
                                onCopy = { text, label -> copyToClipboard(label, text) }
                            )
                        }
                    }
                }

                WifiSectionTab.SPEED_TEST -> {
                    item {
                        SpeedTestCard(
                            speedTestState = speedTestState,
                            networkInfo = networkInfo,
                            onStartTest = { viewModel.startSpeedTest() },
                            onCancelTest = { viewModel.cancelSpeedTest() }
                        )
                    }
                }

                WifiSectionTab.SPEED_HISTORY -> {
                    item {
                        SpeedHistoryCard(
                            history = speedHistory,
                            onClearHistory = { viewModel.wifiStore.clearSpeedHistory() },
                            onDeleteRecord = { id -> viewModel.wifiStore.deleteSpeedRecord(id) }
                        )
                    }
                }

                WifiSectionTab.SIGNAL_CHANNEL -> {
                    item {
                        SignalAndChannelCard(
                            signalInfo = signalInfo,
                            channels = channels,
                            recommendedChannel = recommendedChannel,
                            linkSpeedMbps = networkInfo.linkSpeedMbps,
                            onRefresh = { viewModel.refreshSignalAndChannels() }
                        )
                    }
                }

                WifiSectionTab.SCANNER -> {
                    item {
                        WifiScannerCard(
                            networks = scannedNetworks,
                            isScanning = isScanningNetworks,
                            onStartScan = { viewModel.scanWifiNetworks() },
                            onConnect = { ssid, psk, isWps ->
                                viewModel.connectToWifi(ssid, psk, isWps)
                            }
                        )
                    }
                }

                else -> {}
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // Detail Modal Sheet
    if (selectedDevice != null) {
        val device = selectedDevice!!
        ModalBottomSheet(
            onDismissRequest = { selectedDevice = null },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 48.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = device.customLabel ?: device.friendlyName ?: device.hostname ?: "Unknown Device",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(text = device.vendor, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(24.dp))
                InfoRow("IP Address", device.ip)
                InfoRow("MAC Address", device.mac)
                device.latencyMs?.let { InfoRow("Latency", "$it ms") }
            }
        }
    }

    editingDevice?.let { dev ->
        var tempName by remember(dev.ip) { mutableStateOf(dev.customLabel ?: dev.friendlyName ?: "") }

        AlertDialog(
            onDismissRequest = { editingDevice = null },
            title = {
                Text(
                    text = "Set Custom Device Label",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Assign an easy-to-scan friendly name for ${dev.ip} (${dev.vendor.ifBlank { "Host" }}):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        placeholder = { Text("e.g. Phone or Office TV") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val labelKey = dev.mac.takeIf { it != "Unknown" && it.isNotBlank() } ?: dev.ip
                    viewModel.saveCustomDeviceName(labelKey, tempName)
                    editingDevice = null
                }) {
                    Text("Save Label")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDevice = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HeroOverviewCard(
    networkInfo: com.arwora.feature.system.wifi.NetworkInfo,
    signalInfo: com.arwora.feature.system.wifi.WifiSignalInfo,
    deviceCount: Int,
    statusColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(statusColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (networkInfo.isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (networkInfo.isConnected) networkInfo.ssid else "Disconnected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (networkInfo.isConnected) "Connected • ${networkInfo.gateway}" else "No active network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = if (networkInfo.isConnected) "ONLINE" else "OFFLINE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = statusColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroMetric(Icons.Default.Devices, "Devices", "$deviceCount", Color(0xFF3B82F6))
                HeroMetric(Icons.Default.Refresh, "Speed", "${networkInfo.linkSpeedMbps} Mbps", Color(0xFF10B981))
                HeroMetric(Icons.Default.Wifi, "Signal", "${signalInfo.rssiDbm} dBm", Color(0xFFF59E0B))
                HeroMetric(Icons.Default.Router, "Gateway", if (networkInfo.gateway.isNotBlank()) "Stable" else "Error", Color(0xFF8B5CF6))
            }
        }
    }
}

@Composable
private fun HeroMetric(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickActionRow(
    isScanning: Boolean,
    isSpeedTesting: Boolean,
    onScan: () -> Unit,
    onSpeedTest: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Search,
            label = if (isScanning) "Stop Scan" else "Scan Net",
            color = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            onClick = onScan
        )
        QuickActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Refresh,
            label = "Speed Test",
            color = if (isSpeedTesting) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            onClick = onSpeedTest
        )
        QuickActionButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Refresh,
            label = "Refresh",
            color = MaterialTheme.colorScheme.primary,
            onClick = onRefresh
        )
    }
}

@Composable
private fun QuickActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyStateCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
