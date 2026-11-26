package com.arwora.feature.system.connect.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.arwora.MainActivity
import com.arwora.R
import com.arwora.feature.system.connect.model.FileTransferRecord
import com.arwora.feature.system.connect.model.P2pMessageRetentionPolicy
import com.arwora.feature.system.connect.service.ArworaConnectService
import com.arwora.feature.system.connect.service.AutoReconnectWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * App-wide coordinator for Arwora Connect managing the lazy [ArworaConnectEngine],
 * foreground service lifecycle, WorkManager auto-reconnect scheduling, transfer alerts,
 * and P2P message auto-delete retention policy.
 */
object ArworaConnectManager {

    private const val TAG = "ArworaConnectManager"
    private const val PREFS_NAME = "arwora_connect_runtime_prefs"
    private const val KEY_AUTO_RECONNECT = "auto_reconnect_enabled"
    private const val KEY_MESSAGE_RETENTION_POLICY = "p2p_message_retention_policy"
    private const val KEY_LAST_RETENTION_DURATION = "p2p_last_retention_duration"
    private const val RECONNECT_WORK_NAME = "arwora_connect_auto_reconnect_work"

    const val CHANNEL_MONITOR = "arwora_connect_monitor_channel"
    const val CHANNEL_TRANSFERS = "arwora_connect_transfers_channel"
    const val EXTRA_OPEN_ARWORA_CONNECT = "extra_open_arwora_connect"

    @Volatile
    private var engineInstance: ArworaConnectEngine? = null

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _autoReconnectEnabled = MutableStateFlow(true)
    val autoReconnectEnabled: StateFlow<Boolean> = _autoReconnectEnabled.asStateFlow()

    private val _messageRetentionPolicy = MutableStateFlow(P2pMessageRetentionPolicy.OFF)
    val messageRetentionPolicy: StateFlow<P2pMessageRetentionPolicy> = _messageRetentionPolicy.asStateFlow()

    private val _lastNonOffRetentionPolicy = MutableStateFlow(P2pMessageRetentionPolicy.HOURS_24)
    val lastNonOffRetentionPolicy: StateFlow<P2pMessageRetentionPolicy> = _lastNonOffRetentionPolicy.asStateFlow()

    private val notifIdSeed = AtomicInteger(7200)

    fun syncPreferences(context: Context) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _autoReconnectEnabled.value = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
        val policy = P2pMessageRetentionPolicy.fromStorageValue(
            prefs.getString(KEY_MESSAGE_RETENTION_POLICY, null)
        )
        val storedLastDuration = P2pMessageRetentionPolicy.fromStorageValue(
            prefs.getString(KEY_LAST_RETENTION_DURATION, null)
        )
        val resolvedLastDuration = when {
            policy != P2pMessageRetentionPolicy.OFF -> policy
            storedLastDuration != P2pMessageRetentionPolicy.OFF -> storedLastDuration
            else -> P2pMessageRetentionPolicy.HOURS_24
        }
        _messageRetentionPolicy.value = policy
        _lastNonOffRetentionPolicy.value = resolvedLastDuration
        engineInstance?.chatManager?.setRetentionPolicy(policy)
    }

    @Synchronized
    fun getEngine(context: Context): ArworaConnectEngine {
        val existing = engineInstance
        if (existing != null) return existing
        val appCtx = context.applicationContext
        syncPreferences(appCtx)

        val engine = ArworaConnectEngine(
            context = appCtx,
            initialRetentionPolicy = _messageRetentionPolicy.value
        ).apply {
            onIncomingFileCompleted = { record ->
                dispatchIncomingTransferNotification(appCtx, record)
            }
        }
        engineInstance = engine
        return engine
    }

    fun syncServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun setAutoReconnectEnabled(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext
        _autoReconnectEnabled.value = enabled
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_RECONNECT, enabled)
            .apply()
        if (enabled) {
            scheduleAutoReconnectWorker(appCtx)
        } else {
            cancelAutoReconnectWorker(appCtx)
        }
    }

    fun setMessageRetentionPolicy(context: Context, policy: P2pMessageRetentionPolicy) {
        val appCtx = context.applicationContext
        _messageRetentionPolicy.value = policy
        val nextDuration = if (policy != P2pMessageRetentionPolicy.OFF) {
            policy
        } else {
            _lastNonOffRetentionPolicy.value.takeIf { it != P2pMessageRetentionPolicy.OFF }
                ?: P2pMessageRetentionPolicy.HOURS_24
        }
        _lastNonOffRetentionPolicy.value = nextDuration
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MESSAGE_RETENTION_POLICY, policy.storageValue)
            .putString(KEY_LAST_RETENTION_DURATION, nextDuration.storageValue)
            .apply()
        engineInstance?.chatManager?.setRetentionPolicy(policy)
    }

    fun startHubService(context: Context) {
        val appCtx = context.applicationContext
        getEngine(appCtx)
        ArworaConnectService.start(appCtx)
        if (_autoReconnectEnabled.value) {
            scheduleAutoReconnectWorker(appCtx)
        }
    }

    fun stopHubService(context: Context) {
        val appCtx = context.applicationContext
        ArworaConnectService.stop(appCtx)
        cancelAutoReconnectWorker(appCtx)
    }

    fun scheduleAutoReconnectWorker(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val work = PeriodicWorkRequestBuilder<AutoReconnectWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                RECONNECT_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        } catch (e: Exception) {
            Log.d(TAG, "WorkManager schedule skipped: ${e.message}")
        }
    }

    fun cancelAutoReconnectWorker(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(RECONNECT_WORK_NAME)
        } catch (e: Exception) {
            Log.d(TAG, "WorkManager cancel skipped: ${e.message}")
        }
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_MONITOR) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_MONITOR,
                        "Arwora Connect P2P Service",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Keeps offline P2P socket listener and peer auto-reconnect active."
                    }
                )
            }
            if (nm.getNotificationChannel(CHANNEL_TRANSFERS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_TRANSFERS,
                        "Arwora Connect File Transfers",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Alerts when trusted peers finish streaming files."
                    }
                )
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun dispatchIncomingTransferNotification(context: Context, record: FileTransferRecord) {
        val appCtx = context.applicationContext
        if (!hasNotificationPermission(appCtx)) return
        ensureChannels(appCtx)

        val openIntent = Intent(appCtx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_ARWORA_CONNECT, true)
            putExtra("skip_splash", true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val notifId = notifIdSeed.incrementAndGet()
        val pi = PendingIntent.getActivity(appCtx, notifId, openIntent, flags)

        val body = "Auto-accepted from ${record.peerDeviceName} (${ConnectNetworkUtils.formatBytes(record.totalBytes)})"
        val notification = NotificationCompat.Builder(appCtx, CHANNEL_TRANSFERS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Received: ${record.fileName}")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            NotificationManagerCompat.from(appCtx).notify(notifId, notification)
        } catch (_: SecurityException) {
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post transfer notification", e)
        }
    }

    @Synchronized
    fun resetForTests(context: Context) {
        val appCtx = context.applicationContext
        engineInstance?.shutdown()
        engineInstance = null
        _isServiceRunning.value = false
        _autoReconnectEnabled.value = true
        _messageRetentionPolicy.value = P2pMessageRetentionPolicy.OFF
        _lastNonOffRetentionPolicy.value = P2pMessageRetentionPolicy.HOURS_24
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        runCatching { File(appCtx.filesDir, ArworaConnectChatManager.HISTORY_FILE_NAME).delete() }
        runCatching { File(appCtx.cacheDir, "arwora_voice_cache").deleteRecursively() }
        ConnectSecurityStore(appCtx).clearAllForTests()
    }
}
