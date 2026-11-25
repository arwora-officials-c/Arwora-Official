package com.arwora.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arwora.AppLockActivity
import com.arwora.MainActivity
import com.arwora.receiver.AppInstallReceiver
import com.arwora.receiver.UninstallReceiver
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.AppLockerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Coordinates real-time monitoring of newly installed packages and uninstallation requests
 * when Advanced Security guards (Install Block / Uninstall Protection) are active.
 */
class AppInstallMonitorService : Service() {

    private var dynamicInstallReceiver: AppInstallReceiver? = null
    private var dynamicPackageRemovedReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        AppLockerManager.init(applicationContext)
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        registerDynamicReceivers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun registerDynamicReceivers() {
        if (dynamicInstallReceiver == null) {
            dynamicInstallReceiver = runCatching { AppInstallReceiver.registerDynamic(this) }.getOrNull()
        }
        if (dynamicPackageRemovedReceiver == null) {
            val receiver = UninstallReceiver()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                addDataScheme("package")
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    applicationContext.registerReceiver(receiver, filter)
                }
                dynamicPackageRemovedReceiver = receiver
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Install & Uninstall Security Guard",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors new package installs and protected app removal attempts"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            4012,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Advanced App Guard Active")
            .setContentText("Monitoring new app installs & uninstall protection")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        AppInstallReceiver.unregisterDynamic(this, dynamicInstallReceiver)
        dynamicInstallReceiver = null
        dynamicPackageRemovedReceiver?.let {
            runCatching { applicationContext.unregisterReceiver(it) }
        }
        dynamicPackageRemovedReceiver = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AppInstallMonitor"
        const val NOTIFICATION_ID = 4012
        const val CHANNEL_ID = "app_install_monitor_channel"
        const val ACTION_STOP = "com.arwora.service.AppInstallMonitorService.STOP"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var lastInstallPromptMs: Long = 0L

        private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun start(context: Context) {
            val intent = Intent(context, AppInstallMonitorService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure {
                Log.e(TAG, "Failed to start AppInstallMonitorService", it)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppInstallMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
                .onFailure { runCatching { context.stopService(intent) } }
        }

        fun syncWithPreferences(context: Context) {
            AppLockerManager.init(context)
            val needed = AppLockerManager.isInstallBlockEnabled.value ||
                AppLockerManager.isUninstallProtectionEnabled.value
            if (needed) {
                start(context)
            } else {
                stop(context)
            }
        }

        /**
         * Handles pre-install installer UI detection or post-install [Intent.ACTION_PACKAGE_ADDED].
         * Launches [AppLockActivity] in [AppLockActivity.MODE_INSTALL_BLOCK].
         */
        fun handleNewInstallDetected(
            context: Context,
            packageName: String?,
            bypassCooldown: Boolean = false
        ): Boolean {
            val appContext = context.applicationContext
            AppLockerManager.init(appContext)
            if (!AppLockerManager.isInstallBlockEnabled.value) return false

            val targetPkg = packageName?.trim().orEmpty()
            if (targetPkg.isNotEmpty() && AppLockerManager.isSelfPackageName(targetPkg)) return false
            if (targetPkg.isNotEmpty() && AppLockerManager.isInstallAuthorized(targetPkg)) return false

            val now = System.currentTimeMillis()
            if (!bypassCooldown && now - lastInstallPromptMs < 1200L) {
                return true
            }
            lastInstallPromptMs = now

            return try {
                val lockIntent = Intent(appContext, AppLockActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    putExtra(AppLockActivity.EXTRA_PACKAGE_NAME, targetPkg.ifBlank { INSTALLER_PLACEHOLDER_PKG })
                    putExtra(AppLockActivity.EXTRA_LOCK_MODE, AppLockActivity.MODE_INSTALL_BLOCK)
                }
                appContext.startActivity(lockIntent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Unable to launch install block activity", e)
                false
            }
        }

        /**
         * Rejects an unauthorized newly installed package by running privileged uninstall via Shizuku/ADB,
         * or launching system [Intent.ACTION_DELETE] fallback when unprivileged.
         */
        suspend fun rejectAndUninstallPackage(context: Context, packageName: String): Boolean {
            if (packageName.isBlank() ||
                packageName == INSTALLER_PLACEHOLDER_PKG ||
                AppLockerManager.isSelfPackageName(packageName)
            ) {
                return false
            }

            if (AdbShellExecutor.isPrivilegedShellReady(context)) {
                val res = AdbShellExecutor.execute("pm uninstall $packageName", context = context)
                if (res.success) return true
            }

            return try {
                val deleteIntent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(deleteIntent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request uninstall for blocked app $packageName", e)
                false
            }
        }

        fun rejectAndUninstallAsync(context: Context, packageName: String) {
            val appCtx = context.applicationContext
            monitorScope.launch {
                rejectAndUninstallPackage(appCtx, packageName)
            }
        }

        const val INSTALLER_PLACEHOLDER_PKG = "com.android.packageinstaller.pending"
    }
}
