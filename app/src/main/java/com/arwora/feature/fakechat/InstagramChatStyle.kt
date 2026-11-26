package com.arwora.feature.fakechat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val IgIncomingLight = Color(0xFFEFEFEF)
private val IgIncomingDark = Color(0xFF262626)
private val IgSolidIndigo = Color(0xFF3797EF)
private val IgVerifiedBlue = Color(0xFF0095F6)

@Composable
fun InstagramChatPreview(
    config: FakeChatConfig,
    messages: List<FakeChatMessage>,
    modifier: Modifier = Modifier
) {
    val isDark = config.isDarkPreview
    val bgColor = if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color(0xFFF5F5F5) else Color(0xFF111111)
    val textSecondary = if (isDark) Color(0xFFA8A8A8) else Color(0xFF737373)
    val incomingBg = if (isDark) IgIncomingDark else IgIncomingLight
    val composerBg = if (isDark) Color(0xFF262626) else Color(0xFFEFEFEF)

    val igGradientBrush = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF833AB4),
                Color(0xFFC13584),
                Color(0xFFE1306C)
            )
        )
    }
    val igSolidBrush = remember {
        Brush.linearGradient(listOf(IgSolidIndigo, IgSolidIndigo))
    }
    val outgoingBrush = if (config.igOutgoingPalette == InstagramOutgoingPalette.GRADIENT) {
        igGradientBrush
    } else {
        igSolidBrush
    }

    val latestOutgoingId = remember(messages) {
        messages.lastOrNull { it.isOutgoing && it.messageType != ChatMessageType.DELETED }?.id
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("instagram_chat_preview"),
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 6.dp,
        color = bgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Instagram Direct Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(1.8.dp, igGradientBrush, CircleShape)
                        .padding(2.5.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF262626) else Color(0xFFEFEFEF)),
                    contentAlignment = Alignment.Center
                ) {
                    if (config.avatarUri != null) {
                        AsyncImage(
                            model = config.avatarUri,
                            contentDescription = "Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = textPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = config.senderName.ifBlank { "sarah.studio" },
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (config.isVerifiedBadge) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = IgVerifiedBlue,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    val subline = buildString {
                        if (config.isBusinessChat) append("Business chat · ")
                        append(config.statusText.ifBlank { "Active now" })
                    }
                    Text(
                        text = subline,
                        fontSize = 11.5.sp,
                        color = textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Audio call",
                    tint = textPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video call",
                    tint = textPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera",
                    tint = textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            HorizontalDivider(color = textSecondary.copy(alpha = 0.14f))

            // Messages Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                messages.forEach { msg ->
                    val showSeen = config.showIgSeenIndicator && msg.id == latestOutgoingId
                    InstagramMessageItem(
                        message = msg,
                        config = config,
                        incomingBg = incomingBg,
                        outgoingBrush = outgoingBrush,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        isDark = isDark,
                        showSeen = showSeen
                    )
                }
            }

            // Instagram Direct Bottom Composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = composerBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(IgSolidIndigo),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Message...",
                            fontSize = 14.sp,
                            color = textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice note",
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Gallery",
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Default.Mood,
                            contentDescription = "Stickers",
                            tint = textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstagramMessageItem(
    message: FakeChatMessage,
    config: FakeChatConfig,
    incomingBg: Color,
    outgoingBrush: Brush,
    textPrimary: Color,
    textSecondary: Color,
    isDark: Boolean,
    showSeen: Boolean
) {
    val hasReply = message.replyToSnippet.isNotBlank() || message.replyToAuthor.isNotBlank()
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    ) {
        if (hasReply) {
            Column(
                modifier = Modifier
                    .padding(
                        start = if (message.isOutgoing) 48.dp else 32.dp,
                        end = if (message.isOutgoing) 4.dp else 48.dp,
                        bottom = 4.dp
                    ),
                horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
            ) {
                Text(
                    text = if (message.isOutgoing) {
                        "You replied to ${message.replyToAuthor.ifBlank { config.senderName }}"
                    } else {
                        "${config.senderName} replied to you"
                    },
                    fontSize = 11.sp,
                    color = textSecondary
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!message.isOutgoing) {
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(textSecondary.copy(alpha = 0.45f))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = incomingBg.copy(alpha = 0.72f)
                    ) {
                        Text(
                            text = message.replyToSnippet.ifBlank { "Original message..." },
                            fontSize = 12.sp,
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    if (message.isOutgoing) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(textSecondary.copy(alpha = 0.45f))
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!message.isOutgoing) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(incomingBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (config.avatarUri != null) {
                        AsyncImage(
                            model = config.avatarUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box {
                val bubbleShape = RoundedCornerShape(20.dp)
                val contentTextColor = if (message.isOutgoing) Color.White else textPrimary

                val bubbleModifier = if (message.messageType == ChatMessageType.DELETED) {
                    Modifier
                        .widthIn(max = 264.dp)
                        .clip(bubbleShape)
                        .border(1.dp, textSecondary.copy(alpha = 0.35f), bubbleShape)
                        .background(Color.Transparent)
                } else if (message.isOutgoing) {
                    Modifier
                        .widthIn(max = 264.dp)
                        .clip(bubbleShape)
                        .background(outgoingBrush)
                } else {
                    Modifier
                        .widthIn(max = 264.dp)
                        .clip(bubbleShape)
                        .background(incomingBg)
                }

                Box(modifier = bubbleModifier) {
                    when (message.messageType) {
                        ChatMessageType.TEXT -> {
                            Text(
                                text = message.text.ifBlank { "Hey!" },
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                                color = contentTextColor,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                        ChatMessageType.VOICE_NOTE -> {
                            InstagramVoiceNoteBody(
                                durationSeconds = message.voiceDurationSeconds,
                                contentColor = contentTextColor
                            )
                        }
                        ChatMessageType.VOICE_CALL -> {
                            InstagramCallBody(
                                callStatus = message.callStatus,
                                durationText = message.callDurationText,
                                contentColor = contentTextColor
                            )
                        }
                        ChatMessageType.DELETED -> {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = "Unsent",
                                    tint = textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = message.text.ifBlank { "You deleted a message" },
                                    fontSize = 13.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = textSecondary
                                )
                            }
                        }
                        ChatMessageType.MEDIA_ATTACHMENT -> {
                            InstagramSharedPostPreview(
                                message = message,
                                senderName = config.senderName,
                                textPrimary = contentTextColor,
                                isDark = isDark
                            )
                        }
                    }
                }

                if (message.reactionEmoji.isNotBlank() && message.messageType != ChatMessageType.DELETED) {
                    Surface(
                        shape = CircleShape,
                        color = if (isDark) Color(0xFF262626) else Color.White,
                        shadowElevation = 2.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isDark) Color.Black else Color(0xFFE5E5E5)
                        ),
                        modifier = Modifier
                            .align(if (message.isOutgoing) Alignment.BottomStart else Alignment.BottomEnd)
                            .offset(y = 10.dp)
                    ) {
                        Text(
                            text = message.reactionEmoji,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        if (message.reactionEmoji.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (showSeen) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Seen",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = textSecondary,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

@Composable
private fun InstagramVoiceNoteBody(
    durationSeconds: Int,
    contentColor: Color
) {
    val bars = remember { listOf(8, 16, 11, 20, 14, 22, 9, 17, 19, 12, 15, 8, 18, 13) }
    Row(
        modifier = Modifier
            .widthIn(min = 188.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(contentColor.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play voice note",
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            modifier = Modifier.weight(1f)
        ) {
            bars.forEach { h ->
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(h.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(contentColor.copy(alpha = 0.88f))
                )
            }
        }
        Text(
            text = formatVoiceDuration(durationSeconds),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@Composable
private fun InstagramCallBody(
    callStatus: CallStatus,
    durationText: String,
    contentColor: Color
) {
    val isMissed = callStatus == CallStatus.MISSED
    Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (isMissed) Color(0xFFEF4444).copy(alpha = 0.22f)
                    else contentColor.copy(alpha = 0.2f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isMissed) Icons.Default.CallMissed else Icons.Default.CallMade,
                contentDescription = callStatus.label,
                tint = if (isMissed) Color(0xFFFF6B6B) else contentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Column {
            Text(
                text = if (isMissed) "Missed audio call" else "Audio call ended",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
            Text(
                text = if (isMissed) "Tap to call back" else durationText.ifBlank { "04:12" },
                fontSize = 11.sp,
                color = contentColor.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun InstagramSharedPostPreview(
    message: FakeChatMessage,
    senderName: String,
    textPrimary: Color,
    isDark: Boolean
) {
    Column(modifier = Modifier.width(224.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(textPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = textPrimary,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = senderName.ifBlank { "creator.reels" },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF405DE6),
                            Color(0xFF833AB4),
                            Color(0xFFE1306C)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (message.mediaUri != null) {
                AsyncImage(
                    model = message.mediaUri,
                    contentDescription = message.mediaTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(34.dp)
                )
            }
            if (message.isVideoMedia) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.56f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDark) Color.Black.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.04f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.mediaTitle.ifBlank { "Shared Reel" },
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (message.text.isNotBlank()) {
                Text(
                    text = message.text,
                    fontSize = 12.sp,
                    color = textPrimary.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
