package com.arwora.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.arwora.service.AutoClickerAccessibilityService
import com.arwora.service.AutoClickerOverlayService
import com.arwora.ui.components.ArworaAnimatedSwitch
import com.arwora.utils.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoClickerScreen(navController: NavController) {
    val context = LocalContext.current

    val mode by AutoClickerManager.mode.collectAsStateWithLifecycle()
    val clickInterval by AutoClickerManager.clickIntervalMs.collectAsStateWithLifecycle()
    val tapDuration by AutoClickerManager.tapDurationMs.collectAsStateWithLifecycle()
    val stopType by AutoClickerManager.stopType.collectAsStateWithLifecycle()
    val timerMins by AutoClickerManager.stopTimerMinutes.collectAsStateWithLifecycle()
    val repeatCount by AutoClickerManager.stopRepeatCount.collectAsStateWithLifecycle()
    val randomize by AutoClickerManager.randomizeJitter.collectAsStateWithLifecycle()
    val isOverlayActive by AutoClickerManager.isOverlayActive.collectAsStateWithLifecycle()
    val isClicking by AutoClickerManager.isClicking.collectAsStateWithLifecycle()
    val totalClicks by AutoClickerManager.totalClicks.collectAsStateWithLifecycle()

    var isAccessibilityEnabled by remember {
        mutableStateOf(AutoClickerManager.isAccessibilityServiceEnabled(context) || AutoClickerAccessibilityService.isRunning)
    }
    var canDrawOverlays by remember {
        mutableStateOf(AutoClickerManager.canDrawOverlays(context))
    }

    var showAccessibilityHelpDialog by remember { mutableStateOf(false) }
    var showOverlayHelpDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Refresh permissions automatically on resume
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = AutoClickerManager.isAccessibilityServiceEnabled(context) || AutoClickerAccessibilityService.isRunning
                canDrawOverlays = AutoClickerManager.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // In-app test pad states
    var testTapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var currentCps by remember { mutableFloatStateOf(0f) }

    fun startOverlay() {
        if (!canDrawOverlays) {
            showOverlayHelpDialog = true
            return
        }

        if (!isAccessibilityEnabled && !AutoClickerAccessibilityService.isRunning) {
            showAccessibilityHelpDialog = true
            return
        }

        val overlayIntent = Intent(context, AutoClickerOverlayService::class.java).apply {
            action = AutoClickerOverlayService.ACTION_START_OVERLAY
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(overlayIntent)
        } else {
            context.startService(overlayIntent)
        }
        SoundEffectManager.playSuccess()
        Toast.makeText(context, "Floating controller started! Switch to any app or game.", Toast.LENGTH_SHORT).show()
    }

    fun stopOverlay() {
        val overlayIntent = Intent(context, AutoClickerOverlayService::class.java).apply {
            action = AutoClickerOverlayService.ACTION_STOP_OVERLAY
        }
        context.stopService(overlayIntent)
        SoundEffectManager.playClick()
        Toast.makeText(context, "Floating controller stopped.", Toast.LENGTH_SHORT).show()
    }

    if (showAccessibilityHelpDialog) {
        com.arwora.ui.components.AccessibilityGuidanceDialog(
            title = "Auto Clicker Accessibility Required",
            description = "Auto Clicker requires Android Accessibility Service permission to perform automatic screen tap gestures and swipe macros on your behalf.",
            onDismiss = { showAccessibilityHelpDialog = false }
        )
    }

    if (showOverlayHelpDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayHelpDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.PictureInPictureAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Display Over Other Apps",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "To show the floating tap targets and play/pause controls above games or other apps, the 'Display over other apps' permission is required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Quick Setup Steps:",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "1. Tap 'Grant Permission' below\n2. Toggle 'Allow display over other apps' to ON\n3. Return back to Auto Clicker",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayHelpDialog = false
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open Overlay settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayHelpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Auto Clicker", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Automated screen taps & gesture overlay",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isOverlayActive) {
                        IconButton(onClick = { stopOverlay() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Overlay", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = { startOverlay() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Start Overlay", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Permission Diagnostic Status Cards
            PermissionsStatusSection(
                isAccessibilityEnabled = isAccessibilityEnabled || AutoClickerAccessibilityService.isRunning,
                canDrawOverlays = canDrawOverlays,
                onEnableAccessibility = {
                    SoundEffectManager.playClick()
                    showAccessibilityHelpDialog = true
                },
                onEnableOverlay = {
                    SoundEffectManager.playClick()
                    showOverlayHelpDialog = true
                }
            )

            // 2. Main Control & Launch Card
            LaunchControllerCard(
                isOverlayActive = isOverlayActive,
                isClicking = isClicking,
                totalClicks = totalClicks,
                onStart = { startOverlay() },
                onStop = { stopOverlay() },
                onResetClicks = {
                    SoundEffectManager.playClick()
                    AutoClickerManager.resetClicks()
                }
            )

            // 3. Click Mode Selector Card
            ClickModeSelectorCard(
                currentMode = mode,
                onModeSelected = { newMode ->
                    SoundEffectManager.playClick()
                    AutoClickerManager.setMode(newMode)
                }
            )

            // 4. Interval & Speed Settings Card
            IntervalSettingsCard(
                clickInterval = clickInterval,
                onIntervalChanged = { AutoClickerManager.setInterval(it) },
                tapDuration = tapDuration,
                onTapDurationChanged = { AutoClickerManager.setTapDuration(it) },
                randomize = randomize,
                onRandomizeChanged = { AutoClickerManager.setRandomizeJitter(it) }
            )

            // 5. Stop Conditions (Infinite, Timer, Repeat Count)
            StopConditionCard(
                stopType = stopType,
                onStopTypeChanged = { AutoClickerManager.setStopType(it) },
                timerMins = timerMins,
                onTimerMinsChanged = { AutoClickerManager.setStopTimerMinutes(it) },
                repeatCount = repeatCount,
                onRepeatCountChanged = { AutoClickerManager.setStopRepeatCount(it) }
            )

            // 6. Profiles & Presets Card
            ClickerProfilesCard(
                profiles = AutoClickerManager.profiles.collectAsStateWithLifecycle().value,
                onApply = { profile ->
                    SoundEffectManager.playClick()
                    AutoClickerManager.applyProfile(profile)
                    Toast.makeText(context, "Loaded preset: ${profile.name}", Toast.LENGTH_SHORT).show()
                },
                onSaveCurrent = { name ->
                    SoundEffectManager.playClick()
                    AutoClickerManager.saveCurrentAsProfile(name)
                    Toast.makeText(context, "Profile '$name' saved!", Toast.LENGTH_SHORT).show()
                },
                onDelete = { id ->
                    SoundEffectManager.playClick()
                    AutoClickerManager.deleteProfile(id)
                }
            )

            // 7. Interactive Click Test Pad / Simulator
            ClickTestPadCard(
                testTapCount = testTapCount,
                currentCps = currentCps,
                onTap = {
                    val now = SystemClock.uptimeMillis()
                    if (lastTapTime > 0) {
                        val delta = (now - lastTapTime).coerceAtLeast(1L)
                        val instantCps = 1000f / delta
                        currentCps = (currentCps * 0.7f + instantCps * 0.3f).coerceAtMost(50f)
                    }
                    lastTapTime = now
                    testTapCount++
                    SoundEffectManager.playClick()
                },
                onReset = {
                    testTapCount = 0
                    currentCps = 0f
                    lastTapTime = 0L
                    SoundEffectManager.playClick()
                }
            )
        }
    }
}

// ==================== SUB-COMPOSABLES ====================

@Composable
fun PermissionsStatusSection(
    isAccessibilityEnabled: Boolean,
    canDrawOverlays: Boolean,
    onEnableAccessibility: () -> Unit,
    onEnableOverlay: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Required Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Accessibility Item
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isAccessibilityEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Accessibility Service",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isAccessibilityEnabled) "Service active & ready to tap" else "Needed to simulate taps & swipes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isAccessibilityEnabled) {
                    Button(
                        onClick = onEnableAccessibility,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Enable", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Active",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Overlay Item
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (canDrawOverlays) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (canDrawOverlays) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Display Over Other Apps",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (canDrawOverlays) "Permission granted" else "Required for floating buttons",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!canDrawOverlays) {
                    Button(
                        onClick = onEnableOverlay,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Allow", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "Allowed",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LaunchControllerCard(
    isOverlayActive: Boolean,
    isClicking: Boolean,
    totalClicks: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onResetClicks: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOverlayActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isOverlayActive) Icons.Default.SmartDisplay else Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = if (isOverlayActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text("Floating Controller", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isClicking) "Status: Currently Clicking" else if (isOverlayActive) "Status: Floating Overlay Visible" else "Status: Inactive",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isClicking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "$totalClicks clicks",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Launch floating widget overlay with Play, Stop, Add Target, and Swipe buttons over any game, social feed, or app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isOverlayActive) {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Floating Controller", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onStop,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Stop Overlay", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onResetClicks,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Count", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ClickModeSelectorCard(
    currentMode: ClickMode,
    onModeSelected: (ClickMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Target Mode",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ClickMode.values().forEach { mode ->
                    val isSelected = currentMode == mode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onModeSelected(mode) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    ClickMode.SINGLE -> Icons.Default.AdsClick
                                    ClickMode.MULTI -> Icons.Default.Animation
                                    ClickMode.SWIPE -> Icons.Default.Swipe
                                },
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = mode.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Text(
                text = currentMode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun IntervalSettingsCard(
    clickInterval: Long,
    onIntervalChanged: (Long) -> Unit,
    tapDuration: Long,
    onTapDurationChanged: (Long) -> Unit,
    randomize: Boolean,
    onRandomizeChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Interval & Click Speed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Preset Speed Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf(
                    50L to "50ms (20/s)",
                    100L to "100ms (10/s)",
                    250L to "250ms (4/s)",
                    500L to "500ms (2/s)",
                    1000L to "1s (1/s)"
                )
                presets.forEach { (ms, label) ->
                    FilterChip(
                        selected = clickInterval == ms,
                        onClick = {
                            SoundEffectManager.playClick()
                            onIntervalChanged(ms)
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            // Custom Milliseconds Input
            OutlinedTextField(
                value = clickInterval.toString(),
                onValueChange = { str ->
                    val num = str.filter { it.isDigit() }.toLongOrNull()
                    if (num != null) onIntervalChanged(num)
                },
                label = { Text("Click Interval (milliseconds)") },
                trailingIcon = { Text("ms", modifier = Modifier.padding(end = 12.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            // Tap Hold Duration
            OutlinedTextField(
                value = tapDuration.toString(),
                onValueChange = { str ->
                    val num = str.filter { it.isDigit() }.toLongOrNull()
                    if (num != null) onTapDurationChanged(num)
                },
                label = { Text("Tap Hold Duration (milliseconds)") },
                trailingIcon = { Text("ms", modifier = Modifier.padding(end = 12.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Random Jitter / Anti-Bot Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Randomize Interval (Anti-Detection)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Adds slight ±15% natural jitter to click intervals and positions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ArworaAnimatedSwitch(
                    checked = randomize,
                    onCheckedChange = {
                        SoundEffectManager.playClick()
                        onRandomizeChanged(it)
                    }
                )
            }
        }
    }
}

@Composable
fun StopConditionCard(
    stopType: StopType,
    onStopTypeChanged: (StopType) -> Unit,
    timerMins: Int,
    onTimerMinsChanged: (Int) -> Unit,
    repeatCount: Int,
    onRepeatCountChanged: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Stop Condition",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StopType.values().forEach { type ->
                    val isSelected = stopType == type
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            SoundEffectManager.playClick()
                            onStopTypeChanged(type)
                        },
                        label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            AnimatedVisibility(visible = stopType == StopType.TIMER) {
                OutlinedTextField(
                    value = timerMins.toString(),
                    onValueChange = { str ->
                        val num = str.filter { it.isDigit() }.toIntOrNull()
                        if (num != null) onTimerMinsChanged(num)
                    },
                    label = { Text("Auto-Stop After (Minutes)") },
                    trailingIcon = { Text("mins", modifier = Modifier.padding(end = 12.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            AnimatedVisibility(visible = stopType == StopType.REPEAT_COUNT) {
                OutlinedTextField(
                    value = repeatCount.toString(),
                    onValueChange = { str ->
                        val num = str.filter { it.isDigit() }.toIntOrNull()
                        if (num != null) onRepeatCountChanged(num)
                    },
                    label = { Text("Auto-Stop After (Cycles / Taps)") },
                    trailingIcon = { Text("taps", modifier = Modifier.padding(end = 12.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
fun ClickTestPadCard(
    testTapCount: Int,
    currentCps: Float,
    onTap: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("In-App Test Simulator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = onReset) {
                    Text("Clear", style = MaterialTheme.typography.labelMedium)
                }
            }

            Text(
                text = "Tap or position the Auto Clicker target over this box to test tap accuracy, speed, and clicks-per-second in real time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Interactive Clickable Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .clickable { onTap() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$testTapCount",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Speed: ${String.format("%.1f", currentCps)} CPS (Clicks/Sec)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "TAP HERE TO TEST",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun ClickerProfilesCard(
    profiles: List<ClickerProfile>,
    onApply: (ClickerProfile) -> Unit,
    onSaveCurrent: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var profileNameInput by remember { mutableStateOf("") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Save Current Preset", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a name for your custom tap/swipe configuration:")
                    OutlinedTextField(
                        value = profileNameInput,
                        onValueChange = { profileNameInput = it },
                        placeholder = { Text("e.g., Level 50 Boss Farm") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (profileNameInput.isNotBlank()) {
                            onSaveCurrent(profileNameInput.trim())
                            profileNameInput = ""
                            showDialog = false
                        }
                    }
                ) {
                    Text("Save Preset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Bookmarks, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Macro & Script Profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save Current", style = MaterialTheme.typography.labelMedium)
                }
            }

            Text(
                text = "Quickly switch between gaming speed presets, social feed scrolling, and multi-point macros.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            profiles.forEach { profile ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(profile.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        profile.mode.label,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                "Speed: ${profile.intervalMs}ms | Tap: ${profile.tapDurationMs}ms | ${profile.stopType.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilledTonalButton(
                                onClick = { onApply(profile) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Load", style = MaterialTheme.typography.labelSmall)
                            }

                            if (!profile.id.startsWith("fast_") && !profile.id.startsWith("social_") && !profile.id.startsWith("balanced_")) {
                                IconButton(onClick = { onDelete(profile.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

