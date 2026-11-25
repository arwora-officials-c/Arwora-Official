package com.arwora.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.service.AutoScrollAccessibilityService
import com.arwora.service.AutoScrollOverlayService
import com.arwora.utils.AutoScrollMode
import com.arwora.utils.AutoVideoScrollManager
import com.arwora.utils.SoundEffectManager

@Composable
fun AutoVideoScrollCard(
    onToastMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val selectedMode by AutoVideoScrollManager.mode.collectAsStateWithLifecycle()
    val intervalSeconds by AutoVideoScrollManager.intervalSeconds.collectAsStateWithLifecycle()
    val isServiceActive by AutoVideoScrollManager.isServiceActive.collectAsStateWithLifecycle()
    val isPaused by AutoVideoScrollManager.isPaused.collectAsStateWithLifecycle()
    val scrollCount by AutoVideoScrollManager.scrollCount.collectAsStateWithLifecycle()
    val countdownRemaining by AutoVideoScrollManager.countdownSecondsRemaining.collectAsStateWithLifecycle()
    val statusDetail by AutoVideoScrollManager.statusDetail.collectAsStateWithLifecycle()
    val isAccessibilityServiceRunning by AutoVideoScrollManager.isAccessibilityRunning.collectAsStateWithLifecycle()

    var isAccessibilityReady by remember {
        mutableStateOf(AutoVideoScrollManager.isAccessibilityServiceEnabled(context))
    }
    var canDrawOverlays by remember {
        mutableStateOf(AutoVideoScrollManager.canDrawOverlays(context))
    }

    fun updatePermissionStatusUI() {
        isAccessibilityReady = AutoVideoScrollManager.isAccessibilityServiceEnabled(context)
        canDrawOverlays = AutoVideoScrollManager.canDrawOverlays(context)
    }

    LaunchedEffect(Unit) {
        AutoVideoScrollManager.init(context)
        updatePermissionStatusUI()
    }

    LaunchedEffect(isVisible, isServiceActive, isAccessibilityServiceRunning) {
        if (isVisible) {
            updatePermissionStatusUI()
        }
    }

    var showOverlayHelpDialog by remember { mutableStateOf(false) }
    var showAccessibilityHelpDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Foreground service still runs safely regardless of notification grant on Android 13+
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                updatePermissionStatusUI()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        val accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val stateListener = AccessibilityManager.AccessibilityStateChangeListener {
            updatePermissionStatusUI()
        }
        accessibilityManager?.addAccessibilityStateChangeListener(stateListener)

        val servicesStateListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            AccessibilityManager.AccessibilityServicesStateChangeListener {
                updatePermissionStatusUI()
            }.also { listener ->
                accessibilityManager?.addAccessibilityServicesStateChangeListener(listener)
            }
        } else {
            null
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            accessibilityManager?.removeAccessibilityStateChangeListener(stateListener)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && servicesStateListener != null) {
                accessibilityManager?.removeAccessibilityServicesStateChangeListener(servicesStateListener)
            }
        }
    }

    fun startAutoScrollService() {
        updatePermissionStatusUI()

        if (!canDrawOverlays) {
            showOverlayHelpDialog = true
            return
        }
        if (!isAccessibilityReady) {
            showAccessibilityHelpDialog = true
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotifPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotifPerm) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        runCatching {
            AutoScrollOverlayService.start(context)
            SoundEffectManager.playSuccess()
            onToastMessage("Auto Video Scroll active! Open TikTok, Reels, or Shorts.")
        }.onFailure { err ->
            onToastMessage("Could not start Auto Video Scroll: ${err.localizedMessage ?: "Error"}")
        }
    }

    fun stopAutoScrollService() {
        runCatching {
            AutoScrollOverlayService.stop(context)
        }
        AutoVideoScrollManager.setServiceActive(false)
        SoundEffectManager.playClick()
        onToastMessage("Auto Video Scroll stopped.")
    }

    if (showAccessibilityHelpDialog) {
        AccessibilityGuidanceDialog(
            title = "Auto Scroll Accessibility Required",
            description = "Auto Video Scroll needs Android Accessibility Service permission to swipe up to the next video on TikTok, Reels, and Shorts.",
            serviceName = "Auto Video Scroll Service",
            isEnabled = isAccessibilityReady,
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
                        text = "To display the floating Pause/Stop control pill above TikTok, Reels, or Shorts, allow 'Display over other apps'.",
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
                                text = "1. Tap 'Grant Permission' below\n2. Enable 'Allow display over other apps'\n3. Return and toggle Auto Video Scroll ON",
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
                        openOverlaySettings(context)
                    }
                ) {
                    Text("Grant Permission", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayHelpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val cardBgColor = ArworaDesignTokens.cardContainerColor()
    val cardShape = ArworaDesignTokens.CardShape
    val cardBorder = ArworaDesignTokens.cardBorder()
    val cardElevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())

    val statusColor = when {
        isServiceActive && !isPaused -> Color(0xFF10B981)
        isServiceActive && isPaused -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    }
    val statusBadgeText = when {
        isServiceActive && !isPaused -> "Running / Monitoring Feed"
        isServiceActive && isPaused -> "Paused"
        else -> "Idle"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("auto_scroll_card"),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder,
        elevation = cardElevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card Header: Icon, Title, Subtitle, Master Floating Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaTonalIconBadge(
                    icon = Icons.Default.SlowMotionVideo,
                    size = 46.dp,
                    iconSize = 23.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto Video Scroll",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Auto-scroll TikTok, Reels & Shorts",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                ArworaAnimatedSwitch(
                    checked = isServiceActive,
                    onCheckedChange = { enable ->
                        if (enable) startAutoScrollService() else stopAutoScrollService()
                    },
                    modifier = Modifier.testTag("auto_scroll_master_switch")
                )
            }

            // Live Status Indicator Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaPillBadge(
                    text = statusBadgeText,
                    tint = statusColor,
                    showDot = true,
                    modifier = Modifier.testTag("auto_scroll_status_badge")
                )

                if (isServiceActive) {
                    Text(
                        text = if (selectedMode == AutoScrollMode.TIME_BASED && !isPaused) {
                            "${countdownRemaining}s • $scrollCount swipes"
                        } else {
                            "$scrollCount swipes"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = if (isAccessibilityReady && canDrawOverlays) "Ready" else "Setup Needed",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAccessibilityReady && canDrawOverlays) {
                            Color(0xFF10B981)
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            if (isServiceActive && statusDetail.isNotBlank()) {
                Surface(
                    shape = ArworaDesignTokens.InnerTileShape,
                    color = statusColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.28f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusDetail,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalPauseChip(
                            isPaused = isPaused,
                            onClick = { AutoVideoScrollManager.togglePaused() }
                        )
                    }
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )

            // Mode Selector Segmented Buttons
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Scroll Trigger Mode",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedMode == AutoScrollMode.TIME_BASED,
                        onClick = {
                            SoundEffectManager.playClick()
                            AutoVideoScrollManager.setMode(AutoScrollMode.TIME_BASED)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("auto_scroll_mode_time")
                    ) {
                        Text(
                            text = "Time-Based (${intervalSeconds}s)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SegmentedButton(
                        selected = selectedMode == AutoScrollMode.COMPLETION_BASED,
                        onClick = {
                            SoundEffectManager.playClick()
                            AutoVideoScrollManager.setMode(AutoScrollMode.COMPLETION_BASED)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("auto_scroll_mode_completion")
                    ) {
                        Text(
                            text = "Completion-Based",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Custom Time Interval Controls (shown smoothly for Time-Based mode)
            AnimatedVisibility(visible = selectedMode == AutoScrollMode.TIME_BASED) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Wait Duration Before Scroll",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    AutoVideoScrollManager.setIntervalSeconds(intervalSeconds - 5)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("auto_scroll_interval_minus")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Decrease wait seconds",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = "${intervalSeconds}s",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    AutoVideoScrollManager.setIntervalSeconds(intervalSeconds + 5)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("auto_scroll_interval_plus")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Increase wait seconds",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(10, 15, 20, 30, 45, 60).forEach { sec ->
                            val isSelected = intervalSeconds == sec
                            Surface(
                                shape = ArworaDesignTokens.PillShape,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                border = if (isSelected) {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
                                } else {
                                    ArworaDesignTokens.innerTileBorder()
                                },
                                modifier = Modifier
                                    .clip(ArworaDesignTokens.PillShape)
                                    .clickable {
                                        SoundEffectManager.playClick()
                                        AutoVideoScrollManager.setIntervalSeconds(sec)
                                    }
                                    .testTag("auto_scroll_interval_chip_$sec")
                            ) {
                                Text(
                                    text = "${sec}s",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = selectedMode == AutoScrollMode.COMPLETION_BASED) {
                Text(
                    text = "Monitors active video progress, timestamps, and loop resets across short video feeds and swipes automatically when playback ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            // Permission Status Chips (Strictly reflect live system state)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PermissionTileButton(
                    label = if (isAccessibilityReady) "Accessibility Ready" else "Enable Accessibility",
                    granted = isAccessibilityReady,
                    icon = Icons.Default.Accessibility,
                    onClick = { showAccessibilityHelpDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("auto_scroll_accessibility_btn")
                )
                PermissionTileButton(
                    label = if (canDrawOverlays) "Overlay Ready" else "Allow Overlay",
                    granted = canDrawOverlays,
                    icon = Icons.Default.PictureInPictureAlt,
                    onClick = { showOverlayHelpDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("auto_scroll_overlay_btn")
                )
            }

            AnimatedVisibility(visible = !isAccessibilityReady) {
                Surface(
                    shape = ArworaDesignTokens.InnerTileShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                    border = ArworaDesignTokens.innerTileBorder(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_scroll_accessibility_helper")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Accessibility,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Enable 'Auto Video Scroll Service' under Installed apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = ArworaDesignTokens.PillShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)),
                            modifier = Modifier
                                .clip(ArworaDesignTokens.PillShape)
                                .clickable {
                                    SoundEffectManager.playClick()
                                    showAccessibilityHelpDialog = true
                                }
                                .testTag("auto_scroll_accessibility_guide_btn")
                        ) {
                            Text(
                                text = "View Guide",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Supported Platforms Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("TikTok", "Instagram Reels", "Facebook Reels", "YouTube Shorts").forEach { name ->
                    Surface(
                        shape = ArworaDesignTokens.PillShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = ArworaDesignTokens.innerTileBorder()
                    ) {
                        Text(
                            text = name,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            softWrap = false,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilledTonalPauseChip(
    isPaused: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = ArworaDesignTokens.PillShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .clip(ArworaDesignTokens.PillShape)
            .clickable(onClick = onClick)
            .testTag("auto_scroll_pause_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = if (isPaused) "Resume" else "Pause",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (isPaused) "Resume" else "Pause",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PermissionTileButton(
    label: String,
    granted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (granted) Color(0xFF10B981) else Color(0xFFF59E0B)
    Surface(
        shape = ArworaDesignTokens.InnerTileShape,
        color = accent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(ArworaDesignTokens.InnerTileShape)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun openOverlaySettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Could not open Overlay settings", Toast.LENGTH_SHORT).show()
    }
}
