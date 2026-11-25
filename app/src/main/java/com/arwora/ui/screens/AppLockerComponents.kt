package com.arwora.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.arwora.ui.AppItem
import com.arwora.ui.components.ArworaAnimatedSwitch
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaMotionTokens
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.utils.AppLockerManager
import com.arwora.utils.BiometricStatus
import com.arwora.utils.LockableTool

private val EmeraldAccent = Color(0xFF10B981)
private val AmberAccent = Color(0xFFF59E0B)

@Composable
internal fun AppLockerHeroBannerCard(
    isLockerEnabled: Boolean,
    lockedToolCount: Int,
    lockedAppCount: Int,
    hasPin: Boolean,
    isBiometricEnabled: Boolean,
    biometricStatus: BiometricStatus,
    onToggleMaster: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val statusAccent by animateColorAsState(
        targetValue = if (isLockerEnabled) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = ArworaMotionTokens.CARD_TOGGLE_DURATION_MS),
        label = "locker_hero_accent"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isLockerEnabled) 1.04f else 1f,
        animationSpec = tween(durationMillis = ArworaMotionTokens.CARD_TOGGLE_DURATION_MS),
        label = "locker_hero_scale"
    )

    val cardBgColor = ArworaDesignTokens.cardContainerColor()
    val cardBorder = ArworaDesignTokens.cardBorder()
    val cardElevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("master_lock_card"),
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder,
        elevation = cardElevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .scale(iconScale)
                        .clip(CircleShape)
                        .background(
                            if (isLockerEnabled) EmeraldAccent.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isLockerEnabled) {
                                EmeraldAccent.copy(alpha = 0.34f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLockerEnabled) Icons.Default.Shield else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isLockerEnabled) EmeraldAccent else statusAccent,
                        modifier = Modifier.size(25.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isLockerEnabled) "App Locker Active" else "App Locker Inactive",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isLockerEnabled) {
                            "$lockedToolCount tools & $lockedAppCount apps secured"
                        } else {
                            "Enable to safeguard tools & apps"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                ArworaAnimatedSwitch(
                    checked = isLockerEnabled,
                    onCheckedChange = onToggleMaster,
                    modifier = Modifier.testTag("app_locker_master_switch")
                )
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )

            // Inline Auth Status Pills inside the unified Hero Card
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val pinTint = if (hasPin) MaterialTheme.colorScheme.primary else AmberAccent
                val pinLabel = if (hasPin) "PIN: Configured" else "PIN: Not Set"
                HeroInlineStatusPill(
                    icon = Icons.Default.Pin,
                    text = pinLabel,
                    tint = pinTint,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("app_locker_pin_status_pill")
                )

                val bioLabel = when {
                    isBiometricEnabled && biometricStatus == BiometricStatus.AVAILABLE -> "Biometrics: Enabled"
                    !isBiometricEnabled -> "Biometrics: Disabled"
                    else -> "Biometrics: Setup"
                }
                val bioTint = if (isBiometricEnabled && biometricStatus == BiometricStatus.AVAILABLE) {
                    EmeraldAccent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                HeroInlineStatusPill(
                    icon = Icons.Default.Fingerprint,
                    text = bioLabel,
                    tint = bioTint,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("app_locker_biometric_status_pill")
                )
            }
        }
    }
}

@Composable
private fun HeroInlineStatusPill(
    icon: ImageVector,
    text: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = ArworaDesignTokens.InnerTileShape,
        color = tint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun AppLockerSegmentedTabSelector(
    selectedTab: Int,
    lockedToolCount: Int,
    lockedAppCount: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val containerShape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_locker_tab_container"),
        shape = containerShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = ArworaDesignTokens.innerTileBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SegmentedPillTabItem(
                label = "Tools ($lockedToolCount)",
                icon = Icons.Default.Shield,
                selected = selectedTab == 0,
                onClick = { onSelectTab(0) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_tools")
            )
            SegmentedPillTabItem(
                label = "Apps ($lockedAppCount)",
                icon = Icons.Default.Apps,
                selected = selectedTab == 1,
                onClick = { onSelectTab(1) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_apps")
            )
            SegmentedPillTabItem(
                label = "Security",
                icon = Icons.Default.Security,
                selected = selectedTab == 2,
                onClick = { onSelectTab(2) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_security")
            )
        }
    }
}

@Composable
private fun SegmentedPillTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pillColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = ArworaMotionTokens.CARD_TOGGLE_DURATION_MS),
        label = "segmented_pill_bg"
    )
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = pillColor,
        border = if (selected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            null
        },
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ToolsLockerTab(
    cardShape: androidx.compose.ui.graphics.Shape,
    cardBorder: BorderStroke,
    cardBgColor: Color,
    lockedToolIds: Set<String>,
    onToggleTool: (String) -> Unit,
    onLockAll: () -> Unit,
    onUnlockAll: () -> Unit
) {
    val tools = remember { AppLockerManager.availableTools }
    val elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Tools & Features",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onLockAll,
                        shape = ArworaDesignTokens.PillShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .heightIn(min = 36.dp)
                            .testTag("app_locker_lock_all_btn")
                    ) {
                        Text(
                            text = "Lock All",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    OutlinedButton(
                        onClick = onUnlockAll,
                        shape = ArworaDesignTokens.PillShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .heightIn(min = 36.dp)
                            .testTag("app_locker_unlock_all_btn")
                    ) {
                        Text(
                            text = "Unlock All",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        items(tools, key = { it.id }) { tool ->
            val isLocked = lockedToolIds.contains(tool.id)
            ToolLockerItemCard(
                tool = tool,
                isLocked = isLocked,
                cardShape = cardShape,
                cardBorder = cardBorder,
                cardBgColor = cardBgColor,
                cardElevation = elevation,
                onToggle = { onToggleTool(tool.id) }
            )
        }
    }
}

@Composable
private fun ToolLockerItemCard(
    tool: LockableTool,
    isLocked: Boolean,
    cardShape: androidx.compose.ui.graphics.Shape,
    cardBorder: BorderStroke,
    cardBgColor: Color,
    cardElevation: androidx.compose.material3.CardElevation,
    onToggle: () -> Unit
) {
    val iconTint = if (isLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val containerBg = if (isLocked) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val containerBorder = if (isLocked) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .testTag("tool_lock_item_${tool.id}"),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder,
        elevation = cardElevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArworaTonalIconBadge(
                icon = if (isLocked) Icons.Default.Lock else toolIconForId(tool.id),
                tint = iconTint,
                containerColor = containerBg,
                borderColor = containerBorder,
                size = 44.dp,
                iconSize = 21.dp,
                cornerRadius = 14.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryPillTag(
                        category = tool.category,
                        highlighted = isLocked
                    )
                    Text(
                        text = tool.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            ArworaAnimatedSwitch(
                checked = isLocked,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun CategoryPillTag(
    category: String,
    highlighted: Boolean
) {
    val pillTint = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = ArworaDesignTokens.PillShape,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.75f)
        },
        border = BorderStroke(1.dp, pillTint.copy(alpha = 0.24f))
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = category,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    letterSpacing = 0.2.sp
                ),
                fontWeight = FontWeight.Bold,
                color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else pillTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AppsLockerTab(
    cardShape: androidx.compose.ui.graphics.Shape,
    cardBorder: BorderStroke,
    cardBgColor: Color,
    installedApps: List<AppItem>,
    lockedPackages: Set<String>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onToggleApp: (String) -> Unit
) {
    val filteredApps = remember(installedApps, lockedPackages, searchQuery) {
        val query = searchQuery.trim().lowercase()
        installedApps.filter { app ->
            if (query.isEmpty()) true
            else app.appName.lowercase().contains(query) || app.packageName.lowercase().contains(query)
        }.sortedByDescending { lockedPackages.contains(it.packageName) }
    }
    val elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_locker_search_input"),
                placeholder = { Text("Search installed apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            )
        }

        if (filteredApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (installedApps.isEmpty()) "Loading installed applications..." else "No apps match search",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredApps, key = { it.packageName }) { app ->
                val isLocked = lockedPackages.contains(app.packageName)
                val bitmap = remember(app.icon) { app.icon?.toBitmap() }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleApp(app.packageName) }
                        .testTag("app_lock_item_${app.packageName}"),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = cardBorder,
                    elevation = elevation
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = app.appName,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                                        RoundedCornerShape(14.dp)
                                    )
                            )
                        } else {
                            ArworaTonalIconBadge(
                                icon = Icons.Default.Apps,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                size = 44.dp,
                                iconSize = 21.dp,
                                cornerRadius = 14.dp
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CategoryPillTag(
                                    category = if (isLocked) "Locked" else "App",
                                    highlighted = isLocked
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        ArworaAnimatedSwitch(
                            checked = isLocked,
                            onCheckedChange = { onToggleApp(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecuritySettingsTab(
    cardShape: androidx.compose.ui.graphics.Shape,
    cardBorder: BorderStroke,
    cardBgColor: Color,
    hasPin: Boolean,
    isBiometricEnabled: Boolean,
    timeoutMinutes: Int,
    vibrateOnError: Boolean,
    scrambleKeypad: Boolean,
    biometricStatus: BiometricStatus,
    onSetupPin: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit,
    onTimeoutSelect: (Int) -> Unit,
    onToggleVibrate: (Boolean) -> Unit,
    onToggleScramble: (Boolean) -> Unit,
    onTestUnlock: () -> Unit
) {
    val elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = cardBorder,
                elevation = elevation
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.Pin,
                        size = 44.dp,
                        iconSize = 21.dp,
                        cornerRadius = 14.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Security PIN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (hasPin) "4-digit passcode configured" else "No passcode set yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSetupPin,
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier.heightIn(min = 40.dp)
                    ) {
                        Text(
                            text = if (hasPin) "Change PIN" else "Set PIN",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = cardBorder,
                elevation = elevation
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.Fingerprint,
                        size = 44.dp,
                        iconSize = 21.dp,
                        cornerRadius = 14.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Biometric Authentication",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when (biometricStatus) {
                                BiometricStatus.AVAILABLE -> "Fingerprint / Face Unlock ready"
                                BiometricStatus.NOT_ENROLLED -> "No fingerprint enrolled on device"
                                else -> "Hardware not supported"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    ArworaAnimatedSwitch(
                        checked = isBiometricEnabled,
                        onCheckedChange = onToggleBiometric
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = cardBorder,
                elevation = elevation
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Auto-Relock Timeout",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "When to prompt for authentication again after unlocking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val timeouts = listOf(
                        0 to "Immediately",
                        1 to "1 Minute",
                        5 to "5 Minutes",
                        15 to "15 Minutes"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        timeouts.forEach { (mins, label) ->
                            val selected = (timeoutMinutes == mins)
                            OutlinedButton(
                                onClick = { onTimeoutSelect(mins) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    } else {
                                        Color.Transparent
                                    }
                                ),
                                border = BorderStroke(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                    }
                                ),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                border = cardBorder,
                elevation = elevation
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Protection Options",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Vibrate on Failed Attempt",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Haptic feedback on incorrect PIN entry",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        ArworaAnimatedSwitch(checked = vibrateOnError, onCheckedChange = onToggleVibrate)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scramble Keypad",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Randomizes PIN digits to prevent shoulder-surfing",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        ArworaAnimatedSwitch(checked = scrambleKeypad, onCheckedChange = onToggleScramble)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    Button(
                        onClick = onTestUnlock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = ArworaDesignTokens.PillShape
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test App Locker Authentication", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun toolIconForId(toolId: String): ImageVector {
    return when (toolId) {
        "wifi_ctrl" -> Icons.Default.Wifi
        "dns_changer", "hide_private_dns" -> Icons.Default.Dns
        "fake_gps" -> Icons.Default.LocationOn
        "deep_scanner" -> Icons.Default.Memory
        "security_scan" -> Icons.Default.Security
        "perm_inspector" -> Icons.Default.VerifiedUser
        "notif_history" -> Icons.Default.Notifications
        "battery_alarm" -> Icons.Default.BatteryChargingFull
        "dup_finder", "storage_analyzer" -> Icons.Default.Storage
        "sound_direction" -> Icons.Default.GraphicEq
        "exif_editor" -> Icons.Default.Tune
        "app_clone" -> Icons.Default.ContentCopy
        "settings" -> Icons.Default.Settings
        else -> Icons.Default.LockOpen
    }
}
