package com.arwora.feature.fakechat

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val WaHeaderLight = Color(0xFF075E54)
private val WaHeaderDark = Color(0xFF1F2C34)
private val WaBgLight = Color(0xFFEFEAE2)
private val WaBgDark = Color(0xFF0B141A)
private val WaIncomingLight = Color(0xFFFFFFFF)
private val WaIncomingDark = Color(0xFF202C33)
private val WaOutgoingLight = Color(0xFFDCF8C6)
private val WaOutgoingDark = Color(0xFF005C4B)
private val WaBlueTick = Color(0xFF34B7F1)
private val WaFabGreen = Color(0xFF00A884)

@Composable
fun WhatsAppChatPreview(
    config: FakeChatConfig,
    messages: List<FakeChatMessage>,
    modifier: Modifier = Modifier
) {
    val isDark = config.isDarkPreview
    val headerBg = if (isDark) WaHeaderDark else WaHeaderLight
    val headerContent = Color.White
    val canvasBg = if (isDark) WaBgDark else WaBgLight
    val incomingBubbleBg = if (isDark) WaIncomingDark else WaIncomingLight
    val outgoingBubbleBg = if (isDark) WaOutgoingDark else WaOutgoingLight
    val textPrimary = if (isDark) Color(0xFFE9EDEF) else Color(0xFF111B21)
    val textMeta = if (isDark) Color(0xFF8696A0) else Color(0xFF667781)
    val inputPillBg = if (isDark) Color(0xFF202C33) else Color.White

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("whatsapp_chat_preview"),
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 6.dp,
        color = canvasBg
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // WhatsApp Top Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = headerContent,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (config.avatarUri != null) {
                        AsyncImage(
                            model = config.avatarUri,
                            contentDescription = "Contact avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Default avatar",
                            tint = headerContent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.senderName.ifBlank { "WhatsApp Contact" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = headerContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = config.statusText.ifBlank { "last seen today at 10:40 AM" },
                        fontSize = 11.5.sp,
                        color = headerContent.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video Call",
                    tint = headerContent,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Voice Call",
                    tint = headerContent,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = headerContent,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Wallpaper Body + Bubbles
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(canvasBg)
            ) {
                if (config.showWhatsAppDoodle) {
                    WhatsAppDoodleWallpaper(
                        isDark = isDark,
                        modifier = Modifier.matchParentSize()
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Date Pill
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isDark) Color(0xFF182229) else Color(0xFFE1F3FB),
                            shadowElevation = 0.5.dp
                        ) {
                            Text(
                                text = "TODAY",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textMeta,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    messages.forEach { msg ->
                        WhatsAppMessageRow(
                            message = msg,
                            incomingBg = incomingBubbleBg,
                            outgoingBg = outgoingBubbleBg,
                            textPrimary = textPrimary,
                            textMeta = textMeta,
                            isDark = isDark
                        )
                    }
                }
            }

            // WhatsApp Bottom Composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(canvasBg)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = inputPillBg,
                    shadowElevation = 1.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mood,
                            contentDescription = "Emoji",
                            tint = textMeta,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Message",
                            fontSize = 14.sp,
                            color = textMeta,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach",
                            tint = textMeta,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            tint = textMeta,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(WaFabGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice note",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WhatsAppDoodleWallpaper(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val doodleColor = if (isDark) {
        Color(0xFFFFFFFF).copy(alpha = 0.045f)
    } else {
        Color(0xFF7B6D5C).copy(alpha = 0.085f)
    }

    Canvas(modifier = modifier) {
        val stepX = 54.dp.toPx()
        val stepY = 50.dp.toPx()
        val stroke = Stroke(width = 1.3.dp.toPx())
        var row = 0
        var y = 16.dp.toPx()
        while (y < size.height) {
            var col = 0
            var x = if (row % 2 == 0) 14.dp.toPx() else 38.dp.toPx()
            while (x < size.width) {
                when ((row + col) % 4) {
                    0 -> {
                        // Mini chat bubble doodle
                        drawRoundRect(
                            color = doodleColor,
                            topLeft = Offset(x, y),
                            size = Size(16.dp.toPx(), 11.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            style = stroke
                        )
                    }
                    1 -> {
                        // Circle / smiley ring doodle
                        drawCircle(
                            color = doodleColor,
                            radius = 6.5.dp.toPx(),
                            center = Offset(x + 6.dp.toPx(), y + 6.dp.toPx()),
                            style = stroke
                        )
                    }
                    2 -> {
                        // Diagonal phone / wave doodle
                        drawLine(
                            color = doodleColor,
                            start = Offset(x, y + 10.dp.toPx()),
                            end = Offset(x + 12.dp.toPx(), y),
                            strokeWidth = 1.4.dp.toPx()
                        )
                    }
                    else -> {
                        // Small diamond / star doodle
                        drawCircle(
                            color = doodleColor,
                            radius = 3.dp.toPx(),
                            center = Offset(x + 6.dp.toPx(), y + 5.dp.toPx())
                        )
                    }
                }
                x += stepX
                col++
            }
            y += stepY
            row++
        }
    }
}

@Composable
private fun WhatsAppMessageRow(
    message: FakeChatMessage,
    incomingBg: Color,
    outgoingBg: Color,
    textPrimary: Color,
    textMeta: Color,
    isDark: Boolean
) {
    val density = LocalDensity.current
    val bubbleShape = remember(message.isOutgoing, density) {
        val tailPx = with(density) { 8.dp.toPx() }
        val cornerPx = with(density) { 12.dp.toPx() }
        GenericShape { size, _ ->
            if (message.isOutgoing) {
                // Top-right sharp tail curve
                moveTo(cornerPx, 0f)
                lineTo(size.width + tailPx * 0.45f, 0f)
                quadraticTo(size.width, tailPx * 0.45f, size.width, tailPx * 1.35f)
                lineTo(size.width, size.height - cornerPx)
                quadraticTo(size.width, size.height, size.width - cornerPx, size.height)
                lineTo(cornerPx, size.height)
                quadraticTo(0f, size.height, 0f, size.height - cornerPx)
                lineTo(0f, cornerPx)
                quadraticTo(0f, 0f, cornerPx, 0f)
            } else {
                // Top-left sharp tail curve
                moveTo(-tailPx * 0.45f, 0f)
                lineTo(size.width - cornerPx, 0f)
                quadraticTo(size.width, 0f, size.width, cornerPx)
                lineTo(size.width, size.height - cornerPx)
                quadraticTo(size.width, size.height, size.width - cornerPx, size.height)
                lineTo(cornerPx, size.height)
                quadraticTo(0f, size.height, 0f, size.height - cornerPx)
                lineTo(0f, tailPx * 1.35f)
                quadraticTo(0f, tailPx * 0.45f, -tailPx * 0.45f, 0f)
            }
            close()
        }
    }

    val bubbleColor = if (message.isOutgoing) outgoingBg else incomingBg

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (message.isOutgoing) 42.dp else 6.dp,
                end = if (message.isOutgoing) 6.dp else 42.dp
            ),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(min = 96.dp, max = 276.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                when (message.messageType) {
                    ChatMessageType.TEXT -> {
                        Text(
                            text = message.text.ifBlank { "Hello!" },
                            fontSize = 14.sp,
                            lineHeight = 19.sp,
                            color = textPrimary
                        )
                    }
                    ChatMessageType.VOICE_NOTE -> {
                        WhatsAppVoiceNoteBody(
                            durationSeconds = message.voiceDurationSeconds,
                            isOutgoing = message.isOutgoing,
                            textMeta = textMeta,
                            isDark = isDark
                        )
                    }
                    ChatMessageType.VOICE_CALL -> {
                        WhatsAppCallCardBody(
                            callStatus = message.callStatus,
                            durationText = message.callDurationText,
                            textPrimary = textPrimary,
                            textMeta = textMeta,
                            isDark = isDark
                        )
                    }
                    ChatMessageType.DELETED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Block,
                                contentDescription = "Deleted",
                                tint = textMeta,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = message.text.ifBlank {
                                    if (message.isOutgoing) "You deleted this message" else "This message was deleted"
                                },
                                fontSize = 13.5.sp,
                                fontStyle = FontStyle.Italic,
                                color = textMeta
                            )
                        }
                    }
                    ChatMessageType.MEDIA_ATTACHMENT -> {
                        WhatsAppMediaBody(
                            message = message,
                            textPrimary = textPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = message.time,
                        fontSize = 10.sp,
                        color = textMeta
                    )
                    if (message.isOutgoing && message.messageType != ChatMessageType.DELETED) {
                        val icon = when (message.readStatus) {
                            WhatsAppReadStatus.SENT -> Icons.Default.Done
                            WhatsAppReadStatus.DELIVERED,
                            WhatsAppReadStatus.READ -> Icons.Default.DoneAll
                        }
                        val tint = if (message.readStatus == WhatsAppReadStatus.READ) WaBlueTick else textMeta
                        Icon(
                            imageVector = icon,
                            contentDescription = message.readStatus.label,
                            tint = tint,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatsAppVoiceNoteBody(
    durationSeconds: Int,
    isOutgoing: Boolean,
    textMeta: Color,
    isDark: Boolean
) {
    val barHeights = remember { listOf(6, 12, 9, 16, 20, 14, 18, 10, 15, 22, 13, 8, 17, 11, 7) }
    val activeColor = if (isOutgoing) Color(0xFF00A884) else Color(0xFF34B7F1)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(min = 190.dp)
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Play voice note",
            tint = textMeta,
            modifier = Modifier.size(26.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                barHeights.forEachIndexed { idx, h ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(h.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (idx < 6) activeColor else textMeta.copy(alpha = 0.45f))
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = formatVoiceDuration(durationSeconds),
                fontSize = 10.5.sp,
                color = textMeta
            )
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF2A3942) else Color(0xFFE0E5E8)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = activeColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun WhatsAppCallCardBody(
    callStatus: CallStatus,
    durationText: String,
    textPrimary: Color,
    textMeta: Color,
    isDark: Boolean
) {
    val isMissed = callStatus == CallStatus.MISSED
    val iconTint = if (isMissed) Color(0xFFEF4444) else Color(0xFF00A884)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isDark) Color.Black.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.05f),
        modifier = Modifier.widthIn(min = 180.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMissed) Icons.Default.CallMissed else Icons.Default.CallMade,
                    contentDescription = callStatus.label,
                    tint = iconTint,
                    modifier = Modifier.size(17.dp)
                )
            }
            Column {
                Text(
                    text = if (isMissed) "Missed voice call" else "Voice call",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )
                Text(
                    text = if (isMissed) "Tap to call back" else durationText.ifBlank { "04:12" },
                    fontSize = 11.sp,
                    color = textMeta
                )
            }
        }
    }
}

@Composable
private fun WhatsAppMediaBody(
    message: FakeChatMessage,
    textPrimary: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1F3B4D), Color(0xFF2B6777))
                    )
                )
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(30.dp)
                    )
                    if (message.mediaTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = message.mediaTitle,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
            if (message.isVideoMedia) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        if (message.text.isNotBlank()) {
            Text(
                text = message.text,
                fontSize = 13.5.sp,
                color = textPrimary
            )
        }
    }
}
