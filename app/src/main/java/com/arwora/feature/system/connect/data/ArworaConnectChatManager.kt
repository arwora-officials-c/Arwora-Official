package com.arwora.feature.system.connect.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.arwora.feature.system.connect.model.ChatMessageType
import com.arwora.feature.system.connect.model.ConnectChatMessage
import com.arwora.feature.system.connect.model.P2pMessageRetentionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Manages real-time P2P chat histories, compressed image encoding/decoding,
 * unread counters, emoji reactions, and automatic retention-based pruning per connected peer.
 */
class ArworaConnectChatManager(
    context: Context,
    initialRetentionPolicy: P2pMessageRetentionPolicy = P2pMessageRetentionPolicy.OFF,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val historyFile: File? = File(context.applicationContext.filesDir, HISTORY_FILE_NAME)
) {

    private val appContext = context.applicationContext
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileMutex = Mutex()
    @Volatile
    private var isShutDown = false

    private val _messagesByPeer = MutableStateFlow<Map<String, List<ConnectChatMessage>>>(emptyMap())
    val messagesByPeer: StateFlow<Map<String, List<ConnectChatMessage>>> = _messagesByPeer.asStateFlow()

    private val _unreadCountsByPeer = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCountsByPeer: StateFlow<Map<String, Int>> = _unreadCountsByPeer.asStateFlow()

    private val _retentionPolicy = MutableStateFlow(initialRetentionPolicy)
    val retentionPolicy: StateFlow<P2pMessageRetentionPolicy> = _retentionPolicy.asStateFlow()

    @Volatile
    var activeChatPeerId: String? = null
        private set

    init {
        if (historyFile != null && historyFile.exists()) {
            loadPersistedHistoryAsync()
        }
    }

    fun setRetentionPolicy(policy: P2pMessageRetentionPolicy): Int {
        _retentionPolicy.value = policy
        return pruneExpiredMessages()
    }

    fun setActiveChatPeer(peerDeviceId: String?) {
        activeChatPeerId = peerDeviceId
        pruneExpiredMessages()
        if (!peerDeviceId.isNullOrBlank()) {
            markPeerRead(peerDeviceId)
        }
    }

    fun markPeerRead(peerDeviceId: String) {
        synchronized(this) {
            if ((_unreadCountsByPeer.value[peerDeviceId] ?: 0) == 0) return
            val next = _unreadCountsByPeer.value.toMutableMap()
            next.remove(peerDeviceId)
            _unreadCountsByPeer.value = next
        }
    }

    fun createOutgoingTextMessage(
        peerDeviceId: String,
        localDeviceId: String,
        localDeviceName: String,
        text: String
    ): Pair<ConnectChatMessage, JSONObject> {
        val cleanText = text.trim()
        val id = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()
        val message = ConnectChatMessage(
            id = id,
            peerDeviceId = peerDeviceId,
            senderDeviceId = localDeviceId,
            senderName = localDeviceName,
            type = ChatMessageType.TEXT,
            text = cleanText,
            timestampMillis = ts,
            isOutgoing = true
        )
        val packet = JSONObject().apply {
            put("type", TYPE_CHAT)
            put("messageId", id)
            put("senderId", localDeviceId)
            put("senderName", localDeviceName)
            put("text", cleanText)
            put("timestamp", ts)
        }
        return message to packet
    }

    fun createOutgoingImageMessage(
        peerDeviceId: String,
        localDeviceId: String,
        localDeviceName: String,
        imageBase64: String,
        caption: String = ""
    ): Pair<ConnectChatMessage, JSONObject> {
        val cleanCaption = caption.trim()
        val id = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()
        val message = ConnectChatMessage(
            id = id,
            peerDeviceId = peerDeviceId,
            senderDeviceId = localDeviceId,
            senderName = localDeviceName,
            type = ChatMessageType.IMAGE,
            text = cleanCaption,
            imageBase64 = imageBase64,
            timestampMillis = ts,
            isOutgoing = true
        )
        val packet = JSONObject().apply {
            put("type", TYPE_IMAGE)
            put("messageId", id)
            put("senderId", localDeviceId)
            put("senderName", localDeviceName)
            put("imageBase64", imageBase64)
            put("caption", cleanCaption)
            put("timestamp", ts)
        }
        return message to packet
    }

    fun createOutgoingVoiceMessage(
        peerDeviceId: String,
        localDeviceId: String,
        localDeviceName: String,
        audioBase64: String,
        durationMs: Long,
        waveform: List<Float>
    ): Pair<ConnectChatMessage, JSONObject> {
        val id = UUID.randomUUID().toString()
        val ts = System.currentTimeMillis()
        val normalizedWave = ArworaVoiceRecorder.resampleWaveform(waveform)
        val message = ConnectChatMessage(
            id = id,
            peerDeviceId = peerDeviceId,
            senderDeviceId = localDeviceId,
            senderName = localDeviceName,
            type = ChatMessageType.VOICE,
            audioBase64 = audioBase64,
            durationMs = durationMs.coerceAtLeast(0L),
            waveform = normalizedWave,
            timestampMillis = ts,
            isOutgoing = true
        )
        val packet = JSONObject().apply {
            put("type", TYPE_VOICE)
            put("messageId", id)
            put("senderId", localDeviceId)
            put("senderName", localDeviceName)
            put("audioBase64", audioBase64)
            put("durationMs", durationMs.coerceAtLeast(0L))
            put("waveform", JSONArray(normalizedWave.map { it.toDouble() }))
            put("timestamp", ts)
        }
        return message to packet
    }

    fun appendLocalMessage(message: ConnectChatMessage) {
        upsertMessage(message.peerDeviceId, message, countUnread = false)
    }

    fun removeMessage(peerDeviceId: String, messageId: String) {
        synchronized(this) {
            val map = _messagesByPeer.value.toMutableMap()
            val list = map[peerDeviceId].orEmpty().filterNot { it.id == messageId }
            map[peerDeviceId] = list
            _messagesByPeer.value = map
        }
    }

    fun handleIncomingTextFrame(
        peerDeviceId: String,
        frame: JSONObject,
        fallbackPeerName: String
    ): ConnectChatMessage? {
        val text = frame.optString("text").trim()
        if (text.isEmpty()) return null
        val id = frame.optString("messageId").ifBlank { UUID.randomUUID().toString() }
        val senderId = frame.optString("senderId").ifBlank { peerDeviceId }
        val senderName = frame.optString("senderName", fallbackPeerName).ifBlank { fallbackPeerName }
        val ts = frame.optLong("timestamp", System.currentTimeMillis()).takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val msg = ConnectChatMessage(
            id = id,
            peerDeviceId = peerDeviceId,
            senderDeviceId = senderId,
            senderName = senderName,
            type = ChatMessageType.TEXT,
            text = text,
            timestampMillis = ts,
            isOutgoing = false
        )
        upsertMessage(peerDeviceId, msg, countUnread = activeChatPeerId != peerDeviceId)
        return msg
    }

    fun handleIncomingImageFrame(
        peerDeviceId: String,
        frame: JSONObject,
        fallbackPeerName: String
    ): ConnectChatMessage? {
        val imageBase64 = frame.optString("imageBase64").trim()
        if (imageBase64.isEmpty()) return null
        val id = frame.optString("messageId").ifBlank { UUID.randomUUID().toString() }
        val senderId = frame.optString("senderId").ifBlank { peerDeviceId }
        val senderName = frame.optString("senderName", fallbackPeerName).ifBlank { fallbackPeerName }
        val caption = frame.optString("caption", "").trim()
        val ts = frame.optLong("timestamp", System.currentTimeMillis()).takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val msg = ConnectChatMessage(
            id = id,
            peerDeviceId = peerDeviceId,
            senderDeviceId = senderId,
            senderName = senderName,
            type = ChatMessageType.IMAGE,
            text = caption,
            imageBase64 = imageBase64,
            timestampMillis = ts,
            isOutgoing = false
        )
        upsertMessage(peerDeviceId, msg, countUnread = activeChatPeerId != peerDeviceId)
        return msg
    }

    fun handleIncomingVoiceFrame(
        peerDeviceId: String,
        frame: JSONObject,
        fallbackPeerName: String
    ): ConnectChatMessage? {
        val audioBase64 = frame.optString("audioBase64").trim()
        if (audioBase64.isEmpty()) return null
        val id = frame.optString("messageId").ifBlank { UUID.randomUUID().toString() }
        val senderId = frame.optString("senderId").ifBlank { peerDeviceId }
        val senderName = frame.optString("senderName", fallbackPeerName).ifBlank { fallbackPeerName }
        val durationMs = frame.optLong("durationMs", 0L).coerceAtLeast(0L)
        val waveArray = frame.optJSONArray("waveform")
        val rawWave = if (waveArray != null) {
            List(waveArray.length()) { idx ->
                waveArray.optDouble(idx, 0.25).toFloat().coerceIn(0.12f, 1.0f)
            }
        } else {
            emptyList()
        }
        val normalizedWave = ArworaVoiceRecorder.resampleWaveform(rawWave)
        val ts = frame.optLong("timestamp", System.currentTimeMillis()).takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val msg = ConnectChatMessage(
            id = id,
            peerDeviceId = peerDeviceId,
            senderDeviceId = senderId,
            senderName = senderName,
            type = ChatMessageType.VOICE,
            audioBase64 = audioBase64,
            durationMs = durationMs,
            waveform = normalizedWave,
            timestampMillis = ts,
            isOutgoing = false
        )
        upsertMessage(peerDeviceId, msg, countUnread = activeChatPeerId != peerDeviceId)
        return msg
    }

    /**
     * Toggles a reaction locally for [actorDeviceId] and returns the wire packet JSON + whether selected.
     */
    fun toggleReaction(
        peerDeviceId: String,
        messageId: String,
        actorDeviceId: String,
        emoji: String
    ): Pair<Boolean, JSONObject>? {
        val cleanEmoji = emoji.trim()
        if (cleanEmoji.isEmpty()) return null
        var isNowSelected = false
        synchronized(this) {
            val map = _messagesByPeer.value.toMutableMap()
            val list = map[peerDeviceId] ?: return null
            val idx = list.indexOfFirst { it.id == messageId }
            if (idx < 0) return null

            val target = list[idx]
            val mutableReactions = target.reactions.mapValues { it.value.toMutableSet() }.toMutableMap()
            val actors = mutableReactions.getOrPut(cleanEmoji) { mutableSetOf() }
            if (actors.contains(actorDeviceId)) {
                actors.remove(actorDeviceId)
                isNowSelected = false
                if (actors.isEmpty()) mutableReactions.remove(cleanEmoji)
            } else {
                actors.add(actorDeviceId)
                isNowSelected = true
            }

            val updatedList = list.toMutableList()
            updatedList[idx] = target.copy(
                reactions = mutableReactions.mapValues { it.value.toSet() }
            )
            map[peerDeviceId] = updatedList
            _messagesByPeer.value = map
        }

        val packet = JSONObject().apply {
            put("type", TYPE_REACTION)
            put("messageId", messageId)
            put("senderId", actorDeviceId)
            put("emoji", cleanEmoji)
            put("selected", isNowSelected)
            put("timestamp", System.currentTimeMillis())
        }
        return isNowSelected to packet
    }

    fun handleIncomingReactionFrame(peerDeviceId: String, frame: JSONObject) {
        val messageId = frame.optString("messageId").trim()
        val emoji = frame.optString("emoji").trim()
        val senderId = frame.optString("senderId").ifBlank { peerDeviceId }
        val selected = frame.optBoolean("selected", true)
        if (messageId.isEmpty() || emoji.isEmpty()) return

        synchronized(this) {
            val map = _messagesByPeer.value.toMutableMap()
            val list = map[peerDeviceId] ?: return
            val idx = list.indexOfFirst { it.id == messageId }
            if (idx < 0) return

            val target = list[idx]
            val mutableReactions = target.reactions.mapValues { it.value.toMutableSet() }.toMutableMap()
            val actors = mutableReactions.getOrPut(emoji) { mutableSetOf() }
            if (selected) {
                actors.add(senderId)
            } else {
                actors.remove(senderId)
                if (actors.isEmpty()) mutableReactions.remove(emoji)
            }

            val updatedList = list.toMutableList()
            updatedList[idx] = target.copy(
                reactions = mutableReactions.mapValues { it.value.toSet() }
            )
            map[peerDeviceId] = updatedList
            _messagesByPeer.value = map
        }
        schedulePersistSnapshot()
    }

    fun pruneExpiredMessages(nowMillis: Long = nowProvider()): Int {
        val retentionMs = _retentionPolicy.value.retentionMillis ?: return 0
        val cutoff = nowMillis - retentionMs
        val expiredVoiceIds = mutableListOf<String>()
        var removedCount = 0

        synchronized(this) {
            val currentMap = _messagesByPeer.value
            if (currentMap.isEmpty()) return 0

            val nextMap = LinkedHashMap<String, List<ConnectChatMessage>>()
            val nextUnread = _unreadCountsByPeer.value.toMutableMap()
            var changed = false

            for ((peerId, list) in currentMap) {
                val kept = ArrayList<ConnectChatMessage>(list.size)
                for (msg in list) {
                    if (msg.timestampMillis > cutoff) {
                        kept.add(msg)
                    } else {
                        removedCount++
                        changed = true
                        if (msg.type == ChatMessageType.VOICE) {
                            expiredVoiceIds.add(msg.id)
                        }
                    }
                }
                if (kept.isNotEmpty()) {
                    nextMap[peerId] = kept
                    val currentUnread = nextUnread[peerId] ?: 0
                    val maxPossibleUnread = kept.count { !it.isOutgoing }
                    if (currentUnread > maxPossibleUnread) {
                        if (maxPossibleUnread <= 0) nextUnread.remove(peerId)
                        else nextUnread[peerId] = maxPossibleUnread
                    }
                } else {
                    nextUnread.remove(peerId)
                }
            }

            if (changed) {
                _messagesByPeer.value = nextMap
                _unreadCountsByPeer.value = nextUnread
            }
        }

        if (removedCount > 0) {
            cleanupVoiceCacheFiles(expiredVoiceIds)
            schedulePersistSnapshot()
        }
        return removedCount
    }

    fun clearPeerChat(peerDeviceId: String) {
        val removedVoiceIds = mutableListOf<String>()
        synchronized(this) {
            val map = _messagesByPeer.value.toMutableMap()
            map.remove(peerDeviceId).orEmpty()
                .filter { it.type == ChatMessageType.VOICE }
                .forEach { removedVoiceIds.add(it.id) }
            _messagesByPeer.value = map

            val unread = _unreadCountsByPeer.value.toMutableMap()
            unread.remove(peerDeviceId)
            _unreadCountsByPeer.value = unread
        }
        cleanupVoiceCacheFiles(removedVoiceIds)
        schedulePersistSnapshot()
    }

    fun clearAll() {
        isShutDown = true
        ioScope.cancel()
        synchronized(this) {
            activeChatPeerId = null
            _messagesByPeer.value = emptyMap()
            _unreadCountsByPeer.value = emptyMap()
        }
        runCatching { historyFile?.delete() }
    }

    private fun upsertMessage(
        peerDeviceId: String,
        message: ConnectChatMessage,
        countUnread: Boolean
    ) {
        val now = nowProvider()
        val retentionMs = _retentionPolicy.value.retentionMillis
        val cutoff = if (retentionMs != null) now - retentionMs else Long.MIN_VALUE
        if (retentionMs != null && message.timestampMillis <= cutoff) {
            return
        }

        val expiredVoiceIds = mutableListOf<String>()
        synchronized(this) {
            val map = _messagesByPeer.value.toMutableMap()
            val current = map[peerDeviceId].orEmpty().toMutableList()
            if (retentionMs != null) {
                val iterator = current.iterator()
                while (iterator.hasNext()) {
                    val existing = iterator.next()
                    if (existing.timestampMillis <= cutoff) {
                        if (existing.type == ChatMessageType.VOICE) {
                            expiredVoiceIds.add(existing.id)
                        }
                        iterator.remove()
                    }
                }
            }
            val existingIdx = current.indexOfFirst { it.id == message.id }
            if (existingIdx >= 0) {
                current[existingIdx] = message
            } else {
                current.add(message)
            }
            map[peerDeviceId] = if (current.size > MAX_MESSAGES_PER_PEER) {
                current.takeLast(MAX_MESSAGES_PER_PEER)
            } else {
                current
            }
            _messagesByPeer.value = map

            if (countUnread && existingIdx < 0) {
                val unread = _unreadCountsByPeer.value.toMutableMap()
                unread[peerDeviceId] = (unread[peerDeviceId] ?: 0) + 1
                _unreadCountsByPeer.value = unread
            }
        }
        cleanupVoiceCacheFiles(expiredVoiceIds)
        schedulePersistSnapshot()
    }

    private fun cleanupVoiceCacheFiles(messageIds: List<String>) {
        if (messageIds.isEmpty() || isShutDown) return
        ioScope.launch {
            val cacheDir = File(appContext.cacheDir, VOICE_CACHE_DIR)
            if (!cacheDir.exists()) return@launch
            for (id in messageIds) {
                val safeName = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                runCatching { File(cacheDir, "$safeName.m4a").delete() }
            }
        }
    }

    private fun loadPersistedHistoryAsync() {
        val file = historyFile ?: return
        ioScope.launch {
            val loaded = fileMutex.withLock {
                if (isShutDown || !file.exists()) return@withLock emptyMap()
                runCatching {
                    val raw = file.readText()
                    if (raw.isBlank()) emptyMap() else deserializeMessagesFromJson(raw)
                }.getOrDefault(emptyMap())
            }
            if (isShutDown || loaded.isEmpty()) return@launch

            val now = nowProvider()
            val retentionMs = _retentionPolicy.value.retentionMillis
            val cutoff = if (retentionMs != null) now - retentionMs else Long.MIN_VALUE
            var prunedAnyOnLoad = false

            synchronized(this@ArworaConnectChatManager) {
                if (isShutDown) return@synchronized
                val merged = _messagesByPeer.value.toMutableMap()
                for ((peerId, persistedList) in loaded) {
                    val validPersisted = persistedList.filter { msg ->
                        val keep = retentionMs == null || msg.timestampMillis > cutoff
                        if (!keep) prunedAnyOnLoad = true
                        keep
                    }
                    if (validPersisted.isEmpty()) continue
                    val current = merged[peerId].orEmpty()
                    val existingIds = current.map { it.id }.toSet()
                    val combined = (validPersisted.filterNot { it.id in existingIds } + current)
                        .sortedBy { it.timestampMillis }
                        .takeLast(MAX_MESSAGES_PER_PEER)
                    merged[peerId] = combined
                }
                _messagesByPeer.value = merged
            }
            if (prunedAnyOnLoad) {
                schedulePersistSnapshot()
            }
        }
    }

    private fun schedulePersistSnapshot() {
        val file = historyFile ?: return
        if (isShutDown) return
        val snapshot = synchronized(this) { _messagesByPeer.value }
        ioScope.launch {
            fileMutex.withLock {
                if (isShutDown) return@withLock
                runCatching {
                    if (snapshot.isEmpty()) {
                        if (file.exists()) file.delete()
                    } else {
                        file.parentFile?.let { if (!it.exists()) it.mkdirs() }
                        file.writeText(serializeMessagesToJson(snapshot))
                    }
                }
            }
        }
    }

    suspend fun compressImageUriToBase64(uri: Uri): String? = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_DIMENSION_PX)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: return@withContext null

        try {
            compressBitmapToBase64(decoded)
        } finally {
            decoded.recycle()
        }
    }

    companion object {
        const val TYPE_CHAT = "chat"
        const val TYPE_IMAGE = "image"
        const val TYPE_VOICE = "voice"
        const val TYPE_REACTION = "reaction"

        const val HISTORY_FILE_NAME = "arwora_connect_chat_history.json"
        private const val VOICE_CACHE_DIR = "arwora_voice_cache"
        private const val MAX_MESSAGES_PER_PEER = 150
        private const val MAX_IMAGE_DIMENSION_PX = 1024
        private const val MAX_COMPRESSED_BYTES = 350_000

        internal fun serializeMessagesToJson(messagesByPeer: Map<String, List<ConnectChatMessage>>): String {
            val root = JSONObject()
            for ((peerId, list) in messagesByPeer) {
                val arr = JSONArray()
                list.forEach { msg ->
                    arr.put(
                        JSONObject().apply {
                            put("id", msg.id)
                            put("peerDeviceId", msg.peerDeviceId)
                            put("senderDeviceId", msg.senderDeviceId)
                            put("senderName", msg.senderName)
                            put("type", msg.type.name)
                            put("text", msg.text)
                            if (!msg.imageBase64.isNullOrBlank()) put("imageBase64", msg.imageBase64)
                            if (!msg.audioBase64.isNullOrBlank()) put("audioBase64", msg.audioBase64)
                            put("durationMs", msg.durationMs)
                            if (msg.waveform.isNotEmpty()) {
                                put("waveform", JSONArray(msg.waveform.map { it.toDouble() }))
                            }
                            put("timestampMillis", msg.timestampMillis)
                            put("isOutgoing", msg.isOutgoing)
                            if (msg.reactions.isNotEmpty()) {
                                val reactObj = JSONObject()
                                msg.reactions.forEach { (emoji, actors) ->
                                    reactObj.put(emoji, JSONArray(actors.toList()))
                                }
                                put("reactions", reactObj)
                            }
                        }
                    )
                }
                root.put(peerId, arr)
            }
            return root.toString()
        }

        internal fun deserializeMessagesFromJson(raw: String): Map<String, List<ConnectChatMessage>> {
            val root = JSONObject(raw)
            val result = LinkedHashMap<String, List<ConnectChatMessage>>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val peerId = keys.next()
                val arr = root.optJSONArray(peerId) ?: continue
                val list = ArrayList<ConnectChatMessage>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    if (id.isEmpty()) continue
                    val type = runCatching {
                        ChatMessageType.valueOf(obj.optString("type", ChatMessageType.TEXT.name))
                    }.getOrDefault(ChatMessageType.TEXT)
                    val waveArr = obj.optJSONArray("waveform")
                    val wave = if (waveArr != null) {
                        List(waveArr.length()) { idx -> waveArr.optDouble(idx, 0.2).toFloat() }
                    } else {
                        emptyList()
                    }
                    val reactObj = obj.optJSONObject("reactions")
                    val reactions = LinkedHashMap<String, Set<String>>()
                    if (reactObj != null) {
                        val rKeys = reactObj.keys()
                        while (rKeys.hasNext()) {
                            val emoji = rKeys.next()
                            val actorArr = reactObj.optJSONArray(emoji) ?: continue
                            val set = LinkedHashSet<String>()
                            for (j in 0 until actorArr.length()) {
                                val actor = actorArr.optString(j)
                                if (actor.isNotBlank()) set.add(actor)
                            }
                            if (set.isNotEmpty()) reactions[emoji] = set
                        }
                    }
                    list.add(
                        ConnectChatMessage(
                            id = id,
                            peerDeviceId = obj.optString("peerDeviceId", peerId),
                            senderDeviceId = obj.optString("senderDeviceId", peerId),
                            senderName = obj.optString("senderName", "Arwora Peer"),
                            type = type,
                            text = obj.optString("text", ""),
                            imageBase64 = obj.optString("imageBase64").takeIf { it.isNotBlank() },
                            audioBase64 = obj.optString("audioBase64").takeIf { it.isNotBlank() },
                            durationMs = obj.optLong("durationMs", 0L),
                            waveform = wave,
                            timestampMillis = obj.optLong("timestampMillis", 0L),
                            isOutgoing = obj.optBoolean("isOutgoing", false),
                            reactions = reactions
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    result[peerId] = list
                }
            }
            return result
        }

        fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
            var sample = 1
            var longest = maxOf(width, height)
            while (longest / 2 >= maxEdge) {
                sample *= 2
                longest /= 2
            }
            return sample.coerceAtLeast(1)
        }

        fun compressBitmapToBase64(source: Bitmap): String {
            val maxSide = maxOf(source.width, source.height)
            val scaled: Bitmap = if (maxSide > MAX_IMAGE_DIMENSION_PX && maxSide > 0) {
                val ratio = MAX_IMAGE_DIMENSION_PX.toFloat() / maxSide.toFloat()
                val targetW = (source.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (source.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(source, targetW, targetH, true)
            } else {
                source
            }

            return try {
                val qualities = intArrayOf(82, 68, 54, 40, 28)
                var bestBytes = ByteArray(0)
                for (q in qualities) {
                    val baos = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, q, baos)
                    bestBytes = baos.toByteArray()
                    if (bestBytes.size <= MAX_COMPRESSED_BYTES) break
                }
                Base64.encodeToString(bestBytes, Base64.NO_WRAP)
            } finally {
                if (scaled !== source) {
                    scaled.recycle()
                }
            }
        }

        fun decodeBase64ToBitmap(base64: String?): Bitmap? {
            if (base64.isNullOrBlank()) return null
            return try {
                val raw = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(raw, 0, raw.size)
            } catch (_: Exception) {
                null
            }
        }
    }
}
