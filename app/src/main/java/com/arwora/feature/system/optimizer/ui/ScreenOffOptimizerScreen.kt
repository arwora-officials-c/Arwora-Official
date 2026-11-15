package com.arwora.feature.system.optimizer.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.arwora.feature.system.optimizer.service.ScreenOffOptimizerManager
import com.arwora.ui.components.ArworaAnimatedSwitch
import com.arwora.utils.SoundEffectManager
import rikka.shizuku.Shizuku

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenOffOptimizerScreen(
    navController: NavController
) {
    val context = LocalContext.current
    BackHandler { navController.popBackStack() }
    val haptic = LocalHapticFeedback.current

    val isServiceActive by ScreenOffOptimizerManager.isServiceActive.collectAsStateWithLifecycle()
    val delaySeconds by ScreenOffOptimizerManager.delaySeconds.collectAsStateWithLifecycle()
    val killApps by ScreenOffOptimizerManager.killBackgroundApps.collectAsStateWithLifecycle()
    val enableSilent by ScreenOffOptimizerManager.enableSilentMode.collectAsStateWithLifecycle()
    val disableSilent by ScreenOffOptimizerManager.disableSilentMode.collectAsStateWithLifecycle()
    val turnOffRadios by ScreenOffOptimizerManager.turnOffRadios.collectAsStateWithLifecycle()
    val useShizuku by ScreenOffOptimizerManager.useShizuku.collectAsStateWithLifecycle()
    val shizukuPermissionGranted by ScreenOffOptimizerManager.shizukuPermissionGranted.collectAsStateWithLifecycle()
    val mediaVolume by ScreenOffOptimizerManager.mediaVolume.collectAsStateWithLifecycle()
    val ringtoneVolume by ScreenOffOptimizerManager.ringtoneVolume.collectAsStateWithLifecycle()
    val notificationVolume by ScreenOffOptimizerManager.notificationVolume.collectAsStateWithLifecycle()
    val alarmVolume by ScreenOffOptimizerManager.alarmVolume.collectAsStateWithLifecycle()
    val lastLog by ScreenOffOptimizerManager.lastOptimizationLog.collectAsStateWithLifecycle()
    val totalCount by ScreenOffOptimizerManager.totalOptimizationsCount.collectAsStateWithLifecycle()
    val serviceWarning by ScreenOffOptimizerManager.serviceWarning.collectAsStateWithLifecycle()

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    var hasPolicyAccess by remember { mutableStateOf(notificationManager?.isNotificationPolicyAccessGranted == true) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    fun launchDndSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:" + context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val genericIntent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(genericIntent)
            } catch (_: Exception) {
                try {
                    val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(appSettingsIntent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Open System Settings to grant Do Not Disturb access", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Dynamic pre-check handler on ON_RESUME: auto-resumes service immediately once permission is granted!
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val shizukuListeners = com.arwora.utils.ShizukuConnectionManager.registerListeners(
            onBinderChanged = {
                ScreenOffOptimizerManager.setShizukuPermissionGranted(
                    com.arwora.utils.ShizukuConnectionManager.hasShizukuPermission()
                )
            },
            onPermissionResult = { _, granted ->
                ScreenOffOptimizerManager.setShizukuPermissionGranted(granted)
            }
        )
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = notificationManager?.isNotificationPolicyAccessGranted == true
                val wasMissing = !hasPolicyAccess
                hasPolicyAccess = granted
                ScreenOffOptimizerManager.checkAndSyncDndPermission(context)

                // Check Shizuku permission cleanly
                val shizukuGranted = com.arwora.utils.ShizukuConnectionManager.hasShizukuPermission()
                ScreenOffOptimizerManager.setShizukuPermissionGranted(shizukuGranted)

                // Auto-start / auto-resume service immediately when user returns with permission granted
                if (granted) {
                    if (wasMissing || !isServiceActive) {
                        ScreenOffOptimizerManager.startService(context)
                        Toast.makeText(context, "DND Access Granted: Screen-Off Automation Service Active", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            com.arwora.utils.ShizukuConnectionManager.unregisterListeners(shizukuListeners)
        }
    }

    // Interactive Permission Dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = { Icon(Icons.Default.DoNotDisturbOn, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Do Not Disturb Access Required", fontWeight = FontWeight.Bold) },
            text = {
                Text("To automatically switch from Silent/DND to Normal/Ringer mode and scale audio streams to 100% when the screen turns off, Arwora Hyper Toolkit requires Do Not Disturb access in System Settings.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        launchDndSettings()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Grant in Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
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
                        Text(
                            text = "Smart Screen-Off Optimization",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = when {
                                isServiceActive && !hasPolicyAccess && (enableSilent || disableSilent) -> "Warning: Missing DND Access"
                                isServiceActive -> "Actively Listening for Screen Lock"
                                else -> "Service Paused"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                isServiceActive && !hasPolicyAccess && (enableSilent || disableSilent) -> Color(0xFFF59E0B)
                                isServiceActive -> Color(0xFF10B981)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Status feedback banner
            item {
                AnimatedVisibility(
                    visible = lastLog != null || serviceWarning != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val isWarning = serviceWarning != null
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isWarning) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, if (isWarning) MaterialTheme.colorScheme.error.copy(alpha = 0.35f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (isWarning) Icons.Default.Warning else Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = serviceWarning ?: lastLog ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // DND / NOTIFICATION POLICY PERMISSION CARD IF MISSING
            if (!hasPolicyAccess && (enableSilent || disableSilent)) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    text = "Do Not Disturb Access Required",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Text(
                                text = "To automatically switch from Silent/DND to Normal/Ringer mode and scale audio streams to 100% when screen turns off, please grant notification policy access permission.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Button(
                                onClick = {
                                    launchDndSettings()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Grant Permission", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 1. MASTER SWITCH & FOREGROUND SERVICE CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("optimizer_master_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 14.dp),
                                verticalAlignment = Alignment.CenterVertically, 
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isServiceActive && !hasPolicyAccess && (enableSilent || disableSilent) -> Color(0xFFF59E0B)
                                                isServiceActive -> Color(0xFF10B981)
                                                else -> Color(0xFFEF4444)
                                            }
                                        )
                                )
                                Text(
                                    text = "Persistent Foreground Service",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            ArworaAnimatedSwitch(
                                checked = isServiceActive,
                                onCheckedChange = { active ->
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    SoundEffectManager.playClick()
                                    if (active) {
                                        if (!hasPolicyAccess && (enableSilent || disableSilent)) {
                                            showPermissionDialog = true
                                        }
                                        ScreenOffOptimizerManager.startService(context)
                                        Toast.makeText(context, "Foreground Service Started", Toast.LENGTH_SHORT).show()
                                    } else {
                                        ScreenOffOptimizerManager.stopService(context)
                                        Toast.makeText(context, "Foreground Service Paused", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.testTag("optimizer_master_switch")
                            )
                        }

                        Text(
                            text = "Reliable Automation: Powered by a persistent Foreground Service with AlarmManager (setExactAndAllowWhileIdle) and CPU WakeLock to bypass Doze mode restrictions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "$totalCount", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                                Text(text = "Routines Triggered", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isServiceActive) "Active" else "Paused",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isServiceActive) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(text = "WakeLock Protected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                SoundEffectManager.playClick()
                                if (!hasPolicyAccess && (enableSilent || disableSilent)) {
                                    showPermissionDialog = true
                                }
                                ScreenOffOptimizerManager.enforceAudioState(context)
                                ScreenOffOptimizerManager.executeScreenOffRoutine(context)
                                Toast.makeText(context, "Enforced Normal Mode, Disabled DND & Maxed Volumes", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("test_optimizer_routine_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test Screen-Off Routine Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. DELAY TIMER SELECTOR
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("optimizer_delay_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Screen-Off Delay Timer",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Select how long the device must remain locked before AlarmManager triggers cleanup.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val delays = listOf(
                                0L to "Immediate",
                                30L to "30 sec",
                                60L to "1 min",
                                120L to "2 mins",
                                300L to "5 mins"
                            )
                            delays.forEach { (sec, label) ->
                                val selected = delaySeconds == sec
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    onClick = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        SoundEffectManager.playClick()
                                        ScreenOffOptimizerManager.setDelaySeconds(sec)
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. FEATURE CHECKBOXES / TOGGLES
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("optimizer_features_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Automation Tasks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Toggle 1: Kill Background Apps & Clean Junk
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Kill Background Apps & Clean Junk", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = "Safely flush cached data and free RAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                ArworaAnimatedSwitch(
                                    checked = killApps,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        SoundEffectManager.playClick()
                                        ScreenOffOptimizerManager.setKillBackgroundApps(it)
                                    },
                                    modifier = Modifier.testTag("switch_kill_apps")
                                )
                            }
                        }

                        // Toggle 2: Auto Switch to Normal Mode & Max Out All System Audio Volumes on Screen-Off
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "Enforce Normal Mode & Max Audio", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(text = "Disables DND, clears silent/vibrate, & sets Ring, Notif, Alarm, Media, System to 100% max", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    ArworaAnimatedSwitch(
                                        checked = enableSilent,
                                        onCheckedChange = { checked ->
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            SoundEffectManager.playClick()
                                            if (checked && !hasPolicyAccess) {
                                                showPermissionDialog = true
                                            }
                                            ScreenOffOptimizerManager.setEnableSilentMode(checked)
                                        },
                                        modifier = Modifier.testTag("switch_enable_silent")
                                    )
                                }

                                if (enableSilent) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(text = "Enforced Audio Streams (Set to Absolute Maximum 100%)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    
                                    VolumeSliderItem(label = "Ringtone (STREAM_RING)", value = ringtoneVolume, onValueChange = { ScreenOffOptimizerManager.setRingtoneVolume(it) })
                                    VolumeSliderItem(label = "Notification (STREAM_NOTIFICATION)", value = notificationVolume, onValueChange = { ScreenOffOptimizerManager.setNotificationVolume(it) })
                                    VolumeSliderItem(label = "Alarm (STREAM_ALARM)", value = alarmVolume, onValueChange = { ScreenOffOptimizerManager.setAlarmVolume(it) })
                                    VolumeSliderItem(label = "Media (STREAM_MUSIC)", value = mediaVolume, onValueChange = { ScreenOffOptimizerManager.setMediaVolume(it) })
                                }
                            }
                        }

                        // Toggle 3: Restore Ring Mode on Screen-On
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Restore Ring Mode on Screen-On", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(text = "Restore ringer mode back to normal when device unlocks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                ArworaAnimatedSwitch(
                                    checked = disableSilent,
                                    onCheckedChange = { checked ->
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        SoundEffectManager.playClick()
                                        if (checked && !hasPolicyAccess) {
                                            showPermissionDialog = true
                                        }
                                        ScreenOffOptimizerManager.setDisableSilentMode(checked)
                                    },
                                    modifier = Modifier.testTag("switch_disable_silent")
                                )
                            }
                        }

                        // Toggle 4: Turn Off Bluetooth & Location
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.BluetoothDisabled, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "Turn Off Standby Radios", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(text = "Bluetooth & Location optimization", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    ArworaAnimatedSwitch(
                                        checked = turnOffRadios,
                                        onCheckedChange = {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            SoundEffectManager.playClick()
                                            ScreenOffOptimizerManager.setTurnOffRadios(it)
                                        },
                                        modifier = Modifier.testTag("switch_turn_off_radios")
                                    )
                                }

                                if (turnOffRadios) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "Use Shizuku API", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (shizukuPermissionGranted) "Status: Authorized" else "Status: Unauthorized / Not Running",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (shizukuPermissionGranted) Color(0xFF10B981) else Color(0xFFEF4444)
                                            )
                                        }
                                        ArworaAnimatedSwitch(
                                            checked = useShizuku,
                                            onCheckedChange = { checked ->
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                SoundEffectManager.playClick()
                                                if (checked && !shizukuPermissionGranted) {
                                                    com.arwora.utils.ShizukuConnectionManager.connect(
                                                        context = context,
                                                        requestCode = 101,
                                                        onConnected = {
                                                            ScreenOffOptimizerManager.setShizukuPermissionGranted(true)
                                                        }
                                                    )
                                                }
                                                ScreenOffOptimizerManager.setUseShizuku(checked)
                                            }
                                        )
                                    }
                                    if (useShizuku && !shizukuPermissionGranted) {
                                        Text(
                                            text = "Advanced automation will fallback to system shell (requires Root/ADB) if Shizuku is not granted.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
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

@Composable
fun VolumeSliderItem(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "$value%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 0,
            modifier = Modifier.height(24.dp)
        )
    }
}
