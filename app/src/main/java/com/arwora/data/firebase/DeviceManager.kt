package com.arwora.data.firebase

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Immutable
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Immutable
data class DeviceRegistrationState(
    val deviceId: String = "",
    val deviceName: String = "",
    val registeredAt: String = "",
    val lastActive: String = "",
    val isOnline: Boolean = true,
    val status: String = "active", // "active", "blocked", "pending", "disabled"
    val isBlocked: Boolean = false,
    val blockReason: String = "Access Denied / Device Blocked by Administrator",
    val isRegistered: Boolean = false,
    val isInstallNotified: Boolean = false
)

object DeviceManager {
    private const val TAG = "DeviceManager"
    private const val PREFS_NAME = "arwora_device_prefs"
    private const val KEY_PERMANENT_DEVICE_ID = "permanent_hardware_device_id"
    private const val KEY_REGISTERED_AT = "saved_registered_at"
    private const val KEY_NEW_INSTALL_NOTIFIED = "new_install_notification_sent"
    private const val KEY_FIRST_LAUNCH_REGISTERED = "is_first_launch_registered"
    private const val NOTIFICATIONS_NODE = "notifications"
    private const val NEW_INSTALLS_NODE = "new_installs"

    private val _deviceState = MutableStateFlow(DeviceRegistrationState())
    val deviceState: StateFlow<DeviceRegistrationState> = _deviceState.asStateFlow()

    private var statusListener: ValueEventListener? = null
    private var isInitialized = false

    /**
     * Resolves the Hardware-Based Permanent Device ID.
     * Uses Settings.Secure.ANDROID_ID as the primary 100% persistent device identifier across reinstalls.
     * Guaranteed to return a non-empty, persistent hardware ID.
     */
    fun getHardwareDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getString(KEY_PERMANENT_DEVICE_ID, null)
        if (!cached.isNullOrBlank()) {
            return cached
        }

        var hardwareId: String? = null
        try {
            hardwareId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read Settings.Secure.ANDROID_ID: ${e.message}")
        }

        val finalId = if (!hardwareId.isNullOrBlank()) {
            hardwareId.trim()
        } else {
            // Extreme hardware fallback derived from hardware attributes if Settings.Secure is unavailable
            val rawHardwareFingerprint = "${Build.BOARD}_${Build.BRAND}_${Build.DEVICE}_${Build.HARDWARE}_${Build.MANUFACTURER}_${Build.MODEL}"
            val hash = rawHardwareFingerprint.hashCode().toLong() and 0xFFFFFFFFL
            "hw_${java.lang.Long.toHexString(hash).padStart(8, '0')}"
        }

        prefs.edit().putString(KEY_PERMANENT_DEVICE_ID, finalId).apply()
        return finalId
    }

    /**
     * Formats device model/name string (e.g. Build.MANUFACTURER.capitalize() + " " + Build.MODEL).
     * Example: "Vivo V2111", "Xiaomi Redmi Note 12", "Samsung SM-S918B"
     */
    fun getDeviceModelName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        val model = Build.MODEL.orEmpty()
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim().ifBlank { "Android Device" }
        }
    }

    /**
     * Initializes device registration and attaches realtime block & status listeners to Firebase Realtime Database
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val deviceId = getHardwareDeviceId(context)
        val deviceName = getDeviceModelName()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val nowFormatted = dateFormat.format(Date())

        var registeredAt = prefs.getString(KEY_REGISTERED_AT, null)
        if (registeredAt.isNullOrBlank()) {
            registeredAt = nowFormatted
            prefs.edit().putString(KEY_REGISTERED_AT, registeredAt).apply()
        }

        val isFirstLaunchRegistered = prefs.getBoolean(KEY_FIRST_LAUNCH_REGISTERED, false)

        _deviceState.value = _deviceState.value.copy(
            deviceId = deviceId,
            deviceName = deviceName,
            registeredAt = registeredAt,
            lastActive = nowFormatted,
            isOnline = true,
            status = "active",
            isBlocked = false,
            isInstallNotified = isFirstLaunchRegistered
        )

        Log.i(TAG, "Initializing hardware device registration for deviceId=$deviceId ($deviceName), isFirstLaunchRegistered=$isFirstLaunchRegistered")

        // First launch detection & Admin Notification Trigger:
        // If is_first_launch_registered is false: push notification to /notifications/new_installs and set true
        if (!isFirstLaunchRegistered) {
            triggerNewInstallNotification(context, deviceId, deviceName, registeredAt, force = false)
        }

        try {
            val db = FirebaseDatabase.getInstance(FirebaseSyncManager.DATABASE_URL)
            val deviceRef = db.getReference("devices").child(deviceId)

            // Setup Firebase onDisconnect handlers:
            // Set status to "user uninstalled the arwora" or "inactive", and update is_online = false
            deviceRef.child("status").onDisconnect().setValue("user uninstalled the arwora")
            deviceRef.child("is_online").onDisconnect().setValue(false)
            deviceRef.child("last_active").onDisconnect().setValue(nowFormatted)

            // Realtime Registration & Heartbeat Sync (/devices/{deviceID})
            val registrationPayload: MutableMap<String, Any> = mutableMapOf(
                "device_id" to deviceId,
                "device_name" to deviceName,
                "registered_at" to registeredAt,
                "last_active" to nowFormatted,
                "is_online" to true,
                "status" to "active",
                "android_version" to "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                "brand" to Build.BRAND,
                "model" to Build.MODEL,
                "app_version" to com.arwora.BuildConfig.VERSION_NAME,
                "app_version_code" to com.arwora.BuildConfig.VERSION_CODE
            )

            // Check if device already has a status (e.g. blocked) or is_blocked flag in Firebase before overwriting
            deviceRef.get().addOnSuccessListener { snap ->
                if (snap.exists()) {
                    val existingStatus = snap.child("status").getValue(String::class.java)?.trim()
                    if (!existingStatus.isNullOrBlank() && (existingStatus.equals("blocked", ignoreCase = true) || existingStatus.equals("banned", ignoreCase = true))) {
                        registrationPayload["status"] = existingStatus
                    }
                }
                deviceRef.updateChildren(registrationPayload)
                    .addOnSuccessListener {
                        Log.i(TAG, "Device registered successfully in Firebase: /devices/$deviceId")
                        _deviceState.value = _deviceState.value.copy(isRegistered = true)
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Failed to update device payload in Firebase: ${e.message}")
                    }
            }.addOnFailureListener {
                // If offline or first read fails, update payload directly
                deviceRef.updateChildren(registrationPayload)
            }

            // Realtime Block Listener (/devices/{deviceID} and /devices/{deviceID}/status / is_blocked)
            statusListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val statusNode = snapshot.child("status")
                        val status = when {
                            statusNode.exists() -> statusNode.getValue(String::class.java).orEmpty().trim()
                            snapshot.value is String -> (snapshot.value as String).trim()
                            else -> "active"
                        }

                        val isBlockedFlag = snapshot.child("is_blocked").getValue(Boolean::class.java) ?: false
                        val blockReason = snapshot.child("block_reason").getValue(String::class.java)
                            ?: snapshot.child("reason").getValue(String::class.java)
                            ?: "This device has been permanently blocked by the system administrator."

                        val isBlocked = isBlockedFlag ||
                                status.equals("blocked", ignoreCase = true) ||
                                status.equals("banned", ignoreCase = true) ||
                                status.equals("disabled", ignoreCase = true)

                        _deviceState.value = _deviceState.value.copy(
                            status = if (status.isNotBlank()) status else "active",
                            isBlocked = isBlocked,
                            blockReason = blockReason,
                            lastActive = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        )

                        Log.d(TAG, "Device state updated: status=$status, isBlockedFlag=$isBlockedFlag, isBlocked=$isBlocked")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing device status snapshot: ${e.message}", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Device status listener cancelled: ${error.message}")
                }
            }

            deviceRef.addValueEventListener(statusListener!!)

        } catch (e: Exception) {
            Log.e(TAG, "Exception during DeviceManager initialization: ${e.message}", e)
        }
    }

    /**
     * Updates device online status and heartbeat timestamp when app enters foreground (onResume / onStart).
     */
    fun onAppForeground() {
        val currentId = _deviceState.value.deviceId
        if (currentId.isBlank()) return

        val nowFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        _deviceState.value = _deviceState.value.copy(
            isOnline = true,
            status = if (_deviceState.value.isBlocked) _deviceState.value.status else "active",
            lastActive = nowFormatted
        )

        try {
            val db = FirebaseDatabase.getInstance(FirebaseSyncManager.DATABASE_URL)
            val updates = mutableMapOf<String, Any>(
                "is_online" to true,
                "last_active" to nowFormatted
            )
            if (!_deviceState.value.isBlocked) {
                updates["status"] = "active"
            }
            db.getReference("devices").child(currentId).updateChildren(updates)
                .addOnSuccessListener {
                    Log.d(TAG, "Device foreground heartbeat sent: status=active, is_online=true, last_active=$nowFormatted")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to send foreground heartbeat: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating foreground status: ${e.message}")
        }
    }

    /**
     * Updates device online and status when app moves to background (onStop / onDestroy).
     * Sets /devices/{deviceID}/status to "inactive" or "installed arwora" with timestamp.
     */
    fun onAppBackground() {
        val currentId = _deviceState.value.deviceId
        if (currentId.isBlank()) return

        val nowFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val newStatus = if (_deviceState.value.isBlocked) _deviceState.value.status else "inactive"
        _deviceState.value = _deviceState.value.copy(
            isOnline = false,
            status = newStatus,
            lastActive = nowFormatted
        )

        try {
            val db = FirebaseDatabase.getInstance(FirebaseSyncManager.DATABASE_URL)
            val updates = mutableMapOf<String, Any>(
                "is_online" to false,
                "last_active" to nowFormatted
            )
            if (!_deviceState.value.isBlocked) {
                updates["status"] = "inactive"
            }
            db.getReference("devices").child(currentId).updateChildren(updates)
                .addOnSuccessListener {
                    Log.d(TAG, "Device background status sent: status=$newStatus, is_online=false")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to send background status: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating background status: ${e.message}")
        }
    }

    /**
     * Re-queries the device status node immediately (e.g. from the Blocked UI retry button)
     */
    fun refreshDeviceStatus() {
        val currentId = _deviceState.value.deviceId
        if (currentId.isBlank()) return

        try {
            val db = FirebaseDatabase.getInstance(FirebaseSyncManager.DATABASE_URL)
            db.getReference("devices").child(currentId).get()
                .addOnSuccessListener { snapshot ->
                    val status = snapshot.child("status").getValue(String::class.java).orEmpty().trim()
                    val isBlockedFlag = snapshot.child("is_blocked").getValue(Boolean::class.java) ?: false
                    val blockReason = snapshot.child("block_reason").getValue(String::class.java)
                        ?: snapshot.child("reason").getValue(String::class.java)
                        ?: "This device has been permanently blocked by the system administrator."

                    val isBlocked = isBlockedFlag ||
                            status.equals("blocked", ignoreCase = true) ||
                            status.equals("banned", ignoreCase = true) ||
                            status.equals("disabled", ignoreCase = true)

                    _deviceState.value = _deviceState.value.copy(
                        status = if (status.isNotBlank()) status else "active",
                        isBlocked = isBlocked,
                        blockReason = blockReason
                    )
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to refresh device status: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing status: ${e.message}", e)
        }
    }

    /**
     * Pushes a new installation notification event to Firebase Realtime Database
     * under /notifications/new_installs on first app launch so the Admin Panel receives
     * an instant push notification alert.
     */
    fun triggerNewInstallNotification(
        context: Context,
        deviceId: String = getHardwareDeviceId(context),
        deviceName: String = getDeviceModelName(),
        installedAt: String? = null,
        force: Boolean = false
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstLaunchRegistered = prefs.getBoolean(KEY_FIRST_LAUNCH_REGISTERED, false)
        if (isFirstLaunchRegistered && !force) {
            Log.d(TAG, "New install notification already registered for device $deviceId. Skipping.")
            return
        }

        val timestamp = installedAt ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val notificationPayload = mapOf<String, Any>(
            "device_name" to deviceName,
            "device_id" to deviceId,
            "timestamp" to timestamp,
            "installed_at" to timestamp,
            "is_read" to false
        )

        try {
            val db = FirebaseDatabase.getInstance(FirebaseSyncManager.DATABASE_URL)
            val newInstallRef = db.getReference(NOTIFICATIONS_NODE).child(NEW_INSTALLS_NODE).push()

            newInstallRef.setValue(notificationPayload)
                .addOnSuccessListener {
                    prefs.edit()
                        .putBoolean(KEY_FIRST_LAUNCH_REGISTERED, true)
                        .putBoolean(KEY_NEW_INSTALL_NOTIFIED, true)
                        .apply()
                    _deviceState.value = _deviceState.value.copy(isInstallNotified = true)
                    Log.i(TAG, "New installation notification successfully pushed to Firebase: /notifications/new_installs/${newInstallRef.key}")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to push new install notification to Firebase: ${e.message}")
                }
        } catch (e: Exception) {
            Log.e(TAG, "Exception pushing new install notification: ${e.message}", e)
        }
    }
}
