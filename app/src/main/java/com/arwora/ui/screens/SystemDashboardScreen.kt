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
import com.arwora.ui.components.AdbPairingBottomSheet
import com.arwora.ui.components.AdbPairingSheetTab
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaMotionTokens
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.ui.components.FakeDeviceDialog
import com.arwora.feature.system.shell.ShellCommandCard
import com.arwora.feature.system.shell.ShellCommandViewModel
import com.arwora.feature.system.htmltoapk.HtmlToApkBottomSheet
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.style.TextAlign
import com.arwora.data.datastore.SettingsRepository
import com.arwora.data.datastore.dataStore
import com.arwora.utils.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDashboardScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") appManagerViewModel: AppManagerViewModel,
    isVisible: Boolean = true
) {
    val t0 = remember { SystemClock.uptimeMillis() }
    SideEffect {
        Log.d("Tabs", "SystemDashboardScreen switched in ${SystemClock.uptimeMillis() - t0}ms")
    }

    var hasDashboardLoadedOnce by rememberSaveable { mutableStateOf(false) }
    var isInitialStaggerWindowActive by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(isVisible) {
        if (isVisible && !hasDashboardLoadedOnce) {
            hasDashboardLoadedOnce = true
            isInitialStaggerWindowActive = true
            kotlinx.coroutines.delay(
                (ArworaMotionTokens.MAX_LIST_STAGGER_ITEMS * ArworaMotionTokens.LIST_STAGGER_STEP_MS) +
                    ArworaMotionTokens.LIST_ENTRY_DURATION_MS + 100L
            )
            isInitialStaggerWindowActive = false
        }
    }
    val canPlayCardEntrance = isVisible || hasDashboardLoadedOnce

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        NativeAdbManager.init(context)
        ShizukuBridgeManager.initialize(context, autoRequestIfNeeded = false)
        AdbShellExecutor.init(context)
        com.arwora.utils.AppLockerManager.init(context)
        com.arwora.feature.system.battery.service.BatteryMonitorManager.init(context)
    }

    val settingsRepository = remember(context) {
        (context.applicationContext as? com.arwora.NameGenApplication)?.settingsRepository
            ?: SettingsRepository(context.applicationContext.dataStore)
    }
    val chargingAlarmEnabled by settingsRepository.batteryAlarmEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val chargingThreshold by settingsRepository.batteryAlarmThresholdFlow.collectAsStateWithLifecycle(initialValue = 80)
    val lowAlarmEnabled by settingsRepository.batteryLowAlarmEnabledFlow.collectAsStateWithLifecycle(initialValue = false)
    val lowThreshold by settingsRepository.batteryLowAlarmThresholdFlow.collectAsStateWithLifecycle(initialValue = 20)

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

    // Expanded section states
    var isDnsExpanded by remember { mutableStateOf(false) }
    var isAnimationExpanded by remember { mutableStateOf(false) }
    var isFakeGpsExpanded by rememberSaveable { mutableStateOf(false) }
    var isFakeDeviceExpanded by remember { mutableStateOf(false) }
    var showHtmlToApkSheet by rememberSaveable { mutableStateOf(false) }
    var showAdbPairingSheet by rememberSaveable { mutableStateOf(false) }
    var adbPairingSheetTab by remember { mutableStateOf(AdbPairingSheetTab.AUTOMATIC) }

    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val subtleBorderColor = if (isLight) Color(0xFFE2E8F0) else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)

    // DNS state
    var selectedDnsProvider by remember { mutableStateOf(DnsManager.getSelectedDns(context)) }

    // Animation & Refresh state
    var animScale by remember { mutableStateOf(AnimationTweakManager.getCurrentAnimationScale(context)) }
    val isAdbConnected by NativeAdbManager.isConnected.collectAsStateWithLifecycle()
    val isUsbDebuggingEnabled by NativeAdbManager.isUsbDebuggingEnabled.collectAsStateWithLifecycle()
    val isWirelessOnlyEnabled by NativeAdbManager.isWirelessOnlyEnabled.collectAsStateWithLifecycle()
    val isSessionForgotten by NativeAdbManager.isSessionForgotten.collectAsStateWithLifecycle()
    val activePrivilegeEngine by AdbShellExecutor.activeEngine.collectAsStateWithLifecycle()
    val shizukuUiStatus by ShizukuBridgeManager.connectionStatus.collectAsStateWithLifecycle()
    val shizukuBinderAlive by ShizukuBridge.isBinderAlive.collectAsStateWithLifecycle()
    val shizukuPermissionGranted by ShizukuBridge.isPermissionGranted.collectAsStateWithLifecycle()
    val shizukuShouldShowRationale by ShizukuBridge.shouldShowRationale.collectAsStateWithLifecycle()
    val isPrivilegeReady = isAdbConnected ||
        (shizukuBinderAlive && shizukuPermissionGranted) ||
        activePrivilegeEngine != PrivilegeEngine.NONE
    var showPrivilegeEngineDialog by remember { mutableStateOf(false) }
    val pendingWifiTrustPrompt by NativeAdbManager.pendingWifiTrustPrompt.collectAsStateWithLifecycle()
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
            ShizukuBridgeManager.checkShizukuStatus(context)
            AdbShellExecutor.refreshEngineStatus(context)
            com.arwora.feature.system.battery.service.BatteryMonitorManager.refreshStats(context, emitLog = false)
            val (scale, dns) = withContext(Dispatchers.IO) {
                AnimationTweakManager.getCurrentAnimationScale(context) to
                    DnsManager.getSelectedDns(context)
            }
            animScale = scale
            selectedDnsProvider = dns
            shellViewModel.refreshStatus()
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            com.arwora.feature.system.battery.service.BatteryMonitorManager.refreshStats(context, emitLog = false)
        }
    }

    fun handleConnectShizukuClick() {
        SoundEffectManager.playClick()
        when (ShizukuBridgeManager.connectShizuku(context)) {
            ShizukuConnectOutcome.ALREADY_CONNECTED -> {
                Toast.makeText(context, "Shizuku permission is active", Toast.LENGTH_SHORT).show()
            }
            ShizukuConnectOutcome.PERMISSION_REQUESTED -> {
                Toast.makeText(context, "Authorizing Shizuku permission...", Toast.LENGTH_SHORT).show()
            }
            ShizukuConnectOutcome.SERVICE_STOPPED_OPENED_APP -> {
                Toast.makeText(context, "Start Shizuku service and return to connect", Toast.LENGTH_SHORT).show()
            }
            ShizukuConnectOutcome.SERVICE_STOPPED_UNAVAILABLE -> {
                Toast.makeText(context, "Shizuku app not installed", Toast.LENGTH_SHORT).show()
            }
        }
        refreshDashboardStatuses()
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
                    AdbShellExecutor.refreshEngineStatus(context)
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
                .padding(horizontal = 16.dp)
                .testTag("system_dashboard_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 0. ACTIVE ALARM / NOTIFICATION BANNER
            if (!activeAlarmMessage.isNullOrBlank()) {
                item {
                    DashboardStaggeredItem(
                        index = 0,
                        triggerAnimation = canPlayCardEntrance,
                        isInitialLoadBurst = isInitialStaggerWindowActive
                    ) {
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
                DashboardStaggeredItem(
                    index = 1,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
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
                DashboardStaggeredItem(
                    index = 2,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    CentralizedControlPanelHeroCard()
                }
            }

            // 2B. UNIVERSAL DUAL-ENGINE PRIVILEGE BRIDGE STATUS CARD
            item {
                DashboardStaggeredItem(
                    index = 3,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    PrivilegeEngineBridgeCard(
                        engine = activePrivilegeEngine,
                        shizukuUiStatus = shizukuUiStatus,
                        onConnectShizukuClick = { handleConnectShizukuClick() },
                        onClick = {
                            SoundEffectManager.playClick()
                            showPrivilegeEngineDialog = true
                        }
                    )
                }
            }

            // 2C. SHIZUKU-STYLE DEBUGGING STATUS TOGGLES & QUICK PAIR CARD
            item {
                DashboardStaggeredItem(
                    index = 4,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    ShizukuDebuggingTogglesCard(
                        isUsbDebuggingEnabled = isUsbDebuggingEnabled,
                        isWirelessDebuggingEnabled = isWirelessOnlyEnabled || isAdbConnected,
                        isSessionForgotten = isSessionForgotten,
                        onOpenDeveloperOptions = {
                            SoundEffectManager.playClick()
                            if (!NativeAdbManager.openDeveloperOptions(context)) {
                                Toast.makeText(context, "Unable to open Developer Options.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onToggleUsbDebugging = {
                            SoundEffectManager.playClick()
                            coroutineScope.launch {
                                val (ok, msg) = NativeAdbManager.toggleUsbDebugging(context)
                                refreshDashboardStatuses()
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (!ok) {
                                    NativeAdbManager.openDeveloperOptions(context)
                                }
                            }
                        },
                        onToggleWirelessDebugging = {
                            SoundEffectManager.playClick()
                            coroutineScope.launch {
                                val (ok, msg) = NativeAdbManager.toggleWirelessDebugging(context)
                                refreshDashboardStatuses()
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (!ok) {
                                    NativeAdbManager.openWirelessDebuggingSettings(context)
                                }
                            }
                        },
                        onOpenPairDialog = {
                            SoundEffectManager.playClick()
                            adbPairingSheetTab = AdbPairingSheetTab.AUTOMATIC
                            showAdbPairingSheet = true
                        },
                        onResetAndPairNewDevice = {
                            SoundEffectManager.playClick()
                            NativeAdbManager.forgetPairedDeviceAndReset(context)
                            refreshDashboardStatuses()
                            adbPairingSheetTab = AdbPairingSheetTab.AUTOMATIC
                            showAdbPairingSheet = true
                            Toast.makeText(context, "Generated fresh pairing keypair for new device.", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

        // SECTION 1: CORE CONTROLS & SPOOFERS
        item {
            DashboardStaggeredItem(
                index = 5,
                triggerAnimation = canPlayCardEntrance,
                isInitialLoadBurst = isInitialStaggerWindowActive
            ) {
                SectionHeader(title = "Core Controls & Spoofers")
            }
        }

            // FAKE GPS / LOCATION SPOOFER CARD (collapsed by default)
            item {
                DashboardStaggeredItem(
                    index = 6,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
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
            }


            // FAKE DEVICE SWITCHER CARD
            val isActualDevice = currentSpoof == defaultDeviceName || currentSpoof.contains(android.os.Build.MODEL, ignoreCase = true)
            item {
                DashboardStaggeredItem(
                    index = 7,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
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
            }

            // SECTION 2: NETWORK & SYSTEM SETTINGS
            item {
                DashboardStaggeredItem(
                    index = 8,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    SectionHeader(title = "Network & System Settings")
                }
            }

            // DNS CHANGER CARD
            item {
                DashboardStaggeredItem(
                    index = 9,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
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
            }

            // APP LOCKER CARD
            item {
                val isLockerEnabled by com.arwora.utils.AppLockerManager.isLockerEnabled.collectAsStateWithLifecycle()
                val hasPin by com.arwora.utils.AppLockerManager.hasPin.collectAsStateWithLifecycle()
                val lockedTools by com.arwora.utils.AppLockerManager.lockedToolIds.collectAsStateWithLifecycle()
                val lockedApps by com.arwora.utils.AppLockerManager.lockedPackages.collectAsStateWithLifecycle()
                val totalSecured = lockedTools.size + lockedApps.size

                DashboardStaggeredItem(
                    index = 10,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    AppLockerDashboardCard(
                        isLockerEnabled = isLockerEnabled,
                        hasPin = hasPin,
                        totalSecured = totalSecured,
                        onToggleLocker = { enable ->
                            SoundEffectManager.playClick(context)
                            if (enable && !hasPin) {
                                Toast.makeText(context, "Set a PIN in App Locker to enable protection", Toast.LENGTH_SHORT).show()
                                navController.navigate(Screen.AppLocker.route)
                            } else {
                                com.arwora.utils.AppLockerManager.setLockerEnabled(context, enable)
                            }
                        },
                        onOpenAppLocker = {
                            SoundEffectManager.playClick(context)
                            navController.navigate(Screen.AppLocker.route)
                        }
                    )
                }
            }

            // AUTO CLICKER CARD
            item {
                DashboardStaggeredItem(
                    index = 11,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
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
            }

            // WI-FI CONTROLLER CARD
            item {
                DashboardStaggeredItem(
                    index = 12,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
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
            }

            // WI-FI PASSWORD CARD
            item {
                DashboardStaggeredItem(
                    index = 13,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    SystemActionRowCard(
                        title = "Wi-Fi Password",
                        subtitle = "Saved Wi-Fi networks, reveal passwords & quick connect",
                        icon = Icons.Default.Password,
                        iconColor = MaterialTheme.colorScheme.primary,
                        badge = if (isPrivilegeReady) "ADB Ready" else "ADB Required",
                        badgeColor = if (isPrivilegeReady) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        onClick = {
                            SoundEffectManager.playClick()
                            navigateGuarded(Screen.WifiPassword.route) {
                                navController.navigate(Screen.WifiPassword.route)
                            }
                        },
                        modifier = Modifier.testTag("system_wifi_password_card")
                    )
                }
            }

            // HIDE PRIVATE DNS CARD
            item {
                DashboardStaggeredItem(
                    index = 14,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
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
            }

            // SMART SCREEN-OFF OPTIMIZER CARD
            item {
                val isScreenOffActive by com.arwora.feature.system.optimizer.service.ScreenOffOptimizerManager.isServiceActive.collectAsStateWithLifecycle()
                val screenOffDelay by com.arwora.feature.system.optimizer.service.ScreenOffOptimizerManager.delaySeconds.collectAsStateWithLifecycle()

                DashboardStaggeredItem(
                    index = 15,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    SmartScreenOffDashboardCard(
                        isServiceActive = isScreenOffActive,
                        delaySeconds = screenOffDelay,
                        onToggleService = { enabled ->
                            SoundEffectManager.playClick(context)
                            if (enabled) {
                                com.arwora.feature.system.optimizer.service.ScreenOffOptimizerManager.startService(context)
                            } else {
                                com.arwora.feature.system.optimizer.service.ScreenOffOptimizerManager.stopService(context)
                            }
                        },
                        onOpenDetails = {
                            SoundEffectManager.playClick(context)
                            navigateGuarded(Screen.ScreenOffOptimizer.route) {
                                navController.navigate(Screen.ScreenOffOptimizer.route)
                            }
                        }
                    )
                }
            }

            // BATTERY MONITOR & DEEP SLEEP CARD
            item {
                DashboardStaggeredItem(
                    index = 16,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    com.arwora.feature.system.battery.ui.BatteryMonitorDashboardCard(
                        onOpenDetails = {
                            SoundEffectManager.playClick(context)
                            navigateGuarded(Screen.BatteryMonitor.route) {
                                navController.navigate(Screen.BatteryMonitor.route)
                            }
                        }
                    )
                }
            }

            // NOTIFICATION HISTORY LOG CARD
            item {
                DashboardStaggeredItem(
                    index = 17,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
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
            }

            // BATTERY ALARM CARD
            item {
                DashboardStaggeredItem(
                    index = 18,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    BatteryChargingAlarmDashboardCard(
                        chargingAlarmEnabled = chargingAlarmEnabled,
                        chargingThreshold = chargingThreshold,
                        lowAlarmEnabled = lowAlarmEnabled,
                        lowThreshold = lowThreshold,
                        onToggleChargingAlarm = { enabled ->
                            SoundEffectManager.playClick(context)
                            coroutineScope.launch {
                                settingsRepository.setBatteryAlarmEnabled(enabled)
                                val serviceIntent = Intent(context, com.arwora.service.BatteryAlarmService::class.java)
                                runCatching {
                                    if (enabled || lowAlarmEnabled) {
                                        serviceIntent.action = com.arwora.service.BatteryAlarmService.ACTION_UPDATE_SETTINGS
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(serviceIntent)
                                        } else {
                                            context.startService(serviceIntent)
                                        }
                                    } else {
                                        serviceIntent.action = com.arwora.service.BatteryAlarmService.ACTION_DISMISS_ALARM
                                        context.stopService(serviceIntent)
                                    }
                                }
                            }
                        },
                        onSelectThreshold = { selectedPct ->
                            SoundEffectManager.playClick(context)
                            coroutineScope.launch {
                                settingsRepository.setBatteryAlarmThreshold(selectedPct)
                                if (chargingAlarmEnabled) {
                                    val serviceIntent = Intent(context, com.arwora.service.BatteryAlarmService::class.java).apply {
                                        action = com.arwora.service.BatteryAlarmService.ACTION_UPDATE_SETTINGS
                                    }
                                    runCatching {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            context.startForegroundService(serviceIntent)
                                        } else {
                                            context.startService(serviceIntent)
                                        }
                                    }
                                }
                            }
                        },
                        onOpenDetails = {
                            SoundEffectManager.playClick(context)
                            navigateGuarded(Screen.BatteryAlarm.route) {
                                navController.navigate(Screen.BatteryAlarm.route)
                            }
                        }
                    )
                }
            }

            // SOUND DIRECTION DETECTOR CARD
            item {
                DashboardStaggeredItem(
                    index = 19,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
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
            }

            // PERMISSION INSPECTOR CARD
            item {
                DashboardStaggeredItem(
                    index = 20,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
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
            }

            // ANIMATION SPEED CARD
            item {
                DashboardStaggeredItem(
                    index = 21,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    SystemFeatureCard(
                    title = "Animation Speed",
                    subtitle = "Window/Transition/Animator scales: ${animScale}x",
                    icon = Icons.Default.Speed,
                    badge = if (isPrivilegeReady) "ADB Ready" else "ADB Required",
                    badgeColor = if (isPrivilegeReady) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
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
                                                Toast.makeText(context, "Connect Shizuku or Wireless ADB first", Toast.LENGTH_SHORT).show()
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
            }

            // SECTION 3: PACKAGES & TOOLS
            item {
                DashboardStaggeredItem(
                    index = 22,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    SectionHeader(title = "Packages & Tools")
                }
            }

            // APP OPS MANAGER CARD
            item {
                DashboardStaggeredItem(
                    index = 23,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    SystemActionRowCard(
                        title = "App Ops",
                        subtitle = "Filter packages, permission controls & standby ops",
                        icon = Icons.Default.AdminPanelSettings,
                        iconColor = Color(0xFF10B981),
                        badge = if (isPrivilegeReady) "ADB Ready" else "ADB Required",
                        badgeColor = if (isPrivilegeReady) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        onClick = {
                            SoundEffectManager.playClick()
                            navController.navigate(Screen.AppOps.route)
                        }
                    )
                }
            }

            // BACKGROUND PROCESS & WAKELOCK DETECTOR CARD
            item {
                DashboardStaggeredItem(
                    index = 24,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    com.arwora.feature.system.process.WakelockDetectorCard(
                        navController = navController
                    )
                }
            }

            // SHELL COMMAND CARD
            item {
                DashboardStaggeredItem(
                    index = 25,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    ShellCommandCard(viewModel = shellViewModel)
                }
            }

            // HTML TO APK CARD
            item {
                DashboardStaggeredItem(
                    index = 26,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
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
            }

            // SMART APP INSTALLATION ASSISTANT CARD
            item {
                DashboardStaggeredItem(
                    index = 27,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    com.arwora.feature.system.installassistant.ui.SmartInstallAssistantCard(
                        onOpenAssistant = { initialPkg ->
                            navigateGuarded(Screen.InstallAssistant.route) {
                                navController.navigate(Screen.InstallAssistant.createRoute(initialPkg))
                            }
                        }
                    )
                }
            }

            // SMART DOWNLOAD WORKFLOW CARD
            item {
                DashboardStaggeredItem(
                    index = 28,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    com.arwora.feature.system.downloadworkflow.ui.SmartDownloadWorkflowCard(
                        onOpenWorkflowHub = { eventId ->
                            navigateGuarded(Screen.DownloadWorkflow.route) {
                                navController.navigate(Screen.DownloadWorkflow.createRoute(eventId))
                            }
                        }
                    )
                }
            }

            // ARWORA CONNECT OFFLINE P2P FILE SHARING HUB CARD
            item {
                DashboardStaggeredItem(
                    index = 29,
                    triggerAnimation = canPlayCardEntrance,
                    isInitialLoadBurst = isInitialStaggerWindowActive
                ) {
                    com.arwora.feature.system.connect.ui.ArworaConnectCard(
                        onOpenConnectHub = {
                            navigateGuarded(Screen.ArworaConnect.route) {
                                navController.navigate(Screen.ArworaConnect.route)
                            }
                        }
                    )
                }
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

    AdbPairingBottomSheet(
        visible = showAdbPairingSheet,
        initialTab = adbPairingSheetTab,
        onDismissRequest = { showAdbPairingSheet = false },
        onPairingComplete = {
            showAdbPairingSheet = false
            refreshDashboardStatuses()
        }
    )

    val activeWifiTrustPrompt = pendingWifiTrustPrompt

    if (activeWifiTrustPrompt != null) {
        var alwaysAllowOnNetwork by remember(activeWifiTrustPrompt.networkKey) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = {
                NativeAdbManager.dismissWifiTrustPrompt()
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.WifiLock,
                    contentDescription = "Allow Wireless Debugging on Wi-Fi Network",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Allow wireless debugging on this network?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Network: ${activeWifiTrustPrompt.ssid ?: "Active Wi-Fi Network"}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Android requires confirming Wi-Fi trust before exposing the local Wireless Debugging service (_adb-tls-connect._tcp). Open Wireless Debugging to accept Android's system dialog on this network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { alwaysAllowOnNetwork = !alwaysAllowOnNetwork }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = alwaysAllowOnNetwork,
                            onCheckedChange = { alwaysAllowOnNetwork = it },
                            modifier = Modifier.testTag("wifi_trust_always_allow_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Always allow on this network",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        SoundEffectManager.playClick()
                        NativeAdbManager.trustCurrentWifiNetwork(context, alwaysAllow = alwaysAllowOnNetwork)
                        coroutineScope.launch {
                            val (ok, msg) = WirelessPairingManager.connectUnifiedWirelessAdb(context)
                            refreshDashboardStatuses()
                            if (!ok) {
                                NativeAdbManager.openWirelessDebuggingSettings(context)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("wifi_trust_allow_connect_button")
                ) {
                    Text("Allow & Connect")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        NativeAdbManager.openWirelessDebuggingSettings(context)
                    },
                    modifier = Modifier.testTag("wifi_trust_open_settings_button")
                ) {
                    Text("Wireless Settings")
                }
            }
        )
    }

    if (showPrivilegeEngineDialog) {
        val shizukuRunning = shizukuBinderAlive || AdbShellExecutor.isShizukuRunning()
        val shizukuPermitted = shizukuPermissionGranted || AdbShellExecutor.hasShizukuPermission()
        val shizukuInstalled = AdbShellExecutor.isShizukuInstalled(context)
        AlertDialog(
            onDismissRequest = { showPrivilegeEngineDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Privilege & Shell Engine", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Arwora automatically executes privileged commands using Shizuku (Priority 1) or Native Wireless ADB (Priority 2).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Active Engine:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(activePrivilegeEngine.statusBadgeLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Shizuku Daemon:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = when (shizukuUiStatus) {
                                        ShizukuUiStatus.ACTIVE_CONNECTED -> ShizukuUiStatus.ACTIVE_CONNECTED.badgeText
                                        ShizukuUiStatus.PERMISSION_REQUIRED -> if (shizukuShouldShowRationale) {
                                            "Permission Required (Denied)"
                                        } else {
                                            ShizukuUiStatus.PERMISSION_REQUIRED.badgeText
                                        }
                                        ShizukuUiStatus.SERVICE_STOPPED -> if (shizukuInstalled) {
                                            ShizukuUiStatus.SERVICE_STOPPED.badgeText
                                        } else {
                                            "Not Installed"
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when (shizukuUiStatus) {
                                        ShizukuUiStatus.ACTIVE_CONNECTED -> Color(0xFF10B981)
                                        ShizukuUiStatus.PERMISSION_REQUIRED -> Color(0xFFF59E0B)
                                        ShizukuUiStatus.SERVICE_STOPPED -> MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Wireless ADB:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (isAdbConnected) "Connected" else "Offline",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (shizukuUiStatus == ShizukuUiStatus.SERVICE_STOPPED && !shizukuRunning) {
                                    showPrivilegeEngineDialog = false
                                }
                                handleConnectShizukuClick()
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = ArworaDesignTokens.PillShape
                        ) {
                            Text(
                                text = when {
                                    shizukuPermitted -> "Shizuku Connected"
                                    shizukuRunning -> "Connect Shizuku"
                                    shizukuInstalled -> "Connect Shizuku"
                                    else -> "Get Shizuku"
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                SoundEffectManager.playClick()
                                showPrivilegeEngineDialog = false
                                coroutineScope.launch {
                                    val (ok, msg) = WirelessPairingManager.connectUnifiedWirelessAdb(context)
                                    refreshDashboardStatuses()
                                    if (!ok) {
                                        adbPairingSheetTab = AdbPairingSheetTab.AUTOMATIC
                                        showAdbPairingSheet = true
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = ArworaDesignTokens.PillShape
                        ) {
                            Text("Wireless ADB", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        refreshDashboardStatuses()
                        showPrivilegeEngineDialog = false
                    }
                ) { Text("Done") }
            }
        )
    }

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
                topStart = ArworaDesignTokens.CardCornerRadius,
                topEnd = ArworaDesignTokens.CardCornerRadius,
                bottomStart = if (isExpanded) 0.dp else ArworaDesignTokens.CardCornerRadius,
                bottomEnd = if (isExpanded) 0.dp else ArworaDesignTokens.CardCornerRadius
            )
            val headerModifier = Modifier
                .fillMaxWidth()
                .clip(headerCornerShape)
                .let { if (headerTestTag != null) it.testTag(headerTestTag) else it }
                .clickable { onToggleExpand() }
                .padding(horizontal = 16.dp, vertical = 16.dp)

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

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
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
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.wrapContentWidth(unbounded = false)
                ) {
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
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(24.dp)
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
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
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
 * across all system dashboard tools, matching DNS Changer and Animation Speed.
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
                text = "A secure, user-controlled diagnostic environment. Toggle permissions, automate configurations, execute ADB shell operations, or manage local device workflows with direct, authorized control.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
internal fun PrivilegeEngineBridgeCard(
    engine: PrivilegeEngine,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shizukuUiStatus: ShizukuUiStatus? = null,
    onConnectShizukuClick: (() -> Unit)? = null
) {
    val badgeColor = when (engine) {
        PrivilegeEngine.SHIZUKU,
        PrivilegeEngine.WIRELESS_ADB -> Color(0xFF10B981)
        PrivilegeEngine.NONE -> Color(0xFFDC2626)
    }
    val subtitleText = when (engine) {
        PrivilegeEngine.SHIZUKU -> "Priority 1: Shizuku RISH active • Auto fallback ready"
        PrivilegeEngine.WIRELESS_ADB -> "Priority 2: Native Wireless ADB connected"
        PrivilegeEngine.NONE -> "Tap to authorize Shizuku or connect Wireless ADB"
    }

    SystemCard(
        modifier = modifier.testTag("system_privilege_engine_card"),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                        .background(badgeColor.copy(alpha = 0.10f))
                        .border(
                            width = 1.dp,
                            color = badgeColor.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Privilege Engine Status",
                        tint = badgeColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Privilege Engine",
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
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = ArworaDesignTokens.PillShape,
                    color = badgeColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f)),
                    modifier = Modifier.testTag("system_privilege_engine_badge")
                ) {
                    Text(
                        text = engine.statusBadgeLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = badgeColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (shizukuUiStatus != null && onConnectShizukuClick != null) {
                val shizukuColor = when (shizukuUiStatus) {
                    ShizukuUiStatus.ACTIVE_CONNECTED -> Color(0xFF10B981)
                    ShizukuUiStatus.PERMISSION_REQUIRED -> Color(0xFFF59E0B)
                    ShizukuUiStatus.SERVICE_STOPPED -> Color(0xFFDC2626)
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = ArworaDesignTokens.cardBorderColor().copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("shizuku_connection_section"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Shizuku Connection",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = ArworaDesignTokens.PillShape,
                            color = shizukuColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, shizukuColor.copy(alpha = 0.35f)),
                            modifier = Modifier.testTag("shizuku_status_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(shizukuColor)
                                )
                                Text(
                                    text = shizukuUiStatus.badgeText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = shizukuColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onConnectShizukuClick,
                        shape = ArworaDesignTokens.PillShape,
                        colors = if (shizukuUiStatus == ShizukuUiStatus.ACTIVE_CONNECTED) {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF10B981).copy(alpha = 0.16f),
                                contentColor = Color(0xFF10B981)
                            )
                        } else {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("connect_shizuku_button")
                    ) {
                        Text(
                            text = if (shizukuUiStatus == ShizukuUiStatus.ACTIVE_CONNECTED) {
                                "Connected"
                            } else {
                                "Connect Shizuku"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShizukuDebuggingTogglesCard(
    isUsbDebuggingEnabled: Boolean,
    isWirelessDebuggingEnabled: Boolean,
    isSessionForgotten: Boolean = false,
    onOpenDeveloperOptions: () -> Unit,
    onToggleUsbDebugging: () -> Unit,
    onToggleWirelessDebugging: () -> Unit,
    onOpenPairDialog: () -> Unit,
    onResetAndPairNewDevice: () -> Unit,
    modifier: Modifier = Modifier
) {
    SystemCard(modifier = modifier.testTag("shizuku_debugging_toggles_card")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "Debugging Quick Controls",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isSessionForgotten) {
                            "Device pairing forgotten or rejected — tap Pair New Device"
                        } else {
                            "Toggle USB/Wireless Debugging or pair directly"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSessionForgotten) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val overallActive = isUsbDebuggingEnabled || isWirelessDebuggingEnabled
                val pillColor = if (overallActive) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    shape = ArworaDesignTokens.PillShape,
                    color = pillColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, pillColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = if (isWirelessDebuggingEnabled) "Wireless ON" else if (isUsbDebuggingEnabled) "USB ON" else "Standby",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = pillColor,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // 3 sleek Shizuku-style pill toggle buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenDeveloperOptions,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("toggle_pill_developer_options"),
                    shape = ArworaDesignTokens.PillShape,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Developer Options",
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onToggleUsbDebugging,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("toggle_pill_usb_debugging"),
                    shape = ArworaDesignTokens.PillShape,
                    colors = if (isUsbDebuggingEnabled) {
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            contentColor = Color.White
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "USB Debugging",
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onToggleWirelessDebugging,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("toggle_pill_wireless_debugging"),
                    shape = ArworaDesignTokens.PillShape,
                    colors = if (isWirelessDebuggingEnabled) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Wireless Debugging",
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenPairDialog,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_adb_pairing_sheet_button"),
                    shape = ArworaDesignTokens.PillShape
                ) {
                    Text(
                        text = "Pair with device",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onResetAndPairNewDevice,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("quick_controls_pair_new_device_button"),
                    shape = ArworaDesignTokens.PillShape
                ) {
                    Text(
                        text = "Pair New Device",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardStaggeredItem(
    index: Int,
    triggerAnimation: Boolean,
    isInitialLoadBurst: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedListItem(
        index = index,
        triggerAnimation = triggerAnimation,
        staggerStepMs = if (isInitialLoadBurst) ArworaMotionTokens.LIST_STAGGER_STEP_MS else 0L,
        maxStaggerItems = ArworaMotionTokens.MAX_LIST_STAGGER_ITEMS,
        durationMillis = if (isInitialLoadBurst) ArworaMotionTokens.LIST_ENTRY_DURATION_MS else 160,
        initialOffsetDp = if (isInitialLoadBurst) 22.dp else 14.dp,
        modifier = modifier.testTag("dashboard_staggered_item_$index"),
        content = content
    )
}


