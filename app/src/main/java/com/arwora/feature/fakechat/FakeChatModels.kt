package com.arwora.feature.fakechat

import android.net.Uri
import androidx.compose.ui.graphics.Color
import java.util.UUID

enum class ChatPlatform(
    val displayName: String,
    val accentColor: Color,
    val defaultStatus: String
) {
    WHATSAPP("WhatsApp", Color(0xFF25D366), "last seen today at 10:40 AM"),
    INSTAGRAM("Instagram", Color(0xFFE1306C), "Active now"),
    PINTEREST("Pinterest", Color(0xFFE60023), "@sarah.creates"),
    MESSENGER("Messenger", Color(0xFF0084FF), "Active now"),
    IMO("Imo", Color(0xFF0091EA), "Online"),
    TIKTOK("TikTok", Color(0xFFFF0050), "Active 5m ago"),
    TWITTER_X("Twitter/X", Color(0xFF1DA1F2), "@sarahjenkins"),
    SNAPCHAT("Snapchat", Color(0xFFFFFC00), "Bitmoji active");

    companion object {
        fun fromLabel(label: String): ChatPlatform {
            val normalized = label.trim().lowercase()
            return entries.firstOrNull {
                it.displayName.lowercase() == normalized || it.name.lowercase() == normalized
            } ?: WHATSAPP
        }
    }
}

enum class ChatMessageType(val label: String) {
    TEXT("Text"),
    VOICE_NOTE("Voice Note"),
    VOICE_CALL("Voice Call"),
    DELETED("Deleted"),
    MEDIA_ATTACHMENT("Media / Video")
}

enum class CallStatus(val label: String) {
    COMPLETED("Completed"),
    MISSED("Missed")
}

enum class WhatsAppReadStatus(val label: String) {
    SENT("Sent ✓"),
    DELIVERED("Delivered ✓✓"),
    READ("Read ✓✓")
}

enum class InstagramOutgoingPalette(val label: String) {
    GRADIENT("IG Gradient"),
    SOLID_INDIGO("Solid Blue")
}

enum class PinterestOutgoingStyle(val label: String) {
    NEUTRAL_GRAY("Minimal Gray"),
    PIN_ACCENT("Red Accent")
}

data class FakeChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val time: String = "10:42 AM",
    val isOutgoing: Boolean = true,
    val isRead: Boolean = true,
    val readStatus: WhatsAppReadStatus = if (isRead) WhatsAppReadStatus.READ else WhatsAppReadStatus.DELIVERED,
    val messageType: ChatMessageType = ChatMessageType.TEXT,
    val voiceDurationSeconds: Int = 18,
    val callStatus: CallStatus = CallStatus.COMPLETED,
    val callDurationText: String = "04:12",
    val replyToAuthor: String = "",
    val replyToSnippet: String = "",
    val mediaUri: Uri? = null,
    val mediaTitle: String = "Shared clip",
    val isVideoMedia: Boolean = false,
    val reactionEmoji: String = ""
)

data class FakeChatConfig(
    val platform: ChatPlatform,
    val senderName: String,
    val statusText: String,
    val avatarUri: Uri?,
    val isDarkPreview: Boolean,
    val clockTime: String,
    val batteryLevel: String,
    val isVerifiedBadge: Boolean,
    val isBusinessChat: Boolean,
    val igOutgoingPalette: InstagramOutgoingPalette,
    val showIgSeenIndicator: Boolean,
    val showWhatsAppDoodle: Boolean,
    val showPinterestProfileCard: Boolean,
    val isPinterestFollowing: Boolean,
    val pinterestOutgoingStyle: PinterestOutgoingStyle
)

fun formatVoiceDuration(seconds: Int): String {
    val safeSeconds = seconds.coerceIn(1, 3599)
    val mins = safeSeconds / 60
    val secs = safeSeconds % 60
    return "%d:%02d".format(mins, secs)
}

fun defaultMessagesForPlatform(platform: ChatPlatform): List<FakeChatMessage> {
    return when (platform) {
        ChatPlatform.WHATSAPP -> listOf(
            FakeChatMessage(
                text = "Hey! Did you check the updated project specs?",
                time = "10:38 AM",
                isOutgoing = false
            ),
            FakeChatMessage(
                text = "Yes, just reviewed them. Everything looks crisp!",
                time = "10:40 AM",
                isOutgoing = true,
                readStatus = WhatsAppReadStatus.READ
            ),
            FakeChatMessage(
                messageType = ChatMessageType.VOICE_NOTE,
                voiceDurationSeconds = 24,
                time = "10:41 AM",
                isOutgoing = false
            ),
            FakeChatMessage(
                text = "Awesome, let's ship the release build today.",
                time = "10:42 AM",
                isOutgoing = true,
                readStatus = WhatsAppReadStatus.READ
            )
        )
        ChatPlatform.INSTAGRAM -> listOf(
            FakeChatMessage(
                text = "Loved your new studio reel! Lighting looks unreal.",
                time = "10:36 AM",
                isOutgoing = false,
                reactionEmoji = "❤️"
            ),
            FakeChatMessage(
                text = "Thank you! Shot it on 4K 60fps earlier.",
                time = "10:38 AM",
                isOutgoing = true,
                replyToAuthor = "Sarah Jenkins",
                replyToSnippet = "Loved your new studio reel! Lighting looks..."
            ),
            FakeChatMessage(
                messageType = ChatMessageType.MEDIA_ATTACHMENT,
                mediaTitle = "reel_behind_scenes.mp4",
                text = "Here is the color grading breakdown clip",
                isVideoMedia = true,
                time = "10:40 AM",
                isOutgoing = false
            ),
            FakeChatMessage(
                messageType = ChatMessageType.VOICE_NOTE,
                voiceDurationSeconds = 16,
                time = "10:42 AM",
                isOutgoing = true
            )
        )
        ChatPlatform.PINTEREST -> listOf(
            FakeChatMessage(
                text = "Saved your minimalist interior moodboard!",
                time = "10:35 AM",
                isOutgoing = false,
                reactionEmoji = "❤️"
            ),
            FakeChatMessage(
                messageType = ChatMessageType.MEDIA_ATTACHMENT,
                mediaTitle = "Nordic Oak Living Room Pin",
                text = "Check this warm terracotta palette",
                isVideoMedia = false,
                time = "10:39 AM",
                isOutgoing = true
            ),
            FakeChatMessage(
                messageType = ChatMessageType.DELETED,
                text = "You deleted a message",
                time = "10:40 AM",
                isOutgoing = true
            ),
            FakeChatMessage(
                text = "Pinned it to my 2026 Architecture board.",
                time = "10:42 AM",
                isOutgoing = false,
                reactionEmoji = "❤️"
            )
        )
        else -> listOf(
            FakeChatMessage(
                text = "Hey! Did you check the new project specs?",
                time = "10:38 AM",
                isOutgoing = false
            ),
            FakeChatMessage(
                text = "Yes, just reviewed them. Looks great!",
                time = "10:40 AM",
                isOutgoing = true
            ),
            FakeChatMessage(
                text = "Awesome, let's deploy the updates today.",
                time = "10:41 AM",
                isOutgoing = false
            )
        )
    }
}
