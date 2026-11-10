package com.arwora.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"
    const val DATABASE_URL = "https://arwora-admin-panel-fff8f-default-rtdb.firebaseio.com"

    private val _remoteConfig = MutableStateFlow(AppRemoteConfig())
    val remoteConfig: StateFlow<AppRemoteConfig> = _remoteConfig.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var database: FirebaseDatabase? = null
    private var configListener: ValueEventListener? = null
    private var connectionListener: ValueEventListener? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }

            val db = FirebaseDatabase.getInstance(DATABASE_URL).apply {
                try {
                    setPersistenceEnabled(true)
                } catch (e: Exception) {
                    Log.w(TAG, "Firebase disk persistence warning: ${e.message}")
                }
            }
            database = db

            // Listen to real-time connection status
            val connectedRef = db.getReference(".info/connected")
            connectionListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val connected = snapshot.getValue(Boolean::class.java) ?: false
                    _isConnected.value = connected
                    _remoteConfig.value = _remoteConfig.value.copy(
                        isConnectedToFirebase = connected,
                        syncStatusMessage = if (connected) "Live Synced with Admin Panel" else "Offline Cached Mode"
                    )
                    Log.d(TAG, "Firebase connection status: $connected")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Connection listener cancelled: ${error.message}")
                    _isConnected.value = false
                }
            }
            connectedRef.addValueEventListener(connectionListener!!)

            // Listen to live database configuration changes
            val configRef = db.reference
            configListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        parseRemoteConfig(snapshot)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse remote config: ${e.message}", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Remote config listener cancelled: ${error.message}")
                    _remoteConfig.value = _remoteConfig.value.copy(
                        syncStatusMessage = "Sync Paused (${error.message})"
                    )
                }
            }
            configRef.addValueEventListener(configListener!!)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Realtime Database: ${e.message}", e)
            _remoteConfig.value = _remoteConfig.value.copy(
                syncStatusMessage = "Offline Fallback Active"
            )
        }
    }

    private fun parseRemoteConfig(rootSnapshot: DataSnapshot) {
        val target = rootSnapshot.child("app_settings").takeIf { it.exists() }
            ?: rootSnapshot.child("remote_config").takeIf { it.exists() }
            ?: rootSnapshot.child("admin_settings").takeIf { it.exists() }
            ?: rootSnapshot.child("toolkit_config").takeIf { it.exists() }
            ?: rootSnapshot.child("admin_config").takeIf { it.exists() }
            ?: rootSnapshot

        val current = _remoteConfig.value

        // Helper readers with snake_case and camelCase fallback
        fun readBoolean(vararg keys: String, default: Boolean): Boolean {
            for (key in keys) {
                // Check in target node
                var node = target.child(key)
                if (!node.exists() && target != rootSnapshot) {
                    // Check fallback at root
                    node = rootSnapshot.child(key)
                }
                if (node.exists()) {
                    val value = node.getValue(Boolean::class.java)
                    if (value != null) return value
                    val stringVal = node.getValue(String::class.java)
                    if (stringVal != null) return stringVal.equals("true", ignoreCase = true)
                }
            }
            return default
        }

        fun readString(vararg keys: String, default: String): String {
            for (key in keys) {
                var node = target.child(key)
                if (!node.exists() && target != rootSnapshot) {
                    node = rootSnapshot.child(key)
                }
                if (node.exists()) {
                    val stringVal = node.getValue(String::class.java)
                    if (!stringVal.isNullOrBlank()) return stringVal
                }
            }
            return default
        }

        fun readLong(vararg keys: String, default: Long): Long {
            for (key in keys) {
                var node = target.child(key)
                if (!node.exists() && target != rootSnapshot) {
                    node = rootSnapshot.child(key)
                }
                if (node.exists()) {
                    val longVal = node.getValue(Long::class.java)
                    if (longVal != null) return longVal
                    val intVal = node.getValue(Int::class.java)
                    if (intVal != null) return intVal.toLong()
                    val stringVal = node.getValue(String::class.java)
                    if (stringVal != null) {
                        stringVal.toLongOrNull()?.let { return it }
                    }
                }
            }
            return default
        }

        val maintenanceMode = readBoolean("is_maintenance_mode", "maintenance_mode", "maintenanceMode", "isMaintenance", default = current.maintenanceMode)
        val isRemoteLocked = readBoolean("is_remote_locked", "remote_locked", "isRemoteLocked", "remoteLocked", default = current.isRemoteLocked)
        val globalLock = readBoolean("global_lock", "globalLock", "is_global_lock", default = current.globalLock)
        val blockMessage = readString("block_message", "blockMessage", default = current.blockMessage)

        val maintenanceTitle = readString("maintenance_title", "maintenanceTitle", default = if (isRemoteLocked || globalLock) "App Locked by Admin" else current.maintenanceTitle)
        val maintenanceMessage = readString("maintenance_message", "maintenanceMessage", "lock_message", default = if (globalLock) blockMessage else current.maintenanceMessage)

        val forceUpdate = readBoolean("force_update", "is_force_update", "forceUpdate", "isForceUpdate", default = current.forceUpdate)
        val minVersionCode = readLong("version_code", "versionCode", "min_version_code", "minVersionCode", "min_version", default = current.minVersionCode)
        val latestVersionName = readString("latest_version_name", "latestVersionName", "versionName", default = current.latestVersionName)
        val updateUrl = readString("update_url", "updateUrl", default = current.updateUrl)
        val updateMessage = readString("update_message", "updateMessage", default = current.updateMessage)
        val updateNotes = readString("update_notes", "updateNotes", default = current.updateNotes)

        // Parse Contacts from /app_settings/contacts or root
        val contactsNode = target.child("contacts").takeIf { it.exists() }
            ?: rootSnapshot.child("contacts").takeIf { it.exists() }

        val telegramUser = contactsNode?.child("telegram_username")?.getValue(String::class.java)
            ?: target.child("telegram_username").getValue(String::class.java)
            ?: rootSnapshot.child("telegram_username").getValue(String::class.java)
            ?: current.contacts.telegramUsername

        val whatsappNum = contactsNode?.child("whatsapp_number")?.getValue(String::class.java)
            ?: target.child("whatsapp_number").getValue(String::class.java)
            ?: rootSnapshot.child("whatsapp_number").getValue(String::class.java)
            ?: current.contacts.whatsappNumber

        val supportMail = contactsNode?.child("support_email")?.getValue(String::class.java)
            ?: target.child("support_email").getValue(String::class.java)
            ?: rootSnapshot.child("support_email").getValue(String::class.java)
            ?: current.contacts.supportEmail

        val parsedContacts = AdminContacts(
            telegramUsername = telegramUser,
            whatsappNumber = whatsappNum,
            supportEmail = supportMail
        )

        val announcementMsg = target.child("announcement_message").getValue(String::class.java)
            ?: target.child("announcementMessage").getValue(String::class.java)
            ?: target.child("announcement").getValue(String::class.java)
            ?: rootSnapshot.child("announcement_message").getValue(String::class.java)
        val isAnnouncementActive = readBoolean("is_announcement_active", "announcement_active", "isAnnouncementActive", default = announcementMsg?.isNotBlank() == true)

        val dnsChangerEnabled = readBoolean("dns_changer_enabled", "dnsChangerMasterEnabled", default = current.dnsChangerMasterEnabled)
        val appOpsEnabled = readBoolean("app_ops_enabled", "appOpsMasterEnabled", default = current.appOpsMasterEnabled)
        val batteryMonitorEnabled = readBoolean("battery_monitor_enabled", "batteryMonitorMasterEnabled", default = current.batteryMonitorMasterEnabled)
        val wifiToolsEnabled = readBoolean("wifi_tools_enabled", "wifiToolsMasterEnabled", default = current.wifiToolsMasterEnabled)

        _remoteConfig.value = current.copy(
            maintenanceMode = maintenanceMode,
            isRemoteLocked = isRemoteLocked,
            globalLock = globalLock,
            blockMessage = blockMessage,
            contacts = parsedContacts,
            maintenanceTitle = maintenanceTitle,
            maintenanceMessage = maintenanceMessage,
            forceUpdate = forceUpdate,
            minVersionCode = minVersionCode,
            latestVersionName = latestVersionName,
            updateUrl = updateUrl,
            updateMessage = updateMessage,
            updateNotes = updateNotes,
            announcementMessage = announcementMsg,
            isAnnouncementActive = isAnnouncementActive,
            dnsChangerMasterEnabled = dnsChangerEnabled,
            appOpsMasterEnabled = appOpsEnabled,
            batteryMonitorMasterEnabled = batteryMonitorEnabled,
            wifiToolsMasterEnabled = wifiToolsEnabled,
            lastSyncTimestamp = System.currentTimeMillis(),
            syncStatusMessage = "Synchronized at ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
        )
        Log.d(TAG, "Remote configuration synchronized: maintenanceMode=$maintenanceMode, isRemoteLocked=$isRemoteLocked, globalLock=$globalLock, forceUpdate=$forceUpdate, minVersion=$minVersionCode")
    }

    fun forceRefresh() {
        val db = database ?: return
        db.reference.get().addOnSuccessListener { snapshot ->
            parseRemoteConfig(snapshot)
        }.addOnFailureListener { error ->
            Log.e(TAG, "Force refresh failed: ${error.message}")
        }
    }
}
