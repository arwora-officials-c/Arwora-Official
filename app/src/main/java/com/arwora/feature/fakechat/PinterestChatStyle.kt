package com.arwora.feature.fakechat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val PinRed = Color(0xFFE60023)
private val PinIncomingLight = Color(0xFFF1F1F1)
private val PinIncomingDark = Color(0xFF2B2B2B)
private val PinOutgoingNeutralLight = Color(0xFFE9E9E9)
private val PinOutgoingNeutralDark = Color(0xFF3A3A3A)

@Composable
fun PinterestChatPreview(
    config: FakeChatConfig,
    messages: List<FakeChatMessage>,
    onToggleFollow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = config.isDarkPreview
    val bgColor = if (isDark) Color(0xFF121212) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color(0xFFF3F3F3) else Color(0xFF111111)
    val textSecondary = if (isDark) Color(0xFFA6A6A6) else Color(0xFF6E6E6E)
    val incomingBg = if (isDark) PinIncomingDark else PinIncomingLight
    val outgoingBg = when (config.pinterestOutgoingStyle) {
        PinterestOutgoingStyle.PIN_ACCENT -> PinRed
        PinterestOutgoingStyle.NEUTRAL_GRAY -> if (isDark) PinOutgoingNeutralDark else PinOutgoingNeutralLight
    }
    val outgoingText = if (config.pinterestOutgoingStyle == PinterestOutgoingStyle.PIN_ACCENT) {
        Color.White
    } else {
        textPrimary
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pinterest_chat_preview"),
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 6.dp,
        color = bgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Minimalist Pinterest Messages Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = textPrimary,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(incomingBg),
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
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = config.senderName.ifBlank { "Sarah Jenkins" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "Settings",
                    tint = textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            HorizontalDivider(color = textSecondary.copy(alpha = 0.14f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (config.showPinterestProfileCard) {
                    PinterestProfileIntroCard(
                        config = config,
                        incomingBg = incomingBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onToggleFollow = onToggleFollow
                    )
                }

                messages.forEach { msg ->
                    PinterestMessageRow(
                        message = msg,
                        config = config,
                        incomingBg = incomingBg,
                        outgoingBg = outgoingBg,
                        textPrimary = textPrimary,
                        outgoingText = outgoingText,
                        textSecondary = textSecondary,
                        isDark = isDark
                    )
                }
            }

            // Pinterest Bottom Composer with Attachment (+) & Heart Reaction Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(incomingBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add attachment",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = bgColor,
                    border = BorderStroke(1.dp, textSecondary.copy(alpha = 0.28f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Write a message...",
                            fontSize = 13.5.sp,
                            color = textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(PinRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Heart reaction",
                        tint = PinRed,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PinterestProfileIntroCard(
    config: FakeChatConfig,
    incomingBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onToggleFollow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(incomingBg),
            contentAlignment = Alignment.Center
        ) {
            if (config.avatarUri != null) {
                AsyncImage(
                    model = config.avatarUri,
                    contentDescription = "Profile avatar",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = textPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = config.senderName.ifBlank { "Sarah Jenkins" },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
        )
        Text(
            text = config.statusText.ifBlank { "@sarah.creates" },
            fontSize = 12.sp,
            color = textSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "This could be the beginning of something good",
            fontSize = 12.5.sp,
            color = textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onToggleFollow,
            shape = RoundedCornerShape(50),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (config.isPinterestFollowing) incomingBg else PinRed,
                contentColor = if (config.isPinterestFollowing) textPrimary else Color.White
            ),
            modifier = Modifier
                .height(36.dp)
                .testTag("pinterest_follow_button")
        ) {
            Text(
                text = if (config.isPinterestFollowing) "Following" else "Follow",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun PinterestMessageRow(
    message: FakeChatMessage,
    config: FakeChatConfig,
    incomingBg: Color,
    outgoingBg: Color,
    textPrimary: Color,
    outgoingText: Color,
    textSecondary: Color,
    isDark: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    ) {
        if (message.messageType == ChatMessageType.DELETED) {
            PinterestDeletedCapsule(
                text = message.text.ifBlank { "You deleted a message" },
                textSecondary = textSecondary
            )
            return
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            if (!message.isOutgoing) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
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

            val bubbleBg = if (message.isOutgoing) outgoingBg else incomingBg
            val bubbleTextColor = if (message.isOutgoing) outgoingText else textPrimary

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = bubbleBg,
                modifier = Modifier.widthIn(max = 264.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    when (message.messageType) {
                        ChatMessageType.TEXT -> {
                            Text(
                                text = message.text.ifBlank { "Looks lovely!" },
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                                color = bubbleTextColor
                            )
                        }
                        ChatMessageType.VOICE_NOTE -> {
                            PinterestVoiceNoteBody(
                                durationSeconds = message.voiceDurationSeconds,
                                contentColor = bubbleTextColor
                            )
                        }
                        ChatMessageType.VOICE_CALL -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isMissed = message.callStatus == CallStatus.MISSED
                                Icon(
                                    imageVector = if (isMissed) Icons.Default.CallMissed else Icons.Default.CallMade,
                                    contentDescription = message.callStatus.label,
                                    tint = if (isMissed) PinRed else bubbleTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isMissed) {
                                        "Missed voice call"
                                    } else {
                                        "Voice call · ${message.callDurationText.ifBlank { "04:12" }}"
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = bubbleTextColor
                                )
                            }
                        }
                        ChatMessageType.MEDIA_ATTACHMENT -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(148.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(Color(0xFFD97757), Color(0xFFE60023))
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
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    if (message.isVideoMedia) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.55f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play video",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                                if (message.mediaTitle.isNotBlank()) {
                                    Text(
                                        text = message.mediaTitle,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = bubbleTextColor
                                    )
                                }
                                if (message.text.isNotBlank()) {
                                    Text(
                                        text = message.text,
                                        fontSize = 13.sp,
                                        color = bubbleTextColor.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                        ChatMessageType.DELETED -> Unit
                    }
                }
            }
        }

        // Reaction hearts under messages
        if (message.reactionEmoji.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isDark) Color(0xFF262626) else Color(0xFFFFF0F2),
                border = BorderStroke(1.dp, PinRed.copy(alpha = 0.28f)),
                modifier = Modifier.padding(
                    start = if (message.isOutgoing) 0.dp else 36.dp,
                    end = if (message.isOutgoing) 4.dp else 0.dp
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Reaction heart",
                        tint = PinRed,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = message.time,
                        fontSize = 10.sp,
                        color = textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun PinterestDeletedCapsule(
    text: String,
    textSecondary: Color
) {
    val dashColor = textSecondary.copy(alpha = 0.55f)
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawRoundRect(
                    color = dashColor,
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    style = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                )
            }
            Text(
                text = text,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                color = textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PinterestVoiceNoteBody(
    durationSeconds: Int,
    contentColor: Color
) {
    val bars = remember { listOf(7, 14, 10, 18, 12, 16, 8, 14, 17, 11, 9) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(min = 172.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Play voice note",
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
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
                        .background(contentColor.copy(alpha = 0.85f))
                )
            }
        }
        Text(
            text = formatVoiceDuration(durationSeconds),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}
