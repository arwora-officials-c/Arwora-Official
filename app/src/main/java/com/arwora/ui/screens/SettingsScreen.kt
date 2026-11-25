package com.arwora.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import com.arwora.ui.MainViewModel
import com.arwora.ui.components.AppLogo
import com.arwora.ui.components.ArworaAnimatedSwitch
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.navigation.Screen
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.AppPermissionsHelper
import com.arwora.utils.ManagedAppPermission
import com.arwora.utils.ManagedPermissionCategory
import com.arwora.utils.PrivilegeEngine
import com.arwora.utils.ShizukuBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, viewModel: MainViewModel) {
    val t0 = remember { SystemClock.uptimeMillis() }
    SideEffect {
        Log.d("Tabs", "SettingsScreen switched in ${SystemClock.uptimeMillis() - t0}ms")
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onSettingsVisible()
        }
    }

    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val whiteTheme by viewModel.whiteTheme.collectAsStateWithLifecycle()
    val hapticFeedback by viewModel.hapticFeedback.collectAsStateWithLifecycle()
    val soundEffects by viewModel.soundEffects.collectAsStateWithLifecycle()
    val animations by viewModel.animationsEnabled.collectAsStateWithLifecycle()
    val floatingMode by viewModel.floatingModeEnabled.collectAsStateWithLifecycle()
    val rememberPos by viewModel.rememberFloatingPosition.collectAsStateWithLifecycle()
    val defaultSize by viewModel.defaultFloatingSize.collectAsStateWithLifecycle()
    val uiColorLong by viewModel.uiColor.collectAsStateWithLifecycle()
    val buttonColorLong by viewModel.buttonColor.collectAsStateWithLifecycle()
    
    val history by viewModel.allHistory.collectAsStateWithLifecycle()
    val favorites by viewModel.allFavorites.collectAsStateWithLifecycle()
    
    val context = LocalContext.current

    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showClearFavDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showResetColorsDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var originalUiColor by remember { mutableStateOf(uiColorLong) }
    var originalButtonColor by remember { mutableStateOf(buttonColorLong) }
    val coroutineScope = rememberCoroutineScope()
    var isPermissionsExpanded by remember { mutableStateOf(false) }
    var appPermissions by remember { mutableStateOf<List<ManagedAppPermission>>(emptyList()) }
    var permissionsLoaded by remember { mutableStateOf(false) }
    var pendingPermissionCategory by remember { mutableStateOf<ManagedPermissionCategory?>(null) }
    var pendingQuickFixNextStep by remember { mutableStateOf(false) }
    var isBulkPermissionBusy by remember { mutableStateOf(false) }
    var showRevokeAllConfirmDialog by remember { mutableStateOf(false) }
    var remainingSpecialPermissionsDialogList by remember { mutableStateOf<List<ManagedAppPermission>>(emptyList()) }
    val activePrivilegeEngine by AdbShellExecutor.activeEngine.collectAsStateWithLifecycle()
    val shizukuBinderAlive by ShizukuBridge.isBinderAlive.collectAsStateWithLifecycle()
    val shizukuPermissionGranted by ShizukuBridge.isPermissionGranted.collectAsStateWithLifecycle()

    var showSafFallbackDialog by remember { mutableStateOf(false) }

    val safTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val saved = AppPermissionsHelper.persistSafTreeUri(context, treeUri)
            coroutineScope.launch {
                val loaded = withContext(Dispatchers.IO) {
                    AppPermissionsHelper.inspectPermissions(context)
                }
                appPermissions = loaded
                permissionsLoaded = true
                Toast.makeText(
                    context,
                    if (saved) "Folder access granted via Scoped Storage (SAF)" else "Unable to persist folder permission",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val allFilesAccessSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        coroutineScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                AppPermissionsHelper.inspectPermissions(context)
            }
            appPermissions = loaded
            permissionsLoaded = true
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
                !AppPermissionsHelper.hasAllFilesAccess(context) &&
                !AppPermissionsHelper.hasSafDirectoryGrant(context)
            ) {
                showSafFallbackDialog = true
            }
        }
    }

    val singleCategoryRuntimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val category = pendingPermissionCategory
        pendingPermissionCategory = null
        coroutineScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                AppPermissionsHelper.inspectPermissions(context)
            }
            appPermissions = loaded
            permissionsLoaded = true
            val anyGranted = results.values.any { it }
            if (!anyGranted && category != null && results.isNotEmpty()) {
                val activity = context as? Activity
                val canShowRationale = activity != null && results.keys.any { perm ->
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
                }
                if (!canShowRationale) {
                    if (category == ManagedPermissionCategory.STORAGE &&
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                    ) {
                        val ok = AppPermissionsHelper.openAllFilesAccessWithSafFallback(
                            context = context,
                            onSafFallback = {
                                safTreeLauncher.launch(AppPermissionsHelper.getPersistedSafTreeUri(context))
                            }
                        )
                        if (!ok) {
                            showSafFallbackDialog = true
                        }
                    } else {
                        AppPermissionsHelper.openPermissionSettings(context, category)
                    }
                }
            }
        }
    }

    val quickFixRuntimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val continueToSystemSettings = pendingQuickFixNextStep
        pendingQuickFixNextStep = false
        coroutineScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                AppPermissionsHelper.inspectPermissions(context)
            }
            appPermissions = loaded
            permissionsLoaded = true
            if (continueToSystemSettings) {
                val remainingSpecial = AppPermissionsHelper.findRemainingSpecialPermissions(loaded)
                if (remainingSpecial.isNotEmpty()) {
                    remainingSpecialPermissionsDialogList = remainingSpecial
                } else if (loaded.all { it.isGranted }) {
                    Toast.makeText(
                        context,
                        "All permissions granted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(context) {
        val loaded = withContext(Dispatchers.IO) {
            AppPermissionsHelper.inspectPermissions(context)
        }
        appPermissions = loaded
        permissionsLoaded = true
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    val loaded = withContext(Dispatchers.IO) {
                        AppPermissionsHelper.inspectPermissions(context)
                    }
                    appPermissions = loaded
                    permissionsLoaded = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Performance, Game, Display, and Thermal states (Removed)

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Display Over Other Apps") },
            text = { Text("Display over other apps permission is required to use Floating Mode over other apps.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }) { Text("Allow Permission") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSafFallbackDialog) {
        AlertDialog(
            onDismissRequest = { showSafFallbackDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Folder Access Fallback (SAF)") },
            text = {
                Text(
                    "If \"All files access\" is restricted or unavailable on your device (common on sideloaded builds), select a working directory with Android Scoped Storage instead."
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("settings_saf_fallback_button"),
                    onClick = {
                        showSafFallbackDialog = false
                        try {
                            safTreeLauncher.launch(AppPermissionsHelper.getPersistedSafTreeUri(context))
                        } catch (_: Exception) {
                            AppPermissionsHelper.openAppDetailsSettings(context)
                        }
                    }
                ) { Text("Select Folder (SAF)") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showSafFallbackDialog = false
                            AppPermissionsHelper.openAppDetailsSettings(context)
                        }
                    ) { Text("App Info") }
                    TextButton(onClick = { showSafFallbackDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    if (showRevokeAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeAllConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Disable App Permissions?") },
            text = { Text("Disable all app permissions and pause background listeners?") },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("settings_confirm_all_off_button"),
                    onClick = {
                        showRevokeAllConfirmDialog = false
                        isBulkPermissionBusy = true
                        coroutineScope.launch {
                            viewModel.setFloatingMode(false)
                            withContext(Dispatchers.IO) {
                                AppPermissionsHelper.stopPermissionDependentServices(context)
                            }
                            if (AdbShellExecutor.isPrivilegedShellReady(context)) {
                                val usedEngine = AdbShellExecutor.resolveActiveEngine(context)
                                val adbResult = withContext(Dispatchers.IO) {
                                    AppPermissionsHelper.revokeAllPermissionsViaAdb(context)
                                }
                                val reloaded = withContext(Dispatchers.IO) {
                                    AppPermissionsHelper.inspectPermissions(context)
                                }
                                appPermissions = reloaded
                                permissionsLoaded = true
                                isBulkPermissionBusy = false
                                val engineName = if (usedEngine == PrivilegeEngine.SHIZUKU) "Shizuku" else "Wireless ADB"
                                Toast.makeText(
                                    context,
                                    "Permissions revoked via $engineName (${adbResult.commandsSucceeded}/${adbResult.commandsAttempted})",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                AppPermissionsHelper.requestSelfRevokeRuntimePermissions(context)
                                val reloaded = withContext(Dispatchers.IO) {
                                    AppPermissionsHelper.inspectPermissions(context)
                                }
                                appPermissions = reloaded
                                permissionsLoaded = true
                                isBulkPermissionBusy = false
                                Toast.makeText(
                                    context,
                                    "Background listeners stopped. Toggle remaining permissions off in App Info.",
                                    Toast.LENGTH_LONG
                                ).show()
                                AppPermissionsHelper.openAppDetailsSettings(context)
                            }
                        }
                    }
                ) { Text("Disable All", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("settings_cancel_all_off_button"),
                    onClick = { showRevokeAllConfirmDialog = false }
                ) { Text("Cancel") }
            }
        )
    }

    if (remainingSpecialPermissionsDialogList.isNotEmpty()) {
        val nextSpecial = AppPermissionsHelper.findNextMissingSystemCategory(
            remainingSpecialPermissionsDialogList
        )
        val remainingNames = remainingSpecialPermissionsDialogList.joinToString("\n• ") { it.title }
        AlertDialog(
            onDismissRequest = { remainingSpecialPermissionsDialogList = emptyList() },
            icon = {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Additional System Toggles Needed") },
            text = {
                Text(
                    "Android requires manual confirmation in System Settings for:\n• $remainingNames"
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("settings_special_perm_open_next_button"),
                    onClick = {
                        remainingSpecialPermissionsDialogList = emptyList()
                        if (nextSpecial != null) {
                            AppPermissionsHelper.openPermissionSettings(context, nextSpecial)
                        }
                    }
                ) { Text("Open Next Setting") }
            },
            dismissButton = {
                TextButton(onClick = { remainingSpecialPermissionsDialogList = emptyList() }) {
                    Text("Later")
                }
            }
        )
    }

    if (showResetColorsDialog) {
        AlertDialog(
            onDismissRequest = { showResetColorsDialog = false },
            title = { Text("Reset Colors") },
            text = { Text("Are you sure you want to reset UI and Button colors to default?") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetColors(); showResetColorsDialog = false }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showResetColorsDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to clear all history?") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHistory(); showClearHistoryDialog = false }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearFavDialog) {
        AlertDialog(
            onDismissRequest = { showClearFavDialog = false },
            title = { Text("Clear Favorites") },
            text = { Text("Are you sure you want to clear all favorites?") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearFavorites(); showClearFavDialog = false }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearFavDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Data") },
            text = { Text("Choose a format to export your History and Favorites.") },
            confirmButton = {},
            dismissButton = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { exportData(context, history, favorites, "TXT"); showExportDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Export as TXT") }
                    TextButton(onClick = { exportData(context, history, favorites, "CSV"); showExportDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Export as CSV") }
                    TextButton(onClick = { exportData(context, history, favorites, "JSON"); showExportDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Export as JSON") }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { showExportDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = MaterialTheme.colorScheme.error) }
                }
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            com.arwora.utils.SoundEffectManager.playClick()
                            navController.popBackStack()
                        },
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==========================================
            // 1. APPEARANCE & THEMING CATEGORY
            // ==========================================
            SettingsCategoryHeader(title = "Appearance & Theming", icon = Icons.Default.Palette)

            SettingsGroupCard {
                // Theme Mode Selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    SettingsRowContent(
                        title = "Theme Mode",
                        subtitle = "Interface color scheme & contrast",
                        icon = Icons.Default.Brightness4
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Compact 4-Grid Segmented Theme Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val modes = listOf(
                            Triple("System", Icons.Default.SettingsBrightness, darkMode == null && !whiteTheme),
                            Triple("Light", Icons.Default.LightMode, darkMode == false && !whiteTheme),
                            Triple("Dark", Icons.Default.DarkMode, darkMode == true && !whiteTheme),
                            Triple("White", Icons.Default.InvertColors, whiteTheme == true)
                        )

                        modes.forEach { (mode, icon, isSelected) ->
                            val animBg by animateColorAsState(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                label = "themeModeBg"
                            )
                            val animContent by animateColorAsState(
                                if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                label = "themeModeContent"
                            )

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .testTag("settings_theme_mode_${mode.lowercase()}")
                                    .clickable {
                                        com.arwora.utils.SoundEffectManager.playClick()
                                        when (mode) {
                                            "System" -> com.arwora.ui.theme.ThemeManager.selectThemeMode(viewModel, com.arwora.ui.theme.ThemeMode.SYSTEM)
                                            "Light" -> com.arwora.ui.theme.ThemeManager.selectThemeMode(viewModel, com.arwora.ui.theme.ThemeMode.LIGHT)
                                            "Dark" -> com.arwora.ui.theme.ThemeManager.selectThemeMode(viewModel, com.arwora.ui.theme.ThemeMode.DARK)
                                            "White" -> com.arwora.ui.theme.ThemeManager.selectThemeMode(viewModel, com.arwora.ui.theme.ThemeMode.WHITE)
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = animBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = mode,
                                        tint = animContent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = mode,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = animContent
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                // Theme / Accent Color Presets
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Accent Color Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Instantly change button accents, highlights, and UI tints",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val presets = listOf(
                            Pair("Purple", 0xFF6C63FFL),
                            Pair("Blue", 0xFF1E88E5L),
                            Pair("Emerald", 0xFF00C853L),
                            Pair("Crimson", 0xFFD50000L),
                            Pair("Amber", 0xFFFFAB00L),
                            Pair("Cyan", 0xFF00838FL)
                        )
                        presets.forEach { (name, colorValue) ->
                            val isSelected = uiColorLong == colorValue && buttonColorLong == colorValue
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorValue.toInt()))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.White.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        com.arwora.utils.SoundEffectManager.playClick()
                                        viewModel.setAccentColors(colorValue, colorValue)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected $name",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = {
                            com.arwora.utils.SoundEffectManager.playClick()
                            originalUiColor = uiColorLong
                            originalButtonColor = buttonColorLong
                            showColorPickerDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_custom_color_button"),
                        shape = ArworaDesignTokens.PillShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(imageVector = Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Custom Color Wheel Picker", fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                // Animations switch
                SettingsItemRow(
                    title = "Smooth Animations",
                    subtitle = "Enable fluid transitions and motion effects",
                    icon = Icons.Default.Animation,
                    trailingContent = {
                        ArworaAnimatedSwitch(
                            checked = animations,
                            onCheckedChange = { viewModel.setAnimations(it) },
                            modifier = Modifier.testTag("settings_animations_switch")
                        )
                    }
                )
            }

            // ==========================================
            // 2. CONTROLS, AUDIO & OVERLAY CATEGORY
            // ==========================================
            SettingsCategoryHeader(title = "Controls, Audio & Overlay", icon = Icons.Default.TouchApp)

            SettingsGroupCard {
                SettingsItemRow(
                    title = "Sound Effects",
                    subtitle = "Play subtle click audio on user interactions",
                    icon = Icons.Default.VolumeUp,
                    trailingContent = {
                        ArworaAnimatedSwitch(
                            checked = soundEffects,
                            onCheckedChange = { viewModel.setSoundEffects(it) },
                            modifier = Modifier.testTag("settings_sound_effects_switch")
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                SettingsItemRow(
                    title = "Haptic Feedback",
                    subtitle = "Tactile vibration responses on button presses",
                    icon = Icons.Default.Vibration,
                    trailingContent = {
                        ArworaAnimatedSwitch(
                            checked = hapticFeedback,
                            onCheckedChange = { viewModel.setHapticFeedback(it) },
                            modifier = Modifier.testTag("settings_haptic_feedback_switch")
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                SettingsItemRow(
                    title = "Floating Window Mode",
                    subtitle = "Show system-wide floating overlay bubble",
                    icon = Icons.Default.PictureInPictureAlt,
                    trailingContent = {
                        ArworaAnimatedSwitch(
                            checked = floatingMode,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    val canOverlay = try {
                                        Settings.canDrawOverlays(context)
                                    } catch (_: Throwable) {
                                        false
                                    }
                                    if (canOverlay) {
                                        viewModel.setFloatingMode(true)
                                        try {
                                            if (!com.arwora.service.FloatingOverlayService.isRunning) {
                                                context.startService(Intent(context, com.arwora.service.FloatingOverlayService::class.java))
                                            }
                                        } catch (_: Throwable) {
                                            Toast.makeText(context, "Could not start floating service", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        showPermissionDialog = true
                                    }
                                } else {
                                    viewModel.setFloatingMode(false)
                                    try {
                                        context.stopService(Intent(context, com.arwora.service.FloatingOverlayService::class.java))
                                    } catch (_: Throwable) {
                                        // ignore
                                    }
                                }
                            },
                            modifier = Modifier.testTag("settings_floating_mode_switch")
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                SettingsItemRow(
                    title = "Remember Floating Position",
                    subtitle = "Save dragged window location on screen",
                    icon = Icons.Default.PinDrop,
                    trailingContent = {
                        ArworaAnimatedSwitch(
                            checked = rememberPos,
                            onCheckedChange = { viewModel.setRememberFloatingPosition(it) },
                            modifier = Modifier.testTag("settings_remember_pos_switch")
                        )
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    SettingsRowContent(
                        title = "Default Floating Size",
                        subtitle = "Initial window dimensions on launch",
                        icon = Icons.Default.AspectRatio
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Compact", "Medium", "Large").forEach { size ->
                            FilterChip(
                                selected = defaultSize == size,
                                onClick = {
                                    com.arwora.utils.SoundEffectManager.playClick()
                                    viewModel.setDefaultFloatingSize(size)
                                },
                                modifier = Modifier.testTag("settings_floating_size_${size.lowercase()}"),
                                shape = ArworaDesignTokens.PillShape,
                                label = { Text(size, fontWeight = if (defaultSize == size) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 3. APP PERMISSIONS & STATUS CATEGORY
            // ==========================================
            SettingsCategoryHeader(title = "App Permissions & Status", icon = Icons.Default.AdminPanelSettings)

            val grantedCount = appPermissions.count { it.isGranted }
            SettingsGroupCard(modifier = Modifier.testTag("settings_app_permissions_card")) {
                SettingsItemRow(
                    title = "App Permissions",
                    subtitle = if (permissionsLoaded) "$grantedCount of ${appPermissions.size} permissions granted" else "Checking permissions...",
                    icon = Icons.Default.AdminPanelSettings,
                    modifier = Modifier.testTag("settings_app_permissions_row"),
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                            ) {
                                Text(
                                    text = "$grantedCount/${appPermissions.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Icon(
                                imageVector = if (isPermissionsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isPermissionsExpanded) "Collapse Permissions" else "Expand Permissions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        com.arwora.utils.SoundEffectManager.playClick()
                        isPermissionsExpanded = !isPermissionsExpanded
                        coroutineScope.launch {
                            val loaded = withContext(Dispatchers.IO) {
                                AppPermissionsHelper.inspectPermissions(context)
                            }
                            appPermissions = loaded
                            permissionsLoaded = true
                        }
                    }
                )

                AnimatedVisibility(visible = isPermissionsExpanded) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        val engineBadgeColor = when (activePrivilegeEngine) {
                            PrivilegeEngine.SHIZUKU,
                            PrivilegeEngine.WIRELESS_ADB -> Color(0xFF10B981)
                            PrivilegeEngine.NONE -> Color(0xFFDC2626)
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .testTag("settings_privilege_engine_row")
                                .clickable {
                                    com.arwora.utils.SoundEffectManager.playClick()
                                    val binderAlive = shizukuBinderAlive || AdbShellExecutor.isShizukuRunning()
                                    val permGranted = shizukuPermissionGranted || AdbShellExecutor.hasShizukuPermission()
                                    if (binderAlive && permGranted) {
                                        AdbShellExecutor.refreshEngineStatus(context)
                                        Toast.makeText(
                                            context,
                                            "Shizuku RISH is active and authorized",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else if (binderAlive) {
                                        ShizukuBridge.requestPermission(ShizukuBridge.REQUEST_CODE)
                                    } else if (ShizukuBridge.isShizukuInstalled(context)) {
                                        AdbShellExecutor.openOrInstallShizuku(context)
                                    } else {
                                        AppPermissionsHelper.openPermissionSettings(
                                            context,
                                            ManagedPermissionCategory.WIRELESS_ADB
                                        )
                                    }
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = engineBadgeColor.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, engineBadgeColor.copy(alpha = 0.28f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Shell & Permission Engine",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = when (activePrivilegeEngine) {
                                            PrivilegeEngine.SHIZUKU -> "Prioritizing Shizuku RISH for 1-click grants"
                                            PrivilegeEngine.WIRELESS_ADB -> "Routing via active Wireless ADB session"
                                            PrivilegeEngine.NONE -> "Authorize Shizuku or connect Wireless ADB for zero-prompt toggles"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = engineBadgeColor.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, engineBadgeColor.copy(alpha = 0.35f)),
                                    modifier = Modifier.testTag("settings_privilege_engine_badge")
                                ) {
                                    Text(
                                        text = activePrivilegeEngine.statusBadgeLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = engineBadgeColor,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))

                        appPermissions.forEachIndexed { index, permission ->
                            AppPermissionStatusRow(
                                permission = permission,
                                onOpenSettings = {
                                    com.arwora.utils.SoundEffectManager.playClick()
                                    if (permission.category == ManagedPermissionCategory.STORAGE &&
                                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
                                        !AppPermissionsHelper.hasAllFilesAccess(context)
                                    ) {
                                        val manageIntent = AppPermissionsHelper.createManageAllFilesAccessIntent(context).apply {
                                            flags = flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
                                        }
                                        val launched = try {
                                            if (manageIntent.resolveActivity(context.packageManager) != null) {
                                                allFilesAccessSettingsLauncher.launch(manageIntent)
                                                true
                                            } else {
                                                false
                                            }
                                        } catch (_: Exception) {
                                            false
                                        }
                                        if (!launched) {
                                            val fallbackLaunched = AppPermissionsHelper.openAllFilesAccessWithSafFallback(
                                                context = context,
                                                onSafFallback = {
                                                    try {
                                                        safTreeLauncher.launch(
                                                            AppPermissionsHelper.getPersistedSafTreeUri(context)
                                                        )
                                                    } catch (_: Exception) {
                                                        Toast.makeText(
                                                            context,
                                                            "Unable to open folder picker",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            )
                                            if (!fallbackLaunched) {
                                                showSafFallbackDialog = true
                                            }
                                        }
                                    } else {
                                        val runtimePerms = if (!permission.isGranted) {
                                            AppPermissionsHelper.runtimePermissionsForCategory(permission.category)
                                        } else {
                                            emptyList()
                                        }
                                        if (runtimePerms.isNotEmpty()) {
                                            pendingPermissionCategory = permission.category
                                            singleCategoryRuntimeLauncher.launch(runtimePerms.toTypedArray())
                                        } else {
                                            val opened = AppPermissionsHelper.openPermissionSettings(
                                                context,
                                                permission.category
                                            )
                                            if (!opened) {
                                                Toast.makeText(
                                                    context,
                                                    "Unable to open system settings",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            )
                            if (index < appPermissions.lastIndex) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        val missingCount = appPermissions.count { !it.isGranted }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    com.arwora.utils.SoundEffectManager.playClick()
                                    if (AdbShellExecutor.isPrivilegedShellReady(context)) {
                                        val usedEngine = AdbShellExecutor.resolveActiveEngine(context)
                                        isBulkPermissionBusy = true
                                        coroutineScope.launch {
                                            val adbResult = withContext(Dispatchers.IO) {
                                                AppPermissionsHelper.grantAllPermissionsViaAdb(context)
                                            }
                                            val reloaded = withContext(Dispatchers.IO) {
                                                AppPermissionsHelper.inspectPermissions(context)
                                            }
                                            appPermissions = reloaded
                                            permissionsLoaded = true
                                            isBulkPermissionBusy = false
                                            val remainingSpecial = AppPermissionsHelper.findRemainingSpecialPermissions(reloaded)
                                            if (remainingSpecial.isNotEmpty()) {
                                                remainingSpecialPermissionsDialogList = remainingSpecial
                                            } else {
                                                val engineName = if (usedEngine == PrivilegeEngine.SHIZUKU) "Shizuku" else "Wireless ADB"
                                                Toast.makeText(
                                                    context,
                                                    "Permissions enabled via $engineName (${adbResult.commandsSucceeded}/${adbResult.commandsAttempted})",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    } else {
                                        com.arwora.service.NotificationHistoryService.setListenerPaused(context, false)
                                        val missingRuntime = AppPermissionsHelper.collectMissingRuntimePermissions(
                                            context = context,
                                            permissions = appPermissions
                                        )
                                        if (missingRuntime.isNotEmpty()) {
                                            pendingQuickFixNextStep = true
                                            quickFixRuntimeLauncher.launch(missingRuntime.toTypedArray())
                                        } else {
                                            val remainingSpecial = AppPermissionsHelper.findRemainingSpecialPermissions(
                                                appPermissions
                                            )
                                            if (remainingSpecial.isNotEmpty()) {
                                                remainingSpecialPermissionsDialogList = remainingSpecial
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "All permissions granted",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                },
                                enabled = permissionsLoaded && !isBulkPermissionBusy && missingCount > 0,
                                shape = RoundedCornerShape(22.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("settings_permissions_all_on_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "1-Click All ON",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    com.arwora.utils.SoundEffectManager.playClick()
                                    showRevokeAllConfirmDialog = true
                                },
                                enabled = permissionsLoaded && !isBulkPermissionBusy,
                                shape = RoundedCornerShape(22.dp),
                                border = BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("settings_permissions_all_off_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "1-Click All OFF",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 4. DATA, STORAGE & TELEMETRY CATEGORY
            // ==========================================
            SettingsCategoryHeader(title = "Data, Storage & Telemetry", icon = Icons.Default.Storage)

            val compressionDefaults by com.arwora.feature.compressor.CompressionDefaultsStore.settings.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                com.arwora.feature.compressor.CompressionDefaultsStore.ensureLoaded(context)
            }

            SettingsGroupCard {
                SettingsItemRow(
                    title = "Compression Settings",
                    subtitle = compressionDefaults.summaryLabel(),
                    icon = Icons.Default.Compress,
                    modifier = Modifier.testTag("settings_compression_defaults_row"),
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        com.arwora.utils.SoundEffectManager.playClick()
                        navController.navigate(Screen.CompressionSettings.route)
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                SettingsItemRow(
                    title = "Export Data",
                    subtitle = "Share history and favorites (TXT / CSV / JSON)",
                    icon = Icons.Default.Share,
                    modifier = Modifier.testTag("settings_export_data_row"),
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        com.arwora.utils.SoundEffectManager.playClick()
                        showExportDialog = true
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                SettingsItemRow(
                    title = "Clear History",
                    subtitle = "Permanently delete cached generation records",
                    icon = Icons.Default.History,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBackground = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    titleColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("settings_clear_history_row"),
                    onClick = {
                        com.arwora.utils.SoundEffectManager.playClick()
                        showClearHistoryDialog = true
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                SettingsItemRow(
                    title = "Clear Favorites",
                    subtitle = "Delete all bookmarked styles and entries",
                    icon = Icons.Default.FavoriteBorder,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBackground = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    titleColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("settings_clear_favorites_row"),
                    onClick = {
                        com.arwora.utils.SoundEffectManager.playClick()
                        showClearFavDialog = true
                    }
                )
            }

            // ==========================================
            // 4. ABOUT & DEVELOPER BRANDING CATEGORY
            // ==========================================
            SettingsCategoryHeader(title = "About & Application", icon = Icons.Default.Info)

            SettingsGroupCard {
                SettingsItemRow(
                    title = "About App",
                    subtitle = "System details, licenses, and architecture",
                    icon = Icons.Default.Info,
                    modifier = Modifier.testTag("settings_about_app_row"),
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        com.arwora.utils.SoundEffectManager.playClick()
                        navController.navigate(Screen.About.route)
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                SettingsItemRow(
                    title = "Privacy Policy",
                    subtitle = "Offline data handling & permission transparency",
                    icon = Icons.Default.VerifiedUser,
                    modifier = Modifier.testTag("settings_privacy_policy_row"),
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        com.arwora.utils.SoundEffectManager.playClick()
                        navController.navigate(Screen.PrivacyPolicy.route)
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                // Developer Branding Card Block
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AppLogo(
                        size = 90.dp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = com.arwora.utils.AppConstants.APP_NAME,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Version ${com.arwora.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "App Developed by Mahfuz",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Designed by Mahfuz",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(96.dp))
        }
    }

    if (showColorPickerDialog) {
        ColorWheelPickerDialog(
            initialColorLong = originalUiColor,
            onDismissRequest = {
                viewModel.setLiveAccentPreview(null)
                showColorPickerDialog = false
            },
            onConfirm = { finalColor ->
                viewModel.setAccentColors(finalColor, finalColor)
                showColorPickerDialog = false
            },
            onLiveColorUpdate = { liveColor ->
                viewModel.setLiveAccentPreview(liveColor)
            }
        )
    }
}

/**
 * Standard category header matching the clean typography hierarchy.
 */
@Composable
fun SettingsCategoryHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(horizontal = 20.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Unified Card Container adhering strictly to the standard design system (matching SystemCard, DNS Changer).
 */
@Composable
fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp()),
        content = content
    )
}

/**
 * Internal component to ensure identical alignment for all settings rows/headers.
 */
@Composable
fun SettingsRowContent(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon Badge matching ArworaTonalIconBadge
        Box(
            modifier = Modifier
                .size(ArworaDesignTokens.IconBadgeSize)
                .clip(ArworaDesignTokens.IconBadgeShape)
                .background(iconBackground)
                .border(1.dp, iconTint.copy(alpha = 0.25f), ArworaDesignTokens.IconBadgeShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }

        // Text Block
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                lineHeight = 20.sp
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * Standard settings item row with leading icon badge, title, subtitle, and trailing action.
 */
@Composable
fun SettingsItemRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SettingsRowContent(
                title = title,
                subtitle = subtitle,
                icon = icon,
                iconTint = iconTint,
                iconBackground = iconBackground,
                titleColor = titleColor,
                modifier = Modifier.weight(1f)
            )

            if (trailingContent != null) {
                Spacer(modifier = Modifier.width(16.dp))
                Box(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    contentAlignment = Alignment.Center
                ) {
                    trailingContent()
                }
            }
        }
    }
}

@Composable
private fun AppPermissionStatusRow(
    permission: ManagedAppPermission,
    onOpenSettings: () -> Unit
) {
    val statusColor = if (permission.isGranted) Color(0xFF16A34A) else Color(0xFFDC2626)
    val statusLabel = if (permission.isGranted) "Granted" else "Not Granted"
    val permissionIcon = when (permission.category) {
        ManagedPermissionCategory.MICROPHONE -> Icons.Default.Mic
        ManagedPermissionCategory.STORAGE -> Icons.Default.FolderOpen
        ManagedPermissionCategory.NOTIFICATIONS -> Icons.Default.Notifications
        ManagedPermissionCategory.NOTIFICATION_LISTENER -> Icons.Default.NotificationsActive
        ManagedPermissionCategory.OVERLAY -> Icons.Default.Layers
        ManagedPermissionCategory.CAMERA -> Icons.Default.CameraAlt
        ManagedPermissionCategory.WIRELESS_ADB -> Icons.Default.DeveloperMode
        ManagedPermissionCategory.USAGE_STATS -> Icons.Default.QueryStats
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .testTag("settings_permission_item_${permission.id}")
            .clickable(onClick = onOpenSettings),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(statusColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = permissionIcon,
                    contentDescription = permission.title,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = permission.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = permission.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = CircleShape,
                color = statusColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.30f)),
                modifier = Modifier.testTag("settings_permission_status_${permission.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "Manage ${permission.title}",
                        tint = statusColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ColorSliderSetting(
    title: String,
    description: String,
    currentColorLong: Long,
    onColorChanged: (Long) -> Unit
) {
    val argb = currentColorLong.toInt()
    var hue by remember(currentColorLong) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsv)
        mutableFloatStateOf(hsv[0])
    }
    
    val previewColorInt = remember(hue) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.9f))
    }
    val previewColor = remember(previewColorInt) { Color(previewColorInt) }
    val hexString = remember(previewColorInt) { String.format("#%08X", previewColorInt) }

    SettingsGroupCard(modifier = Modifier.padding(vertical = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(previewColor, CircleShape)
                    )
                    Text(hexString, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = hue,
                onValueChange = { newHue ->
                    hue = newHue
                },
                onValueChangeFinished = {
                    val newColorInt = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.9f))
                    onColorChanged(newColorInt.toLong() and 0xFFFFFFFFL)
                },
                valueRange = 0f..360f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

fun exportData(context: Context, history: List<com.arwora.data.db.HistoryItem>, favorites: List<com.arwora.data.db.FavoriteItem>, format: String) {
    if (history.isEmpty() && favorites.isEmpty()) {
        Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show()
        return
    }
    
    val sb = StringBuilder()
    
    when (format) {
        "TXT" -> {
            sb.append("--- FAVORITES ---\n")
            favorites.forEach { sb.append("[${it.type}] ${it.content}\n") }
            sb.append("\n--- HISTORY ---\n")
            history.forEach { sb.append("[${it.type}] ${it.content}\n") }
        }
        "CSV" -> {
            sb.append("Category,Type,Content\n")
            favorites.forEach { sb.append("Favorite,${it.type},\"${it.content.replace("\"", "\"\"")}\"\n") }
            history.forEach { sb.append("History,${it.type},\"${it.content.replace("\"", "\"\"")}\"\n") }
        }
        "JSON" -> {
            sb.append("{\n  \"favorites\": [\n")
            favorites.forEachIndexed { idx, it ->
                sb.append("    { \"type\": \"${it.type}\", \"content\": \"${it.content.replace("\"", "\\\"").replace("\n", "\\n")}\" }")
                if (idx < favorites.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("  ],\n  \"history\": [\n")
            history.forEachIndexed { idx, it ->
                sb.append("    { \"type\": \"${it.type}\", \"content\": \"${it.content.replace("\"", "\\\"").replace("\n", "\\n")}\" }")
                if (idx < history.size - 1) sb.append(",")
                sb.append("\n")
            }
            sb.append("  ]\n}")
        }
    }
    
    val data = sb.toString()
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "${com.arwora.utils.AppConstants.APP_NAME} Export ($format)")
        putExtra(Intent.EXTRA_TEXT, data)
    }
    context.startActivity(Intent.createChooser(intent, "Export Data"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorWheelPickerDialog(
    initialColorLong: Long,
    onDismissRequest: () -> Unit,
    onConfirm: (Long) -> Unit,
    onLiveColorUpdate: (Long) -> Unit
) {
    val initialColorInt = initialColorLong.toInt()
    val initialHsv = FloatArray(3)
    android.graphics.Color.colorToHSV(initialColorInt, initialHsv)
    val initialAlpha = (android.graphics.Color.alpha(initialColorInt) / 255f)

    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }
    var alpha by remember { mutableStateOf(initialAlpha) }

    val currentSelectedColor = remember(hue, saturation, value, alpha) {
        val alphaInt = (alpha * 255f).toInt().coerceIn(0, 255)
        val colorInt = android.graphics.Color.HSVToColor(alphaInt, floatArrayOf(hue, saturation, value))
        Color(colorInt)
    }

    var isInitialEmission by remember { mutableStateOf(true) }
    LaunchedEffect(currentSelectedColor) {
        if (isInitialEmission) {
            isInitialEmission = false
        } else {
            onLiveColorUpdate(currentSelectedColor.toArgb().toLong() and 0xFFFFFFFFL)
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dynamic Color Wheel",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = {
                        com.arwora.utils.SoundEffectManager.playClick()
                        onConfirm(currentSelectedColor.toArgb().toLong() and 0xFFFFFFFFL)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm Theme Color",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Drag on the wheel to pick custom Hue. Adjust Saturation, Brightness, and Opacity below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(220.dp)
                        .padding(8.dp)
                ) {
                    val colors = listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val centerX = size.width / 2f
                                        val centerY = size.height / 2f
                                        val dx = offset.x - centerX
                                        val dy = offset.y - centerY
                                        val angleRad = atan2(dy, dx)
                                        var degrees = Math.toDegrees(angleRad.toDouble()).toFloat()
                                        if (degrees < 0f) degrees += 360f
                                        hue = degrees
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val offset = change.position
                                        val centerX = size.width / 2f
                                        val centerY = size.height / 2f
                                        val dx = offset.x - centerX
                                        val dy = offset.y - centerY
                                        val angleRad = atan2(dy, dx)
                                        var degrees = Math.toDegrees(angleRad.toDouble()).toFloat()
                                        if (degrees < 0f) degrees += 360f
                                        hue = degrees
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    val dx = offset.x - centerX
                                    val dy = offset.y - centerY
                                    val angleRad = atan2(dy, dx)
                                    var degrees = Math.toDegrees(angleRad.toDouble()).toFloat()
                                    if (degrees < 0f) degrees += 360f
                                    hue = degrees
                                }
                            }
                    ) {
                        val strokeWidth = 24.dp.toPx()
                        val outerRadius = size.width / 2f
                        val wheelRadius = outerRadius - strokeWidth / 2f

                        drawCircle(
                            brush = Brush.sweepGradient(colors),
                            radius = wheelRadius,
                            style = Stroke(width = strokeWidth)
                        )

                        val angleRad = Math.toRadians(hue.toDouble())
                        val handleX = (size.width / 2f) + cos(angleRad).toFloat() * wheelRadius
                        val handleY = (size.height / 2f) + sin(angleRad).toFloat() * wheelRadius

                        drawCircle(
                            color = Color.White,
                            radius = 12.dp.toPx(),
                            center = Offset(handleX, handleY),
                            style = Stroke(width = 3.dp.toPx())
                        )
                        drawCircle(
                            color = Color.Black,
                            radius = 10.dp.toPx(),
                            center = Offset(handleX, handleY),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(currentSelectedColor)
                                .border(2.dp, Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${hue.toInt()}°",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format("#%08X", currentSelectedColor.toArgb()),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                val saturationBrush = remember(hue, value) {
                    val alphaInt = (alpha * 255f).toInt().coerceIn(0, 255)
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(android.graphics.Color.HSVToColor(alphaInt, floatArrayOf(hue, 0f, value))),
                            Color(android.graphics.Color.HSVToColor(alphaInt, floatArrayOf(hue, 1f, value)))
                        )
                    )
                }
                GradientSlider(
                    value = saturation,
                    onValueChange = { saturation = it },
                    trackBrush = saturationBrush,
                    label = "Saturation",
                    valueText = "${(saturation * 100).toInt()}%"
                )

                val valueBrush = remember(hue, saturation) {
                    val alphaInt = (alpha * 255f).toInt().coerceIn(0, 255)
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(android.graphics.Color.HSVToColor(alphaInt, floatArrayOf(hue, saturation, 0f))),
                            Color(android.graphics.Color.HSVToColor(alphaInt, floatArrayOf(hue, saturation, 1f)))
                        )
                    )
                }
                GradientSlider(
                    value = value,
                    onValueChange = { value = it },
                    trackBrush = valueBrush,
                    label = "Brightness (Value)",
                    valueText = "${(value * 100).toInt()}%"
                )

                val alphaBrush = remember(hue, saturation, value) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(android.graphics.Color.HSVToColor(0, floatArrayOf(hue, saturation, value))),
                            Color(android.graphics.Color.HSVToColor(255, floatArrayOf(hue, saturation, value)))
                        )
                    )
                }
                GradientSlider(
                    value = alpha,
                    onValueChange = { alpha = it },
                    trackBrush = alphaBrush,
                    label = "Opacity (Alpha)",
                    valueText = "${(alpha * 100).toInt()}%"
                )

                Text(
                    text = "Preset Swatches",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start).padding(top = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val quickPresets = listOf(
                        Pair("Purple", 0xFF6C63FFL),
                        Pair("Blue", 0xFF1E88E5L),
                        Pair("Emerald", 0xFF00C853L),
                        Pair("Crimson", 0xFFD50000L),
                        Pair("Amber", 0xFFFFAB00L),
                        Pair("Cyan", 0xFF00838FL)
                    )
                    quickPresets.forEach { (name, colorValue) ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(colorValue.toInt()))
                                .clickable {
                                    com.arwora.utils.SoundEffectManager.playClick()
                                    val presetColorInt = colorValue.toInt()
                                    val presetHsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(presetColorInt, presetHsv)
                                    hue = presetHsv[0]
                                    saturation = presetHsv[1]
                                    value = presetHsv[2]
                                    alpha = (android.graphics.Color.alpha(presetColorInt) / 255f)
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    com.arwora.utils.SoundEffectManager.playClick()
                    onConfirm(currentSelectedColor.toArgb().toLong() and 0xFFFFFFFFL)
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    com.arwora.utils.SoundEffectManager.playClick()
                    onDismissRequest()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    trackBrush: Brush,
    label: String,
    valueText: String
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(valueText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(trackBrush)
        ) {
            val maxW = constraints.maxWidth.toFloat()
            val density = LocalDensity.current
            val thumbSizePx = with(density) { 16.dp.toPx() }
            val thumbOffset = value * (maxW - thumbSizePx)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val newValue = (offset.x / maxW).coerceIn(0f, 1f)
                            onValueChange(newValue)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val newValue = (change.position.x / maxW).coerceIn(0f, 1f)
                            onValueChange(newValue)
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = with(density) { thumbOffset.toDp() })
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}
