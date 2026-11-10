package com.arwora.feature.system.wifi

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arwora.feature.system.wifi.resolve.MulticastHelper
import com.arwora.feature.system.wifi.ui.*
import com.arwora.ui.screens.SystemFeatureCard
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class WifiSectionTab(val label: String) {
    ALL("All"),
    OVERVIEW("Overview"),
    DEVICES("Devices"),
    SPEED_TEST("Speed Test"),
    SPEED_HISTORY("History"),
    SIGNAL_CHANNEL("Signal & Channel"),
    SCANNER("Scanner"),
    ALERTS("Alerts")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiControllerCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    viewModel: WifiControllerViewModel = viewModel()
) {
    val networkInfo by viewModel.networkInfo.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanMode by viewModel.scanMode.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val statusText by viewModel.statusText.collectAsStateWithLifecycle()
    val isStale by viewModel.isStale.collectAsStateWithLifecycle()
    val speedTestState by viewModel.speedTestState.collectAsStateWithLifecycle()

    val isSpeedTesting = remember(speedTestState.phase) {
        speedTestState.phase != com.arwora.feature.system.wifi.speedtest.SpeedTestPhase.IDLE && 
        speedTestState.phase != com.arwora.feature.system.wifi.speedtest.SpeedTestPhase.COMPLETED
    }

    // Store & diagnostics states
    val alerts by viewModel.wifiStore.alerts.collectAsStateWithLifecycle()
    val alertsEnabled by viewModel.wifiStore.alertsEnabled.collectAsStateWithLifecycle()
    val customLabels by viewModel.wifiStore.customLabels.collectAsStateWithLifecycle()
    val speedHistory by viewModel.wifiStore.speedHistory.collectAsStateWithLifecycle()
    val blockedMacs by viewModel.wifiStore.blockedMacs.collectAsStateWithLifecycle()
    val signalInfo by viewModel.signalInfo.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val recommendedChannel by viewModel.recommendedChannel.collectAsStateWithLifecycle()
    val gatewayPingResult by viewModel.gatewayPingResult.collectAsStateWithLifecycle()
    val isPingingGateway by viewModel.isPingingGateway.collectAsStateWithLifecycle()
    val scannedNetworks by viewModel.scannedNetworks.collectAsStateWithLifecycle()
    val isScanningNetworks by viewModel.isScanningNetworks.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var selectedDevice by remember { mutableStateOf<DeviceInfo?>(null) }
    var currentTab by remember { mutableStateOf(WifiSectionTab.ALL) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

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

    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            }
            val missing = perms.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
            if (missing) {
                permissionLauncher.launch(perms)
            } else {
                hasPermissions = true
                viewModel.refreshNetworkInfo()
            }
        } else {
            viewModel.stopScan()
            MulticastHelper.releaseMdnsLock()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopScan()
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

    fun exportDevicesCsv() {
        val csvData = buildString {
            appendLine("IP,MAC,Vendor,CustomLabel,FriendlyName,Latency")
            devices.forEach {
                appendLine("${it.ip},${it.mac},${it.vendor},${it.customLabel ?: ""},${it.friendlyName ?: ""},${it.latencyMs}")
            }
        }
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, csvData)
            type = "text/csv"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Export Devices CSV"))
    }

    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val outlineAlpha = if (isLight) 0.25f else 0.45f

    SystemFeatureCard(
        title = "Wi-Fi Controller",
        subtitle = if (networkInfo.isConnected) "${networkInfo.ssid} • ${networkInfo.gateway} • ${devices.size} Hosts" else "Not connected to Wi-Fi",
        icon = if (networkInfo.isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
        badge = if (networkInfo.isConnected) "Connected" else "No Wi-Fi",
        badgeColor = if (networkInfo.isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
        isExpanded = isExpanded,
        onToggleExpand = onToggleExpand
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Top Disconnected State
            if (!networkInfo.isConnected) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = outlineAlpha))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Wi-Fi Disconnected",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Connect your Android device to a local Wi-Fi network to scan subnet hosts, benchmark speed, analyze RF channels, and inspect default gateways.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Open Wi-Fi Settings", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            } else {
                // Permission Banner if Location is Missing
                if (networkInfo.ssid == "<unknown ssid>" || !hasPermissions) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Location permission is required by Android to read the Wi-Fi SSID and discover subnet devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Open Location Settings", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Stale Cache Banner
                if (isStale) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "Cached results shown • Tap Scan to refresh live hosts on subnet",
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                // ==========================================
                // 1. TOP OVERVIEW CARD
                // ==========================================
                if (currentTab == WifiSectionTab.ALL || currentTab == WifiSectionTab.OVERVIEW) {
                    WifiOverviewCard(
                        networkInfo = networkInfo,
                        signalInfo = signalInfo,
                        deviceCount = devices.size,
                        onRefresh = { viewModel.refreshNetworkInfo() },
                        onExportCsv = { exportDevicesCsv() }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // QUICK SECTION NAVIGATION FILTER BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WifiSectionTab.entries.forEach { tab ->
                        FilterChip(
                            selected = currentTab == tab,
                            onClick = {
                                SoundEffectManager.playClick()
                                currentTab = tab
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // 2. CONNECTED DEVICES
                // ==========================================
                if (currentTab == WifiSectionTab.ALL || currentTab == WifiSectionTab.DEVICES) {
                    ConnectedDevicesCard(
                        devices = devices,
                        isScanning = isScanning,
                        scanMode = scanMode,
                        scanProgress = scanProgress?.let { it.first.toFloat() / it.second.toFloat() } ?: 0f,
                        statusText = statusText,
                        onSetScanMode = { mode -> viewModel.setScanMode(mode) },
                        onStartScan = {
                            SoundEffectManager.playClick()
                            viewModel.startScan()
                        },
                        onStopScan = {
                            SoundEffectManager.playClick()
                            viewModel.stopScan()
                        },
                        onSaveCustomName = { mac, name ->
                            viewModel.saveCustomDeviceName(mac, name)
                        },
                        onPingDevice = { dev ->
                            viewModel.pingDevice(dev.ip)
                        },
                        onToggleBlock = { dev ->
                            viewModel.toggleBlockDevice(dev)
                        },
                        hostIp = networkInfo.localIp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ==========================================
                // 3. SPEED TEST
                // ==========================================
                if (currentTab == WifiSectionTab.ALL || currentTab == WifiSectionTab.SPEED_TEST) {
                    SpeedTestCard(
                        speedTestState = speedTestState,
                        networkInfo = networkInfo,
                        onStartTest = { viewModel.startSpeedTest() },
                        onCancelTest = { viewModel.cancelSpeedTest() }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ==========================================
                // 4. SPEED HISTORY
                // ==========================================
                if (currentTab == WifiSectionTab.ALL || currentTab == WifiSectionTab.SPEED_HISTORY) {
                    SpeedHistoryCard(
                        history = speedHistory,
                        onClearHistory = { viewModel.wifiStore.clearSpeedHistory() },
                        onDeleteRecord = { id -> viewModel.wifiStore.deleteSpeedRecord(id) }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ==========================================
                // 5. SIGNAL & CHANNEL
                // ==========================================
                if (currentTab == WifiSectionTab.ALL || currentTab == WifiSectionTab.SIGNAL_CHANNEL) {
                    SignalAndChannelCard(
                        signalInfo = signalInfo,
                        channels = channels,
                        recommendedChannel = recommendedChannel,
                        linkSpeedMbps = networkInfo.linkSpeedMbps,
                        onRefresh = { viewModel.refreshSignalAndChannels() }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ==========================================
                // 6. WI-FI SCANNER
                // ==========================================
                if (currentTab == WifiSectionTab.ALL || currentTab == WifiSectionTab.SCANNER) {
                    WifiScannerCard(
                        networks = scannedNetworks,
                        isScanning = isScanningNetworks,
                        onStartScan = { viewModel.scanWifiNetworks() },
                        onConnect = { ssid, psk, isWps ->
                            viewModel.connectToWifi(ssid, psk, isWps)
                        }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ==========================================
                // 8. DEVICE NAMING & ALERTS
                // ==========================================
                if (currentTab == WifiSectionTab.ALL || currentTab == WifiSectionTab.ALERTS) {
                    DeviceNamingAndAlertsCard(
                        alerts = alerts,
                        alertsEnabled = alertsEnabled,
                        onToggleAlerts = { enabled ->
                            viewModel.wifiStore.setAlertsEnabled(enabled)
                        },
                        onClearAlerts = {
                            viewModel.wifiStore.clearAlerts()
                        },
                        customLabels = customLabels,
                        devices = devices,
                        onSaveCustomName = { mac, name ->
                            viewModel.saveCustomDeviceName(mac, name)
                        }
                    )
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // Detail Modal Sheet
    selectedDevice?.let { device ->
        ModalBottomSheet(onDismissRequest = { selectedDevice = null }) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = device.customLabel ?: device.friendlyName ?: device.hostname ?: "${device.vendor} Device",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "IP Address: ${device.ip}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "MAC Address: ${device.mac}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Vendor: ${device.vendor}", style = MaterialTheme.typography.bodyMedium)
                device.latencyMs?.let {
                    Text(text = "Latency: $it ms", style = MaterialTheme.typography.bodyMedium)
                }
                if (device.nameSource != null) {
                    Text(
                        text = "Name Source: ${device.nameSource}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { copyToClipboard("IP", device.ip) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy IP")
                    }
                    OutlinedButton(
                        onClick = { copyToClipboard("MAC", device.mac) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy MAC")
                    }
                    Button(
                        onClick = { viewModel.pingDevice(device.ip) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.NetworkPing, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ping")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
