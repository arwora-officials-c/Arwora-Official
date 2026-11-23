package com.arwora.feature.system.downloadworkflow.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.feature.system.downloadworkflow.data.DownloadWorkflowManager
import com.arwora.utils.SoundEffectManager
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import kotlinx.coroutines.launch

/**
 * System Dashboard card for Smart Download Workflow featuring Master ON/OFF switch
 * and 4 independent granular ON/OFF toggles matching [ArworaDesignTokens].
 */
@Composable
fun SmartDownloadWorkflowCard(
    onOpenWorkflowHub: (eventId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val config by DownloadWorkflowManager.config.collectAsStateWithLifecycle()
    val isServiceRunning by DownloadWorkflowManager.isServiceRunning.collectAsStateWithLifecycle()
    val events by DownloadWorkflowManager.processedEvents.collectAsStateWithLifecycle()
    val isScanning by DownloadWorkflowManager.isScanning.collectAsStateWithLifecycle()

    var hasStorageAccess by remember { mutableStateOf(DownloadWorkflowManager.hasStorageAccess(context)) }
    var hasNotifPerm by remember { mutableStateOf(DownloadWorkflowManager.hasNotificationPermission(context)) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPerm = granted
    }

    LaunchedEffect(Unit) {
        DownloadWorkflowManager.initIfNeeded(context)
        hasStorageAccess = DownloadWorkflowManager.hasStorageAccess(context)
        hasNotifPerm = DownloadWorkflowManager.hasNotificationPermission(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasStorageAccess = DownloadWorkflowManager.hasStorageAccess(context)
                hasNotifPerm = DownloadWorkflowManager.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activeMonitor = config.masterEnabled || isServiceRunning
    val statusColor = if (activeMonitor) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
    val statusText = if (activeMonitor) "Monitoring" else "Idle"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_download_workflow_card"),
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Header + Master Service Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaTonalIconBadge(
                    icon = Icons.Default.DownloadDone,
                    tint = if (activeMonitor) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    size = 46.dp,
                    contentDescription = "Smart Download Workflow"
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Smart Download Workflow",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Auto-organize, rename & detect duplicate downloads",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                ArworaPillBadge(
                    text = statusText,
                    tint = statusColor,
                    showDot = true,
                    modifier = Modifier.testTag("download_workflow_status_badge")
                )
            }

            // Master Service Switch Tile
            Surface(
                shape = ArworaDesignTokens.InnerTileShape,
                color = if (activeMonitor) {
                    Color(0xFF10B981).copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                },
                border = if (activeMonitor) {
                    BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.30f))
                } else {
                    ArworaDesignTokens.innerTileBorder()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            SoundEffectManager.playClick()
                            val next = !config.masterEnabled
                            if (next && !hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            scope.launch {
                                DownloadWorkflowManager.setMasterEnabled(context, next)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Master Monitoring Service",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Watch /Download/ folder for completed files (>1.5s stable)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.masterEnabled,
                        onCheckedChange = { enabled ->
                            SoundEffectManager.playClick()
                            if (enabled && !hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            scope.launch {
                                DownloadWorkflowManager.setMasterEnabled(context, enabled)
                            }
                        },
                        modifier = Modifier.testTag("download_workflow_master_switch")
                    )
                }
            }

            if (!hasStorageAccess) {
                Surface(
                    onClick = {
                        SoundEffectManager.playClick()
                        DownloadWorkflowManager.openStorageAccessSettings(context)
                    },
                    shape = ArworaDesignTokens.InnerTileShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.20f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_workflow_storage_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Storage access required to organize /Download/ files. Tap to grant.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))

            // 4 Granular ON/OFF Switches
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkflowGranularToggleRow(
                    title = "1. Completion & Categorization",
                    subtitle = "Sort finished files into APKs, Archives, Media & Documents",
                    icon = Icons.Default.FolderOpen,
                    checked = config.categorizationEnabled,
                    onCheckedChange = { enabled ->
                        SoundEffectManager.playClick()
                        scope.launch { DownloadWorkflowManager.setCategorizationEnabled(context, enabled) }
                    },
                    testTag = "toggle_download_categorization"
                )

                WorkflowGranularToggleRow(
                    title = "2. Automatic Rename",
                    subtitle = "Clean cluttered filenames and strip junk prefixes",
                    icon = Icons.Default.DriveFileRenameOutline,
                    checked = config.autoRenameEnabled,
                    onCheckedChange = { enabled ->
                        SoundEffectManager.playClick()
                        scope.launch { DownloadWorkflowManager.setAutoRenameEnabled(context, enabled) }
                    },
                    testTag = "toggle_download_auto_rename"
                )

                WorkflowGranularToggleRow(
                    title = "3. Duplicate Detection",
                    subtitle = "Scan MD5 hashes & sizes to flag duplicate downloads",
                    icon = Icons.Default.ContentCopy,
                    checked = config.duplicateDetectionEnabled,
                    onCheckedChange = { enabled ->
                        SoundEffectManager.playClick()
                        scope.launch { DownloadWorkflowManager.setDuplicateDetectionEnabled(context, enabled) }
                    },
                    testTag = "toggle_download_duplicate_detection"
                )

                WorkflowGranularToggleRow(
                    title = "4. Instant Notification",
                    subtitle = "Rich alerts with Open Folder & View Details actions",
                    icon = Icons.Default.NotificationsActive,
                    checked = config.instantNotificationEnabled,
                    onCheckedChange = { enabled ->
                        SoundEffectManager.playClick()
                        if (enabled && !hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        scope.launch { DownloadWorkflowManager.setInstantNotificationEnabled(context, enabled) }
                    },
                    testTag = "toggle_download_instant_notification"
                )
            }

            // Recent summary pill bar if events exist
            if (events.isNotEmpty()) {
                val latest = events.first()
                val dupCount = events.count { it.isDuplicate }
                Surface(
                    onClick = {
                        SoundEffectManager.playClick()
                        onOpenWorkflowHub(latest.id)
                    },
                    shape = ArworaDesignTokens.InnerTileShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                    border = ArworaDesignTokens.innerTileBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Latest: ${latest.finalName}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${events.size} processed • $dupCount duplicate(s) flagged",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ArworaPillBadge(
                            text = latest.category.label,
                            tint = latest.category.accentColor
                        )
                    }
                }
            }

            // Clean Action Buttons (no emojis or sparkles per AGENTS.md)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        scope.launch {
                            val count = DownloadWorkflowManager.scanDownloadDirectoryNow(
                                context = context,
                                stabilityDurationMs = 0L,
                                requireMasterEnabled = false
                            )
                            val msg = if (count > 0) "Processed $count download(s)" else "Downloads checked"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isScanning,
                    shape = ArworaDesignTokens.PillShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("download_workflow_scan_button")
                ) {
                    Text(
                        text = if (isScanning) "Scanning..." else "Scan Downloads",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = {
                        SoundEffectManager.playClick()
                        onOpenWorkflowHub(null)
                    },
                    shape = ArworaDesignTokens.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("download_workflow_open_hub_button")
                ) {
                    Text(
                        text = "Open Workflow Hub",
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
internal fun WorkflowGranularToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = ArworaDesignTokens.InnerTileShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
        border = ArworaDesignTokens.innerTileBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
