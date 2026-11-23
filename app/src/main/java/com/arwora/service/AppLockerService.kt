package com.arwora.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.arwora.MainActivity
import com.arwora.utils.AppLockerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppLockerService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var lastCheckedPackage: String = ""
    private var lastObservedForeground: String? = null
    private var lastEventTimestamp: Long = 0L

    companion object {
        const val NOTIFICATION_ID = 4001
        const val CHANNEL_ID = "app_locker_persistent_channel"
        const val ACTION_STOP_SERVICE = "com.arwora.service.AppLockerService.STOP"
        var isRunning = false

        fun isSelfPackage(pkg: String?, context: Context? = null): Boolean {
            if (pkg.isNullOrBlank()) return false
            val clean = pkg.trim()
            if (clean == "com.arworahyper.toolkit" ||
                clean == "com.arwora" ||
                clean == "com.arwora.service" ||
                clean.startsWith("com.arworahyper.toolkit") ||
                clean.startsWith("com.arwora.")
            ) {
                return true
            }
            if (context != null) {
                if (clean == context.packageName || clean == context.applicationContext.packageName) {
                    return true
                }
            }
            return false
        }

        fun start(context: Context) {
            val intent = Intent(context, AppLockerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("AppLockerService", "Failed to start service", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppLockerService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                try {
                    context.stopService(intent)
                } catch (_: Exception) {}
            }
        }
    }

    private fun isSelf(pkg: String?): Boolean {
        return isSelfPackage(pkg, this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
        }
        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Locker Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors and locks protected applications"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Locker Protection Active")
            .setContentText("Shielding protected apps with biometric & PIN security")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startMonitoringLoop() {
        serviceScope.launch {
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager

            while (isActive) {
                try {
                    // Check if master App Locker is enabled
                    if (!AppLockerManager.isLockerEnabled.value) {
                        lastCheckedPackage = ""
                        lastObservedForeground = null
                        delay(1500L)
                        continue
                    }

                    // Sanitize locked apps: exclude any self-package
                    val lockedApps = AppLockerManager.lockedPackages.value
                        .filterNot { isSelf(it) }
                        .toSet()

                    if (lockedApps.isEmpty()) {
                        lastCheckedPackage = ""
                        lastObservedForeground = null
                        delay(1500L)
                        continue
                    }

                    // Screen off or device keyguard locked check
                    val isScreenInteractive = powerManager?.isInteractive ?: true
                    val isKeyguardLocked = keyguardManager?.isKeyguardLocked ?: false
                    if (!isScreenInteractive || isKeyguardLocked) {
                        lastCheckedPackage = ""
                        lastObservedForeground = null
                        delay(500L)
                        continue
                    }

                    // Accurately extract the real foreground package from UsageEvents
                    val currentForeground = getForegroundPackage(usageStatsManager)

                    // EXCLUDE SELF-PACKAGE:
                    // If foreground package is the toolkit itself or any toolkit component,
                    // completely bypass/ignore so the toolkit never triggers the lock screen overlay.
                    if (isSelf(currentForeground)) {
                        delay(400L)
                        continue
                    }

                    if (!currentForeground.isNullOrBlank()) {
                        // Notify manager of active foreground transition to support immediate timeout revocation
                        AppLockerManager.onForegroundPackageChanged(currentForeground)

                        // Strictly compare foreground package against user's locked apps list
                        if (lockedApps.contains(currentForeground)) {
                            if (!AppLockerManager.isPackageUnlocked(currentForeground)) {
                                if (lastCheckedPackage != currentForeground) {
                                    lastCheckedPackage = currentForeground
                                    launchLockScreen(currentForeground)
                                }
                            }
                        } else {
                            // User switched to another external unlocked app or home launcher
                            lastCheckedPackage = ""
                        }
                    } else {
                        // In between app transitions
                        lastCheckedPackage = ""
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AppLockerService", "Error during monitor loop", e)
                }
                delay(350L)
            }
        }
    }

    private fun getForegroundPackage(usageStatsManager: UsageStatsManager?): String? {
        if (usageStatsManager == null) return null
        val now = System.currentTimeMillis()

        // 1. Query events in a sliding window
        val startTime = if (lastEventTimestamp > 0L) {
            maxOf(now - 10000L, lastEventTimestamp - 1000L)
        } else {
            now - 20000L
        }

        val usageEvents = try {
            usageStatsManager.queryEvents(startTime, now)
        } catch (e: Exception) {
            android.util.Log.e("AppLockerService", "Failed to query usage events", e)
            null
        } ?: return lastObservedForeground

        var latestResumedPkg: String? = null
        var latestResumedTime = 0L
        var latestPausedOrStoppedPkg: String? = null
        var latestPausedOrStoppedTime = 0L

        val event = UsageEvents.Event()
        var maxEventTimestamp = lastEventTimestamp

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val eventTime = event.timeStamp
            if (eventTime > maxEventTimestamp) {
                maxEventTimestamp = eventTime
            }

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED, 1 -> { // 1 = MOVE_TO_FOREGROUND / ACTIVITY_RESUMED
                    if (eventTime >= latestResumedTime) {
                        latestResumedPkg = pkg
                        latestResumedTime = eventTime
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED, 2, // 2 = MOVE_TO_BACKGROUND / ACTIVITY_PAUSED
                23 -> { // 23 = ACTIVITY_STOPPED (API 29+)
                    if (eventTime >= latestPausedOrStoppedTime) {
                        latestPausedOrStoppedPkg = pkg
                        latestPausedOrStoppedTime = eventTime
                    }
                }
            }
        }

        if (maxEventTimestamp > lastEventTimestamp) {
            lastEventTimestamp = maxEventTimestamp
        }

        // If a package was resumed
        if (latestResumedPkg != null) {
            // If the latest resumed package was subsequently paused or stopped with no newer resumed package:
            if (latestPausedOrStoppedPkg == latestResumedPkg && latestPausedOrStoppedTime >= latestResumedTime) {
                lastObservedForeground = null
                return null
            }
            lastObservedForeground = latestResumedPkg
            return latestResumedPkg
        }

        // No new resume/pause events in this window:
        // Keep the last observed foreground package. Do NOT fall back to queryUsageStats.lastTimeUsed
        // because queryUsageStats returns recently stopped/paused apps rather than the real foreground app.
        return lastObservedForeground
    }

    private fun launchLockScreen(lockedPackage: String) {
        if (isSelf(lockedPackage)) return
        if (!AppLockerManager.isLockerEnabled.value) return
        if (!AppLockerManager.isAppLocked(lockedPackage)) return
        if (AppLockerManager.isPackageUnlocked(lockedPackage)) return

        try {
            val intent = Intent(this, com.arwora.AppLockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(com.arwora.AppLockActivity.EXTRA_PACKAGE_NAME, lockedPackage)
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("AppLockerService", "Could not launch lock activity", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceJob.cancel()
    }
}
