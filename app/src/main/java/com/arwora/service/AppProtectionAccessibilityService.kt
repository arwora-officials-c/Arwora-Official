package com.arwora.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.arwora.receiver.UninstallReceiver
import com.arwora.utils.AppLockerManager

/**
 * Lightweight Accessibility Guard for Advanced App Locker Security:
 * 1. Prevents unauthorized uninstallation of Arwora Hyper Toolkit or protected apps
 *    and blocks disabling Arwora Device Admin without PIN/Biometric authentication.
 * 2. Detects system PackageInstaller / Play Store APK installation dialogs when
 *    Block New App Installations is enabled.
 */
class AppProtectionAccessibilityService : AccessibilityService() {

    private var lastEventHandledMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AppLockerManager.init(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        AppLockerManager.init(applicationContext)
        val uninstallGuardOn = AppLockerManager.isUninstallProtectionEnabled.value
        val installBlockOn = AppLockerManager.isInstallBlockEnabled.value
        if (!uninstallGuardOn && !installBlockOn) return

        val foregroundPkg = event.packageName?.toString()?.trim().orEmpty()
        if (foregroundPkg.isEmpty() || AppLockerManager.isSelfPackageName(foregroundPkg)) return

        val now = System.currentTimeMillis()
        if (now - lastEventHandledMs < 750L) return

        val isInstallerUi = foregroundPkg in INSTALLER_PACKAGES
        val isSettingsUi = foregroundPkg == SETTINGS_PACKAGE

        if (!isInstallerUi && !isSettingsUi) return

        val root = runCatching { rootInActiveWindow }.getOrNull()
        val visibleText = collectWindowTextLower(root, maxNodes = 40)
        runCatching { root?.recycle() }

        // 1. Check Install Block on PackageInstaller install prompt
        if (installBlockOn && isInstallerUi && !AppLockerManager.isInstallAuthorized(AppInstallMonitorService.INSTALLER_PLACEHOLDER_PKG)) {
            val looksLikeInstall = visibleText.contains("do you want to install") ||
                visibleText.contains("install this app") ||
                visibleText.contains("install anyway") ||
                (visibleText.contains("install") && !visibleText.contains("uninstall"))
            if (looksLikeInstall) {
                lastEventHandledMs = now
                performGlobalAction(GLOBAL_ACTION_BACK)
                AppInstallMonitorService.handleNewInstallDetected(
                    context = applicationContext,
                    packageName = AppInstallMonitorService.INSTALLER_PLACEHOLDER_PKG
                )
                return
            }
        }

        // 2. Check Uninstall Protection on PackageInstaller or Settings (App Info / Device Admin)
        if (uninstallGuardOn && !AppLockerManager.isUninstallAuthorized()) {
            val looksLikeUninstall = visibleText.contains("uninstall") ||
                visibleText.contains("deactivate") ||
                visibleText.contains("remove device admin") ||
                visibleText.contains("delete app")

            if (looksLikeUninstall) {
                val matchedPkg = findProtectedPackageInText(visibleText)
                val mentionsArwora = visibleText.contains("arwora") || visibleText.contains("hyper toolkit")
                if (mentionsArwora || matchedPkg != null || isInstallerUi) {
                    lastEventHandledMs = now
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    UninstallReceiver.interceptUninstallIntent(
                        context = applicationContext,
                        targetPackage = matchedPkg ?: applicationContext.packageName
                    )
                }
            }
        }
    }

    override fun onInterrupt() {
        // No pending gestures to cancel
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    private fun findProtectedPackageInText(visibleText: String): String? {
        if (visibleText.contains(applicationContext.packageName.lowercase())) {
            return applicationContext.packageName
        }
        val pm = packageManager
        for (pkg in AppLockerManager.lockedPackages.value) {
            if (visibleText.contains(pkg.lowercase())) return pkg
            val appLabel = runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(info).toString().lowercase()
            }.getOrNull()
            if (!appLabel.isNullOrBlank() && visibleText.contains(appLabel)) {
                return pkg
            }
        }
        return null
    }

    private fun collectWindowTextLower(root: AccessibilityNodeInfo?, maxNodes: Int): String {
        if (root == null) return ""
        val sb = StringBuilder()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++
            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()
            if (!text.isNullOrBlank()) {
                sb.append(text.lowercase()).append(' ')
            }
            if (!desc.isNullOrBlank()) {
                sb.append(desc.lowercase()).append(' ')
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                queue.add(child)
            }
        }
        return sb.toString()
    }

    companion object {
        @Volatile
        var instance: AppProtectionAccessibilityService? = null
            private set

        private const val SETTINGS_PACKAGE = "com.android.settings"

        private val INSTALLER_PACKAGES = setOf(
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.samsung.android.packageinstaller",
            "com.miui.packageinstaller",
            "com.coloros.packageinstaller",
            "com.oplus.packageinstaller",
            "com.oneplus.packageinstaller",
            "com.vivo.packageinstaller"
        )

        fun isAccessibilityEnabled(context: Context): Boolean {
            if (instance != null) return true
            return try {
                val expectedComponent = ComponentName(context, AppProtectionAccessibilityService::class.java)
                val enabledServices = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val full = expectedComponent.flattenToString()
                val short = expectedComponent.flattenToShortString()
                enabledServices.split(':').any { entry ->
                    entry.equals(full, ignoreCase = true) || entry.equals(short, ignoreCase = true)
                }
            } catch (_: Exception) {
                false
            }
        }

        fun openAccessibilitySettings(context: Context) {
            runCatching {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }
}
