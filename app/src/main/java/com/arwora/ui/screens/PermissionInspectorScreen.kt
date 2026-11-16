package com.arwora.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.arwora.ui.components.GoogleStyleLinearLoadingCard
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PermissionDetail(
    val permissionName: String,
    val simpleName: String,
    val isGranted: Boolean,
    val isDangerous: Boolean,
    val description: String? = null
)

data class AppPermissionItem(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val icon: Drawable?,
    val dangerousPermissions: List<PermissionDetail>,
    val totalPermissionsCount: Int,
    val grantedDangerousCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionInspectorScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppPermissionItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) } // 0 = All with Dangerous, 1 = User Apps, 2 = System Apps, 3 = High Risk (>=3 Dangerous)
    var expandedPackageNames by remember { mutableStateOf<Set<String>>(emptySet()) }

    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val subtleBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.15f else 0.25f)

    // Load applications safely in IO dispatcher
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            safeQueryInstalledAppPermissions(context)
        }
        loading = false
    }

    val filteredApps = remember(apps, searchQuery, selectedFilter) {
        apps.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true) ||
                    app.dangerousPermissions.any { it.simpleName.contains(searchQuery, ignoreCase = true) }

            val matchesFilter = when (selectedFilter) {
                1 -> !app.isSystemApp
                2 -> app.isSystemApp
                3 -> app.grantedDangerousCount >= 3
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Permission Inspector",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "${apps.size} apps analyzed for security",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        loading = true
                        scope.launch {
                            apps = withContext(Dispatchers.IO) {
                                safeQueryInstalledAppPermissions(context)
                            }
                            loading = false
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Overview Stats Card
            val totalDangerousGranted = remember(apps) { apps.sumOf { it.grantedDangerousCount } }
            val highRiskCount = remember(apps) { apps.count { it.grantedDangerousCount >= 3 } }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLight) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.25f else 0.45f)),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Runtime Permissions",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Inspect sensitive and dangerous permissions granted to installed apps.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$highRiskCount",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "High Risk",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = {
                    Text(
                        text = "Search by app name, package, or permission...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = subtleBorderColor,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Filter Chips Row
            ScrollableTabRow(
                selectedTabIndex = selectedFilter,
                edgePadding = 16.dp,
                containerColor = Color.Transparent,
                indicator = {},
                divider = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                val filters = listOf(
                    "All (${apps.size})",
                    "User Apps (${apps.count { !it.isSystemApp }})",
                    "System Apps (${apps.count { it.isSystemApp }})",
                    "High Risk ($highRiskCount)"
                )
                filters.forEachIndexed { index, label ->
                    val isSelected = selectedFilter == index
                    Tab(
                        selected = isSelected,
                        onClick = {
                            SoundEffectManager.playClick()
                            selectedFilter = index
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                            .border(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else subtleBorderColor,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // List Content
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GoogleStyleLinearLoadingCard(
                        title = "Analyzing application permissions safely...",
                        subtitle = "Auditing declared and runtime permissions across installed apps",
                        icon = Icons.Default.VerifiedUser
                    )
                }
            } else if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        )
                        Text(
                            "No Matching Apps Found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            if (searchQuery.isNotEmpty()) "Try changing your search keywords."
                            else "No applications matched the current filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { appItem ->
                        val isExpanded = expandedPackageNames.contains(appItem.packageName)
                        AppPermissionCard(
                            app = appItem,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                SoundEffectManager.playClick()
                                expandedPackageNames = if (isExpanded) {
                                    expandedPackageNames - appItem.packageName
                                } else {
                                    expandedPackageNames + appItem.packageName
                                }
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AppPermissionCard(
    app: AppPermissionItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val subtleBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.15f else 0.25f)

    val bitmap = remember(app.icon) {
        try {
            app.icon?.toBitmap(96, 96)
        } catch (e: Exception) {
            null
        }
    }

    val riskColor = when {
        app.grantedDangerousCount >= 4 -> MaterialTheme.colorScheme.error
        app.grantedDangerousCount >= 1 -> Color(0xFFF59E0B) // Amber
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLight) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.25f else 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "${app.appName} Icon",
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        Text(
                            text = app.appName.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        // System or User badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (app.isSystemApp) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            border = BorderStroke(
                                1.dp,
                                if (app.isSystemApp) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            )
                        ) {
                            Text(
                                text = if (app.isSystemApp) "System" else "User",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = if (app.isSystemApp) MaterialTheme.colorScheme.onTertiaryContainer
                                else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Permission Count Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = riskColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, riskColor.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = riskColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${app.grantedDangerousCount} Dangerous",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = riskColor
                        )
                    }
                }
            }

            // Expanded Details Section
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = subtleBorderColor)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Action Buttons Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Open System App Settings
                            Button(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${app.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot open app settings", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Manage Permissions", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }

                            // Copy Package Name
                            OutlinedButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    clipboardManager.setText(AnnotatedString(app.packageName))
                                    Toast.makeText(context, "Package copied", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, subtleBorderColor),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Permission details list
                        Text(
                            "DETAILED PERMISSION AUDIT (${app.dangerousPermissions.size} SENSITIVE)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )

                        if (app.dangerousPermissions.isEmpty()) {
                            Text(
                                "No dangerous runtime permissions declared.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                app.dangerousPermissions.forEach { perm ->
                                    PermissionRowItem(perm)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Total Manifest Permissions: ${app.totalPermissionsCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionRowItem(perm: PermissionDetail) {
    val isGranted = perm.isGranted
    val statusColor = if (isGranted) Color(0xFF10B981) else Color(0xFF64748B) // Green or Slate
    val isLight = MaterialTheme.colorScheme.background.red > 0.8f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isLight) Color(0xFFF8FAFC)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            )
            .border(
                0.75.dp,
                if (isGranted) statusColor.copy(alpha = 0.35f) else Color(0xFF94A3B8).copy(alpha = 0.2f),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Column {
                Text(
                    text = perm.simpleName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = perm.permissionName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF64748B).copy(alpha = 0.12f),
            border = BorderStroke(
                1.dp,
                if (isGranted) Color(0xFF10B981).copy(alpha = 0.3f) else Color(0xFF64748B).copy(alpha = 0.2f)
            )
        ) {
            Text(
                text = if (isGranted) "GRANTED" else "DENIED / IDLE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = if (isGranted) Color(0xFF059669) else Color(0xFF64748B),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Safely queries all installed applications and their permissions,
 * catching any SecurityExceptions or NameNotFoundExceptions across all Android versions.
 */
private fun safeQueryInstalledAppPermissions(context: Context): List<AppPermissionItem> {
    val pm = context.packageManager
    val resultList = mutableListOf<AppPermissionItem>()

    try {
        // Safe query for installed packages with permissions flags
        val flags = (PackageManager.GET_PERMISSIONS).toLong()
        val packages: List<PackageInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags.toInt())
            }
        } catch (e: Exception) {
            // Fallback: Query installed applications first, then fetch package info safely
            val apps = try {
                pm.getInstalledApplications(0)
            } catch (ex: Exception) {
                emptyList()
            }
            apps.mapNotNull { appInfo ->
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageInfo(appInfo.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageInfo(appInfo.packageName, PackageManager.GET_PERMISSIONS)
                    }
                } catch (ex: Exception) {
                    null
                }
            }
        }

        for (pkgInfo in packages) {
            try {
                val appInfo = pkgInfo.applicationInfo ?: continue
                val pkgName = pkgInfo.packageName ?: continue
                val appLabel = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkgName
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val appIcon = try {
                    pm.getApplicationIcon(appInfo)
                } catch (e: Exception) {
                    null
                }

                val requestedPerms = pkgInfo.requestedPermissions
                val requestedFlags = pkgInfo.requestedPermissionsFlags

                val dangerousList = mutableListOf<PermissionDetail>()
                var totalPerms = 0

                if (requestedPerms != null) {
                    totalPerms = requestedPerms.size
                    for (i in requestedPerms.indices) {
                        val permName = requestedPerms[i] ?: continue
                        val isGranted = if (requestedFlags != null && i < requestedFlags.size) {
                            (requestedFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                        } else {
                            try {
                                pm.checkPermission(permName, pkgName) == PackageManager.PERMISSION_GRANTED
                            } catch (e: Exception) {
                                false
                            }
                        }

                        // Safely check protection level with try-catch to prevent NameNotFoundException
                        val isDangerous = try {
                            val permInfo = pm.getPermissionInfo(permName, 0)
                            val baseLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                permInfo.protection
                            } else {
                                @Suppress("DEPRECATION")
                                (permInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
                            }
                            baseLevel == PermissionInfo.PROTECTION_DANGEROUS
                        } catch (e: Exception) {
                            // Fallback heuristic for standard dangerous Android permissions if getPermissionInfo throws
                            isStandardDangerousPermission(permName)
                        }

                        if (isDangerous) {
                            val simpleName = formatPermissionSimpleName(permName)
                            dangerousList.add(
                                PermissionDetail(
                                    permissionName = permName,
                                    simpleName = simpleName,
                                    isGranted = isGranted,
                                    isDangerous = true
                                )
                            )
                        }
                    }
                }

                // Sort: Granted first, then by simple name
                dangerousList.sortWith(compareByDescending<PermissionDetail> { it.isGranted }.thenBy { it.simpleName })

                val grantedCount = dangerousList.count { it.isGranted }

                resultList.add(
                    AppPermissionItem(
                        appName = appLabel,
                        packageName = pkgName,
                        isSystemApp = isSystem,
                        icon = appIcon,
                        dangerousPermissions = dangerousList,
                        totalPermissionsCount = totalPerms,
                        grantedDangerousCount = grantedCount
                    )
                )
            } catch (e: Exception) {
                // Ignore single app parsing errors
            }
        }
    } catch (e: Exception) {
        // Fallback catch for safety
    }

    // Sort apps: High risk (most granted dangerous permissions) first, then alphabetical
    return resultList.sortedWith(
        compareByDescending<AppPermissionItem> { it.grantedDangerousCount }
            .thenBy { it.appName.lowercase() }
    )
}

/**
 * Standard known dangerous permissions fallback for when getPermissionInfo is restricted or throws.
 */
private fun isStandardDangerousPermission(perm: String): Boolean {
    return perm.startsWith("android.permission.") && (
            perm.contains("CAMERA") ||
                    perm.contains("RECORD_AUDIO") ||
                    perm.contains("FINE_LOCATION") ||
                    perm.contains("COARSE_LOCATION") ||
                    perm.contains("READ_CONTACTS") ||
                    perm.contains("WRITE_CONTACTS") ||
                    perm.contains("GET_ACCOUNTS") ||
                    perm.contains("READ_CALENDAR") ||
                    perm.contains("WRITE_CALENDAR") ||
                    perm.contains("READ_CALL_LOG") ||
                    perm.contains("WRITE_CALL_LOG") ||
                    perm.contains("PROCESS_OUTGOING_CALLS") ||
                    perm.contains("READ_PHONE_STATE") ||
                    perm.contains("CALL_PHONE") ||
                    perm.contains("USE_SIP") ||
                    perm.contains("BODY_SENSORS") ||
                    perm.contains("ACTIVITY_RECOGNITION") ||
                    perm.contains("SEND_SMS") ||
                    perm.contains("RECEIVE_SMS") ||
                    perm.contains("READ_SMS") ||
                    perm.contains("RECEIVE_WAP_PUSH") ||
                    perm.contains("RECEIVE_MMS") ||
                    perm.contains("READ_EXTERNAL_STORAGE") ||
                    perm.contains("WRITE_EXTERNAL_STORAGE") ||
                    perm.contains("READ_MEDIA_IMAGES") ||
                    perm.contains("READ_MEDIA_VIDEO") ||
                    perm.contains("READ_MEDIA_AUDIO") ||
                    perm.contains("POST_NOTIFICATIONS") ||
                    perm.contains("NEARBY_WIFI_DEVICES") ||
                    perm.contains("BLUETOOTH_CONNECT") ||
                    perm.contains("BLUETOOTH_SCAN")
            )
}

private fun formatPermissionSimpleName(perm: String): String {
    val lastPart = perm.substringAfterLast(".")
    return when (lastPart) {
        "CAMERA" -> "Camera"
        "RECORD_AUDIO" -> "Microphone"
        "ACCESS_FINE_LOCATION" -> "Precise Location"
        "ACCESS_COARSE_LOCATION" -> "Approximate Location"
        "READ_CONTACTS" -> "Read Contacts"
        "WRITE_CONTACTS" -> "Modify Contacts"
        "READ_CALENDAR" -> "Read Calendar"
        "WRITE_CALENDAR" -> "Modify Calendar"
        "READ_CALL_LOG" -> "Read Call Log"
        "WRITE_CALL_LOG" -> "Modify Call Log"
        "READ_PHONE_STATE" -> "Phone State"
        "CALL_PHONE" -> "Direct Call"
        "SEND_SMS" -> "Send SMS"
        "READ_SMS" -> "Read SMS"
        "RECEIVE_SMS" -> "Receive SMS"
        "READ_MEDIA_IMAGES" -> "Photos & Images"
        "READ_MEDIA_VIDEO" -> "Videos"
        "READ_MEDIA_AUDIO" -> "Music & Audio"
        "READ_EXTERNAL_STORAGE" -> "Storage (Read)"
        "WRITE_EXTERNAL_STORAGE" -> "Storage (Write)"
        "POST_NOTIFICATIONS" -> "Notifications"
        "BODY_SENSORS" -> "Body Sensors"
        "ACTIVITY_RECOGNITION" -> "Physical Activity"
        "BLUETOOTH_SCAN" -> "Bluetooth Scan"
        "BLUETOOTH_CONNECT" -> "Bluetooth Connect"
        "NEARBY_WIFI_DEVICES" -> "Nearby WiFi"
        else -> lastPart.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }
}
