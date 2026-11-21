package com.arwora.feature.system.appops.model

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable

@Immutable
enum class AppOpsFilter(val label: String) {
    ALL("All"),
    LOCATION("Location"),
    CAMERA("Camera"),
    AUDIO("Audio"),
    SYSTEM_OPS("System Ops"),
    USER("User"),
    SYSTEM("System"),
    DISABLED("Disabled"),
    HIDDEN("Hidden"),
    ALERT_WINDOW("Alert Window"),
    BACKGROUND_LOCATION("Bg Location")
}

@Immutable
enum class AppOpsCategory(val label: String) {
    ALL("All"),
    LOCATION("Location"),
    CAMERA("Camera"),
    AUDIO("Audio"),
    SYSTEM("System")
}

@Immutable
data class AppOpDescriptor(
    val opName: String,
    val title: String,
    val description: String,
    val category: AppOpsCategory,
    val relatedPermissions: List<String> = emptyList()
)

@Immutable
enum class AppOpsSort(val label: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    PACKAGE_NAME("Package Name"),
    INSTALL_TIME("Recently Installed"),
    TARGET_SDK("Target SDK")
}

@Immutable
enum class StandbyBucketMode(
    val title: String,
    val description: String,
    val bucketValue: Int,
    val shellArgument: String
) {
    UNRESTRICTED("Unrestricted", "No background battery or job execution limits", 10, "active"),
    DEFAULT("Default", "Standard system adaptive battery management", 20, "working_set"),
    RARE("Rare", "App rarely used; high background restrictions", 40, "rare"),
    RESTRICTED("Restricted", "Severely throttled background network and alarms", 45, "restricted"),
    NEVER("Never", "Strictly dormant; no background operations", 50, "never")
}

@Immutable
data class PermissionItem(
    val permission: String,
    val simpleName: String,
    val group: String,
    val isGranted: Boolean,
    val isDangerous: Boolean
)

@Immutable
data class AppOpsItem(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val uid: Int,
    val minSdk: Int,
    val targetSdk: Int,
    val dataDir: String,
    val sourceDir: String,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val firstInstallFormatted: String,
    val lastUpdateFormatted: String,
    val isSystem: Boolean,
    val isDisabled: Boolean,
    val isHidden: Boolean,
    val requestedPermissions: List<PermissionItem> = emptyList(),
    val standbyBucket: StandbyBucketMode = StandbyBucketMode.DEFAULT,
    val isBackgroundRestricted: Boolean = false,
    val hasWakeLockPermission: Boolean = false,
    val hasBootPermission: Boolean = false,
    val hasAlertWindowPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val hasSystemOpsPermission: Boolean = false,
    val appOpsStates: Map<String, String> = emptyMap(),
    val icon: Drawable? = null
) {
    val allowedOpsCount: Int
        get() = appOpsStates.values.count { it.equals("allow", ignoreCase = true) }

    val ignoredOpsCount: Int
        get() = appOpsStates.values.count { it.equals("ignore", ignoreCase = true) }

    val restrictedOpsCount: Int
        get() = appOpsStates.values.count {
            it.equals("ignore", ignoreCase = true) ||
                it.equals("deny", ignoreCase = true) ||
                it.equals("errored", ignoreCase = true)
        }
}
