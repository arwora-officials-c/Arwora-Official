package com.arwora.feature.system.appops.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.arwora.feature.system.appops.model.AppOpsItem
import com.arwora.feature.system.appops.model.PermissionItem
import com.arwora.feature.system.appops.model.StandbyBucketMode
import com.arwora.utils.AdbShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppOpsManager {

    private const val TAG = "AppOpsManager"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun isAdbAvailable(): Boolean {
        return AdbShellExecutor.isPrivilegedShellReady()
    }

    fun runShellCommand(command: String): String {
        if (isAdbAvailable()) {
            val adbRes = AdbShellExecutor.executeBlocking(command)
            if (adbRes.success) {
                return adbRes.output.trim()
            }
        }
        val result = StringBuilder()
        var process: Process? = null
        try {
            try {
                process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            } catch (_: Exception) {
                process = Runtime.getRuntime().exec(command)
            }

            process?.let { p ->
                val reader = BufferedReader(InputStreamReader(p.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    result.append(line).append("\n")
                }
                p.waitFor()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Shell execution failed ($command): ${e.message}")
        } finally {
            process?.destroy()
        }
        return result.toString().trim()
    }

    fun setAppOp(packageName: String, opName: String, mode: String): Boolean {
        val cmd = "appops set $packageName $opName $mode"
        val output = runShellCommand(cmd)
        val verifiedMode = getAppOp(packageName, opName)
        return verifiedMode.equals(mode, ignoreCase = true) || output.isEmpty()
    }

    fun getAppOp(packageName: String, opName: String): String {
        val output = runShellCommand("appops get $packageName $opName")
        val lower = output.lowercase(Locale.ROOT)
        return when {
            lower.contains("allow") -> "allow"
            lower.contains("ignore") -> "ignore"
            lower.contains("deny") -> "deny"
            lower.contains("default") -> "default"
            else -> "default"
        }
    }

    fun revokeAllSensitiveOps(packageName: String): Boolean {
        val sensitiveOps = listOf(
            "CAMERA",
            "RECORD_AUDIO",
            "COARSE_LOCATION",
            "FINE_LOCATION",
            "SYSTEM_ALERT_WINDOW",
            "WRITE_SETTINGS",
            "RUN_IN_BACKGROUND"
        )
        var allSuccess = true
        for (op in sensitiveOps) {
            val success = setAppOp(packageName, op, "deny")
            if (!success) allSuccess = false
        }
        return allSuccess
    }

    suspend fun getInstalledApps(context: Context): List<AppOpsItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val usageStatsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        } else null

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PackageManager.PackageInfoFlags.of(
                (PackageManager.GET_PERMISSIONS or 
                 PackageManager.MATCH_DISABLED_COMPONENTS or 
                 PackageManager.MATCH_UNINSTALLED_PACKAGES).toLong()
            )
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_PERMISSIONS or 
            PackageManager.MATCH_DISABLED_COMPONENTS or 
            PackageManager.MATCH_UNINSTALLED_PACKAGES
        }

        val rawPackages: List<PackageInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(flags as PackageManager.PackageInfoFlags)
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags as Int)
            }
        } catch (e: Exception) {
            emptyList()
        }

        rawPackages.mapNotNull { pkgInfo ->
            try {
                val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                val packageName = pkgInfo.packageName ?: return@mapNotNull null
                val appName = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    packageName
                }

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isDisabled = !appInfo.enabled || try {
                    val state = pm.getApplicationEnabledSetting(packageName)
                    state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                    state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                } catch (e: Exception) {
                    false
                }

                val launchIntent = try {
                    pm.getLaunchIntentForPackage(packageName)
                } catch (e: Exception) {
                    null
                }
                val isHidden = launchIntent == null

                val firstInstall = pkgInfo.firstInstallTime
                val lastUpdate = pkgInfo.lastUpdateTime
                val firstInstallFormatted = if (firstInstall > 0) dateFormat.format(Date(firstInstall)) else "N/A"
                val lastUpdateFormatted = if (lastUpdate > 0) dateFormat.format(Date(lastUpdate)) else "N/A"

                val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appInfo.minSdkVersion
                } else 21
                val targetSdk = appInfo.targetSdkVersion
                val uid = appInfo.uid
                val dataDir = appInfo.dataDir ?: "/data/data/$packageName"
                val sourceDir = appInfo.sourceDir ?: ""

                val requestedPerms = mutableListOf<PermissionItem>()
                var hasWakeLock = false
                var hasBoot = false
                var hasAlertWindow = false
                var hasBgLocation = false

                val permissionsArray = pkgInfo.requestedPermissions
                val requestedFlags = pkgInfo.requestedPermissionsFlags

                if (permissionsArray != null) {
                    for (i in permissionsArray.indices) {
                        val permName = permissionsArray[i]
                        val isGranted = if (requestedFlags != null && i < requestedFlags.size) {
                            (requestedFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                        } else {
                            try {
                                pm.checkPermission(permName, packageName) == PackageManager.PERMISSION_GRANTED
                            } catch (e: Exception) {
                                false
                            }
                        }

                        if (permName.equals(android.Manifest.permission.WAKE_LOCK, ignoreCase = true)) {
                            hasWakeLock = true
                        }
                        if (permName.equals(android.Manifest.permission.RECEIVE_BOOT_COMPLETED, ignoreCase = true)) {
                            hasBoot = true
                        }
                        if (permName.equals(android.Manifest.permission.SYSTEM_ALERT_WINDOW, ignoreCase = true)) {
                            hasAlertWindow = true
                        }
                        if (permName.equals("android.permission.ACCESS_BACKGROUND_LOCATION", ignoreCase = true)) {
                            hasBgLocation = true
                        }

                        val simple = permName.substringAfterLast(".")
                        val group = resolvePermissionGroup(permName)
                        val isDangerous = group != "Normal"

                        requestedPerms.add(
                            PermissionItem(
                                permission = permName,
                                simpleName = simple,
                                group = group,
                                isGranted = isGranted,
                                isDangerous = isDangerous
                            )
                        )
                    }
                }

                val standbyBucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && usageStatsManager != null) {
                    try {
                        val bucket = usageStatsManager.getAppStandbyBucket()
                        when {
                            bucket <= 10 -> StandbyBucketMode.UNRESTRICTED
                            bucket <= 20 -> StandbyBucketMode.DEFAULT
                            bucket <= 40 -> StandbyBucketMode.RARE
                            bucket <= 45 -> StandbyBucketMode.RESTRICTED
                            else -> StandbyBucketMode.NEVER
                        }
                    } catch (e: Exception) {
                        StandbyBucketMode.DEFAULT
                    }
                } else {
                    StandbyBucketMode.DEFAULT
                }

                val isBgRestricted = if (powerManager != null) {
                    !powerManager.isIgnoringBatteryOptimizations(packageName)
                } else false

                val icon = try {
                    pm.getApplicationIcon(appInfo)
                } catch (e: Exception) {
                    null
                }

                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }

                // Quick initial App Ops states check for key ops
                val opsStates = mutableMapOf<String, String>()
                try {
                    opsStates["SYSTEM_ALERT_WINDOW"] = getAppOp(packageName, "SYSTEM_ALERT_WINDOW")
                    opsStates["WRITE_SETTINGS"] = getAppOp(packageName, "WRITE_SETTINGS")
                    opsStates["CAMERA"] = getAppOp(packageName, "CAMERA")
                    opsStates["ACCESS_FINE_LOCATION"] = getAppOp(packageName, "COARSE_LOCATION")
                } catch (_: Exception) {}

                AppOpsItem(
                    appName = appName,
                    packageName = packageName,
                    versionName = pkgInfo.versionName ?: "1.0",
                    versionCode = versionCode,
                    uid = uid,
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    dataDir = dataDir,
                    sourceDir = sourceDir,
                    firstInstallTime = firstInstall,
                    lastUpdateTime = lastUpdate,
                    firstInstallFormatted = firstInstallFormatted,
                    lastUpdateFormatted = lastUpdateFormatted,
                    isSystem = isSystem,
                    isDisabled = isDisabled,
                    isHidden = isHidden,
                    requestedPermissions = requestedPerms,
                    standbyBucket = standbyBucket,
                    isBackgroundRestricted = isBgRestricted,
                    hasWakeLockPermission = hasWakeLock,
                    hasBootPermission = hasBoot,
                    hasAlertWindowPermission = hasAlertWindow,
                    hasBackgroundLocationPermission = hasBgLocation,
                    appOpsStates = opsStates,
                    icon = icon
                )
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.appName.lowercase(Locale.ROOT) }
    }

    private fun resolvePermissionGroup(perm: String): String {
        return when {
            perm.contains("CAMERA", ignoreCase = true) -> "Camera"
            perm.contains("LOCATION", ignoreCase = true) -> "Location"
            perm.contains("STORAGE", ignoreCase = true) || perm.contains("MEDIA", ignoreCase = true) -> "Storage"
            perm.contains("RECORD_AUDIO", ignoreCase = true) || perm.contains("AUDIO", ignoreCase = true) -> "Microphone"
            perm.contains("CONTACTS", ignoreCase = true) -> "Contacts"
            perm.contains("PHONE", ignoreCase = true) || perm.contains("CALL", ignoreCase = true) -> "Phone"
            perm.contains("SMS", ignoreCase = true) -> "SMS"
            perm.contains("NOTIFICATION", ignoreCase = true) -> "Notifications"
            perm.contains("CALENDAR", ignoreCase = true) -> "Calendar"
            perm.contains("SENSOR", ignoreCase = true) -> "Sensors"
            perm.contains("WAKE_LOCK", ignoreCase = true) -> "Wake Lock"
            perm.contains("BOOT", ignoreCase = true) -> "Autostart"
            perm.contains("SYSTEM_ALERT_WINDOW", ignoreCase = true) -> "Alert Window"
            else -> "Normal"
        }
    }

    fun openApp(context: Context, packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun uninstallApp(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:$packageName")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(deleteIntent)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun openAppDetails(context: Context, packageName: String) {
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

    fun openPermissionSettings(context: Context, packageName: String) {
        try {
            val intent = Intent("android.intent.action.MANAGE_APP_PERMISSIONS").apply {
                putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppDetails(context, packageName)
        }
    }
}
