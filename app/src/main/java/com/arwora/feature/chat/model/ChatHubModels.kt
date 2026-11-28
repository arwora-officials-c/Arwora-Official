package com.arwora.feature.chat.model

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

enum class ChatMessageType(val storageValue: String) {
    TEXT("TEXT"),
    VOICE("VOICE"),
    IMAGE("IMAGE"),
    VIDEO("VIDEO"),
    FILE("FILE"),
    LOCATION("LOCATION"),
    STICKER("STICKER"),
    SYSTEM_ALERT("SYSTEM_ALERT");

    companion object {
        fun fromStorage(raw: String?): ChatMessageType {
            return entries.firstOrNull { it.storageValue == raw } ?: TEXT
        }
    }
}

enum class MessageDeliveryStatus(val storageValue: String, val label: String) {
    SCHEDULED("SCHEDULED", "Scheduled"),
    SENDING("SENDING", "Sending"),
    SENT("SENT", "Sent"),
    DELIVERED("DELIVERED", "Delivered"),
    SEEN("SEEN", "Seen");

    companion object {
        fun fromStorage(raw: String?): MessageDeliveryStatus {
            return entries.firstOrNull { it.storageValue == raw } ?: SENT
        }
    }
}

enum class ChatHubTab(val title: String) {
    CHATS("Chats"),
    ARCHIVED("Archived"),
    NEAR_CHAT("Near Chat"),
    VAULT("Vault")
}

enum class ChatMediaFilterTab(val title: String) {
    MEDIA("Media"),
    LINKS("Links"),
    FILES("Files")
}

data class ChatThemePreset(
    val id: String,
    val name: String,
    val outgoingBubbleColor: Color,
    val outgoingTextColor: Color,
    val accentColor: Color
) {
    companion object {
        val presets: List<ChatThemePreset> = listOf(
            ChatThemePreset(
                id = "indigo_pulse",
                name = "Indigo Pulse",
                outgoingBubbleColor = Color(0xFF4F46E5),
                outgoingTextColor = Color.White,
                accentColor = Color(0xFF818CF8)
            ),
            ChatThemePreset(
                id = "emerald_signal",
                name = "Emerald Signal",
                outgoingBubbleColor = Color(0xFF047857),
                outgoingTextColor = Color.White,
                accentColor = Color(0xFF34D399)
            ),
            ChatThemePreset(
                id = "crimson_ember",
                name = "Crimson Ember",
                outgoingBubbleColor = Color(0xFFBE123C),
                outgoingTextColor = Color.White,
                accentColor = Color(0xFFFB7185)
            ),
            ChatThemePreset(
                id = "ocean_cyan",
                name = "Ocean Cyan",
                outgoingBubbleColor = Color(0xFF0E7490),
                outgoingTextColor = Color.White,
                accentColor = Color(0xFF22D3EE)
            ),
            ChatThemePreset(
                id = "obsidian_amber",
                name = "Obsidian Gold",
                outgoingBubbleColor = Color(0xFFB45309),
                outgoingTextColor = Color.White,
                accentColor = Color(0xFFFBBF24)
            )
        )

        fun byId(id: String?): ChatThemePreset =
            presets.firstOrNull { it.id == id } ?: presets.first()
    }
}

data class ChatWallpaperPreset(
    val id: String,
    val name: String,
    val startColor: Color,
    val endColor: Color,
    val patternAlpha: Float
) {
    companion object {
        val presets: List<ChatWallpaperPreset> = listOf(
            ChatWallpaperPreset(
                id = "midnight_obsidian",
                name = "Obsidian Mesh",
                startColor = Color(0xFF0B0F17),
                endColor = Color(0xFF131B2E),
                patternAlpha = 0.08f
            ),
            ChatWallpaperPreset(
                id = "nebula_indigo",
                name = "Nebula Dusk",
                startColor = Color(0xFF141029),
                endColor = Color(0xFF1E1B4B),
                patternAlpha = 0.11f
            ),
            ChatWallpaperPreset(
                id = "forest_cipher",
                name = "Cipher Pine",
                startColor = Color(0xFF071915),
                endColor = Color(0xFF0F2E26),
                patternAlpha = 0.09f
            ),
            ChatWallpaperPreset(
                id = "deep_ocean",
                name = "Deep Abyss",
                startColor = Color(0xFF071624),
                endColor = Color(0xFF0E2A42),
                patternAlpha = 0.10f
            ),
            ChatWallpaperPreset(
                id = "crimson_night",
                name = "Crimson Velvet",
                startColor = Color(0xFF1F0A14),
                endColor = Color(0xFF331020),
                patternAlpha = 0.09f
            )
        )

        fun byId(id: String?): ChatWallpaperPreset =
            presets.firstOrNull { it.id == id } ?: presets.first()
    }
}

data class ChatStickerItem(
    val id: String,
    val packName: String,
    val emoji: String,
    val caption: String,
    val badgeColorHex: Long
)

object ChatStickerCatalog {
    val stickers: List<ChatStickerItem> = listOf(
        ChatStickerItem("stk_shield_ok", "Cipher", "🛡️", "Verified E2EE", 0xFF10B981),
        ChatStickerItem("stk_rocket", "Vibes", "🚀", "Ship It!", 0xFF6366F1),
        ChatStickerItem("stk_ghost", "Stealth", "👻", "Ghost Mode", 0xFF8B5CF6),
        ChatStickerItem("stk_fire", "Vibes", "🔥", "On Fire", 0xFFF97316),
        ChatStickerItem("stk_100", "Vibes", "💯", "100% Locked", 0xFFEC4899),
        ChatStickerItem("stk_wave", "Social", "👋", "Hey Nearby!", 0xFF06B6D4),
        ChatStickerItem("stk_mindblown", "Social", "🤯", "Mind Blown", 0xFFF59E0B),
        ChatStickerItem("stk_key", "Cipher", "🔐", "Encrypted", 0xFF3B82F6),
        ChatStickerItem("stk_bolt", "Tech", "⚡", "Hyper Fast", 0xFFEAB308),
        ChatStickerItem("stk_party", "Social", "🎉", "Celebrate!", 0xFFA855F7),
        ChatStickerItem("stk_radar", "Tech", "📡", "Near P2P", 0xFF14B8A6),
        ChatStickerItem("stk_salute", "Social", "🫡", "Copy That", 0xFF22C55E)
    )

    fun byId(id: String?): ChatStickerItem? = stickers.firstOrNull { it.id == id }
}

data class ChatThread(
    val id: String,
    val title: String,
    val isGroup: Boolean = false,
    val participantIds: List<String> = emptyList(),
    val participantNames: List<String> = emptyList(),
    val peerDeviceId: String? = null,
    val isArchived: Boolean = false,
    val isHidden: Boolean = false,
    val isLocked: Boolean = false,
    val isMuted: Boolean = false,
    val customSoundUri: String? = null,
    val customSoundName: String? = null,
    val themeId: String = "indigo_pulse",
    val wallpaperPresetOrUri: String = "midnight_obsidian",
    val isOnline: Boolean = false,
    val isTyping: Boolean = false,
    val lastSeenMillis: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    val lastMessagePreview: String = "",
    val lastMessageTimeMillis: Long = System.currentTimeMillis(),
    val e2eeFingerprint: String = ""
)

data class ChatMessage(
    val id: String,
    val threadId: String,
    val senderId: String,
    val senderName: String,
    val isOutgoing: Boolean,
    val type: ChatMessageType = ChatMessageType.TEXT,
    val text: String = "",
    val mediaPathOrUri: String? = null,
    val mediaBase64: String? = null,
    val mediaMimeType: String? = null,
    val fileName: String? = null,
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val waveform: List<Float> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String? = null,
    val stickerId: String? = null,
    val replyToMessageId: String? = null,
    val replyPreviewText: String? = null,
    val replySenderName: String? = null,
    val forwardedFrom: String? = null,
    val isEdited: Boolean = false,
    val editedAtMillis: Long? = null,
    val isDeletedForEveryone: Boolean = false,
    val isViewOnce: Boolean = false,
    val viewOnceConsumed: Boolean = false,
    val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
    val deliveredAtMillis: Long? = null,
    val seenAtMillis: Long? = null,
    val reactions: Map<String, Set<String>> = emptyMap(),
    val scheduledForMillis: Long? = null,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val reactionCounts: List<Pair<String, Int>>
        get() = SUPPORTED_REACTIONS.mapNotNull { emoji ->
            val count = reactions[emoji]?.size ?: 0
            if (count > 0) emoji to count else null
        } + reactions.entries
            .filter { it.key !in SUPPORTED_REACTIONS && it.value.isNotEmpty() }
            .map { it.key to it.value.size }

    fun displaySummary(): String {
        if (isDeletedForEveryone) return "Message deleted for everyone"
        return when (type) {
            ChatMessageType.TEXT -> text.ifBlank { "Message" }
            ChatMessageType.VOICE -> "Voice note (${formatShortDuration(durationMs)})"
            ChatMessageType.IMAGE -> if (isViewOnce) "View once photo" else (text.ifBlank { "Photo" })
            ChatMessageType.VIDEO -> if (isViewOnce) "View once video" else (fileName ?: "Video")
            ChatMessageType.FILE -> fileName ?: "File attachment"
            ChatMessageType.LOCATION -> locationLabel ?: "Shared location"
            ChatMessageType.STICKER -> ChatStickerCatalog.byId(stickerId)?.let { "${it.emoji} ${it.caption}" } ?: "Sticker"
            ChatMessageType.SYSTEM_ALERT -> text
        }
    }

    companion object {
        val SUPPORTED_REACTIONS = listOf("❤️", "😂", "👍", "🔥", "😮", "🙏")

        fun formatShortDuration(ms: Long): String {
            val sec = ((ms + 500L) / 1000L).coerceAtLeast(0L)
            return "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
        }

        fun encodeReactions(map: Map<String, Set<String>>): String {
            if (map.isEmpty()) return "{}"
            val obj = JSONObject()
            map.forEach { (emoji, actors) ->
                if (actors.isNotEmpty()) {
                    obj.put(emoji, JSONArray(actors.toList()))
                }
            }
            return obj.toString()
        }

        fun decodeReactions(raw: String?): Map<String, Set<String>> {
            if (raw.isNullOrBlank() || raw == "{}") return emptyMap()
            return runCatching {
                val obj = JSONObject(raw)
                val out = LinkedHashMap<String, Set<String>>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val arr = obj.optJSONArray(key) ?: continue
                    val set = LinkedHashSet<String>()
                    for (i in 0 until arr.length()) {
                        val actor = arr.optString(i)
                        if (actor.isNotBlank()) set.add(actor)
                    }
                    if (set.isNotEmpty()) out[key] = set
                }
                out
            }.getOrDefault(emptyMap())
        }

        fun encodeWaveform(bars: List<Float>): String {
            if (bars.isEmpty()) return "[]"
            return JSONArray(bars.map { it.toDouble() }).toString()
        }

        fun decodeWaveform(raw: String?): List<Float> {
            if (raw.isNullOrBlank() || raw == "[]") return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                List(arr.length()) { idx -> arr.optDouble(idx, 0.25).toFloat().coerceIn(0.1f, 1f) }
            }.getOrDefault(emptyList())
        }
    }
}

enum class NearDiscoverySource(val label: String) {
    WIFI_NSD("Wi-Fi mDNS"),
    BLUETOOTH_LE("Bluetooth LE"),
    WIFI_DIRECT("Wi-Fi Direct")
}

data class NearChatPeer(
    val id: String,
    val displayName: String,
    val source: NearDiscoverySource,
    val hostIp: String? = null,
    val port: Int = 0,
    val bluetoothAddress: String? = null,
    val rssiDbm: Int? = null,
    val isConnected: Boolean = false,
    val lastSeenMillis: Long = System.currentTimeMillis()
)

data class ChatHubSettings(
    val ghostModeEnabled: Boolean = false,
    val screenshotAlertsEnabled: Boolean = true,
    val flagSecureLockedChats: Boolean = true,
    val dndEnabled: Boolean = false,
    val dndStartHour: Int = 22,
    val dndStartMinute: Int = 0,
    val dndEndHour: Int = 7,
    val dndEndMinute: Int = 0,
    val dataSaverMode: Boolean = false,
    val lowBatteryAutoCompress: Boolean = true,
    val disableHeavyAutoDownload: Boolean = false,
    val defaultThemeId: String = "indigo_pulse",
    val defaultWallpaperId: String = "midnight_obsidian",
    val defaultSoundUri: String? = null,
    val defaultSoundName: String = "System Default"
)
