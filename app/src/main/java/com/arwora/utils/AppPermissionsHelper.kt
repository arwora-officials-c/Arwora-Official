package com.arwora.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

enum class ManagedPermissionCategory {
    MICROPHONE,
    STORAGE,
    NOTIFICATIONS,
    NOTIFICATION_LISTENER,
    OVERLAY
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
 * Evaluates Arwora Hyper Toolkit's active permission states and launches
 * corresponding Android system settings screens.
 *
 * Intentionally excludes CAMERA permission checks, requests, and entries.
 */
object AppPermissionsHelper {

    fun inspectPermissions(context: Context): List<ManagedAppPermission> {
        val micGranted = isMicrophoneGranted(context)
        val storageGranted = isStorageGranted(context)
        val notificationsGranted = areNotificationsGranted(context)
        val listenerGranted = isNotificationListenerGranted(context)
        val overlayGranted = isOverlayGranted(context)

        return buildPermissionList(
            sdkInt = Build.VERSION.SDK_INT,
            micGranted = micGranted,
            storageGranted = storageGranted,
            notificationsGranted = notificationsGranted,
            listenerGranted = listenerGranted,
            overlayGranted = overlayGranted
        )
    }

    internal fun buildPermissionList(
        sdkInt: Int,
        micGranted: Boolean,
        storageGranted: Boolean,
        notificationsGranted: Boolean,
        listenerGranted: Boolean,
        overlayGranted: Boolean
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

    fun createSettingsIntent(context: Context, category: ManagedPermissionCategory): Intent {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        return when (category) {
            ManagedPermissionCategory.MICROPHONE,
            ManagedPermissionCategory.STORAGE -> Intent(
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
}
