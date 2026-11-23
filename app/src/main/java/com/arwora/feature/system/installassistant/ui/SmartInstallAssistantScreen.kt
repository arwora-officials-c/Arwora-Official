package com.arwora.feature.system.installassistant.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arwora.feature.system.installassistant.data.SmartInstallAssistantManager
import com.arwora.feature.system.installassistant.model.AppInstallCategory
import com.arwora.feature.system.installassistant.model.InstalledAppAssistantRecord
import com.arwora.feature.system.installassistant.model.OnlineAppInfoResult
import com.arwora.feature.system.installassistant.model.OnlineSyncStatus
import com.arwora.feature.system.installassistant.model.SetupChecklistStep
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.ui.components.GoogleStyleLoadingView
import com.arwora.utils.SoundEffectManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartInstallAssistantScreen(
    initialPackageName: String? = null,
    onBack: () -> Unit,
    onOpenAppOpsForPackage: (String) -> Unit,
    vm: SmartInstallAssistantViewModel = viewModel()
) {
    val context = LocalContext.current
    val filteredRecords by vm.filteredRecords.collectAsStateWithLifecycle()
    val allRecords by vm.allRecords.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val selectedCategory by vm.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val selectedRecord by vm.selectedRecord.collectAsStateWithLifecycle()
    val syncingPackages by vm.syncingPackages.collectAsStateWithLifecycle()
    val categoryCounts by vm.categoryCounts.collectAsStateWithLifecycle()

    LaunchedEffect(initialPackageName) {
        if (!initialPackageName.isNullOrBlank()) {
            vm.selectPackage(initialPackageName)
        }
    }

    BackHandler(enabled = selectedRecord != null) {
        SoundEffectManager.playClick()
        vm.selectPackage(null)
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
                            text = "Installation Assistant",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isLoading) {
                            ArworaPillBadge(
                                text = "${allRecords.size}",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            if (selectedRecord != null) {
                                vm.selectPackage(null)
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("install_assistant_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            vm.refresh()
                            Toast.makeText(context, "Refreshing installed apps...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("install_assistant_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.setSearchQuery(it) },
                placeholder = { Text("Search installed apps, packages, or categories...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = ArworaDesignTokens.cardBorderColor(),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("install_assistant_search_input")
            )

            // Category filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("install_assistant_category_filters")
            ) {
                items(AppInstallCategory.filterTabs, key = { it.id }) { category ->
                    val count = categoryCounts[category] ?: 0
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            SoundEffectManager.playClick()
                            vm.selectCategory(category)
                        },
                        label = {
                            Text(
                                text = if (count > 0 || category == AppInstallCategory.ALL) {
                                    "${category.label} ($count)"
                                } else {
                                    category.label
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = ArworaDesignTokens.PillShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = category.accentColor.copy(alpha = 0.18f),
                            selectedLabelColor = category.accentColor
                        ),
                        modifier = Modifier.testTag("category_chip_${category.id}")
                    )
                }
            }

            when {
                isLoading && allRecords.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        GoogleStyleLoadingView(title = "Scanning recent installations...")
                    }
                }

                filteredRecords.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = ArworaDesignTokens.CardShape,
                            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
                            border = ArworaDesignTokens.cardBorder()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "No Matching Apps Found",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Try switching categories, clearing search filters, or installing a new app.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("install_assistant_list")
                    ) {
                        items(filteredRecords, key = { it.packageName }) { record ->
                            InstallAssistantAppCard(
                                record = record,
                                onClick = {
                                    SoundEffectManager.playClick()
                                    vm.selectPackage(record.packageName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    val activeRecord = selectedRecord
    if (activeRecord != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { vm.selectPackage(null) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            InstallAssistantDetailSheet(
                record = activeRecord,
                isSyncingOnline = syncingPackages.contains(activeRecord.packageName),
                onToggleStep = { step, checked ->
                    SoundEffectManager.playClick()
                    vm.toggleChecklistStep(activeRecord.packageName, step, checked)
                },
                onOpenBatterySettings = {
                    SoundEffectManager.playClick()
                    vm.markStepReviewed(activeRecord.packageName, SetupChecklistStep.BATTERY_OPTIMIZATION)
                    val ok = SmartInstallAssistantManager.openBatteryOptimizationSettings(context, activeRecord.packageName)
                    if (!ok) {
                        Toast.makeText(context, "Unable to open battery settings", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenNotificationSettings = {
                    SoundEffectManager.playClick()
                    vm.markStepReviewed(activeRecord.packageName, SetupChecklistStep.NOTIFICATIONS)
                    val ok = SmartInstallAssistantManager.openAppNotificationSettings(context, activeRecord.packageName)
                    if (!ok) {
                        Toast.makeText(context, "Unable to open notification settings", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenPermissionsSettings = {
                    SoundEffectManager.playClick()
                    vm.markStepReviewed(activeRecord.packageName, SetupChecklistStep.DANGEROUS_PERMISSIONS)
                    val ok = SmartInstallAssistantManager.openAppPermissionsSettings(context, activeRecord.packageName)
                    if (!ok) {
                        Toast.makeText(context, "Unable to open app settings", Toast.LENGTH_SHORT).show()
                    }
                },
                onOpenAppOps = {
                    SoundEffectManager.playClick()
                    vm.markStepReviewed(activeRecord.packageName, SetupChecklistStep.APP_OPS_BACKGROUND)
                    vm.selectPackage(null)
                    onOpenAppOpsForPackage(activeRecord.packageName)
                },
                onSyncOnlineInfo = {
                    SoundEffectManager.playClick()
                    vm.syncOnlineMetadata(activeRecord.packageName)
                },
                onRefreshAudit = {
                    SoundEffectManager.playClick()
                    vm.reAuditPackage(activeRecord.packageName)
                }
            )
        }
    }
}

@Composable
private fun InstallAssistantAppCard(
    record: InstalledAppAssistantRecord,
    onClick: () -> Unit
) {
    val statusColor = if (record.isFullyReviewed) Color(0xFF10B981) else Color(0xFFF59E0B)
    val grantedDangerous = record.grantedDangerousCount
    val totalDangerous = record.dangerousPermissions.size

    Card(
        onClick = onClick,
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp()),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("install_assistant_card_${record.packageName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppPackageIcon(
                    packageName = record.packageName,
                    contentDescription = record.appName,
                    size = 44.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${record.packageName} • v${record.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                ArworaPillBadge(
                    text = record.statusBadgeText,
                    tint = statusColor,
                    showDot = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = ArworaDesignTokens.PillShape,
                    color = record.category.accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, record.category.accentColor.copy(alpha = 0.30f))
                ) {
                    Text(
                        text = record.category.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = record.category.accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    shape = ArworaDesignTokens.PillShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                ) {
                    val permText = if (totalDangerous == 0) {
                        "No Sensitive Perms"
                    } else {
                        "$grantedDangerous/$totalDangerous Sensitive Perms"
                    }
                    Text(
                        text = permText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = formatRelativeTimestamp(record.installedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { record.setupProgressFraction },
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InstallAssistantDetailSheet(
    record: InstalledAppAssistantRecord,
    isSyncingOnline: Boolean,
    onToggleStep: (SetupChecklistStep, Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenPermissionsSettings: () -> Unit,
    onOpenAppOps: () -> Unit,
    onSyncOnlineInfo: () -> Unit,
    onRefreshAudit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App summary header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppPackageIcon(
                packageName = record.packageName,
                contentDescription = record.appName,
                size = 52.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = record.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArworaPillBadge(text = record.category.label, tint = record.category.accentColor)
                    ArworaPillBadge(text = "v${record.versionName}", tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(
                onClick = onRefreshAudit,
                modifier = Modifier.testTag("detail_refresh_audit_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Re-audit package")
            }
        }

        HorizontalDivider(color = ArworaDesignTokens.cardBorderColor())

        // Setup Checklist Section
        Text(
            text = "App Setup Checklist (${record.completedCount}/${record.totalChecklistSteps})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        ChecklistStepCard(
            step = SetupChecklistStep.BATTERY_OPTIMIZATION,
            checked = record.completedSteps.contains(SetupChecklistStep.BATTERY_OPTIMIZATION),
            icon = Icons.Default.BatteryChargingFull,
            statusSummary = if (record.batteryOptimizationIgnored) {
                "Status: Unrestricted (Battery optimization ignored)"
            } else {
                "Status: Optimized (System may restrict background runs)"
            },
            statusColor = if (record.batteryOptimizationIgnored) Color(0xFF10B981) else Color(0xFFF59E0B),
            onCheckedChange = { onToggleStep(SetupChecklistStep.BATTERY_OPTIMIZATION, it) },
            onActionClick = onOpenBatterySettings,
            testTag = "checklist_step_battery"
        )

        val notifStatusText = when (record.notificationsAllowed) {
            true -> "Status: Notification permission granted"
            false -> "Status: Notification permission denied"
            null -> "Status: Open system settings to configure notification channels"
        }
        val notifStatusColor = when (record.notificationsAllowed) {
            true -> Color(0xFF10B981)
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.primary
        }

        ChecklistStepCard(
            step = SetupChecklistStep.NOTIFICATIONS,
            checked = record.completedSteps.contains(SetupChecklistStep.NOTIFICATIONS),
            icon = Icons.Default.NotificationsActive,
            statusSummary = notifStatusText,
            statusColor = notifStatusColor,
            onCheckedChange = { onToggleStep(SetupChecklistStep.NOTIFICATIONS, it) },
            onActionClick = onOpenNotificationSettings,
            testTag = "checklist_step_notifications"
        )

        ChecklistStepCard(
            step = SetupChecklistStep.DANGEROUS_PERMISSIONS,
            checked = record.completedSteps.contains(SetupChecklistStep.DANGEROUS_PERMISSIONS),
            icon = Icons.Default.Security,
            statusSummary = if (record.dangerousPermissions.isEmpty()) {
                "No sensitive runtime permissions requested"
            } else {
                "${record.grantedDangerousCount} granted of ${record.dangerousPermissions.size} dangerous permissions"
            },
            statusColor = if (record.grantedDangerousCount > 0) Color(0xFFF59E0B) else Color(0xFF10B981),
            onCheckedChange = { onToggleStep(SetupChecklistStep.DANGEROUS_PERMISSIONS, it) },
            onActionClick = onOpenPermissionsSettings,
            testTag = "checklist_step_permissions"
        ) {
            if (record.dangerousPermissions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    record.dangerousPermissions.take(12).forEach { perm ->
                        val chipColor = if (perm.isGranted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                        Surface(
                            shape = ArworaDesignTokens.PillShape,
                            color = chipColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, chipColor.copy(alpha = 0.30f))
                        ) {
                            Text(
                                text = "${perm.groupLabel}: ${if (perm.isGranted) "Granted" else "Denied"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = chipColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        ChecklistStepCard(
            step = SetupChecklistStep.APP_OPS_BACKGROUND,
            checked = record.completedSteps.contains(SetupChecklistStep.APP_OPS_BACKGROUND),
            icon = Icons.Default.AdminPanelSettings,
            statusSummary = "Review standby bucket, wake locks & background run ops in App Ops",
            statusColor = MaterialTheme.colorScheme.primary,
            onCheckedChange = { onToggleStep(SetupChecklistStep.APP_OPS_BACKGROUND, it) },
            onActionClick = onOpenAppOps,
            testTag = "checklist_step_app_ops"
        )

        // Online App Info Sync Card
        OnlineAppSyncPanel(
            packageName = record.packageName,
            metadata = record.onlineMetadata,
            isSyncing = isSyncingOnline,
            onSyncClick = onSyncOnlineInfo
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ChecklistStepCard(
    step: SetupChecklistStep,
    checked: Boolean,
    icon: ImageVector,
    statusSummary: String,
    statusColor: Color,
    onCheckedChange: (Boolean) -> Unit,
    onActionClick: () -> Unit,
    testTag: String,
    extraContent: (@Composable () -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaTonalIconBadge(
                    icon = icon,
                    tint = if (checked) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                    size = 38.dp,
                    iconSize = 20.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = step.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Checkbox(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.testTag("${testTag}_checkbox")
                )
            }

            Text(
                text = statusSummary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = statusColor
            )

            extraContent?.invoke()

            OutlinedButton(
                onClick = onActionClick,
                shape = ArworaDesignTokens.PillShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("${testTag}_action")
            ) {
                Text(
                    text = step.actionLabel,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OnlineAppSyncPanel(
    packageName: String,
    metadata: OnlineAppInfoResult?,
    isSyncing: Boolean,
    onSyncClick: () -> Unit
) {
    Card(
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("online_app_sync_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Online App Information Sync",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (metadata != null) {
                    val statusTint = when (metadata.status) {
                        OnlineSyncStatus.SUCCESS -> Color(0xFF10B981)
                        OnlineSyncStatus.OFFLINE,
                        OnlineSyncStatus.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                    ArworaPillBadge(text = metadata.status.name.replace('_', ' '), tint = statusTint)
                }
            }

            if (isSyncing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Fetching public metadata & verifying APK signature...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (metadata != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = if (metadata.status == OnlineSyncStatus.ERROR || metadata.status == OnlineSyncStatus.OFFLINE) {
                                Icons.Default.Warning
                            } else {
                                Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            tint = if (metadata.status == OnlineSyncStatus.ERROR || metadata.status == OnlineSyncStatus.OFFLINE) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color(0xFF10B981)
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = metadata.syncSummary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (metadata.storeTitle.isNotBlank()) {
                        Text(
                            text = "Listing Title: ${metadata.storeTitle}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (metadata.installerSource.isNotBlank()) {
                        Text(
                            text = "Installer Origin: ${metadata.installerSource}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (metadata.signingSha256.isNotBlank()) {
                        Text(
                            text = "APK SHA-256: ${metadata.signingSha256.take(35)}...",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = "Verify $packageName installer origin, APK SHA-256 certificate, and public store listing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = onSyncClick,
                enabled = !isSyncing,
                shape = ArworaDesignTokens.PillShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sync_online_metadata_button")
            ) {
                Text(
                    text = if (isSyncing) "Syncing..." else if (metadata != null) "Refresh Metadata" else "Sync App Metadata",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun formatRelativeTimestamp(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "Recent"
    val diff = System.currentTimeMillis() - timestampMillis
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestampMillis))
    }
}
