package com.arwora.feature.system.appops.model

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Immutable

@Immutable
enum class AppOpsFilter(val label: String) {
    ALL("All"),
    USER("User"),
    SYSTEM("System"),
    DISABLED("Disabled"),
    HIDDEN("Hidden"),
    ALERT_WINDOW("Alert Window"),
    BACKGROUND_LOCATION("Bg Location")
}

@Immutable
enum class AppOpsSort(val label: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    PACKAGE_NAME("Package Name"),
    INSTALL_TIME("Recently Installed"),
    TARGET_SDK("Target SDK")
}

@Immutable
enum class StandbyBucketMode(val title: String, val description: String, val bucketValue: Int) {
    UNRESTRICTED("Unrestricted", "No background battery or job execution limits", 10),
    DEFAULT("Default", "Standard system adaptive battery management", 20),
    RARE("Rare", "App rarely used; high background restrictions", 40),
    RESTRICTED("Restricted", "Severely throttled background network and alarms", 45),
    NEVER("Never", "Strictly dormant; no background operations", 50)
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
    val appOpsStates: Map<String, String> = emptyMap(),
    val icon: Drawable? = null
)
