package com.arwora.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProcessInfo(
    val pid: Int,
    val uid: Int,
    val processName: String,
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val memoryMb: Double,
    val importance: Int,
    val importanceLabel: String,
    val hasWakelock: Boolean,
    val wakelockReason: String,
    val isSystemApp: Boolean,
    val runningServices: List<String> = emptyList()
)

data class ProcessDiagnosticsSummary(
    val totalProcesses: Int = 0,
    val totalMemoryMb: Double = 0.0,
    val wakelockCount: Int = 0,
    val backgroundServicesCount: Int = 0,
    val highMemoryCount: Int = 0
)

object ProcessWakelockManager {

    suspend fun getRunningProcesses(context: Context): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ProcessInfo>()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@withContext emptyList()
        val pm = context.packageManager

        val runningProcesses = try {
            am.runningAppProcesses ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val runningServicesMap = mutableMapOf<String, MutableList<String>>()
        try {
            @Suppress("DEPRECATION")
            val services = am.getRunningServices(300)
            services?.forEach { s ->
                val pkg = s.service.packageName
                val serviceName = s.service.shortClassName.removePrefix(".")
                runningServicesMap.getOrPut(pkg) { mutableListOf() }.add(serviceName)
            }
        } catch (_: Exception) {}

        if (runningProcesses.isEmpty()) return@withContext emptyList()

        val pids = runningProcesses.map { it.pid }.toIntArray()
        val memInfos = try {
            am.getProcessMemoryInfo(pids)
        } catch (_: Exception) {
            null
        }

        runningProcesses.forEachIndexed { index, proc ->
            val pkgName = proc.pkgList?.firstOrNull() ?: proc.processName.split(":").first()
            var appName = proc.processName
            var icon: Drawable? = null
            var isSystem = false

            try {
                val appInfo = pm.getApplicationInfo(pkgName, 0)
                appName = pm.getApplicationLabel(appInfo).toString()
                icon = pm.getApplicationIcon(appInfo)
                isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } catch (_: Exception) {}

            val pssKb = memInfos?.getOrNull(index)?.totalPss ?: 0
            val memMb = String.format(java.util.Locale.US, "%.1f", pssKb / 1024.0).toDoubleOrNull() ?: 0.0

            val (importanceLabel, hasWakelock, wakelockReason) = resolveImportanceAndWakelock(proc.importance, pkgName, runningServicesMap[pkgName])

            list.add(
                ProcessInfo(
                    pid = proc.pid,
                    uid = proc.uid,
                    processName = proc.processName,
                    packageName = pkgName,
                    appName = appName,
                    icon = icon,
                    memoryMb = memMb,
                    importance = proc.importance,
                    importanceLabel = importanceLabel,
                    hasWakelock = hasWakelock,
                    wakelockReason = wakelockReason,
                    isSystemApp = isSystem,
                    runningServices = runningServicesMap[pkgName] ?: emptyList()
                )
            )
        }

        list.sortedByDescending { it.memoryMb }
    }

    private fun resolveImportanceAndWakelock(
        importance: Int,
        packageName: String,
        services: List<String>?
    ): Triple<String, Boolean, String> {
        val hasActiveServices = !services.isNullOrEmpty()
        return when {
            importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> {
                Triple("Foreground", false, "Active User Focus")
            }
            importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> {
                Triple(
                    "Foreground Service",
                    true,
                    if (hasActiveServices) "Ongoing Service (${services?.first()})" else "Persistent Foreground Worker"
                )
            }
            importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> {
                Triple("Visible Window", false, "Visible Activity / Overlay")
            }
            importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> {
                Triple(
                    "Background Service",
                    hasActiveServices,
                    if (hasActiveServices) "Active Background Wakelock (${services?.first()})" else "Background Routine"
                )
            }
            importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE -> {
                Triple("Heavy Process", true, "Retained Background State")
            }
            else -> {
                Triple("Cached Background", false, "Sleeping / Cached")
            }
        }
    }

    suspend fun killBackgroundProcess(context: Context, packageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@withContext false
            am.killBackgroundProcesses(packageName)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun optimizeAllBackgroundProcesses(context: Context): Int = withContext(Dispatchers.IO) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return@withContext 0
        var killedCount = 0
        try {
            val running = am.runningAppProcesses ?: emptyList()
            running.forEach { proc ->
                val pkg = proc.pkgList?.firstOrNull() ?: proc.processName.split(":").first()
                if (pkg != context.packageName && proc.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                    am.killBackgroundProcesses(pkg)
                    killedCount++
                }
            }
            System.gc()
        } catch (_: Exception) {}
        killedCount
    }

    fun openAppDetails(context: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun openBatteryOptimization(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
