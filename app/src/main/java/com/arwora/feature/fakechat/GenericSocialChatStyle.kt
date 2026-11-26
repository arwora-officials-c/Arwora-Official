package com.arwora.feature.fakechat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun GenericSocialChatPreview(
    config: FakeChatConfig,
    messages: List<FakeChatMessage>,
    modifier: Modifier = Modifier
) {
    val platform = config.platform
    val isDark = config.isDarkPreview

    val headerBgColor = when (platform) {
        ChatPlatform.MESSENGER -> Color(0xFF0084FF)
        ChatPlatform.IMO -> Color(0xFF0091EA)
        ChatPlatform.TIKTOK -> Color(0xFF111111)
        ChatPlatform.TWITTER_X -> Color(0xFF0F1419)
        ChatPlatform.SNAPCHAT -> Color(0xFFFFFC00)
        else -> platform.accentColor
    }
    val headerTextColor = if (platform == ChatPlatform.SNAPCHAT) Color.Black else Color.White

    val chatBgColor = when (platform) {
        ChatPlatform.TWITTER_X -> if (isDark) Color(0xFF15202B) else Color(0xFFF7F9F9)
        ChatPlatform.TIKTOK -> if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
        else -> if (isDark) Color(0xFF121417) else Color(0xFFF8F9FA)
    }

    val outgoingBubbleColor = when (platform) {
        ChatPlatform.MESSENGER -> Color(0xFF0084FF)
        ChatPlatform.IMO -> Color(0xFF0091EA)
        ChatPlatform.TIKTOK -> Color(0xFFFE2C55)
        ChatPlatform.TWITTER_X -> Color(0xFF1D9BF0)
        ChatPlatform.SNAPCHAT -> Color(0xFF0FADFF)
        else -> platform.accentColor
    }
    val outgoingTextColor = Color.White

    val incomingBubbleColor = if (isDark) Color(0xFF252A30) else Color(0xFFE9ECEF)
    val incomingTextColor = if (isDark) Color(0xFFF1F3F5) else Color(0xFF111827)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("generic_chat_preview_${platform.name.lowercase()}"),
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 6.dp,
        color = chatBgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBgColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = headerTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(headerTextColor.copy(alpha = 0.18f)),
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
                            tint = headerTextColor
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.senderName.ifBlank { "Sarah Jenkins" },
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = headerTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = config.statusText.ifBlank { platform.defaultStatus },
                        fontSize = 11.5.sp,
                        color = headerTextColor.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Voice call",
                    tint = headerTextColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video call",
                    tint = headerTextColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                messages.forEach { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isOutgoing) Arrangement.End else Arrangement.Start
                    ) {
                        val bubbleColor = if (msg.isOutgoing) outgoingBubbleColor else incomingBubbleColor
                        val textColor = if (msg.isOutgoing) outgoingTextColor else incomingTextColor

                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (msg.isOutgoing) 16.dp else 4.dp,
                                bottomEnd = if (msg.isOutgoing) 4.dp else 16.dp
                            ),
                            color = bubbleColor,
                            shadowElevation = 1.dp,
                            modifier = Modifier.widthIn(max = 264.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                when (msg.messageType) {
                                    ChatMessageType.TEXT -> {
                                        Text(
                                            text = msg.text.ifBlank { "Hello!" },
                                            fontSize = 14.sp,
                                            color = textColor
                                        )
                                    }
                                    ChatMessageType.VOICE_NOTE -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Mic,
                                                contentDescription = "Voice note",
                                                tint = textColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = "Voice note · ${formatVoiceDuration(msg.voiceDurationSeconds)}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = textColor
                                            )
                                        }
                                    }
                                    ChatMessageType.VOICE_CALL -> {
                                        val isMissed = msg.callStatus == CallStatus.MISSED
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isMissed) Icons.Default.CallMissed else Icons.Default.CallMade,
                                                contentDescription = msg.callStatus.label,
                                                tint = if (isMissed) Color(0xFFFF6B6B) else textColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = if (isMissed) "Missed voice call" else "Voice call · ${msg.callDurationText}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = textColor
                                            )
                                        }
                                    }
                                    ChatMessageType.DELETED -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Block,
                                                contentDescription = "Deleted",
                                                tint = textColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = msg.text.ifBlank { "You deleted a message" },
                                                fontSize = 13.sp,
                                                fontStyle = FontStyle.Italic,
                                                color = textColor.copy(alpha = 0.78f)
                                            )
                                        }
                                    }
                                    ChatMessageType.MEDIA_ATTACHMENT -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(126.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(headerBgColor.copy(alpha = 0.8f), Color(0xFF1F2937))
                                                        )
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (msg.mediaUri != null) {
                                                    AsyncImage(
                                                        model = msg.mediaUri,
                                                        contentDescription = msg.mediaTitle,
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.Image,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                                if (msg.isVideoMedia) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Play video",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }
                                            }
                                            if (msg.text.isNotBlank()) {
                                                Text(
                                                    text = msg.text,
                                                    fontSize = 13.sp,
                                                    color = textColor
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(
                                    modifier = Modifier.align(Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = msg.time,
                                        fontSize = 10.sp,
                                        color = textColor.copy(alpha = 0.65f)
                                    )
                                    if (msg.isOutgoing && msg.messageType != ChatMessageType.DELETED) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = "Read",
                                            tint = textColor.copy(alpha = 0.78f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color(0xFF1C2026) else Color.Black.copy(alpha = 0.04f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Attach",
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isDark) Color(0xFF282D35) else Color.White,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Message...",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = outgoingBubbleColor
                )
            }
        }
    }
}
