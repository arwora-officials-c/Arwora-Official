package com.arwora.deviceadmin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.arwora.AppLockActivity
import com.arwora.utils.AppLockerManager

class AppDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        AppLockerManager.init(context.applicationContext)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val appContext = context.applicationContext
        AppLockerManager.init(appContext)
        if (AppLockerManager.isUninstallProtectionEnabled.value && !AppLockerManager.isUninstallAuthorized()) {
            runCatching {
                val lockIntent = Intent(appContext, AppLockActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                    putExtra(AppLockActivity.EXTRA_PACKAGE_NAME, appContext.packageName)
                    putExtra(AppLockActivity.EXTRA_LOCK_MODE, AppLockActivity.MODE_UNINSTALL_GUARD)
                }
                appContext.startActivity(lockIntent)
            }
            return "App Locker Uninstall Protection is active. Authenticate with your PIN or biometric before deactivating protection."
        }
        return "Deactivating Device Admin removes uninstall protection for Arwora Hyper Toolkit."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, AppDeviceAdminReceiver::class.java)
        }

        fun isDeviceAdminActive(context: Context): Boolean {
            return try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                dpm?.isAdminActive(getComponentName(context)) == true
            } catch (_: Throwable) {
                false
            }
        }

        fun requestDeviceAdminActivation(context: Context) {
            runCatching {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Enables Arwora Uninstall Protection guard against unauthorized removal."
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }

        fun syncDeviceOwnerUninstallPolicy(context: Context, block: Boolean) {
            try {
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return
                val admin = getComponentName(context)
                if (dpm.isDeviceOwnerApp(context.packageName)) {
                    dpm.setUninstallBlocked(admin, context.packageName, block)
                    AppLockerManager.lockedPackages.value.forEach { pkg ->
                        runCatching { dpm.setUninstallBlocked(admin, pkg, block) }
                    }
                }
            } catch (_: Throwable) {}
        }
    }
}
