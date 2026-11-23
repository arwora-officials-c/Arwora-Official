package com.arwora.feature.system.installassistant.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppInstallCategory(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    ALL("all", "All", Icons.Default.Apps, Color(0xFF6366F1)),
    GAMES("games", "Games", Icons.Default.SportsEsports, Color(0xFFEC4899)),
    PRODUCTIVITY("productivity", "Productivity", Icons.Default.Work, Color(0xFF3B82F6)),
    UTILITIES("utilities", "Utilities", Icons.Default.Build, Color(0xFF10B981)),
    SOCIAL("social", "Social", Icons.Default.Forum, Color(0xFF8B5CF6)),
    MEDIA("media", "Media", Icons.Default.PlayCircle, Color(0xFFF59E0B)),
    FINANCE("finance", "Finance", Icons.Default.Payments, Color(0xFF14B8A6)),
    SYSTEM("system", "System", Icons.Default.SettingsApplications, Color(0xFF64748B)),
    OTHER("other", "Other", Icons.Default.Apps, Color(0xFF94A3B8));

    companion object {
        fun fromId(id: String?): AppInstallCategory =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OTHER

        val filterTabs: List<AppInstallCategory> = listOf(
            ALL, GAMES, PRODUCTIVITY, UTILITIES, SOCIAL, MEDIA, FINANCE, SYSTEM, OTHER
        )
    }
}

enum class SetupChecklistStep(
    val id: String,
    val title: String,
    val subtitle: String,
    val actionLabel: String
) {
    BATTERY_OPTIMIZATION(
        id = "battery_optimization",
        title = "Battery Optimization",
        subtitle = "Review background power saving or unrestricted battery mode",
        actionLabel = "Battery Settings"
    ),
    NOTIFICATIONS(
        id = "notifications",
        title = "Notification Permission Review",
        subtitle = "Control alerts, channels, and silent notification behavior",
        actionLabel = "Notification Settings"
    ),
    DANGEROUS_PERMISSIONS(
        id = "dangerous_permissions",
        title = "Dangerous Permissions Audit",
        subtitle = "Inspect camera, location, storage, contacts & microphone access",
        actionLabel = "Permission Settings"
    ),
    APP_OPS_BACKGROUND(
        id = "app_ops_background",
        title = "App Ops Background Restrictions",
        subtitle = "Inspect background wake, standby bucket & hidden runtime ops",
        actionLabel = "Open in App Ops"
    );

    companion object {
        fun fromId(id: String?): SetupChecklistStep? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

data class DangerousPermissionAuditItem(
    val permission: String,
    val simpleName: String,
    val groupLabel: String,
    val isGranted: Boolean
)

enum class OnlineSyncStatus {
    IDLE,
    LOADING,
    SUCCESS,
    OFFLINE,
    NOT_FOUND_PUBLIC,
    ERROR
}

data class OnlineAppInfoResult(
    val packageName: String,
    val status: OnlineSyncStatus = OnlineSyncStatus.IDLE,
    val storeTitle: String = "",
    val developerOrOrigin: String = "",
    val publicCategory: String = "",
    val installerSource: String = "Package Installer",
    val signingSha256: String = "",
    val storeUrl: String = "",
    val syncSummary: String = "",
    val syncedAtMillis: Long = 0L,
    val errorMessage: String? = null
)

data class InstalledAppAssistantRecord(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val category: AppInstallCategory,
    val installedAtMillis: Long,
    val isUpdate: Boolean = false,
    val installerPackage: String? = null,
    val completedSteps: Set<SetupChecklistStep> = emptySet(),
    val batteryOptimizationIgnored: Boolean = false,
    val notificationsAllowed: Boolean? = null,
    val dangerousPermissions: List<DangerousPermissionAuditItem> = emptyList(),
    val onlineMetadata: OnlineAppInfoResult? = null,
    val lastAuditedAtMillis: Long = 0L
) {
    val totalChecklistSteps: Int get() = SetupChecklistStep.entries.size
    val completedCount: Int get() = completedSteps.size
    val isFullyReviewed: Boolean get() = completedSteps.size >= SetupChecklistStep.entries.size
    val grantedDangerousCount: Int get() = dangerousPermissions.count { it.isGranted }
    val setupProgressFraction: Float
        get() = if (totalChecklistSteps == 0) 0f else completedCount.toFloat() / totalChecklistSteps.toFloat()
    val statusBadgeText: String
        get() = if (isFullyReviewed) "Reviewed" else "$completedCount/$totalChecklistSteps Done"
}
