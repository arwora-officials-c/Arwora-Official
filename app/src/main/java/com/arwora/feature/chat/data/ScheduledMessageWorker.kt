package com.arwora.feature.chat.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arwora.R
import com.arwora.feature.chat.model.MessageDeliveryStatus
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that automatically dispatches scheduled Chat Hub messages
 * at the exact target timestamp, bridging P2P delivery when connected.
 */
class ScheduledMessageWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_MESSAGE_ID) ?: return Result.failure()
        val repo = ChatHubRepository.getInstance(applicationContext)
        val delivered = repo.dispatchScheduledMessageNow(messageId)
        if (delivered) {
            postScheduledSentAlertIfAllowed(applicationContext, messageId)
        }
        return Result.success()
    }

    private suspend fun postScheduledSentAlertIfAllowed(context: Context, messageId: String) {
        val prefs = ChatPreferencesStore(context)
        if (prefs.isWithinDndSchedule()) return
        val db = ChatEncryptedDatabase.getInstance(context)
        val msg = db.chatDao().getMessageById(messageId)?.toDomain() ?: return
        val thread = db.chatDao().getThreadById(msg.threadId)?.toDomain() ?: return
        if (thread.isMuted || thread.isHidden) return
        if (!com.arwora.feature.system.connect.data.ArworaConnectManager.hasNotificationPermission(context)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null && nm.getNotificationChannel(CHANNEL_SCHEDULED) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_SCHEDULED,
                        "Chat Hub Scheduled Messages",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Confirms when scheduled messages are delivered."
                    }
                )
            }
        }

        val notif = NotificationCompat.Builder(context, CHANNEL_SCHEDULED)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Scheduled message sent to ${thread.title}")
            .setContentText(msg.displaySummary())
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(messageId.hashCode(), notif)
        }
    }

    companion object {
        const val KEY_MESSAGE_ID = "scheduled_message_id"
        private const val CHANNEL_SCHEDULED = "arwora_chat_scheduled_channel"

        fun enqueue(context: Context, messageId: String, triggerAtMillis: Long) {
            val delayMs = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(1_000L)
            val data = Data.Builder()
                .putString(KEY_MESSAGE_ID, messageId)
                .build()
            val request = OneTimeWorkRequestBuilder<ScheduledMessageWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
            runCatching {
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    uniqueWorkName(messageId),
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            }
        }

        fun cancel(context: Context, messageId: String) {
            runCatching {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueWorkName(messageId))
            }
        }

        fun uniqueWorkName(messageId: String): String = "chat_scheduled_msg_$messageId"
    }
}
