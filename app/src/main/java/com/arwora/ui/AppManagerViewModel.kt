package com.arwora.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arwora.data.db.AppHistoryDao
import com.arwora.data.db.AppHistoryEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class AppItem(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val firstInstallTime: Long,
    val firstInstallTimeFormatted: String,
    val dayFormattedDate: String,
    val relativeTimeAgo: String,
    val isSystemApp: Boolean,
    val icon: android.graphics.drawable.Drawable?,
    val appInfo: ApplicationInfo? = null,
    val targetSdk: Int = 0,
    val apkSizeBytes: Long = 0L,
    val apkSizeFormatted: String = ""
)

class AppManagerViewModel(
    private val context: Context,
    private val appHistoryDao: AppHistoryDao
) : ViewModel() {

    private val _installedApps = MutableStateFlow<List<AppItem>>(emptyList())
    val installedApps: StateFlow<List<AppItem>> = _installedApps.asStateFlow()

    private val _extractedApks = MutableStateFlow<List<com.arwora.utils.ExtractedApkItem>>(emptyList())
    val extractedApks: StateFlow<List<com.arwora.utils.ExtractedApkItem>> = _extractedApks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    val historyList: StateFlow<List<AppHistoryEntity>> = appHistoryDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow(AppFilterType.ALL)
    val filterType: StateFlow<AppFilterType> = _filterType.asStateFlow()

    enum class AppFilterType { ALL, USER, SYSTEM, EXTRACTED }

    init {
        memoryCachedApps?.let {
            _installedApps.value = it
        }
        loadExtractedApks()
    }

    fun onVisible() {
        loadInstalledApps()
        loadExtractedApks()
    }

    fun loadExtractedApks() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _extractedApks.value = com.arwora.utils.ApkExtractorUtils.getExtractedApks(context)
        }
    }

    fun extractSingleApk(
        context: Context,
        app: AppItem,
        onComplete: (com.arwora.utils.ExtractionResult) -> Unit
    ) {
        viewModelScope.launch {
            _isExtracting.value = true
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val appInfo = app.appInfo ?: try {
                    context.packageManager.getApplicationInfo(app.packageName, 0)
                } catch (e: Exception) {
                    null
                }

                if (appInfo == null) {
                    com.arwora.utils.ExtractionResult.Error("Could not retrieve application info")
                } else {
                    com.arwora.utils.ApkExtractorUtils.extractApk(
                        context = context,
                        appInfo = appInfo,
                        appName = app.appName,
                        versionName = app.versionName
                    )
                }
            }
            _isExtracting.value = false
            loadExtractedApks()
            onComplete(result)
        }
    }

    fun loadInstalledApps(forceRefresh: Boolean = false) {
        if (!forceRefresh) {
            if (_installedApps.value.isNotEmpty()) return
            memoryCachedApps?.let {
                _installedApps.value = it
                return
            }
        }

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _isLoading.value = true
            try {
                val pm = context.packageManager
                val packages = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        pm.getInstalledPackages(android.content.pm.PackageManager.PackageInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstalledPackages(0)
                    }
                } ?: emptyList()
                val standardDateFormat = SimpleDateFormat("dd/MM/yyyy - HH:mm:ss", Locale.getDefault())
                val dayMonthFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
                val now = System.currentTimeMillis()

                val list = packages.mapNotNull { pkgInfo ->
                    try {
                        val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        val installTime = pkgInfo.firstInstallTime
                        val dateObj = Date(installTime)

                        val formattedDateTime = standardDateFormat.format(dateObj)
                        val dayDateString = "Day: ${dayMonthFormat.format(dateObj)}"
                        val relativeAgo = calculateRelativeTime(installTime, now)
                        val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }

                        val sizeBytes = try {
                            java.io.File(appInfo.sourceDir ?: "").length()
                        } catch (_: Exception) {
                            0L
                        }
                        val sizeFormatted = com.arwora.utils.ApkExtractorUtils.formatFileSize(sizeBytes)

                        AppItem(
                            appName = appName,
                            packageName = pkgInfo.packageName ?: "",
                            versionName = pkgInfo.versionName ?: "1.0",
                            firstInstallTime = installTime,
                            firstInstallTimeFormatted = formattedDateTime,
                            dayFormattedDate = dayDateString,
                            relativeTimeAgo = relativeAgo,
                            isSystemApp = isSystem,
                            icon = icon,
                            appInfo = appInfo,
                            targetSdk = appInfo.targetSdkVersion,
                            apkSizeBytes = sizeBytes,
                            apkSizeFormatted = sizeFormatted
                        )
                    } catch (e: Exception) {
                        null
                    }
                }.sortedBy { it.appName.lowercase() }

                memoryCachedApps = list
                _installedApps.value = list
            } catch (e: Exception) {
                if (!forceRefresh) {
                    _installedApps.value = emptyList()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    companion object {
        @Volatile
        private var memoryCachedApps: List<AppItem>? = null

        fun clearMemoryCache() {
            memoryCachedApps = null
        }
        fun calculateRelativeTime(installTime: Long, currentTime: Long = System.currentTimeMillis()): String {
            if (installTime <= 0L) return "Installed on unknown date"
            val diffMillis = currentTime - installTime
            if (diffMillis < 0L) return "Installed just now"

            val diffSeconds = diffMillis / 1000
            val diffMinutes = diffSeconds / 60
            val diffHours = diffMinutes / 60
            val diffDays = diffHours / 24

            return when {
                diffDays >= 365 -> {
                    val years = diffDays / 365
                    val remainingDays = diffDays % 365
                    val months = remainingDays / 30
                    if (months > 0) {
                        val yrStr = if (years == 1L) "1 year" else "$years years"
                        val moStr = if (months == 1L) "1 month" else "$months months"
                        "Installed $yrStr $moStr ago"
                    } else {
                        val yrStr = if (years == 1L) "1 year" else "$years years"
                        "Installed $yrStr ago"
                    }
                }
                diffDays >= 30 -> {
                    val months = diffDays / 30
                    val remainingDays = diffDays % 30
                    if (remainingDays > 0) {
                        val moStr = if (months == 1L) "1 month" else "$months months"
                        val dStr = if (remainingDays == 1L) "1 day" else "$remainingDays days"
                        "Installed $moStr $dStr ago"
                    } else {
                        val moStr = if (months == 1L) "1 month" else "$months months"
                        "Installed $moStr ago"
                    }
                }
                diffDays > 0 -> {
                    val dStr = if (diffDays == 1L) "1 day" else "$diffDays days"
                    "Installed $dStr ago"
                }
                diffHours > 0 -> {
                    val hStr = if (diffHours == 1L) "1 hour" else "$diffHours hours"
                    "Installed $hStr ago"
                }
                diffMinutes > 0 -> {
                    val mStr = if (diffMinutes == 1L) "1 min" else "$diffMinutes mins"
                    "Installed $mStr ago"
                }
                else -> {
                    "Installed just now"
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterType(type: AppFilterType) {
        _filterType.value = type
    }

    fun formatDuration(installMillis: Long, uninstallMillis: Long): String {
        if (installMillis <= 0L) return "Unknown duration"
        val end = if (uninstallMillis > 0L) uninstallMillis else System.currentTimeMillis()
        val diff = end - installMillis
        if (diff < 0) return "Just now"

        val seconds = (diff / 1000) % 60
        val minutes = (diff / (1000 * 60)) % 60
        val hours = (diff / (1000 * 60 * 60)) % 24
        val days = diff / (1000 * 60 * 60 * 24)

        val sb = StringBuilder()
        if (days > 0) sb.append("$days days ")
        if (hours > 0 || days > 0) sb.append("$hours hours ")
        if (minutes > 0 || hours > 0 || days > 0) sb.append("$minutes mins ")
        sb.append("$seconds secs")

        return if (uninstallMillis > 0L) "Active duration: $sb" else "Installed for: $sb"
    }
}
