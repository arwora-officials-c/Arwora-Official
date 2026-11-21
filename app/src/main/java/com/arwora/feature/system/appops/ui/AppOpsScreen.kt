package com.arwora.feature.system.appops.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SecurityUpdateWarning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arwora.feature.system.appops.data.AppOpsManager
import com.arwora.feature.system.appops.model.AppOpDescriptor
import com.arwora.feature.system.appops.model.AppOpsCategory
import com.arwora.feature.system.appops.model.AppOpsFilter
import com.arwora.feature.system.appops.model.AppOpsItem
import com.arwora.feature.system.appops.model.AppOpsSort
import com.arwora.feature.system.appops.model.StandbyBucketMode
import com.arwora.ui.components.AdbPairingBottomSheet
import com.arwora.ui.components.AdbPairingSheetTab
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.ui.components.GoogleStyleLoadingView
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.PrivilegeEngine
import com.arwora.utils.SoundEffectManager

@Composable
fun AppOpsContainerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardShape = ArworaDesignTokens.CardShape
    val cardBorder = ArworaDesignTokens.cardBorder()
    val cardColors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor())
    val cardElevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = cardShape,
            colors = cardColors,
            border = cardBorder,
            elevation = cardElevation,
            content = content
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = cardShape,
            colors = cardColors,
            border = cardBorder,
            elevation = cardElevation,
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOpsScreen(
    onBack: () -> Unit,
    vm: AppOpsViewModel = viewModel()
) {
    val context = LocalContext.current
    val filteredApps by vm.filteredApps.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val isRefreshingSelectedOps by vm.isRefreshingSelectedOps.collectAsStateWithLifecycle()
    val isAdbConnected by vm.isAdbConnected.collectAsStateWithLifecycle()
    val nativeAdbConnected by NativeAdbManager.isConnected.collectAsStateWithLifecycle()
    val activePrivilegeEngine by vm.activeEngine.collectAsStateWithLifecycle()
    val isShizukuRunning by AdbShellExecutor.isShizukuBinderAlive.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by vm.selectedFilter.collectAsStateWithLifecycle()
    val selectedSort by vm.selectedSort.collectAsStateWithLifecycle()
    val selectedDetailCategory by vm.selectedDetailCategory.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val selectedApp by vm.selectedApp.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }
    var showPairingSheet by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(nativeAdbConnected, activePrivilegeEngine) {
        vm.checkAdbStatus()
    }

    DisposableEffect(lifecycleOwner) {
        vm.checkAdbStatus()
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.checkAdbStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
        }
    }

    BackHandler(enabled = selectedApp != null) {
        SoundEffectManager.playClick()
        vm.clearSelectedApp()
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
                            text = if (selectedApp != null) "App Details" else "App Ops",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (selectedApp == null && !isLoading) {
                            Surface(
                                shape = ArworaDesignTokens.PillShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = "${counts.all}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            if (selectedApp != null) {
                                vm.clearSelectedApp()
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("app_ops_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isAdbConnected) {
                        ArworaPillBadge(
                            text = if (activePrivilegeEngine == PrivilegeEngine.SHIZUKU) "Shizuku" else "Wireless ADB",
                            tint = Color(0xFF10B981),
                            showDot = true,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .testTag("app_ops_engine_badge")
                        )
                    }

                    if (selectedApp == null) {
                        Box {
                            IconButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    showSortMenu = true
                                },
                                modifier = Modifier.testTag("app_ops_sort_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Sort Options"
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                AppOpsSort.entries.forEach { sort ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = sort.label,
                                                    fontWeight = if (selectedSort == sort) FontWeight.Bold else FontWeight.Normal
                                                )
                                                if (selectedSort == sort) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            SoundEffectManager.playClick()
                                            vm.setSort(sort)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                vm.loadApps()
                                Toast.makeText(context, "Refreshing packages...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("app_ops_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            if (selectedApp != null) {
                val activeApp = selectedApp!!
                AppOpsDetailView(
                    app = activeApp,
                    isAdbConnected = isAdbConnected,
                    isShizukuRunning = isShizukuRunning,
                    isRefreshingOps = isRefreshingSelectedOps,
                    selectedCategory = selectedDetailCategory,
                    onCategorySelect = { vm.setDetailCategory(it) },
                    onRefreshOps = { vm.refreshOpsForSelectedApp(activeApp.packageName) },
                    onStandbyBucketChange = { newBucket ->
                        vm.updateStandbyBucketForApp(activeApp.packageName, newBucket) { ok ->
                            val msg = if (ok) {
                                "Standby bucket set to ${newBucket.title}"
                            } else {
                                "ADB or Shizuku permission required for App Ops execution."
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onUpdateAppOp = { opName, mode ->
                        vm.updateAppOpState(activeApp.packageName, opName, mode) { success ->
                            if (success) {
                                Toast.makeText(context, "App Op $opName set to $mode", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Failed to set $opName (Requires Wireless ADB or Shizuku)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onRevokeAll = {
                        vm.revokeAllSensitiveOps(activeApp.packageName) { success ->
                            if (success) {
                                Toast.makeText(context, "Revoked all sensitive ops", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Requires Wireless ADB or Shizuku to revoke ops",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onOpenPairingWizard = {
                        SoundEffectManager.playClick()
                        showPairingSheet = true
                    },
                    onAuthorizeShizuku = {
                        SoundEffectManager.playClick()
                        vm.requestShizukuAuthorization(onUnavailable = { showPairingSheet = true })
                    }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!isAdbConnected) {
                        AppOpsPrivilegeWarningCard(
                            isShizukuRunning = isShizukuRunning,
                            onOpenPairingWizard = {
                                SoundEffectManager.playClick()
                                showPairingSheet = true
                            },
                            onAuthorizeShizuku = {
                                SoundEffectManager.playClick()
                                vm.requestShizukuAuthorization(onUnavailable = { showPairingSheet = true })
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { vm.setSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("app_ops_search_input"),
                        placeholder = { Text("Search apps by name or package...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        SoundEffectManager.playClick()
                                        vm.setSearchQuery("")
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    // Horizontal Category & Status Filter Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppOpsFilter.entries.forEach { filter ->
                            val count = when (filter) {
                                AppOpsFilter.ALL -> counts.all
                                AppOpsFilter.LOCATION -> counts.location
                                AppOpsFilter.CAMERA -> counts.camera
                                AppOpsFilter.AUDIO -> counts.audio
                                AppOpsFilter.SYSTEM_OPS -> counts.systemOps
                                AppOpsFilter.USER -> counts.user
                                AppOpsFilter.SYSTEM -> counts.system
                                AppOpsFilter.DISABLED -> counts.disabled
                                AppOpsFilter.HIDDEN -> counts.hidden
                                AppOpsFilter.ALERT_WINDOW -> counts.alertWindow
                                AppOpsFilter.BACKGROUND_LOCATION -> counts.bgLocation
                            }
                            val isSelected = selectedFilter == filter

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    SoundEffectManager.playClick()
                                    vm.setFilter(filter)
                                },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = filter.label,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1
                                        )
                                        Surface(
                                            shape = ArworaDesignTokens.PillShape,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            contentColor = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        ) {
                                            Text(
                                                text = "$count",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                },
                                shape = ArworaDesignTokens.PillShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("app_ops_filter_${filter.name.lowercase()}")
                            )
                        }
                    }

                    val contentPhase = when {
                        isLoading -> 0
                        filteredApps.isEmpty() -> 1
                        else -> 2
                    }

                    AnimatedContent(
                        targetState = contentPhase,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        label = "app_ops_content_state"
                    ) { phase ->
                        when (phase) {
                            0 -> {
                                GoogleStyleLoadingView(
                                    title = "Scanning installed applications",
                                    subtitle = "Querying App Ops permissions, standby buckets, and package metadata",
                                    icon = Icons.Default.AdminPanelSettings,
                                    showSkeletonRows = true,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            1 -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SearchOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(56.dp)
                                        )
                                        Text(
                                            text = "No matching applications found",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Try adjusting your search query or category filter.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        items = filteredApps,
                                        key = { it.packageName }
                                    ) { app ->
                                        AppOpsRowCard(
                                            app = app,
                                            onClick = {
                                                SoundEffectManager.playClick()
                                                vm.selectApp(app)
                                            }
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

    AdbPairingBottomSheet(
        visible = showPairingSheet,
        initialTab = AdbPairingSheetTab.AUTOMATIC,
        onDismissRequest = {
            showPairingSheet = false
            vm.checkAdbStatus()
        },
        onPairingComplete = {
            showPairingSheet = false
            vm.checkAdbStatus()
            vm.loadApps()
        }
    )
}

@Composable
fun AppOpsPrivilegeWarningCard(
    isShizukuRunning: Boolean,
    onOpenPairingWizard: () -> Unit,
    onAuthorizeShizuku: () -> Unit,
    modifier: Modifier = Modifier,
    connectButtonTag: String = "app_ops_connect_button"
) {
    Surface(
        onClick = onOpenPairingWizard,
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_ops_privilege_warning_card"),
        shape = ArworaDesignTokens.CardShape,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArworaTonalIconBadge(
                    icon = Icons.Default.Warning,
                    tint = MaterialTheme.colorScheme.error,
                    size = 40.dp,
                    iconSize = 20.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ADB or Shizuku permission required for App Ops execution. Tap to connect.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenPairingWizard,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag(connectButtonTag),
                    shape = ArworaDesignTokens.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        text = "Connect Wireless ADB",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isShizukuRunning) {
                    OutlinedButton(
                        onClick = onAuthorizeShizuku,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("app_ops_authorize_shizuku_button"),
                        shape = ArworaDesignTokens.PillShape
                    ) {
                        Text(
                            text = "Authorize Shizuku",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppOpsRowCard(
    app: AppOpsItem,
    onClick: () -> Unit
) {
    AppOpsContainerCard(
        onClick = onClick,
        modifier = Modifier.testTag("app_ops_row_${app.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val appBitmap = remember(app.packageName) {
                app.icon?.toBitmap(width = 88, height = 88, config = Bitmap.Config.ARGB_8888)
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(ArworaDesignTokens.IconBadgeShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (appBitmap != null) {
                    Image(
                        bitmap = appBitmap.asImageBitmap(),
                        contentDescription = app.appName,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (app.isSystem) Icons.Default.Settings else Icons.Default.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (app.isSystem) {
                        StatusTag(
                            label = "System",
                            bgColor = Color(0xFFFCE7F3),
                            textColor = Color(0xFFBE185D)
                        )
                    } else {
                        StatusTag(
                            label = "User",
                            bgColor = Color(0xFFD1FAE5),
                            textColor = Color(0xFF047857)
                        )
                    }

                    val allowedCount = app.allowedOpsCount
                    val restrictedCount = app.restrictedOpsCount
                    if (allowedCount > 0) {
                        StatusTag(
                            label = "$allowedCount Allowed",
                            bgColor = Color(0xFF10B981).copy(alpha = 0.16f),
                            textColor = Color(0xFF10B981)
                        )
                    }
                    if (restrictedCount > 0) {
                        StatusTag(
                            label = "$restrictedCount Restricted",
                            bgColor = Color(0xFFEF4444).copy(alpha = 0.16f),
                            textColor = Color(0xFFEF4444)
                        )
                    }
                    if (app.isDisabled) {
                        StatusTag(
                            label = "Disabled",
                            bgColor = Color(0xFFFEE2E2),
                            textColor = Color(0xFFB91C1C)
                        )
                    }
                    if (app.isHidden) {
                        StatusTag(
                            label = "Hidden",
                            bgColor = Color(0xFFEDE9FE),
                            textColor = Color(0xFF6D28D9)
                        )
                    }
                    if (app.hasAlertWindowPermission) {
                        StatusTag(
                            label = "Alert Window",
                            bgColor = Color(0xFFFEF3C7),
                            textColor = Color(0xFFB45309)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun StatusTag(
    label: String,
    bgColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = ArworaDesignTokens.PillShape,
        color = bgColor,
        contentColor = textColor
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun OpModeBadge(
    mode: String,
    modifier: Modifier = Modifier
) {
    val normalized = mode.lowercase()
    val (label, tint) = when (normalized) {
        "allow" -> "Allowed" to Color(0xFF10B981)
        "ignore" -> "Ignored" to Color(0xFFF59E0B)
        "deny", "errored" -> "Restricted" to Color(0xFFEF4444)
        else -> "Default" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    StatusTag(
        label = label,
        bgColor = tint.copy(alpha = 0.14f),
        textColor = tint,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppOpsDetailView(
    app: AppOpsItem,
    isAdbConnected: Boolean,
    isShizukuRunning: Boolean = false,
    isRefreshingOps: Boolean = false,
    selectedCategory: AppOpsCategory = AppOpsCategory.ALL,
    onCategorySelect: (AppOpsCategory) -> Unit = {},
    onRefreshOps: () -> Unit = {},
    onStandbyBucketChange: (StandbyBucketMode) -> Unit,
    onUpdateAppOp: (String, String) -> Unit,
    onRevokeAll: () -> Unit,
    onOpenPairingWizard: () -> Unit,
    onAuthorizeShizuku: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    val groupedCatalog = remember(selectedCategory) {
        val items = if (selectedCategory == AppOpsCategory.ALL) {
            AppOpsManager.managedOpsCatalog
        } else {
            AppOpsManager.managedOpsCatalog.filter { it.category == selectedCategory }
        }
        items.groupBy { it.category }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isAdbConnected) {
            AppOpsPrivilegeWarningCard(
                isShizukuRunning = isShizukuRunning,
                onOpenPairingWizard = onOpenPairingWizard,
                onAuthorizeShizuku = onAuthorizeShizuku,
                connectButtonTag = "app_ops_detail_connect_button"
            )
        }

        // App Header Card
        AppOpsContainerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val appBitmap = remember(app.packageName) {
                    app.icon?.toBitmap(width = 120, height = 120, config = Bitmap.Config.ARGB_8888)
                }

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (appBitmap != null) {
                        Image(
                            bitmap = appBitmap.asImageBitmap(),
                            contentDescription = app.appName,
                            modifier = Modifier.size(60.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (app.isSystem) Icons.Default.Settings else Icons.Default.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (app.isSystem) {
                        StatusTag("System", Color(0xFFFCE7F3), Color(0xFFBE185D))
                    } else {
                        StatusTag("User", Color(0xFFD1FAE5), Color(0xFF047857))
                    }
                    if (app.isDisabled) {
                        StatusTag("Disabled", Color(0xFFFEE2E2), Color(0xFFB91C1C))
                    }
                    if (app.isHidden) {
                        StatusTag("Hidden", Color(0xFFEDE9FE), Color(0xFF6D28D9))
                    }
                    StatusTag(
                        "${app.allowedOpsCount} Allowed",
                        Color(0xFF10B981).copy(alpha = 0.16f),
                        Color(0xFF10B981)
                    )
                    StatusTag(
                        "${app.restrictedOpsCount} Restricted",
                        Color(0xFFEF4444).copy(alpha = 0.16f),
                        Color(0xFFEF4444)
                    )
                    StatusTag(
                        "v${app.versionName}",
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Quick Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    SoundEffectManager.playClick()
                    val opened = AppOpsManager.openApp(context, app.packageName)
                    if (!opened) {
                        Toast.makeText(context, "Cannot open: app has no launcher interface", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("app_ops_action_open"),
                shape = ArworaDesignTokens.PillShape
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open", fontWeight = FontWeight.Bold, maxLines = 1)
            }

            OutlinedButton(
                onClick = {
                    SoundEffectManager.playClick()
                    if (app.isSystem) {
                        Toast.makeText(context, "System packages cannot be uninstalled", Toast.LENGTH_SHORT).show()
                    } else {
                        AppOpsManager.uninstallApp(context, app.packageName)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("app_ops_action_uninstall"),
                shape = ArworaDesignTokens.PillShape,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (app.isSystem) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error
                )
            ) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Uninstall", fontWeight = FontWeight.Bold, maxLines = 1)
            }

            OutlinedButton(
                onClick = {
                    SoundEffectManager.playClick()
                    AppOpsManager.openAppDetails(context, app.packageName)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("app_ops_action_force_stop"),
                shape = ArworaDesignTokens.PillShape
            ) {
                Icon(imageVector = Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Details", fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }

        // Batch Revoke CTA
        Button(
            onClick = {
                SoundEffectManager.playClick()
                onRevokeAll()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("app_ops_revoke_all_button"),
            shape = ArworaDesignTokens.PillShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(imageVector = Icons.Default.SecurityUpdateWarning, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Revoke All Sensitive Ops", fontWeight = FontWeight.Bold)
        }

        // Grouped App Ops Permissions Card
        AppOpsContainerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "App Ops Permissions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isRefreshingOps) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                onRefreshOps()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("app_ops_detail_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh App Ops"
                            )
                        }
                    }
                }

                // Category Filter Bar inside Detail View
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppOpsCategory.entries.forEach { category ->
                        val isSelected = selectedCategory == category
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                SoundEffectManager.playClick()
                                onCategorySelect(category)
                            },
                            label = {
                                Text(
                                    text = category.label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = ArworaDesignTokens.PillShape,
                            modifier = Modifier.testTag("app_ops_detail_category_${category.name.lowercase()}")
                        )
                    }
                }

                groupedCatalog.forEach { (category, ops) ->
                    val categoryIcon = when (category) {
                        AppOpsCategory.LOCATION -> Icons.Default.LocationOn
                        AppOpsCategory.CAMERA -> Icons.Default.CameraAlt
                        AppOpsCategory.AUDIO -> Icons.Default.Mic
                        else -> Icons.Default.Tune
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = categoryIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    ops.forEach { descriptor ->
                        AppOpControlTile(
                            descriptor = descriptor,
                            currentMode = app.appOpsStates[descriptor.opName] ?: "default",
                            onSelectMode = { mode -> onUpdateAppOp(descriptor.opName, mode) }
                        )
                    }
                }
            }
        }

        // Standby Bucket Card
        AppOpsContainerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "App Standby Bucket Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Applies standby limits via Wireless ADB / Shizuku (am set-standby-bucket):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                StandbyBucketMode.entries.forEach { bucket ->
                    val isSelected = app.standbyBucket == bucket
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ArworaDesignTokens.InnerTileShape)
                            .clickable {
                                SoundEffectManager.playClick()
                                onStandbyBucketChange(bucket)
                            }
                            .testTag("app_ops_standby_${bucket.name.lowercase()}"),
                        shape = ArworaDesignTokens.InnerTileShape,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        },
                        border = if (isSelected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else ArworaDesignTokens.innerTileBorder()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bucket.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = bucket.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    SoundEffectManager.playClick()
                                    onStandbyBucketChange(bucket)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Package Information Card
        AppOpsContainerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Package Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                InfoItemRow("Version Name", app.versionName)
                InfoItemRow("Version Code", "${app.versionCode}")
                InfoItemRow("UID", "${app.uid}")
                InfoItemRow("Target SDK", "${app.targetSdk}")
                InfoItemRow("Min SDK", "${app.minSdk}")
                InfoItemRow("First Install", app.firstInstallFormatted)
                InfoItemRow("Last Update", app.lastUpdateFormatted)
                InfoItemRow("Data Directory", app.dataDir, isCopyable = true, onCopy = {
                    clipboardManager.setText(AnnotatedString(app.dataDir))
                    Toast.makeText(context, "Copied data dir", Toast.LENGTH_SHORT).show()
                })
                InfoItemRow("Source APK", app.sourceDir, isCopyable = true, onCopy = {
                    clipboardManager.setText(AnnotatedString(app.sourceDir))
                    Toast.makeText(context, "Copied APK path", Toast.LENGTH_SHORT).show()
                })
            }
        }

        OutlinedButton(
            onClick = {
                SoundEffectManager.playClick()
                AppOpsManager.openAppDetails(context, app.packageName)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("app_ops_open_system_settings"),
            shape = ArworaDesignTokens.PillShape
        ) {
            Icon(imageVector = Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open System Application Details", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AppOpControlTile(
    descriptor: AppOpDescriptor,
    currentMode: String,
    onSelectMode: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_op_tile_${descriptor.opName}"),
        shape = ArworaDesignTokens.InnerTileShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        border = ArworaDesignTokens.innerTileBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = descriptor.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "OP_${descriptor.opName} • ${descriptor.description}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                OpModeBadge(
                    mode = currentMode,
                    modifier = Modifier.testTag("app_op_badge_${descriptor.opName}")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val modes = listOf("allow", "ignore", "deny", "default")
                modes.forEach { mode ->
                    val isSelected = currentMode.equals(mode, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("app_op_${descriptor.opName}_$mode"),
                        shape = ArworaDesignTokens.PillShape,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
                            }
                        ),
                        onClick = {
                            SoundEffectManager.playClick()
                            onSelectMode(mode)
                        }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoItemRow(
    label: String,
    value: String,
    isCopyable: Boolean = false,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isCopyable && onCopy != null) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $label",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
