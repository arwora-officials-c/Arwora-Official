package com.arwora.feature.system.appops.ui

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.arwora.feature.system.appops.model.AppOpsFilter
import com.arwora.feature.system.appops.model.AppOpsItem
import com.arwora.feature.system.appops.model.AppOpsSort
import com.arwora.feature.system.appops.model.StandbyBucketMode
import com.arwora.ui.components.GoogleStyleLoadingView
import com.arwora.utils.ShizukuConnectionManager
import com.arwora.utils.SoundEffectManager

@Composable
fun AppOpsContainerCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val outlineAlpha = if (isLight) 0.25f else 0.4f
    val cardBgColor = if (isLight) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    }
    val elevationDp = if (isLight) 1.dp else 0.dp
    val cardShape = RoundedCornerShape(22.dp)
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = outlineAlpha))
    val cardColors = CardDefaults.cardColors(containerColor = cardBgColor)
    val cardElevation = CardDefaults.cardElevation(defaultElevation = elevationDp)

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
    val rawApps by vm.rawApps.collectAsStateWithLifecycle()
    val filteredApps by vm.filteredApps.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    val isShizukuConnected by vm.isShizukuConnected.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by vm.selectedFilter.collectAsStateWithLifecycle()
    val selectedSort by vm.selectedSort.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val selectedApp by vm.selectedApp.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        vm.checkShizuku()
        val shizukuListeners = ShizukuConnectionManager.registerListeners(
            onBinderChanged = { vm.checkShizuku() },
            onPermissionResult = { _, granted ->
                vm.checkShizuku()
                if (granted) {
                    vm.loadApps()
                }
            }
        )
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.checkShizuku()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            ShizukuConnectionManager.unregisterListeners(shizukuListeners)
        }
    }

    BackHandler(enabled = selectedApp != null) {
        SoundEffectManager.playClick()
        vm.clearSelectedApp()
    }

    Scaffold(
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
                            fontWeight = FontWeight.Bold
                        )
                        if (selectedApp == null && !isLoading) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
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
        ) {
            if (selectedApp != null) {
                AppOpsDetailView(
                    app = selectedApp!!,
                    isShizukuConnected = isShizukuConnected,
                    onBack = {
                        SoundEffectManager.playClick()
                        vm.clearSelectedApp()
                    },
                    onStandbyBucketChange = { newBucket ->
                        vm.updateStandbyBucketForApp(selectedApp!!.packageName, newBucket)
                        Toast.makeText(context, "Standby profile set to ${newBucket.title}", Toast.LENGTH_SHORT).show()
                    },
                    onUpdateAppOp = { opName, mode ->
                        vm.updateAppOpState(selectedApp!!.packageName, opName, mode) { success ->
                            if (success) {
                                Toast.makeText(context, "App Op $opName set to $mode", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed (Requires Shizuku or Root)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onRevokeAll = {
                        vm.revokeAllSensitiveOps(selectedApp!!.packageName) { success ->
                            if (success) {
                                Toast.makeText(context, "Revoked all sensitive ops successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to revoke ops (Requires Shizuku/Root)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onRequestShizuku = {
                        SoundEffectManager.playClick()
                        ShizukuConnectionManager.connect(
                            context = context,
                            requestCode = 100,
                            onConnected = {
                                vm.checkShizuku()
                                vm.loadApps()
                            },
                            onPermissionRequested = {
                                vm.checkShizuku()
                            }
                        )
                    }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Shizuku Connection Fallback Banner if not connected
                    if (!isShizukuConnected) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Column {
                                        Text(
                                            text = "Shizuku / Root Inactive",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "Direct App Ops modification requires Shizuku or Root permissions.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        SoundEffectManager.playClick()
                                        ShizukuConnectionManager.connect(
                                            context = context,
                                            requestCode = 100,
                                            onConnected = {
                                                vm.checkShizuku()
                                                vm.loadApps()
                                            },
                                            onPermissionRequested = {
                                                vm.checkShizuku()
                                            }
                                        )
                                    },
                                    modifier = Modifier.testTag("app_ops_connect_button")
                                ) {
                                    Text("Connect", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
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

                    // Horizontal Filter Tabs
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
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
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
                                shape = RoundedCornerShape(12.dp),
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
                                    subtitle = "Inspecting package metadata, standby buckets, and permissions",
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
                                            text = "Try adjusting your search query or filter chip.",
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
}

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
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (appBitmap != null) {
                    Image(
                        bitmap = appBitmap.asImageBitmap(),
                        contentDescription = app.appName,
                        modifier = Modifier.size(42.dp)
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
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
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor,
        contentColor = textColor
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun AppOpsDetailView(
    app: AppOpsItem,
    isShizukuConnected: Boolean,
    onBack: () -> Unit,
    onStandbyBucketChange: (StandbyBucketMode) -> Unit,
    onUpdateAppOp: (String, String) -> Unit,
    onRevokeAll: () -> Unit,
    onRequestShizuku: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!isShizukuConnected) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shizuku / Root Inactive",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "App Op toggles require Shizuku or Root shell execution.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onRequestShizuku,
                        modifier = Modifier.testTag("app_ops_detail_connect_button")
                    ) {
                        Text("Connect", fontWeight = FontWeight.Bold)
                    }
                }
            }
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

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    StatusTag("v${app.versionName}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // QUICK ACTION BUTTONS ROW
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
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open", fontWeight = FontWeight.Bold)
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
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (app.isSystem) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error
                )
            ) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Uninstall", fontWeight = FontWeight.Bold)
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
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Force Stop", fontWeight = FontWeight.Bold)
            }
        }

        // BATCH REVOKE SENSITIVE OPS BUTTON
        Button(
            onClick = {
                SoundEffectManager.playClick()
                onRevokeAll()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(imageVector = Icons.Default.SecurityUpdateWarning, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Revoke All Sensitive Ops", fontWeight = FontWeight.Bold)
        }

        // ADVANCED APP OPS SYSTEM ACCESS CONTROLS CARD
        AppOpsContainerCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Advanced App Ops System Toggles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Configure high-level system permissions via direct Shizuku / Root shell execution:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val opsToManage = listOf(
                    Triple("SYSTEM_ALERT_WINDOW", "Display Over Other Apps", "Allows drawing overlays on top of other windows"),
                    Triple("WRITE_SETTINGS", "Modify System Settings", "Allows changing system configuration flags"),
                    Triple("CAMERA", "Camera Access", "Hardware camera sensor access"),
                    Triple("COARSE_LOCATION", "Location Access", "GPS and Network location services"),
                    Triple("RUN_IN_BACKGROUND", "Run in Background", "Background execution privileges")
                )

                opsToManage.forEach { (opName, opTitle, opDesc) ->
                    val currentState = app.appOpsStates[opName] ?: "default"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column {
                            Text(
                                text = opTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = opDesc,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }

                        // Mode Selector Buttons (Allow, Ignore, Deny, Default) strictly aligned horizontally
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val modes = listOf("allow", "ignore", "deny", "default")
                            modes.forEach { mode ->
                                val isSelected = currentState.equals(mode, ignoreCase = true)
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    onClick = {
                                        SoundEffectManager.playClick()
                                        onUpdateAppOp(opName, mode)
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = mode.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // STANDBY BUCKET CONTROLS CARD
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
                    text = "Android dynamically throttles background execution based on standby states. Select a bucket to enforce restrictions:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                StandbyBucketMode.entries.forEach { bucket ->
                    val isSelected = app.standbyBucket == bucket
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                SoundEffectManager.playClick()
                                onStandbyBucketChange(bucket)
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        },
                        border = if (isSelected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        } else null
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

        // PACKAGE INFO CARD
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
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(imageVector = Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open System Application Details Settings", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun FeatureIndicatorChip(
    title: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
            Text(
                text = if (active) "Active" else "Restricted",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
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
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $label",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
