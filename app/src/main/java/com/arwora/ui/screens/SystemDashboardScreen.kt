package com.arwora.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import kotlinx.coroutines.launch
import com.arwora.service.MockLocationService
import com.arwora.ui.AppManagerViewModel
import com.arwora.ui.navigation.Screen
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.ui.components.ExpressiveCompactLoadingIndicator
import com.arwora.ui.components.ExpressiveWavyLinearProgressIndicator
import com.arwora.ui.components.FakeDeviceDialog
import com.arwora.ui.components.GooglePlayCompactLinearProgress
import com.arwora.ui.components.GooglePlayLinearProgressBar
import com.arwora.ui.components.GoogleStyleLinearLoadingCard
import com.arwora.feature.system.shell.ShellCommandCard
import com.arwora.feature.system.shell.ShellCommandViewModel
import com.arwora.feature.system.linkshortener.LinkShortenerCard
import com.arwora.feature.system.linkshortener.LinkShortenerViewModel
import com.arwora.feature.system.htmltoapk.HtmlToApkBottomSheet
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arwora.utils.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDashboardScreen(
    navController: NavController,
    appManagerViewModel: AppManagerViewModel
) {
    val t0 = remember { SystemClock.uptimeMillis() }
    SideEffect {
        Log.d("Tabs", "SystemDashboardScreen switched in ${SystemClock.uptimeMillis() - t0}ms")
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        NativeAdbManager.init(context)
        com.arwora.utils.AppLockerManager.init(context)
    }

    var pendingGuardedRoute by remember { mutableStateOf<String?>(null) }
    var pendingGuardedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun navigateGuarded(route: String, action: () -> Unit) {
        if (com.arwora.utils.AppLockerManager.isToolLockedByRoute(route)) {
            pendingGuardedRoute = route
            pendingGuardedAction = action
        } else {
            action()
        }
    }

    val clipboardManager = LocalClipboardManager.current
    val shellViewModel: ShellCommandViewModel = viewModel()
    val linkShortenerViewModel: LinkShortenerViewModel = viewModel()

    // Expanded section states
    var isDnsExpanded by remember { mutableStateOf(false) }

    var isWirelessDebuggingExpanded by remember { mutableStateOf(false) }
    var isAnimationExpanded by remember { mutableStateOf(false) }
    var isFakeGpsExpanded by rememberSaveable { mutableStateOf(false) }
    var isFakeDeviceExpanded by remember { mutableStateOf(false) }
    var isAppManagerExpanded by remember { mutableStateOf(false) }
    var isIpLookupExpanded by remember { mutableStateOf(false) }
    var showHtmlToApkSheet by rememberSaveable { mutableStateOf(false) }

    var ipDetails by remember { mutableStateOf<com.arwora.utils.IpLookupManager.IpDetails?>(null) }
    var isLoadingIp by remember { mutableStateOf(false) }
    var refreshIpTrigger by remember { mutableStateOf(0) }
    var ipLookupInput by remember { mutableStateOf("") }
    var targetForLookup by remember { mutableStateOf("") }

    // Diagnostic Results
    var reverseDnsResult by remember { mutableStateOf("") }
    var portScanResults by remember { mutableStateOf<List<com.arwora.utils.IpLookupManager.PortStatus>>(emptyList()) }
    var pingResult by remember { mutableStateOf<com.arwora.utils.IpLookupManager.PingResult?>(null) }
    var tracerouteHops by remember { mutableStateOf<List<com.arwora.utils.IpLookupManager.TraceHop>>(emptyList()) }

    var isLoadingReverseDns by remember { mutableStateOf(false) }
    var isScanningPorts by remember { mutableStateOf(false) }
    var isPinging by remember { mutableStateOf(false) }
    var isTracerouting by remember { mutableStateOf(false) }

    // IP/Network Toolkit Extended States
    var activeToolkitTab by remember { mutableStateOf(0) } // 0: Lookup & Geolocation, 1: Subnet & MAC, 2: Web & Security, 3: DNS Propagation
    
    // Subnet Calculator State
    var subnetIpInput by remember { mutableStateOf("192.168.1.1") }
    var subnetCidrInput by remember { mutableStateOf("24") }
    var subnetResult by remember { mutableStateOf<com.arwora.utils.IpLookupManager.SubnetResult?>(null) }

    // DNS Propagation State
    var dnsDomainInput by remember { mutableStateOf("google.com") }
    var dnsRecordType by remember { mutableStateOf("A") } // A, CNAME, MX, TXT
    var dnsPropagationResults by remember { mutableStateOf<List<com.arwora.utils.IpLookupManager.DnsResolverResult>>(emptyList()) }
    var isCheckingDns by remember { mutableStateOf(false) }

    // SSL Certificate State
    var sslDomainInput by remember { mutableStateOf("google.com") }
    var sslCertResult by remember { mutableStateOf<com.arwora.utils.IpLookupManager.SslCertResult?>(null) }
    var isCheckingSsl by remember { mutableStateOf(false) }

    // HTTP Header State
    var httpUrlInput by remember { mutableStateOf("google.com") }
    var httpHeadersResult by remember { mutableStateOf<List<com.arwora.utils.IpLookupManager.HttpHeader>>(emptyList()) }
    var isFetchingHeaders by remember { mutableStateOf(false) }

    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val subtleBorderColor = if (isLight) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    // MAC Address State
    var macInput by remember { mutableStateOf("00:1A:11") }
    var macLookupResult by remember { mutableStateOf<com.arwora.utils.IpLookupManager.MacLookupResult?>(null) }

    // DNS state
    var selectedDnsProvider by remember { mutableStateOf(DnsManager.getSelectedDns(context)) }

    LaunchedEffect(isIpLookupExpanded, targetForLookup, refreshIpTrigger) {
        if (isIpLookupExpanded) {
            isLoadingIp = true
            val queryTarget = targetForLookup.trim()
            if (queryTarget.isEmpty()) {
                // Fetch public current IP details
                val rawPublicIp = com.arwora.utils.IpLookupManager.fetchPublicIp(context)
                ipDetails = rawPublicIp
                if (rawPublicIp.ip != "Fetch Failed" && rawPublicIp.ip != "No Connection") {
                    ipLookupInput = rawPublicIp.ip
                }
            } else {
                ipDetails = com.arwora.utils.IpLookupManager.fetchDetails(context, queryTarget)
            }
            isLoadingIp = false

            // Automatically query sub-diagnostics for the loaded IP
            val activeIp = ipDetails?.ip
            if (activeIp != null && activeIp != "Fetch Failed" && activeIp != "No Connection" && activeIp != "Empty target") {
                // Reverse DNS
                isLoadingReverseDns = true
                reverseDnsResult = com.arwora.utils.IpLookupManager.lookupReverseDns(activeIp)
                isLoadingReverseDns = false

                // Port scan
                isScanningPorts = true
                portScanResults = com.arwora.utils.IpLookupManager.scanPorts(activeIp)
                isScanningPorts = false

                // Ping
                isPinging = true
                pingResult = com.arwora.utils.IpLookupManager.performPing(activeIp)
                isPinging = false

                // Traceroute
                isTracerouting = true
                tracerouteHops = com.arwora.utils.IpLookupManager.performTraceroute(activeIp)
                isTracerouting = false
            } else {
                reverseDnsResult = ""
                portScanResults = emptyList()
                pingResult = null
                tracerouteHops = emptyList()
            }
        }
    }

    // Animation & Refresh state
    var animScale by remember { mutableStateOf(AnimationTweakManager.getCurrentAnimationScale(context)) }
    val isAdbConnected by NativeAdbManager.isConnected.collectAsStateWithLifecycle()
    val hasNotificationAccess by WirelessPairingManager.notificationAccessGranted.collectAsStateWithLifecycle()

    // Fake Device State
    val defaultDeviceName = remember {
        val brand = android.os.Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }
        val model = android.os.Build.MODEL
        if (model.startsWith(brand, ignoreCase = true)) model else "$brand $model"
    }
    val prefs = remember { context.getSharedPreferences("fake_device_prefs", Context.MODE_PRIVATE) }
    var currentSpoof by remember { mutableStateOf(defaultDeviceName) }
    var showFakeDeviceDialog by remember { mutableStateOf(false) }
    var deviceSearchQuery by remember { mutableStateOf("") }

    // Fake GPS State
    val isMockActive by FakeGpsManager.isMockingActive.collectAsStateWithLifecycle()
    val savedLat by FakeGpsManager.currentLat.collectAsStateWithLifecycle()
    val savedLng by FakeGpsManager.currentLng.collectAsStateWithLifecycle()
    val mockUpdateCount by FakeGpsManager.updateCounter.collectAsStateWithLifecycle()
    val mockError by FakeGpsManager.lastError.collectAsStateWithLifecycle()
    val activeAlarmMessage by com.arwora.service.BatteryAlarmService.activeAlarmMessage.collectAsStateWithLifecycle()

    var inputLat by remember(savedLat) { mutableStateOf(savedLat.toString()) }
    var inputLng by remember(savedLng) { mutableStateOf(savedLng.toString()) }
    var currentPresetName by remember { mutableStateOf("Custom Coordinate") }
    var showMockHelpDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    fun checkNotificationPermissionStatus() {
        WirelessPairingManager.checkNotificationPermissionStatus(context)
    }

    fun refreshDashboardStatuses() {
        coroutineScope.launch {
            checkNotificationPermissionStatus()
            NativeAdbManager.refreshConnectionStatus(context)
            val (scale, dns) = withContext(Dispatchers.IO) {
                AnimationTweakManager.getCurrentAnimationScale(context) to
                    DnsManager.getSelectedDns(context)
            }
            animScale = scale
            selectedDnsProvider = dns
            shellViewModel.refreshStatus()
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val resumeObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshDashboardStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(resumeObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(resumeObserver)
        }
    }

    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            appManagerViewModel.onVisible()
            withContext(Dispatchers.IO) {
                FakeGpsManager.init(context)
                val dns = DnsManager.getSelectedDns(context)
                val scale = AnimationTweakManager.getCurrentAnimationScale(context)
                val preset = FakeGpsManager.getSavedPresetName(context)
                val spoof = prefs.getString("selected_device", null) ?: defaultDeviceName
                withContext(Dispatchers.Main) {
                    selectedDnsProvider = dns
                    animScale = scale
                    currentPresetName = preset
                    currentSpoof = spoof
                    checkNotificationPermissionStatus()
                    NativeAdbManager.refreshConnectionStatus(context)
                    shellViewModel.refreshStatus()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Dns,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("System Dashboard", fontWeight = FontWeight.ExtraBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0. ACTIVE ALARM / NOTIFICATION BANNER
            if (!activeAlarmMessage.isNullOrBlank()) {
                item {
                    AnimatedListItem(index = 0) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isLight) 0.85f else 0.95f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Alarm Active",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "System Alarm Notification",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = activeAlarmMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
                            }

                            IconButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    val intent = Intent(context, com.arwora.service.BatteryAlarmService::class.java).apply {
                                        action = com.arwora.service.BatteryAlarmService.ACTION_DISMISS_ALARM
                                    }
                                    context.startService(intent)
                                    com.arwora.service.BatteryAlarmService.setAlarmMessage(null)
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss Alarm",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

            // 1. TOP HEADER SECTION
            item {
                AnimatedListItem(index = 1) {
                    Column {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "ADVANCED SYSTEM UTILITIES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Manage private DNS routing, spoof location signatures, adjust animator scales, audit wireless background debugging, and inspect history logs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            // 2. HERO / SUMMARY CARD
            item {
                AnimatedListItem(index = 2) {
                    CentralizedControlPanelHeroCard()
                }
            }

        // SECTION 1: CORE CONTROLS & SPOOFERS
        item {
            SectionHeader(title = "Core Controls & Spoofers")
        }

            // FAKE GPS / LOCATION SPOOFER CARD (collapsed by default)
            item {
                FakeGpsLocationSpooferCard(
                    isExpanded = isFakeGpsExpanded,
                    onToggleExpand = {
                        SoundEffectManager.playClick()
                        isFakeGpsExpanded = !isFakeGpsExpanded
                    },
                    isMockActive = isMockActive,
                    savedLat = savedLat,
                    savedLng = savedLng,
                    mockUpdateCount = mockUpdateCount,
                    mockError = mockError,
                    currentPresetName = currentPresetName,
                    inputLat = inputLat,
                    inputLng = inputLng,
                    onInputLatChange = { inputLat = it },
                    onInputLngChange = { inputLng = it },
                    onSelectPreset = { preset ->
                        SoundEffectManager.playClick()
                        currentPresetName = preset.name
                        inputLat = preset.latitude.toString()
                        inputLng = preset.longitude.toString()
                        FakeGpsManager.saveCoordinates(context, preset.latitude, preset.longitude, preset.name)
                        if (isMockActive) {
                            MockLocationService.start(context, preset.latitude, preset.longitude)
                        }
                    },
                    onOpenMapPicker = {
                        SoundEffectManager.playClick()
                        navController.navigate(Screen.FakeGps.route)
                    },
                    onShowSetupHelp = {
                        SoundEffectManager.playClick()
                        showMockHelpDialog = true
                    },
                    onStartMockGps = {
                        SoundEffectManager.playClick()
                        val lat = inputLat.toDoubleOrNull()
                        val lng = inputLng.toDoubleOrNull()
                        if (lat == null || lng == null || lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
                            Toast.makeText(context, "Please enter valid Latitude (-90..90) and Longitude (-180..180)", Toast.LENGTH_SHORT).show()
                        } else {
                            FakeGpsManager.saveCoordinates(context, lat, lng, currentPresetName)
                            val started = FakeGpsManager.startMockLocation(context, lat, lng)
                            if (started) {
                                MockLocationService.start(context, lat, lng)
                                Toast.makeText(context, "Mock Location started at ($lat, $lng)", Toast.LENGTH_SHORT).show()
                            } else {
                                showMockHelpDialog = true
                            }
                        }
                    },
                    onStopMockGps = {
                        SoundEffectManager.playClick()
                        MockLocationService.stop(context)
                        FakeGpsManager.stopMockLocation(context)
                        Toast.makeText(context, "Mock Location stopped.", Toast.LENGTH_SHORT).show()
                    },
                    onOpenExternalMap = {
                        SoundEffectManager.playClick()
                        val lat = inputLat.toDoubleOrNull() ?: savedLat
                        val lng = inputLng.toDoubleOrNull() ?: savedLng
                        try {
                            val geoUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(Spoofed+Location)")
                            val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                            context.startActivity(mapIntent)
                        } catch (e: Exception) {
                            clipboardManager.setText(AnnotatedString("$lat, $lng"))
                            Toast.makeText(context, "Coordinates copied to clipboard: $lat, $lng", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenDeveloperSettings = {
                        SoundEffectManager.playClick()
                        FakeGpsManager.openDeveloperSettings(context)
                    }
                )
            }


            // FAKE DEVICE SWITCHER CARD
            val isActualDevice = currentSpoof == defaultDeviceName || currentSpoof.contains(android.os.Build.MODEL, ignoreCase = true)
            item {
                SystemFeatureCard(
                    title = "Device Model",
                    subtitle = if (isActualDevice) "Actual Hardware: $currentSpoof" else "Active Spoof: $currentSpoof",
                    icon = Icons.Default.PhoneAndroid,
                    badge = if (isActualDevice) "HARDWARE" else "SPOOFED",
                    badgeColor = if (isActualDevice) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    isExpanded = isFakeDeviceExpanded,
                    onToggleExpand = {
                        SoundEffectManager.playClick()
                        isFakeDeviceExpanded = !isFakeDeviceExpanded
                    }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Display genuine system hardware model or customize build properties dynamically for simulation testing and benchmarks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Dynamic Hardware Specs Grid
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Device Model:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(android.os.Build.MODEL, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Manufacturer / Brand:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${android.os.Build.MANUFACTURER} (${android.os.Build.BRAND})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Hardware / Board:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${android.os.Build.HARDWARE} • ${android.os.Build.BOARD}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Android Version & API:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(if (isActualDevice) "Active Hardware Profile:" else "Active Spoofed Profile:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(currentSpoof, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!isActualDevice) {
                                    OutlinedButton(
                                        onClick = {
                                            SoundEffectManager.playClick()
                                            prefs.edit().putString("selected_device", defaultDeviceName).apply()
                                            currentSpoof = defaultDeviceName
                                            FakeDeviceManager.resetToRealDevice(context)
                                        },
                                        shape = ArworaDesignTokens.PillShape
                                    ) {
                                        Text("Reset")
                                    }
                                }
                                Button(
                                    onClick = {
                                        SoundEffectManager.playClick()
                                        showFakeDeviceDialog = true
                                    },
                                    shape = ArworaDesignTokens.PillShape
                                ) {
                                    Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Choose Device")
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 2: NETWORK & SYSTEM SETTINGS
            item {
                SectionHeader(title = "Network & System Settings")
            }

            // DNS CHANGER CARD
            item {
                SystemFeatureCard(
                    title = "DNS Changer",
                    subtitle = "Current: ${selectedDnsProvider.name}",
                    icon = Icons.Default.Security,
                    badge = "Active",
                    badgeColor = Color(0xFF10B981),
                    isExpanded = isDnsExpanded,
                    onToggleExpand = {
                        SoundEffectManager.playClick()
                        isDnsExpanded = !isDnsExpanded
                    }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Quick DNS Presets",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val quickPresets = listOf(
                            DnsManager.providers.firstOrNull { it.name.contains("Cloudflare", ignoreCase = true) } ?: DnsManager.providers[0],
                            DnsManager.providers.firstOrNull { it.name.contains("Google", ignoreCase = true) } ?: DnsManager.providers[1],
                            DnsManager.providers.firstOrNull { it.name.contains("AdGuard", ignoreCase = true) } ?: DnsManager.providers[2]
                        )

                        quickPresets.forEach { provider ->
                            val isSelected = selectedDnsProvider.id == provider.id
                            Surface(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    DnsManager.saveSelectedDns(context, provider)
                                    selectedDnsProvider = provider
                                    coroutineScope.launch {
                                        val applied = withContext(Dispatchers.IO) {
                                            DnsSecureSettingsHelper.setPrivateDns(context, "hostname", provider.dotHost)
                                        }
                                        if (applied) {
                                            Toast.makeText(context, "${provider.name} applied!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Selected ${provider.name}. Tap below to apply via Settings or Wireless ADB", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else subtleBorderColor)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(provider.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text(provider.dotHost, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                SoundEffectManager.playClick()
                                navController.navigate(Screen.DnsChanger.route)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = ArworaDesignTokens.PillShape
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Full DNS Selector")
                        }
                    }
                }
            }

            // APP LOCKER CARD
            item {
                val isLockerEnabled by com.arwora.utils.AppLockerManager.isLockerEnabled.collectAsStateWithLifecycle()
                val lockedTools by com.arwora.utils.AppLockerManager.lockedToolIds.collectAsStateWithLifecycle()
                val lockedApps by com.arwora.utils.AppLockerManager.lockedPackages.collectAsStateWithLifecycle()
                val totalSecured = lockedTools.size + lockedApps.size

                SystemActionRowCard(
                    title = "App Locker",
                    subtitle = if (isLockerEnabled) "$totalSecured items secured with PIN & Biometrics" else "Lock apps & system features with PIN / Biometrics",
                    icon = Icons.Default.Lock,
                    iconColor = if (isLockerEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    badge = if (isLockerEnabled) "Active" else "Security",
                    badgeColor = if (isLockerEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    onClick = {
                        SoundEffectManager.playClick()
                        navController.navigate(Screen.AppLocker.route)
                    },
                    modifier = Modifier.testTag("system_app_locker_card")
                )
            }

            // AUTO CLICKER CARD
            item {
                SystemActionRowCard(
                    title = "Auto Clicker",
                    subtitle = "Automated taps, multi-target & swipe floating overlay",
                    icon = Icons.Default.TouchApp,
                    iconColor = MaterialTheme.colorScheme.primary,
                    badge = "Automation",
                    badgeColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        SoundEffectManager.playClick()
                        navigateGuarded(Screen.AutoClicker.route) {
                            navController.navigate(Screen.AutoClicker.route)
                        }
                    },
                    modifier = Modifier.testTag("system_auto_clicker_card")
                )
            }

            // WI-FI CONTROLLER CARD
            item {
                SystemActionRowCard(
                    title = "Wi-Fi Controller",
                    subtitle = "Network diagnostics & connected devices",
                    icon = Icons.Default.Wifi,
                    iconColor = MaterialTheme.colorScheme.primary,
                    badge = "Network",
                    badgeColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        SoundEffectManager.playClick()
                        navigateGuarded(Screen.WifiController.route) {
                            navController.navigate(Screen.WifiController.route)
                        }
                    }
                )
            }

            // WI-FI PASSWORD CARD
            item {
                SystemActionRowCard(
                    title = "Wi-Fi Password",
                    subtitle = "Saved Wi-Fi networks, reveal passwords & quick connect",
                    icon = Icons.Default.Password,
                    iconColor = MaterialTheme.colorScheme.primary,
                    badge = if (isAdbConnected) "ADB Ready" else "ADB Required",
                    badgeColor = if (isAdbConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                    onClick = {
                        SoundEffectManager.playClick()
                        navigateGuarded(Screen.WifiPassword.route) {
                            navController.navigate(Screen.WifiPassword.route)
                        }
                    },
                    modifier = Modifier.testTag("system_wifi_password_card")
                )
            }

            // HIDE PRIVATE DNS CARD
            item {
                SystemActionRowCard(
                    title = "Hide Private DNS",
                    subtitle = "Restrict system Private DNS for selected apps & games",
                    icon = Icons.Default.VpnLock,
                    iconColor = MaterialTheme.colorScheme.primary,
                    badge = "DNS",
                    badgeColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        SoundEffectManager.playClick()
                        navigateGuarded(Screen.HidePrivateDns.route) {
                            navController.navigate(Screen.HidePrivateDns.route)
                        }
                    },
                    modifier = Modifier.testTag("system_hide_private_dns_card")
                )
            }

            // SMART SCREEN-OFF OPTIMIZER CARD
            item {
                SystemActionRowCard(
                    title = "Smart Screen-Off Optimizer",
                    subtitle = "Automated background cleanup routines on device screen-off events",
                    icon = Icons.Default.Bedtime,
                    iconColor = MaterialTheme.colorScheme.primary,
                    badge = "Automation",
                    badgeColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        SoundEffectManager.playClick()
                        navigateGuarded(Screen.ScreenOffOptimizer.route) {
                            navController.navigate(Screen.ScreenOffOptimizer.route)
                        }
                    },
                    modifier = Modifier.testTag("system_screen_off_optimizer_card")
                )
            }

            // BATTERY USAGE & DEEP SLEEP MONITOR CARD
            item {
                SystemActionRowCard(
                    title = "Battery Monitor & Deep Sleep",
                    subtitle = "Real-time app consumption stats & CPU wake lock diagnostics",
                    icon = Icons.Default.BatteryChargingFull,
                    iconColor = MaterialTheme.colorScheme.primary,
                    badge = "Power",
                    badgeColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        SoundEffectManager.playClick()
                        navigateGuarded(Screen.BatteryMonitor.route) {
                            navController.navigate(Screen.BatteryMonitor.route)
                        }
                    },
                    modifier = Modifier.testTag("system_battery_monitor_card")
                )
            }

            // NOTIFICATION HISTORY LOG CARD
            item {
                SystemActionRowCard(
                    title = "Notification History Log",
                    subtitle = if (hasNotificationAccess) {
                        "Notification Access Active • View captured logs"
                    } else {
                        "Notification Access Required • Tap to configure"
                    },
                    icon = if (hasNotificationAccess) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                    iconColor = if (hasNotificationAccess) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    badge = if (hasNotificationAccess) "Active" else "Grant",
                    badgeColor = if (hasNotificationAccess) Color(0xFF10B981) else Color(0xFFF59E0B),
                    onClick = {
                        SoundEffectManager.playClick()
                        navigateGuarded(Screen.NotificationHistory.route) {
                            navController.navigate(Screen.NotificationHistory.route)
                        }
                    },
                    modifier = Modifier.testTag("system_notification_history_card")
                )
            }

            // BATTERY ALARM CARD
            item {
                SystemActionRowCard(
                    title = "Battery Charging Alarm",
                    subtitle = "Alert at target charge level",
                    icon = Icons.Default.BatteryChargingFull,
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        SoundEffectManager.playClick()
                        navigateGuarded(Screen.BatteryAlarm.route) {
                            navController.navigate(Screen.BatteryAlarm.route)
                        }
                    }
                )
            }

            // SOUND DIRECTION DETECTOR CARD
            item {
                SystemActionRowCard(
                    title = "Sound Direction Detector",
                    subtitle = "Detect sound source direction",
                    icon = Icons.Default.Mic,
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        SoundEffectManager.playClick()
                        navigateGuarded(Screen.SoundDirection.route) {
                            navController.navigate(Screen.SoundDirection.route)
                        }
                    }
                )
            }

            // PERMISSION INSPECTOR CARD
            item {
                SystemActionRowCard(
                    title = "Permission Inspector",
                    subtitle = "Manage app permissions",
                    icon = Icons.Default.Security,
                    iconColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        SoundEffectManager.playClick()
                        navigateGuarded(Screen.PermissionInspector.route) {
                            navController.navigate(Screen.PermissionInspector.route)
                        }
                    }
                )
            }

            // IP ADDRESS LOOKUP CARD
            item {
                val scope = rememberCoroutineScope()
                SystemFeatureCard(
                    title = "IP Address Lookup",
                    subtitle = "Polished network diagnostics & utilities toolkit",
                    icon = Icons.Default.Language,
                    badge = if (ipDetails != null && ipDetails!!.ip != "No Connection" && ipDetails!!.ip != "Fetch Failed" && !ipDetails!!.isPrivate) "Online" else if (ipDetails?.isPrivate == true) "LAN" else "Toolkit",
                    badgeColor = if (ipDetails != null && ipDetails!!.ip != "No Connection" && ipDetails!!.ip != "Fetch Failed" && !ipDetails!!.isPrivate) Color(0xFF10B981) else if (ipDetails?.isPrivate == true) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                    isExpanded = isIpLookupExpanded,
                    onToggleExpand = {
                        SoundEffectManager.playClick()
                        isIpLookupExpanded = !isIpLookupExpanded
                    }
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        
                        // Header with Title & Subtitle inside the card
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Network Diagnostics Toolkit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Explore standard utilities & DNS intelligence tools", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Horizontal Segmented Tabs with visible bottom divider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ScrollableTabRow(
                                selectedTabIndex = activeToolkitTab,
                                edgePadding = 6.dp,
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.primary,
                                indicator = {},
                                divider = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isLight) Color(0xFFF9FAFB)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .border(0.75.dp, subtleBorderColor.copy(alpha = if (isLight) 0.65f else 0.8f), RoundedCornerShape(14.dp))
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                val toolkitTabs = listOf("Lookup & WHOIS", "Diagnostics", "Calculators & Utils", "Web & Security")
                                toolkitTabs.forEachIndexed { index, tabTitle ->
                                    val isSelected = activeToolkitTab == index
                                    val tabBorderColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    } else {
                                        subtleBorderColor.copy(alpha = if (isLight) 0.65f else 0.8f)
                                    }
                                    val tabBgColor = if (isSelected) {
                                        if (isLight) Color(0xFFF3E8FF) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                    } else {
                                        if (isLight) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                    }

                                    Tab(
                                        selected = isSelected,
                                        onClick = { 
                                            SoundEffectManager.playClick()
                                            activeToolkitTab = index 
                                        },
                                        selectedContentColor = MaterialTheme.colorScheme.primary,
                                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp, vertical = 2.dp)
                                            .shadow(if (isLight && isSelected) 1.dp else 0.dp, RoundedCornerShape(10.dp))
                                            .background(
                                                color = tabBgColor,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .border(0.75.dp, tabBorderColor, RoundedCornerShape(10.dp))
                                            .clip(RoundedCornerShape(10.dp)),
                                        text = { 
                                            Text(
                                                text = tabTitle, 
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, 
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) 
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // RENDER TABS CONTENT
                        when (activeToolkitTab) {
                            0 -> { // LOOKUP & WHOIS
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // Primary Search Inputs
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = if (isLight) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                                        border = BorderStroke(1.dp, subtleBorderColor),
                                        shape = RoundedCornerShape(14.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text("Search Target IP / Domain", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            
                                            OutlinedTextField(
                                                value = ipLookupInput,
                                                onValueChange = { ipLookupInput = it },
                                                label = { Text("IP Address or Domain") },
                                                placeholder = { Text("e.g. 8.8.8.8 or google.com") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                trailingIcon = {
                                                    if (ipLookupInput.isNotEmpty()) {
                                                        IconButton(onClick = { ipLookupInput = "" }) {
                                                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                                                        }
                                                    }
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = subtleBorderColor,
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                                )
                                            )

                                            Button(
                                                onClick = {
                                                    SoundEffectManager.playClick()
                                                    targetForLookup = ipLookupInput
                                                },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = ArworaDesignTokens.PillShape,
                                                enabled = !isLoadingIp
                                            ) {
                                                if (isLoadingIp) {
                                                    ExpressiveCompactLoadingIndicator(
                                                        size = 18.dp,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Looking up...", fontWeight = FontWeight.Bold)
                                                } else {
                                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Lookup Target", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }

                                    if (isLoadingIp) {
                                        GoogleStyleLinearLoadingCard(
                                            title = "Resolving IP & WHOIS Intelligence...",
                                            subtitle = "Querying ASN, geolocation, and registrar records",
                                            icon = Icons.Default.TravelExplore
                                        )
                                    } else {
                                        val details = ipDetails
                                        if (details != null) {
                                            // Private Warning Banner
                                            if (details.isPrivate) {
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                                    shape = RoundedCornerShape(14.dp),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                            Text("Private Address Space", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            "This is a private/local IP range (${details.ip}). Global geolocation and registrar lookups require public routing coordinates.",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onErrorContainer
                                                        )
                                                    }
                                                }
                                            }

                                            // 1. WHOIS & ASN Details
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = if (isLight) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                                border = BorderStroke(1.dp, subtleBorderColor),
                                                shape = RoundedCornerShape(14.dp),
                                                elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                        Text("WHOIS & ASN Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    }
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                                    
                                                    TooltipRow("Resolved IP", details.ip, isPrimary = true)
                                                    TooltipRow("ISP / Carrier", details.isp)
                                                    TooltipRow("Organization", details.org)
                                                    TooltipRow("ASN Identifier", details.asn)
                                                    TooltipRow("Registrar", details.registrar)
                                                    TooltipRow("Abuse Contact", details.abuseContact)
                                                }
                                            }

                                            // 2. Reverse DNS PTR
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = if (isLight) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                                border = BorderStroke(1.dp, subtleBorderColor),
                                                shape = RoundedCornerShape(14.dp),
                                                elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Icon(Icons.Default.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                        Text("Reverse DNS (PTR Record)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    }
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                                    if (isLoadingReverseDns) {
                                                        GooglePlayLinearProgressBar(modifier = Modifier.fillMaxWidth(), height = 4.dp)
                                                    } else {
                                                        val ptrText = if (reverseDnsResult.isEmpty() || reverseDnsResult == "No PTR Record") "No reverse DNS found" else reverseDnsResult
                                                        Text(
                                                            text = ptrText,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (ptrText == "No reverse DNS found") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }

                                            // 3. Geolocation Map
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = if (isLight) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                                border = BorderStroke(1.dp, subtleBorderColor),
                                                shape = RoundedCornerShape(14.dp),
                                                elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                        Text("Approximate Geolocation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                    }
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                                    
                                                    TooltipRow("Location Details", "${details.city}, ${details.region}, ${details.country}")
                                                    TooltipRow("GPS Coordinates", "${details.latitude}, ${details.longitude}")

                                                    if (!details.isPrivate && details.latitude != 0.0) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        OutlinedButton(
                                                            onClick = {
                                                                SoundEffectManager.playClick()
                                                                try {
                                                                    val uri = "geo:${details.latitude},${details.longitude}?q=${details.latitude},${details.longitude}(IP Geolocation)"
                                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                                                    context.startActivity(intent)
                                                                } catch (e: Exception) {
                                                                    Toast.makeText(context, "Google Maps app not found", Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                                            shape = ArworaDesignTokens.PillShape,
                                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                                        ) {
                                                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text("Open in Google Maps")
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            // Empty State
                                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                                Text("Enter a target above to start diagnostics.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                            
                            1 -> { // PING, ROUTE & PORTS
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // Port Scan
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = if (isLight) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                        border = BorderStroke(1.dp, subtleBorderColor),
                                        shape = RoundedCornerShape(14.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                    Text("Diagnostic Port Check", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("Authorized Only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            
                                            if (isScanningPorts) {
                                                GooglePlayLinearProgressBar(modifier = Modifier.fillMaxWidth(), height = 4.dp)
                                            } else {
                                                if (portScanResults.isEmpty()) {
                                                    Text("No target resolved. Perform WHOIS lookup first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                } else {
                                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        portScanResults.forEach { port ->
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                Text("Port ${port.port} (${port.name})", style = MaterialTheme.typography.bodySmall)
                                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                    val color = when (port.status) {
                                                                        "Open" -> Color(0xFF10B981)
                                                                        "Closed" -> Color(0xFFEF4444)
                                                                        else -> Color(0xFF6B7280)
                                                                    }
                                                                    Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
                                                                    Text(port.status, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Ping & Route Summary
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = if (isLight) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                        border = BorderStroke(1.dp, subtleBorderColor),
                                        shape = RoundedCornerShape(14.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Text("Ping & Route Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            
                                            if (isPinging) {
                                                GooglePlayLinearProgressBar(modifier = Modifier.fillMaxWidth(), height = 4.dp)
                                            } else {
                                                pingResult?.let { pr ->
                                                    TooltipRow(
                                                        label = "ICMP Ping Status",
                                                        value = if (pr.success) "${pr.packetsReceived}/${pr.packetsSent} Received (${pr.latencyMs} ms)" else "Timeout",
                                                        isPrimary = pr.success
                                                    )
                                                } ?: Text("Run IP Lookup to measure ping parameters.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }

                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            Text("Network Route Hops (Traceroute)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                            if (isTracerouting) {
                                                GooglePlayLinearProgressBar(modifier = Modifier.fillMaxWidth(), height = 4.dp)
                                            } else {
                                                if (tracerouteHops.isEmpty()) {
                                                    Text("Hops not resolved.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                } else {
                                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        tracerouteHops.forEach { hop ->
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                Text("${hop.hop}. ${hop.ip} (${hop.hostname.ifEmpty { "Transit Node" }})", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                                                                Text("${hop.latencyMs} ms", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            2 -> { // CALCULATORS & UTILS
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // 1. Subnet Calculator
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = if (isLight) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                        border = BorderStroke(1.dp, subtleBorderColor),
                                        shape = RoundedCornerShape(14.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Text("Subnet Calculator & CIDR Finder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = subnetIpInput,
                                                    onValueChange = { subnetIpInput = it },
                                                    label = { Text("Base IP Address") },
                                                    placeholder = { Text("e.g. 192.168.1.1") },
                                                    modifier = Modifier.weight(1f),
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        unfocusedBorderColor = subtleBorderColor,
                                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                                OutlinedTextField(
                                                    value = subnetCidrInput,
                                                    onValueChange = { subnetCidrInput = it },
                                                    label = { Text("CIDR") },
                                                    placeholder = { Text("24") },
                                                    modifier = Modifier.width(80.dp),
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        unfocusedBorderColor = subtleBorderColor,
                                                        focusedBorderColor = MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                            }
                                            
                                            Button(
                                                onClick = {
                                                    SoundEffectManager.playClick()
                                                    val cidrVal = subnetCidrInput.toIntOrNull() ?: 24
                                                    subnetResult = com.arwora.utils.IpLookupManager.calculateSubnet(subnetIpInput, cidrVal)
                                                },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = ArworaDesignTokens.PillShape
                                            ) {
                                                Text("Calculate Subnet Boundaries", fontWeight = FontWeight.Bold)
                                            }
                                            
                                            subnetResult?.let { res ->
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = if (isLight) Color(0xFFF9FAFB) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                                                    border = BorderStroke(1.dp, subtleBorderColor),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        TooltipRow("Network Address", res.networkAddress, isPrimary = true)
                                                        TooltipRow("Subnet Mask", res.subnetMask)
                                                        TooltipRow("Broadcast Address", res.broadcastAddress)
                                                        TooltipRow("Usable Range", "${res.firstUsable} - ${res.lastUsable}")
                                                        TooltipRow("Total Usable Hosts", res.totalHosts.toString())
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 2. MAC Address Lookup
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = if (isLight) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                        border = BorderStroke(1.dp, subtleBorderColor),
                                        shape = RoundedCornerShape(14.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Text("MAC Address Vendor Finder", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            
                                            OutlinedTextField(
                                                value = macInput,
                                                onValueChange = { macInput = it },
                                                label = { Text("MAC Address / OUI Prefix") },
                                                placeholder = { Text("e.g. 00:1A:11") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = subtleBorderColor,
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                            
                                            Button(
                                                onClick = {
                                                    SoundEffectManager.playClick()
                                                    macLookupResult = com.arwora.utils.IpLookupManager.lookupMacVendor(macInput)
                                                },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = ArworaDesignTokens.PillShape
                                            ) {
                                                Text("Lookup Vendor OUI", fontWeight = FontWeight.Bold)
                                            }
                                            
                                            macLookupResult?.let { res ->
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = if (res.formatValid) { if (isLight) Color(0xFFF9FAFB) else MaterialTheme.colorScheme.surface } else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
                                                    border = BorderStroke(1.dp, if (res.formatValid) subtleBorderColor else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        TooltipRow("Standard MAC Format", res.mac)
                                                        TooltipRow("OUI Manufacturer", res.vendor, isPrimary = res.formatValid)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            3 -> { // WEB & SECURITY
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    // 1. SSL Certificate Checker
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = if (isLight) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                        border = BorderStroke(1.dp, subtleBorderColor),
                                        shape = RoundedCornerShape(14.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Text("SSL Certificate Checker", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            
                                            OutlinedTextField(
                                                value = sslDomainInput,
                                                onValueChange = { sslDomainInput = it },
                                                label = { Text("Secure Domain URL") },
                                                placeholder = { Text("e.g. google.com") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = subtleBorderColor,
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                            
                                            Button(
                                                onClick = {
                                                    SoundEffectManager.playClick()
                                                    isCheckingSsl = true
                                                    scope.launch {
                                                        sslCertResult = com.arwora.utils.IpLookupManager.checkSslCertificate(sslDomainInput)
                                                        isCheckingSsl = false
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = ArworaDesignTokens.PillShape,
                                                enabled = !isCheckingSsl
                                            ) {
                                                if (isCheckingSsl) {
                                                    ExpressiveCompactLoadingIndicator(
                                                        size = 18.dp,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Verifying SSL...", fontWeight = FontWeight.Bold)
                                                } else {
                                                    Text("Verify SSL Validity", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            
                                            sslCertResult?.let { res ->
                                                val cardBg = if (!res.isValid) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f) 
                                                             else if (res.daysRemaining < 30) Color(0xFFFEF3C7).copy(alpha = 0.3f) 
                                                             else if (isLight) Color(0xFFF9FAFB) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                                val cardBorder = if (!res.isValid) MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                                                 else if (res.daysRemaining < 30) Color(0xFFF59E0B).copy(alpha = 0.4f)
                                                                 else subtleBorderColor
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = cardBg),
                                                    border = BorderStroke(1.dp, cardBorder),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text("Trust Status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            val statusText = if (!res.isValid) "Invalid / Expired" else "Valid & Encrypted"
                                                            val statusColor = if (!res.isValid) Color(0xFFEF4444) else Color(0xFF10B981)
                                                            Text(statusText, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = statusColor)
                                                        }
                                                        if (res.error.isNotEmpty()) {
                                                            Text("Error: ${res.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                                        } else {
                                                            TooltipRow("Subject DN", res.subject)
                                                            TooltipRow("Issuer Authority", res.issuer)
                                                            TooltipRow("Issued From", res.validFrom)
                                                            TooltipRow("Expires At", res.validTo)
                                                            TooltipRow("Days Active Remaining", "${res.daysRemaining} Days", isPrimary = res.isValid)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 2. HTTP Headers Inspector
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = if (isLight) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                        border = BorderStroke(1.dp, subtleBorderColor),
                                        shape = RoundedCornerShape(14.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Text("HTTP Response Headers", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            
                                            OutlinedTextField(
                                                value = httpUrlInput,
                                                onValueChange = { httpUrlInput = it },
                                                label = { Text("Secure URL / Address") },
                                                placeholder = { Text("e.g. google.com") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = subtleBorderColor,
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                            
                                            Button(
                                                onClick = {
                                                    SoundEffectManager.playClick()
                                                    isFetchingHeaders = true
                                                    scope.launch {
                                                        httpHeadersResult = com.arwora.utils.IpLookupManager.fetchHttpHeaders(httpUrlInput)
                                                        isFetchingHeaders = false
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = ArworaDesignTokens.PillShape,
                                                enabled = !isFetchingHeaders
                                            ) {
                                                if (isFetchingHeaders) {
                                                    ExpressiveCompactLoadingIndicator(
                                                        size = 18.dp,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Fetching Headers...", fontWeight = FontWeight.Bold)
                                                } else {
                                                    Text("Parse HTTP Headers", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            
                                            if (httpHeadersResult.isNotEmpty()) {
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = if (isLight) Color(0xFFF9FAFB) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                                                    border = BorderStroke(1.dp, subtleBorderColor),
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        httpHeadersResult.take(12).forEach { header ->
                                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                    Text(header.key, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (header.isSecurityHeader) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface)
                                                                    if (header.isSecurityHeader) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .background(Color(0xFF10B981).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                                        ) {
                                                                            Text("Security", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                                                        }
                                                                    }
                                                                }
                                                                Text(header.value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            }
                                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    
                                    // 3. DNS Propagation
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = if (isLight) Color.White else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                                        border = BorderStroke(1.dp, subtleBorderColor),
                                        shape = RoundedCornerShape(14.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Text("DNS Record Propagation Check", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            }
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            
                                            OutlinedTextField(
                                                value = dnsDomainInput,
                                                onValueChange = { dnsDomainInput = it },
                                                label = { Text("Query Hostname") },
                                                placeholder = { Text("google.com") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                shape = RoundedCornerShape(12.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    unfocusedBorderColor = subtleBorderColor,
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                                )
                                            )
                                            
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                listOf("A", "CNAME", "MX", "TXT").forEach { type ->
                                                    val isSelected = dnsRecordType == type
                                                    FilterChip(
                                                        selected = isSelected,
                                                        onClick = { dnsRecordType = type },
                                                        label = { Text(type) },
                                                        shape = ArworaDesignTokens.PillShape,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                            }
                                            
                                            Button(
                                                onClick = {
                                                    SoundEffectManager.playClick()
                                                    isCheckingDns = true
                                                    scope.launch {
                                                        dnsPropagationResults = com.arwora.utils.IpLookupManager.checkDnsPropagation(dnsDomainInput, dnsRecordType)
                                                        isCheckingDns = false
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                                shape = ArworaDesignTokens.PillShape,
                                                enabled = !isCheckingDns
                                            ) {
                                                if (isCheckingDns) {
                                                    ExpressiveCompactLoadingIndicator(
                                                        size = 18.dp,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Checking DNS...", fontWeight = FontWeight.Bold)
                                                } else {
                                                    Text("Check Global Propagation", fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            
                                            if (dnsPropagationResults.isNotEmpty()) {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                                    dnsPropagationResults.forEach { res ->
                                                        Card(
                                                            colors = CardDefaults.cardColors(containerColor = if (isLight) Color(0xFFF9FAFB) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                                                            border = BorderStroke(1.dp, subtleBorderColor),
                                                            shape = RoundedCornerShape(12.dp),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text(res.resolverName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                                    Text(res.resolvedValue, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                }
                                                                val statusColor = when (res.status) {
                                                                    "Resolved" -> Color(0xFF10B981)
                                                                    "No Record" -> Color(0xFFF59E0B)
                                                                    else -> Color(0xFFEF4444)
                                                                }
                                                                Box(
                                                                    modifier = Modifier
                                                                        .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                                                        .border(1.dp, statusColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text(res.status, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // WIRELESS DEBUGGING & NATIVE ADB MANAGER CARD
            item {
                val adbPrefs = remember { context.getSharedPreferences("adb_access_prefs", Context.MODE_PRIVATE) }
                var isUserStopped by remember { mutableStateOf(adbPrefs.getBoolean("is_user_stopped", false)) }
                var showAdbHelpDialog by remember { mutableStateOf(false) }

                val wifiIp = remember(isWirelessDebuggingExpanded, isAdbConnected) {
                    NativeAdbManager.getLocalWifiIpAddress(context) ?: "127.0.0.1"
                }

                val adbConnectionState by NativeAdbManager.connectionState.collectAsStateWithLifecycle()
                val connectedHostState by NativeAdbManager.connectedHost.collectAsStateWithLifecycle()
                val connectedPortState by NativeAdbManager.connectedPort.collectAsStateWithLifecycle()
                val discoveredPortState by NativeAdbManager.discoveredPort.collectAsStateWithLifecycle()
                val requiresDevOptionsGuidance by NativeAdbManager.requiresDevOptionsGuidance.collectAsStateWithLifecycle()
                val adbStatusMessage by NativeAdbManager.statusMessage.collectAsStateWithLifecycle()

                var adbPort by remember { mutableIntStateOf(-1) }
                var isWirelessAdbEnabled by remember { mutableStateOf(false) }

                val cardLifecycleOwner = LocalLifecycleOwner.current
                val updateAdbStatus = {
                    adbPort = NativeAdbManager.getSystemWirelessDebugPort(context)
                        ?: NativeAdbManager.connectedPort.value
                        ?: NativeAdbManager.discoveredPort.value
                        ?: -1
                    isWirelessAdbEnabled = NativeAdbManager.isWirelessDebuggingSettingEnabled(context)
                    isUserStopped = adbPrefs.getBoolean("is_user_stopped", false)
                    NativeAdbManager.refreshConnectionStatus(context)
                    checkNotificationPermissionStatus()
                }

                val effectiveHost = connectedHostState?.takeIf { it.isNotBlank() } ?: wifiIp
                val effectivePort = connectedPortState ?: discoveredPortState ?: adbPort
                val isAdbConnecting = adbConnectionState == NativeAdbConnectionState.CONNECTING

                DisposableEffect(cardLifecycleOwner) {
                    updateAdbStatus()
                    val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            updateAdbStatus()
                        }
                    }
                    cardLifecycleOwner.lifecycle.addObserver(lifecycleObserver)
                    onDispose {
                        cardLifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
                    }
                }

                val badgeColor = when {
                    isAdbConnected -> Color(0xFF10B981)
                    isAdbConnecting -> MaterialTheme.colorScheme.primary
                    isUserStopped && !isAdbConnected -> MaterialTheme.colorScheme.error
                    else -> Color(0xFFFF9800)
                }

                val connectionStatusText = when {
                    isAdbConnected -> "Active / Connected"
                    isAdbConnecting -> "Connecting..."
                    else -> "Inactive / Disconnected"
                }

                SystemFeatureCard(
                    title = "Wireless Debugging",
                    subtitle = "IP: $effectiveHost${if (effectivePort > 0) ":$effectivePort" else ""} • Status: $connectionStatusText",
                    icon = Icons.Default.Wifi,
                    badge = if (isAdbConnected) "Active / Connected" else if (isAdbConnecting) "Connecting" else "Inactive",
                    badgeColor = badgeColor,
                    statusDotColor = badgeColor,
                    isExpanded = isWirelessDebuggingExpanded,
                    onToggleExpand = {
                        SoundEffectManager.playClick()
                        isWirelessDebuggingExpanded = !isWirelessDebuggingExpanded
                    }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Connection & Network Details",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Local IP Address:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(effectiveHost, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Wireless Debug Port:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (effectivePort > 0) "$effectivePort" else "Auto Detection",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("wireless_debug_port_value")
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Developer ADB:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (isAdbConnected || isWirelessAdbEnabled) "Enabled" else "Disabled / Check Settings",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAdbConnected || isWirelessAdbEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        if (isAdbConnecting) {
                            Spacer(modifier = Modifier.height(10.dp))
                            ExpressiveWavyLinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wireless_adb_connect_wavy_progress"),
                                height = 10.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = adbStatusMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (!isAdbConnected && requiresDevOptionsGuidance) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = ArworaDesignTokens.InnerTileShape,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wireless_adb_error_guidance_banner")
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
                                            text = adbStatusMessage,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            SoundEffectManager.playClick()
                                            try {
                                                context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                            } catch (_: Exception) {
                                                Toast.makeText(context, "Unable to open Developer Options.", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("wireless_adb_error_open_dev_options_button"),
                                        shape = ArworaDesignTokens.PillShape
                                    ) {
                                        Icon(Icons.Default.DeveloperMode, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Developer Options", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                val endpoint = "$effectiveHost${if (effectivePort > 0) ":$effectivePort" else ""}"
                                clipboardManager.setText(AnnotatedString(endpoint))
                                Toast.makeText(context, "ADB address copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = ArworaDesignTokens.PillShape
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy IP & Port", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Actions & Developer Options",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    try {
                                        context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Unable to open Developer Options. Please enable them in system settings.", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = ArworaDesignTokens.PillShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Developer Options", fontWeight = FontWeight.Bold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        SoundEffectManager.playClick()
                                        showAdbHelpDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = ArworaDesignTokens.PillShape
                                ) {
                                    Text("Pairing Help", fontWeight = FontWeight.SemiBold)
                                }

                                Button(
                                    onClick = {
                                        SoundEffectManager.playClick()
                                        if (isAdbConnected) {
                                            NativeAdbManager.disconnect(userInitiated = true)
                                            updateAdbStatus()
                                            refreshDashboardStatuses()
                                            Toast.makeText(context, "Wireless ADB disconnected", Toast.LENGTH_SHORT).show()
                                        } else {
                                            coroutineScope.launch {
                                                val (_, msg) = WirelessPairingManager.connectUnifiedWirelessAdb(context)
                                                updateAdbStatus()
                                                refreshDashboardStatuses()
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    enabled = !isAdbConnecting,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("wireless_adb_top_connect_button"),
                                    shape = ArworaDesignTokens.PillShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAdbConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                                    )
                                ) {
                                    Text(
                                        text = when {
                                            isAdbConnected -> "Disconnect"
                                            isAdbConnecting -> "Connecting..."
                                            else -> "Connect Now"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(14.dp))

                        // Native Automated Wireless Debugging Pairing System
                        com.arwora.ui.components.WirelessPairingWizard(
                            onPairingSuccess = {
                                Toast.makeText(context, "Active / Connected!", Toast.LENGTH_SHORT).show()
                                updateAdbStatus()
                                refreshDashboardStatuses()
                            }
                        )

                        if (showAdbHelpDialog) {
                            AlertDialog(
                                onDismissRequest = { showAdbHelpDialog = false },
                                title = { Text("Wireless Debugging & Pairing Guide") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("How to connect built-in Wireless ADB:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text("1. Open System Settings > Developer Options.", style = MaterialTheme.typography.bodySmall)
                                        Text("2. Connect to Wi-Fi and enable 'Wireless Debugging'.", style = MaterialTheme.typography.bodySmall)
                                        Text("3. If pairing for the first time, tap 'Pair device with pairing code' and enter the code in Mode 1 or use Mode 2.", style = MaterialTheme.typography.bodySmall)
                                        Text("4. Tap 'Connect Now' to bind directly to the local ADB daemon.", style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            showAdbHelpDialog = false
                                            try {
                                                context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                            } catch (_: Exception) {}
                                        }
                                    ) {
                                        Text("Open Developer Options")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showAdbHelpDialog = false }) {
                                        Text("Dismiss")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ANIMATION SPEED CARD
            item {
                SystemFeatureCard(
                    title = "Animation Speed",
                    subtitle = "Window/Transition/Animator scales: ${animScale}x",
                    icon = Icons.Default.Speed,
                    badge = if (isAdbConnected) "ADB Ready" else "ADB Required",
                    badgeColor = if (isAdbConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                    isExpanded = isAnimationExpanded,
                    onToggleExpand = {
                        SoundEffectManager.playClick()
                        isAnimationExpanded = !isAnimationExpanded
                    }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Quick Animation Scale Preset",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(0.0f, 0.25f, 0.5f, 1.0f).forEach { scale ->
                                val isSelected = animScale == scale
                                Button(
                                    onClick = {
                                        SoundEffectManager.playClick()
                                        coroutineScope.launch {
                                            val ok = withContext(Dispatchers.IO) {
                                                AnimationTweakManager.setAnimationScales(context, scale)
                                            }
                                            if (ok) {
                                                animScale = scale
                                            } else {
                                                Toast.makeText(context, "Connect Wireless ADB first", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = ArworaDesignTokens.PillShape,
                                    colors = if (isSelected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary) else ButtonDefaults.outlinedButtonColors()
                                ) {
                                    Text(if (scale == 0.0f) "Off" else "${scale}x", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                navController.navigate(Screen.AnimationSettings.route)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = ArworaDesignTokens.PillShape
                        ) {
                            Icon(Icons.Default.DisplaySettings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Advanced Settings")
                        }
                    }
                }
            }

            // SECTION 3: PACKAGES & TOOLS
            item {
                SectionHeader(title = "Packages & Tools")
            }

            // APP OPS MANAGER CARD
            item {
                SystemActionRowCard(
                    title = "App Ops",
                    subtitle = "Filter packages, permission controls & standby ops",
                    icon = Icons.Default.AdminPanelSettings,
                    iconColor = Color(0xFF10B981),
                    badge = if (isAdbConnected) "ADB Ready" else "ADB Required",
                    badgeColor = if (isAdbConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                    onClick = {
                        SoundEffectManager.playClick()
                        navController.navigate(Screen.AppOps.route)
                    }
                )
            }

            // ADVANCED APP MANAGER & APK EXTRACTOR CARD
            item {
                com.arwora.feature.system.apps.AppManagerCard(
                    isExpanded = isAppManagerExpanded,
                    onToggleExpand = {
                        SoundEffectManager.playClick()
                        isAppManagerExpanded = !isAppManagerExpanded
                    },
                    navController = navController
                )
            }

            // BACKGROUND PROCESS & WAKELOCK DETECTOR CARD
            item {
                com.arwora.feature.system.process.WakelockDetectorCard(
                    navController = navController
                )
            }

            // SHELL COMMAND CARD
            item {
                ShellCommandCard(viewModel = shellViewModel)
            }

            // LINK SHORTENER CARD
            item {
                LinkShortenerCard(viewModel = linkShortenerViewModel)
            }

            // HTML TO APK CARD
            item {
                SystemActionRowCard(
                    title = "Html To Apk",
                    subtitle = "Convert local HTML project to Android APK",
                    icon = Icons.Default.Code,
                    iconColor = MaterialTheme.colorScheme.primary,
                    badge = "Converter",
                    badgeColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        SoundEffectManager.playClick()
                        showHtmlToApkSheet = true
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }

    if (showMockHelpDialog) {
        AlertDialog(
            onDismissRequest = { showMockHelpDialog = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Enable Mock Location in Android", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "To allow this app to inject fake GPS coordinates system-wide:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("1. Open Android Developer Options.", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                    Text("2. Scroll to 'Select mock location app'.", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                    Text("3. Choose ${context.applicationInfo.loadLabel(context.packageManager)}.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    Text("4. Return here and tap Start Mock GPS.", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMockHelpDialog = false
                        FakeGpsManager.openDeveloperSettings(context)
                    }
                ) {
                    Text("Open Developer Options")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMockHelpDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }

    if (showFakeDeviceDialog) {
        FakeDeviceDialog(
            onDismissRequest = { showFakeDeviceDialog = false },
            currentSelectedDevice = currentSpoof,
            onSelectDevice = { profile, isShortName ->
                val chosenName = if (isShortName) profile.shortName else profile.fullName
                prefs.edit().putString("selected_device", chosenName).apply()
                currentSpoof = chosenName
                showFakeDeviceDialog = false
                FakeDeviceManager.applyDeviceProfile(context, profile, isShortName)
            }
        )
    }

    HtmlToApkBottomSheet(
        visible = showHtmlToApkSheet,
        onDismissRequest = { showHtmlToApkSheet = false }
    )

    if (pendingGuardedRoute != null) {
        com.arwora.ui.components.PinAuthDialog(
            title = "Protected Feature",
            subtitle = "Enter PIN or use Biometrics to access",
            onDismiss = {
                pendingGuardedRoute = null
                pendingGuardedAction = null
            },
            onSuccess = {
                val route = pendingGuardedRoute
                val action = pendingGuardedAction
                pendingGuardedRoute = null
                pendingGuardedAction = null
                if (route != null) {
                    com.arwora.utils.AppLockerManager.unlockSession(route)
                }
                action?.invoke()
            }
        )
    }
}

@Composable
fun SystemSimpleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    SystemCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FakeGpsLocationSpooferCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isMockActive: Boolean,
    savedLat: Double,
    savedLng: Double,
    mockUpdateCount: Int,
    mockError: String?,
    currentPresetName: String,
    inputLat: String,
    inputLng: String,
    onInputLatChange: (String) -> Unit,
    onInputLngChange: (String) -> Unit,
    onSelectPreset: (LocationPreset) -> Unit,
    onOpenMapPicker: () -> Unit,
    onShowSetupHelp: () -> Unit,
    onStartMockGps: () -> Unit,
    onStopMockGps: () -> Unit,
    onOpenExternalMap: () -> Unit,
    onOpenDeveloperSettings: () -> Unit
) {
    SystemFeatureCard(
        title = "Fake GPS / Location Spoofer",
        subtitle = if (isMockActive) "Broadcasting: $currentPresetName ($mockUpdateCount fixes)" else "Ready (Lat: $savedLat, Lng: $savedLng)",
        icon = Icons.Default.LocationOn,
        badge = if (isMockActive) "ACTIVE" else "IDLE",
        badgeColor = if (isMockActive) Color(0xFF10B981) else MaterialTheme.colorScheme.outline,
        statusDotColor = if (isMockActive) Color(0xFF10B981) else null,
        isExpanded = isExpanded,
        onToggleExpand = onToggleExpand,
        headerTestTag = "fake_gps_card_header",
        badgeTestTag = "fake_gps_card_badge",
        chevronTestTag = "fake_gps_card_chevron"
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .testTag("fake_gps_card_content")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mock Location Controller",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(
                    onClick = onShowSetupHelp,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Outlined.HelpOutline,
                        contentDescription = "Setup Help",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // INTERACTIVE MAP PICKER PROMINENT BUTTON
            Surface(
                onClick = onOpenMapPicker,
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Map,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f).align(Alignment.CenterVertically)) {
                            Text(
                                text = "Interactive Map Picker",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Full-screen map with draggable pin & live broadcast",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (mockError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = mockError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Worldwide Popular Presets",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(FakeGpsManager.popularPresets) { preset ->
                    val isSelected = currentPresetName == preset.name
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPreset(preset) },
                        label = { Text("${preset.name} (${preset.country})", fontSize = 12.sp) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        shape = ArworaDesignTokens.PillShape
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = inputLat,
                    onValueChange = onInputLatChange,
                    label = { Text("Latitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = inputLng,
                    onValueChange = onInputLngChange,
                    label = { Text("Longitude") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isMockActive) {
                    Button(
                        onClick = onStartMockGps,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("fake_gps_start_button"),
                        shape = ArworaDesignTokens.PillShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start Mock GPS", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onStopMockGps,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("fake_gps_stop_button"),
                        shape = ArworaDesignTokens.PillShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop Mock GPS", fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = onOpenExternalMap,
                    modifier = Modifier.height(48.dp),
                    shape = ArworaDesignTokens.PillShape
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Map")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Developer Options Setup:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onOpenDeveloperSettings) {
                    Text("Open Developer Settings", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SystemFeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    badge: String,
    badgeColor: Color,
    statusDotColor: Color? = null,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    headerTestTag: String? = null,
    badgeTestTag: String? = null,
    chevronTestTag: String? = null,
    content: @Composable () -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val outlineAlpha = if (isLight) 0.25f else 0.45f
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(
            durationMillis = com.arwora.ui.components.ArworaMotionTokens.CARD_TOGGLE_DURATION_MS,
            easing = com.arwora.ui.components.ArworaMotionTokens.StandardEasing
        ),
        label = "feature_card_chevron_rotation"
    )

    SystemCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            val headerCornerShape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isExpanded) 12.dp else 20.dp,
                bottomEnd = if (isExpanded) 12.dp else 20.dp
            )
            val headerModifier = Modifier
                .fillMaxWidth()
                .clip(headerCornerShape)
                .let { if (headerTestTag != null) it.testTag(headerTestTag) else it }
                .clickable { onToggleExpand() }
                .padding(16.dp)

            Row(
                modifier = headerModifier,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (statusDotColor != null) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusDotColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    modifier = if (badgeTestTag != null) Modifier.testTag(badgeTestTag) else Modifier,
                    shape = ArworaDesignTokens.PillShape,
                    color = badgeColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .let { if (chevronTestTag != null) it.testTag(chevronTestTag) else it },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse $title" else "Expand $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = com.arwora.ui.components.ArworaMotionTokens.standardExpandTransition(),
                exit = com.arwora.ui.components.ArworaMotionTokens.standardCollapseTransition()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = outlineAlpha))
                    content()
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun SystemActionRowCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    badge: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SystemCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(iconColor.copy(alpha = 0.08f))
                    .border(
                        width = 1.dp,
                        color = iconColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (badge != null) {
                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = ArworaDesignTokens.PillShape,
                    color = badgeColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TooltipRow(
    label: String,
    value: String,
    isPrimary: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 210.dp)
        )
    }
}


@Composable
fun SystemToolNavigationCard(
    title: String,
    icon: ImageVector,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SystemCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconColor.copy(alpha = 0.08f))
                        .border(
                            width = 1.dp,
                            color = iconColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Common System Card implementation that unifies the border, shape, elevation, and background style
 * across all system dashboard tools, matching DNS Changer and IP Address Lookup.
 */
@Composable
fun SystemCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = ArworaDesignTokens.CardShape
    val cardBorder = ArworaDesignTokens.cardBorder()
    val cardColors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor())
    val cardElevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())

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

@Composable
internal fun CentralizedControlPanelHeroCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("centralized_control_panel_card"),
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.14f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Centralized Control Panel",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "A secure, user-controlled diagnostic environment. Toggle permissions, automate configurations, execute ADB shell operations, or shorten links locally with direct, authorized control.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                lineHeight = 16.sp
            )
        }
    }
}
