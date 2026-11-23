package com.arwora.feature.system.downloadworkflow.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arwora.feature.system.downloadworkflow.data.DownloadWorkflowEngine
import com.arwora.feature.system.downloadworkflow.data.DownloadWorkflowManager
import com.arwora.feature.system.downloadworkflow.model.ProcessedDownloadEvent
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.utils.SoundEffectManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartDownloadWorkflowScreen(
    initialEventId: String? = null,
    onBack: () -> Unit,
    vm: SmartDownloadWorkflowViewModel = viewModel()
) {
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    val isServiceRunning by vm.isServiceRunning.collectAsStateWithLifecycle()
    val filteredEvents by vm.filteredEvents.collectAsStateWithLifecycle()
    val allEvents by vm.allEvents.collectAsStateWithLifecycle()
    val selectedFilter by vm.selectedFilter.collectAsStateWithLifecycle()
    val selectedEvent by vm.selectedEvent.collectAsStateWithLifecycle()
    val isScanning by vm.isScanning.collectAsStateWithLifecycle()
    val statusMessage by vm.statusMessage.collectAsStateWithLifecycle()

    var hasNotifPerm by remember { mutableStateOf(DownloadWorkflowManager.hasNotificationPermission(context)) }
    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotifPerm = granted }

    LaunchedEffect(initialEventId) {
        if (!initialEventId.isNullOrBlank()) {
            vm.selectEvent(initialEventId)
        }
    }

    BackHandler(enabled = selectedEvent != null) {
        SoundEffectManager.playClick()
        vm.selectEvent(null)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Download Workflow",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ArworaPillBadge(
                            text = "${allEvents.size}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            if (selectedEvent != null) {
                                vm.selectEvent(null)
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("download_workflow_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (allEvents.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                vm.clearLogs()
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            vm.scanNow()
                        },
                        enabled = !isScanning,
                        modifier = Modifier.testTag("download_workflow_hub_scan_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan Downloads")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .testTag("download_workflow_hub_list")
        ) {
            // Controls Card
            item {
                Card(
                    shape = ArworaDesignTokens.CardShape,
                    colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                    border = ArworaDesignTokens.cardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Workflow Rules & Toggles",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (config.masterEnabled || isServiceRunning) {
                                        "Active background monitor • /Download/"
                                    } else {
                                        "Background monitor paused"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = config.masterEnabled,
                                onCheckedChange = { enabled ->
                                    SoundEffectManager.playClick()
                                    if (enabled && !hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    vm.setMasterEnabled(enabled)
                                },
                                modifier = Modifier.testTag("hub_master_switch")
                            )
                        }

                        WorkflowGranularToggleRow(
                            title = "1. Completion & Categorization",
                            subtitle = "Sort into APKs, Archives, Media, Documents",
                            icon = Icons.Default.FolderOpen,
                            checked = config.categorizationEnabled,
                            onCheckedChange = { vm.setCategorizationEnabled(it) },
                            testTag = "hub_toggle_categorization"
                        )
                        WorkflowGranularToggleRow(
                            title = "2. Automatic Rename",
                            subtitle = "Strip junk prefixes and clean formatting",
                            icon = Icons.Default.DriveFileRenameOutline,
                            checked = config.autoRenameEnabled,
                            onCheckedChange = { vm.setAutoRenameEnabled(it) },
                            testTag = "hub_toggle_auto_rename"
                        )
                        WorkflowGranularToggleRow(
                            title = "3. Duplicate Detection",
                            subtitle = "Check MD5 hashes & sizes to flag duplicates",
                            icon = Icons.Default.ContentCopy,
                            checked = config.duplicateDetectionEnabled,
                            onCheckedChange = { vm.setDuplicateDetectionEnabled(it) },
                            testTag = "hub_toggle_duplicate_detection"
                        )
                        WorkflowGranularToggleRow(
                            title = "4. Instant Notification",
                            subtitle = "Send high-priority completion & duplicate alerts",
                            icon = Icons.Default.NotificationsActive,
                            checked = config.instantNotificationEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                vm.setInstantNotificationEnabled(enabled)
                            },
                            testTag = "hub_toggle_instant_notification"
                        )

                        if (!statusMessage.isNullOrBlank()) {
                            Text(
                                text = statusMessage!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workflow_filter_row")
                ) {
                    items(DownloadWorkflowFilter.entries, key = { it.id }) { filter ->
                        val selected = selectedFilter == filter
                        FilterChip(
                            selected = selected,
                            onClick = {
                                SoundEffectManager.playClick()
                                vm.selectFilter(filter)
                            },
                            label = {
                                Text(
                                    text = filter.label,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = ArworaDesignTokens.PillShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("workflow_filter_${filter.id}")
                        )
                    }
                }
            }

            if (filteredEvents.isEmpty()) {
                item {
                    Card(
                        shape = ArworaDesignTokens.CardShape,
                        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                        border = ArworaDesignTokens.cardBorder(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DownloadDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = "No Processed Downloads Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enable the Master Service Switch or tap Scan Downloads to organize completed files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    vm.scanNow()
                                },
                                shape = ArworaDesignTokens.PillShape,
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Scan Downloads", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(filteredEvents, key = { it.id }) { event ->
                    ProcessedDownloadEventCard(
                        event = event,
                        onClick = {
                            SoundEffectManager.playClick()
                            vm.selectEvent(event.id)
                        }
                    )
                }
            }
        }
    }

    val detailEvent = selectedEvent
    if (detailEvent != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { vm.selectEvent(null) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DownloadWorkflowEventDetailSheet(
                event = detailEvent,
                onOpenFolder = {
                    SoundEffectManager.playClick()
                    val ok = DownloadWorkflowManager.openDownloadFolder(
                        context,
                        File(detailEvent.finalPath).parent
                    )
                    if (!ok) {
                        Toast.makeText(context, "Unable to open Downloads folder", Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteFile = {
                    SoundEffectManager.playClick()
                    vm.deleteFile(detailEvent.id) { ok ->
                        Toast.makeText(
                            context,
                            if (ok) "Deleted ${detailEvent.finalName}" else "Unable to delete file",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
    }
}

@Composable
private fun ProcessedDownloadEventCard(
    event: ProcessedDownloadEvent,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = if (event.isDuplicate) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
        } else {
            ArworaDesignTokens.cardBorder()
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_event_card_${event.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaTonalIconBadge(
                    icon = event.category.icon,
                    tint = event.category.accentColor,
                    size = 42.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.finalName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${DownloadWorkflowEngine.formatBytes(event.sizeBytes)} • ${formatEventTime(event.processedAtMillis)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ArworaPillBadge(
                    text = if (event.isDuplicate) "Duplicate" else event.category.label,
                    tint = if (event.isDuplicate) MaterialTheme.colorScheme.error else event.category.accentColor
                )
            }

            if (event.wasRenamed) {
                Text(
                    text = "Renamed from: ${event.originalName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (event.wasMoved) {
                Text(
                    text = "Sorted to: Download/${event.category.folderName}/",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (event.isDuplicate) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Matches MD5 of ${event.duplicateOfName ?: "previous download"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadWorkflowEventDetailSheet(
    event: ProcessedDownloadEvent,
    onOpenFolder: () -> Unit,
    onDeleteFile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArworaTonalIconBadge(
                icon = event.category.icon,
                tint = event.category.accentColor,
                size = 46.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.finalName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${event.category.label} • ${DownloadWorkflowEngine.formatBytes(event.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (event.isDuplicate) {
                ArworaPillBadge(text = "Duplicate", tint = MaterialTheme.colorScheme.error)
            }
        }

        HorizontalDivider(color = ArworaDesignTokens.cardBorderColor())

        DetailRow("Original Name", event.originalName)
        DetailRow("Final Name", event.finalName)
        DetailRow("Original Path", event.originalPath)
        DetailRow("Current Path", event.finalPath)
        if (event.md5Hash.isNotBlank()) {
            DetailRow("MD5 Checksum", event.md5Hash, monospace = true)
        }
        if (event.isDuplicate) {
            DetailRow("Duplicate Of", event.duplicateOfName ?: "Previously processed download")
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDeleteFile,
                shape = ArworaDesignTokens.PillShape,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("detail_delete_download_button")
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (event.isDuplicate) "Delete Duplicate" else "Delete File", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onOpenFolder,
                shape = ArworaDesignTokens.PillShape,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("detail_open_folder_button")
            ) {
                Text("Open Folder", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    monospace: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatEventTime(millis: Long): String {
    if (millis <= 0L) return "Recent"
    return SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(millis))
}
