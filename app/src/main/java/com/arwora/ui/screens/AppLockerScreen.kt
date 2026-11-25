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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.arwora.ui.AppManagerViewModel
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.PinAuthDialog
import com.arwora.ui.components.PinSetupDialog
import com.arwora.utils.AppLockerManager
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockerScreen(
    navController: NavController,
    appManagerViewModel: AppManagerViewModel
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        AppLockerManager.init(context)
        appManagerViewModel.loadInstalledApps()
    }

    val isLockerEnabled by AppLockerManager.isLockerEnabled.collectAsStateWithLifecycle()
    val isBiometricEnabled by AppLockerManager.isBiometricEnabled.collectAsStateWithLifecycle()
    val hasPin by AppLockerManager.hasPin.collectAsStateWithLifecycle()
    val lockedPackages by AppLockerManager.lockedPackages.collectAsStateWithLifecycle()
    val lockedToolIds by AppLockerManager.lockedToolIds.collectAsStateWithLifecycle()
    val timeoutMinutes by AppLockerManager.timeoutMinutes.collectAsStateWithLifecycle()
    val vibrateOnError by AppLockerManager.vibrateOnError.collectAsStateWithLifecycle()
    val scrambleKeypad by AppLockerManager.scrambleKeypad.collectAsStateWithLifecycle()
    val installedApps by appManagerViewModel.installedApps.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Tools, 1: Apps, 2: Security
    var searchQuery by remember { mutableStateOf("") }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showTestUnlockDialog by remember { mutableStateOf(false) }
    var showSuccessBanner by remember { mutableStateOf(false) }
    var bannerMessage by remember { mutableStateOf("") }

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
