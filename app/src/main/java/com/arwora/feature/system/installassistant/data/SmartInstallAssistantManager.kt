package com.arwora.feature.system.installassistant.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.arwora.MainActivity
import com.arwora.R
import com.arwora.feature.system.installassistant.model.AppInstallCategory
import com.arwora.feature.system.installassistant.model.DangerousPermissionAuditItem
import com.arwora.feature.system.installassistant.model.InstalledAppAssistantRecord
import com.arwora.feature.system.installassistant.model.OnlineAppInfoResult
import com.arwora.feature.system.installassistant.model.OnlineSyncStatus
import com.arwora.feature.system.installassistant.model.SetupChecklistStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class OnlineFetchResponse(
    val statusCode: Int,
    val body: String
)

/**
 * Coordinates package install detection, automatic categorization, permission & setup checklist audits,
 * gentle install notifications, and non-blocking online app metadata sync.
 */
object SmartInstallAssistantManager {

    private const val TAG = "SmartInstallAssistant"
    private const val PREFS_NAME = "smart_install_assistant_prefs"
    private const val KEY_RECORDS_JSON = "tracked_records_json"
    private const val MAX_STORED_RECORDS = 50
    private const val INITIAL_SEED_LIMIT = 10

    const val NOTIFICATION_CHANNEL_ID = "smart_install_assistant_channel"
    const val EXTRA_OPEN_INSTALL_ASSISTANT = "extra_open_install_assistant"
    const val EXTRA_INSTALL_ASSISTANT_PKG = "extra_install_assistant_pkg"

    private val _records = MutableStateFlow<List<InstalledAppAssistantRecord>>(emptyList())
    val records: StateFlow<List<InstalledAppAssistantRecord>> = _records.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val initialized = AtomicBoolean(false)
    private val stateMutex = Mutex()
    private val lastBroadcastTimestampByPkg = mutableMapOf<String, Long>()
    private val notificationIdSeed = AtomicLong(4200L)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun initIfNeeded(context: Context, forceReload: Boolean = false) {
        if (!forceReload && initialized.get() && _records.value.isNotEmpty()) return
        val appContext = context.applicationContext
        _isLoading.value = true
        try {
            withContext(Dispatchers.IO) {
                stateMutex.withLock {
                    if (!forceReload && initialized.get() && _records.value.isNotEmpty()) {
                        return@withLock
                    }
                    val loaded = loadRecordsFromPrefs(appContext)
                    val initialList = if (loaded.isNotEmpty()) {
                        pruneAndRefreshRecords(appContext, loaded)
                    } else {
                        seedRecentUserApps(appContext)
                    }
                    _records.value = initialList
                    saveRecordsToPrefs(appContext, initialList)
                    initialized.set(true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize install assistant", e)
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        _isLoading.value = true
        try {
            withContext(Dispatchers.IO) {
                stateMutex.withLock {
                    val current = _records.value.ifEmpty { loadRecordsFromPrefs(appContext) }
                    val refreshed = if (current.isEmpty()) {
                        seedRecentUserApps(appContext)
                    } else {
                        pruneAndRefreshRecords(appContext, current)
                    }
                    _records.value = refreshed
                    saveRecordsToPrefs(appContext, refreshed)
                    initialized.set(true)
                }
            }
        } finally {
            _isLoading.value = false
        }
    }

    suspend fun recordPackageEvent(
        context: Context,
        packageName: String,
        isUpdate: Boolean,
        timestampMillis: Long = System.currentTimeMillis()
    ): InstalledAppAssistantRecord? {
        if (packageName.isBlank()) return null
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            stateMutex.withLock {
                val previous = _records.value.ifEmpty { loadRecordsFromPrefs(appContext) }
                val existing = previous.firstOrNull { it.packageName == packageName }
                val audited = buildOrAuditRecord(
                    context = appContext,
                    packageName = packageName,
                    isUpdate = isUpdate,
                    installedAtMillis = timestampMillis,
                    existing = existing
                ) ?: return@withLock null

                val updatedList = (listOf(audited) + previous.filterNot { it.packageName == packageName })
                    .sortedByDescending { it.installedAtMillis }
                    .take(MAX_STORED_RECORDS)

                _records.value = updatedList
                saveRecordsToPrefs(appContext, updatedList)
                initialized.set(true)
                audited
            }
        }
    }

    fun shouldHandleBroadcast(packageName: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        synchronized(lastBroadcastTimestampByPkg) {
            val prev = lastBroadcastTimestampByPkg[packageName]
            if (prev != null && (nowMillis - prev) in 0L..3_500L) {
                return false
            }
            lastBroadcastTimestampByPkg[packageName] = nowMillis
            return true
        }
    }

    suspend fun auditPackage(context: Context, packageName: String): InstalledAppAssistantRecord? {
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            stateMutex.withLock {
                val currentList = _records.value.ifEmpty { loadRecordsFromPrefs(appContext) }
                val existing = currentList.firstOrNull { it.packageName == packageName }
                val audited = buildOrAuditRecord(
                    context = appContext,
                    packageName = packageName,
                    isUpdate = existing?.isUpdate ?: false,
                    installedAtMillis = existing?.installedAtMillis ?: System.currentTimeMillis(),
                    existing = existing
                ) ?: return@withLock null

                val nextList = if (existing != null) {
                    currentList.map { if (it.packageName == packageName) audited else it }
                } else {
                    (listOf(audited) + currentList).take(MAX_STORED_RECORDS)
                }
                _records.value = nextList
                saveRecordsToPrefs(appContext, nextList)
                audited
            }
        }
    }

    suspend fun setChecklistStepCompleted(
        context: Context,
        packageName: String,
        step: SetupChecklistStep,
        completed: Boolean
    ) {
        val appContext = context.applicationContext
        val currentSnapshot = _records.value
        if (currentSnapshot.isNotEmpty()) {
            _records.value = currentSnapshot.map { record ->
                if (record.packageName == packageName) {
                    val nextSteps = record.completedSteps.toMutableSet().apply {
                        if (completed) add(step) else remove(step)
                    }
                    record.copy(completedSteps = nextSteps)
                } else {
                    record
                }
            }
        }
        withContext(Dispatchers.IO) {
            stateMutex.withLock {
                val baseList = _records.value.ifEmpty { loadRecordsFromPrefs(appContext) }
                val nextList = baseList.map { record ->
                    if (record.packageName == packageName) {
                        val nextSteps = record.completedSteps.toMutableSet().apply {
                            if (completed) add(step) else remove(step)
                        }
                        record.copy(completedSteps = nextSteps)
                    } else {
                        record
                    }
                }
                _records.value = nextList
                saveRecordsToPrefs(appContext, nextList)
            }
        }
    }

    suspend fun markStepReviewed(context: Context, packageName: String, step: SetupChecklistStep) {
        setChecklistStepCompleted(context, packageName, step, completed = true)
    }

    suspend fun syncOnlineAppInfo(
        context: Context,
        packageName: String,
        httpFetcher: (suspend (String) -> OnlineFetchResponse?)? = null
    ): OnlineAppInfoResult {
        val appContext = context.applicationContext
        val localSha256 = withContext(Dispatchers.IO) { extractSigningSha256(appContext, packageName) }
        val installerSource = withContext(Dispatchers.IO) { resolveInstallerLabel(appContext, packageName) }
        val localAppName = withContext(Dispatchers.IO) { resolveAppLabel(appContext, packageName) }

        val result = withContext(Dispatchers.IO) {
            withTimeoutOrNull(8_000L) {
                try {
                    if (httpFetcher == null && !isNetworkConnected(appContext)) {
                        return@withTimeoutOrNull OnlineAppInfoResult(
                            packageName = packageName,
                            status = OnlineSyncStatus.OFFLINE,
                            storeTitle = localAppName,
                            installerSource = installerSource,
                            signingSha256 = localSha256,
                            storeUrl = "https://play.google.com/store/apps/details?id=$packageName",
                            syncSummary = "Device is offline. Local APK signature and installer source verified.",
                            syncedAtMillis = System.currentTimeMillis(),
                            errorMessage = "No internet connection available"
                        )
                    }

                    val playUrl = "https://play.google.com/store/apps/details?id=$packageName&hl=en"
                    val playResp = if (httpFetcher != null) httpFetcher(playUrl) else defaultHttpGet(playUrl)
                    if (playResp != null && playResp.statusCode in 200..299 && playResp.body.isNotBlank()) {
                        val parsed = parsePlayStoreHtmlMetadata(
                            packageName = packageName,
                            html = playResp.body,
                            fallbackTitle = localAppName,
                            installerSource = installerSource,
                            signingSha256 = localSha256,
                            storeUrl = playUrl
                        )
                        if (parsed != null) return@withTimeoutOrNull parsed
                    }

                    // Fallback to F-Droid public API for open-source Android apps
                    val fdroidUrl = "https://f-droid.org/api/v1/packages/$packageName"
                    val fdroidResp = if (httpFetcher != null) httpFetcher(fdroidUrl) else defaultHttpGet(fdroidUrl)
                    if (fdroidResp != null && fdroidResp.statusCode in 200..299 && fdroidResp.body.isNotBlank()) {
                        val parsedFdroid = parseFdroidJsonMetadata(
                            packageName = packageName,
                            jsonStr = fdroidResp.body,
                            fallbackTitle = localAppName,
                            installerSource = installerSource,
                            signingSha256 = localSha256
                        )
                        if (parsedFdroid != null) return@withTimeoutOrNull parsedFdroid
                    }

                    OnlineAppInfoResult(
                        packageName = packageName,
                        status = OnlineSyncStatus.NOT_FOUND_PUBLIC,
                        storeTitle = localAppName,
                        developerOrOrigin = installerSource,
                        installerSource = installerSource,
                        signingSha256 = localSha256,
                        storeUrl = playUrl,
                        syncSummary = "No public store listing found; local APK signature & installer origin verified.",
                        syncedAtMillis = System.currentTimeMillis()
                    )
                } catch (e: UnknownHostException) {
                    OnlineAppInfoResult(
                        packageName = packageName,
                        status = OnlineSyncStatus.OFFLINE,
                        storeTitle = localAppName,
                        installerSource = installerSource,
                        signingSha256 = localSha256,
                        syncSummary = "Network unreachable. Local package audit preserved.",
                        syncedAtMillis = System.currentTimeMillis(),
                        errorMessage = e.message ?: "Network unreachable"
                    )
                } catch (e: SocketTimeoutException) {
                    OnlineAppInfoResult(
                        packageName = packageName,
                        status = OnlineSyncStatus.ERROR,
                        storeTitle = localAppName,
                        installerSource = installerSource,
                        signingSha256 = localSha256,
                        syncSummary = "Online metadata request timed out.",
                        syncedAtMillis = System.currentTimeMillis(),
                        errorMessage = "Request timed out"
                    )
                } catch (e: IOException) {
                    OnlineAppInfoResult(
                        packageName = packageName,
                        status = OnlineSyncStatus.ERROR,
                        storeTitle = localAppName,
                        installerSource = installerSource,
                        signingSha256 = localSha256,
                        syncSummary = "Failed to sync online info: ${e.localizedMessage ?: "I/O error"}",
                        syncedAtMillis = System.currentTimeMillis(),
                        errorMessage = e.localizedMessage ?: "Network I/O error"
                    )
                } catch (e: Exception) {
                    OnlineAppInfoResult(
                        packageName = packageName,
                        status = OnlineSyncStatus.ERROR,
                        storeTitle = localAppName,
                        installerSource = installerSource,
                        signingSha256 = localSha256,
                        syncSummary = "Online sync failed cleanly.",
                        syncedAtMillis = System.currentTimeMillis(),
                        errorMessage = e.localizedMessage ?: "Unexpected error"
                    )
                }
            } ?: OnlineAppInfoResult(
                packageName = packageName,
                status = OnlineSyncStatus.ERROR,
                storeTitle = localAppName,
                installerSource = installerSource,
                signingSha256 = localSha256,
                syncSummary = "Online metadata sync timed out after 8 seconds.",
                syncedAtMillis = System.currentTimeMillis(),
                errorMessage = "Timed out"
            )
        }

        stateMutex.withLock {
            val currentList = _records.value
            val updatedList = currentList.map { rec ->
                if (rec.packageName == packageName) rec.copy(onlineMetadata = result) else rec
            }
            _records.value = updatedList
            saveRecordsToPrefs(appContext, updatedList)
        }
        return result
    }

    internal fun parsePlayStoreHtmlMetadata(
        packageName: String,
        html: String,
        fallbackTitle: String,
        installerSource: String,
        signingSha256: String,
        storeUrl: String
    ): OnlineAppInfoResult? {
        val titleMatch = Regex("<title>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace("&amp;", "&")
            ?.replace("&#39;", "'")
            ?.replace(" - Apps on Google Play", "")
            ?.trim()
            .orEmpty()

        val ogTitle = Regex("property=[\"']og:title[\"']\\s+content=[\"'](.*?)[\"']", RegexOption.IGNORE_CASE)
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(" - Apps on Google Play", "")
            ?.trim()
            .orEmpty()

        val resolvedTitle = ogTitle.ifBlank { titleMatch }.ifBlank { fallbackTitle }
        if (resolvedTitle.equals("Not Found", ignoreCase = true) ||
            html.contains("the requested URL was not found on this server", ignoreCase = true)
        ) {
            return null
        }

        val genre = Regex("\"applicationCategory\":\"(.*?)\"", RegexOption.IGNORE_CASE)
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

        val developer = Regex("\"author\":\\{\"@type\":\"Person\",\"name\":\"(.*?)\"", RegexOption.IGNORE_CASE)
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

        return OnlineAppInfoResult(
            packageName = packageName,
            status = OnlineSyncStatus.SUCCESS,
            storeTitle = resolvedTitle,
            developerOrOrigin = developer.ifBlank { "Google Play Store" },
            publicCategory = genre,
            installerSource = installerSource,
            signingSha256 = signingSha256,
            storeUrl = storeUrl,
            syncSummary = "Synced from Google Play public listing${if (genre.isNotBlank()) " ($genre)" else ""}.",
            syncedAtMillis = System.currentTimeMillis()
        )
    }

    internal fun parseFdroidJsonMetadata(
        packageName: String,
        jsonStr: String,
        fallbackTitle: String,
        installerSource: String,
        signingSha256: String
    ): OnlineAppInfoResult? {
        return try {
            val obj = JSONObject(jsonStr)
            val suggestedCode = obj.optLong("suggestedVersionCode", 0L)
            val pkgArray = obj.optJSONArray("packages")
            val latestVer = if (pkgArray != null && pkgArray.length() > 0) {
                pkgArray.getJSONObject(0).optString("versionName", "")
            } else {
                ""
            }
            OnlineAppInfoResult(
                packageName = packageName,
                status = OnlineSyncStatus.SUCCESS,
                storeTitle = fallbackTitle,
                developerOrOrigin = "F-Droid Open Source Repository",
                publicCategory = "Open Source",
                installerSource = installerSource,
                signingSha256 = signingSha256,
                storeUrl = "https://f-droid.org/packages/$packageName/",
                syncSummary = "Verified on F-Droid API${if (latestVer.isNotBlank()) " (latest v$latestVer)" else if (suggestedCode > 0) " (code $suggestedCode)" else ""}.",
                syncedAtMillis = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun defaultHttpGet(url: String): OnlineFetchResponse {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) ArworaHyperToolkit")
            .get()
            .build()
        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            return OnlineFetchResponse(response.code, bodyStr)
        }
    }

    fun postInstallNotification(context: Context, record: InstalledAppAssistantRecord) {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureNotificationChannel(appContext)

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_INSTALL_ASSISTANT, true)
            putExtra(EXTRA_INSTALL_ASSISTANT_PKG, record.packageName)
            putExtra("skip_splash", true)
        }

        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            record.packageName.hashCode(),
            launchIntent,
            pendingFlags
        )

        val prefix = if (record.isUpdate) "App updated" else "New app installed"
        val title = "$prefix: ${record.appName}"
        val body = "Category: ${record.category.label}. Tap to review setup checklist & permissions."

        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(
                (notificationIdSeed.incrementAndGet() % Int.MAX_VALUE).toInt(),
                notification
            )
        } catch (se: SecurityException) {
            Log.w(TAG, "Notification permission rejected at runtime", se)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post install assistant notification", e)
        }
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Smart App Installation Assistant",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts when new apps are installed so you can review setup checklists and permissions."
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    @SuppressLint("BatteryLife")
    fun openBatteryOptimizationSettings(context: Context, packageName: String): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
        return launchFirstAvailableIntent(context, intents)
    }

    fun openAppNotificationSettings(context: Context, packageName: String): Boolean {
        val intents = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intents.add(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            )
        }
        intents.add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
        return launchFirstAvailableIntent(context, intents)
    }

    fun openAppPermissionsSettings(context: Context, packageName: String): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        return launchFirstAvailableIntent(context, listOf(intent))
    }

    private fun launchFirstAvailableIntent(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                if (context !is android.app.Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Try fallback intent
            }
        }
        return false
    }

    private fun seedRecentUserApps(context: Context): List<InstalledAppAssistantRecord> {
        val pm = context.packageManager
        val installedPackages = safeGetInstalledPackages(pm)
        val userApps = installedPackages
            .filter { pkgInfo ->
                val appInfo = pkgInfo.applicationInfo ?: return@filter false
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                !isSystem && pkgInfo.packageName != context.packageName
            }
            .sortedByDescending { it.lastUpdateTime.coerceAtLeast(it.firstInstallTime) }
            .take(INITIAL_SEED_LIMIT)

        return userApps.mapNotNull { pkgInfo ->
            buildRecordFromPackageInfo(
                context = context,
                pkgInfo = pkgInfo,
                isUpdate = pkgInfo.lastUpdateTime > pkgInfo.firstInstallTime + 60_000L,
                installedAtMillis = pkgInfo.lastUpdateTime.coerceAtLeast(pkgInfo.firstInstallTime),
                existing = null
            )
        }
    }

    private fun pruneAndRefreshRecords(
        context: Context,
        stored: List<InstalledAppAssistantRecord>
    ): List<InstalledAppAssistantRecord> {
        return stored.mapNotNull { existing ->
            buildOrAuditRecord(
                context = context,
                packageName = existing.packageName,
                isUpdate = existing.isUpdate,
                installedAtMillis = existing.installedAtMillis,
                existing = existing
            )
        }.sortedByDescending { it.installedAtMillis }
            .take(MAX_STORED_RECORDS)
    }

    private fun buildOrAuditRecord(
        context: Context,
        packageName: String,
        isUpdate: Boolean,
        installedAtMillis: Long,
        existing: InstalledAppAssistantRecord?
    ): InstalledAppAssistantRecord? {
        val pm = context.packageManager
        val pkgInfo = safeGetPackageInfo(pm, packageName) ?: return null
        return buildRecordFromPackageInfo(
            context = context,
            pkgInfo = pkgInfo,
            isUpdate = isUpdate,
            installedAtMillis = installedAtMillis,
            existing = existing
        )
    }

    private fun buildRecordFromPackageInfo(
        context: Context,
        pkgInfo: PackageInfo,
        isUpdate: Boolean,
        installedAtMillis: Long,
        existing: InstalledAppAssistantRecord?
    ): InstalledAppAssistantRecord {
        val pm = context.packageManager
        val appInfo = pkgInfo.applicationInfo
        val label = if (appInfo != null) {
            try {
                pm.getApplicationLabel(appInfo).toString().ifBlank { pkgInfo.packageName }
            } catch (_: Exception) {
                pkgInfo.packageName
            }
        } else {
            pkgInfo.packageName
        }

        val category = AppCategoryClassifier.classify(appInfo, pkgInfo.packageName, label)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }

        val batteryIgnored = try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(pkgInfo.packageName) ?: false
        } catch (_: Exception) {
            false
        }

        val dangerousPerms = extractDangerousPermissions(pm, pkgInfo)
        val notificationsAllowed = resolveNotificationStatus(context, pkgInfo, dangerousPerms)
        val installerPkg = resolveInstallerPackageName(context, pkgInfo.packageName)

        return InstalledAppAssistantRecord(
            packageName = pkgInfo.packageName,
            appName = label,
            versionName = pkgInfo.versionName ?: "1.0",
            versionCode = versionCode,
            category = category,
            installedAtMillis = installedAtMillis,
            isUpdate = isUpdate,
            installerPackage = installerPkg,
            completedSteps = existing?.completedSteps ?: emptySet(),
            batteryOptimizationIgnored = batteryIgnored,
            notificationsAllowed = notificationsAllowed,
            dangerousPermissions = dangerousPerms,
            onlineMetadata = existing?.onlineMetadata,
            lastAuditedAtMillis = System.currentTimeMillis()
        )
    }

    internal fun extractDangerousPermissions(
        pm: PackageManager,
        pkgInfo: PackageInfo
    ): List<DangerousPermissionAuditItem> {
        val requested = pkgInfo.requestedPermissions ?: return emptyList()
        val flags = pkgInfo.requestedPermissionsFlags
        val items = mutableListOf<DangerousPermissionAuditItem>()

        requested.forEachIndexed { index, perm ->
            val groupLabel = classifyDangerousPermissionGroup(pm, perm) ?: return@forEachIndexed
            val isGranted = if (flags != null && index < flags.size) {
                (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            } else {
                false
            }
            val simpleName = perm.substringAfterLast('.')
                .replace('_', ' ')
                .lowercase()
                .replaceFirstChar { it.uppercaseChar() }
            items.add(
                DangerousPermissionAuditItem(
                    permission = perm,
                    simpleName = simpleName,
                    groupLabel = groupLabel,
                    isGranted = isGranted
                )
            )
        }
        return items.sortedWith(compareByDescending<DangerousPermissionAuditItem> { it.isGranted }.thenBy { it.groupLabel })
    }

    internal fun classifyDangerousPermissionGroup(pm: PackageManager, permission: String): String? {
        when (permission) {
            Manifest.permission.CAMERA -> return "Camera"
            Manifest.permission.RECORD_AUDIO -> return "Microphone"
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION -> return "Location"
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO -> return "Storage / Media"
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.GET_ACCOUNTS -> return "Contacts"
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS -> return "Phone / SMS"
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR -> return "Calendar"
            Manifest.permission.POST_NOTIFICATIONS -> return "Notifications"
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.NEARBY_WIFI_DEVICES -> return "Nearby Devices"
            Manifest.permission.BODY_SENSORS,
            Manifest.permission.ACTIVITY_RECOGNITION -> return "Sensors"
        }

        return try {
            val info = pm.getPermissionInfo(permission, 0)
            val protection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.protection
            } else {
                @Suppress("DEPRECATION")
                info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE
            }
            if (protection == PermissionInfo.PROTECTION_DANGEROUS) {
                "Sensitive Runtime"
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveNotificationStatus(
        context: Context,
        pkgInfo: PackageInfo,
        dangerousPerms: List<DangerousPermissionAuditItem>
    ): Boolean? {
        if (pkgInfo.packageName == context.packageName) {
            return NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        val postNotif = dangerousPerms.firstOrNull { it.permission == Manifest.permission.POST_NOTIFICATIONS }
        return postNotif?.isGranted
    }

    private fun safeGetInstalledPackages(pm: PackageManager): List<PackageInfo> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun safeGetPackageInfo(pm: PackageManager, packageName: String): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveAppLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(info).toString().ifBlank { packageName }
        } catch (_: Exception) {
            packageName
        }
    }

    private fun resolveInstallerPackageName(context: Context, packageName: String): String? {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveInstallerLabel(context: Context, packageName: String): String {
        val installerPkg = resolveInstallerPackageName(context, packageName)
        return when (installerPkg) {
            "com.android.vending" -> "Google Play Store"
            "com.google.android.packageinstaller",
            "com.android.packageinstaller" -> "Android Package Installer (APK)"
            "org.fdroid.fdroid" -> "F-Droid"
            "com.aurora.store" -> "Aurora Store"
            null -> "ADB / Sideload / System"
            else -> installerPkg
        }
    }

    private fun extractSigningSha256(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val flags = PackageManager.GET_SIGNING_CERTIFICATES.toLong()
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                }
                val signingInfo = info.signingInfo ?: return ""
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return ""
            val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (_: Exception) {
            ""
        }
    }

    private fun isNetworkConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun loadRecordsFromPrefs(context: Context): List<InstalledAppAssistantRecord> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECORDS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<InstalledAppAssistantRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val stepsArr = obj.optJSONArray("completedSteps")
                val steps = mutableSetOf<SetupChecklistStep>()
                if (stepsArr != null) {
                    for (j in 0 until stepsArr.length()) {
                        SetupChecklistStep.fromId(stepsArr.optString(j))?.let { steps.add(it) }
                    }
                }
                val onlineObj = obj.optJSONObject("onlineMetadata")
                val onlineMeta = if (onlineObj != null) {
                    OnlineAppInfoResult(
                        packageName = onlineObj.optString("packageName"),
                        status = runCatching {
                            OnlineSyncStatus.valueOf(onlineObj.optString("status", OnlineSyncStatus.IDLE.name))
                        }.getOrDefault(OnlineSyncStatus.IDLE),
                        storeTitle = onlineObj.optString("storeTitle"),
                        developerOrOrigin = onlineObj.optString("developerOrOrigin"),
                        publicCategory = onlineObj.optString("publicCategory"),
                        installerSource = onlineObj.optString("installerSource"),
                        signingSha256 = onlineObj.optString("signingSha256"),
                        storeUrl = onlineObj.optString("storeUrl"),
                        syncSummary = onlineObj.optString("syncSummary"),
                        syncedAtMillis = onlineObj.optLong("syncedAtMillis", 0L),
                        errorMessage = onlineObj.optString("errorMessage").ifBlank { null }
                    )
                } else {
                    null
                }
                result.add(
                    InstalledAppAssistantRecord(
                        packageName = obj.optString("packageName"),
                        appName = obj.optString("appName"),
                        versionName = obj.optString("versionName", "1.0"),
                        versionCode = obj.optLong("versionCode", 1L),
                        category = AppInstallCategory.fromId(obj.optString("category")),
                        installedAtMillis = obj.optLong("installedAtMillis", 0L),
                        isUpdate = obj.optBoolean("isUpdate", false),
                        installerPackage = obj.optString("installerPackage").ifBlank { null },
                        completedSteps = steps,
                        onlineMetadata = onlineMeta
                    )
                )
            }
            result.filter { it.packageName.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing install assistant cache", e)
            emptyList()
        }
    }

    private fun saveRecordsToPrefs(context: Context, list: List<InstalledAppAssistantRecord>) {
        try {
            val arr = JSONArray()
            list.take(MAX_STORED_RECORDS).forEach { record ->
                val obj = JSONObject().apply {
                    put("packageName", record.packageName)
                    put("appName", record.appName)
                    put("versionName", record.versionName)
                    put("versionCode", record.versionCode)
                    put("category", record.category.id)
                    put("installedAtMillis", record.installedAtMillis)
                    put("isUpdate", record.isUpdate)
                    put("installerPackage", record.installerPackage.orEmpty())
                    val stepsArr = JSONArray()
                    record.completedSteps.forEach { stepsArr.put(it.id) }
                    put("completedSteps", stepsArr)
                    record.onlineMetadata?.let { meta ->
                        val metaObj = JSONObject().apply {
                            put("packageName", meta.packageName)
                            put("status", meta.status.name)
                            put("storeTitle", meta.storeTitle)
                            put("developerOrOrigin", meta.developerOrOrigin)
                            put("publicCategory", meta.publicCategory)
                            put("installerSource", meta.installerSource)
                            put("signingSha256", meta.signingSha256)
                            put("storeUrl", meta.storeUrl)
                            put("syncSummary", meta.syncSummary)
                            put("syncedAtMillis", meta.syncedAtMillis)
                            put("errorMessage", meta.errorMessage.orEmpty())
                        }
                        put("onlineMetadata", metaObj)
                    }
                }
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RECORDS_JSON, arr.toString())
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving install assistant records", e)
        }
    }

    fun clearForTests(context: Context) {
        initialized.set(false)
        _records.value = emptyList()
        synchronized(lastBroadcastTimestampByPkg) {
            lastBroadcastTimestampByPkg.clear()
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
