package com.arwora.utils

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import com.arwora.deviceadmin.AppDeviceAdminReceiver
import com.arwora.feature.system.apps.capability.AppManagerCapability
import com.arwora.feature.system.apps.capability.Capabilities
import com.arwora.receiver.SecretCodeReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class HiderPrivilegeMode(val displayLabel: String) {
    DEVICE_OWNER("Device Owner Active"),
    SHIZUKU("Shizuku Privilege Active"),
    WIRELESS_ADB("Wireless ADB Active"),
    VAULT_ONLY("In-App Vault Mode")
}

data class HideOperationResult(
    val success: Boolean,
    val systemLevelHidden: Boolean,
    val privilegeMode: HiderPrivilegeMode,
    val message: String
)

/**
 * Stealth & App Hiding Engine for App Locker.
 *
 * Supports:
 * 1. Device Owner hidden state via [DevicePolicyManager.setApplicationHidden]
 * 2. Privileged shell hiding (`pm hide` / fallback `pm disable-user --user 0`) via Shizuku or Wireless ADB
 * 3. Self-Stealth launcher hiding for Arwora Hyper Toolkit + recovery via `*#*#2796#*#*` [SecretCodeReceiver]
 * 4. Honest in-app PIN/Biometric-gated Stealth Vault tracking when unprivileged
 */
object AppHiderManager {

    private const val TAG = "AppHiderManager"
    private const val PREFS_NAME = "app_hider_preferences"
    private const val KEY_HIDDEN_PACKAGES = "vault_hidden_packages"
    private const val KEY_EXCLUDED_RECENTS = "vault_excluded_recents_packages"

    private var prefs: SharedPreferences? = null

    private val _hiddenPackages = MutableStateFlow<Set<String>>(emptySet())
    val hiddenPackages: StateFlow<Set<String>> = _hiddenPackages.asStateFlow()

    private val _privilegeMode = MutableStateFlow(HiderPrivilegeMode.VAULT_ONLY)
    val privilegeMode: StateFlow<HiderPrivilegeMode> = _privilegeMode.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    // Optional test hooks
    internal var deviceOwnerProbeOverride: (() -> Boolean)? = null
    internal var deviceOwnerHideRunnerOverride: ((String, Boolean) -> Boolean)? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            syncFromPrefs()
        }
        refreshPrivilegeMode(context)
    }

    fun resetForTests(context: Context) {
        deviceOwnerProbeOverride = null
        deviceOwnerHideRunnerOverride = null
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs?.edit()?.clear()?.apply()
        _hiddenPackages.value = emptySet()
        _statusMessage.value = null
        refreshPrivilegeMode(context)
    }

    private fun syncFromPrefs() {
        val raw = prefs?.getStringSet(KEY_HIDDEN_PACKAGES, emptySet()) ?: emptySet()
        _hiddenPackages.value = raw.filterNot { AppLockerManager.isSelfPackageName(it) }.toSet()
    }

    fun detectPrivilegeMode(context: Context): HiderPrivilegeMode {
        val isDo = try {
            deviceOwnerProbeOverride?.invoke() ?: run {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                dpm?.isDeviceOwnerApp(context.packageName) == true
            }
        } catch (_: Throwable) {
            false
        }
        if (isDo) return HiderPrivilegeMode.DEVICE_OWNER

        return when (Capabilities.detectCapability(context)) {
            AppManagerCapability.DEVICE_OWNER -> HiderPrivilegeMode.DEVICE_OWNER
            AppManagerCapability.WIRELESS_ADB -> {
                if (AdbShellExecutor.hasShizukuPermission()) HiderPrivilegeMode.SHIZUKU
                else HiderPrivilegeMode.WIRELESS_ADB
            }
            AppManagerCapability.NONE -> HiderPrivilegeMode.VAULT_ONLY
        }
    }

    fun refreshPrivilegeMode(context: Context): HiderPrivilegeMode {
        val mode = detectPrivilegeMode(context)
        _privilegeMode.value = mode
        return mode
    }

    fun isAppHidden(packageName: String): Boolean = _hiddenPackages.value.contains(packageName)

    fun isSecretDialerCode(input: String): Boolean {
        val trimmed = input.trim()
        val code = AppLockerManager.secretDialerCode.value
        return trimmed == "*#*#$code#*#*" || trimmed == code
    }

    suspend fun hideApp(context: Context, packageName: String): HideOperationResult = withContext(Dispatchers.IO) {
        init(context)
        if (packageName.isBlank() ||
            AppLockerManager.isSelfPackageName(packageName) ||
            packageName == context.packageName
        ) {
            return@withContext HideOperationResult(
                success = false,
                systemLevelHidden = false,
                privilegeMode = _privilegeMode.value,
                message = "Use Self-Stealth Mode to hide Arwora Hyper Toolkit itself."
            )
        }

        val mode = refreshPrivilegeMode(context)
        var systemHidden = false
        var opNote = ""

        when (mode) {
            HiderPrivilegeMode.DEVICE_OWNER -> {
                systemHidden = applyDeviceOwnerHidden(context, packageName, true)
                opNote = if (systemHidden) {
                    "Hidden from launcher and recents via Device Owner."
                } else {
                    "Device Owner hide call failed; tracked inside Stealth Vault."
                }
            }
            HiderPrivilegeMode.SHIZUKU, HiderPrivilegeMode.WIRELESS_ADB -> {
                val hideRes = AdbShellExecutor.execute("pm hide $packageName", context = context)
                if (hideRes.success) {
                    systemHidden = true
                    opNote = "Hidden from launcher & recents via ${mode.displayLabel}."
                } else {
                    val disableRes = AdbShellExecutor.execute(
                        "pm disable-user --user 0 $packageName",
                        context = context
                    )
                    if (disableRes.success) {
                        systemHidden = true
                        opNote = "Disabled from launcher via ${mode.displayLabel}."
                    } else {
                        opNote = "Shell hide returned: ${disableRes.output.ifBlank { hideRes.output }}. Tracked in Vault."
                    }
                }
            }
            HiderPrivilegeMode.VAULT_ONLY -> {
                opNote = "Added to PIN-protected Stealth Vault. Activate Shizuku, Wireless ADB, or Device Owner to also remove the icon from system launcher."
            }
        }

        val updated = _hiddenPackages.value + packageName
        prefs?.edit()?.putStringSet(KEY_HIDDEN_PACKAGES, updated)?.apply()
        _hiddenPackages.value = updated
        _statusMessage.value = opNote

        HideOperationResult(
            success = true,
            systemLevelHidden = systemHidden,
            privilegeMode = mode,
            message = opNote
        )
    }

    suspend fun unhideApp(context: Context, packageName: String): HideOperationResult = withContext(Dispatchers.IO) {
        init(context)
        val mode = refreshPrivilegeMode(context)
        var systemRestored = false
        var opNote = "Restored from Stealth Vault."

        when (mode) {
            HiderPrivilegeMode.DEVICE_OWNER -> {
                systemRestored = applyDeviceOwnerHidden(context, packageName, false)
                if (systemRestored) {
                    opNote = "Unhidden and restored to system launcher via Device Owner."
                }
            }
            HiderPrivilegeMode.SHIZUKU, HiderPrivilegeMode.WIRELESS_ADB -> {
                val unhideRes = AdbShellExecutor.execute("pm unhide $packageName", context = context)
                val enableRes = AdbShellExecutor.execute("pm enable $packageName", context = context)
                systemRestored = unhideRes.success || enableRes.success
                if (systemRestored) {
                    opNote = "Restored to system launcher via ${mode.displayLabel}."
                }
            }
            HiderPrivilegeMode.VAULT_ONLY -> {
                opNote = "Removed from Stealth Vault."
            }
        }

        val updated = _hiddenPackages.value - packageName
        prefs?.edit()?.putStringSet(KEY_HIDDEN_PACKAGES, updated)?.apply()
        _hiddenPackages.value = updated
        _statusMessage.value = opNote

        HideOperationResult(
            success = true,
            systemLevelHidden = systemRestored,
            privilegeMode = mode,
            message = opNote
        )
    }

    suspend fun launchHiddenApp(context: Context, packageName: String): HideOperationResult = withContext(Dispatchers.IO) {
        init(context)
        val mode = refreshPrivilegeMode(context)
        if (mode == HiderPrivilegeMode.DEVICE_OWNER) {
            applyDeviceOwnerHidden(context, packageName, false)
        } else if (mode == HiderPrivilegeMode.SHIZUKU || mode == HiderPrivilegeMode.WIRELESS_ADB) {
            AdbShellExecutor.execute("pm unhide $packageName", context = context)
            AdbShellExecutor.execute("pm enable $packageName", context = context)
        }

        AppLockerManager.unlockSession(packageName)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }

        if (launchIntent == null) {
            return@withContext HideOperationResult(
                success = false,
                systemLevelHidden = false,
                privilegeMode = mode,
                message = "Unable to resolve launcher activity for $packageName"
            )
        }

        withContext(Dispatchers.Main) {
            try {
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch hidden app $packageName", e)
            }
        }

        HideOperationResult(
            success = true,
            systemLevelHidden = mode != HiderPrivilegeMode.VAULT_ONLY,
            privilegeMode = mode,
            message = "Launched $packageName privately (excluded from Recents)."
        )
    }

    /**
     * Toggles Arwora Hyper Toolkit's own launcher icon visibility.
     * Ensures [SecretCodeReceiver] (`*#*#2796#*#*`) stays active so the user can always reopen the Vault.
     */
    fun setSelfStealthEnabled(context: Context, enabled: Boolean): HideOperationResult {
        init(context)
        val pm = context.packageManager
        val pkg = context.packageName
        var updatedCount = 0

        try {
            // Always keep SecretCodeReceiver enabled before hiding launcher icons
            pm.setComponentEnabledSetting(
                ComponentName(pkg, SecretCodeReceiver::class.java.name),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not ensure SecretCodeReceiver enabled: ${e.message}")
        }

        val launcherComponents = listOf(".SplashActivity") + IconManager.variants.map { it.alias }
        if (enabled) {
            launcherComponents.forEach { suffix ->
                val fullClass = "$pkg$suffix"
                runCatching {
                    pm.setComponentEnabledSetting(
                        ComponentName(pkg, fullClass),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    updatedCount++
                }
            }
        } else {
            runCatching {
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, "$pkg.SplashActivity"),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                updatedCount++
            }
            // Keep secondary icon aliases in sync with user preference or disabled if default SplashActivity is shown
            IconManager.variants.forEach { variant ->
                runCatching {
                    pm.setComponentEnabledSetting(
                        ComponentName(pkg, "$pkg${variant.alias}"),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }

        AppLockerManager.setSelfStealthState(context, enabled)
        val dialCode = "*#*#${AppLockerManager.secretDialerCode.value}#*#*"
        val msg = if (enabled) {
            "Stealth Mode enabled ($updatedCount launcher entries hidden). Dial $dialCode to reopen Vault."
        } else {
            "Arwora Hyper Toolkit launcher icon restored."
        }
        _statusMessage.value = msg

        return HideOperationResult(
            success = updatedCount > 0,
            systemLevelHidden = enabled,
            privilegeMode = _privilegeMode.value,
            message = msg
        )
    }

    fun onPackageRemoved(context: Context, packageName: String) {
        init(context)
        if (_hiddenPackages.value.contains(packageName)) {
            val updated = _hiddenPackages.value - packageName
            prefs?.edit()?.putStringSet(KEY_HIDDEN_PACKAGES, updated)?.apply()
            _hiddenPackages.value = updated
        }
    }

    private fun applyDeviceOwnerHidden(context: Context, packageName: String, hidden: Boolean): Boolean {
        deviceOwnerHideRunnerOverride?.let { return it(packageName, hidden) }
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
            val admin = ComponentName(context, AppDeviceAdminReceiver::class.java)
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                dpm.setApplicationHidden(admin, packageName, hidden)
            } else {
                false
            }
        } catch (t: Throwable) {
            Log.e(TAG, "DeviceOwner setApplicationHidden error", t)
            false
        }
    }
}
