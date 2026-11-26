package com.arwora.feature.fakechat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.ui.components.AppCard
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaPrimaryPillButton
import com.arwora.utils.SoundEffectManager
import kotlin.math.roundToInt

private fun iconForPlatform(platform: ChatPlatform): ImageVector = when (platform) {
    ChatPlatform.WHATSAPP -> Icons.Default.Chat
    ChatPlatform.INSTAGRAM -> Icons.Default.CameraAlt
    ChatPlatform.PINTEREST -> Icons.Default.Image
    ChatPlatform.MESSENGER -> Icons.Default.Message
    ChatPlatform.IMO -> Icons.Default.Call
    ChatPlatform.TIKTOK -> Icons.Default.PlayArrow
    ChatPlatform.TWITTER_X -> Icons.Default.Share
    ChatPlatform.SNAPCHAT -> Icons.Default.CameraAlt
}

@Composable
fun PlatformSelectorGridCard(
    selectedPlatform: ChatPlatform,
    onSelectPlatform: (ChatPlatform) -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Platform Interface",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select an authentic app theme engine",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ArworaPillBadge(
                    text = selectedPlatform.displayName,
                    tint = selectedPlatform.accentColor,
                    showDot = true
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val rows = ChatPlatform.entries.chunked(4)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { platform ->
                            val isSelected = selectedPlatform == platform
                            val borderStroke = if (isSelected) {
                                BorderStroke(1.5.dp, platform.accentColor)
                            } else {
                                ArworaDesignTokens.innerTileBorder()
                            }
                            val tileBg = if (isSelected) {
                                platform.accentColor.copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            }

                            Surface(
                                shape = ArworaDesignTokens.InnerTileShape,
                                color = tileBg,
                                border = borderStroke,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 68.dp)
                                    .clip(ArworaDesignTokens.InnerTileShape)
                                    .testTag("platform_tile_${platform.name.lowercase()}")
                                    .clickable {
                                        SoundEffectManager.playClick()
                                        onSelectPlatform(platform)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(platform.accentColor.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = iconForPlatform(platform),
                                            contentDescription = platform.displayName,
                                            tint = if (platform == ChatPlatform.SNAPCHAT) {
                                                Color(0xFFF59E0B)
                                            } else {
                                                platform.accentColor
                                            },
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = platform.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
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

@Composable
fun PlatformAppearanceOptionsCard(
    platform: ChatPlatform,
    isDarkPreview: Boolean,
    onDarkPreviewChange: (Boolean) -> Unit,
    showWhatsAppDoodle: Boolean,
    onShowWhatsAppDoodleChange: (Boolean) -> Unit,
    defaultWhatsAppReadStatus: WhatsAppReadStatus,
    onDefaultWhatsAppReadStatusChange: (WhatsAppReadStatus) -> Unit,
    isVerifiedBadge: Boolean,
    onVerifiedBadgeChange: (Boolean) -> Unit,
    isBusinessChat: Boolean,
    onBusinessChatChange: (Boolean) -> Unit,
    igOutgoingPalette: InstagramOutgoingPalette,
    onIgOutgoingPaletteChange: (InstagramOutgoingPalette) -> Unit,
    showIgSeenIndicator: Boolean,
    onShowIgSeenIndicatorChange: (Boolean) -> Unit,
    showPinterestProfileCard: Boolean,
    onShowPinterestProfileCardChange: (Boolean) -> Unit,
    pinterestOutgoingStyle: PinterestOutgoingStyle,
    onPinterestOutgoingStyleChange: (PinterestOutgoingStyle) -> Unit
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "${platform.displayName} Theme Options",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // Preview Light / Dark Mode Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dark Mode Preview",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Toggle authentic light/dark chat colors",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isDarkPreview,
                    onCheckedChange = {
                        SoundEffectManager.playClick()
                        onDarkPreviewChange(it)
                    },
                    modifier = Modifier.testTag("fake_chat_dark_preview_switch")
                )
            }

            when (platform) {
                ChatPlatform.WHATSAPP -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Classic Doodle Wallpaper",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Vector pattern behind message bubbles",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showWhatsAppDoodle,
                            onCheckedChange = {
                                SoundEffectManager.playClick()
                                onShowWhatsAppDoodleChange(it)
                            }
                        )
                    }

                    Text(
                        text = "Outgoing Tick Status",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WhatsAppReadStatus.entries.forEach { status ->
                            FilterChip(
                                selected = defaultWhatsAppReadStatus == status,
                                onClick = {
                                    SoundEffectManager.playClick()
                                    onDefaultWhatsAppReadStatusChange(status)
                                },
                                label = { Text(status.label, fontSize = 11.5.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                ChatPlatform.INSTAGRAM -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Verified Account Badge",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = isVerifiedBadge,
                            onCheckedChange = {
                                SoundEffectManager.playClick()
                                onVerifiedBadgeChange(it)
                            },
                            modifier = Modifier.testTag("ig_verified_switch")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Business Chat Subtext",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = isBusinessChat,
                            onCheckedChange = {
                                SoundEffectManager.playClick()
                                onBusinessChatChange(it)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show \"Seen\" Status",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = showIgSeenIndicator,
                            onCheckedChange = {
                                SoundEffectManager.playClick()
                                onShowIgSeenIndicatorChange(it)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InstagramOutgoingPalette.entries.forEach { palette ->
                            FilterChip(
                                selected = igOutgoingPalette == palette,
                                onClick = {
                                    SoundEffectManager.playClick()
                                    onIgOutgoingPaletteChange(palette)
                                },
                                label = { Text(palette.label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                ChatPlatform.PINTEREST -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Intro Profile Card",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Displays Follow CTA & intro subtitle",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showPinterestProfileCard,
                            onCheckedChange = {
                                SoundEffectManager.playClick()
                                onShowPinterestProfileCardChange(it)
                            }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PinterestOutgoingStyle.entries.forEach { style ->
                            FilterChip(
                                selected = pinterestOutgoingStyle == style,
                                onClick = {
                                    SoundEffectManager.playClick()
                                    onPinterestOutgoingStyleChange(style)
                                },
                                label = { Text(style.label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
fun AdvancedMessageComposerCard(
    messageType: ChatMessageType,
    onMessageTypeChange: (ChatMessageType) -> Unit,
    newMessageText: String,
    onNewMessageTextChange: (String) -> Unit,
    isNewOutgoing: Boolean,
    onIsNewOutgoingChange: (Boolean) -> Unit,
    newMessageTime: String,
    onNewMessageTimeChange: (String) -> Unit,
    voiceDurationSeconds: Int,
    onVoiceDurationSecondsChange: (Int) -> Unit,
    callStatus: CallStatus,
    onCallStatusChange: (CallStatus) -> Unit,
    callDurationText: String,
    onCallDurationTextChange: (String) -> Unit,
    includeQuotedReply: Boolean,
    onIncludeQuotedReplyChange: (Boolean) -> Unit,
    replyToAuthor: String,
    onReplyToAuthorChange: (String) -> Unit,
    replyToSnippet: String,
    onReplyToSnippetChange: (String) -> Unit,
    mediaUri: Uri?,
    onMediaUriChange: (Uri?) -> Unit,
    mediaTitle: String,
    onMediaTitleChange: (String) -> Unit,
    isVideoMedia: Boolean,
    onIsVideoMediaChange: (Boolean) -> Unit,
    includeHeartReaction: Boolean,
    onIncludeHeartReactionChange: (Boolean) -> Unit,
    onAddMessage: () -> Unit
) {
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onMediaUriChange(uri)
        }
    }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Message Type Builder",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // Message Type Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChatMessageType.entries.forEach { type ->
                    val icon = when (type) {
                        ChatMessageType.TEXT -> Icons.Default.TextFields
                        ChatMessageType.VOICE_NOTE -> Icons.Default.Mic
                        ChatMessageType.VOICE_CALL -> Icons.Default.Call
                        ChatMessageType.DELETED -> Icons.Default.Block
                        ChatMessageType.MEDIA_ATTACHMENT -> Icons.Default.Videocam
                    }
                    FilterChip(
                        selected = messageType == type,
                        onClick = {
                            SoundEffectManager.playClick()
                            onMessageTypeChange(type)
                        },
                        leadingIcon = {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        label = { Text(type.label, fontSize = 12.sp) },
                        modifier = Modifier.testTag("message_type_chip_${type.name.lowercase()}")
                    )
                }
            }

            // Direction + Time Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isNewOutgoing,
                        onClick = { onIsNewOutgoingChange(false) },
                        label = { Text("Incoming") }
                    )
                    FilterChip(
                        selected = isNewOutgoing,
                        onClick = { onIsNewOutgoingChange(true) },
                        label = { Text("Outgoing") }
                    )
                }

                OutlinedTextField(
                    value = newMessageTime,
                    onValueChange = onNewMessageTimeChange,
                    label = { Text("Time") },
                    singleLine = true,
                    modifier = Modifier.width(112.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Dynamic Type-Specific Inputs
            when (messageType) {
                ChatMessageType.TEXT -> {
                    OutlinedTextField(
                        value = newMessageText,
                        onValueChange = onNewMessageTextChange,
                        label = { Text("Message Text") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("composer_message_text_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add \"You replied to...\" quote",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Switch(
                            checked = includeQuotedReply,
                            onCheckedChange = onIncludeQuotedReplyChange
                        )
                    }

                    if (includeQuotedReply) {
                        OutlinedTextField(
                            value = replyToAuthor,
                            onValueChange = onReplyToAuthorChange,
                            label = { Text("Original Sender") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = replyToSnippet,
                            onValueChange = onReplyToSnippetChange,
                            label = { Text("Quoted Snippet") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                ChatMessageType.VOICE_NOTE -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ArworaDesignTokens.InnerTileShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .border(ArworaDesignTokens.innerTileBorder(), ArworaDesignTokens.InnerTileShape)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Voice Note Duration",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            ArworaPillBadge(text = formatVoiceDuration(voiceDurationSeconds))
                        }
                        Slider(
                            value = voiceDurationSeconds.toFloat(),
                            onValueChange = { onVoiceDurationSecondsChange(it.roundToInt()) },
                            valueRange = 5f..120f,
                            modifier = Modifier.testTag("voice_duration_slider")
                        )
                    }
                }
                ChatMessageType.VOICE_CALL -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CallStatus.entries.forEach { status ->
                            FilterChip(
                                selected = callStatus == status,
                                onClick = { onCallStatusChange(status) },
                                label = { Text("${status.label} Call") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (callStatus == CallStatus.COMPLETED) {
                        OutlinedTextField(
                            value = callDurationText,
                            onValueChange = onCallDurationTextChange,
                            label = { Text("Call Duration (e.g. 04:12)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
                ChatMessageType.DELETED -> {
                    OutlinedTextField(
                        value = newMessageText,
                        onValueChange = onNewMessageTextChange,
                        label = { Text("Deleted Notice Text") },
                        placeholder = { Text("You deleted a message") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                ChatMessageType.MEDIA_ATTACHMENT -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (mediaUri != null) "Change Media" else "Pick Thumbnail",
                                maxLines = 1
                            )
                        }
                        FilterChip(
                            selected = isVideoMedia,
                            onClick = { onIsVideoMediaChange(!isVideoMedia) },
                            leadingIcon = {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            label = { Text("Video Overlay") }
                        )
                    }
                    OutlinedTextField(
                        value = mediaTitle,
                        onValueChange = onMediaTitleChange,
                        label = { Text("Post / Attachment Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = newMessageText,
                        onValueChange = onNewMessageTextChange,
                        label = { Text("Caption (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            if (messageType != ChatMessageType.DELETED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFE1306C),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Attach Heart Reaction",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = includeHeartReaction,
                        onCheckedChange = onIncludeHeartReactionChange
                    )
                }
            }

            ArworaPrimaryPillButton(
                text = "Add Message",
                onClick = onAddMessage,
                height = 48.dp,
                modifier = Modifier.testTag("add_message_button")
            )
        }
    }
}
