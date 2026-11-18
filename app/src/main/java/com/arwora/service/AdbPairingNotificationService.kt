package com.arwora.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.arwora.MainActivity
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.WirelessPairingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared notification builder and state helper for Wireless Debugging Direct Reply pairing
 * on the dedicated high-priority [CHANNEL_ID] channel.
 */
object AdbPairingNotifier {
    const val CHANNEL_ID = "adb_pairing_channel"
    const val CHANNEL_NAME = "Wireless ADB Pairing"
    const val NOTIFICATION_ID = 4205
    const val KEY_PAIRING_CODE = "key_pairing_code"

    const val ACTION_SUBMIT_PAIRING_CODE = "com.arwora.service.ACTION_SUBMIT_PAIRING_CODE"
    const val ACTION_RETRY_PAIRING = "com.arwora.service.ACTION_RETRY_PAIRING"
    const val ACTION_RETRY_CONNECT = "com.arwora.service.ACTION_RETRY_CONNECT"
    const val ACTION_STOP_PAIRING = "com.arwora.service.ACTION_STOP_PAIRING"

    const val EXTRA_HOST = "extra_host"
    const val EXTRA_PORT = "extra_port"
    const val EXTRA_PAIRING_PORT = "extra_pairing_port"

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Interactive Direct Reply notifications for native Wireless ADB pairing"
                setShowBadge(true)
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    fun buildReadyNotification(
        context: Context,
        host: String = "127.0.0.1",
        port: Int? = null
    ): Notification {
        createChannel(context)
        val cleanHost = host.trim().ifBlank { "127.0.0.1" }
        val contentText = if (port != null && port in 1024..65535) {
            "Pairing port detected ($port). Enter the 6-digit code from Developer Options below."
        } else {
            "Scanning for Wireless Debugging pairing port... Open Developer Options > Pair device with pairing code."
        }

        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel("Enter 6-Digit Code")
            .build()

        val replyIntent = Intent(context, AdbPairingReceiver::class.java).apply {
            action = ACTION_SUBMIT_PAIRING_CODE
            putExtra(EXTRA_HOST, cleanHost)
            if (port != null && port in 1024..65535) {
                putExtra(EXTRA_PORT, port)
            }
        }
        val replyFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            replyIntent,
            replyFlags
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Enter 6-Digit Code",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val stopIntent = Intent(context, AdbPairingReceiver::class.java).apply {
            action = ACTION_STOP_PAIRING
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Wireless Debugging Pairing Ready")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(buildOpenAppPendingIntent(context))
            .addAction(replyAction)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .build()
    }

    fun buildPairingProgressNotification(context: Context): Notification {
        createChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Wireless Debugging Pairing")
            .setContentText("Pairing with ${NativeAdbManager.CLIENT_IDENTITY}...")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(buildOpenAppPendingIntent(context))
            .build()
    }

    fun buildSuccessConnectingNotification(
        context: Context,
        connected: Boolean = false,
        detail: String? = null
    ): Notification {
        createChannel(context)
        val title = if (connected) "Wireless ADB Connected" else "Wireless Debugging Paired"
        val content = if (connected) {
            "ADB Connected Successfully!"
        } else {
            "Paired! Resolving connection port..."
        }
        val expandedText = if (connected && !detail.isNullOrBlank()) {
            "$content ($detail)"
        } else {
            content
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(buildOpenAppPendingIntent(context))
            .setOnlyAlertOnce(true)

        if (!connected) {
            builder.setProgress(0, 0, true).setOngoing(true)
        } else {
            builder.setProgress(0, 0, false).setOngoing(false).setAutoCancel(true)
        }
        return builder.build()
    }

    fun buildConnectTimeoutNotification(
        context: Context,
        reason: String? = null,
        pairingPort: Int? = null
    ): Notification {
        createChannel(context)
        val retryIntent = Intent(context, AdbPairingReceiver::class.java).apply {
            action = ACTION_RETRY_CONNECT
            if (pairingPort != null && pairingPort in 1024..65535) {
                putExtra(EXTRA_PAIRING_PORT, pairingPort)
            }
        }
        val retryPendingIntent = PendingIntent.getBroadcast(
            context,
            104,
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = reason?.takeIf { it.isNotBlank() }
            ?: "Timed out resolving Wireless Debugging connection port. Ensure Wireless Debugging is ON and tap Retry Connect."

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("ADB Connection Timed Out")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(buildOpenAppPendingIntent(context))
            .addAction(
                android.R.drawable.ic_menu_rotate,
                "Retry Connect",
                retryPendingIntent
            )
            .build()
    }

    fun buildFailureNotification(
        context: Context,
        reason: String? = null
    ): Notification {
        createChannel(context)
        val retryIntent = Intent(context, AdbPairingReceiver::class.java).apply {
            action = ACTION_RETRY_PAIRING
        }
        val retryPendingIntent = PendingIntent.getBroadcast(
            context,
            103,
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = reason?.takeIf { it.isNotBlank() }
            ?: "Could not complete wireless ADB pairing."

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Pairing Failed — Tap to Retry")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(retryPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_rotate,
                "Pairing Failed — Tap to Retry",
                retryPendingIntent
            )
            .build()
    }

    fun notifySafely(context: Context, notification: Notification) {
        createChannel(context)
        if (!hasNotificationPermission(context)) return
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (se: SecurityException) {
            Log.w("AdbPairingNotifier", "Missing notification permission", se)
        } catch (t: Throwable) {
            Log.w("AdbPairingNotifier", "Unable to post pairing notification", t)
        }
    }

    fun cancelNotification(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (_: Throwable) {
        }
    }

    private fun buildOpenAppPendingIntent(context: Context): PendingIntent {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            100,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * Foreground pairing listener service that registers an [NsdManager.DiscoveryListener] for
 * `_adb-tls-pairing._tcp`, automatically resolves the local host IP and active pairing port
 * when Android's "Pair device with pairing code" dialog opens, and posts an interactive
 * Direct Reply (`RemoteInput`) notification on `adb_pairing_channel`.
 */
class AdbPairingNotificationService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    private var nsdManager: NsdManager? = null
    private var pairingDiscoveryListener: NsdManager.DiscoveryListener? = null
    private val isDiscoveryActive = AtomicBoolean(false)
    private val isResolving = AtomicBoolean(false)

    @Volatile
    private var activeHost: String = "127.0.0.1"

    @Volatile
    private var activePort: Int? = null

    companion object {
        private const val TAG = "AdbPairingNotifService"
        private const val NSD_PAIRING_SERVICE = "_adb-tls-pairing._tcp"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var activeInstance: AdbPairingNotificationService? = null

        fun start(context: Context) {
            val appCtx = context.applicationContext
            AdbPairingNotifier.createChannel(appCtx)
            val intent = Intent(appCtx, AdbPairingNotificationService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(intent)
                } else {
                    appCtx.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "startForegroundService failed, falling back to notification post", e)
                AdbPairingNotifier.notifySafely(
                    appCtx,
                    AdbPairingNotifier.buildReadyNotification(
                        context = appCtx,
                        host = WirelessPairingManager.savedHost.value.ifBlank { "127.0.0.1" },
                        port = WirelessPairingManager.discoveredPort.value
                    )
                )
            }
        }

        fun pausePairingDiscovery() {
            activeInstance?.stopPairingMdnsDiscovery()
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            try {
                appCtx.stopService(Intent(appCtx, AdbPairingNotificationService::class.java))
            } catch (_: Exception) {
            }
            AdbPairingNotifier.cancelNotification(appCtx)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        isRunning = true
        NativeAdbManager.ensureClientIdentity()
        WirelessPairingManager.init(this)

        activeHost = NativeAdbManager.getLocalWifiIpAddress(this)
            ?.takeIf { it.isNotBlank() }
            ?: WirelessPairingManager.savedHost.value.ifBlank { "127.0.0.1" }
        activePort = null

        val initialNotification = AdbPairingNotifier.buildReadyNotification(
            context = this,
            host = activeHost,
            port = activePort
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                AdbPairingNotifier.NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(AdbPairingNotifier.NOTIFICATION_ID, initialNotification)
        }

        startPairingMdnsDiscovery()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == AdbPairingNotifier.ACTION_STOP_PAIRING) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isDiscoveryActive.get()) {
            startPairingMdnsDiscovery()
        }
        AdbPairingNotifier.notifySafely(
            this,
            AdbPairingNotifier.buildReadyNotification(
                context = this,
                host = activeHost,
                port = activePort
            )
        )
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        isRunning = false
        stopPairingMdnsDiscovery()
        serviceJob.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPairingMdnsDiscovery() {
        stopPairingMdnsDiscovery()
        serviceScope.launch {
            try {
                val nsd = getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return@launch
                nsdManager = nsd

                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(regType: String?) {
                        isDiscoveryActive.set(true)
                        Log.d(TAG, "NSD pairing discovery started: $regType")
                    }

                    override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                        val info = serviceInfo ?: return
                        if (!isResolving.compareAndSet(false, true)) return
                        try {
                            @Suppress("DEPRECATION")
                            nsd.resolveService(info, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                                    isResolving.set(false)
                                    Log.w(TAG, "Pairing service resolve failed: $errorCode")
                                }

                                override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                                    isResolving.set(false)
                                    val port = resolvedInfo?.port ?: -1
                                    if (port !in 1024..65535) return
                                    val resolvedHost = NativeAdbManager.extractPreferredHostIp(
                                        resolvedInfo = resolvedInfo,
                                        wifiIpFallback = NativeAdbManager.getLocalWifiIpAddress(this@AdbPairingNotificationService)
                                    )
                                    onPairingEndpointDetected(resolvedHost, port)
                                }
                            })
                        } catch (e: Exception) {
                            isResolving.set(false)
                            Log.w(TAG, "Error resolving pairing mDNS service", e)
                        }
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}

                    override fun onDiscoveryStopped(serviceType: String?) {
                        isDiscoveryActive.set(false)
                    }

                    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                        isDiscoveryActive.set(false)
                        Log.w(TAG, "Start pairing mDNS discovery failed: $errorCode")
                    }

                    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                        isDiscoveryActive.set(false)
                    }
                }

                pairingDiscoveryListener = listener
                nsd.discoverServices(NSD_PAIRING_SERVICE, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to register NsdManager pairing listener", e)
            }
        }
    }

    internal fun onPairingEndpointDetected(host: String, port: Int) {
        if (port !in 1024..65535) return
        val cleanHost = host.trim().ifBlank { "127.0.0.1" }
        activeHost = cleanHost
        activePort = port
        WirelessPairingManager.onMdnsPairingEndpointResolved(this, cleanHost, port)
        AdbPairingNotifier.notifySafely(
            this,
            AdbPairingNotifier.buildReadyNotification(
                context = this,
                host = cleanHost,
                port = port
            )
        )
    }

    private fun stopPairingMdnsDiscovery() {
        val nsd = nsdManager
        val listener = pairingDiscoveryListener
        if (nsd != null && listener != null && isDiscoveryActive.compareAndSet(true, false)) {
            try {
                nsd.stopServiceDiscovery(listener)
            } catch (_: Exception) {
            }
        }
        isResolving.set(false)
        pairingDiscoveryListener = null
        nsdManager = null
    }
}
