package com.arwora.feature.system.connect.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arwora.MainActivity
import com.arwora.R
import com.arwora.feature.system.connect.data.ArworaConnectManager
import com.arwora.feature.system.connect.model.PeerConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Persistent foreground [Service] running Arwora Connect's multi-client TCP server
 * and low-power coroutine peer auto-reconnection loop.
 */
class ArworaConnectService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectLoopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ArworaConnectManager.ensureChannels(this)
        startForegroundSafely(buildStatusNotification(connectedCount = 0))
        ArworaConnectManager.syncServiceRunning(true)

        val engine = ArworaConnectManager.getEngine(applicationContext)
        serviceScope.launch {
            runCatching { engine.startServer() }
            if (ArworaConnectManager.autoReconnectEnabled.value) {
                engine.probeAndAutoReconnectDisconnectedPeers()
            }
        }

        reconnectLoopJob = serviceScope.launch {
            while (isActive) {
                delay(AUTO_RECONNECT_INTERVAL_MS)
                engine.refreshLocalNetworkInfo()
                if (ArworaConnectManager.autoReconnectEnabled.value) {
                    engine.probeAndAutoReconnectDisconnectedPeers()
                }
                val connected = engine.peers.value.count { it.connectionState == PeerConnectionState.CONNECTED }
                startForegroundSafely(buildStatusNotification(connected))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        val engine = ArworaConnectManager.getEngine(applicationContext)
        val connected = engine.peers.value.count { it.connectionState == PeerConnectionState.CONNECTED }
        startForegroundSafely(buildStatusNotification(connected))
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        reconnectLoopJob?.cancel()
        serviceScope.cancel()
        ArworaConnectManager.getEngine(applicationContext).stopServerImmediate()
        ArworaConnectManager.syncServiceRunning(false)
    }

    private fun startForegroundSafely(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to promote ArworaConnectService to foreground", e)
        }
    }

    private fun buildStatusNotification(connectedCount: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ArworaConnectManager.EXTRA_OPEN_ARWORA_CONNECT, true)
            putExtra("skip_splash", true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 7201, openIntent, flags)

        val text = if (connectedCount > 0) {
            "$connectedCount trusted peer(s) connected • Offline auto-accept active"
        } else {
            "Listening for paired QR/Code peers on local Wi-Fi"
        }

        return NotificationCompat.Builder(this, ArworaConnectManager.CHANNEL_MONITOR)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Arwora Connect P2P Hub")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val TAG = "ArworaConnectService"
        private const val NOTIFICATION_ID = 7299
        private const val AUTO_RECONNECT_INTERVAL_MS = 12_000L
        const val ACTION_STOP_SERVICE = "com.arwora.ACTION_STOP_ARWORA_CONNECT"

        fun start(context: Context) {
            val intent = Intent(context, ArworaConnectService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start ArworaConnectService", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, ArworaConnectService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop ArworaConnectService", e)
            }
        }
    }
}
