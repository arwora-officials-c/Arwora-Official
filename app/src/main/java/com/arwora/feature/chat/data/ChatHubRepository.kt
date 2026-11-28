package com.arwora.feature.chat.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayOutputStream
import com.arwora.feature.chat.model.ChatHubSettings
import com.arwora.feature.chat.model.ChatMessage
import com.arwora.feature.chat.model.ChatMessageType
import com.arwora.feature.chat.model.ChatStickerCatalog
import com.arwora.feature.chat.model.ChatThread
import com.arwora.feature.chat.model.MessageDeliveryStatus
import com.arwora.feature.chat.model.NearChatPeer
import com.arwora.feature.system.connect.data.ArworaConnectEngine
import com.arwora.feature.system.connect.data.ArworaConnectManager
import com.arwora.feature.system.connect.data.ConnectNetworkUtils
import com.arwora.feature.system.connect.model.P2pMessageRetentionPolicy
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Central Chat Hub data repository managing SQLCipher-encrypted threads/messages,
 * P2P frame sync over ArworaConnectEngine, Ghost Mode, View Once destruction,
 * WorkManager scheduled messages, search/date filters, and retention pruning.
 */
class ChatHubRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: ChatEncryptedDatabase = ChatEncryptedDatabase.getInstance(appContext)
    val dao: ChatDao = db.chatDao()
    val preferencesStore = ChatPreferencesStore(appContext)
    val connectEngine: ArworaConnectEngine = ArworaConnectManager.getEngine(appContext)
    val nearTransport = NearChatTransportManager(appContext, connectEngine)

    val settings: StateFlow<ChatHubSettings> = preferencesStore.settings
    val nearPeers: StateFlow<List<NearChatPeer>> = nearTransport.nearPeers

    private val _typingThreads = MutableStateFlow<Set<String>>(emptySet())
    val typingThreads: StateFlow<Set<String>> = _typingThreads.asStateFlow()

    init {
        ArworaConnectManager.syncPreferences(appContext)
        connectEngine.onChatHubFrame = { peerDeviceId, frame ->
            scope.launch {
                handleIncomingP2pFrame(peerDeviceId, frame)
            }
        }
        scope.launch {
            connectEngine.peers.collect { peerStates ->
                syncConnectedPeersIntoThreads(peerStates.map { it.peer.deviceId to (it.connectionState == com.arwora.feature.system.connect.model.PeerConnectionState.CONNECTED) })
            }
        }
    }

    fun observeAllThreads(): Flow<List<ChatThread>> {
        return dao.observeAllThreads().map { list -> list.map { it.toDomain() } }
    }

    fun observeThread(threadId: String): Flow<ChatThread?> {
        return dao.observeThread(threadId).map { it?.toDomain() }
    }

    fun observeMessagesForThread(threadId: String): Flow<List<ChatMessage>> {
        return dao.observeMessagesForThread(threadId).map { list -> list.map { it.toDomain() } }
    }

    suspend fun getOrCreatePeerThread(peer: NearChatPeer): ChatThread {
        dao.getThreadByPeerDeviceId(peer.id)?.let { return it.toDomain() }
        val defaults = settings.value
        val thread = ChatThread(
            id = "peer_${peer.id}",
            title = peer.displayName,
            isGroup = false,
            participantIds = listOf(peer.id),
            participantNames = listOf(peer.displayName),
            peerDeviceId = peer.id,
            themeId = defaults.defaultThemeId,
            wallpaperPresetOrUri = defaults.defaultWallpaperId,
            isOnline = peer.isConnected,
            e2eeFingerprint = peer.id.takeLast(12).uppercase(Locale.US)
        )
        dao.upsertThread(ChatThreadEntity.fromDomain(thread))
        return thread
    }

    suspend fun createDirectThread(contactName: String, peerDeviceId: String? = null): ChatThread {
        val cleanName = contactName.trim().ifBlank { "New Contact" }
        val defaults = settings.value
        val id = if (!peerDeviceId.isNullOrBlank()) "peer_$peerDeviceId" else "dm_${UUID.randomUUID()}"
        val existing = dao.getThreadById(id)
        if (existing != null) return existing.toDomain()
        val thread = ChatThread(
            id = id,
            title = cleanName,
            isGroup = false,
            participantIds = listOfNotNull(peerDeviceId),
            participantNames = listOf(cleanName),
            peerDeviceId = peerDeviceId,
            themeId = defaults.defaultThemeId,
            wallpaperPresetOrUri = defaults.defaultWallpaperId,
            isOnline = peerDeviceId != null && connectEngine.peers.value.any {
                it.peer.deviceId == peerDeviceId &&
                    it.connectionState == com.arwora.feature.system.connect.model.PeerConnectionState.CONNECTED
            },
            e2eeFingerprint = UUID.randomUUID().toString().replace("-", "").take(12).uppercase(Locale.US)
        )
        dao.upsertThread(ChatThreadEntity.fromDomain(thread))
        return thread
    }

    suspend fun createGroupThread(groupTitle: String, members: List<String>): ChatThread {
        val cleanTitle = groupTitle.trim().ifBlank { "New Group" }
        val cleanMembers = members.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val defaults = settings.value
        val thread = ChatThread(
            id = "grp_${UUID.randomUUID()}",
            title = cleanTitle,
            isGroup = true,
            participantIds = cleanMembers.map { "member_${it.lowercase(Locale.US)}" },
            participantNames = cleanMembers,
            themeId = defaults.defaultThemeId,
            wallpaperPresetOrUri = defaults.defaultWallpaperId,
            isOnline = true,
            e2eeFingerprint = UUID.randomUUID().toString().replace("-", "").take(12).uppercase(Locale.US)
        )
        dao.upsertThread(ChatThreadEntity.fromDomain(thread))
        return thread
    }

    suspend fun updateThread(threadId: String, transform: (ChatThread) -> ChatThread) {
        val existing = dao.getThreadById(threadId)?.toDomain() ?: return
        dao.upsertThread(ChatThreadEntity.fromDomain(transform(existing)))
    }

    suspend fun deleteThread(threadId: String) {
        val messages = dao.getMessagesForThread(threadId)
        messages.forEach { msg ->
            msg.mediaPathOrUri?.let { path ->
                if (path.startsWith(appContext.filesDir.absolutePath) || path.startsWith(appContext.cacheDir.absolutePath)) {
                    runCatching { File(path).delete() }
                }
            }
        }
        dao.deleteMessagesForThread(threadId)
        dao.deleteThread(threadId)
    }

    suspend fun markThreadOpened(threadId: String) {
        dao.clearUnreadCount(threadId)
        val thread = dao.getThreadById(threadId)?.toDomain() ?: return
        val ghostMode = settings.value.ghostModeEnabled
        if (!ghostMode && !thread.peerDeviceId.isNullOrBlank()) {
            val packet = JSONObject().apply {
                put("type", FRAME_SEEN)
                put("threadId", threadId)
                put("seenAt", System.currentTimeMillis())
            }
            connectEngine.sendChatHubPacket(thread.peerDeviceId, packet)
        }
    }

    suspend fun sendTypingSignal(threadId: String, isTyping: Boolean) {
        if (settings.value.ghostModeEnabled) return
        val thread = dao.getThreadById(threadId)?.toDomain() ?: return
        val peerId = thread.peerDeviceId ?: return
        val packet = JSONObject().apply {
            put("type", FRAME_TYPING)
            put("threadId", threadId)
            put("isTyping", isTyping)
        }
        connectEngine.sendChatHubPacket(peerId, packet)
    }

    suspend fun sendTextMessage(
        threadId: String,
        text: String,
        replyTo: ChatMessage? = null,
        scheduledForMillis: Long? = null
    ): Result<ChatMessage> {
        val clean = text.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Message cannot be empty"))
        return createAndDispatchMessage(
            threadId = threadId,
            type = ChatMessageType.TEXT,
            text = clean,
            replyTo = replyTo,
            scheduledForMillis = scheduledForMillis
        )
    }

    suspend fun sendStickerMessage(
        threadId: String,
        stickerId: String,
        replyTo: ChatMessage? = null
    ): Result<ChatMessage> {
        val sticker = ChatStickerCatalog.byId(stickerId)
            ?: return Result.failure(IllegalArgumentException("Unknown sticker"))
        return createAndDispatchMessage(
            threadId = threadId,
            type = ChatMessageType.STICKER,
            text = "${sticker.emoji} ${sticker.caption}",
            stickerId = sticker.id,
            replyTo = replyTo
        )
    }

    suspend fun sendVoiceMessage(
        threadId: String,
        audioBase64: String,
        durationMs: Long,
        waveform: List<Float>,
        replyTo: ChatMessage? = null
    ): Result<ChatMessage> {
        return createAndDispatchMessage(
            threadId = threadId,
            type = ChatMessageType.VOICE,
            mediaBase64 = audioBase64,
            mediaMimeType = "audio/mp4",
            durationMs = durationMs,
            waveform = waveform,
            replyTo = replyTo
        )
    }

    suspend fun sendImageMessage(
        threadId: String,
        uri: Uri,
        caption: String = "",
        isViewOnce: Boolean = false,
        replyTo: ChatMessage? = null,
        scheduledForMillis: Long? = null
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        val compressAggressive = preferencesStore.isLowBatteryOrDataSaverActive()
        val maxDim = if (compressAggressive) 720 else 1280
        val quality = if (compressAggressive) 58 else 78
        val encoded = compressImageUriForChat(uri, maxDimension = maxDim, quality = quality)
            ?: return@withContext Result.failure(IllegalStateException("Unable to read selected photo"))
        val cachedFile = copyUriToChatMediaCache(uri, "img", "jpg")
        createAndDispatchMessage(
            threadId = threadId,
            type = ChatMessageType.IMAGE,
            text = caption.trim(),
            mediaPathOrUri = cachedFile?.absolutePath ?: uri.toString(),
            mediaBase64 = encoded,
            mediaMimeType = "image/jpeg",
            fileName = resolveDisplayName(uri) ?: "photo.jpg",
            fileSizeBytes = cachedFile?.length() ?: 0L,
            isViewOnce = isViewOnce,
            replyTo = replyTo,
            scheduledForMillis = scheduledForMillis
        )
    }

    suspend fun sendVideoMessage(
        threadId: String,
        uri: Uri,
        caption: String = "",
        isViewOnce: Boolean = false,
        replyTo: ChatMessage? = null
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        val meta = ConnectNetworkUtils.readOutboundFileMeta(appContext, uri)
        val cachedFile = copyUriToChatMediaCache(uri, "vid", "mp4")
        val targetPath = cachedFile?.absolutePath ?: uri.toString()
        val peerThread = dao.getThreadById(threadId)?.toDomain()
        if (peerThread?.peerDeviceId != null && !preferencesStore.shouldBlockHeavyAutoDownload()) {
            connectEngine.broadcastFilesToSelectedPeers(listOf(uri), targetPeerIds = listOf(peerThread.peerDeviceId))
        }
        createAndDispatchMessage(
            threadId = threadId,
            type = ChatMessageType.VIDEO,
            text = caption.trim(),
            mediaPathOrUri = targetPath,
            mediaMimeType = appContext.contentResolver.getType(uri) ?: "video/mp4",
            fileName = meta?.displayName ?: resolveDisplayName(uri) ?: "video.mp4",
            fileSizeBytes = meta?.sizeBytes ?: cachedFile?.length() ?: 0L,
            isViewOnce = isViewOnce,
            replyTo = replyTo
        )
    }

    suspend fun sendFileMessage(
        threadId: String,
        uri: Uri,
        replyTo: ChatMessage? = null
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        val meta = ConnectNetworkUtils.readOutboundFileMeta(appContext, uri)
        val name = meta?.displayName ?: resolveDisplayName(uri) ?: "attachment.bin"
        val ext = name.substringAfterLast('.', "bin").take(8)
        val cachedFile = copyUriToChatMediaCache(uri, "doc", ext)
        val peerThread = dao.getThreadById(threadId)?.toDomain()
        if (peerThread?.peerDeviceId != null) {
            connectEngine.broadcastFilesToSelectedPeers(listOf(uri), targetPeerIds = listOf(peerThread.peerDeviceId))
        }
        createAndDispatchMessage(
            threadId = threadId,
            type = ChatMessageType.FILE,
            text = name,
            mediaPathOrUri = cachedFile?.absolutePath ?: uri.toString(),
            mediaMimeType = appContext.contentResolver.getType(uri) ?: "application/octet-stream",
            fileName = name,
            fileSizeBytes = meta?.sizeBytes ?: cachedFile?.length() ?: 0L,
            replyTo = replyTo
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun sendCurrentGpsLocation(
        threadId: String,
        replyTo: ChatMessage? = null
    ): Result<ChatMessage> = withContext(Dispatchers.IO) {
        val loc = fetchBestLastLocation()
            ?: return@withContext Result.failure(IllegalStateException("GPS location unavailable. Enable location services and try again."))
        val label = reverseGeocodeShort(loc.latitude, loc.longitude)
            ?: String.format(Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude)
        createAndDispatchMessage(
            threadId = threadId,
            type = ChatMessageType.LOCATION,
            text = label,
            latitude = loc.latitude,
            longitude = loc.longitude,
            locationLabel = label,
            replyTo = replyTo
        )
    }

    suspend fun sendLocationCoordinates(
        threadId: String,
        latitude: Double,
        longitude: Double,
        label: String,
        replyTo: ChatMessage? = null
    ): Result<ChatMessage> {
        return createAndDispatchMessage(
            threadId = threadId,
            type = ChatMessageType.LOCATION,
            text = label,
            latitude = latitude,
            longitude = longitude,
            locationLabel = label,
            replyTo = replyTo
        )
    }

    suspend fun createAndDispatchMessage(
        threadId: String,
        type: ChatMessageType,
        text: String = "",
        mediaPathOrUri: String? = null,
        mediaBase64: String? = null,
        mediaMimeType: String? = null,
        fileName: String? = null,
        fileSizeBytes: Long = 0L,
        durationMs: Long = 0L,
        waveform: List<Float> = emptyList(),
        latitude: Double? = null,
        longitude: Double? = null,
        locationLabel: String? = null,
        stickerId: String? = null,
        isViewOnce: Boolean = false,
        replyTo: ChatMessage? = null,
        forwardedFrom: String? = null,
        scheduledForMillis: Long? = null
    ): Result<ChatMessage> {
        val thread = dao.getThreadById(threadId)?.toDomain()
            ?: return Result.failure(IllegalArgumentException("Chat thread not found"))
        val now = System.currentTimeMillis()
        val isScheduled = scheduledForMillis != null && scheduledForMillis > now + 1_000L
        val identity = connectEngine.localIdentity.value
        val msgId = UUID.randomUUID().toString()
        val initialStatus = if (isScheduled) MessageDeliveryStatus.SCHEDULED else MessageDeliveryStatus.SENT
        val message = ChatMessage(
            id = msgId,
            threadId = threadId,
            senderId = identity.deviceId.ifBlank { "local_self" },
            senderName = identity.deviceName.ifBlank { "Me" },
            isOutgoing = true,
            type = type,
            text = text,
            mediaPathOrUri = mediaPathOrUri,
            mediaBase64 = mediaBase64,
            mediaMimeType = mediaMimeType,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            durationMs = durationMs,
            waveform = waveform,
            latitude = latitude,
            longitude = longitude,
            locationLabel = locationLabel,
            stickerId = stickerId,
            replyToMessageId = replyTo?.id,
            replyPreviewText = replyTo?.displaySummary(),
            replySenderName = replyTo?.senderName,
            forwardedFrom = forwardedFrom,
            isViewOnce = isViewOnce,
            deliveryStatus = initialStatus,
            scheduledForMillis = if (isScheduled) scheduledForMillis else null,
            timestampMillis = if (isScheduled) scheduledForMillis!! else now
        )

        dao.upsertMessage(ChatMessageEntity.fromDomain(message))

        if (isScheduled) {
            ScheduledMessageWorker.enqueue(appContext, msgId, scheduledForMillis!!)
            return Result.success(message)
        }

        return finalizeImmediateSend(thread, message)
    }

    suspend fun dispatchScheduledMessageNow(messageId: String): Boolean {
        val entity = dao.getMessageById(messageId) ?: return false
        val msg = entity.toDomain()
        if (msg.deliveryStatus != MessageDeliveryStatus.SCHEDULED) return false
        val thread = dao.getThreadById(msg.threadId)?.toDomain() ?: return false
        val now = System.currentTimeMillis()
        val ready = msg.copy(
            deliveryStatus = MessageDeliveryStatus.SENT,
            scheduledForMillis = null,
            timestampMillis = now
        )
        dao.upsertMessage(ChatMessageEntity.fromDomain(ready))
        finalizeImmediateSend(thread, ready)
        return true
    }

    suspend fun cancelScheduledMessage(messageId: String) {
        ScheduledMessageWorker.cancel(appContext, messageId)
        dao.deleteMessageById(messageId)
    }

    private suspend fun finalizeImmediateSend(thread: ChatThread, message: ChatMessage): Result<ChatMessage> {
        var finalMsg = message
        val peerId = thread.peerDeviceId
        if (!peerId.isNullOrBlank()) {
            val packet = encodeChatHubMessagePacket(thread, message)
            val sendRes = connectEngine.sendChatHubPacket(peerId, packet)
            if (sendRes.isSuccess) {
                finalMsg = message.copy(
                    deliveryStatus = MessageDeliveryStatus.DELIVERED,
                    deliveredAtMillis = System.currentTimeMillis()
                )
                dao.upsertMessage(ChatMessageEntity.fromDomain(finalMsg))
            }
        }
        dao.upsertThread(
            ChatThreadEntity.fromDomain(
                thread.copy(
                    lastMessagePreview = finalMsg.displaySummary(),
                    lastMessageTimeMillis = finalMsg.timestampMillis
                )
            )
        )
        return Result.success(finalMsg)
    }

    suspend fun toggleReaction(threadId: String, messageId: String, emoji: String) {
        val entity = dao.getMessageById(messageId) ?: return
        val msg = entity.toDomain()
        val myId = connectEngine.localIdentity.value.deviceId.ifBlank { "local_self" }
        val mutable = msg.reactions.mapValues { it.value.toMutableSet() }.toMutableMap()
        val actors = mutable.getOrPut(emoji) { mutableSetOf() }
        val selected = if (actors.contains(myId)) {
            actors.remove(myId)
            if (actors.isEmpty()) mutable.remove(emoji)
            false
        } else {
            actors.add(myId)
            true
        }
        dao.upsertMessage(
            ChatMessageEntity.fromDomain(
                msg.copy(reactions = mutable.mapValues { it.value.toSet() })
            )
        )
        val thread = dao.getThreadById(threadId)?.toDomain() ?: return
        thread.peerDeviceId?.let { peerId ->
            val packet = JSONObject().apply {
                put("type", FRAME_REACTION)
                put("messageId", messageId)
                put("actorId", myId)
                put("emoji", emoji)
                put("selected", selected)
            }
            connectEngine.sendChatHubPacket(peerId, packet)
        }
    }

    suspend fun editMessage(threadId: String, messageId: String, newText: String): Boolean {
        val clean = newText.trim()
        if (clean.isEmpty()) return false
        val msg = dao.getMessageById(messageId)?.toDomain() ?: return false
        if (!msg.isOutgoing || msg.isDeletedForEveryone || msg.type != ChatMessageType.TEXT) return false
        val editedAt = System.currentTimeMillis()
        val updated = msg.copy(text = clean, isEdited = true, editedAtMillis = editedAt)
        dao.upsertMessage(ChatMessageEntity.fromDomain(updated))

        val thread = dao.getThreadById(threadId)?.toDomain()
        if (thread != null) {
            refreshThreadLastPreview(threadId)
            thread.peerDeviceId?.let { peerId ->
                connectEngine.sendChatHubPacket(
                    peerId,
                    JSONObject().apply {
                        put("type", FRAME_EDIT)
                        put("messageId", messageId)
                        put("newText", clean)
                        put("editedAt", editedAt)
                    }
                )
            }
        }
        return true
    }

    suspend fun deleteForEveryone(threadId: String, messageId: String) {
        val msg = dao.getMessageById(messageId)?.toDomain() ?: return
        if (msg.deliveryStatus == MessageDeliveryStatus.SCHEDULED) {
            cancelScheduledMessage(messageId)
            return
        }
        msg.mediaPathOrUri?.let { path ->
            if (path.startsWith(appContext.filesDir.absolutePath) || path.startsWith(appContext.cacheDir.absolutePath)) {
                runCatching { File(path).delete() }
            }
        }
        val tomb = msg.copy(
            text = "This message was deleted",
            mediaBase64 = null,
            mediaPathOrUri = null,
            fileName = null,
            isDeletedForEveryone = true,
            reactions = emptyMap()
        )
        dao.upsertMessage(ChatMessageEntity.fromDomain(tomb))
        refreshThreadLastPreview(threadId)

        val thread = dao.getThreadById(threadId)?.toDomain()
        thread?.peerDeviceId?.let { peerId ->
            connectEngine.sendChatHubPacket(
                peerId,
                JSONObject().apply {
                    put("type", FRAME_DELETE_EVERYONE)
                    put("messageId", messageId)
                }
            )
        }
    }

    suspend fun forwardMessage(sourceMessage: ChatMessage, targetThreadId: String): Result<ChatMessage> {
        return createAndDispatchMessage(
            threadId = targetThreadId,
            type = sourceMessage.type,
            text = sourceMessage.text,
            mediaPathOrUri = sourceMessage.mediaPathOrUri,
            mediaBase64 = sourceMessage.mediaBase64,
            mediaMimeType = sourceMessage.mediaMimeType,
            fileName = sourceMessage.fileName,
            fileSizeBytes = sourceMessage.fileSizeBytes,
            durationMs = sourceMessage.durationMs,
            waveform = sourceMessage.waveform,
            latitude = sourceMessage.latitude,
            longitude = sourceMessage.longitude,
            locationLabel = sourceMessage.locationLabel,
            stickerId = sourceMessage.stickerId,
            forwardedFrom = sourceMessage.senderName
        )
    }

    suspend fun consumeViewOnceMedia(messageId: String) {
        val msg = dao.getMessageById(messageId)?.toDomain() ?: return
        if (!msg.isViewOnce || msg.viewOnceConsumed) return
        msg.mediaPathOrUri?.let { path ->
            if (path.startsWith(appContext.filesDir.absolutePath) || path.startsWith(appContext.cacheDir.absolutePath)) {
                runCatching { File(path).delete() }
            }
        }
        val wiped = msg.copy(
            viewOnceConsumed = true,
            mediaBase64 = null,
            mediaPathOrUri = null,
            text = ""
        )
        dao.upsertMessage(ChatMessageEntity.fromDomain(wiped))
    }

    suspend fun recordScreenshotDetected(threadId: String, actorName: String = "You") {
        if (!settings.value.screenshotAlertsEnabled) return
        val thread = dao.getThreadById(threadId)?.toDomain() ?: return
        val alertMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            threadId = threadId,
            senderId = "system",
            senderName = "Security",
            isOutgoing = false,
            type = ChatMessageType.SYSTEM_ALERT,
            text = "$actorName took a screenshot of this chat",
            deliveryStatus = MessageDeliveryStatus.SEEN,
            timestampMillis = System.currentTimeMillis()
        )
        dao.upsertMessage(ChatMessageEntity.fromDomain(alertMsg))
        thread.peerDeviceId?.let { peerId ->
            val myName = connectEngine.localIdentity.value.deviceName.ifBlank { "Peer" }
            connectEngine.sendChatHubPacket(
                peerId,
                JSONObject().apply {
                    put("type", FRAME_SCREENSHOT_ALERT)
                    put("threadId", threadId)
                    put("actorName", myName)
                }
            )
        }
    }

    suspend fun searchGlobalMessages(query: String, includeHidden: Boolean): List<ChatMessage> {
        val clean = query.trim()
        if (clean.isEmpty()) return emptyList()
        val raw = dao.searchMessagesGlobal(clean).map { it.toDomain() }
        if (includeHidden) return raw
        return raw.filter { msg -> dao.getThreadById(msg.threadId)?.isHidden == false }
    }

    private fun compressImageUriForChat(uri: Uri, maxDimension: Int, quality: Int): String? {
        return runCatching {
            val resolver = appContext.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var inSample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / inSample > maxDimension * 2) {
                inSample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = inSample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            try {
                val scale = minOf(
                    maxDimension.toFloat() / decoded.width.coerceAtLeast(1),
                    maxDimension.toFloat() / decoded.height.coerceAtLeast(1),
                    1f
                )
                val scaled = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        decoded,
                        (decoded.width * scale).toInt().coerceAtLeast(1),
                        (decoded.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    decoded
                }
                try {
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(30, 95), out)
                    Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                } finally {
                    if (scaled !== decoded) scaled.recycle()
                }
            } finally {
                decoded.recycle()
            }
        }.getOrNull()
    }

    suspend fun searchInThread(threadId: String, query: String, dayMillis: Long?): List<ChatMessage> {
        val (startMs, endMs) = if (dayMillis != null) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = dayMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            val end = start + 24L * 60L * 60L * 1000L - 1L
            start to end
        } else {
            null to null
        }
        return dao.searchInThread(
            threadId = threadId,
            query = query.trim(),
            startMs = startMs,
            endMs = endMs
        ).map { it.toDomain() }
    }

    suspend fun applyRetentionPolicy(policy: P2pMessageRetentionPolicy): Int {
        ArworaConnectManager.setMessageRetentionPolicy(appContext, policy)
        val ms = policy.retentionMillis ?: return 0
        val cutoff = System.currentTimeMillis() - ms
        return dao.deleteMessagesOlderThan(cutoff)
    }

    private suspend fun refreshThreadLastPreview(threadId: String) {
        val thread = dao.getThreadById(threadId)?.toDomain() ?: return
        val latest = dao.getMessagesForThread(threadId)
            .lastOrNull { it.deliveryStatus != MessageDeliveryStatus.SCHEDULED.storageValue }
            ?.toDomain()
        dao.upsertThread(
            ChatThreadEntity.fromDomain(
                thread.copy(
                    lastMessagePreview = latest?.displaySummary() ?: "",
                    lastMessageTimeMillis = latest?.timestampMillis ?: thread.lastMessageTimeMillis
                )
            )
        )
    }

    private suspend fun syncConnectedPeersIntoThreads(states: List<Pair<String, Boolean>>) {
        states.forEach { (peerId, connected) ->
            val existing = dao.getThreadByPeerDeviceId(peerId)?.toDomain()
            if (existing != null && existing.isOnline != connected) {
                dao.upsertThread(
                    ChatThreadEntity.fromDomain(
                        existing.copy(
                            isOnline = connected,
                            lastSeenMillis = System.currentTimeMillis()
                        )
                    )
                )
            }
        }
    }

    private suspend fun handleIncomingP2pFrame(peerDeviceId: String, frame: JSONObject) {
        val peerName = connectEngine.securityStore.loadTrustedPeers()
            .firstOrNull { it.deviceId == peerDeviceId }?.deviceName ?: "Arwora Peer"
        val existingThread = dao.getThreadByPeerDeviceId(peerDeviceId)?.toDomain()
            ?: getOrCreatePeerThread(
                NearChatPeer(
                    id = peerDeviceId,
                    displayName = peerName,
                    source = com.arwora.feature.chat.model.NearDiscoverySource.WIFI_NSD,
                    isConnected = true
                )
            )
        when (frame.optString("type")) {
            FRAME_MESSAGE -> {
                val msg = decodeChatHubMessagePacket(existingThread.id, peerDeviceId, peerName, frame) ?: return
                dao.upsertMessage(ChatMessageEntity.fromDomain(msg))
                dao.upsertThread(
                    ChatThreadEntity.fromDomain(
                        existingThread.copy(
                            isOnline = true,
                            isTyping = false,
                            unreadCount = existingThread.unreadCount + 1,
                            lastMessagePreview = msg.displaySummary(),
                            lastMessageTimeMillis = msg.timestampMillis
                        )
                    )
                )
            }
            FRAME_TYPING -> {
                val typing = frame.optBoolean("isTyping", false)
                dao.upsertThread(
                    ChatThreadEntity.fromDomain(existingThread.copy(isTyping = typing, isOnline = true))
                )
                val next = _typingThreads.value.toMutableSet()
                if (typing) next.add(existingThread.id) else next.remove(existingThread.id)
                _typingThreads.value = next
            }
            FRAME_SEEN -> {
                val seenAt = frame.optLong("seenAt", System.currentTimeMillis())
                dao.markOutgoingSeen(existingThread.id, seenAt)
            }
            FRAME_REACTION -> {
                val msgId = frame.optString("messageId")
                val actor = frame.optString("actorId", peerDeviceId)
                val emoji = frame.optString("emoji")
                val selected = frame.optBoolean("selected", true)
                val msg = dao.getMessageById(msgId)?.toDomain() ?: return
                val mutable = msg.reactions.mapValues { it.value.toMutableSet() }.toMutableMap()
                val set = mutable.getOrPut(emoji) { mutableSetOf() }
                if (selected) set.add(actor) else {
                    set.remove(actor)
                    if (set.isEmpty()) mutable.remove(emoji)
                }
                dao.upsertMessage(ChatMessageEntity.fromDomain(msg.copy(reactions = mutable.mapValues { it.value.toSet() })))
            }
            FRAME_EDIT -> {
                val msgId = frame.optString("messageId")
                val newText = frame.optString("newText")
                val editedAt = frame.optLong("editedAt", System.currentTimeMillis())
                val msg = dao.getMessageById(msgId)?.toDomain() ?: return
                dao.upsertMessage(ChatMessageEntity.fromDomain(msg.copy(text = newText, isEdited = true, editedAtMillis = editedAt)))
                refreshThreadLastPreview(existingThread.id)
            }
            FRAME_DELETE_EVERYONE -> {
                val msgId = frame.optString("messageId")
                val msg = dao.getMessageById(msgId)?.toDomain() ?: return
                dao.upsertMessage(
                    ChatMessageEntity.fromDomain(
                        msg.copy(
                            text = "This message was deleted",
                            mediaBase64 = null,
                            mediaPathOrUri = null,
                            isDeletedForEveryone = true,
                            reactions = emptyMap()
                        )
                    )
                )
                refreshThreadLastPreview(existingThread.id)
            }
            FRAME_SCREENSHOT_ALERT -> {
                val actorName = frame.optString("actorName", peerName)
                val alert = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    threadId = existingThread.id,
                    senderId = "system",
                    senderName = "Security",
                    isOutgoing = false,
                    type = ChatMessageType.SYSTEM_ALERT,
                    text = "$actorName took a screenshot of this chat",
                    deliveryStatus = MessageDeliveryStatus.SEEN,
                    timestampMillis = System.currentTimeMillis()
                )
                dao.upsertMessage(ChatMessageEntity.fromDomain(alert))
            }
        }
    }

    private fun encodeChatHubMessagePacket(thread: ChatThread, message: ChatMessage): JSONObject {
        return JSONObject().apply {
            put("type", FRAME_MESSAGE)
            put("messageId", message.id)
            put("threadId", thread.id)
            put("senderId", message.senderId)
            put("senderName", message.senderName)
            put("msgType", message.type.storageValue)
            put("text", message.text)
            message.mediaBase64?.let { put("mediaBase64", it) }
            message.mediaMimeType?.let { put("mediaMimeType", it) }
            message.fileName?.let { put("fileName", it) }
            put("fileSizeBytes", message.fileSizeBytes)
            put("durationMs", message.durationMs)
            put("waveform", JSONArray(message.waveform.map { it.toDouble() }))
            message.latitude?.let { put("latitude", it) }
            message.longitude?.let { put("longitude", it) }
            message.locationLabel?.let { put("locationLabel", it) }
            message.stickerId?.let { put("stickerId", it) }
            message.replyToMessageId?.let { put("replyToMessageId", it) }
            message.replyPreviewText?.let { put("replyPreviewText", it) }
            message.replySenderName?.let { put("replySenderName", it) }
            message.forwardedFrom?.let { put("forwardedFrom", it) }
            put("isViewOnce", message.isViewOnce)
            put("timestamp", message.timestampMillis)
        }
    }

    private fun decodeChatHubMessagePacket(
        threadId: String,
        peerId: String,
        fallbackName: String,
        frame: JSONObject
    ): ChatMessage? {
        val msgId = frame.optString("messageId").ifBlank { return null }
        val waveArr = frame.optJSONArray("waveform")
        val wave = if (waveArr != null) {
            List(waveArr.length()) { idx -> waveArr.optDouble(idx, 0.2).toFloat().coerceIn(0.1f, 1f) }
        } else emptyList()
        return ChatMessage(
            id = msgId,
            threadId = threadId,
            senderId = frame.optString("senderId").ifBlank { peerId },
            senderName = frame.optString("senderName", fallbackName).ifBlank { fallbackName },
            isOutgoing = false,
            type = ChatMessageType.fromStorage(frame.optString("msgType")),
            text = frame.optString("text"),
            mediaBase64 = frame.optString("mediaBase64").takeIf { it.isNotBlank() },
            mediaMimeType = frame.optString("mediaMimeType").takeIf { it.isNotBlank() },
            fileName = frame.optString("fileName").takeIf { it.isNotBlank() },
            fileSizeBytes = frame.optLong("fileSizeBytes", 0L),
            durationMs = frame.optLong("durationMs", 0L),
            waveform = wave,
            latitude = if (frame.has("latitude")) frame.optDouble("latitude") else null,
            longitude = if (frame.has("longitude")) frame.optDouble("longitude") else null,
            locationLabel = frame.optString("locationLabel").takeIf { it.isNotBlank() },
            stickerId = frame.optString("stickerId").takeIf { it.isNotBlank() },
            replyToMessageId = frame.optString("replyToMessageId").takeIf { it.isNotBlank() },
            replyPreviewText = frame.optString("replyPreviewText").takeIf { it.isNotBlank() },
            replySenderName = frame.optString("replySenderName").takeIf { it.isNotBlank() },
            forwardedFrom = frame.optString("forwardedFrom").takeIf { it.isNotBlank() },
            isViewOnce = frame.optBoolean("isViewOnce", false),
            deliveryStatus = MessageDeliveryStatus.DELIVERED,
            deliveredAtMillis = System.currentTimeMillis(),
            timestampMillis = frame.optLong("timestamp", System.currentTimeMillis())
        )
    }

    private fun copyUriToChatMediaCache(uri: Uri, prefix: String, ext: String): File? {
        return runCatching {
            val dir = File(appContext.filesDir, "chat_hub_media").apply { if (!exists()) mkdirs() }
            val outFile = File(dir, "${prefix}_${UUID.randomUUID()}.$ext")
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            outFile
        }.getOrNull()
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchBestLastLocation(): Location? = suspendCancellableCoroutine { cont ->
        val fused = LocationServices.getFusedLocationProviderClient(appContext)
        runCatching {
            fused.lastLocation
                .addOnSuccessListener { loc ->
                    if (cont.isActive) {
                        cont.resume(loc ?: fallbackLocationManager())
                    }
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(fallbackLocationManager())
                }
        }.onFailure {
            if (cont.isActive) cont.resume(fallbackLocationManager())
        }
    }

    @SuppressLint("MissingPermission")
    private fun fallbackLocationManager(): Location? {
        return runCatching {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocodeShort(lat: Double, lng: Double): String? {
        return runCatching {
            val geocoder = Geocoder(appContext, Locale.getDefault())
            val list = geocoder.getFromLocation(lat, lng, 1)
            val addr = list?.firstOrNull() ?: return null
            listOfNotNull(addr.featureName, addr.locality, addr.countryName)
                .distinct()
                .joinToString(", ")
                .ifBlank { null }
        }.getOrNull()
    }

    companion object {
        private const val FRAME_MESSAGE = "CHAT_HUB_MESSAGE"
        private const val FRAME_TYPING = "CHAT_HUB_TYPING"
        private const val FRAME_SEEN = "CHAT_HUB_SEEN"
        private const val FRAME_REACTION = "CHAT_HUB_REACTION"
        private const val FRAME_EDIT = "CHAT_HUB_EDIT"
        private const val FRAME_DELETE_EVERYONE = "CHAT_HUB_DELETE_EVERYONE"
        private const val FRAME_SCREENSHOT_ALERT = "CHAT_HUB_SCREENSHOT_ALERT"

        @Volatile
        private var INSTANCE: ChatHubRepository? = null

        fun getInstance(context: Context): ChatHubRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatHubRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        @Synchronized
        internal fun resetForTests(context: Context) {
            INSTANCE?.scope?.cancel()
            INSTANCE?.nearTransport?.stopDiscovery()
            INSTANCE?.preferencesStore?.clearForTests()
            INSTANCE = null
            ChatEncryptedDatabase.resetForTests(context)
            ChatCipherKeyStore.clearForTests(context)
        }
    }
}
