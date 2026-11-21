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
import com.arwora.feature.system.appops.model.AppOpDescriptor
import com.arwora.feature.system.appops.model.AppOpsCategory
import com.arwora.feature.system.appops.model.AppOpsItem
import com.arwora.feature.system.appops.model.PermissionItem
import com.arwora.feature.system.appops.model.StandbyBucketMode
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.DualEngineShellResult
import com.arwora.utils.PrivilegeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppOpsManager {

    private const val TAG = "AppOpsManager"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    private val VALID_MODES = setOf("allow", "ignore", "deny", "default")

    val managedOpsCatalog: List<AppOpDescriptor> = listOf(
        // Location
        AppOpDescriptor(
            opName = "COARSE_LOCATION",
            title = "Approximate Location",
            description = "Network and Wi-Fi based coarse location access",
            category = AppOpsCategory.LOCATION,
            relatedPermissions = listOf("ACCESS_COARSE_LOCATION")
        ),
        AppOpDescriptor(
            opName = "FINE_LOCATION",
            title = "Precise Location",
            description = "GPS and high-accuracy hardware location tracking",
            category = AppOpsCategory.LOCATION,
            relatedPermissions = listOf("ACCESS_FINE_LOCATION")
        ),
        AppOpDescriptor(
            opName = "MONITOR_LOCATION",
            title = "Background / Passive Location",
            description = "Continuous passive and background location monitoring",
            category = AppOpsCategory.LOCATION,
            relatedPermissions = listOf("ACCESS_BACKGROUND_LOCATION")
        ),
        // Camera
        AppOpDescriptor(
            opName = "CAMERA",
            title = "Camera Access",
            description = "Hardware camera sensor photo and video capture",
            category = AppOpsCategory.CAMERA,
            relatedPermissions = listOf("CAMERA")
        ),
        // Audio
        AppOpDescriptor(
            opName = "RECORD_AUDIO",
            title = "Microphone / Record Audio",
            description = "Audio recording and live microphone stream access",
            category = AppOpsCategory.AUDIO,
            relatedPermissions = listOf("RECORD_AUDIO")
        ),
        AppOpDescriptor(
            opName = "MODIFY_AUDIO_SETTINGS",
            title = "Modify Audio Settings",
            description = "Adjust global audio routing, speakerphone, and streams",
            category = AppOpsCategory.AUDIO,
            relatedPermissions = listOf("MODIFY_AUDIO_SETTINGS")
        ),
        AppOpDescriptor(
            opName = "TAKE_AUDIO_FOCUS",
            title = "Audio Focus Control",
            description = "Request media playback focus over other apps",
            category = AppOpsCategory.AUDIO
        ),
        // System
        AppOpDescriptor(
            opName = "SYSTEM_ALERT_WINDOW",
            title = "Display Over Other Apps",
            description = "Allows drawing floating overlays above other windows",
            category = AppOpsCategory.SYSTEM,
            relatedPermissions = listOf("SYSTEM_ALERT_WINDOW")
        ),
        AppOpDescriptor(
            opName = "WRITE_SETTINGS",
            title = "Modify System Settings",
            description = "Allows modifying system settings and configuration flags",
            category = AppOpsCategory.SYSTEM,
            relatedPermissions = listOf("WRITE_SETTINGS")
        ),
        AppOpDescriptor(
            opName = "READ_PHONE_STATE",
            title = "Read Phone State",
            description = "Access telephony status, carrier state, and device call state",
            category = AppOpsCategory.SYSTEM,
            relatedPermissions = listOf("READ_PHONE_STATE")
        ),
        AppOpDescriptor(
            opName = "RUN_IN_BACKGROUND",
            title = "Run in Background",
            description = "Execute background services and scheduled work",
            category = AppOpsCategory.SYSTEM
        ),
        AppOpDescriptor(
            opName = "RUN_ANY_IN_BACKGROUND",
            title = "Run Any in Background",
            description = "Unrestricted background execution outside foreground",
            category = AppOpsCategory.SYSTEM
        )
    )

    private val packageHeaderRegex = Regex("""^\s*Package\s+([A-Za-z0-9_.\-]+)(?:\s|\(|:|$)""")
    private val opLineRegex = Regex("""^\s*(?:Uid mode:\s*)?([A-Z0-9_]+)\s*:\s*([a-zA-Z]+)\b""")

    fun isAdbAvailable(context: Context? = null): Boolean {
        return AdbShellExecutor.isPrivilegedShellReady(context)
    }

    fun activePrivilegeEngine(context: Context? = null): PrivilegeEngine {
        return AdbShellExecutor.resolveActiveEngine(context)
    }

    suspend fun executePrivilegedShell(
        command: String,
        context: Context? = null,
        timeoutMs: Long = 12_000L
    ): DualEngineShellResult {
        return AdbShellExecutor.executeWithEngine(
            command = command,
            timeoutMs = timeoutMs,
            context = context
        )
    }

    fun runShellCommand(command: String): String {
        if (!isAdbAvailable()) {
            return ""
        }
        val res = AdbShellExecutor.executeBlocking(command)
        return if (res.success) res.output.trim() else ""
    }

    internal fun normalizeOpName(rawName: String): String {
        val cleaned = rawName.trim().uppercase(Locale.ROOT)
        return if (cleaned.startsWith("OP_")) cleaned.removePrefix("OP_") else cleaned
    }

    internal fun normalizeOpMode(rawMode: String): String? {
        val lower = rawMode.trim().lowercase(Locale.ROOT)
        return when {
            lower == "allow" || lower == "allowed" || lower == "foreground" -> "allow"
            lower == "ignore" || lower == "ignored" -> "ignore"
            lower == "deny" || lower == "denied" || lower == "errored" -> "deny"
            lower == "default" -> "default"
            else -> null
        }
    }

    internal fun parseSinglePackageAppOps(output: String): Map<String, String> {
        if (output.isBlank()) return emptyMap()
        val parsed = linkedMapOf<String, String>()
        output.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.equals("No operations.", ignoreCase = true)) {
                return@forEach
            }
            val match = opLineRegex.find(trimmed) ?: return@forEach
            val op = normalizeOpName(match.groupValues[1])
            val mode = normalizeOpMode(match.groupValues[2]) ?: return@forEach
            if (op.isNotBlank() && op != "UID" && op != "PACKAGE") {
                parsed[op] = mode
            }
        }
        return parsed
    }

    internal fun parseBatchAppOpsDump(dumpOutput: String): Map<String, Map<String, String>> {
        if (dumpOutput.isBlank()) return emptyMap()
        val result = linkedMapOf<String, MutableMap<String, String>>()
        var currentPackage: String? = null

        dumpOutput.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach

            val pkgMatch = packageHeaderRegex.find(line)
            if (pkgMatch != null) {
                currentPackage = pkgMatch.groupValues[1].trim()
                if (!result.containsKey(currentPackage!!)) {
                    result[currentPackage!!] = linkedMapOf()
                }
                return@forEach
            }

            if (trimmed.startsWith("Uid ", ignoreCase = true) && !trimmed.startsWith("Uid mode:", ignoreCase = true)) {
                currentPackage = null
                return@forEach
            }

            val pkg = currentPackage ?: return@forEach
            val opMatch = opLineRegex.find(trimmed) ?: return@forEach
            val op = normalizeOpName(opMatch.groupValues[1])
            val mode = normalizeOpMode(opMatch.groupValues[2]) ?: return@forEach
            if (op.isNotBlank()) {
                result.getOrPut(pkg) { linkedMapOf() }[op] = mode
            }
        }
        return result
    }

    private fun isShellErrorOutput(output: String): Boolean {
        val lower = output.lowercase(Locale.ROOT)
        return lower.contains("securityexception") ||
            lower.contains("unknown operation") ||
            lower.contains("unknown package") ||
            lower.contains("no active privilege engine") ||
            lower.startsWith("error:") ||
            lower.contains("\nerror:")
    }

    suspend fun setAppOp(
        packageName: String,
        opName: String,
        mode: String,
        context: Context? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val safePackage = packageName.trim()
        val safeOp = normalizeOpName(opName)
        val safeMode = normalizeOpMode(mode)
        if (safePackage.isEmpty() || safeOp.isEmpty() || safeMode == null || safeMode !in VALID_MODES) {
            return@withContext false
        }

        if (!AdbShellExecutor.isPrivilegedShellReady(context)) {
            Log.w(TAG, "Refusing appops set for $safePackage/$safeOp: no active privilege engine")
            return@withContext false
        }

        val cmd = "appops set $safePackage $safeOp $safeMode"
        val setResult = executePrivilegedShell(cmd, context = context)
        if (!setResult.success || setResult.engineUsed == PrivilegeEngine.NONE || isShellErrorOutput(setResult.output)) {
            Log.w(TAG, "appops set failed ($cmd) via ${setResult.engineUsed}: ${setResult.output}")
            return@withContext false
        }

        val verifyResult = executePrivilegedShell("appops get $safePackage $safeOp", context = context)
        if (!verifyResult.success || verifyResult.engineUsed == PrivilegeEngine.NONE) {
            return@withContext false
        }

        val verifiedMap = parseSinglePackageAppOps(verifyResult.output)
        val verifiedMode = verifiedMap[safeOp]
        if (verifiedMode != null) {
            return@withContext verifiedMode.equals(safeMode, ignoreCase = true)
        }

        val lowerVerify = verifyResult.output.lowercase(Locale.ROOT)
        when {
            lowerVerify.contains("no operations") -> safeMode == "default"
            else -> !isShellErrorOutput(verifyResult.output)
        }
    }

    suspend fun getAppOp(
        packageName: String,
        opName: String,
        context: Context? = null
    ): String = withContext(Dispatchers.IO) {
        val safePackage = packageName.trim()
        val safeOp = normalizeOpName(opName)
        if (safePackage.isEmpty() || safeOp.isEmpty()) return@withContext "default"
        if (!AdbShellExecutor.isPrivilegedShellReady(context)) return@withContext "default"

        val result = executePrivilegedShell("appops get $safePackage $safeOp", context = context)
        if (!result.success || result.engineUsed == PrivilegeEngine.NONE) {
            return@withContext "default"
        }

        val parsed = parseSinglePackageAppOps(result.output)
        parsed[safeOp] ?: run {
            val lower = result.output.lowercase(Locale.ROOT)
            when {
                lower.contains("allow") -> "allow"
                lower.contains("ignore") -> "ignore"
                lower.contains("deny") || lower.contains("errored") -> "deny"
                else -> "default"
            }
        }
    }

    suspend fun queryOpsForPackage(
        packageName: String,
        context: Context? = null
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val defaults = managedOpsCatalog.associate { it.opName to "default" }.toMutableMap()
        val safePackage = packageName.trim()
        if (safePackage.isEmpty() || !AdbShellExecutor.isPrivilegedShellReady(context)) {
            return@withContext defaults
        }

        val result = executePrivilegedShell("appops get $safePackage", context = context, timeoutMs = 12_000L)
        if (result.success && result.engineUsed != PrivilegeEngine.NONE) {
            val parsed = parseSinglePackageAppOps(result.output)
            parsed.forEach { (op, mode) ->
                defaults[op] = mode
            }
        }
        defaults
    }

    suspend fun queryBatchAppOps(context: Context? = null): Map<String, Map<String, String>> = withContext(Dispatchers.IO) {
        if (!AdbShellExecutor.isPrivilegedShellReady(context)) {
            return@withContext emptyMap()
        }
        val dumpResult = executePrivilegedShell(
            command = "dumpsys appops",
            context = context,
            timeoutMs = 15_000L
        )
        if (dumpResult.success && dumpResult.output.isNotBlank()) {
            val parsed = parseBatchAppOpsDump(dumpResult.output)
            if (parsed.isNotEmpty()) {
                return@withContext parsed
            }
        }
        emptyMap()
    }

    suspend fun setStandbyBucket(
        packageName: String,
        bucket: StandbyBucketMode,
        context: Context? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val safePackage = packageName.trim()
        if (safePackage.isEmpty() || !AdbShellExecutor.isPrivilegedShellReady(context)) {
            return@withContext false
        }
        val primaryCmd = "am set-standby-bucket $safePackage ${bucket.shellArgument}"
        val res = executePrivilegedShell(primaryCmd, context = context)
        if (res.success && !isShellErrorOutput(res.output)) {
            return@withContext true
        }
        val fallbackCmd = "am set-standby-bucket $safePackage ${bucket.bucketValue}"
        val fallbackRes = executePrivilegedShell(fallbackCmd, context = context)
        fallbackRes.success && !isShellErrorOutput(fallbackRes.output)
    }

    suspend fun revokeAllSensitiveOps(
        packageName: String,
        context: Context? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!AdbShellExecutor.isPrivilegedShellReady(context)) {
            return@withContext false
        }
        val sensitiveOps = listOf(
            "CAMERA",
            "RECORD_AUDIO",
            "COARSE_LOCATION",
            "FINE_LOCATION",
            "SYSTEM_ALERT_WINDOW",
            "WRITE_SETTINGS",
            "READ_PHONE_STATE",
            "RUN_IN_BACKGROUND"
        )
        var allSuccess = true
        for (op in sensitiveOps) {
            val success = setAppOp(packageName, op, "deny", context)
            if (!success) allSuccess = false
        }
        allSuccess
    }

    suspend fun getInstalledApps(context: Context): List<AppOpsItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val usageStatsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        } else null

        val batchOpsByPackage = queryBatchAppOps(context)

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
        } catch (_: Exception) {
            emptyList()
        }

        rawPackages.mapNotNull { pkgInfo ->
            try {
                val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                val packageName = pkgInfo.packageName ?: return@mapNotNull null
                val appName = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    packageName
                }

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isDisabled = !appInfo.enabled || try {
                    val state = pm.getApplicationEnabledSetting(packageName)
                    state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                        state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                } catch (_: Exception) {
                    false
                }

                val launchIntent = try {
                    pm.getLaunchIntentForPackage(packageName)
                } catch (_: Exception) {
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
                var hasLocation = false
                var hasCamera = false
                var hasAudio = false
                var hasSystemOps = false

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
                            } catch (_: Exception) {
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
                            hasSystemOps = true
                        }
                        if (permName.equals(android.Manifest.permission.WRITE_SETTINGS, ignoreCase = true) ||
                            permName.equals(android.Manifest.permission.READ_PHONE_STATE, ignoreCase = true)
                        ) {
                            hasSystemOps = true
                        }
                        if (permName.equals("android.permission.ACCESS_BACKGROUND_LOCATION", ignoreCase = true)) {
                            hasBgLocation = true
                            hasLocation = true
                        }
                        if (permName.contains("LOCATION", ignoreCase = true)) {
                            hasLocation = true
                        }
                        if (permName.contains("CAMERA", ignoreCase = true)) {
                            hasCamera = true
                        }
                        if (permName.contains("RECORD_AUDIO", ignoreCase = true) ||
                            permName.contains("AUDIO", ignoreCase = true)
                        ) {
                            hasAudio = true
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

                val batchOps = batchOpsByPackage[packageName].orEmpty()
                val opsStates = linkedMapOf<String, String>()
                managedOpsCatalog.forEach { descriptor ->
                    opsStates[descriptor.opName] = batchOps[descriptor.opName] ?: "default"
                }
                batchOps.forEach { (op, mode) ->
                    if (!opsStates.containsKey(op)) {
                        opsStates[op] = mode
                    }
                }

                if (batchOps.keys.any { it.contains("LOCATION") }) hasLocation = true
                if (batchOps.containsKey("CAMERA")) hasCamera = true
                if (batchOps.keys.any { it.contains("AUDIO") }) hasAudio = true
                if (batchOps.keys.any { it in setOf("SYSTEM_ALERT_WINDOW", "WRITE_SETTINGS", "READ_PHONE_STATE", "RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND") }) {
                    hasSystemOps = true
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
                    } catch (_: Exception) {
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
                } catch (_: Exception) {
                    null
                }

                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }

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
                    hasLocationPermission = hasLocation,
                    hasCameraPermission = hasCamera,
                    hasAudioPermission = hasAudio,
                    hasSystemOpsPermission = hasSystemOps,
                    appOpsStates = opsStates,
                    icon = icon
                )
            } catch (_: Exception) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            openAppDetails(context, packageName)
        }
    }
}
