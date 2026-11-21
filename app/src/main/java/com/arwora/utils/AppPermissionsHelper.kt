package com.arwora.utils

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

enum class ManagedPermissionCategory {
    MICROPHONE,
    STORAGE,
    NOTIFICATIONS,
    NOTIFICATION_LISTENER,
    OVERLAY,
    CAMERA,
    WIRELESS_ADB,
    USAGE_STATS
}

data class ManagedAppPermission(
    val id: String,
    val title: String,
    val subtitle: String,
    val permissionKey: String,
    val category: ManagedPermissionCategory,
    val isGranted: Boolean
)

/**
 * Evaluates Arwora Hyper Toolkit's active permission & system status states and
 * provides runtime permission + Android system settings navigation helpers.
 */
object AppPermissionsHelper {

    fun inspectPermissions(context: Context): List<ManagedAppPermission> {
        val micGranted = isMicrophoneGranted(context)
        val storageGranted = isStorageGranted(context)
        val notificationsGranted = areNotificationsGranted(context)
        val listenerGranted = isNotificationListenerGranted(context)
        val overlayGranted = isOverlayGranted(context)
        val cameraGranted = isCameraGranted(context)
        val wirelessAdbGranted = isWirelessAdbGranted(context)
        val usageStatsGranted = isUsageStatsGranted(context)

        return buildPermissionList(
            sdkInt = Build.VERSION.SDK_INT,
            micGranted = micGranted,
            storageGranted = storageGranted,
            notificationsGranted = notificationsGranted,
            listenerGranted = listenerGranted,
            overlayGranted = overlayGranted,
            cameraGranted = cameraGranted,
            wirelessAdbGranted = wirelessAdbGranted,
            usageStatsGranted = usageStatsGranted
        )
    }

    internal fun buildPermissionList(
        sdkInt: Int,
        micGranted: Boolean,
        storageGranted: Boolean,
        notificationsGranted: Boolean,
        listenerGranted: Boolean,
        overlayGranted: Boolean,
        cameraGranted: Boolean = false,
        wirelessAdbGranted: Boolean = false,
        usageStatsGranted: Boolean = false
    ): List<ManagedAppPermission> {
        val storageKey = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            "READ_MEDIA_IMAGES / VIDEO"
        } else {
            "READ_EXTERNAL_STORAGE"
        }
        val notificationsKey = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            "POST_NOTIFICATIONS"
        } else {
            "APP_NOTIFICATIONS"
        }

        return listOf(
            ManagedAppPermission(
                id = "microphone",
                title = "Microphone / Audio Access",
                subtitle = "Used locally for Sound Direction acoustic analyzer",
                permissionKey = "RECORD_AUDIO",
                category = ManagedPermissionCategory.MICROPHONE,
                isGranted = micGranted
            ),
            ManagedAppPermission(
                id = "storage",
                title = "Storage / File Access",
                subtitle = "Used locally for Storage Analyzer, APK & metadata tools",
                permissionKey = storageKey,
                category = ManagedPermissionCategory.STORAGE,
                isGranted = storageGranted
            ),
            ManagedAppPermission(
                id = "notifications",
                title = "App Notifications",
                subtitle = "Alerts for Battery Alarm, Wi-Fi monitor & optimizers",
                permissionKey = notificationsKey,
                category = ManagedPermissionCategory.NOTIFICATIONS,
                isGranted = notificationsGranted
            ),
            ManagedAppPermission(
                id = "notification_listener",
                title = "Notifications / Listener Access",
                subtitle = "Required for local Notification History logger",
                permissionKey = "BIND_NOTIFICATION_LISTENER_SERVICE",
                category = ManagedPermissionCategory.NOTIFICATION_LISTENER,
                isGranted = listenerGranted
            ),
            ManagedAppPermission(
                id = "overlay",
                title = "Display Over Other Apps",
                subtitle = "Required for Floating Toolkit bubble & overlay controls",
                permissionKey = "SYSTEM_ALERT_WINDOW",
                category = ManagedPermissionCategory.OVERLAY,
                isGranted = overlayGranted
            ),
            ManagedAppPermission(
                id = "camera",
                title = "Camera Permission",
                subtitle = "Required for Offline QR & Barcode Scanner",
                permissionKey = "CAMERA",
                category = ManagedPermissionCategory.CAMERA,
                isGranted = cameraGranted
            ),
            ManagedAppPermission(
                id = "wireless_adb",
                title = "Wireless ADB Status",
                subtitle = "Required for ADB tools, spoofing & system actions",
                permissionKey = "WIRELESS_ADB",
                category = ManagedPermissionCategory.WIRELESS_ADB,
                isGranted = wirelessAdbGranted
            ),
            ManagedAppPermission(
                id = "usage_stats",
                title = "Usage Stats Access",
                subtitle = "Required for App Usage Stats monitoring in System dashboard",
                permissionKey = "PACKAGE_USAGE_STATS",
                category = ManagedPermissionCategory.USAGE_STATS,
                isGranted = usageStatsGranted
            )
        )
    }

    fun isMicrophoneGranted(context: Context): Boolean = try {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun isStorageGranted(context: Context): Boolean = try {
        val manageAllGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
        if (manageAllGranted) {
            true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    } catch (_: Throwable) {
        false
    }

    fun areNotificationsGranted(context: Context): Boolean = try {
        val channelEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            channelEnabled && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            channelEnabled
        }
    } catch (_: Throwable) {
        false
    }

    fun isNotificationListenerGranted(context: Context): Boolean = try {
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    } catch (_: Throwable) {
        false
    }

    fun isOverlayGranted(context: Context): Boolean = try {
        Settings.canDrawOverlays(context)
    } catch (_: Throwable) {
        false
    }

    fun isCameraGranted(context: Context): Boolean = try {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun isWirelessAdbGranted(context: Context): Boolean = try {
        AdbShellExecutor.isPrivilegedShellReady(context) ||
            NativeAdbManager.isWirelessDebuggingSettingEnabled(context)
    } catch (_: Throwable) {
        false
    }

    fun isUsageStatsGranted(context: Context): Boolean = try {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Throwable) {
        false
    }

    fun runtimePermissionsForCategory(
        category: ManagedPermissionCategory,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): List<String> = when (category) {
        ManagedPermissionCategory.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
        ManagedPermissionCategory.CAMERA -> listOf(Manifest.permission.CAMERA)
        ManagedPermissionCategory.STORAGE -> if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        ManagedPermissionCategory.NOTIFICATIONS -> if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
        ManagedPermissionCategory.NOTIFICATION_LISTENER,
        ManagedPermissionCategory.OVERLAY,
        ManagedPermissionCategory.WIRELESS_ADB,
        ManagedPermissionCategory.USAGE_STATS -> emptyList()
    }

    internal fun buildMissingRuntimePermissions(
        permissions: List<ManagedAppPermission>,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): List<String> = permissions
        .filter { !it.isGranted }
        .flatMap { runtimePermissionsForCategory(it.category, sdkInt) }
        .distinct()

    fun collectMissingRuntimePermissions(
        context: Context,
        permissions: List<ManagedAppPermission> = inspectPermissions(context),
        sdkInt: Int = Build.VERSION.SDK_INT
    ): List<String> = buildMissingRuntimePermissions(permissions, sdkInt)
        .filter { permission ->
            try {
                ContextCompat.checkSelfPermission(context, permission) !=
                    PackageManager.PERMISSION_GRANTED
            } catch (_: Throwable) {
                true
            }
        }

    fun findNextMissingSystemCategory(
        permissions: List<ManagedAppPermission>
    ): ManagedPermissionCategory? {
        val preferredSystemOrder = listOf(
            ManagedPermissionCategory.USAGE_STATS,
            ManagedPermissionCategory.NOTIFICATION_LISTENER,
            ManagedPermissionCategory.OVERLAY,
            ManagedPermissionCategory.WIRELESS_ADB
        )
        preferredSystemOrder.forEach { category ->
            val match = permissions.firstOrNull { it.category == category && !it.isGranted }
            if (match != null) return match.category
        }
        return permissions.firstOrNull { !it.isGranted }?.category
    }

    fun createSettingsIntent(context: Context, category: ManagedPermissionCategory): Intent {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        return when (category) {
            ManagedPermissionCategory.MICROPHONE,
            ManagedPermissionCategory.STORAGE,
            ManagedPermissionCategory.CAMERA -> Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                packageUri
            )
            ManagedPermissionCategory.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                }
            }
            ManagedPermissionCategory.NOTIFICATION_LISTENER -> Intent(
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            )
            ManagedPermissionCategory.OVERLAY -> Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                packageUri
            )
            ManagedPermissionCategory.WIRELESS_ADB -> Intent(
                Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            )
            ManagedPermissionCategory.USAGE_STATS -> Intent(
                Settings.ACTION_USAGE_ACCESS_SETTINGS
            )
        }.apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun openPermissionSettings(context: Context, category: ManagedPermissionCategory): Boolean {
        return try {
            context.startActivity(createSettingsIntent(context, category))
            true
        } catch (_: Exception) {
            try {
                val fallback = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).apply {
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(fallback)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    data class BulkPermissionAdbResult(
        val commandsAttempted: Int,
        val commandsSucceeded: Int
    )

    fun buildAdbGrantCommands(
        packageName: String,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): List<String> = buildList {
        add("pm grant $packageName ${Manifest.permission.CAMERA}")
        add("pm grant $packageName ${Manifest.permission.RECORD_AUDIO}")
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            add("pm grant $packageName ${Manifest.permission.READ_MEDIA_IMAGES}")
            add("pm grant $packageName ${Manifest.permission.READ_MEDIA_VIDEO}")
            add("pm grant $packageName ${Manifest.permission.POST_NOTIFICATIONS}")
        } else {
            add("pm grant $packageName ${Manifest.permission.READ_EXTERNAL_STORAGE}")
        }
        add("appops set $packageName SYSTEM_ALERT_WINDOW allow")
        add("appops set $packageName GET_USAGE_STATS allow")
        add("cmd notification allow_listener $packageName/com.arwora.service.NotificationHistoryService")
    }

    fun buildAdbRevokeCommands(
        packageName: String,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): List<String> = buildList {
        add("pm revoke $packageName ${Manifest.permission.CAMERA}")
        add("pm revoke $packageName ${Manifest.permission.RECORD_AUDIO}")
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            add("pm revoke $packageName ${Manifest.permission.READ_MEDIA_IMAGES}")
            add("pm revoke $packageName ${Manifest.permission.READ_MEDIA_VIDEO}")
            add("pm revoke $packageName ${Manifest.permission.POST_NOTIFICATIONS}")
        } else {
            add("pm revoke $packageName ${Manifest.permission.READ_EXTERNAL_STORAGE}")
        }
        add("appops set $packageName SYSTEM_ALERT_WINDOW ignore")
        add("appops set $packageName GET_USAGE_STATS ignore")
        add("cmd notification disallow_listener $packageName/com.arwora.service.NotificationHistoryService")
    }

    fun allRevocableRuntimePermissions(
        sdkInt: Int = Build.VERSION.SDK_INT
    ): List<String> = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_IMAGES)
            add(Manifest.permission.READ_MEDIA_VIDEO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun findRemainingSpecialPermissions(
        permissions: List<ManagedAppPermission>
    ): List<ManagedAppPermission> {
        val specialCategories = setOf(
            ManagedPermissionCategory.OVERLAY,
            ManagedPermissionCategory.NOTIFICATION_LISTENER,
            ManagedPermissionCategory.USAGE_STATS,
            ManagedPermissionCategory.WIRELESS_ADB
        )
        return permissions.filter { !it.isGranted && it.category in specialCategories }
    }

    suspend fun grantAllPermissionsViaAdb(context: Context): BulkPermissionAdbResult {
        com.arwora.service.NotificationHistoryService.setListenerPaused(context, false)
        val commands = buildAdbGrantCommands(context.packageName, Build.VERSION.SDK_INT)
        var succeeded = 0
        for (cmd in commands) {
            val result = try {
                AdbShellExecutor.execute(cmd, context = context)
            } catch (_: Throwable) {
                null
            }
            if (result?.success == true) {
                succeeded++
            }
        }
        return BulkPermissionAdbResult(commands.size, succeeded)
    }

    suspend fun revokeAllPermissionsViaAdb(context: Context): BulkPermissionAdbResult {
        val commands = buildAdbRevokeCommands(context.packageName, Build.VERSION.SDK_INT)
        var succeeded = 0
        for (cmd in commands) {
            val result = try {
                AdbShellExecutor.execute(cmd, context = context)
            } catch (_: Throwable) {
                null
            }
            if (result?.success == true) {
                succeeded++
            }
        }
        return BulkPermissionAdbResult(commands.size, succeeded)
    }

    fun stopPermissionDependentServices(context: Context) {
        try {
            com.arwora.service.NotificationHistoryService.setListenerPaused(context, true)
        } catch (_: Throwable) {
        }
        try {
            com.arwora.service.FloatingWindowService.stop(context)
        } catch (_: Throwable) {
        }
        try {
            val stopAutoClickerIntent = Intent(
                context,
                com.arwora.service.AutoClickerOverlayService::class.java
            ).apply {
                action = com.arwora.service.AutoClickerOverlayService.ACTION_STOP_OVERLAY
            }
            context.stopService(stopAutoClickerIntent)
        } catch (_: Throwable) {
        }
        try {
            FakeGpsManager.stopMockLocation(context)
        } catch (_: Throwable) {
        }
    }

    fun requestSelfRevokeRuntimePermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            val grantedPerms = allRevocableRuntimePermissions(Build.VERSION.SDK_INT).filter { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
            if (grantedPerms.isNotEmpty()) {
                context.revokeSelfPermissionsOnKill(grantedPerms)
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }
}
