package com.arwora.feature.chat.data

import android.content.Context
import android.os.Build
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.arwora.feature.chat.model.ChatMessage
import com.arwora.feature.chat.model.ChatMessageType
import com.arwora.feature.chat.model.ChatThread
import com.arwora.feature.chat.model.MessageDeliveryStatus
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONArray

@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val isGroup: Boolean = false,
    val participantIdsJson: String = "[]",
    val participantNamesJson: String = "[]",
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
) {
    fun toDomain(): ChatThread = ChatThread(
        id = id,
        title = title,
        isGroup = isGroup,
        participantIds = decodeStringList(participantIdsJson),
        participantNames = decodeStringList(participantNamesJson),
        peerDeviceId = peerDeviceId,
        isArchived = isArchived,
        isHidden = isHidden,
        isLocked = isLocked,
        isMuted = isMuted,
        customSoundUri = customSoundUri,
        customSoundName = customSoundName,
        themeId = themeId,
        wallpaperPresetOrUri = wallpaperPresetOrUri,
        isOnline = isOnline,
        isTyping = isTyping,
        lastSeenMillis = lastSeenMillis,
        unreadCount = unreadCount,
        lastMessagePreview = lastMessagePreview,
        lastMessageTimeMillis = lastMessageTimeMillis,
        e2eeFingerprint = e2eeFingerprint
    )

    companion object {
        fun fromDomain(thread: ChatThread): ChatThreadEntity = ChatThreadEntity(
            id = thread.id,
            title = thread.title,
            isGroup = thread.isGroup,
            participantIdsJson = JSONArray(thread.participantIds).toString(),
            participantNamesJson = JSONArray(thread.participantNames).toString(),
            peerDeviceId = thread.peerDeviceId,
            isArchived = thread.isArchived,
            isHidden = thread.isHidden,
            isLocked = thread.isLocked,
            isMuted = thread.isMuted,
            customSoundUri = thread.customSoundUri,
            customSoundName = thread.customSoundName,
            themeId = thread.themeId,
            wallpaperPresetOrUri = thread.wallpaperPresetOrUri,
            isOnline = thread.isOnline,
            isTyping = thread.isTyping,
            lastSeenMillis = thread.lastSeenMillis,
            unreadCount = thread.unreadCount,
            lastMessagePreview = thread.lastMessagePreview,
            lastMessageTimeMillis = thread.lastMessageTimeMillis,
            e2eeFingerprint = thread.e2eeFingerprint
        )

        private fun decodeStringList(raw: String): List<String> {
            if (raw.isBlank() || raw == "[]") return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
            }.getOrDefault(emptyList())
        }
    }
}

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["threadId", "timestampMillis"]),
        Index(value = ["deliveryStatus", "scheduledForMillis"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val senderId: String,
    val senderName: String,
    val isOutgoing: Boolean,
    val type: String,
    val text: String = "",
    val mediaPathOrUri: String? = null,
    val mediaBase64: String? = null,
    val mediaMimeType: String? = null,
    val fileName: String? = null,
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val waveformJson: String = "[]",
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
    val deliveryStatus: String = MessageDeliveryStatus.SENT.storageValue,
    val deliveredAtMillis: Long? = null,
    val seenAtMillis: Long? = null,
    val reactionsJson: String = "{}",
    val scheduledForMillis: Long? = null,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    fun toDomain(): ChatMessage = ChatMessage(
        id = id,
        threadId = threadId,
        senderId = senderId,
        senderName = senderName,
        isOutgoing = isOutgoing,
        type = ChatMessageType.fromStorage(type),
        text = text,
        mediaPathOrUri = mediaPathOrUri,
        mediaBase64 = mediaBase64,
        mediaMimeType = mediaMimeType,
        fileName = fileName,
        fileSizeBytes = fileSizeBytes,
        durationMs = durationMs,
        waveform = ChatMessage.decodeWaveform(waveformJson),
        latitude = latitude,
        longitude = longitude,
        locationLabel = locationLabel,
        stickerId = stickerId,
        replyToMessageId = replyToMessageId,
        replyPreviewText = replyPreviewText,
        replySenderName = replySenderName,
        forwardedFrom = forwardedFrom,
        isEdited = isEdited,
        editedAtMillis = editedAtMillis,
        isDeletedForEveryone = isDeletedForEveryone,
        isViewOnce = isViewOnce,
        viewOnceConsumed = viewOnceConsumed,
        deliveryStatus = MessageDeliveryStatus.fromStorage(deliveryStatus),
        deliveredAtMillis = deliveredAtMillis,
        seenAtMillis = seenAtMillis,
        reactions = ChatMessage.decodeReactions(reactionsJson),
        scheduledForMillis = scheduledForMillis,
        timestampMillis = timestampMillis
    )

    companion object {
        fun fromDomain(msg: ChatMessage): ChatMessageEntity = ChatMessageEntity(
            id = msg.id,
            threadId = msg.threadId,
            senderId = msg.senderId,
            senderName = msg.senderName,
            isOutgoing = msg.isOutgoing,
            type = msg.type.storageValue,
            text = msg.text,
            mediaPathOrUri = msg.mediaPathOrUri,
            mediaBase64 = msg.mediaBase64,
            mediaMimeType = msg.mediaMimeType,
            fileName = msg.fileName,
            fileSizeBytes = msg.fileSizeBytes,
            durationMs = msg.durationMs,
            waveformJson = ChatMessage.encodeWaveform(msg.waveform),
            latitude = msg.latitude,
            longitude = msg.longitude,
            locationLabel = msg.locationLabel,
            stickerId = msg.stickerId,
            replyToMessageId = msg.replyToMessageId,
            replyPreviewText = msg.replyPreviewText,
            replySenderName = msg.replySenderName,
            forwardedFrom = msg.forwardedFrom,
            isEdited = msg.isEdited,
            editedAtMillis = msg.editedAtMillis,
            isDeletedForEveryone = msg.isDeletedForEveryone,
            isViewOnce = msg.isViewOnce,
            viewOnceConsumed = msg.viewOnceConsumed,
            deliveryStatus = msg.deliveryStatus.storageValue,
            deliveredAtMillis = msg.deliveredAtMillis,
            seenAtMillis = msg.seenAtMillis,
            reactionsJson = ChatMessage.encodeReactions(msg.reactions),
            scheduledForMillis = msg.scheduledForMillis,
            timestampMillis = msg.timestampMillis
        )
    }
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_threads ORDER BY lastMessageTimeMillis DESC")
    fun observeAllThreads(): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads WHERE id = :threadId LIMIT 1")
    fun observeThread(threadId: String): Flow<ChatThreadEntity?>

    @Query("SELECT * FROM chat_threads WHERE id = :threadId LIMIT 1")
    suspend fun getThreadById(threadId: String): ChatThreadEntity?

    @Query("SELECT * FROM chat_threads WHERE peerDeviceId = :peerDeviceId LIMIT 1")
    suspend fun getThreadByPeerDeviceId(peerDeviceId: String): ChatThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThread(thread: ChatThreadEntity)

    @Query("DELETE FROM chat_threads WHERE id = :threadId")
    suspend fun deleteThread(threadId: String)

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestampMillis ASC")
    fun observeMessagesForThread(threadId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY timestampMillis ASC")
    suspend fun getMessagesForThread(threadId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): ChatMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE threadId = :threadId")
    suspend fun deleteMessagesForThread(threadId: String)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query(
        "SELECT * FROM chat_messages WHERE isDeletedForEveryone = 0 AND text LIKE '%' || :query || '%' ORDER BY timestampMillis DESC LIMIT 100"
    )
    suspend fun searchMessagesGlobal(query: String): List<ChatMessageEntity>

    @Query(
        "SELECT * FROM chat_messages WHERE threadId = :threadId AND isDeletedForEveryone = 0 AND (:startMs IS NULL OR (timestampMillis >= :startMs AND timestampMillis <= :endMs)) AND (:query = '' OR text LIKE '%' || :query || '%' OR fileName LIKE '%' || :query || '%' OR locationLabel LIKE '%' || :query || '%') ORDER BY timestampMillis DESC"
    )
    suspend fun searchInThread(
        threadId: String,
        query: String,
        startMs: Long?,
        endMs: Long?
    ): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE deliveryStatus = 'SCHEDULED'")
    suspend fun getScheduledMessages(): List<ChatMessageEntity>

    @Query(
        "UPDATE chat_messages SET deliveryStatus = :seenStatus, seenAtMillis = :seenAt WHERE threadId = :threadId AND isOutgoing = 1 AND deliveryStatus != 'SCHEDULED' AND deliveryStatus != :seenStatus"
    )
    suspend fun markOutgoingSeen(threadId: String, seenAt: Long, seenStatus: String = MessageDeliveryStatus.SEEN.storageValue)

    @Query("UPDATE chat_threads SET unreadCount = 0 WHERE id = :threadId")
    suspend fun clearUnreadCount(threadId: String)

    @Query("DELETE FROM chat_messages WHERE deliveryStatus != 'SCHEDULED' AND timestampMillis <= :cutoffMillis")
    suspend fun deleteMessagesOlderThan(cutoffMillis: Long): Int
}

@Database(
    entities = [ChatThreadEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ChatEncryptedDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        private const val DB_NAME = "arwora_chat_encrypted.db"

        @Volatile
        private var INSTANCE: ChatEncryptedDatabase? = null

        @Volatile
        var isSqlCipherActive: Boolean = false
            private set

        fun getInstance(context: Context): ChatEncryptedDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): ChatEncryptedDatabase {
            val isRobolectric = Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
            if (isRobolectric) {
                isSqlCipherActive = false
                return Room.inMemoryDatabaseBuilder(
                    appContext,
                    ChatEncryptedDatabase::class.java
                )
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build()
            }

            val builder = Room.databaseBuilder(
                appContext,
                ChatEncryptedDatabase::class.java,
                DB_NAME
            ).fallbackToDestructiveMigration()

            val cipherLoaded = runCatching {
                System.loadLibrary("sqlcipher")
                val passphrase = ChatCipherKeyStore.getOrCreateDatabasePassphrase(appContext)
                builder.openHelperFactory(SupportOpenHelperFactory(passphrase))
                true
            }.getOrDefault(false)
            isSqlCipherActive = cipherLoaded

            return builder.build()
        }

        @Synchronized
        internal fun resetForTests(context: Context) {
            INSTANCE?.close()
            INSTANCE = null
            isSqlCipherActive = false
            context.applicationContext.deleteDatabase(DB_NAME)
        }
    }
}
