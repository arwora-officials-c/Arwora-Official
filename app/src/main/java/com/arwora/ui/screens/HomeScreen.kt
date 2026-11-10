package com.arwora.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.arwora.ui.components.AppLogo
import com.arwora.ui.MainViewModel
import com.arwora.ui.navigation.Screen
import com.arwora.utils.HighRiskApp
import com.arwora.utils.SoundEffectManager
import com.arwora.utils.SystemScanResult
import com.arwora.utils.SystemScannerManager

data class CompactFeature(
    val title: String,
    val icon: ImageVector,
    val route: String
)

data class GlobalToolItem(
    val title: String,
    val category: String,
    val description: String,
    val icon: ImageVector,
    val route: String,
    val keywords: List<String> = emptyList()
)

val allGlobalTools = listOf(
    // Security & Privacy
    GlobalToolItem("Deep Security Scan", "Security", "Full malware, privacy, and system integrity diagnostics", Icons.Default.Security, Screen.DeepScanner.route, listOf("virus", "scan", "antivirus", "malware", "privacy")),
    GlobalToolItem("App Locker", "Security", "Protect apps and tools with biometric fingerprint and PIN passcode", Icons.Default.Lock, Screen.AppLocker.route, listOf("lock", "pin", "biometric", "password", "protect")),
    GlobalToolItem("Permission Inspector", "Security", "Audit and inspect dangerous granted runtime permissions", Icons.Default.AdminPanelSettings, Screen.PermissionInspector.route, listOf("permission", "camera", "privacy", "microphone")),
    GlobalToolItem("Notification History", "Privacy", "Log, recover and search through dismissed notification alerts", Icons.Default.Notifications, Screen.NotificationHistory.route, listOf("notification", "history", "alerts", "messages")),
    GlobalToolItem("Image EXIF Metadata Editor", "Privacy", "Inspect, strip, or customize photo GPS location and camera tags", Icons.Default.Image, Screen.ImageMetadataEditor.route, listOf("exif", "metadata", "photo", "location", "camera")),

    // System & Diagnostics
    GlobalToolItem("System Stats & Hardware", "System", "Real-time CPU cores, RAM, GPU, storage and sensor metrics", Icons.Default.Dashboard, Screen.SystemDashboard.route, listOf("system", "stats", "cpu", "ram", "hardware", "specs")),
    GlobalToolItem("App Usage & Wellbeing", "Diagnostics", "Track screen time, daily usage stats & app launch limits", Icons.Default.QueryStats, Screen.AppUsageStats.route, listOf("usage", "wellbeing", "screentime", "stats", "timer")),
    GlobalToolItem("Battery Alarm & Health", "System", "Configurable full charge chime, discharge and thermal warning", Icons.Default.BatteryChargingFull, Screen.BatteryAlarm.route, listOf("battery", "alarm", "charge", "health", "temperature")),
    GlobalToolItem("System Overlay / Floating Window", "System", "Multitasking floating bubble with always-on-top toolbox", Icons.Default.PictureInPictureAlt, Screen.SystemDashboard.route, listOf("floating", "overlay", "pip", "window", "multitask")),
    GlobalToolItem("Animation Speed Tweaker", "System", "Adjust global transition and animation duration scales", Icons.Default.Animation, Screen.AnimationSettings.route, listOf("animation", "speed", "scale", "transition", "fast")),
    GlobalToolItem("App Cloner & Dual Workspace", "System", "Clone installed apps and run multiple parallel accounts", Icons.Default.FilterNone, Screen.AppClone.route, listOf("clone", "dual", "parallel", "multi", "workspace")),
    GlobalToolItem("App Ops Manager", "System", "Advanced permissions, standby buckets, user vs system filtering & package details", Icons.Default.AdminPanelSettings, Screen.AppOps.route, listOf("ops", "permissions", "packages", "apps", "standby", "bucket", "system", "user")),
    GlobalToolItem("App Manager & APK Backup", "System", "Extract installed APK packages and view package components", Icons.Default.Apps, Screen.AppList.route, listOf("apps", "manager", "apk", "backup", "package")),
    GlobalToolItem("App Booster & RAM Cleaner", "System", "One-tap RAM optimization, background process killer & floating widget", Icons.Default.Speed, Screen.AppBooster.route, listOf("booster", "ram", "cleaner", "memory", "boost", "speed", "optimizer")),
    GlobalToolItem("Smart Screen-Off Optimizer", "System", "Automated background cleanup routines on device screen-off events", Icons.Default.Bedtime, Screen.ScreenOffOptimizer.route, listOf("screen", "off", "optimizer", "sleep", "battery", "automation", "cleaner")),
    GlobalToolItem("Battery Monitor & Deep Sleep", "System", "Real-time app consumption stats & CPU wake lock diagnostics", Icons.Default.BatteryChargingFull, Screen.BatteryMonitor.route, listOf("battery", "monitor", "deep", "sleep", "wakelock", "power", "consumption")),
    GlobalToolItem("Shell Terminal Console", "System", "Interactive command prompt for ADB, local shell and scripts", Icons.Default.Terminal, Screen.SystemDashboard.route, listOf("shell", "terminal", "bash", "command", "root")),

    // Network & Wi-Fi
    GlobalToolItem("Private DNS Changer", "Network", "Switch to Cloudflare, Google, AdGuard, NextDNS or custom DoT", Icons.Default.VpnKey, Screen.DnsChanger.route, listOf("dns", "cloudflare", "adguard", "network", "private", "dot")),
    GlobalToolItem("Wi-Fi Controller & Inspector", "Network", "Scan nearby Wi-Fi networks, signal dBm and connected LAN devices", Icons.Default.Wifi, Screen.WifiController.route, listOf("wifi", "lan", "network", "router", "signal")),
    GlobalToolItem("Wi-Fi Password", "Network", "Saved Wi-Fi networks, reveal passwords and quick connect", Icons.Default.Password, Screen.WifiPassword.route, listOf("wifi", "password", "saved", "network", "wlan", "key", "passphrase")),
    GlobalToolItem("Hide Private DNS", "Network", "Restrict system Private DNS for selected apps & games", Icons.Default.VpnLock, Screen.HidePrivateDns.route, listOf("dns", "hide", "private", "dot", "bypass", "games")),
    GlobalToolItem("Network Speed Test", "Network", "Measure download, upload bandwidth, latency and ping jitter", Icons.Default.Speed, Screen.WifiController.route, listOf("speed", "speedtest", "bandwidth", "upload", "download", "ping")),
    GlobalToolItem("Port Scanner & Ping", "Network", "Scan open network ports, trace route and ping remote hosts", Icons.Default.Sensors, Screen.WifiController.route, listOf("port", "ping", "traceroute", "network", "scanner")),
    GlobalToolItem("Reverse DNS & Whois", "Network", "Look up domain registrations, IP ownership and DNS records", Icons.Default.Search, Screen.WifiController.route, listOf("whois", "domain", "dns", "lookup", "reverse")),
    GlobalToolItem("IP Address Lookup", "Network", "Discover public IP address, ISP, ASN and geolocation details", Icons.Default.Public, Screen.WifiController.route, listOf("ip", "lookup", "isp", "location", "address")),
    GlobalToolItem("Subnet Calculator", "Network", "CIDR network prefix calculations, broadcast, and IP range breakdown", Icons.Default.Calculate, Screen.WifiController.route, listOf("subnet", "cidr", "ip", "network", "mask")),

    // Location & Sensors
    GlobalToolItem("Fake GPS Location", "Location", "Spoof mock GPS coordinates with precision latitude & longitude", Icons.Default.LocationOn, Screen.FakeGps.route, listOf("gps", "fake", "mock", "location", "spoof")),
    GlobalToolItem("Sound Direction Compass", "Sensors", "Acoustic compass detecting incoming sound direction using mics", Icons.Default.Explore, Screen.SoundDirection.route, listOf("sound", "compass", "audio", "sensor", "direction")),

    // Storage & Media
    GlobalToolItem("Storage Space Analyzer", "Storage", "Visual breakdown of internal disk usage and large files", Icons.Default.PieChart, Screen.StorageAnalyzer.route, listOf("storage", "disk", "space", "analyzer", "files")),
    GlobalToolItem("Social Media Downloader", "Social", "Save videos, audio and reels from supported social links", Icons.Default.Download, Screen.Social.route, listOf("social", "download", "video", "reels", "audio", "status")),
    GlobalToolItem("Audio Extractor & Converter", "Media", "Extract high quality MP3 / M4A audio tracks from video files", Icons.Default.Audiotrack, Screen.Social.route, listOf("audio", "extractor", "mp3", "video", "convert")),

    // Utilities & Generators
    GlobalToolItem("Auto Clicker", "Utility", "Automated screen tap gestures, multi-point macro and swipe scripts", Icons.Default.TouchApp, Screen.AutoClicker.route, listOf("auto", "clicker", "tap", "macro", "game")),
    GlobalToolItem("Nickname Generator", "Generator", "Cool gamer tags, stylish nick styles and clan names", Icons.Default.Badge, Screen.NicknameGen.route, listOf("nickname", "gaming", "tag", "names", "generator")),
    GlobalToolItem("Random Name Generator", "Generator", "Generate realistic male, female, and fantasy names", Icons.Default.Person, Screen.NameGen.route, listOf("name", "random", "person", "generator")),
    GlobalToolItem("Mix Name Combiner", "Generator", "Combine couple names, baby names, or team names together", Icons.Default.Shuffle, Screen.NameMixer.route, listOf("mix", "combine", "couple", "names", "blend")),
    GlobalToolItem("Stylish Font & Fancy Text", "Creative", "Convert plain text to decorative symbols and Unicode styles", Icons.Default.TextFields, Screen.StylishTextGen.route, listOf("font", "fancy", "text", "style", "symbols")),
    GlobalToolItem("Password Generator", "Security", "Generate strong cryptographically secure passwords", Icons.Default.Password, Screen.PasswordGen.route, listOf("password", "generator", "crypto", "secure", "pass")),
    GlobalToolItem("Fake Data & Identity Generator", "Generator", "Generate realistic addresses, credit cards, profiles and data", Icons.Default.PermIdentity, Screen.FakeDataGenerator.route, listOf("fake", "identity", "data", "profile", "generator")),
    GlobalToolItem("URL Link Shortener", "Utility", "Shorten long URLs with TinyURL, is.gd, and da.gd", Icons.Default.Link, Screen.SystemDashboard.route, listOf("link", "shortener", "url", "tinyurl")),
    GlobalToolItem("Age & Date Calculator", "Utility", "Calculate precise age, elapsed days, and upcoming birthdays", Icons.Default.Cake, Screen.AgeCalculator.route, listOf("age", "birthday", "date", "calendar", "calculator")),
    GlobalToolItem("Scientific Calculator", "Utility", "Standard and scientific mathematical calculation engine", Icons.Default.Calculate, Screen.Calculator.route, listOf("calculator", "math", "calc", "scientific")),
    GlobalToolItem("Coin Toss & Dice Roller", "Utility", "Fair virtual 3D coin toss and multi-sided dice roll simulator", Icons.Default.Casino, Screen.CoinToss.route, listOf("coin", "toss", "dice", "random", "roll")),
    GlobalToolItem("Text & Note Saver", "Utility", "Quick offline notepad with cloudless local persistence", Icons.Default.Save, Screen.TextSaver.route, listOf("note", "text", "saver", "notepad")),
    GlobalToolItem("Download & Tool History", "History", "Archive of downloaded media, generated names and logs", Icons.Default.History, Screen.DownloadHistory.route, listOf("history", "downloads", "archive", "logs")),
    GlobalToolItem("Settings & Appearance", "Settings", "Configure theme modes, accent colors, sound and system options", Icons.Default.Settings, Screen.Settings.route, listOf("settings", "theme", "color", "appearance", "dark", "light"))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val smartSuggestions by viewModel.smartSuggestions.collectAsStateWithLifecycle()

    // Deep All-In-One Scanner states
    val isScanning by SystemScannerManager.isScanning.collectAsStateWithLifecycle()
    val scanProgress by SystemScannerManager.scanProgress.collectAsStateWithLifecycle()
    val scanStatusText by SystemScannerManager.statusText.collectAsStateWithLifecycle()
    val currentPhase by SystemScannerManager.currentPhase.collectAsStateWithLifecycle()
    val scanResult by SystemScannerManager.scanResult.collectAsStateWithLifecycle()

    var showReportDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val suggestedFeatures = remember(smartSuggestions) {
        val features = mutableListOf<CompactFeature>()
        smartSuggestions.forEach { suggestion ->
            when (suggestion) {
                "App Ops" -> features.add(CompactFeature("App Ops", Icons.Default.AdminPanelSettings, Screen.AppOps.route))
                "App Manager & Backup" -> features.add(CompactFeature("App Manager", Icons.Default.Apps, Screen.AppList.route))
                "Password Generator" -> features.add(CompactFeature("Passwords", Icons.Default.Password, Screen.PasswordGen.route))
                "System Diagnostics" -> features.add(CompactFeature("System Stats", Icons.Default.Dashboard, Screen.SystemDashboard.route))
                "DNS & Network" -> features.add(CompactFeature("DNS Changer", Icons.Default.VpnKey, Screen.DnsChanger.route))
                "Animation Settings" -> features.add(CompactFeature("Animations", Icons.Default.Animation, Screen.AnimationSettings.route))
            }
        }
        
        val allFeatures = listOf(
            CompactFeature("App Locker", Icons.Default.Lock, Screen.AppLocker.route),
            CompactFeature("Security Scan", Icons.Default.Security, Screen.DeepScanner.route),
            CompactFeature("App Usage Stats", Icons.Default.QueryStats, Screen.AppUsageStats.route),
            CompactFeature("Fake GPS", Icons.Default.LocationOn, Screen.FakeGps.route),
            CompactFeature("App Manager", Icons.Default.Apps, Screen.AppList.route),
            CompactFeature("System Stats", Icons.Default.Dashboard, Screen.SystemDashboard.route),
            CompactFeature("DNS Changer", Icons.Default.VpnKey, Screen.DnsChanger.route),
            CompactFeature("Animations", Icons.Default.Animation, Screen.AnimationSettings.route),
            CompactFeature("Age Calculator", Icons.Default.Cake, Screen.AgeCalculator.route),
            CompactFeature("Calculator", Icons.Default.Calculate, Screen.Calculator.route),
            CompactFeature("History", Icons.Default.History, Screen.DownloadHistory.route),
            CompactFeature("Coin Toss", Icons.Default.Casino, Screen.CoinToss.route),
            CompactFeature("Passwords", Icons.Default.Password, Screen.PasswordGen.route),
            CompactFeature("Text Saver", Icons.Default.Save, Screen.TextSaver.route)
        )
        
        allFeatures.forEach { feature ->
            if (features.none { it.route == feature.route }) {
                features.add(feature)
            }
        }
        features.take(6)
    }

    // Dynamic icon representing the current scan phase
    val phaseIcon = when (currentPhase) {
        SystemScannerManager.ScanPhase.SCANNING_APPS -> Icons.Default.Apps
        SystemScannerManager.ScanPhase.SCANNING_FILES -> Icons.Default.FolderOpen
        SystemScannerManager.ScanPhase.SCANNING_NETWORK -> Icons.Default.Wifi
        SystemScannerManager.ScanPhase.SCANNING_BLUETOOTH -> Icons.Default.Bluetooth
        SystemScannerManager.ScanPhase.SCANNING_BATTERY_THERMAL -> Icons.Default.BatteryChargingFull
        SystemScannerManager.ScanPhase.SCANNING_HARDWARE_RAM_CPU -> Icons.Default.Memory
        SystemScannerManager.ScanPhase.CHECKING_INTEGRITY -> Icons.Default.Shield
        SystemScannerManager.ScanPhase.COMPLETED -> Icons.Default.Verified
        else -> Icons.Default.Security
    }

    val t0 = remember { SystemClock.uptimeMillis() }
    SideEffect {
        Log.d("Tabs", "HomeScreen switched in ${SystemClock.uptimeMillis() - t0}ms")
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var homeTimeframe by remember { mutableStateOf(UsageTimeframe.TODAY) }
    var wellbeingSummary by remember { mutableStateOf<WellbeingSummary?>(null) }
    var hasUsagePermission by remember { mutableStateOf(false) }
    var isWellbeingLoading by remember { mutableStateOf(true) }

    val savedLimits = remember(context) { getSavedAppLimits(context) }
    val activeTimersCount = remember(wellbeingSummary, savedLimits) {
        wellbeingSummary?.appUsageList?.count { it.timerLimitMinutes > 0 } ?: savedLimits.size
    }
    val exceededTimersCount = remember(wellbeingSummary) {
        wellbeingSummary?.appUsageList?.count { it.isTimerExceeded } ?: 0
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onHomeVisible()
        }
    }

    LaunchedEffect(lifecycleOwner, homeTimeframe) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val granted = checkUsageStatsPermission(context)
            hasUsagePermission = granted
            if (granted) {
                val cached = WellbeingDataCache.get(homeTimeframe)
                if (cached != null) {
                    wellbeingSummary = cached
                    isWellbeingLoading = false
                } else {
                    isWellbeingLoading = true
                }
                withContext(Dispatchers.IO) {
                    val stats = fetchWellbeingStatsInternal(context, homeTimeframe, WellbeingDataCache.globalIconCache)
                    withContext(Dispatchers.Main) {
                        wellbeingSummary = stats
                        isWellbeingLoading = false
                    }
                }
            } else {
                isWellbeingLoading = false
            }
        }
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val cardBgColor = if (isLight) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    val cardShape = RoundedCornerShape(22.dp)
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.25f else 0.4f))

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AnimatedListItem(index = 0) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System Diagnostics & Toolkit",
                                color = Color(0xFF8B5CF6), // Indigo-500
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
                            )

                            Text(
                                text = "Arwora Hyper Toolkit",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.2.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 2.dp, top = 2.dp, bottom = 2.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                navController.navigate(Screen.DownloadHistory.route)
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Deep hardware diagnostics, Wi-Fi security, battery thermals, junk cleaner & system utilities.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ==================== SECURITY SCAN ====================
        item {
            AnimatedListItem(index = 1) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = {
                        SoundEffectManager.playClick()
                        navController.navigate(Screen.DeepScanner.route)
                    },
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = cardBorder,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "System Security Scan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap to run a deep security scan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // ==================== SCREEN ACTIVITY ====================
        item {
            AnimatedListItem(index = 2) {
                CompactWellbeingDashboardCard(
                    summary = wellbeingSummary ?: WellbeingSummary(),
                    activeTimersCount = activeTimersCount,
                    exceededTimersCount = exceededTimersCount,
                    selectedTimeframe = homeTimeframe,
                    onTimeframeSelected = { tf ->
                        homeTimeframe = tf
                    },
                    cardTitle = "App Usage Stats",
                    hasPermission = hasUsagePermission,
                    isLoading = isWellbeingLoading,
                    onCardClick = {
                        navController.navigate(Screen.AppUsageStats.route)
                    }
                )
            }
        }

        // ==================== FEATURED BANNER ====================
        item {
            AnimatedListItem(index = 3) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(),
                    onClick = {
                        SoundEffectManager.playClick()
                        navController.navigate(Screen.AppOps.route)
                    },
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = cardBorder,
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "App Ops Manager",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Inspect and manage application permissions, standby buckets, and package controls with advanced system diagnostics.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = "Open App Ops",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // ==================== SEARCH & SMART TOOLS GRID ====================
        item {
            AnimatedListItem(index = 4) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search tools, diagnostics & generators...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        unfocusedContainerColor = cardBgColor,
                        focusedContainerColor = cardBgColor
                    )
                )
            }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            val matchedTools = allGlobalTools.filter { tool ->
                tool.title.lowercase().contains(q) ||
                tool.category.lowercase().contains(q) ||
                tool.description.lowercase().contains(q) ||
                tool.keywords.any { it.lowercase().contains(q) }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Search Results (${matchedTools.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { searchQuery = "" }) {
                        Text("Clear", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (matchedTools.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No tools found matching '$searchQuery'",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try searching for keywords like wifi, dns, battery, lock, scan, or name",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(matchedTools) { tool ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        onClick = {
                            SoundEffectManager.playClick()
                            navController.navigate(tool.route)
                        },
                        shape = cardShape,
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = cardBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.title,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = tool.category.uppercase(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tool.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = tool.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Open",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        } else {
            item {
                AnimatedListItem(index = 5) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Quick Tools & Utilities",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "All Tools",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                SoundEffectManager.playClick()
                                navController.navigate(Screen.GenerateDashboard.route)
                            }
                        )
                    }
                }
            }

            val filteredFeatures = suggestedFeatures
            val featurePairs = filteredFeatures.chunked(2)

            itemsIndexed(featurePairs) { index, pair ->
                AnimatedListItem(index = index + 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        pair.forEach { feature ->
                            Card(
                                modifier = Modifier
                                    .weight(1f),
                                onClick = {
                                    SoundEffectManager.playClick()
                                    navController.navigate(feature.route)
                                },
                                shape = cardShape,
                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                border = cardBorder,
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.Start
                                ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = feature.icon,
                                        contentDescription = feature.title,
                                        modifier = Modifier.size(22.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = feature.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    item {
        Spacer(modifier = Modifier.height(80.dp))
    }
}

    // ==================== DETAILED HARDWARE & SYSTEM REPORT DIALOG ====================
    if (showReportDialog) {
        val result = scanResult
        if (result != null) {
            var showHighRiskList by remember { mutableStateOf(false) }

            AlertDialog(
            onDismissRequest = { showReportDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("All-In-One Diagnostic Report", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Device Health Score: ${result.healthScore}/100",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.healthScore >= 80) Color(0xFF10B981) else Color(0xFFF59E0B),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Wi-Fi & Network
                    item {
                        ReportSectionCard(
                            title = "Wi-Fi & Network Security",
                            icon = Icons.Default.Wifi,
                            statusText = if (result.wifiInfo.isConnected) "Connected • ${result.wifiInfo.securityType}" else "Disconnected",
                            isPass = result.wifiInfo.isConnected,
                            actionLabel = "Wi-Fi Settings",
                            onAction = {
                                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                            }
                        ) {
                            Text("• SSID: ${result.wifiInfo.ssid}", style = MaterialTheme.typography.bodySmall)
                            Text("• Signal Strength (RSSI): ${result.wifiInfo.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                            Text("• Link Speed: ${result.wifiInfo.linkSpeedMbps} Mbps", style = MaterialTheme.typography.bodySmall)
                            Text("• Local IP: ${result.wifiInfo.localIp}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // 2. Bluetooth Security
                    item {
                        ReportSectionCard(
                            title = "Bluetooth Security",
                            icon = Icons.Default.Bluetooth,
                            statusText = if (result.bluetoothInfo.isEnabled) "${result.bluetoothInfo.pairedDevicesCount} Paired Devices (${result.bluetoothInfo.scanMode})" else "Bluetooth Disabled",
                            isPass = result.bluetoothInfo.isSecure,
                            actionLabel = "Bluetooth",
                            onAction = {
                                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            }
                        ) {
                            Text("• Adapter State: ${if (result.bluetoothInfo.isEnabled) "Active" else "Disabled"}", style = MaterialTheme.typography.bodySmall)
                            Text("• Visibility Mode: ${result.bluetoothInfo.scanMode}", style = MaterialTheme.typography.bodySmall)
                            if (result.bluetoothInfo.pairedDeviceNames.isNotEmpty()) {
                                Text("• Paired: ${result.bluetoothInfo.pairedDeviceNames.take(3).joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // 3. Battery & Thermal Health
                    item {
                        ReportSectionCard(
                            title = "Battery & Thermal Health",
                            icon = Icons.Default.BatteryChargingFull,
                            statusText = "${result.batteryInfo.levelPercentage}% • ${result.batteryInfo.temperatureCelsius}°C • ${result.batteryInfo.health}",
                            isPass = !result.batteryInfo.isOverheating,
                            actionLabel = "Battery Settings",
                            onAction = {
                                context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
                            }
                        ) {
                            Text("• Temperature: ${result.batteryInfo.temperatureCelsius}°C (${if (result.batteryInfo.isOverheating) "Warning: High Temp" else "Optimal Range"})", style = MaterialTheme.typography.bodySmall)
                            Text("• Voltage: ${result.batteryInfo.voltageMv} mV", style = MaterialTheme.typography.bodySmall)
                            Text("• Power Source: ${result.batteryInfo.plugType}", style = MaterialTheme.typography.bodySmall)
                            Text("• Health Condition: ${result.batteryInfo.health}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // 4. Hardware: RAM & CPU Performance
                    item {
                        ReportSectionCard(
                            title = "RAM Memory & Processor",
                            icon = Icons.Default.Memory,
                            statusText = "${result.hardwareInfo.usedRamPercentage}% RAM used • ${result.hardwareInfo.cpuCoreCount} Cores (${result.hardwareInfo.cpuArchitecture})",
                            isPass = result.hardwareInfo.usedRamPercentage < 85
                        ) {
                            Text("• Total RAM: ${result.hardwareInfo.formatBytes(result.hardwareInfo.totalRamBytes)}", style = MaterialTheme.typography.bodySmall)
                            Text("• Free RAM: ${result.hardwareInfo.formatBytes(result.hardwareInfo.availableRamBytes)}", style = MaterialTheme.typography.bodySmall)
                            Text("• Storage: ${result.hardwareInfo.usedStoragePercentage}% used (${result.hardwareInfo.formatBytes(result.hardwareInfo.freeStorageBytes)} free)", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // 5. Installed Apps & Permissions
                    item {
                        ReportSectionCard(
                            title = "Installed Apps & Risk Analysis",
                            icon = Icons.Default.Apps,
                            statusText = "${result.totalAppsChecked} Apps (${result.highRiskApps.size} with high-risk permissions)",
                            isPass = result.highRiskApps.size <= 5,
                            actionLabel = if (result.highRiskApps.isNotEmpty()) (if (showHighRiskList) "Hide Details" else "View Risky Apps") else null,
                            onAction = { showHighRiskList = !showHighRiskList }
                        ) {
                            Text("• User Apps: ${result.userAppsCount} | System Apps: ${result.systemAppsCount}", style = MaterialTheme.typography.bodySmall)
                            Text("• High Risk Apps: ${result.highRiskApps.size}", style = MaterialTheme.typography.bodySmall)

                            if (showHighRiskList && result.highRiskApps.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(4.dp))
                                result.highRiskApps.take(8).forEach { app ->
                                    Text("⚠️ ${app.appName}: ${app.sensitivePermissions.size} permissions", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    // 6. Temporary Files & Junk Cache
                    item {
                        ReportSectionCard(
                            title = "Files & Storage Junk",
                            icon = Icons.Default.DeleteOutline,
                            statusText = "${result.totalFilesAnalyzed} files checked • ${result.junkFilesCount} junk items (${result.formattedJunkSize})",
                            isPass = result.junkFilesCount == 0,
                            actionLabel = if (result.junkFilesCount > 0) "Clean Junk" else null,
                            onAction = {
                                SoundEffectManager.playClick()
                                val (deletedCount, deletedBytes) = SystemScannerManager.cleanJunkFiles(context)
                                Toast.makeText(context, "Cleaned $deletedCount temporary junk files!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("• Temporary / Log / Cache: ${result.junkFilesCount} files", style = MaterialTheme.typography.bodySmall)
                            Text("• Space Reclaimable: ${result.formattedJunkSize}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // 7. System Integrity (Root, ADB, Mock GPS)
                    item {
                        ReportSectionCard(
                            title = "System Integrity & Debugging",
                            icon = Icons.Default.Shield,
                            statusText = if (!result.isRooted && !result.isAdbEnabled) "System Secure • No Root Detected" else "Developer/Root Flags Detected",
                            isPass = !result.isRooted,
                            actionLabel = "Dev Options",
                            onAction = {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                            }
                        ) {
                            Text("• Root Binary (Su): ${if (result.isRooted) "Root Found ⚠️" else "Safe / Clean ✅"}", style = MaterialTheme.typography.bodySmall)
                            Text("• ADB Debugging: ${if (result.isAdbEnabled) "Enabled (Dev Mode)" else "Disabled ✅"}", style = MaterialTheme.typography.bodySmall)
                            Text("• Mock GPS Provider: ${if (result.isMockLocationEnabled) "Active" else "Inactive"}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showReportDialog = false }) {
                    Text("Close Diagnostic")
                }
            }
        )
    }
}
}

@Composable
private fun DiagnosticMiniChip(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isPass: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (isPass) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isPass) Color(0xFF10B981) else Color(0xFFF59E0B),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(text = subtitle, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun ReportSectionCard(
    title: String,
    icon: ImageVector,
    statusText: String,
    isPass: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, if (isPass) MaterialTheme.colorScheme.outline else Color(0xFFF59E0B).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isPass) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isPass) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (isPass) "PASS" else "CHECK",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPass) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))
            content()

            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
