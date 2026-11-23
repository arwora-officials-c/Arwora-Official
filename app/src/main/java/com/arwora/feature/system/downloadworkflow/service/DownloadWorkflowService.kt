package com.arwora.feature.system.downloadworkflow.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arwora.MainActivity
import com.arwora.R
import com.arwora.feature.system.downloadworkflow.data.DownloadWorkflowEngine
import com.arwora.feature.system.downloadworkflow.data.DownloadWorkflowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Lightweight background foreground [Service] watching the local Downloads directory
 * via [FileObserver] (and low-frequency fallback sweep) to detect finished downloads.
 */
class DownloadWorkflowService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var fileObserver: FileObserver? = null
    private var fallbackSweepJob: Job? = null
    private var watchedDirectory: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DownloadWorkflowManager.ensureChannels(this)
        startForegroundSafely(buildMonitorNotification())
        DownloadWorkflowManager.syncServiceRunning(true)

        serviceScope.launch {
            DownloadWorkflowManager.initIfNeeded(applicationContext)
        }

        val targetDir = customWatchDirForTests ?: DownloadWorkflowManager.resolveDownloadDirectory()
        startWatchingDirectory(targetDir)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely(buildMonitorNotification())
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopWatchingDirectory()
        fallbackSweepJob?.cancel()
        serviceScope.cancel()
        DownloadWorkflowManager.syncServiceRunning(false)
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
            Log.e(TAG, "Unable to promote DownloadWorkflowService to foreground", e)
        }
    }

    private fun buildMonitorNotification(): Notification {
        val openHubIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(DownloadWorkflowManager.EXTRA_OPEN_DOWNLOAD_WORKFLOW, true)
            putExtra(DownloadWorkflowManager.EXTRA_WORKFLOW_ACTION, DownloadWorkflowManager.ACTION_VIEW_DETAILS)
            putExtra("skip_splash", true)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(this, 6101, openHubIntent, flags)

        return NotificationCompat.Builder(this, DownloadWorkflowManager.MONITOR_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Smart Download Workflow Active")
            .setContentText("Monitoring Download folder for finished files")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    internal fun startWatchingDirectory(directory: File) {
        stopWatchingDirectory()
        watchedDirectory = directory
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val eventMask = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.CREATE
        val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(directory, eventMask) {
                override fun onEvent(event: Int, path: String?) {
                    handleObserverEvent(directory, path)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(directory.absolutePath, eventMask) {
                override fun onEvent(event: Int, path: String?) {
                    handleObserverEvent(directory, path)
                }
            }
        }
        fileObserver = observer
        try {
            observer.startWatching()
        } catch (e: Exception) {
            Log.e(TAG, "FileObserver failed to watch ${directory.absolutePath}", e)
        }

        fallbackSweepJob?.cancel()
        fallbackSweepJob = serviceScope.launch {
            while (isActive) {
                delay(FALLBACK_SWEEP_INTERVAL_MS)
                val cfg = DownloadWorkflowManager.config.value
                if (!cfg.masterEnabled) continue
                val dir = watchedDirectory ?: continue
                if (!dir.exists() || !dir.isDirectory) continue
                val candidates = dir.listFiles()
                    ?.filter { it.isFile && !DownloadWorkflowEngine.isPartialOrTemporaryFile(it.name) }
                    .orEmpty()
                for (candidate in candidates) {
                    DownloadWorkflowManager.handleCandidateFile(
                        context = applicationContext,
                        file = candidate,
                        downloadRoot = dir,
                        requireMasterEnabled = true
                    )
                }
            }
        }
    }

    private fun handleObserverEvent(rootDir: File, relativePath: String?) {
        if (relativePath.isNullOrBlank()) return
        if (DownloadWorkflowEngine.isPartialOrTemporaryFile(relativePath)) return
        val candidate = File(rootDir, relativePath)
        serviceScope.launch {
            DownloadWorkflowManager.handleCandidateFile(
                context = applicationContext,
                file = candidate,
                downloadRoot = rootDir,
                requireMasterEnabled = true
            )
        }
    }

    private fun stopWatchingDirectory() {
        try {
            fileObserver?.stopWatching()
        } catch (_: Exception) {
            // Safe ignore on teardown
        }
        fileObserver = null
    }

    companion object {
        private const val TAG = "DownloadWorkflowService"
        private const val NOTIFICATION_ID = 6199
        private const val FALLBACK_SWEEP_INTERVAL_MS = 30_000L
        const val ACTION_STOP_SERVICE = "com.arwora.ACTION_STOP_DOWNLOAD_WORKFLOW"

        @Volatile
        internal var customWatchDirForTests: File? = null

        fun start(context: Context) {
            val intent = Intent(context, DownloadWorkflowService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start DownloadWorkflowService", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, DownloadWorkflowService::class.java))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop DownloadWorkflowService", e)
            }
        }
    }
}
