package com.arwora.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.arwora.AppLockActivity
import com.arwora.utils.AppHiderManager
import com.arwora.utils.AppLockerManager

/**
 * Monitors package removal events and intercepts uninstall requests for protected apps
 * or Arwora Hyper Toolkit itself by prompting App Locker authentication.
 */
class UninstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val appContext = context.applicationContext
        AppLockerManager.init(appContext)

        val targetPackage = intent.data?.schemeSpecificPart
            ?: intent.getStringExtra(EXTRA_TARGET_PACKAGE)

        when (action) {
            Intent.ACTION_DELETE, ACTION_INTERCEPT_UNINSTALL -> {
                interceptUninstallIntent(appContext, targetPackage)
            }
            Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                val isReplacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                if (!isReplacing && !targetPackage.isNullOrBlank()) {
                    AppHiderManager.onPackageRemoved(appContext, targetPackage)
                }
            }
        }
    }

    companion object {
        private const val TAG = "UninstallReceiver"
        const val ACTION_INTERCEPT_UNINSTALL = "com.arwora.action.INTERCEPT_UNINSTALL"
        const val EXTRA_TARGET_PACKAGE = "extra_uninstall_target_package"

        @Volatile
        private var lastPromptTimestampMs: Long = 0L

        /**
         * Returns true if uninstall protection intercepted the request and launched [AppLockActivity].
         */
        fun interceptUninstallIntent(
            context: Context,
            targetPackage: String?,
            bypassCooldown: Boolean = false
        ): Boolean {
            val appContext = context.applicationContext
            AppLockerManager.init(appContext)

            if (!AppLockerManager.isUninstallProtectionEnabled.value) return false
            if (AppLockerManager.isUninstallAuthorized()) return false
            if (!AppLockerManager.isPackageProtectedFromUninstall(targetPackage)) return false

            val now = System.currentTimeMillis()
            if (!bypassCooldown && now - lastPromptTimestampMs < 1200L) {
                return true
            }
            lastPromptTimestampMs = now

            val resolvedPkg = targetPackage?.takeIf { it.isNotBlank() } ?: appContext.packageName
            return try {
                val lockIntent = Intent(appContext, AppLockActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    putExtra(AppLockActivity.EXTRA_PACKAGE_NAME, resolvedPkg)
                    putExtra(AppLockActivity.EXTRA_LOCK_MODE, AppLockActivity.MODE_UNINSTALL_GUARD)
                }
                appContext.startActivity(lockIntent)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch uninstall guard activity for $resolvedPkg", e)
                false
            }
        }
    }
}
