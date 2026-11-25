package com.arwora.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.arwora.ui.AppItem
import com.arwora.ui.components.ArworaAnimatedSwitch
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.utils.AppLockerManager
import com.arwora.utils.HiderPrivilegeMode

private val EmeraldAccent = Color(0xFF10B981)
private val AmberAccent = Color(0xFFF59E0B)

@Composable
internal fun AdvancedSecuritySection(
    cardShape: Shape,
    cardBorder: BorderStroke,
    cardBgColor: Color,
    elevation: CardElevation,
    hiddenAppCount: Int,
    secretDialerCode: String,
    isSelfStealthEnabled: Boolean,
    isUninstallProtectionEnabled: Boolean,
    isInstallBlockEnabled: Boolean,
    isAccessibilityGuardEnabled: Boolean,
    isDeviceAdminEnabled: Boolean,
    onOpenHiddenVault: () -> Unit,
    onToggleUninstallProtection: (Boolean) -> Unit,
    onToggleInstallBlock: (Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestDeviceAdmin: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Advanced Security",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .testTag("advanced_security_section_header")
        )

        // 1. Hide Apps (Stealth Vault)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("advanced_hide_apps_card"),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = cardBorder,
            elevation = elevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.VisibilityOff,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 44.dp,
                        iconSize = 21.dp,
                        cornerRadius = 14.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hide Apps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$hiddenAppCount hidden • Dial *#*#$secretDialerCode#*#* for vault",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onOpenHiddenVault,
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier
                            .heightIn(min = 42.dp)
                            .testTag("open_hidden_apps_vault_button")
                    ) {
                        Text(
                            text = "Open Vault",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                if (isSelfStealthEnabled) {
                    GuardStatusPill(
                        label = "Self-Stealth Active: Dial *#*#$secretDialerCode#*#* to open Vault",
                        active = true
                    )
                }
            }
        }

        // 2. Prevent Uninstallation
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("advanced_uninstall_protect_card"),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = cardBorder,
            elevation = elevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.DeleteForever,
                        tint = if (isUninstallProtectionEnabled) EmeraldAccent else MaterialTheme.colorScheme.primary,
                        size = 44.dp,
                        iconSize = 21.dp,
                        cornerRadius = 14.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Prevent Uninstallation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Require PIN or biometric before uninstalling Arwora or locked apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    ArworaAnimatedSwitch(
                        checked = isUninstallProtectionEnabled,
                        onCheckedChange = onToggleUninstallProtection,
                        modifier = Modifier.testTag("toggle_uninstall_protection")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GuardStatusPill(
                        label = if (isAccessibilityGuardEnabled) "Accessibility Guard: Active" else "Accessibility Guard: Off",
                        active = isAccessibilityGuardEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isAccessibilityGuardEnabled) {
                        OutlinedButton(
                            onClick = onOpenAccessibilitySettings,
                            shape = ArworaDesignTokens.PillShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .testTag("enable_uninstall_accessibility_btn")
                        ) {
                            Text("Enable Guard", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GuardStatusPill(
                        label = if (isDeviceAdminEnabled) "Device Admin Lock: Active" else "Device Admin Lock: Off",
                        active = isDeviceAdminEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isDeviceAdminEnabled) {
                        OutlinedButton(
                            onClick = onRequestDeviceAdmin,
                            shape = ArworaDesignTokens.PillShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .testTag("enable_device_admin_btn")
                        ) {
                            Text("Enable Admin", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Block New App Installations
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("advanced_install_block_card"),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = cardBorder,
            elevation = elevation
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.Block,
                        tint = if (isInstallBlockEnabled) EmeraldAccent else MaterialTheme.colorScheme.primary,
                        size = 44.dp,
                        iconSize = 21.dp,
                        cornerRadius = 14.dp
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Block New App Installations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Lock Play Store & APK sideload installs until PIN or biometric auth",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    ArworaAnimatedSwitch(
                        checked = isInstallBlockEnabled,
                        onCheckedChange = onToggleInstallBlock,
                        modifier = Modifier.testTag("toggle_install_block")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GuardStatusPill(
                        label = if (isAccessibilityGuardEnabled) {
                            "Installer Interceptor: Ready"
                        } else {
                            "Package Monitor Active • Enable Guard for Pre-Install Lock"
                        },
                        active = isAccessibilityGuardEnabled || isInstallBlockEnabled,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isAccessibilityGuardEnabled) {
                        OutlinedButton(
                            onClick = onOpenAccessibilitySettings,
                            shape = ArworaDesignTokens.PillShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 38.dp)
                                .testTag("enable_install_accessibility_btn")
                        ) {
                            Text("Enable Guard", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppsTabHiddenVaultBanner(
    cardShape: Shape,
    cardBorder: BorderStroke,
    cardBgColor: Color,
    hiddenCount: Int,
    secretCode: String,
    onOpenVault: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("apps_tab_hidden_vault_card"),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArworaTonalIconBadge(
                icon = Icons.Default.VisibilityOff,
                tint = MaterialTheme.colorScheme.primary,
                size = 42.dp,
                iconSize = 20.dp,
                cornerRadius = 13.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Hidden Apps Vault",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$hiddenCount hidden • Dial *#*#$secretCode#*#*",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onOpenVault,
                shape = ArworaDesignTokens.PillShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .testTag("apps_tab_open_vault_btn")
            ) {
                Text(
                    text = "Open Vault",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HiddenAppsVaultSheet(
    installedApps: List<AppItem>,
    hiddenPackages: Set<String>,
    privilegeMode: HiderPrivilegeMode,
    isSelfStealthEnabled: Boolean,
    secretDialerCode: String,
    onDismiss: () -> Unit,
    onToggleSelfStealth: (Boolean) -> Unit,
    onHideApp: (String) -> Unit,
    onUnhideApp: (String) -> Unit,
    onLaunchHiddenApp: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var vaultQuery by remember { mutableStateOf("") }

    val cardShape = ArworaDesignTokens.CardShape
    val cardBorder = ArworaDesignTokens.cardBorder()
    val cardBgColor = ArworaDesignTokens.cardContainerColor()

    val hiddenItems = remember(installedApps, hiddenPackages) {
        val byPkg = installedApps.associateBy { it.packageName }
        hiddenPackages.sorted().map { pkg ->
            byPkg[pkg] ?: AppItem(
                appName = pkg,
                packageName = pkg,
                versionName = "",
                firstInstallTime = 0L,
                firstInstallTimeFormatted = "",
                dayFormattedDate = "",
                relativeTimeAgo = "",
                isSystemApp = false,
                icon = null
            )
        }
    }

    val visibleAppsToHide = remember(installedApps, hiddenPackages, vaultQuery) {
        val q = vaultQuery.trim().lowercase()
        installedApps.filter { app ->
            !AppLockerManager.isSelfPackageName(app.packageName) &&
                !hiddenPackages.contains(app.packageName) &&
                (q.isEmpty() || app.appName.lowercase().contains(q) || app.packageName.lowercase().contains(q))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("hidden_apps_vault_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp, max = 640.dp)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArworaTonalIconBadge(
                        icon = Icons.Default.Security,
                        tint = EmeraldAccent,
                        size = 44.dp,
                        iconSize = 22.dp,
                        cornerRadius = 14.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hidden Apps Vault",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = privilegeMode.displayLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (privilegeMode == HiderPrivilegeMode.VAULT_ONLY) AmberAccent else EmeraldAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_hidden_vault_btn")) {
                        Icon(Icons.Default.Close, contentDescription = "Close Vault")
                    }
                }
            }

            // Self-Stealth Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("self_stealth_card"),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = cardBorder
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dialpad,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hide Arwora Icon (Self-Stealth)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Hide from launcher & recent list. Dial *#*#$secretDialerCode#*#* to open Vault.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            ArworaAnimatedSwitch(
                                checked = isSelfStealthEnabled,
                                onCheckedChange = onToggleSelfStealth,
                                modifier = Modifier.testTag("toggle_self_stealth")
                            )
                        }
                        if (privilegeMode == HiderPrivilegeMode.VAULT_ONLY) {
                            GuardStatusPill(
                                label = "Activate Shizuku, Wireless ADB, or Device Owner to hide third-party apps from system launcher.",
                                active = false
                            )
                        }
                    }
                }
            }

            // Currently Hidden Apps
            item {
                Text(
                    text = "Hidden Apps (${hiddenItems.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (hiddenItems.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = cardShape,
                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                        border = cardBorder
                    ) {
                        Text(
                            text = "No apps hidden yet. Select an app below to move it into your stealth vault.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(hiddenItems, key = { "hidden_${it.packageName}" }) { app ->
                    VaultAppRowCard(
                        app = app,
                        cardShape = cardShape,
                        cardBorder = cardBorder,
                        cardBgColor = cardBgColor,
                        isHidden = true,
                        onPrimaryAction = { onUnhideApp(app.packageName) },
                        onLaunchAction = { onLaunchHiddenApp(app.packageName) }
                    )
                }
            }

            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hide Installed Apps",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                OutlinedTextField(
                    value = vaultQuery,
                    onValueChange = { vaultQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_search_input"),
                    placeholder = { Text("Search apps to hide...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (vaultQuery.isNotEmpty()) {
                            IconButton(onClick = { vaultQuery = "" }) {
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

            items(visibleAppsToHide, key = { "visible_${it.packageName}" }) { app ->
                VaultAppRowCard(
                    app = app,
                    cardShape = cardShape,
                    cardBorder = cardBorder,
                    cardBgColor = cardBgColor,
                    isHidden = false,
                    onPrimaryAction = { onHideApp(app.packageName) },
                    onLaunchAction = null
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun VaultAppRowCard(
    app: AppItem,
    cardShape: Shape,
    cardBorder: BorderStroke,
    cardBgColor: Color,
    isHidden: Boolean,
    onPrimaryAction: () -> Unit,
    onLaunchAction: (() -> Unit)?
) {
    val bitmap = remember(app.icon) { app.icon?.toBitmap() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_app_item_${app.packageName}"),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                ArworaTonalIconBadge(
                    icon = Icons.Default.Apps,
                    size = 40.dp,
                    iconSize = 19.dp,
                    cornerRadius = 12.dp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isHidden && onLaunchAction != null) {
                IconButton(
                    onClick = onLaunchAction,
                    modifier = Modifier.testTag("vault_launch_${app.packageName}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = "Launch Privately",
                        tint = EmeraldAccent
                    )
                }
            }

            OutlinedButton(
                onClick = onPrimaryAction,
                shape = ArworaDesignTokens.PillShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .heightIn(min = 38.dp)
                    .testTag(if (isHidden) "vault_unhide_${app.packageName}" else "vault_hide_${app.packageName}")
            ) {
                Icon(
                    imageVector = if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isHidden) "Unhide" else "Hide",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun GuardStatusPill(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val tint = if (active) EmeraldAccent else AmberAccent
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
