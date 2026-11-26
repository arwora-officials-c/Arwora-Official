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
import android.provider.DocumentsContract
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
    USAGE_STATS,
    NEARBY_DEVICES
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

    private const val PREFS_PERMISSIONS = "arwora_permissions_prefs"
    private const val KEY_SAF_TREE_URI = "saf_tree_uri"

    fun inspectPermissions(context: Context): List<ManagedAppPermission> {
        val micGranted = isMicrophoneGranted(context)
        val storageGranted = isStorageGranted(context)
        val notificationsGranted = areNotificationsGranted(context)
        val listenerGranted = isNotificationListenerGranted(context)
        val overlayGranted = isOverlayGranted(context)
        val cameraGranted = isCameraGranted(context)
        val wirelessAdbGranted = isWirelessAdbGranted(context)
        val usageStatsGranted = isUsageStatsGranted(context)
        val nearbyDevicesGranted = isNearbyDevicesGranted(context)

        return buildPermissionList(
            sdkInt = Build.VERSION.SDK_INT,
            micGranted = micGranted,
            storageGranted = storageGranted,
            notificationsGranted = notificationsGranted,
            listenerGranted = listenerGranted,
            overlayGranted = overlayGranted,
            cameraGranted = cameraGranted,
            wirelessAdbGranted = wirelessAdbGranted,
            usageStatsGranted = usageStatsGranted,
            nearbyDevicesGranted = nearbyDevicesGranted
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
        usageStatsGranted: Boolean = false,
        nearbyDevicesGranted: Boolean = false
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
        val nearbyDevicesKey = when {
            sdkInt >= Build.VERSION_CODES.TIRAMISU ->
                "NEARBY_WIFI_DEVICES / BLUETOOTH_SCAN / BLUETOOTH_CONNECT"
            sdkInt >= Build.VERSION_CODES.S ->
                "BLUETOOTH_SCAN / BLUETOOTH_CONNECT"
            else ->
                "ACCESS_FINE_LOCATION"
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
            ),
            ManagedAppPermission(
                id = "nearby_devices",
                title = "Nearby Devices",
                subtitle = "Required for Arwora Connect P2P file sharing, Wi-Fi & peer discovery",
                permissionKey = nearbyDevicesKey,
                category = ManagedPermissionCategory.NEARBY_DEVICES,
                isGranted = nearbyDevicesGranted
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

    fun hasAllFilesAccess(context: Context): Boolean = try {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            Environment.isExternalStorageManager()
    } catch (_: Throwable) {
        false
    }

    fun hasSafDirectoryGrant(context: Context): Boolean = try {
        context.contentResolver.persistedUriPermissions.any {
            it.isReadPermission || it.isWritePermission
        }
    } catch (_: Throwable) {
        false
    }

    fun getPersistedSafTreeUri(context: Context): Uri? = try {
        val persisted = context.contentResolver.persistedUriPermissions
            .firstOrNull { it.isReadPermission || it.isWritePermission }
            ?.uri
        if (persisted != null) {
            persisted
        } else {
            val raw = context.getSharedPreferences(PREFS_PERMISSIONS, Context.MODE_PRIVATE)
                .getString(KEY_SAF_TREE_URI, null)
            if (!raw.isNullOrBlank() && hasSafDirectoryGrant(context)) Uri.parse(raw) else null
        }
    } catch (_: Throwable) {
        null
    }

    fun persistSafTreeUri(context: Context, uri: Uri?): Boolean {
        if (uri == null) return false
        return try {
            val readWriteFlags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, readWriteFlags)
            } catch (_: SecurityException) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    return false
                }
            }
            context.getSharedPreferences(PREFS_PERMISSIONS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SAF_TREE_URI, uri.toString())
                .apply()
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun clearSafTreeUri(context: Context) {
        try {
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.persistedUriPermissions.forEach { perm ->
                try {
                    context.contentResolver.releasePersistableUriPermission(perm.uri, flags)
                } catch (_: Throwable) {
                }
            }
            context.getSharedPreferences(PREFS_PERMISSIONS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_SAF_TREE_URI)
                .apply()
        } catch (_: Throwable) {
        }
    }

    fun isStorageGranted(context: Context): Boolean = try {
        if (hasAllFilesAccess(context) || hasSafDirectoryGrant(context)) {
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

    fun isNearbyDevicesGranted(context: Context): Boolean = try {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
            }
        }
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
        ManagedPermissionCategory.NEARBY_DEVICES -> when {
            sdkInt >= Build.VERSION_CODES.TIRAMISU -> listOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            sdkInt >= Build.VERSION_CODES.S -> listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
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

    fun createManageAllFilesAccessIntent(context: Context): Intent {
        val packageUri = Uri.parse("package:${context.packageName}")
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        }
        return intent.apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun createSafDocumentTreeIntent(initialUri: Uri? = null): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
            if (initialUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            }
        }

    fun createSettingsIntent(context: Context, category: ManagedPermissionCategory): Intent {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        return when (category) {
            ManagedPermissionCategory.STORAGE -> return createManageAllFilesAccessIntent(context)
            ManagedPermissionCategory.MICROPHONE,
            ManagedPermissionCategory.CAMERA,
            ManagedPermissionCategory.NEARBY_DEVICES -> Intent(
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

    fun openAllFilesAccessWithSafFallback(
        context: Context,
        onSafFallback: (() -> Unit)? = null
    ): Boolean {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(createManageAllFilesAccessIntent(context))
                add(
                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        if (context !is android.app.Activity) {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                )
            }
        }
        for (intent in candidates) {
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {
                // Fall back to next candidate
            }
        }
        if (onSafFallback != null) {
            onSafFallback()
            return true
        }
        return try {
            val safIntent = createSafDocumentTreeIntent().apply {
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            if (safIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(safIntent)
                true
            } else {
                openAppDetailsSettings(context)
            }
        } catch (_: Exception) {
            openAppDetailsSettings(context)
        }
    }

    fun openPermissionSettings(context: Context, category: ManagedPermissionCategory): Boolean {
        if (category == ManagedPermissionCategory.STORAGE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return openAllFilesAccessWithSafFallback(context)
        }
        return try {
            context.startActivity(createSettingsIntent(context, category))
            true
        } catch (_: Exception) {
            openAppDetailsSettings(context)
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
            add("pm grant $packageName ${Manifest.permission.NEARBY_WIFI_DEVICES}")
        } else {
            add("pm grant $packageName ${Manifest.permission.READ_EXTERNAL_STORAGE}")
        }
        if (sdkInt >= Build.VERSION_CODES.S) {
            add("pm grant $packageName ${Manifest.permission.BLUETOOTH_SCAN}")
            add("pm grant $packageName ${Manifest.permission.BLUETOOTH_CONNECT}")
        } else {
            add("pm grant $packageName ${Manifest.permission.ACCESS_FINE_LOCATION}")
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
            add("pm revoke $packageName ${Manifest.permission.NEARBY_WIFI_DEVICES}")
        } else {
            add("pm revoke $packageName ${Manifest.permission.READ_EXTERNAL_STORAGE}")
        }
        if (sdkInt >= Build.VERSION_CODES.S) {
            add("pm revoke $packageName ${Manifest.permission.BLUETOOTH_SCAN}")
            add("pm revoke $packageName ${Manifest.permission.BLUETOOTH_CONNECT}")
        } else {
            add("pm revoke $packageName ${Manifest.permission.ACCESS_FINE_LOCATION}")
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
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (sdkInt >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
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
