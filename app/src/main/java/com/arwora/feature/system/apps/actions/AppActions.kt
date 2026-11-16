package com.arwora.feature.system.apps.actions

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.arwora.feature.system.apps.capability.AppManagerCapability
import com.arwora.feature.system.apps.capability.Capabilities
import com.arwora.feature.system.apps.util.SafeBlocklist
import com.arwora.utils.NativeAdbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppActions {

    suspend fun enableApp(
        packageName: String,
        capability: AppManagerCapability,
        context: Context
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        when (capability) {
            AppManagerCapability.WIRELESS_ADB -> {
                try {
                    val res = NativeAdbManager.executeShellCommand("pm enable $packageName")
                    if (res.success && !res.output.contains("Exception", ignoreCase = true)) {
                        Result.success(true)
                    } else {
                        Result.failure(Exception(res.output.ifBlank { "pm enable exited with code ${res.exitCode}" }))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            AppManagerCapability.DEVICE_OWNER -> {
                try {
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val admin = Capabilities.getAdminComponent(context)
                    dpm.setApplicationHidden(admin, packageName, false)
                    try {
                        context.packageManager.setApplicationEnabledSetting(
                            packageName,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                            0
                        )
                    } catch (_: Exception) {}
                    Result.success(true)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            AppManagerCapability.NONE -> {
                Result.failure(IllegalStateException("Requires Wireless ADB or Device Owner"))
            }
        }
    }

    suspend fun disableApp(
        packageName: String,
        capability: AppManagerCapability,
        context: Context
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        if (SafeBlocklist.isSafeBlocklisted(packageName, context)) {
            return@withContext Result.failure(IllegalArgumentException("Cannot disable system-critical app."))
        }

        when (capability) {
            AppManagerCapability.WIRELESS_ADB -> {
                try {
                    val res = NativeAdbManager.executeShellCommand("pm disable-user --user 0 $packageName")
                    if (res.success && !res.output.contains("Exception", ignoreCase = true)) {
                        Result.success(true)
                    } else {
                        Result.failure(Exception(res.output.ifBlank { "pm disable-user exited with code ${res.exitCode}" }))
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            AppManagerCapability.DEVICE_OWNER -> {
                try {
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val admin = Capabilities.getAdminComponent(context)
                    dpm.setApplicationHidden(admin, packageName, true)
                    Result.success(true)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            AppManagerCapability.NONE -> {
                Result.failure(IllegalStateException("Requires Wireless ADB or Device Owner"))
            }
        }
    }

    fun uninstallApp(
        packageName: String,
        capability: AppManagerCapability,
        context: Context
    ): Result<Unit> {
        return try {
            if (capability == AppManagerCapability.DEVICE_OWNER) {
                try {
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val admin = Capabilities.getAdminComponent(context)
                    dpm.setUninstallBlocked(admin, packageName, false)
                } catch (_: Exception) {}
            }

            // Method 1: PackageInstaller with PendingIntent
            var launchedViaPackageInstaller = false
            try {
                val packageInstaller = context.packageManager.packageInstaller
                val intent = Intent("com.arwora.action.UNINSTALL_RESULT").apply {
                    setPackage(context.packageName)
                    putExtra("package_name", packageName)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    packageName.hashCode(),
                    intent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                )
                packageInstaller.uninstall(packageName, pendingIntent.intentSender)
                launchedViaPackageInstaller = true
            } catch (_: Exception) {}

            // Fallback: ACTION_UNINSTALL_PACKAGE / ACTION_DELETE
            if (!launchedViaPackageInstaller) {
                val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.parse("package:$packageName")
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(uninstallIntent)
                } catch (_: Exception) {
                    val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(deleteIntent)
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openAppInfo(packageName: String, context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun launchApp(packageName: String, context: Context): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
