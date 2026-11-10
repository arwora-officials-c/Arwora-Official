package com.arwora.data.firebase

import androidx.compose.runtime.Immutable

@Immutable
data class AdminContacts(
    val telegramUsername: String = "@Arwora_Mahfuz13",
    val whatsappNumber: String = "01303623287",
    val supportEmail: String = "mahfuz45245@gmail.com"
)

@Immutable
data class AppRemoteConfig(
    val maintenanceMode: Boolean = false,
    val isRemoteLocked: Boolean = false,
    val globalLock: Boolean = false,
    val blockMessage: String = "Your Device ID has been blocked.",
    val contacts: AdminContacts = AdminContacts(),
    val maintenanceTitle: String = "App Locked / System Maintenance",
    val maintenanceMessage: String = "App is currently under maintenance / locked by admin.",
    val forceUpdate: Boolean = false,
    val minVersionCode: Long = 1L,
    val latestVersionName: String = "1.48.0",
    val updateUrl: String = "https://github.com/arwora",
    val updateMessage: String = "A critical update is required to continue using Arwora Hyper Toolkit safely.",
    val updateNotes: String = "Important security and performance updates are available. Please update now to continue.",
    val announcementMessage: String? = null,
    val isAnnouncementActive: Boolean = false,
    val dnsChangerMasterEnabled: Boolean = true,
    val appOpsMasterEnabled: Boolean = true,
    val batteryMonitorMasterEnabled: Boolean = true,
    val wifiToolsMasterEnabled: Boolean = true,
    val lastSyncTimestamp: Long = 0L,
    val isConnectedToFirebase: Boolean = false,
    val syncStatusMessage: String = "Initializing Cloud Sync..."
) {
    val isLockedOrMaintenance: Boolean
        get() = maintenanceMode || isRemoteLocked

    val isGloballyBlocked: Boolean
        get() = globalLock || isRemoteLocked
}
