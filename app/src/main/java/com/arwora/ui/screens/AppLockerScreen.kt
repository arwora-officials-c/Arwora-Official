package com.arwora.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.arwora.deviceadmin.AppDeviceAdminReceiver
import com.arwora.service.AppProtectionAccessibilityService
import com.arwora.ui.AppManagerViewModel
import com.arwora.ui.MainViewModel
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.PinAuthDialog
import com.arwora.ui.components.PinSetupDialog
import com.arwora.utils.AppHiderManager
import com.arwora.utils.AppLockerManager
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PendingSecurityAuthAction {
    OPEN_HIDDEN_VAULT,
    DISABLE_UNINSTALL_PROTECTION,
    DISABLE_INSTALL_BLOCK
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockerScreen(
    navController: NavController,
    appManagerViewModel: AppManagerViewModel,
    mainViewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var isAccessibilityGuardEnabled by remember {
        mutableStateOf(AppProtectionAccessibilityService.isAccessibilityEnabled(context))
    }
    var isDeviceAdminEnabled by remember {
        mutableStateOf(AppDeviceAdminReceiver.isDeviceAdminActive(context))
    }

    LaunchedEffect(Unit) {
        AppLockerManager.init(context)
        AppHiderManager.init(context)
        appManagerViewModel.loadInstalledApps()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityGuardEnabled = AppProtectionAccessibilityService.isAccessibilityEnabled(context)
                isDeviceAdminEnabled = AppDeviceAdminReceiver.isDeviceAdminActive(context)
                AppHiderManager.refreshPrivilegeMode(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isLockerEnabled by AppLockerManager.isLockerEnabled.collectAsStateWithLifecycle()
    val isBiometricEnabled by AppLockerManager.isBiometricEnabled.collectAsStateWithLifecycle()
    val hasPin by AppLockerManager.hasPin.collectAsStateWithLifecycle()
    val lockedPackages by AppLockerManager.lockedPackages.collectAsStateWithLifecycle()
    val lockedToolIds by AppLockerManager.lockedToolIds.collectAsStateWithLifecycle()
    val timeoutMinutes by AppLockerManager.timeoutMinutes.collectAsStateWithLifecycle()
    val vibrateOnError by AppLockerManager.vibrateOnError.collectAsStateWithLifecycle()
    val scrambleKeypad by AppLockerManager.scrambleKeypad.collectAsStateWithLifecycle()
    val isUninstallProtectionEnabled by AppLockerManager.isUninstallProtectionEnabled.collectAsStateWithLifecycle()
    val isInstallBlockEnabled by AppLockerManager.isInstallBlockEnabled.collectAsStateWithLifecycle()
    val isSelfStealthEnabled by AppLockerManager.isSelfStealthEnabled.collectAsStateWithLifecycle()
    val secretDialerCode by AppLockerManager.secretDialerCode.collectAsStateWithLifecycle()
    val hiddenPackages by AppHiderManager.hiddenPackages.collectAsStateWithLifecycle()
    val privilegeMode by AppHiderManager.privilegeMode.collectAsStateWithLifecycle()
    val installedApps by appManagerViewModel.installedApps.collectAsStateWithLifecycle()

    val pendingHiddenVaultNav = mainViewModel?.pendingHiddenVaultNav?.collectAsStateWithLifecycle()?.value ?: false

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Tools, 1: Apps, 2: Security
    var searchQuery by remember { mutableStateOf("") }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showTestUnlockDialog by remember { mutableStateOf(false) }
    var showHiddenVaultSheet by remember { mutableStateOf(false) }
    var pendingSecurityAuth by remember { mutableStateOf<PendingSecurityAuthAction?>(null) }
    var showSuccessBanner by remember { mutableStateOf(false) }
    var bannerMessage by remember { mutableStateOf("") }

    fun requestOpenVault() {
        if (hasPin) {
            pendingSecurityAuth = PendingSecurityAuthAction.OPEN_HIDDEN_VAULT
        } else {
            showHiddenVaultSheet = true
        }
    }

    LaunchedEffect(pendingHiddenVaultNav) {
        if (pendingHiddenVaultNav) {
            mainViewModel?.consumePendingHiddenVault()
            requestOpenVault()
        }
    }

    val biometricStatus = remember(context) { AppLockerManager.checkBiometricStatus(context) }

    val cardBgColor = ArworaDesignTokens.cardContainerColor()
    val cardShape = ArworaDesignTokens.CardShape
    val cardBorder = ArworaDesignTokens.cardBorder()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "App Locker",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isLockerEnabled) "Protected with PIN & Biometrics" else "Protection is disabled",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLockerEnabled) {
                                Color(0xFF10B981)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            navController.popBackStack()
                        },
                        modifier = Modifier.testTag("app_locker_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            requestOpenVault()
                        },
                        modifier = Modifier.testTag("top_bar_hidden_vault_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Hidden Apps Vault",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (isLockerEnabled) {
                        IconButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                showTestUnlockDialog = true
                            },
                            modifier = Modifier.testTag("test_unlock_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "Test Unlock",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
            ) {
                // Unified Hero Protection Banner Card (Master toggle + inline PIN/Biometric status strip)
                AppLockerHeroBannerCard(
                    isLockerEnabled = isLockerEnabled,
                    lockedToolCount = lockedToolIds.size,
                    lockedAppCount = lockedPackages.size,
                    hasPin = hasPin,
                    isBiometricEnabled = isBiometricEnabled,
                    biometricStatus = biometricStatus,
                    onToggleMaster = { checked ->
                        SoundEffectManager.playClick()
                        if (checked && !hasPin) {
                            showPinSetupDialog = true
                        } else {
                            AppLockerManager.setLockerEnabled(context, checked)
                            bannerMessage = if (checked) {
                                "App Locker successfully activated"
                            } else {
                                "App Locker deactivated"
                            }
                            showSuccessBanner = true
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Modern Segmented Pill Tab Selector
                AppLockerSegmentedTabSelector(
                    selectedTab = selectedTab,
                    lockedToolCount = lockedToolIds.size,
                    lockedAppCount = lockedPackages.size,
                    onSelectTab = { newTab ->
                        SoundEffectManager.playClick()
                        selectedTab = newTab
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                // Tab Content
                when (selectedTab) {
                    0 -> ToolsLockerTab(
                        cardShape = cardShape,
                        cardBorder = cardBorder,
                        cardBgColor = cardBgColor,
                        lockedToolIds = lockedToolIds,
                        onToggleTool = { toolId ->
                            SoundEffectManager.playClick()
                            val locked = AppLockerManager.toggleToolLock(context, toolId)
                            if (!isLockerEnabled && locked) {
                                if (!hasPin) showPinSetupDialog = true
                                else AppLockerManager.setLockerEnabled(context, true)
                            }
                        },
                        onLockAll = {
                            SoundEffectManager.playClick()
                            AppLockerManager.lockAllTools(context)
                            if (!hasPin) showPinSetupDialog = true
                            else if (!isLockerEnabled) AppLockerManager.setLockerEnabled(context, true)
                        },
                        onUnlockAll = {
                            SoundEffectManager.playClick()
                            AppLockerManager.unlockAllTools(context)
                        }
                    )
                    1 -> AppsLockerTab(
                        cardShape = cardShape,
                        cardBorder = cardBorder,
                        cardBgColor = cardBgColor,
                        installedApps = installedApps,
                        lockedPackages = lockedPackages,
                        hiddenCount = hiddenPackages.size,
                        secretDialerCode = secretDialerCode,
                        onOpenVault = {
                            SoundEffectManager.playClick()
                            requestOpenVault()
                        },
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onToggleApp = { pkg ->
                            SoundEffectManager.playClick()
                            val locked = AppLockerManager.toggleAppLock(context, pkg)
                            if (!isLockerEnabled && locked) {
                                if (!hasPin) showPinSetupDialog = true
                                else AppLockerManager.setLockerEnabled(context, true)
                            }
                        }
                    )
                    2 -> SecuritySettingsTab(
                        cardShape = cardShape,
                        cardBorder = cardBorder,
                        cardBgColor = cardBgColor,
                        hasPin = hasPin,
                        isBiometricEnabled = isBiometricEnabled,
                        timeoutMinutes = timeoutMinutes,
                        vibrateOnError = vibrateOnError,
                        scrambleKeypad = scrambleKeypad,
                        biometricStatus = biometricStatus,
                        hiddenAppCount = hiddenPackages.size,
                        secretDialerCode = secretDialerCode,
                        isSelfStealthEnabled = isSelfStealthEnabled,
                        isUninstallProtectionEnabled = isUninstallProtectionEnabled,
                        isInstallBlockEnabled = isInstallBlockEnabled,
                        isAccessibilityGuardEnabled = isAccessibilityGuardEnabled,
                        isDeviceAdminEnabled = isDeviceAdminEnabled,
                        onSetupPin = { showPinSetupDialog = true },
                        onToggleBiometric = {
                            SoundEffectManager.playClick()
                            AppLockerManager.setBiometricEnabled(context, it)
                        },
                        onTimeoutSelect = {
                            SoundEffectManager.playClick()
                            AppLockerManager.setTimeoutMinutes(context, it)
                        },
                        onToggleVibrate = {
                            SoundEffectManager.playClick()
                            AppLockerManager.setVibrateOnError(context, it)
                        },
                        onToggleScramble = {
                            SoundEffectManager.playClick()
                            AppLockerManager.setScrambleKeypad(context, it)
                        },
                        onTestUnlock = {
                            SoundEffectManager.playClick()
                            showTestUnlockDialog = true
                        },
                        onOpenHiddenVault = {
                            SoundEffectManager.playClick()
                            requestOpenVault()
                        },
                        onToggleUninstallProtection = { checked ->
                            SoundEffectManager.playClick()
                            if (checked) {
                                if (!hasPin) {
                                    showPinSetupDialog = true
                                }
                                AppLockerManager.setUninstallProtectionEnabled(context, true)
                                bannerMessage = "Uninstall Protection activated"
                                showSuccessBanner = true
                            } else if (hasPin) {
                                pendingSecurityAuth = PendingSecurityAuthAction.DISABLE_UNINSTALL_PROTECTION
                            } else {
                                AppLockerManager.setUninstallProtectionEnabled(context, false)
                                bannerMessage = "Uninstall Protection disabled"
                                showSuccessBanner = true
                            }
                        },
                        onToggleInstallBlock = { checked ->
                            SoundEffectManager.playClick()
                            if (checked) {
                                if (!hasPin) {
                                    showPinSetupDialog = true
                                }
                                AppLockerManager.setInstallBlockEnabled(context, true)
                                bannerMessage = "New App Install Block activated"
                                showSuccessBanner = true
                            } else if (hasPin) {
                                pendingSecurityAuth = PendingSecurityAuthAction.DISABLE_INSTALL_BLOCK
                            } else {
                                AppLockerManager.setInstallBlockEnabled(context, false)
                                bannerMessage = "New App Install Block disabled"
                                showSuccessBanner = true
                            }
                        },
                        onOpenAccessibilitySettings = {
                            SoundEffectManager.playClick()
                            AppProtectionAccessibilityService.openAccessibilitySettings(context)
                        },
                        onRequestDeviceAdmin = {
                            SoundEffectManager.playClick()
                            AppDeviceAdminReceiver.requestDeviceAdminActivation(context)
                        }
                    )
                }
            }

            // Success Banner at the Top
            SuccessBanner(
                message = bannerMessage,
                visible = showSuccessBanner,
                onDismiss = { showSuccessBanner = false }
            )
        }
    }

    if (showPinSetupDialog) {
        PinSetupDialog(
            onDismiss = { showPinSetupDialog = false },
            onPinSet = {
                showPinSetupDialog = false
                bannerMessage = "PIN successfully configured & App Locker activated"
                showSuccessBanner = true
            }
        )
    }

    if (showTestUnlockDialog) {
        PinAuthDialog(
            title = "Test Unlock",
            subtitle = "Verify your PIN or Biometric setup",
            onDismiss = { showTestUnlockDialog = false },
            onSuccess = {
                showTestUnlockDialog = false
                bannerMessage = "Verification Successful! App Locker is working perfectly."
                showSuccessBanner = true
            }
        )
    }

    pendingSecurityAuth?.let { action ->
        val (authTitle, authSubtitle) = when (action) {
            PendingSecurityAuthAction.OPEN_HIDDEN_VAULT ->
                "Hidden Apps Vault" to "Verify identity to unlock hidden apps"
            PendingSecurityAuthAction.DISABLE_UNINSTALL_PROTECTION ->
                "Disable Uninstall Guard" to "Verify identity before disabling Uninstall Protection"
            PendingSecurityAuthAction.DISABLE_INSTALL_BLOCK ->
                "Disable Install Block" to "Verify identity before disabling Install Block"
        }
        PinAuthDialog(
            title = authTitle,
            subtitle = authSubtitle,
            onDismiss = { pendingSecurityAuth = null },
            onSuccess = {
                pendingSecurityAuth = null
                when (action) {
                    PendingSecurityAuthAction.OPEN_HIDDEN_VAULT -> {
                        showHiddenVaultSheet = true
                    }
                    PendingSecurityAuthAction.DISABLE_UNINSTALL_PROTECTION -> {
                        AppLockerManager.setUninstallProtectionEnabled(context, false)
                        bannerMessage = "Uninstall Protection disabled"
                        showSuccessBanner = true
                    }
                    PendingSecurityAuthAction.DISABLE_INSTALL_BLOCK -> {
                        AppLockerManager.setInstallBlockEnabled(context, false)
                        bannerMessage = "New App Install Block disabled"
                        showSuccessBanner = true
                    }
                }
            }
        )
    }

    if (showHiddenVaultSheet) {
        HiddenAppsVaultSheet(
            installedApps = installedApps,
            hiddenPackages = hiddenPackages,
            privilegeMode = privilegeMode,
            isSelfStealthEnabled = isSelfStealthEnabled,
            secretDialerCode = secretDialerCode,
            onDismiss = { showHiddenVaultSheet = false },
            onToggleSelfStealth = { enabled ->
                SoundEffectManager.playClick()
                val res = AppHiderManager.setSelfStealthEnabled(context, enabled)
                bannerMessage = res.message
                showSuccessBanner = true
            },
            onHideApp = { pkg ->
                SoundEffectManager.playClick()
                coroutineScope.launch {
                    val res = AppHiderManager.hideApp(context, pkg)
                    bannerMessage = res.message
                    showSuccessBanner = true
                }
            },
            onUnhideApp = { pkg ->
                SoundEffectManager.playClick()
                coroutineScope.launch {
                    val res = AppHiderManager.unhideApp(context, pkg)
                    bannerMessage = res.message
                    showSuccessBanner = true
                }
            },
            onLaunchHiddenApp = { pkg ->
                SoundEffectManager.playClick()
                coroutineScope.launch {
                    val res = AppHiderManager.launchHiddenApp(context, pkg)
                    bannerMessage = res.message
                    showSuccessBanner = true
                }
            }
        )
    }
}

@Composable
fun SuccessBanner(
    message: String,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF10B981),
            contentColor = Color.White,
            tonalElevation = 8.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (visible) {
        LaunchedEffect(message) {
            delay(3000L)
            onDismiss()
        }
    }
}
