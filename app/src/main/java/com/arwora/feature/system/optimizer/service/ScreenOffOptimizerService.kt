package com.arwora.feature.system.optimizer.service

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.NativeAdbManager
import kotlinx.coroutines.*

class ScreenOffOptimizerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "ScreenOffOptimizerService"
        private const val NOTIFICATION_ID = 9021
        private const val CHANNEL_ID = "screen_off_optimizer_channel"
        const val ACTION_EXECUTE_ROUTINE = "com.arwora.ACTION_EXECUTE_OPTIMIZATION_ROUTINE"

        fun start(context: Context) {
            val intent = Intent(context, ScreenOffOptimizerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScreenOffOptimizerService::class.java)
            context.stopService(intent)
        }
    }

    private var screenReceiver: BroadcastReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var alarmManager: AlarmManager? = null
    private var pendingIntent: PendingIntent? = null
    private var isSilentActivatedByRoutine = false

    override fun onCreate() {
        super.onCreate()
        ScreenOffOptimizerManager.syncServiceState(true)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Smart Screen-Off Automation Active"))

        alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Arwora:ScreenOffOptimizerWakeLock")

        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Smart Screen-Off Automation Active"))
        if (intent?.action == ACTION_EXECUTE_ROUTINE) {
            executeOptimizationTasks()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        ScreenOffOptimizerManager.syncServiceState(false)
        unregisterScreenReceiver()
        cancelAlarm()
        releaseWakeLock()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen-Off Automation Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps screen-off cleanup monitor active in background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen-Off Optimizer Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // Acquire temporary WakeLock to ensure execution starts during Doze
                val pm = context?.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val tempWakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Arwora:ScreenOffReceiverLock")
                tempWakeLock?.acquire(15 * 1000L) // 15 seconds

                serviceScope.launch {
                    try {
                        when (intent?.action) {
                            Intent.ACTION_SCREEN_OFF -> {
                                Log.d(TAG, "Screen OFF detected. Triggering optimization flow on IO thread.")
                                
                                // Immediate DND disable (Standard API)
                                val nm = context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm?.isNotificationPolicyAccessGranted == true) {
                                    try {
                                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                                    } catch (_: Exception) {}
                                }

                                executeOptimizationTasks()
                                scheduleScreenOffAlarm()
                            }
                            Intent.ACTION_SCREEN_ON -> {
                                Log.d(TAG, "Screen ON detected. Routine state clearing.")
                                withContext(Dispatchers.Main) {
                                    cancelAlarm()
                                    restoreRingModeOnScreenOn()
                                    releaseWakeLock()
                                }
                            }
                        }
                    } finally {
                        withContext(Dispatchers.Main) {
                            if (tempWakeLock?.isHeld == true) tempWakeLock.release()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
        }
        screenReceiver = null
    }

    private fun scheduleScreenOffAlarm() {
        val delaySecs = ScreenOffOptimizerManager.delaySeconds.value
        val triggerAtMillis = System.currentTimeMillis() + (delaySecs * 1000L)

        val intent = Intent(this, ScreenOffOptimizerService::class.java).apply {
            action = ACTION_EXECUTE_ROUTINE
        }
        pendingIntent = PendingIntent.getService(
            this,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            acquireWakeLock()
            if (delaySecs <= 0L) {
                executeOptimizationTasks()
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager?.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent!!
                    )
                } else {
                    alarmManager?.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent!!
                    )
                }
                ScreenOffOptimizerManager.updateLog("Screen off detected. Scheduled cleanup in ${delaySecs}s...")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm: ${e.message}", e)
            releaseWakeLock()
        }
    }

    private fun cancelAlarm() {
        if (pendingIntent != null && alarmManager != null) {
            alarmManager?.cancel(pendingIntent!!)
            pendingIntent = null
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(15 * 60 * 1000L) // 15 minutes max safety timeout
                Log.d(TAG, "WakeLock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release WakeLock: ${e.message}")
        }
    }

    fun executeOptimizationTasks() {
        serviceScope.launch {
            acquireWakeLock()
            try {
                val actions = mutableListOf<String>()

                // 1. Memory and Apps cleanup
                if (ScreenOffOptimizerManager.killBackgroundApps.value) {
                    val killedCount = performBackgroundAppsCleanup()
                    System.gc()
                    Runtime.getRuntime().gc()
                    deleteDir(cacheDir)
                    if (killedCount > 0) {
                        actions.add("Killed $killedCount Background Apps")
                    } else {
                        actions.add("Memory Optimized")
                    }
                }

                // 2. Audio profile enforcement
                if (ScreenOffOptimizerManager.enableSilentMode.value) {
                    val audioSummary = enforceNormalRingerAndMaxVolumes()
                    actions.add(audioSummary)
                }

                // 3. Radio shutdown (Bluetooth, Location)
                if (ScreenOffOptimizerManager.turnOffRadios.value) {
                    val radioSummary = performRadioShutdown()
                    actions.add(radioSummary)
                }

                ScreenOffOptimizerManager.incrementOptimizationsCount()
                val summary = if (actions.isNotEmpty()) {
                    "Optimized: ${actions.joinToString(" • ")}"
                } else {
                    "Optimization cycle finished"
                }
                ScreenOffOptimizerManager.updateLog(summary)
            } catch (e: Exception) {
                Log.e(TAG, "Background optimization failed: ${e.message}")
            } finally {
                releaseWakeLock()
            }
        }
    }

    private fun performBackgroundAppsCleanup(): Int {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0
        val pm = packageManager
        val runningProcesses = am.runningAppProcesses ?: return 0
        
        val useAdb = ScreenOffOptimizerManager.useWirelessAdb.value
        val hasAdb = useAdb && AdbShellExecutor.isPrivilegedShellReady(this)

        val protectedPackages = setOf(
            packageName, // Arwora Toolkit
            "android",
            "com.android.systemui",
            "com.google.android.gms",
            "com.google.android.inputmethod.latin",
            "com.android.vending",
            "com.google.android.apps.maps",
            "com.android.settings",
            "com.android.phone",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.oppo.launcher",
            "com.huawei.android.launcher",
            "com.android.chrome",
            "com.google.android.apps.wellbeing"
        )

        var killedCount = 0
        val handledPackages = mutableSetOf<String>()

        for (proc in runningProcesses) {
            // Only target background or cached processes
            if (proc.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) continue
            
            for (pkg in proc.pkgList) {
                if (protectedPackages.contains(pkg) || handledPackages.contains(pkg)) continue
                
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    // Skip system apps strictly unless they are non-critical third-party ones
                    val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    
                    if (isSystemApp && !isUpdatedSystemApp) continue
                    
                    if (hasAdb) {
                        // Aggressive cleanup for non-critical apps via Wireless ADB
                        if (runAdbCommand("am force-stop $pkg")) {
                            killedCount++
                            handledPackages.add(pkg)
                            Log.d(TAG, "Wireless ADB cleanup: Force-stopped $pkg")
                        }
                    } else {
                        // Standard API kill (safe fallback)
                        am.killBackgroundProcesses(pkg)
                        killedCount++
                        handledPackages.add(pkg)
                    }
                } catch (_: Exception) {}
            }
        }
        
        // Final memory trim if Wireless ADB is available
        if (hasAdb) {
            runAdbCommand("am kill-all")
        }

        return killedCount
    }

    private fun performRadioShutdown(): String {
        val useAdb = ScreenOffOptimizerManager.useWirelessAdb.value
        val hasAdb = useAdb && AdbShellExecutor.isPrivilegedShellReady(this)

        var btSuccess = false
        var locSuccess = false

        if (hasAdb) {
            btSuccess = runAdbCommand("cmd bluetooth_manager disable") ||
                        runAdbCommand("service call bluetooth_manager 6") ||
                        runAdbCommand("svc bluetooth disable")
            locSuccess = runAdbCommand("cmd location set-location-enabled false") ||
                         runAdbCommand("settings put secure location_mode 0")
        }

        if (!btSuccess) {
            // Try Root shell
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "svc bluetooth disable"))
                if (process.waitFor() == 0) btSuccess = true
            } catch (_: Exception) {}
        }

        if (!btSuccess) {
            // Try standard shell command
            try {
                val process = Runtime.getRuntime().exec("svc bluetooth disable")
                if (process.waitFor() == 0) btSuccess = true
            } catch (_: Exception) {}
        }

        if (!btSuccess) {
            // Fallback: Standard BluetoothManager / BluetoothAdapter disable API
            try {
                val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
                val btAdapter = btManager?.adapter
                if (btAdapter?.isEnabled == true) {
                    @Suppress("DEPRECATION")
                    btSuccess = btAdapter.disable()
                }
            } catch (e: Exception) {
                Log.e(TAG, "BluetoothAdapter disable failed: ${e.message}")
            }
        }

        // Location shutdown fallback if needed
        if (!locSuccess) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put secure location_mode 0"))
                if (process.waitFor() == 0) locSuccess = true
            } catch (_: Exception) {}
        }

        return when {
            hasAdb -> "Radios: Bluetooth & Location Disabled (Wireless ADB)"
            btSuccess -> "Radios: Bluetooth Disabled (Root/Shell)"
            else -> "Radios: Optimization Attempted"
        }
    }

    private fun runAdbCommand(command: String): Boolean {
        return try {
            AdbShellExecutor.executeBlocking(command, context = this).success
        } catch (e: Exception) {
            Log.e(TAG, "Privileged CMD failed ($command): ${e.message}")
            false
        }
    }

    /**
     * Strictly enforces Normal Ringer Mode, disables Do Not Disturb (DND),
     * and sets all system audio streams (Ring, Notification, Alarm, Media, System)
     * to their absolute maximum levels whenever the screen turns OFF.
     */
    fun enforceNormalRingerAndMaxVolumes(): String {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val hasPolicyAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager?.isNotificationPolicyAccessGranted == true
        } else {
            true
        }
        ScreenOffOptimizerManager.checkAndSyncDndPermission(this)

        if (audioManager == null) {
            Log.e(TAG, "AudioManager not available on device")
            return "AudioManager Unavailable"
        }

        // Save previous volumes and ringer mode once before overriding (for Screen-On restoration)
        if (!isSilentActivatedByRoutine) {
            try {
                ScreenOffOptimizerManager.savedRingerMode = audioManager.ringerMode
                ScreenOffOptimizerManager.savedMediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                ScreenOffOptimizerManager.savedRingtoneVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                ScreenOffOptimizerManager.savedNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
                ScreenOffOptimizerManager.savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                ScreenOffOptimizerManager.savedSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read prior audio state: ${e.message}")
            }
        }

        // 1. DND Forced Disable Logic:
        // Access NotificationManager and force-disable DND mode completely by setting:
        // notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        var dndStatus = "DND Forced OFF"
        if (hasPolicyAccess && notificationManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    Log.d(TAG, "DND Disabled: interruptionFilter set to INTERRUPTION_FILTER_ALL")
                    dndStatus = "DND Forced OFF (INTERRUPTION_FILTER_ALL)"
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException disabling DND filter: ${e.message}")
                dndStatus = "DND Permission Denied"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to force-disable DND: ${e.message}")
                dndStatus = "DND Override Error: ${e.message}"
            }
        } else {
            Log.w(TAG, "Notification policy access not granted; DND override skipped")
            dndStatus = "DND Skipped (Missing Access)"
        }

        // 2. Audio & Ringer Mode Enforcements:
        // Change the system ringer mode strictly to Normal Mode:
        // audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        var ringerStatus = "Forced Normal Ringer"
        try {
            audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            Log.d(TAG, "Ringer mode set to RINGER_MODE_NORMAL")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException forcing ringer mode NORMAL: ${e.message}")
            ringerStatus = "Ringer Security Denied"
        } catch (e: Exception) {
            Log.e(TAG, "Failed forcing ringer mode NORMAL: ${e.message}")
            ringerStatus = "Ringer Error: ${e.message}"
        }

        // Re-enforce DND OFF immediately after setting ringer mode to prevent system from re-engaging DND
        if (hasPolicyAccess && notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            } catch (_: Exception) {}
        }

        // 3. Full Volume Maximization for ALL Streams:
        // Attempt Wireless ADB maximization first if enabled, fallback to standard API
        val useAdb = ScreenOffOptimizerManager.useWirelessAdb.value
        val hasAdb = useAdb && AdbShellExecutor.isPrivilegedShellReady(this)

        var streamsMaxed = 0
        if (hasAdb) {
            val adbSuccess = executeAdbVolumeMaximization(audioManager)
            if (adbSuccess) {
                streamsMaxed = 5
                Log.d(TAG, "All streams maxed via Wireless ADB successfully.")
            }
        }

        if (streamsMaxed == 0) {
            val targetStreams = listOf(
                AudioManager.STREAM_RING to "Ringtone (STREAM_RING)",
                AudioManager.STREAM_NOTIFICATION to "Notification (STREAM_NOTIFICATION)",
                AudioManager.STREAM_ALARM to "Alarm (STREAM_ALARM)",
                AudioManager.STREAM_MUSIC to "Media (STREAM_MUSIC)",
                AudioManager.STREAM_SYSTEM to "System (STREAM_SYSTEM)"
            )

            for ((stream, name) in targetStreams) {
                try {
                    // Explicitly unmute stream on Android M+ to clear any muted/silent flags
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
                        } catch (_: Exception) {}
                    }
                    val maxVolume = audioManager.getStreamMaxVolume(stream)
                    audioManager.setStreamVolume(stream, maxVolume, 0)
                    streamsMaxed++
                    Log.d(TAG, "Maxed out stream $name to $maxVolume with flag 0")
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException setting $name max volume: ${e.message}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed setting $name max volume: ${e.message}")
                }
            }
        }

        // Final DND guard: Ensure DND filter is still INTERRUPTION_FILTER_ALL
        if (hasPolicyAccess && notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            } catch (_: Exception) {}
        }

        isSilentActivatedByRoutine = true
        val summary = "Audio: $ringerStatus • $dndStatus • $streamsMaxed/5 Streams Maxed ${if (hasAdb) "(Wireless ADB)" else ""}"
        ScreenOffOptimizerManager.updateLog(summary)
        return summary
    }

    private fun executeAdbVolumeMaximization(audioManager: AudioManager?): Boolean {
        if (audioManager == null) return false
        var success = false
        // Stream mapping for 'cmd audio set-stream-volume':
        // 1: SYSTEM, 2: RING, 3: MUSIC, 4: ALARM, 5: NOTIFICATION
        val targetStreams = listOf(
            AudioManager.STREAM_RING to 2,
            AudioManager.STREAM_NOTIFICATION to 5,
            AudioManager.STREAM_ALARM to 4,
            AudioManager.STREAM_MUSIC to 3,
            AudioManager.STREAM_SYSTEM to 1
        )
        
        for ((streamType, streamId) in targetStreams) {
            try {
                val maxVolume = audioManager.getStreamMaxVolume(streamType)
                val command = "cmd audio set-stream-volume $streamId $maxVolume"
                if (runAdbCommand(command)) success = true
            } catch (e: Exception) {
                Log.e(TAG, "Wireless ADB volume command failed for stream $streamId: ${e.message}")
            }
        }
        return success
    }

    private fun restoreRingModeOnScreenOn() {
        if (ScreenOffOptimizerManager.disableSilentMode.value || isSilentActivatedByRoutine) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val hasPolicyAccess = notificationManager?.isNotificationPolicyAccessGranted == true
            
            if (hasPolicyAccess) {
                try {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (audioManager != null) {
                        // Removed ringer mode and volume restoration to prevent switching back to Vibration/Silent
                        // The optimized audio profile (Normal Mode & Max Volume) is now respected and maintained.
                        
                        isSilentActivatedByRoutine = false
                        ScreenOffOptimizerManager.updateLog("Screen turned back ON: Optimized Audio Profile Maintained")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear routine state on screen ON: ${e.message}")
                }
            } else {
                ScreenOffOptimizerManager.updateLog("Screen turned ON (Routine state cleared)")
            }
        } else {
            ScreenOffOptimizerManager.updateLog("Screen turned back ON (No routine active)")
        }
    }

    private fun executeRootOrFallbackRadiosShutdown(): Boolean {
        var success = false
        val commands = arrayOf(
            "svc bluetooth disable",
            "settings put secure location_mode 0"
        )
        for (cmd in commands) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                val exitCode = process.waitFor()
                if (exitCode == 0) {
                    success = true
                }
            } catch (_: Exception) {
                try {
                    val process = Runtime.getRuntime().exec(cmd)
                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        success = true
                    }
                } catch (_: Exception) {}
            }
        }
        return success
    }

    private fun deleteDir(dir: java.io.File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (i in children.indices) {
                    deleteDir(java.io.File(dir, children[i]))
                }
            }
            return dir.delete()
        } else if (dir != null && dir.isFile) {
            return dir.delete()
        } else {
            return false
        }
    }
}
