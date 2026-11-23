package com.arwora.feature.system.downloadworkflow.data

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.arwora.MainActivity
import com.arwora.R
import com.arwora.feature.system.downloadworkflow.model.DownloadFileCategory
import com.arwora.feature.system.downloadworkflow.model.DownloadWorkflowConfig
import com.arwora.feature.system.downloadworkflow.model.KnownDownloadSignature
import com.arwora.feature.system.downloadworkflow.model.ProcessedDownloadEvent
import com.arwora.feature.system.downloadworkflow.service.DownloadWorkflowService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates Smart Download Workflow toggles, background monitoring state,
 * non-blocking file processing, MD5 duplicate signature caching, and rich notifications.
 */
object DownloadWorkflowManager {

    private const val TAG = "DownloadWorkflowManager"
    private const val PREFS_NAME = "smart_download_workflow_prefs"
    private const val KEY_MASTER_ENABLED = "master_enabled"
    private const val KEY_CATEGORIZATION_ENABLED = "categorization_enabled"
    private const val KEY_AUTO_RENAME_ENABLED = "auto_rename_enabled"
    private const val KEY_DUPLICATE_DETECTION_ENABLED = "duplicate_detection_enabled"
    private const val KEY_INSTANT_NOTIFICATION_ENABLED = "instant_notification_enabled"
    private const val KEY_EVENTS_JSON = "processed_events_json"
    private const val KEY_SIGNATURES_JSON = "known_signatures_json"

    private const val MAX_EVENTS = 100
    private const val MAX_SIGNATURES = 250

    const val ALERT_CHANNEL_ID = "download_workflow_alerts_channel"
    const val MONITOR_CHANNEL_ID = "download_workflow_monitor_channel"

    const val EXTRA_OPEN_DOWNLOAD_WORKFLOW = "extra_open_download_workflow"
    const val EXTRA_WORKFLOW_ACTION = "extra_workflow_action"
    const val EXTRA_WORKFLOW_EVENT_ID = "extra_workflow_event_id"
    const val EXTRA_WORKFLOW_FOLDER_PATH = "extra_workflow_folder_path"

    const val ACTION_OPEN_FOLDER = "open_folder"
    const val ACTION_VIEW_DETAILS = "view_details"

    private val _config = MutableStateFlow(DownloadWorkflowConfig())
    val config: StateFlow<DownloadWorkflowConfig> = _config.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _processedEvents = MutableStateFlow<List<ProcessedDownloadEvent>>(emptyList())
    val processedEvents: StateFlow<List<ProcessedDownloadEvent>> = _processedEvents.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val initialized = AtomicBoolean(false)
    private val processingMutex = Mutex()
    private val inFlightPaths: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private var knownSignatures: MutableList<KnownDownloadSignature> = mutableListOf()
    private val notificationIdSeed = AtomicLong(6100L)

    fun resolveDownloadDirectory(): File {
        @Suppress("DEPRECATION")
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    fun hasStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Environment.isExternalStorageManager()
            } catch (_: Throwable) {
                false
            }
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    fun openStorageAccessSettings(context: Context): Boolean {
        val intents = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intents.add(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            )
            intents.add(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
        intents.add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        )
        for (intent in intents) {
            try {
                if (context !is android.app.Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Try fallback
            }
        }
        return false
    }

    fun openDownloadFolder(context: Context, folderPath: String? = null): Boolean {
        val candidates = mutableListOf<Intent>()
        candidates.add(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        candidates.add(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"),
                    "vnd.android.document/directory"
                )
            }
        )
        for (intent in candidates) {
            try {
                if (context !is android.app.Activity) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                // Try next
            }
        }
        return false
    }

    suspend fun initIfNeeded(context: Context) {
        if (initialized.get()) return
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            processingMutex.withLock {
                if (initialized.get()) return@withLock
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val loadedConfig = DownloadWorkflowConfig(
                    masterEnabled = prefs.getBoolean(KEY_MASTER_ENABLED, false),
                    categorizationEnabled = prefs.getBoolean(KEY_CATEGORIZATION_ENABLED, true),
                    autoRenameEnabled = prefs.getBoolean(KEY_AUTO_RENAME_ENABLED, true),
                    duplicateDetectionEnabled = prefs.getBoolean(KEY_DUPLICATE_DETECTION_ENABLED, true),
                    instantNotificationEnabled = prefs.getBoolean(KEY_INSTANT_NOTIFICATION_ENABLED, true)
                )
                _config.value = loadedConfig
                _processedEvents.value = loadEvents(prefs)
                knownSignatures = loadSignatures(prefs).toMutableList()
                initialized.set(true)
            }
        }
    }

    fun syncServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    suspend fun setMasterEnabled(context: Context, enabled: Boolean, startServiceIfEnabled: Boolean = true) {
        val appContext = context.applicationContext
        initIfNeeded(appContext)
        updateConfig(appContext) { it.copy(masterEnabled = enabled) }
        if (startServiceIfEnabled) {
            if (enabled) {
                DownloadWorkflowService.start(appContext)
            } else {
                DownloadWorkflowService.stop(appContext)
            }
        }
    }

    suspend fun setCategorizationEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        initIfNeeded(appContext)
        updateConfig(appContext) { it.copy(categorizationEnabled = enabled) }
    }

    suspend fun setAutoRenameEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        initIfNeeded(appContext)
        updateConfig(appContext) { it.copy(autoRenameEnabled = enabled) }
    }

    suspend fun setDuplicateDetectionEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        initIfNeeded(appContext)
        updateConfig(appContext) { it.copy(duplicateDetectionEnabled = enabled) }
    }

    suspend fun setInstantNotificationEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        initIfNeeded(appContext)
        updateConfig(appContext) { it.copy(instantNotificationEnabled = enabled) }
    }

    private suspend fun updateConfig(
        context: Context,
        transform: (DownloadWorkflowConfig) -> DownloadWorkflowConfig
    ) {
        val next = transform(_config.value)
        _config.value = next
        withContext(Dispatchers.IO) {
            saveConfig(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE), next)
        }
    }

    suspend fun handleCandidateFile(
        context: Context,
        file: File,
        downloadRoot: File = resolveDownloadDirectory(),
        stabilityDurationMs: Long = DownloadWorkflowEngine.DEFAULT_STABILITY_DURATION_MS,
        pollIntervalMs: Long = DownloadWorkflowEngine.DEFAULT_POLL_INTERVAL_MS,
        requireMasterEnabled: Boolean = true
    ): ProcessedDownloadEvent? {
        val appContext = context.applicationContext
        initIfNeeded(appContext)

        if (requireMasterEnabled && !_config.value.masterEnabled) return null
        if (DownloadWorkflowEngine.isPartialOrTemporaryFile(file.name)) return null

        val key = try {
            file.canonicalPath
        } catch (_: Exception) {
            file.absolutePath
        }
        if (!inFlightPaths.add(key)) {
            return null
        }

        return try {
            val isStable = DownloadWorkflowEngine.waitForStableFileSize(
                file = file,
                stabilityDurationMs = stabilityDurationMs,
                pollIntervalMs = pollIntervalMs
            )
            if (!isStable) return null

            val currentConfig = _config.value
            if (requireMasterEnabled && !currentConfig.masterEnabled) return null

            val outcome = withContext(Dispatchers.IO) {
                processingMutex.withLock {
                    // Avoid reprocessing a file that is already sitting at its recorded final path with the same size
                    val alreadySettled = _processedEvents.value.any {
                        it.finalPath == file.absolutePath && it.sizeBytes == file.length()
                    }
                    if (alreadySettled) {
                        return@withLock null
                    }
                    val result = DownloadWorkflowEngine.processCompletedFile(
                        file = file,
                        downloadRoot = downloadRoot,
                        config = currentConfig,
                        existingSignatures = knownSignatures
                    ) ?: return@withLock null

                    val nextEvents = (listOf(result.event) + _processedEvents.value).take(MAX_EVENTS)
                    _processedEvents.value = nextEvents

                    result.newSignature?.let { sig ->
                        knownSignatures.add(0, sig)
                        if (knownSignatures.size > MAX_SIGNATURES) {
                            knownSignatures = knownSignatures.take(MAX_SIGNATURES).toMutableList()
                        }
                    }

                    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    saveEvents(prefs, nextEvents)
                    saveSignatures(prefs, knownSignatures)
                    result
                }
            } ?: return null

            if (currentConfig.instantNotificationEnabled) {
                dispatchRichNotification(appContext, outcome.event)
            }
            outcome.event
        } finally {
            inFlightPaths.remove(key)
        }
    }

    suspend fun scanDownloadDirectoryNow(
        context: Context,
        downloadRoot: File = resolveDownloadDirectory(),
        stabilityDurationMs: Long = 0L,
        requireMasterEnabled: Boolean = false
    ): Int {
        val appContext = context.applicationContext
        initIfNeeded(appContext)
        _isScanning.value = true
        return try {
            withContext(Dispatchers.IO) {
                if (!downloadRoot.exists() || !downloadRoot.isDirectory) {
                    _statusMessage.value = "Download directory unavailable"
                    return@withContext 0
                }
                val topLevelFiles = downloadRoot.listFiles()
                    ?.filter { it.isFile && !DownloadWorkflowEngine.isPartialOrTemporaryFile(it.name) }
                    ?.sortedByDescending { it.lastModified() }
                    ?: emptyList()

                var processedCount = 0
                for (candidate in topLevelFiles) {
                    val result = handleCandidateFile(
                        context = appContext,
                        file = candidate,
                        downloadRoot = downloadRoot,
                        stabilityDurationMs = stabilityDurationMs,
                        pollIntervalMs = 50L,
                        requireMasterEnabled = requireMasterEnabled
                    )
                    if (result != null) processedCount++
                }
                _statusMessage.value = if (processedCount > 0) {
                    "Processed $processedCount completed download(s)"
                } else {
                    "No new unprocessed downloads found"
                }
                processedCount
            }
        } catch (e: Exception) {
            Log.e(TAG, "Scan download directory failed", e)
            _statusMessage.value = "Scan failed: ${e.localizedMessage ?: "Permission or I/O error"}"
            0
        } finally {
            _isScanning.value = false
        }
    }

    suspend fun deleteProcessedFile(context: Context, eventId: String): Boolean {
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            processingMutex.withLock {
                val target = _processedEvents.value.firstOrNull { it.id == eventId } ?: return@withLock false
                val file = File(target.finalPath)
                val deleted = !file.exists() || file.delete()
                if (deleted) {
                    val nextEvents = _processedEvents.value.filterNot { it.id == eventId }
                    _processedEvents.value = nextEvents
                    knownSignatures.removeAll { it.filePath == target.finalPath }
                    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    saveEvents(prefs, nextEvents)
                    saveSignatures(prefs, knownSignatures)
                }
                deleted
            }
        }
    }

    suspend fun clearWorkflowLogs(context: Context) {
        val appContext = context.applicationContext
        withContext(Dispatchers.IO) {
            processingMutex.withLock {
                _processedEvents.value = emptyList()
                knownSignatures.clear()
                val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                saveEvents(prefs, emptyList())
                saveSignatures(prefs, emptyList())
            }
        }
    }

    fun dispatchRichNotification(context: Context, event: ProcessedDownloadEvent) {
        val appContext = context.applicationContext
        if (!hasNotificationPermission(appContext)) return

        ensureChannels(appContext)
        val notifId = (notificationIdSeed.incrementAndGet() % Int.MAX_VALUE).toInt()

        val viewDetailsIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_DOWNLOAD_WORKFLOW, true)
            putExtra(EXTRA_WORKFLOW_ACTION, ACTION_VIEW_DETAILS)
            putExtra(EXTRA_WORKFLOW_EVENT_ID, event.id)
            putExtra("skip_splash", true)
        }
        val openFolderIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_DOWNLOAD_WORKFLOW, true)
            putExtra(EXTRA_WORKFLOW_ACTION, ACTION_OPEN_FOLDER)
            putExtra(EXTRA_WORKFLOW_EVENT_ID, event.id)
            putExtra(EXTRA_WORKFLOW_FOLDER_PATH, File(event.finalPath).parent ?: "")
            putExtra("skip_splash", true)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val detailsPendingIntent = PendingIntent.getActivity(appContext, notifId * 2, viewDetailsIntent, flags)
        val folderPendingIntent = PendingIntent.getActivity(appContext, notifId * 2 + 1, openFolderIntent, flags)

        val title = when {
            event.isDuplicate -> "Duplicate Download Flagged: ${event.finalName}"
            event.wasRenamed && event.wasMoved -> "Sorted & Renamed: ${event.finalName}"
            event.wasMoved -> "Sorted to ${event.category.folderName}: ${event.finalName}"
            event.wasRenamed -> "Renamed Download: ${event.finalName}"
            else -> "Download Processed: ${event.finalName}"
        }

        val detailsLines = buildList {
            add("Category: ${event.category.label} • Size: ${DownloadWorkflowEngine.formatBytes(event.sizeBytes)}")
            if (event.wasRenamed) add("Renamed from: ${event.originalName}")
            if (event.wasMoved) add("Folder: Download/${event.category.folderName}/")
            if (event.isDuplicate) add("Duplicate matches: ${event.duplicateOfName ?: "previous file"}")
        }.joinToString("\n")

        val notification = NotificationCompat.Builder(appContext, ALERT_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(detailsLines)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailsLines))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(detailsPendingIntent)
            .addAction(0, "Open Folder", folderPendingIntent)
            .addAction(0, "View Details", detailsPendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(notifId, notification)
        } catch (se: SecurityException) {
            Log.w(TAG, "Missing notification runtime permission", se)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch download workflow notification", e)
        }
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        ALERT_CHANNEL_ID,
                        "Smart Download Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "High-priority alerts when downloads are sorted, renamed, or flagged as duplicates."
                    }
                )
            }
            if (nm.getNotificationChannel(MONITOR_CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        MONITOR_CHANNEL_ID,
                        "Download Folder Monitor",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Background service status while watching the Downloads directory."
                    }
                )
            }
        }
    }

    private fun saveConfig(prefs: android.content.SharedPreferences, cfg: DownloadWorkflowConfig) {
        prefs.edit()
            .putBoolean(KEY_MASTER_ENABLED, cfg.masterEnabled)
            .putBoolean(KEY_CATEGORIZATION_ENABLED, cfg.categorizationEnabled)
            .putBoolean(KEY_AUTO_RENAME_ENABLED, cfg.autoRenameEnabled)
            .putBoolean(KEY_DUPLICATE_DETECTION_ENABLED, cfg.duplicateDetectionEnabled)
            .putBoolean(KEY_INSTANT_NOTIFICATION_ENABLED, cfg.instantNotificationEnabled)
            .apply()
    }

    private fun loadEvents(prefs: android.content.SharedPreferences): List<ProcessedDownloadEvent> {
        val raw = prefs.getString(KEY_EVENTS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<ProcessedDownloadEvent>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    ProcessedDownloadEvent(
                        id = obj.optString("id"),
                        originalName = obj.optString("originalName"),
                        finalName = obj.optString("finalName"),
                        originalPath = obj.optString("originalPath"),
                        finalPath = obj.optString("finalPath"),
                        category = DownloadFileCategory.fromId(obj.optString("category")),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        md5Hash = obj.optString("md5Hash"),
                        wasRenamed = obj.optBoolean("wasRenamed", false),
                        wasMoved = obj.optBoolean("wasMoved", false),
                        isDuplicate = obj.optBoolean("isDuplicate", false),
                        duplicateOfName = obj.optString("duplicateOfName").ifBlank { null },
                        processedAtMillis = obj.optLong("processedAtMillis", 0L)
                    )
                )
            }
            list.filter { it.id.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveEvents(prefs: android.content.SharedPreferences, list: List<ProcessedDownloadEvent>) {
        val arr = JSONArray()
        list.take(MAX_EVENTS).forEach { e ->
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("originalName", e.originalName)
                    put("finalName", e.finalName)
                    put("originalPath", e.originalPath)
                    put("finalPath", e.finalPath)
                    put("category", e.category.id)
                    put("sizeBytes", e.sizeBytes)
                    put("md5Hash", e.md5Hash)
                    put("wasRenamed", e.wasRenamed)
                    put("wasMoved", e.wasMoved)
                    put("isDuplicate", e.isDuplicate)
                    put("duplicateOfName", e.duplicateOfName.orEmpty())
                    put("processedAtMillis", e.processedAtMillis)
                }
            )
        }
        prefs.edit().putString(KEY_EVENTS_JSON, arr.toString()).apply()
    }

    private fun loadSignatures(prefs: android.content.SharedPreferences): List<KnownDownloadSignature> {
        val raw = prefs.getString(KEY_SIGNATURES_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<KnownDownloadSignature>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(
                    KnownDownloadSignature(
                        fileName = obj.optString("fileName"),
                        filePath = obj.optString("filePath"),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        md5Hash = obj.optString("md5Hash"),
                        recordedAtMillis = obj.optLong("recordedAtMillis", 0L)
                    )
                )
            }
            list.filter { it.md5Hash.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveSignatures(prefs: android.content.SharedPreferences, list: List<KnownDownloadSignature>) {
        val arr = JSONArray()
        list.take(MAX_SIGNATURES).forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("fileName", s.fileName)
                    put("filePath", s.filePath)
                    put("sizeBytes", s.sizeBytes)
                    put("md5Hash", s.md5Hash)
                    put("recordedAtMillis", s.recordedAtMillis)
                }
            )
        }
        prefs.edit().putString(KEY_SIGNATURES_JSON, arr.toString()).apply()
    }

    fun clearForTests(context: Context) {
        initialized.set(false)
        _config.value = DownloadWorkflowConfig()
        _isServiceRunning.value = false
        _processedEvents.value = emptyList()
        _statusMessage.value = null
        knownSignatures.clear()
        inFlightPaths.clear()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
