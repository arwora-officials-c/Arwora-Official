package com.arwora.ui.screens

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.arwora.ui.AppManagerViewModel
import com.arwora.ui.components.ArworaAnimatedSwitch
import com.arwora.ui.components.PinAuthDialog
import com.arwora.ui.components.PinSetupDialog
import com.arwora.utils.AppLockerManager
import com.arwora.utils.BiometricStatus
import com.arwora.utils.LockableTool
import com.arwora.utils.SoundEffectManager

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

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Tools & Features, 1: Installed Apps, 2: Settings
    var searchQuery by remember { mutableStateOf("") }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showTestUnlockDialog by remember { mutableStateOf(false) }
    var showSuccessBanner by remember { mutableStateOf(false) }
    var bannerMessage by remember { mutableStateOf("") }

    val biometricStatus = remember(context) { AppLockerManager.checkBiometricStatus(context) }

    // Consistent Card Styling Helper
    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val outlineAlpha = if (isLight) 0.25f else 0.4f
    val cardBgColor = if (isLight) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    val cardShape = RoundedCornerShape(22.dp)
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = outlineAlpha))

    Scaffold(
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
                            color = if (isLockerEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
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
                            Icon(Icons.Default.VpnKey, contentDescription = "Test Unlock", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Master Switch & Status Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("master_lock_card"),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = cardBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isLockerEnabled) Color(0xFF10B981).copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                )
                                .border(
                                    1.dp,
                                    if (isLockerEnabled) Color(0xFF10B981).copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isLockerEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isLockerEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isLockerEnabled) "App Locker Active" else "App Locker Inactive",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isLockerEnabled) {
                                    "${lockedToolIds.size} tools & ${lockedPackages.size} apps secured"
                                } else {
                                    "Enable to safeguard tools & apps"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        ArworaAnimatedSwitch(
                            checked = isLockerEnabled,
                            onCheckedChange = { checked ->
                                SoundEffectManager.playClick()
                                if (checked && !hasPin) {
                                    // Must set PIN first
                                    showPinSetupDialog = true
                                } else {
                                    AppLockerManager.setLockerEnabled(context, checked)
                                    bannerMessage = if (checked) "App Locker successfully activated" else "App Locker deactivated"
                                    showSuccessBanner = true
                                }
                            },
                            modifier = Modifier.testTag("app_locker_master_switch")
                        )
                    }
                }

                // Quick Stats / Info Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = cardBgColor,
                        border = cardBorder
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Pin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("PIN Status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (hasPin) "Configured" else "Not Set", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = cardBgColor,
                        border = cardBorder
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = if (isBiometricEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Biometrics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    if (isBiometricEnabled && biometricStatus == BiometricStatus.AVAILABLE) "Enabled"
                                    else if (!isBiometricEnabled) "Disabled"
                                    else "Setup Needed",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Bordered Segmented Navigation Tabs Container
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("app_locker_tab_container"),
                    shape = RoundedCornerShape(16.dp),
                    color = cardBgColor,
                    border = cardBorder
                ) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            if (selectedTab < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                    height = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        divider = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                SoundEffectManager.playClick()
                                selectedTab = 0
                            },
                            text = {
                                Text(
                                    "Tools (${lockedToolIds.size})",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            icon = { Icon(Icons.Default.Widgets, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_tools")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                SoundEffectManager.playClick()
                                selectedTab = 1
                            },
                            text = {
                                Text(
                                    "Apps (${lockedPackages.size})",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            icon = { Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_apps")
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = {
                                SoundEffectManager.playClick()
                                selectedTab = 2
                            },
                            text = {
                                Text(
                                    "Security",
                                    fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            icon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("tab_security")
                        )
                    }
                }

                // Content per tab
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
            color = Color(0xFF10B981), // Emerald 500
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
            kotlinx.coroutines.delay(3000L)
            onDismiss()
        }
    }
}

@Composable
fun ToolsLockerTab(
    cardShape: RoundedCornerShape,
    cardBorder: BorderStroke,
    cardBgColor: Color,
    lockedToolIds: Set<String>,
    onToggleTool: (String) -> Unit,
    onLockAll: () -> Unit,
    onUnlockAll: () -> Unit
) {
    val tools = remember { AppLockerManager.availableTools }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Tools & Features",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onLockAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Lock All", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onUnlockAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Unlock All", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        items(tools, key = { it.id }) { tool ->
            val isLocked = lockedToolIds.contains(tool.id)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleTool(tool.id) }
                    .testTag("tool_lock_item_${tool.id}"),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = cardBorder
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isLocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                1.dp,
                                if (isLocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.10f),
                                RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tool.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ) {
                                Text(
                                    text = tool.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tool.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    ArworaAnimatedSwitch(
                        checked = isLocked,
                        onCheckedChange = { onToggleTool(tool.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun AppsLockerTab(
    cardShape: RoundedCornerShape,
    cardBorder: BorderStroke,
    cardBgColor: Color,
    installedApps: List<com.arwora.ui.AppItem>,
    lockedPackages: Set<String>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onToggleApp: (String) -> Unit
) {
    val filteredApps = remember(installedApps, lockedPackages, searchQuery) {
        val query = searchQuery.trim().lowercase()
        installedApps.filter { app ->
            if (query.isEmpty()) true
            else app.appName.lowercase().contains(query) || app.packageName.lowercase().contains(query)
        }.sortedByDescending { lockedPackages.contains(it.packageName) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_locker_search_input"),
                placeholder = { Text("Search installed apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (filteredApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (installedApps.isEmpty()) "Loading installed applications..." else "No apps match search",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredApps, key = { it.packageName }) { app ->
                val isLocked = lockedPackages.contains(app.packageName)
                val bitmap = remember(app.icon) { app.icon?.toBitmap() }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleApp(app.packageName) }
                        .testTag("app_lock_item_${app.packageName}"),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = cardBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = app.appName,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        ArworaAnimatedSwitch(
                            checked = isLocked,
                            onCheckedChange = { onToggleApp(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecuritySettingsTab(
    cardShape: RoundedCornerShape,
    cardBorder: BorderStroke,
    cardBgColor: Color,
    hasPin: Boolean,
    isBiometricEnabled: Boolean,
    timeoutMinutes: Int,
    vibrateOnError: Boolean,
    scrambleKeypad: Boolean,
    biometricStatus: BiometricStatus,
    onSetupPin: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onTimeoutSelect: (Int) -> Unit,
    onToggleVibrate: (Boolean) -> Unit,
    onToggleScramble: (Boolean) -> Unit,
    onTestUnlock: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // PIN Authentication Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = cardBorder
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Pin, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Security PIN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (hasPin) "4-digit passcode configured" else "No passcode set yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = onSetupPin,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (hasPin) "Change PIN" else "Set PIN", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Biometrics Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = cardBorder
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Biometric Authentication", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                when (biometricStatus) {
                                    BiometricStatus.AVAILABLE -> "Fingerprint / Face Unlock ready"
                                    BiometricStatus.NOT_ENROLLED -> "No fingerprint enrolled on device"
                                    else -> "Hardware not supported"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ArworaAnimatedSwitch(
                            checked = isBiometricEnabled,
                            onCheckedChange = onToggleBiometric
                        )
                    }
                }
            }
        }

        // Relock Timeout Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = cardBorder
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Auto-Relock Timeout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("When to prompt for authentication again after unlocking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    val timeouts = listOf(
                        0 to "Immediately",
                        1 to "1 Minute",
                        5 to "5 Minutes",
                        15 to "15 Minutes"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        timeouts.forEach { (mins, label) ->
                            val selected = (timeoutMinutes == mins)
                            OutlinedButton(
                                onClick = { onTimeoutSelect(mins) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent
                                ),
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Additional Security Options Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = cardBorder
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Protection Options", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vibrate on Failed Attempt", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Haptic feedback on incorrect PIN entry", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ArworaAnimatedSwitch(checked = vibrateOnError, onCheckedChange = onToggleVibrate)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scramble Keypad", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Randomizes PIN digits to prevent shoulder-surfing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ArworaAnimatedSwitch(checked = scrambleKeypad, onCheckedChange = onToggleScramble)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

                    Button(
                        onClick = onTestUnlock,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test App Locker Authentication", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
