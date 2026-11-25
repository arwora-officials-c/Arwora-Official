package com.arwora.utils

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.CancellationSignal
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

data class LockableTool(
    val id: String,
    val name: String,
    val route: String,
    val category: String,
    val description: String
)

enum class BiometricStatus {
    AVAILABLE,
    NOT_ENROLLED,
    NO_HARDWARE,
    UNSUPPORTED
}

object AppLockerManager {
    private const val PREFS_NAME = "app_locker_preferences"
    private const val KEY_ENABLED = "locker_master_enabled"
    private const val KEY_PIN_HASH = "locker_pin_hash"
    private const val KEY_PIN_SALT = "locker_pin_salt"
    private const val KEY_BIOMETRIC_ENABLED = "locker_biometric_enabled"
    private const val KEY_LOCKED_PACKAGES = "locker_locked_packages"
    private const val KEY_LOCKED_TOOLS = "locker_locked_tools"
    private const val KEY_TIMEOUT_MINUTES = "locker_timeout_minutes"
    private const val KEY_VIBRATE = "locker_vibrate_error"
    private const val KEY_SCRAMBLE = "locker_scramble_keypad"
    private const val KEY_PERSISTENT_NOTIF = "locker_persistent_notification"
    private const val KEY_SECURITY_Q = "locker_security_question"
    private const val KEY_SECURITY_A_HASH = "locker_security_answer_hash"
    private const val KEY_FAILED_ATTEMPTS = "locker_failed_attempts"
    private const val KEY_UNINSTALL_PROTECTION = "locker_uninstall_protection"
    private const val KEY_INSTALL_BLOCK = "locker_install_block"
    private const val KEY_SELF_STEALTH = "locker_self_stealth"
    private const val KEY_SECRET_DIALER_CODE = "locker_secret_dialer_code"

    private var prefs: SharedPreferences? = null

    private val _isLockerEnabled = MutableStateFlow(false)
    val isLockerEnabled: StateFlow<Boolean> = _isLockerEnabled.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(true)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _persistentNotification = MutableStateFlow(true)
    val persistentNotification: StateFlow<Boolean> = _persistentNotification.asStateFlow()

    private val _hasPin = MutableStateFlow(false)
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    private val _lockedPackages = MutableStateFlow<Set<String>>(emptySet())
    val lockedPackages: StateFlow<Set<String>> = _lockedPackages.asStateFlow()

    private val _lockedToolIds = MutableStateFlow<Set<String>>(emptySet())
    val lockedToolIds: StateFlow<Set<String>> = _lockedToolIds.asStateFlow()

    private val _timeoutMinutes = MutableStateFlow(0) // 0 = Immediately
    val timeoutMinutes: StateFlow<Int> = _timeoutMinutes.asStateFlow()

    private val _vibrateOnError = MutableStateFlow(true)
    val vibrateOnError: StateFlow<Boolean> = _vibrateOnError.asStateFlow()

    private val _scrambleKeypad = MutableStateFlow(false)
    val scrambleKeypad: StateFlow<Boolean> = _scrambleKeypad.asStateFlow()

    private val _securityQuestion = MutableStateFlow<String?>(null)
    val securityQuestion: StateFlow<String?> = _securityQuestion.asStateFlow()

    private val _failedAttempts = MutableStateFlow(0)
    val failedAttempts: StateFlow<Int> = _failedAttempts.asStateFlow()

    private val _isUninstallProtectionEnabled = MutableStateFlow(false)
    val isUninstallProtectionEnabled: StateFlow<Boolean> = _isUninstallProtectionEnabled.asStateFlow()

    private val _isInstallBlockEnabled = MutableStateFlow(false)
    val isInstallBlockEnabled: StateFlow<Boolean> = _isInstallBlockEnabled.asStateFlow()

    private val _isSelfStealthEnabled = MutableStateFlow(false)
    val isSelfStealthEnabled: StateFlow<Boolean> = _isSelfStealthEnabled.asStateFlow()

    private val _secretDialerCode = MutableStateFlow("2796")
    val secretDialerCode: StateFlow<String> = _secretDialerCode.asStateFlow()

    // Temporary session unlocks (route -> timestamp)
    private val unlockedSessions = mutableMapOf<String, Long>()
    @Volatile
    private var uninstallAuthorizedUntilMs: Long = 0L
    private val authorizedInstallPackages = mutableMapOf<String, Long>()
    private val temporarilyExemptUninstallPackages = mutableSetOf<String>()

    val availableTools = listOf(
        LockableTool("wifi_ctrl", "Wi-Fi Controller", "wifi_controller", "Network", "Diagnostics & connected devices inspector"),
        LockableTool("dns_changer", "DNS Changer", "dns_changer", "Network", "Private & secure DoT DNS selector"),
        LockableTool("hide_private_dns", "Hide Private DNS", "hide_private_dns", "Network", "Restrict system Private DNS for selected apps & games"),
        LockableTool("fake_gps", "Fake GPS Location", "fake_gps", "Location", "Spoof mock location coordinates"),
        LockableTool("deep_scanner", "Deep Scanner", "deep_scanner", "Security", "Comprehensive hardware & system diagnostics"),
        LockableTool("security_scan", "Security Scan", "security_scan", "Security", "Malware, permissions & threat analysis"),
        LockableTool("perm_inspector", "Permission Inspector", "permission_inspector", "Privacy", "Manage sensitive runtime permissions"),
        LockableTool("notif_history", "Notification History", "notification_history", "Privacy", "Dismissed notification logs & tracker"),
        LockableTool("battery_alarm", "Battery Alarm", "battery_alarm", "System", "Battery charge & discharge threshold alerts"),
        LockableTool("dup_finder", "Duplicate File Finder", "duplicate_file_finder", "Storage", "Scan and wipe duplicate media & docs"),
        LockableTool("storage_analyzer", "Storage Analyzer", "storage_analyzer", "Storage", "Visual breakdown of disk usage & space"),
        LockableTool("sound_direction", "Sound Direction", "sound_direction", "Sensors", "Acoustic compass & sound direction finder"),
        LockableTool("exif_editor", "Image Metadata Editor", "image_metadata_editor", "Privacy", "Clean EXIF GPS & camera tags"),
        LockableTool("app_clone", "App Clone", "app_clone", "System", "Dual accounts and workspace cloner"),
        LockableTool("settings", "App Settings", "settings", "System", "Global appearance, theme and app configuration")
    )

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            syncFromPrefs()
        }
    }

    internal fun resetForTests(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = null
        unlockedSessions.clear()
        uninstallAuthorizedUntilMs = 0L
        authorizedInstallPackages.clear()
        temporarilyExemptUninstallPackages.clear()
        init(context)
    }

    private fun syncFromPrefs() {
        val p = prefs ?: return
        _isLockerEnabled.value = p.getBoolean(KEY_ENABLED, false)
        _isBiometricEnabled.value = p.getBoolean(KEY_BIOMETRIC_ENABLED, true)
        val pinHash = p.getString(KEY_PIN_HASH, null)
        _hasPin.value = !pinHash.isNullOrEmpty()
        val rawLockedPackages = p.getStringSet(KEY_LOCKED_PACKAGES, emptySet()) ?: emptySet()
        _lockedPackages.value = rawLockedPackages.filterNot { isSelfPackageName(it) }.toSet()
        _lockedToolIds.value = p.getStringSet(KEY_LOCKED_TOOLS, emptySet()) ?: emptySet()
        _timeoutMinutes.value = p.getInt(KEY_TIMEOUT_MINUTES, 0)
        _vibrateOnError.value = p.getBoolean(KEY_VIBRATE, true)
        _scrambleKeypad.value = p.getBoolean(KEY_SCRAMBLE, false)
        _persistentNotification.value = p.getBoolean(KEY_PERSISTENT_NOTIF, true)
        _securityQuestion.value = p.getString(KEY_SECURITY_Q, null)
        _failedAttempts.value = p.getInt(KEY_FAILED_ATTEMPTS, 0)
        _isUninstallProtectionEnabled.value = p.getBoolean(KEY_UNINSTALL_PROTECTION, false)
        _isInstallBlockEnabled.value = p.getBoolean(KEY_INSTALL_BLOCK, false)
        _isSelfStealthEnabled.value = p.getBoolean(KEY_SELF_STEALTH, false)
        _secretDialerCode.value = p.getString(KEY_SECRET_DIALER_CODE, "2796") ?: "2796"
    }

    fun isSelfPackageName(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val clean = packageName.trim()
        return clean == "com.arworahyper.toolkit" ||
               clean == "com.arwora" ||
               clean == "com.arwora.service" ||
               clean.startsWith("com.arworahyper.toolkit") ||
               clean.startsWith("com.arwora.")
    }

    fun setLockerEnabled(context: Context, enabled: Boolean) {
        init(context)
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
        _isLockerEnabled.value = enabled
        syncBackgroundProtectionServices(context)
    }

    fun setUninstallProtectionEnabled(context: Context, enabled: Boolean) {
        init(context)
        prefs?.edit()?.putBoolean(KEY_UNINSTALL_PROTECTION, enabled)?.apply()
        _isUninstallProtectionEnabled.value = enabled
        if (!enabled) {
            uninstallAuthorizedUntilMs = 0L
        }
        runCatching {
            com.arwora.deviceadmin.AppDeviceAdminReceiver.syncDeviceOwnerUninstallPolicy(context, enabled)
        }
        syncBackgroundProtectionServices(context)
    }

    fun setInstallBlockEnabled(context: Context, enabled: Boolean) {
        init(context)
        prefs?.edit()?.putBoolean(KEY_INSTALL_BLOCK, enabled)?.apply()
        _isInstallBlockEnabled.value = enabled
        if (!enabled) {
            authorizedInstallPackages.clear()
        }
        syncBackgroundProtectionServices(context)
    }

    internal fun setSelfStealthState(context: Context, enabled: Boolean) {
        init(context)
        prefs?.edit()?.putBoolean(KEY_SELF_STEALTH, enabled)?.apply()
        _isSelfStealthEnabled.value = enabled
    }

    fun syncBackgroundProtectionServices(context: Context) {
        val lockerNeeded = _isLockerEnabled.value &&
            (_lockedPackages.value.isNotEmpty() || _isUninstallProtectionEnabled.value || _isInstallBlockEnabled.value)
        if (lockerNeeded) {
            com.arwora.service.AppLockerService.start(context)
        } else {
            com.arwora.service.AppLockerService.stop(context)
        }
        com.arwora.service.AppInstallMonitorService.syncWithPreferences(context)
    }

    fun grantTemporaryUninstallPass(durationMs: Long = 30_000L) {
        uninstallAuthorizedUntilMs = System.currentTimeMillis() + durationMs
    }

    fun isUninstallAuthorized(): Boolean {
        return System.currentTimeMillis() <= uninstallAuthorizedUntilMs
    }

    fun revokeUninstallAuthorization() {
        uninstallAuthorizedUntilMs = 0L
    }

    fun temporarilyExemptUninstall(packageName: String) {
        if (packageName.isNotBlank()) {
            temporarilyExemptUninstallPackages.add(packageName)
        }
    }

    fun authorizeInstall(packageName: String, durationMs: Long = 120_000L) {
        val clean = packageName.trim()
        if (clean.isNotEmpty()) {
            authorizedInstallPackages[clean] = System.currentTimeMillis() + durationMs
            temporarilyExemptUninstallPackages.remove(clean)
        }
    }

    fun isInstallAuthorized(packageName: String): Boolean {
        val clean = packageName.trim()
        val now = System.currentTimeMillis()
        val exactUntil = authorizedInstallPackages[clean]
        if (exactUntil != null && now <= exactUntil) return true
        val pendingUntil = authorizedInstallPackages[com.arwora.service.AppInstallMonitorService.INSTALLER_PLACEHOLDER_PKG]
        if (pendingUntil != null && now <= pendingUntil) return true
        return false
    }

    fun clearInstallAuthorizations() {
        authorizedInstallPackages.clear()
        temporarilyExemptUninstallPackages.clear()
    }

    fun isPackageProtectedFromUninstall(packageName: String?): Boolean {
        if (!_isUninstallProtectionEnabled.value) return false
        val clean = packageName?.trim().orEmpty()
        if (clean.isNotEmpty() && temporarilyExemptUninstallPackages.contains(clean)) return false
        if (clean.isEmpty()) return true
        return isSelfPackageName(clean) ||
            _lockedPackages.value.contains(clean) ||
            AppHiderManager.isAppHidden(clean)
    }

    fun setPersistentNotification(context: Context, enabled: Boolean) {
        init(context)
        prefs?.edit()?.putBoolean(KEY_PERSISTENT_NOTIF, enabled)?.apply()
        _persistentNotification.value = enabled
    }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        init(context)
        prefs?.edit()?.putBoolean(KEY_BIOMETRIC_ENABLED, enabled)?.apply()
        _isBiometricEnabled.value = enabled
    }

    fun setVibrateOnError(context: Context, enabled: Boolean) {
        init(context)
        prefs?.edit()?.putBoolean(KEY_VIBRATE, enabled)?.apply()
        _vibrateOnError.value = enabled
    }

    fun setScrambleKeypad(context: Context, enabled: Boolean) {
        init(context)
        prefs?.edit()?.putBoolean(KEY_SCRAMBLE, enabled)?.apply()
        _scrambleKeypad.value = enabled
    }

    fun setTimeoutMinutes(context: Context, minutes: Int) {
        init(context)
        prefs?.edit()?.putInt(KEY_TIMEOUT_MINUTES, minutes)?.apply()
        _timeoutMinutes.value = minutes
    }

    fun setPin(context: Context, pin: String, securityQuestion: String? = null, securityAnswer: String? = null) {
        init(context)
        val salt = java.util.UUID.randomUUID().toString().take(8)
        val hash = hashString("$salt:$pin")
        val editor = prefs?.edit() ?: return
        editor.putString(KEY_PIN_SALT, salt)
        editor.putString(KEY_PIN_HASH, hash)
        editor.putInt(KEY_FAILED_ATTEMPTS, 0)
        if (!securityQuestion.isNullOrBlank() && !securityAnswer.isNullOrBlank()) {
            val answerHash = hashString(securityAnswer.trim().lowercase())
            editor.putString(KEY_SECURITY_Q, securityQuestion)
            editor.putString(KEY_SECURITY_A_HASH, answerHash)
            _securityQuestion.value = securityQuestion
        }
        editor.apply()
        _hasPin.value = true
        _failedAttempts.value = 0
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        init(context)
        val p = prefs ?: return false
        val salt = p.getString(KEY_PIN_SALT, "") ?: ""
        val storedHash = p.getString(KEY_PIN_HASH, "") ?: ""
        if (storedHash.isEmpty()) return true

        val computed = hashString("$salt:$pin")
        val isMatch = (computed == storedHash)
        if (isMatch) {
            _failedAttempts.value = 0
            p.edit().putInt(KEY_FAILED_ATTEMPTS, 0).apply()
        } else {
            val nextFailed = _failedAttempts.value + 1
            _failedAttempts.value = nextFailed
            p.edit().putInt(KEY_FAILED_ATTEMPTS, nextFailed).apply()
            if (nextFailed >= 3) {
                captureIntruderSelfie(context)
            }
            if (_vibrateOnError.value) {
                vibrate(context)
            }
        }
        return isMatch
    }

    fun captureIntruderSelfie(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.d("AppLocker", "Camera permission not granted for intruder capture")
            return
        }
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager ?: return
        try {
            val frontId = manager.cameraIdList.find { id ->
                val chars = manager.getCameraCharacteristics(id)
                chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
            } ?: return

            val dir = java.io.File(context.filesDir, "intruder_captures")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "intruder_${System.currentTimeMillis()}.jpg")

            manager.openCamera(frontId, object : android.hardware.camera2.CameraDevice.StateCallback() {
                override fun onOpened(camera: android.hardware.camera2.CameraDevice) {
                    try {
                        val reader = android.media.ImageReader.newInstance(640, 480, android.graphics.ImageFormat.JPEG, 1)
                        reader.setOnImageAvailableListener({ r ->
                            val image = r.acquireLatestImage()
                            if (image != null) {
                                val buffer = image.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                try {
                                    file.writeBytes(bytes)
                                    android.util.Log.d("AppLocker", "Intruder selfie captured successfully")
                                } catch (_: Exception) {}
                                image.close()
                                r.close()
                            }
                        }, null)

                        val captureBuilder = camera.createCaptureRequest(android.hardware.camera2.CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                            addTarget(reader.surface)
                            set(android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE, android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE_ON)
                        }

                        camera.createCaptureSession(listOf(reader.surface), object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                                try {
                                    session.capture(captureBuilder.build(), object : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(session: android.hardware.camera2.CameraCaptureSession, request: android.hardware.camera2.CaptureRequest, result: android.hardware.camera2.TotalCaptureResult) {
                                            super.onCaptureCompleted(session, request, result)
                                            camera.close()
                                        }
                                    }, null)
                                } catch (_: Exception) {
                                    camera.close()
                                }
                            }
                            override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                                camera.close()
                            }
                        }, null)
                    } catch (e: Exception) {
                        camera.close()
                    }
                }
                override fun onDisconnected(camera: android.hardware.camera2.CameraDevice) { camera.close() }
                override fun onError(camera: android.hardware.camera2.CameraDevice, error: Int) { camera.close() }
            }, null)
        } catch (_: Exception) {}
    }

    fun resetPinWithSecurityAnswer(context: Context, answer: String, newPin: String): Boolean {
        init(context)
        val p = prefs ?: return false
        val storedAnswerHash = p.getString(KEY_SECURITY_A_HASH, null) ?: return false
        val computed = hashString(answer.trim().lowercase())
        if (computed == storedAnswerHash) {
            setPin(context, newPin)
            return true
        }
        if (_vibrateOnError.value) {
            vibrate(context)
        }
        return false
    }

    fun toggleAppLock(context: Context, packageName: String): Boolean {
        init(context)
        // Self-package (Toolkit itself) cannot be locked
        if (isSelfPackageName(packageName) || packageName == context.packageName || packageName == context.applicationContext.packageName) {
            return false
        }
        val current = _lockedPackages.value.toMutableSet()
        val willLock = !current.contains(packageName)
        if (willLock) current.add(packageName) else current.remove(packageName)
        prefs?.edit()?.putStringSet(KEY_LOCKED_PACKAGES, current)?.apply()
        _lockedPackages.value = current
        syncBackgroundProtectionServices(context)
        return willLock
    }

    fun isAppLocked(packageName: String): Boolean {
        if (isSelfPackageName(packageName)) return false
        return _isLockerEnabled.value && _lockedPackages.value.contains(packageName)
    }

    fun toggleToolLock(context: Context, toolId: String): Boolean {
        init(context)
        val current = _lockedToolIds.value.toMutableSet()
        val willLock = !current.contains(toolId)
        if (willLock) current.add(toolId) else current.remove(toolId)
        prefs?.edit()?.putStringSet(KEY_LOCKED_TOOLS, current)?.apply()
        _lockedToolIds.value = current
        return willLock
    }

    fun lockAllTools(context: Context) {
        init(context)
        val allIds = availableTools.map { it.id }.toSet()
        prefs?.edit()?.putStringSet(KEY_LOCKED_TOOLS, allIds)?.apply()
        _lockedToolIds.value = allIds
    }

    fun unlockAllTools(context: Context) {
        init(context)
        prefs?.edit()?.putStringSet(KEY_LOCKED_TOOLS, emptySet())?.apply()
        _lockedToolIds.value = emptySet()
    }

    fun isToolLockedByRoute(route: String): Boolean {
        if (!_isLockerEnabled.value) return false
        val tool = availableTools.find { it.route == route } ?: return false
        if (!_lockedToolIds.value.contains(tool.id)) return false

        // Check active temporary session
        val unlockTime = unlockedSessions[route]
        if (unlockTime != null) {
            val timeoutMillis = _timeoutMinutes.value * 60 * 1000L
            if (timeoutMillis == 0L) {
                // Immediate timeout: unlocked for this single navigation request
                return false
            }
            if (System.currentTimeMillis() - unlockTime < timeoutMillis) {
                return false
            }
        }
        return true
    }

    fun isPackageUnlocked(packageName: String): Boolean {
        val unlockTime = unlockedSessions[packageName] ?: return false
        val timeoutMinutesVal = _timeoutMinutes.value
        if (timeoutMinutesVal == 0) {
            // Immediate timeout: valid while active in current continuous foreground session
            return true
        }
        val timeoutMillis = timeoutMinutesVal * 60 * 1000L
        return (System.currentTimeMillis() - unlockTime < timeoutMillis)
    }

    fun onForegroundPackageChanged(foregroundPackage: String) {
        // Ignore toolkit and self activities (e.g. PIN screen or settings)
        if (isSelfPackageName(foregroundPackage)) return

        // When timeout is set to Immediate (0), revoke unlock session once user navigates away from that app
        if (_timeoutMinutes.value == 0) {
            val expired = unlockedSessions.keys.filter { it != foregroundPackage }
            expired.forEach { unlockedSessions.remove(it) }
        }
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            return pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        }
        return true
    }

    fun requestIgnoreBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    fun unlockSession(routeOrPackage: String) {
        unlockedSessions[routeOrPackage] = System.currentTimeMillis()
    }

    fun clearUnlockSessions() {
        unlockedSessions.clear()
    }

    fun checkBiometricStatus(context: Context): BiometricStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            return if (km?.isDeviceSecure == true) BiometricStatus.AVAILABLE else BiometricStatus.NOT_ENROLLED
        }

        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km == null || !km.isDeviceSecure) {
            return BiometricStatus.NOT_ENROLLED
        }
        return BiometricStatus.AVAILABLE
    }

    fun showBiometricPrompt(
        activity: Activity,
        title: String = "App Locker Authentication",
        subtitle: String = "Verify identity with fingerprint or face",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val prompt = android.hardware.biometrics.BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setDescription("Use biometric sensor to unlock protected feature")
                    .setNegativeButton("Enter PIN", activity.mainExecutor) { _, _ ->
                        onError("PIN_REQUESTED")
                    }
                    .build()

                val cancellationSignal = CancellationSignal()
                prompt.authenticate(
                    cancellationSignal,
                    activity.mainExecutor,
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                            activity.runOnUiThread { onSuccess() }
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            activity.runOnUiThread {
                                if (errorCode == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                                    errorCode == android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_CANCELED ||
                                    errorCode == 13 /* BIOMETRIC_ERROR_NEGATIVE_BUTTON */) {
                                    onError("PIN_REQUESTED")
                                } else {
                                    onError(errString?.toString() ?: "Biometric error")
                                }
                            }
                        }

                        override fun onAuthenticationFailed() {
                            // Single attempt failed; framework stays active
                        }
                    }
                )
            } catch (e: Throwable) {
                onError(e.localizedMessage ?: "Biometric prompt unavailable")
            }
        } else {
            onError("PIN_REQUESTED")
        }
    }

    private fun hashString(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun vibrate(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(100)
            }
        } catch (_: Exception) {}
    }
}
