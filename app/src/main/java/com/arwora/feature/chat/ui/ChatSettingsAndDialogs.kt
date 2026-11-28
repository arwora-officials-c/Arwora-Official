package com.arwora.feature.chat.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.util.Base64
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.arwora.feature.chat.model.ChatHubSettings
import com.arwora.feature.chat.model.ChatMediaFilterTab
import com.arwora.feature.chat.model.ChatMessage
import com.arwora.feature.chat.model.ChatMessageType
import com.arwora.feature.chat.model.ChatThemePreset
import com.arwora.feature.chat.model.ChatThread
import com.arwora.feature.chat.model.ChatWallpaperPreset
import com.arwora.feature.system.connect.data.ConnectNetworkUtils
import com.arwora.feature.system.connect.model.P2pMessageRetentionPolicy
import com.arwora.feature.system.connect.ui.ArworaConnectSettingsPanel
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ChatHubSettingsDialog(
    settings: ChatHubSettings,
    retentionPolicy: P2pMessageRetentionPolicy,
    lastRetentionDuration: P2pMessageRetentionPolicy,
    isSqlCipherActive: Boolean,
    isBatteryOrDataSaverActive: Boolean,
    isDndActiveNow: Boolean,
    onUpdateSettings: ((ChatHubSettings) -> ChatHubSettings) -> Unit,
    onSelectRetentionPolicy: (P2pMessageRetentionPolicy) -> Unit,
    onOpenVaultSecurity: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val ringtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val pickedUri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val title = if (pickedUri != null) {
                runCatching { RingtoneManager.getRingtone(context, pickedUri)?.getTitle(context) }.getOrNull()
                    ?: "Custom Tone"
            } else {
                "Silent"
            }
            onUpdateSettings {
                it.copy(defaultSoundUri = pickedUri?.toString(), defaultSoundName = title)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
                .heightIn(max = 680.dp)
                .testTag("chat_hub_settings_dialog"),
            shape = ArworaDesignTokens.CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = ArworaDesignTokens.cardBorder(),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Chat Hub Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Privacy, DND schedule, bandwidth & auto-delete",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ArworaPillBadge(
                        text = if (isSqlCipherActive) "SQLCipher AES-256" else "Encrypted Keystore",
                        tint = Color(0xFF10B981),
                        showDot = true,
                        modifier = Modifier.testTag("chat_setting_sqlcipher_badge")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsSwitchTile(
                        icon = Icons.Default.VisibilityOff,
                        title = "Ghost Mode",
                        subtitle = "Hide online presence, typing indicator & read receipts",
                        checked = settings.ghostModeEnabled,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(ghostModeEnabled = v) } },
                        testTag = "chat_setting_ghost_switch"
                    )

                    SettingsSwitchTile(
                        icon = Icons.Default.ScreenshotMonitor,
                        title = "Screenshot Detection Alerts",
                        subtitle = "Log & notify peers when screenshots are captured",
                        checked = settings.screenshotAlertsEnabled,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(screenshotAlertsEnabled = v) } },
                        testTag = "chat_setting_screenshot_switch"
                    )

                    SettingsSwitchTile(
                        icon = Icons.Default.Shield,
                        title = "Block Screenshots in Locked Chats",
                        subtitle = "Apply FLAG_SECURE inside locked & Vault threads",
                        checked = settings.flagSecureLockedChats,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(flagSecureLockedChats = v) } },
                        testTag = "chat_setting_flag_secure_switch"
                    )

                    OutlinedButton(
                        onClick = onOpenVaultSecurity,
                        shape = ArworaDesignTokens.InnerTileShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_setting_vault_pin_btn")
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Manage Vault PIN & Biometric Security", fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsSwitchTile(
                        icon = Icons.Default.Bedtime,
                        title = if (isDndActiveNow) "Night DND Schedule (Active)" else "Night DND Schedule",
                        subtitle = String.format(
                            Locale.US,
                            "Quiet alerts from %02d:%02d to %02d:%02d",
                            settings.dndStartHour,
                            settings.dndStartMinute,
                            settings.dndEndHour,
                            settings.dndEndMinute
                        ),
                        checked = settings.dndEnabled,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(dndEnabled = v) } },
                        testTag = "chat_setting_dnd_switch"
                    )

                    if (settings.dndEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            onUpdateSettings { it.copy(dndStartHour = h, dndStartMinute = m) }
                                        },
                                        settings.dndStartHour,
                                        settings.dndStartMinute,
                                        true
                                    ).show()
                                },
                                shape = ArworaDesignTokens.InnerTileShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(String.format(Locale.US, "Start %02d:%02d", settings.dndStartHour, settings.dndStartMinute))
                            }
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            onUpdateSettings { it.copy(dndEndHour = h, dndEndMinute = m) }
                                        },
                                        settings.dndEndHour,
                                        settings.dndEndMinute,
                                        true
                                    ).show()
                                },
                                shape = ArworaDesignTokens.InnerTileShape,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(String.format(Locale.US, "End %02d:%02d", settings.dndEndHour, settings.dndEndMinute))
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Default Chat Sound")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                settings.defaultSoundUri?.let {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
                                }
                            }
                            ringtonePicker.launch(intent)
                        },
                        shape = ArworaDesignTokens.InnerTileShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
                        border = ArworaDesignTokens.innerTileBorder(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("chat_setting_ringtone_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Default Notification Sound", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(settings.defaultSoundName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    SettingsSwitchTile(
                        icon = Icons.Default.DataSaverOn,
                        title = if (isBatteryOrDataSaverActive) "Data Saver Mode (Active)" else "Data Saver Mode",
                        subtitle = "Aggressively compress outgoing photos & cut payload size",
                        checked = settings.dataSaverMode,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(dataSaverMode = v) } },
                        testTag = "chat_setting_datasaver_switch"
                    )

                    SettingsSwitchTile(
                        icon = Icons.Default.BatterySaver,
                        title = "Low Battery Auto-Compress (≤20%)",
                        subtitle = "Automatically reduce image/media size on low battery",
                        checked = settings.lowBatteryAutoCompress,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(lowBatteryAutoCompress = v) } },
                        testTag = "chat_setting_lowbattery_switch"
                    )

                    SettingsSwitchTile(
                        icon = Icons.Default.NotificationsOff,
                        title = "Disable Heavy Media Auto-Download",
                        subtitle = "Pause automatic P2P video downloads on constrained links",
                        checked = settings.disableHeavyAutoDownload,
                        onCheckedChange = { v -> onUpdateSettings { it.copy(disableHeavyAutoDownload = v) } },
                        testTag = "chat_setting_disable_autodl_switch"
                    )

                    ArworaConnectSettingsPanel(
                        policy = retentionPolicy,
                        lastSelectedDuration = lastRetentionDuration,
                        onSelectPolicy = onSelectRetentionPolicy,
                        showOuterCard = true,
                        includeAutoReconnect = false
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDismiss,
                    shape = ArworaDesignTokens.PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("chat_hub_settings_done_btn")
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
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
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
fun ThreadCustomizationDialog(
    thread: ChatThread,
    onToggleMute: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleArchive: () -> Unit,
    onSetRingtone: (String?, String?) -> Unit,
    onSelectTheme: (String) -> Unit,
    onSelectWallpaper: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSelectWallpaper(uri.toString())
        }
    }
    val ringtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val pickedUri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val title = if (pickedUri != null) {
                runCatching { RingtoneManager.getRingtone(context, pickedUri)?.getTitle(context) }.getOrNull()
                    ?: "Custom VIP Tone"
            } else {
                "Silent"
            }
            onSetRingtone(pickedUri?.toString(), title)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .heightIn(max = 660.dp)
                .testTag("chat_thread_customize_dialog"),
            shape = ArworaDesignTokens.CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = ArworaDesignTokens.cardBorder(),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(thread.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Surface(
                    shape = ArworaDesignTokens.InnerTileShape,
                    color = Color(0xFF10B981).copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Security, null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                        Column {
                            Text("End-to-End Encrypted Session", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Key Fingerprint: ${thread.e2eeFingerprint.ifBlank { "EC-P256-LOCAL" }}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SettingsSwitchTile(
                    icon = Icons.Default.NotificationsOff,
                    title = "Mute Notifications",
                    subtitle = "Silence alerts from this conversation",
                    checked = thread.isMuted,
                    onCheckedChange = { onToggleMute() },
                    testTag = "thread_mute_switch"
                )
                SettingsSwitchTile(
                    icon = Icons.Default.Lock,
                    title = "Lock Chat",
                    subtitle = "Require PIN or Biometric to open",
                    checked = thread.isLocked,
                    onCheckedChange = { onToggleLock() },
                    testTag = "thread_lock_switch"
                )
                SettingsSwitchTile(
                    icon = Icons.Default.VisibilityOff,
                    title = "Hide in Vault",
                    subtitle = "Move conversation to Hidden Chat Vault",
                    checked = thread.isHidden,
                    onCheckedChange = { onToggleHidden() },
                    testTag = "thread_hide_switch"
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "VIP Sound for ${thread.title}")
                            }
                            ringtonePicker.launch(intent)
                        },
                        shape = ArworaDesignTokens.InnerTileShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(thread.customSoundName ?: "Custom Sound", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onToggleArchive,
                        shape = ArworaDesignTokens.InnerTileShape,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (thread.isArchived) "Unarchive" else "Archive")
                    }
                }

                Text("Chat Theme", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatThemePreset.presets.forEach { preset ->
                        val selected = thread.themeId == preset.id
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectTheme(preset.id) },
                            label = { Text(preset.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(preset.outgoingBubbleColor)
                                )
                            },
                            modifier = Modifier.testTag("theme_chip_${preset.id}")
                        )
                    }
                }

                Text("Chat Wallpaper", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatWallpaperPreset.presets.forEach { wp ->
                        val selected = thread.wallpaperPresetOrUri == wp.id
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(ArworaDesignTokens.InnerTileShape)
                                .background(Brush.verticalGradient(listOf(wp.startColor, wp.endColor)))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    shape = ArworaDesignTokens.InnerTileShape
                                )
                                .clickable { onSelectWallpaper(wp.id) }
                                .testTag("wallpaper_preset_${wp.id}"),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = wp.name.substringBefore(' '),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        shape = ArworaDesignTokens.InnerTileShape,
                        modifier = Modifier.height(64.dp)
                    ) {
                        Icon(Icons.Default.Image, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gallery")
                    }
                }

                Button(
                    onClick = onDismiss,
                    shape = ArworaDesignTokens.PillShape,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun InChatSearchAndMediaDialog(
    messages: List<ChatMessage>,
    searchQuery: String,
    selectedDateMillis: Long?,
    searchResults: List<ChatMessage>,
    onSearchChanged: (String, Long?) -> Unit,
    onJumpToMessage: (ChatMessage) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Search, 1=Media, 2=Links, 3=Files
    val dateFmt = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    val mediaList = remember(messages) {
        messages.filter { !it.isDeletedForEveryone && (it.type == ChatMessageType.IMAGE || it.type == ChatMessageType.VIDEO) && !it.viewOnceConsumed }
    }
    val linkList = remember(messages) {
        messages.mapNotNull { msg ->
            if (msg.isDeletedForEveryone) return@mapNotNull null
            val m = Patterns.WEB_URL.matcher(msg.text)
            if (m.find()) msg to m.group() else null
        }
    }
    val fileList = remember(messages) {
        messages.filter { !it.isDeletedForEveryone && it.type == ChatMessageType.FILE }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
                .heightIn(min = 420.dp, max = 660.dp)
                .testTag("inchat_search_dialog"),
            shape = ArworaDesignTokens.CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = ArworaDesignTokens.cardBorder(),
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Search, Media & Files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Search") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Media (${mediaList.size})") }, modifier = Modifier.testTag("inchat_tab_media"))
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Links (${linkList.size})") }, modifier = Modifier.testTag("inchat_tab_links"))
                    Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Files (${fileList.size})") }, modifier = Modifier.testTag("inchat_tab_files"))
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (selectedTab) {
                    0 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { onSearchChanged(it, selectedDateMillis) },
                                placeholder = { Text("Keyword...") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inchat_search_input")
                            )
                            OutlinedButton(
                                onClick = {
                                    val cal = Calendar.getInstance()
                                    if (selectedDateMillis != null) cal.timeInMillis = selectedDateMillis
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val chosen = Calendar.getInstance().apply {
                                                set(y, m, d, 12, 0, 0)
                                            }.timeInMillis
                                            onSearchChanged(searchQuery, chosen)
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                shape = ArworaDesignTokens.InnerTileShape,
                                modifier = Modifier.testTag("inchat_date_picker_btn")
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date")
                            }
                        }

                        if (selectedDateMillis != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            FilterChip(
                                selected = true,
                                onClick = { onSearchChanged(searchQuery, null) },
                                label = { Text("Date: ${dateFmt.format(Date(selectedDateMillis))} ×") },
                                modifier = Modifier.testTag("inchat_clear_date_chip")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(searchResults, key = { it.id }) { msg ->
                                Card(
                                    onClick = { onJumpToMessage(msg) },
                                    shape = ArworaDesignTokens.InnerTileShape,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("${msg.senderName} • ${dateFmt.format(Date(msg.timestampMillis))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        Text(msg.displaySummary(), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(mediaList, key = { it.id }) { msg ->
                                Card(
                                    onClick = { onJumpToMessage(msg) },
                                    shape = ArworaDesignTokens.InnerTileShape,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(msg.fileName ?: msg.type.name, fontWeight = FontWeight.SemiBold)
                                            Text(dateFmt.format(Date(msg.timestampMillis)), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(linkList, key = { it.first.id }) { (msg, url) ->
                                Card(
                                    onClick = {
                                        runCatching {
                                            val normalized = if (url.startsWith("http")) url else "https://$url"
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized)))
                                        }
                                    },
                                    shape = ArworaDesignTokens.InnerTileShape,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(url, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text("${msg.senderName} • ${dateFmt.format(Date(msg.timestampMillis))}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(fileList, key = { it.id }) { msg ->
                                Card(
                                    onClick = { onJumpToMessage(msg) },
                                    shape = ArworaDesignTokens.InnerTileShape,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(msg.fileName ?: "File", fontWeight = FontWeight.SemiBold)
                                            Text("${ConnectNetworkUtils.formatBytes(msg.fileSizeBytes)} • ${dateFmt.format(Date(msg.timestampMillis))}", style = MaterialTheme.typography.labelSmall)
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
}

@Composable
fun NewChatOrGroupDialog(
    onCreateDirect: (String) -> Unit,
    onCreateGroup: (String, List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var isGroup by remember { mutableStateOf(false) }
    var titleInput by remember { mutableStateOf("") }
    var membersInput by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .testTag("new_chat_dialog"),
            shape = ArworaDesignTokens.CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = ArworaDesignTokens.cardBorder(),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isGroup) "New Group Chat" else "New Chat",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isGroup,
                        onClick = { isGroup = false },
                        label = { Text("1-on-1") },
                        leadingIcon = { Icon(Icons.Default.Person, null, Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("new_chat_direct_chip")
                    )
                    FilterChip(
                        selected = isGroup,
                        onClick = { isGroup = true },
                        label = { Text("Group") },
                        leadingIcon = { Icon(Icons.Default.Group, null, Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("new_chat_group_chip")
                    )
                }
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text(if (isGroup) "Group Name" else "Contact Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_chat_title_input")
                )
                if (isGroup) {
                    OutlinedTextField(
                        value = membersInput,
                        onValueChange = { membersInput = it },
                        label = { Text("Members (comma separated)") },
                        placeholder = { Text("Alex, Sam, Jordan") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_chat_members_input")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val cleanTitle = titleInput.trim()
                            if (cleanTitle.isNotEmpty()) {
                                if (isGroup) {
                                    val members = membersInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    onCreateGroup(cleanTitle, members)
                                } else {
                                    onCreateDirect(cleanTitle)
                                }
                            }
                        },
                        enabled = titleInput.trim().isNotEmpty(),
                        shape = ArworaDesignTokens.PillShape,
                        modifier = Modifier.testTag("new_chat_confirm_btn")
                    ) {
                        Text("Start Chat", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduledMessageDialog(
    initialText: String,
    onSchedule: (text: String, triggerAtMillis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialText) }
    var targetMillis by remember { mutableLongStateOf(System.currentTimeMillis() + 5 * 60_000L) }
    val fmt = remember { SimpleDateFormat("EEE, MMM d • HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Schedule, null, tint = MaterialTheme.colorScheme.primary)
                Text("Schedule Message", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth().testTag("schedule_msg_text_input")
                )
                Text("Send At: ${fmt.format(Date(targetMillis))}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { targetMillis = System.currentTimeMillis() + 10 * 60_000L },
                        modifier = Modifier.weight(1f)
                    ) { Text("+10m") }
                    OutlinedButton(
                        onClick = { targetMillis = System.currentTimeMillis() + 60 * 60_000L },
                        modifier = Modifier.weight(1f)
                    ) { Text("+1h") }
                    OutlinedButton(
                        onClick = {
                            val cal = Calendar.getInstance().apply { timeInMillis = targetMillis }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    TimePickerDialog(
                                        context,
                                        { _, h, min ->
                                            val chosen = Calendar.getInstance().apply { set(y, m, d, h, min, 0) }
                                            targetMillis = chosen.timeInMillis
                                        },
                                        cal.get(Calendar.HOUR_OF_DAY),
                                        cal.get(Calendar.MINUTE),
                                        true
                                    ).show()
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                        modifier = Modifier.weight(1.3f)
                    ) { Text("Pick Time") }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSchedule(text.trim(), targetMillis) },
                enabled = text.trim().isNotEmpty() && targetMillis > System.currentTimeMillis(),
                modifier = Modifier.testTag("schedule_msg_confirm_btn")
            ) {
                Text("Schedule", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ForwardMessageDialog(
    threads: List<ChatThread>,
    onSelectTarget: (ChatThread) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward To...", fontWeight = FontWeight.Bold) },
        text = {
            if (threads.isEmpty()) {
                Text("No other chats available.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(threads, key = { it.id }) { thread ->
                        Card(
                            onClick = { onSelectTarget(thread) },
                            shape = ArworaDesignTokens.InnerTileShape,
                            modifier = Modifier.fillMaxWidth().testTag("forward_target_${thread.id}")
                        ) {
                            Text(
                                text = thread.title,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun MessageInfoDialog(
    message: ChatMessage,
    e2eeFingerprint: String,
    onDismiss: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message Info", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Status: ${message.deliveryStatus.label}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Sent: ${fmt.format(Date(message.timestampMillis))}", style = MaterialTheme.typography.bodySmall)
                Text("Delivered: ${message.deliveredAtMillis?.let { fmt.format(Date(it)) } ?: "Pending"}", style = MaterialTheme.typography.bodySmall)
                Text("Seen: ${message.seenAtMillis?.let { fmt.format(Date(it)) } ?: "Not yet"}", style = MaterialTheme.typography.bodySmall)
                if (message.isEdited && message.editedAtMillis != null) {
                    Text("Edited: ${fmt.format(Date(message.editedAtMillis))}", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = "E2EE Key: ${e2eeFingerprint.ifBlank { "EC-P256-LOCAL" }}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

@Composable
fun ViewOnceMediaDialog(
    message: ChatMessage,
    onCloseAndConsume: () -> Unit
) {
    val bitmap = remember(message.mediaBase64) {
        message.mediaBase64?.let { b64 ->
            runCatching {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }

    Dialog(
        onDismissRequest = onCloseAndConsume,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("view_once_media_dialog"),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    bitmap != null -> {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "View Once Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    !message.mediaPathOrUri.isNullOrBlank() -> {
                        val model: Any = if (message.mediaPathOrUri.startsWith("/")) {
                            File(message.mediaPathOrUri)
                        } else {
                            Uri.parse(message.mediaPathOrUri)
                        }
                        AsyncImage(
                            model = model,
                            contentDescription = "View Once Media",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Text(
                            text = "Media expired or wiped",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Surface(
                    shape = ArworaDesignTokens.PillShape,
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "View Once • Self-destructs when closed",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                IconButton(
                    onClick = onCloseAndConsume,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .testTag("view_once_close_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close and Erase", tint = Color.White)
                }
            }
        }
    }
}
