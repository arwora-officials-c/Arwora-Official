package com.arwora.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arwora.MainActivity
import com.arwora.utils.EncryptedAdbCredentialStore
import com.arwora.utils.NativeAdbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Persistent background connection & auto-reconnect service for Wireless ADB.
 * Once a device has completed initial pairing, this service preserves the encrypted session
 * metadata + RSA keypair (`arwora`), automatically reconnects on app launch, Wi-Fi reconnects,
 * or socket drops, and falls back seamlessly to `_adb-tls-connect._tcp` mDNS discovery if the
 * daemon rotates its port.
 */
class AdbConnectionService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        private const val TAG = "AdbConnectionService"
        const val CHANNEL_ID = "adb_persistent_connection_channel"
        const val CHANNEL_NAME = "Persistent Wireless ADB"
        const val NOTIFICATION_ID = 4206
        const val ACTION_RECONNECT = "com.arwora.service.ACTION_PERSISTENT_ADB_RECONNECT"
        const val ACTION_STOP = "com.arwora.service.ACTION_PERSISTENT_ADB_STOP"
        const val ACTION_FORGET_RESET = "com.arwora.service.ACTION_FORGET_RESET"
        private const val KEEPALIVE_INTERVAL_MS = 45_000L
        private const val UNIQUE_RECONNECT_WORK = "arwora_adb_silent_reconnect"

        @Volatile
        var isRunning: Boolean = false
            private set

        private fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (nm != null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Maintains persistent background Wireless ADB auto-reconnect"
                        setShowBadge(false)
                    }
                    nm.createNotificationChannel(channel)
                }
            }
        }

        fun buildStatusNotification(
            context: Context,
            connected: Boolean,
            endpointText: String? = null
        ): Notification {
            createChannel(context)
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                4206,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val stopIntent = Intent(context, AdbConnectionService::class.java).apply {
                action = ACTION_STOP
            }
            val stopPendingIntent = PendingIntent.getService(
                context,
                4207,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (connected) "Wireless ADB Active" else "Wireless ADB Auto-Reconnect"
            val content = when {
                connected && !endpointText.isNullOrBlank() -> "Connected silently to $endpointText"
                connected -> "Wireless ADB session active in background"
                else -> "Monitoring Wi-Fi to silently resume paired ADB session"
            }

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopPendingIntent)
                .build()
        }

        fun startPersistentSession(context: Context) {
            val appCtx = context.applicationContext
            val creds = EncryptedAdbCredentialStore.loadState(appCtx)
            if (!creds.isPaired || creds.userStopped) return

            createChannel(appCtx)
            val intent = Intent(appCtx, AdbConnectionService::class.java).apply {
                action = ACTION_RECONNECT
            }
            val startedForeground = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
                true
            } catch (t: Throwable) {
                // Android 12+ background FGS restrictions -> fall back cleanly to WorkManager worker
                Log.d(TAG, "Foreground service start deferred to WorkManager: ${t.message}")
                false
            }
            if (!startedForeground) {
                enqueueReconnectWork(appCtx)
            }
        }

        fun scheduleReconnect(context: Context, reason: String = "reconnect") {
            val appCtx = context.applicationContext
            val creds = EncryptedAdbCredentialStore.loadState(appCtx)
            if (!creds.isPaired || creds.userStopped) return

            Log.d(TAG, "Scheduling silent Wireless ADB reconnect (reason=$reason)")
            enqueueReconnectWork(appCtx)
        }

        fun enqueueReconnectWork(context: Context) {
            try {
                val request = OneTimeWorkRequestBuilder<AdbReconnectWorker>().build()
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE_RECONNECT_WORK,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
            } catch (t: Throwable) {
                Log.w(TAG, "WorkManager unavailable; running fallback coroutine reconnect: ${t.message}")
                val appCtx = context.applicationContext
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    runCatching { NativeAdbManager.reconnectSavedSession(appCtx) }
                }
            }
        }

        fun cancelPendingWork(context: Context) {
            runCatching {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_RECONNECT_WORK)
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            cancelPendingWork(appCtx)
            runCatching {
                appCtx.stopService(Intent(appCtx, AdbConnectionService::class.java))
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        NativeAdbManager.init(this)

        val notification = buildStatusNotification(
            context = this,
            connected = NativeAdbManager.isAdbReady(),
            endpointText = formatEndpointText()
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        registerWifiCallback()
        startKeepaliveLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            NativeAdbManager.disconnect(userInitiated = true)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_FORGET_RESET) {
            NativeAdbManager.forgetPairedDeviceAndReset(this)
            stopSelf()
            return START_NOT_STICKY
        }

        val creds = EncryptedAdbCredentialStore.loadState(this)
        if (!creds.isPaired || creds.userStopped || NativeAdbManager.isSessionForgotten.value) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_RECONNECT || !NativeAdbManager.isAdbReady()) {
            serviceScope.launch {
                val (ok, _) = NativeAdbManager.reconnectSavedSession(this@AdbConnectionService)
                updateNotification(ok)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        unregisterWifiCallback()
        monitorJob?.cancel()
        serviceJob.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startKeepaliveLoop() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                val creds = EncryptedAdbCredentialStore.loadState(this@AdbConnectionService)
                if (!creds.isPaired || creds.userStopped || NativeAdbManager.isSessionForgotten.value) {
                    stopSelf()
                    break
                }
                val alive = if (NativeAdbManager.isAdbReady()) {
                    NativeAdbManager.verifyConnectionAlive()
                } else if (NativeAdbManager.isWifiConnected(this@AdbConnectionService)) {
                    NativeAdbManager.reconnectSavedSession(this@AdbConnectionService).first
                } else {
                    false
                }
                updateNotification(alive)
                delay(KEEPALIVE_INTERVAL_MS)
            }
        }
    }

    private fun registerWifiCallback() {
        if (networkCallback != null) return
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val creds = EncryptedAdbCredentialStore.loadState(this@AdbConnectionService)
                    if (creds.isPaired && !creds.userStopped) {
                        serviceScope.launch {
                            val (ok, _) = NativeAdbManager.reconnectSavedSession(this@AdbConnectionService)
                            updateNotification(ok)
                        }
                    }
                }

                override fun onLost(network: Network) {
                    NativeAdbManager.refreshConnectionStatus(this@AdbConnectionService)
                    updateNotification(NativeAdbManager.isAdbReady())
                }
            }
            cm.registerNetworkCallback(request, cb)
            networkCallback = cb
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to register NetworkCallback inside AdbConnectionService", t)
        }
    }

    private fun unregisterWifiCallback() {
        val cb = networkCallback ?: return
        networkCallback = null
        runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(cb)
        }
    }

    private fun formatEndpointText(): String? {
        val host = NativeAdbManager.connectedHost.value?.takeIf { it.isNotBlank() } ?: return null
        val port = NativeAdbManager.connectedPort.value?.takeIf { it in 1024..65535 } ?: return host
        return "$host:$port"
    }

    private fun updateNotification(connected: Boolean) {
        if (!AdbPairingNotifier.hasNotificationPermission(this)) return
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(
                NOTIFICATION_ID,
                buildStatusNotification(
                    context = this,
                    connected = connected,
                    endpointText = formatEndpointText()
                )
            )
        }
    }
}

/**
 * WorkManager worker guaranteeing silent background Wireless ADB reconnection when triggered
 * from system connectivity broadcasts under Android 12+ foreground-service restrictions.
 */
class AdbReconnectWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val creds = EncryptedAdbCredentialStore.loadState(applicationContext)
        if (!creds.isPaired || creds.userStopped || NativeAdbManager.isSessionForgotten.value) {
            return Result.success()
        }
        val (ok, _) = NativeAdbManager.reconnectSavedSession(applicationContext)
        if (ok) {
            return Result.success()
        }
        if (NativeAdbManager.isSessionForgotten.value ||
            !EncryptedAdbCredentialStore.loadState(applicationContext).isPaired
        ) {
            return Result.success()
        }
        return if (runAttemptCount < 2) Result.retry() else Result.success()
    }
}
