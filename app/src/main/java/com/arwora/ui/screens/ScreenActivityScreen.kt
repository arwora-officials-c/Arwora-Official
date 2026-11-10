package com.arwora.ui.screens

import android.app.Application
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.arwora.feature.system.usage.RealtimeUsageTracker
import com.arwora.feature.system.usage.formatDataBytes
import com.arwora.feature.system.usage.formatNetworkSpeed
import com.arwora.ui.navigation.Screen
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

enum class UsageTimeframe(val label: String, val shortLabel: String) {
    TODAY("Today", "Today"),
    LAST_24_HOURS("24 Hours", "24h"),
    LAST_7_DAYS("7 Days", "7d")
}

enum class UsageMetric(val label: String, val shortLabel: String) {
    SCREEN_TIME("Screen time", "Screen time"),
    NOTIFICATIONS("Notifications received", "Notifications"),
    TIMES_OPENED("Times opened", "Times opened")
}

data class WellbeingSettings(
    val bedtimeEnabled: Boolean = false,
    val bedtimeStart: String = "22:00",
    val bedtimeEnd: String = "07:00",
    val bedtimeGrayscale: Boolean = true,
    val bedtimeDnd: Boolean = true,
    val bedtimeDays: Set<String> = setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    val focusModeEnabled: Boolean = false,
    val focusPausedApps: Set<String> = emptySet(),
    val focusBreakUntilMs: Long = 0L,
    val reminderEnabled: Boolean = false,
    val reminderIntervalMinutes: Int = 60,
    val eyeRestPrompt: Boolean = true
)

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val iconBitmap: Bitmap?,
    val totalTimeForegroundMs: Long,
    val lastTimeUsed: Long,
    val usagePercentage: Float,
    val isSystemApp: Boolean,
    val notificationCount: Int = 0,
    val timesOpenedCount: Int = 0,
    val timerLimitMinutes: Int = 0 // 0 means no limit
) {
    val timerLimitMs: Long get() = timerLimitMinutes * 60 * 1000L
    val isTimerExceeded: Boolean get() = timerLimitMinutes > 0 && totalTimeForegroundMs >= timerLimitMs
    val timerProgress: Float get() = if (timerLimitMinutes > 0) (totalTimeForegroundMs.toFloat() / timerLimitMs.toFloat()).coerceIn(0f, 1f) else 0f
}

data class WellbeingSummary(
    val totalScreenTimeMs: Long = 0L,
    val yesterdayScreenTimeMs: Long = 0L,
    val unlockCount: Int = 0,
    val notificationCount: Int = 0,
    val timesOpenedTotalCount: Int = 0,
    val appUsageList: List<AppUsageItem> = emptyList(),
    val hourlyActivityMap: Map<Int, Long> = emptyMap(),
    val hourlyNotificationMap: Map<Int, Int> = emptyMap(),
    val hourlyOpensMap: Map<Int, Int> = emptyMap()
)

data class ScreenActivityUiState(
    val hasPermission: Boolean = false,
    val isInitialLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val selectedTimeframe: UsageTimeframe = UsageTimeframe.TODAY,
    val selectedMetric: UsageMetric = UsageMetric.SCREEN_TIME,
    val summary: WellbeingSummary = WellbeingSummary(),
    val settings: WellbeingSettings = WellbeingSettings(),
    val searchQuery: String = "",
    val selectedFilter: String = "All" // "All", "User", "System", "Timers", "Distracting"
)

private const val PREFS_APP_LIMITS = "screen_activity_app_limits"
private const val PREFS_WELLBEING_SETTINGS = "screen_activity_wellbeing_settings"

fun getWellbeingSettings(context: Context): WellbeingSettings {
    val prefs = context.getSharedPreferences(PREFS_WELLBEING_SETTINGS, Context.MODE_PRIVATE)
    return WellbeingSettings(
        bedtimeEnabled = prefs.getBoolean("bedtime_enabled", false),
        bedtimeStart = prefs.getString("bedtime_start", "22:00") ?: "22:00",
        bedtimeEnd = prefs.getString("bedtime_end", "07:00") ?: "07:00",
        bedtimeGrayscale = prefs.getBoolean("bedtime_grayscale", true),
        bedtimeDnd = prefs.getBoolean("bedtime_dnd", true),
        bedtimeDays = prefs.getStringSet("bedtime_days", setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")) ?: setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
        focusModeEnabled = prefs.getBoolean("focus_enabled", false),
        focusPausedApps = prefs.getStringSet("focus_paused_apps", emptySet()) ?: emptySet(),
        focusBreakUntilMs = prefs.getLong("focus_break_until", 0L),
        reminderEnabled = prefs.getBoolean("reminder_enabled", false),
        reminderIntervalMinutes = prefs.getInt("reminder_interval", 60),
        eyeRestPrompt = prefs.getBoolean("eye_rest_prompt", true)
    )
}

fun saveWellbeingSettings(context: Context, settings: WellbeingSettings) {
    val prefs = context.getSharedPreferences(PREFS_WELLBEING_SETTINGS, Context.MODE_PRIVATE)
    prefs.edit()
        .putBoolean("bedtime_enabled", settings.bedtimeEnabled)
        .putString("bedtime_start", settings.bedtimeStart)
        .putString("bedtime_end", settings.bedtimeEnd)
        .putBoolean("bedtime_grayscale", settings.bedtimeGrayscale)
        .putBoolean("bedtime_dnd", settings.bedtimeDnd)
        .putStringSet("bedtime_days", settings.bedtimeDays)
        .putBoolean("focus_enabled", settings.focusModeEnabled)
        .putStringSet("focus_paused_apps", settings.focusPausedApps)
        .putLong("focus_break_until", settings.focusBreakUntilMs)
        .putBoolean("reminder_enabled", settings.reminderEnabled)
        .putInt("reminder_interval", settings.reminderIntervalMinutes)
        .putBoolean("eye_rest_prompt", settings.eyeRestPrompt)
        .apply()
}

// ==================== VIEW MODEL ====================

class ScreenActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(
        ScreenActivityUiState(
            hasPermission = checkUsageStatsPermission(application),
            settings = getWellbeingSettings(application)
        )
    )
    val uiState: StateFlow<ScreenActivityUiState> = _uiState.asStateFlow()

    // In-memory icon cache to prevent redundant decoding across timeframe switches
    private val iconCache = LruCache<String, Bitmap>(100)

    init {
        checkPermissionAndLoad()
    }

    fun checkPermissionAndLoad() {
        val granted = checkUsageStatsPermission(getApplication())
        _uiState.update { it.copy(hasPermission = granted, settings = getWellbeingSettings(getApplication())) }
        if (granted) {
            loadUsageStats(_uiState.value.selectedTimeframe, isBackgroundRefresh = false)
        } else {
            _uiState.update { it.copy(isInitialLoading = false, isRefreshing = false) }
        }
    }

    fun setTimeframe(timeframe: UsageTimeframe) {
        if (_uiState.value.selectedTimeframe == timeframe) return
        _uiState.update { it.copy(selectedTimeframe = timeframe) }
        loadUsageStats(timeframe, isBackgroundRefresh = true)
    }

    fun setMetric(metric: UsageMetric) {
        _uiState.update { it.copy(selectedMetric = metric) }
    }

    fun updateWellbeingSettings(newSettings: WellbeingSettings) {
        saveWellbeingSettings(getApplication(), newSettings)
        _uiState.update { it.copy(settings = newSettings) }
    }

    fun toggleFocusApp(packageName: String) {
        val currentPaused = _uiState.value.settings.focusPausedApps.toMutableSet()
        if (currentPaused.contains(packageName)) {
            currentPaused.remove(packageName)
        } else {
            currentPaused.add(packageName)
        }
        val updated = _uiState.value.settings.copy(focusPausedApps = currentPaused)
        updateWellbeingSettings(updated)
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSelectedFilter(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun refresh() {
        loadUsageStats(_uiState.value.selectedTimeframe, isBackgroundRefresh = true)
    }

    fun updateAppLimit(packageName: String, limitMinutes: Int) {
        saveAppLimit(getApplication(), packageName, limitMinutes)
        loadUsageStats(_uiState.value.selectedTimeframe, isBackgroundRefresh = true)
    }

    private fun loadUsageStats(timeframe: UsageTimeframe, isBackgroundRefresh: Boolean) {
        val cached = WellbeingDataCache.get(timeframe)
        if (cached != null && !isBackgroundRefresh) {
            _uiState.update {
                it.copy(
                    summary = cached,
                    isInitialLoading = false,
                    isRefreshing = true
                )
            }
        } else {
            val hasExistingData = _uiState.value.summary.appUsageList.isNotEmpty()
            _uiState.update {
                it.copy(
                    isInitialLoading = !hasExistingData && !isBackgroundRefresh,
                    isRefreshing = hasExistingData || isBackgroundRefresh
                )
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val summary = fetchWellbeingStatsInternal(getApplication(), timeframe, iconCache, forceRefresh = isBackgroundRefresh)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        summary = summary,
                        isInitialLoading = false,
                        isRefreshing = false
                    )
                }
            }
        }
    }
}

// ==================== SCREEN COMPOSABLE ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenActivityScreen(
    navController: NavController,
    onBack: () -> Unit,
    viewModel: ScreenActivityViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedAppForDetails by remember { mutableStateOf<AppUsageItem?>(null) }
    var appForTimerSetting by remember { mutableStateOf<AppUsageItem?>(null) }

    // Re-check permission and sync on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissionAndLoad()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "App Usage Stats",
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        Text(
                            text = "Digital Wellbeing Dashboard",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.hasPermission) {
                        IconButton(onClick = {
                            SoundEffectManager.playClick()
                            viewModel.refresh()
                        }) {
                            if (uiState.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh"
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!uiState.hasPermission) {
                // Permission Request View
                PermissionRequiredView(
                    onGrantClick = {
                        SoundEffectManager.playClick()
                        openUsageSettings(context)
                    }
                )
            } else {
                // Filter and sort by selected metric
                val filteredList by remember(
                    uiState.summary.appUsageList,
                    uiState.searchQuery,
                    uiState.selectedFilter,
                    uiState.selectedMetric,
                    uiState.settings.focusPausedApps
                ) {
                    derivedStateOf {
                        uiState.summary.appUsageList.filter { item ->
                            val matchesSearch = uiState.searchQuery.isBlank() ||
                                    item.appName.contains(uiState.searchQuery, ignoreCase = true) ||
                                    item.packageName.contains(uiState.searchQuery, ignoreCase = true)
                            val matchesFilter = when (uiState.selectedFilter) {
                                "User" -> !item.isSystemApp
                                "System" -> item.isSystemApp
                                "Timers" -> item.timerLimitMinutes > 0
                                "Distracting" -> uiState.settings.focusPausedApps.contains(item.packageName)
                                else -> true
                            }
                            matchesSearch && matchesFilter
                        }.sortedWith(
                            when (uiState.selectedMetric) {
                                UsageMetric.SCREEN_TIME -> compareByDescending { it.totalTimeForegroundMs }
                                UsageMetric.NOTIFICATIONS -> compareByDescending { it.notificationCount }
                                UsageMetric.TIMES_OPENED -> compareByDescending { it.timesOpenedCount }
                            }
                        )
                    }
                }

                val activeTimersCount = remember(uiState.summary.appUsageList) {
                    uiState.summary.appUsageList.count { it.timerLimitMinutes > 0 }
                }
                val exceededTimersCount = remember(uiState.summary.appUsageList) {
                    uiState.summary.appUsageList.count { it.isTimerExceeded }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
                ) {
                    // 1. Sleek Compact Dashboard Widget (Ring chart, center time, unlocks, notifications, app limits)
                    item {
                        if (uiState.isInitialLoading) {
                            SkeletonDashboardCard()
                        } else {
                            CompactWellbeingDashboardCard(
                                summary = uiState.summary,
                                activeTimersCount = activeTimersCount,
                                exceededTimersCount = exceededTimersCount,
                                selectedTimeframe = uiState.selectedTimeframe,
                                onTimeframeSelected = { tf ->
                                    SoundEffectManager.playClick()
                                    viewModel.setTimeframe(tf)
                                }
                            )
                        }
                    }

                    // 2. App Activity Details & Metric Filter Graph Card
                    item {
                        if (!uiState.isInitialLoading) {
                            WellbeingActivityGraphCard(
                                summary = uiState.summary,
                                selectedMetric = uiState.selectedMetric,
                                onMetricSelected = { metric ->
                                    viewModel.setMetric(metric)
                                }
                            )
                        }
                    }

                    // 3. Search & Filter Row
                    item {
                        CompactSearchFilterRow(
                            searchQuery = uiState.searchQuery,
                            onSearchChange = { viewModel.setSearchQuery(it) },
                            selectedFilter = uiState.selectedFilter,
                            onFilterSelect = { filter ->
                                SoundEffectManager.playClick()
                                viewModel.setSelectedFilter(filter)
                            },
                            allCount = uiState.summary.appUsageList.size,
                            userCount = uiState.summary.appUsageList.count { !it.isSystemApp },
                            systemCount = uiState.summary.appUsageList.count { it.isSystemApp },
                            timersCount = activeTimersCount,
                            isLoading = uiState.isInitialLoading
                        )
                    }

                    // 4. Section Title Header (Compact)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "App Usage Breakdown (${uiState.selectedMetric.shortLabel})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!uiState.isInitialLoading) {
                                Text(
                                    text = "${filteredList.size} apps",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 4. Compact Usage List Items / Shimmer Skeletons
                    if (uiState.isInitialLoading) {
                        items(6) {
                            SkeletonAppUsageRow()
                        }
                    } else if (filteredList.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HourglassEmpty,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = "No usage recorded",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "No apps matched your filter or had screen activity.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredList, key = { it.packageName }) { appItem ->
                            CompactAppUsageRow(
                                item = appItem,
                                onClick = {
                                    SoundEffectManager.playClick()
                                    selectedAppForDetails = appItem
                                },
                                onSetTimerClick = {
                                    SoundEffectManager.playClick()
                                    appForTimerSetting = appItem
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // App Detail Dialog
    selectedAppForDetails?.let { item ->
        AppDetailDialog(
            item = item,
            onDismiss = { selectedAppForDetails = null },
            onSetTimer = {
                selectedAppForDetails = null
                appForTimerSetting = item
            }
        )
    }

    // App Timer Setting Dialog
    appForTimerSetting?.let { item ->
        AppTimerDialog(
            item = item,
            onDismiss = { appForTimerSetting = null },
            onSaveTimer = { newLimitMinutes ->
                viewModel.updateAppLimit(item.packageName, newLimitMinutes)
                appForTimerSetting = null
            }
        )
    }
}

// ==================== SHIMMER BRUSH EFFECT ====================

@Composable
fun rememberShimmerBrush(): Brush {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    )
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )
    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )
}

// ==================== SKELETON LOADERS ====================

@Composable
fun SkeletonDashboardCard() {
    val shimmer = rememberShimmerBrush()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Skeleton
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmer)
                )
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(shimmer)
                )
            }

            // Body Skeleton
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Circle Placeholder
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(shimmer)
                )

                // 3 Tile Placeholders
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(shimmer)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SkeletonAppUsageRow() {
    val shimmer = rememberShimmerBrush()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon placeholder
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(shimmer)
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Text lines
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmer)
                        )
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmer)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(shimmer)
                    )
                }
            }

            // Progress bar placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(shimmer)
            )
        }
    }
}

// ==================== COMPACT UI COMPONENTS ====================

data class DashboardStorageStats(
    val total: Long,
    val free: Long,
    val used: Long,
    val apps: Long,
    val media: Long,
    val docs: Long,
    val cacheSize: Long
)

data class StorageSegmentItem(
    val label: String,
    val sizeBytes: Long,
    val color: Color
)

@Composable
fun CompactWellbeingDashboardCard(
    summary: WellbeingSummary,
    activeTimersCount: Int,
    exceededTimersCount: Int,
    selectedTimeframe: UsageTimeframe,
    onTimeframeSelected: (UsageTimeframe) -> Unit,
    modifier: Modifier = Modifier,
    cardTitle: String = "Daily Screen Overview",
    hasPermission: Boolean = true,
    isLoading: Boolean = false,
    onCardClick: (() -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    val liveSteps by RealtimeUsageTracker.todaySteps.collectAsStateWithLifecycle()
    val hasActivityPermission by RealtimeUsageTracker.hasActivityRecognitionPermission.collectAsStateWithLifecycle()
    val isStepSensorAvailable by RealtimeUsageTracker.isStepSensorAvailable.collectAsStateWithLifecycle()

    val mobileBytes by RealtimeUsageTracker.mobileDataBytes.collectAsStateWithLifecycle()
    val wifiBytes by RealtimeUsageTracker.wifiDataBytes.collectAsStateWithLifecycle()
    val tempCelsius by RealtimeUsageTracker.batteryTempCelsius.collectAsStateWithLifecycle()
    val batteryHealth by RealtimeUsageTracker.batteryHealthStatus.collectAsStateWithLifecycle()
    val batteryLevel by RealtimeUsageTracker.batteryLevelPercent.collectAsStateWithLifecycle()
    val drainRate by RealtimeUsageTracker.batteryDrainRatePerHour.collectAsStateWithLifecycle()
    val ramUsed by RealtimeUsageTracker.ramUsedBytes.collectAsStateWithLifecycle()
    val ramTotal by RealtimeUsageTracker.ramTotalBytes.collectAsStateWithLifecycle()
    val ramPct by RealtimeUsageTracker.ramPercentUsed.collectAsStateWithLifecycle()
    val dlBps by RealtimeUsageTracker.downloadSpeedBps.collectAsStateWithLifecycle()
    val ulBps by RealtimeUsageTracker.uploadSpeedBps.collectAsStateWithLifecycle()
    val pickups by RealtimeUsageTracker.realtimePickups.collectAsStateWithLifecycle()

    val activityPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            RealtimeUsageTracker.init(context, summary.unlockCount)
        }
    }

    LaunchedEffect(summary.unlockCount) {
        RealtimeUsageTracker.init(context, summary.unlockCount)
    }

    val segmentColors = remember(primaryColor) {
        listOf(
            primaryColor,
            Color(0xFF3B82F6), // Blue
            Color(0xFF10B981), // Emerald
            Color(0xFFF59E0B), // Amber
            Color(0xFFEC4899)  // Pink
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (onCardClick != null) {
                    Modifier.clickable {
                        SoundEffectManager.playClick()
                        onCardClick()
                    }
                } else Modifier
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Header: Title and Timeframe Segmented Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueryStats,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = cardTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Compact Timeframe Chips
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        UsageTimeframe.values().forEach { timeframe ->
                            val isSelected = selectedTimeframe == timeframe
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        SoundEffectManager.playClick()
                                        onTimeframeSelected(timeframe)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = timeframe.shortLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Body: Side-by-side progress wheels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 1. Screen Activity Wheel
                Box(
                    modifier = Modifier.size(115.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val topApps = summary.appUsageList.take(5)
                    val totalMs = summary.totalScreenTimeMs
                    Canvas(modifier = Modifier.size(105.dp)) {
                        val strokeWidth = 9.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                        val arcSize = Size(diameter, diameter)
                        drawArc(
                            color = surfaceVariantColor.copy(alpha = 0.4f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        if (hasPermission && totalMs > 0 && topApps.isNotEmpty()) {
                            var currentAngle = -90f
                            val gapAngle = if (topApps.size > 1) 4f else 0f
                            topApps.forEachIndexed { index, app ->
                                val sweep = ((app.totalTimeForegroundMs.toFloat() / totalMs.toFloat()) * 360f - gapAngle).coerceAtLeast(3f)
                                val color = segmentColors[index % segmentColors.size]
                                drawArc(
                                    color = color,
                                    startAngle = currentAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                currentAngle += sweep + gapAngle
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (!hasPermission) "—" else if (isLoading) "..." else formatDurationCompact(summary.totalScreenTimeMs),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 2. Storage Breakdown Wheel
                Box(
                    modifier = Modifier.size(115.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val storageStats = remember {
                        try {
                            val totalBytes = java.io.File(context.filesDir.absolutePath).totalSpace.coerceAtLeast(1L)
                            val freeBytes = java.io.File(context.filesDir.absolutePath).usableSpace.coerceAtLeast(0L)
                            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
                            val cacheSize = context.cacheDir.listFiles()?.sumOf { it.length() }?.coerceAtLeast(12 * 1024 * 1024L) ?: (12 * 1024 * 1024L)
                            val remainingUsed = (usedBytes - cacheSize).coerceAtLeast(0L)
                            DashboardStorageStats(
                                total = totalBytes,
                                free = freeBytes,
                                used = usedBytes,
                                apps = (remainingUsed * 0.35f).toLong(),
                                media = (remainingUsed * 0.45f).toLong(),
                                docs = (remainingUsed * 0.20f).toLong(),
                                cacheSize = cacheSize
                            )
                        } catch (e: Exception) {
                            DashboardStorageStats(
                                total = 64 * 1024 * 1024 * 1024L,
                                free = 24 * 1024 * 1024 * 1024L,
                                used = 40 * 1024 * 1024 * 1024L,
                                apps = 15 * 1024 * 1024 * 1024L,
                                media = 18 * 1024 * 1024 * 1024L,
                                docs = 5 * 1024 * 1024 * 1024L,
                                cacheSize = 2 * 1024 * 1024 * 1024L
                            )
                        }
                    }
                    Canvas(modifier = Modifier.size(105.dp)) {
                        val strokeWidth = 9.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                        val arcSize = Size(diameter, diameter)
                        var currentAngle = -90f
                        val gapAngle = 3f
                        listOf(
                            StorageSegmentItem("Apps", storageStats.apps, Color(0xFF3B82F6)),
                            StorageSegmentItem("Media", storageStats.media, Color(0xFFEC4899)),
                            StorageSegmentItem("Docs", storageStats.docs, Color(0xFFF59E0B)),
                            StorageSegmentItem("Cache", storageStats.cacheSize, Color(0xFF8B5CF6)),
                            StorageSegmentItem("Free", storageStats.free, Color(0xFF10B981))
                        ).forEach { segment ->
                            val sweep = ((segment.sizeBytes.toFloat() / storageStats.total.toFloat()) * 360f - gapAngle).coerceAtLeast(3f)
                            drawArc(
                                color = segment.color,
                                startAngle = currentAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            currentAngle += sweep + gapAngle
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(storageStats.used * 100 / storageStats.total).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Used",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Top App Ring Breakdown Slices Legend
            if (hasPermission && summary.appUsageList.isNotEmpty()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val topApps = summary.appUsageList.take(6)
                    val totalMs = summary.totalScreenTimeMs
                    topApps.forEachIndexed { index, app ->
                        val color = segmentColors[index % segmentColors.size]
                        val pct = if (totalMs > 0) ((app.totalTimeForegroundMs.toFloat() / totalMs.toFloat()) * 100).toInt() else 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = "${formatDurationCompact(app.totalTimeForegroundMs)} ($pct%)",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 6 Real-time Telemetry & Hardware Tracking Metrics formatted as horizontal list rows inside App Usage Stats
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                // Item 1: Steps Today
                CompactMetricItem(
                    icon = Icons.Default.DirectionsWalk,
                    iconTint = Color(0xFF10B981),
                    title = "Steps Today",
                    value = if (hasActivityPermission) "$liveSteps" else "Grant",
                    subtitle = if (isStepSensorAvailable) "hardware sensor" else "pedometer",
                    onClick = { if (!hasActivityPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) activityPermissionLauncher.launch(android.Manifest.permission.ACTIVITY_RECOGNITION) }
                )

                // Item 2: Notification
                CompactMetricItem(
                    icon = Icons.Default.Notifications,
                    iconTint = Color(0xFF3B82F6),
                    title = "Notification",
                    value = if (!hasPermission) "—" else if (isLoading) "..." else "${summary.notificationCount}",
                    subtitle = "received alerts"
                )

                // Item 3: App Limits
                val timerColor = if (exceededTimersCount > 0) Color(0xFFEF4444) else Color(0xFF10B981)
                CompactMetricItem(
                    icon = if (exceededTimersCount > 0) Icons.Default.TimerOff else Icons.Default.Timer,
                    iconTint = timerColor,
                    title = "App",
                    value = if (!hasPermission) "—" else if (isLoading) "..." else if (exceededTimersCount > 0) "$exceededTimersCount Over" else "$activeTimersCount Active",
                    subtitle = if (exceededTimersCount > 0) "limit exceeded" else "monitored",
                    valueColor = if (exceededTimersCount > 0) Color(0xFFEF4444) else null
                )

                // Item 4: Data Usage Today
                CompactMetricItem(
                    icon = Icons.Default.DataUsage,
                    iconTint = Color(0xFF3B82F6),
                    title = "Data Usage Today",
                    value = "Mobile ${formatDataBytes(mobileBytes)} • Wi-Fi ${formatDataBytes(wifiBytes)}",
                    subtitle = "mobile & wi-fi"
                )

                // Item 2: Battery Thermal
                val tempColor = if (tempCelsius > 39.0f) Color(0xFFEF4444) else if (tempCelsius > 35.0f) Color(0xFFF59E0B) else Color(0xFF10B981)
                CompactMetricItem(
                    icon = Icons.Default.Thermostat,
                    iconTint = tempColor,
                    title = "Battery Thermal",
                    value = "${String.format(Locale.US, "%.1f", tempCelsius)} °C • Health: $batteryHealth",
                    subtitle = "temperature",
                    valueColor = tempColor
                )

                // Item 3: Battery Drain Rate
                val drainColor = if (drainRate > 5.0f) Color(0xFFEF4444) else Color(0xFF10B981)
                CompactMetricItem(
                    icon = Icons.Default.BatteryAlert,
                    iconTint = drainColor,
                    title = "Battery Drain Rate",
                    value = "${String.format(Locale.US, "%.1f", drainRate)}% / hr • ~${(batteryLevel / drainRate.coerceAtLeast(0.5f)).toInt()}h est. backup",
                    subtitle = "drain rate",
                    valueColor = drainColor
                )

                // Item 4: RAM Breakdown
                val ramColor = if (ramPct > 80) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                CompactMetricItem(
                    icon = Icons.Default.Memory,
                    iconTint = ramColor,
                    title = "RAM Breakdown",
                    value = "${formatDataBytes(ramUsed)} / ${formatDataBytes(ramTotal)} ($ramPct%)",
                    subtitle = "memory footprint",
                    valueColor = ramColor
                )

                // Item 5: Device Pickups
                CompactMetricItem(
                    icon = Icons.Default.PhonelinkRing,
                    iconTint = Color(0xFFEC4899),
                    title = "Device Pickups",
                    value = "$pickups Pickups",
                    subtitle = "screen pick-ups"
                )
            }
        }
    }
}

@Composable
fun CompactMetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    subtitle: String,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                ),
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}

// ==================== ADVANCED DIGITAL WELLBEING COMPONENTS ====================

@Composable
fun WellbeingActivityGraphCard(
    summary: WellbeingSummary,
    selectedMetric: UsageMetric,
    onMetricSelected: (UsageMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header & Metric Selector Dropdown / Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "App Activity Details",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = when (selectedMetric) {
                            UsageMetric.SCREEN_TIME -> formatDurationCompact(summary.totalScreenTimeMs)
                            UsageMetric.NOTIFICATIONS -> "${summary.notificationCount} received"
                            UsageMetric.TIMES_OPENED -> "${summary.timesOpenedTotalCount} opens"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Full-Width Segmented Metric Selector Tabs
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    UsageMetric.values().forEach { metric ->
                        val isSelected = selectedMetric == metric
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable {
                                    SoundEffectManager.playClick()
                                    onMetricSelected(metric)
                                }
                                .padding(vertical = 7.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = metric.shortLabel,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Subtitle peak hour info
            val activeDataMap = when (selectedMetric) {
                UsageMetric.SCREEN_TIME -> summary.hourlyActivityMap.mapValues { it.value.toFloat() / (60 * 1000f) }
                UsageMetric.NOTIFICATIONS -> summary.hourlyNotificationMap.mapValues { it.value.toFloat() }
                UsageMetric.TIMES_OPENED -> summary.hourlyOpensMap.mapValues { it.value.toFloat() }
            }

            val maxVal = activeDataMap.values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            val peakHour = activeDataMap.maxByOrNull { it.value }?.key ?: 12
            val peakFormatted = if (peakHour == 0) "12 AM" else if (peakHour < 12) "${peakHour} AM" else if (peakHour == 12) "12 PM" else "${peakHour - 12} PM"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (selectedMetric) {
                        UsageMetric.SCREEN_TIME -> "Peak usage around $peakFormatted"
                        UsageMetric.NOTIFICATIONS -> "Peak notifications around $peakFormatted"
                        UsageMetric.TIMES_OPENED -> "Peak app launches around $peakFormatted"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            // Bar chart view across 24 hours
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(85.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                for (h in 0..23) {
                    val valVal = activeDataMap[h] ?: 0f
                    val heightRatio = (valVal / maxVal).coerceIn(0.08f, 1f)
                    val isPeak = h == peakHour && valVal > 0f

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight(heightRatio)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(
                                    if (isPeak) MaterialTheme.colorScheme.primary
                                    else if (valVal > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                        )
                    }
                }
            }

            // X-Axis Hour Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("12 AM", "6 AM", "12 PM", "6 PM", "11 PM").forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun DigitalWellbeingToolsSection(
    settings: WellbeingSettings,
    activeTimersCount: Int,
    exceededTimersCount: Int,
    onOpenBedtimeDialog: () -> Unit,
    onOpenFocusDialog: () -> Unit,
    onOpenReminderDialog: () -> Unit,
    onUpdateSettings: (WellbeingSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Digital Wellbeing Tools",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Row 1: Bedtime Mode & Focus Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tool 1: Bedtime Mode
                    Surface(
                        onClick = {
                            SoundEffectManager.playClick()
                            onOpenBedtimeDialog()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (settings.bedtimeEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, if (settings.bedtimeEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF8B5CF6).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bedtime,
                                        contentDescription = null,
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Box(contentAlignment = Alignment.Center) {
                                    Switch(
                                        checked = settings.bedtimeEnabled,
                                        onCheckedChange = { checked ->
                                            SoundEffectManager.playClick()
                                            onUpdateSettings(settings.copy(bedtimeEnabled = checked))
                                        },
                                        modifier = Modifier.scale(0.75f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Bedtime Mode",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (settings.bedtimeEnabled) "${settings.bedtimeStart} - ${settings.bedtimeEnd}" else "Schedule bedtime",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Tool 2: Focus Mode
                    Surface(
                        onClick = {
                            SoundEffectManager.playClick()
                            onOpenFocusDialog()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (settings.focusModeEnabled) Color(0xFF10B981).copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, if (settings.focusModeEnabled) Color(0xFF10B981).copy(alpha = 0.4f)
                                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CenterFocusStrong,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Box(contentAlignment = Alignment.Center) {
                                    Switch(
                                        checked = settings.focusModeEnabled,
                                        onCheckedChange = { checked ->
                                            SoundEffectManager.playClick()
                                            onUpdateSettings(settings.copy(focusModeEnabled = checked))
                                        },
                                        modifier = Modifier.scale(0.75f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Focus Mode",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (settings.focusModeEnabled) "${settings.focusPausedApps.size} apps paused" else "Pause distractions",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Row 2: Screen Time Reminders & App Timers Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tool 3: Take a Break Reminder
                    Surface(
                        onClick = {
                            SoundEffectManager.playClick()
                            onOpenReminderDialog()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (settings.reminderEnabled) Color(0xFF3B82F6).copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, if (settings.reminderEnabled) Color(0xFF3B82F6).copy(alpha = 0.4f)
                                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3B82F6).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Alarm,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Box(contentAlignment = Alignment.Center) {
                                    Switch(
                                        checked = settings.reminderEnabled,
                                        onCheckedChange = { checked ->
                                            SoundEffectManager.playClick()
                                            onUpdateSettings(settings.copy(reminderEnabled = checked))
                                        },
                                        modifier = Modifier.scale(0.75f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Take a Break",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (settings.reminderEnabled) "Every ${settings.reminderIntervalMinutes}m alert" else "Screen reminders",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Tool 4: App Timers Status Tile
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(
                                            (if (exceededTimersCount > 0) Color(0xFFEF4444) else Color(0xFFF59E0B)).copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = if (exceededTimersCount > 0) Color(0xFFEF4444) else Color(0xFFF59E0B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "$activeTimersCount active",
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "App Timers",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (exceededTimersCount > 0) "$exceededTimersCount limit exceeded!" else "Daily limits set",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = if (exceededTimersCount > 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== BEDTIME MODE DIALOG ====================

@Composable
fun BedtimeModeDialog(
    settings: WellbeingSettings,
    onDismiss: () -> Unit,
    onSave: (WellbeingSettings) -> Unit
) {
    var enabled by remember { mutableStateOf(settings.bedtimeEnabled) }
    var startTime by remember { mutableStateOf(settings.bedtimeStart) }
    var endTime by remember { mutableStateOf(settings.bedtimeEnd) }
    var grayscale by remember { mutableStateOf(settings.bedtimeGrayscale) }
    var dnd by remember { mutableStateOf(settings.bedtimeDnd) }

    val daysList = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    var selectedDays by remember { mutableStateOf(settings.bedtimeDays) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bedtime Mode Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Bedtime Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Schedule times
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("Start Time") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("End Time") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Days selector
                Text("Active Days", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    daysList.forEach { day ->
                        val isSelected = selectedDays.contains(day)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    selectedDays = if (isSelected) selectedDays - day else selectedDays + day
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.take(1),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Grayscale switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Grayscale Display", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Changes screen to black and white at bedtime", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = grayscale, onCheckedChange = { grayscale = it })
                }

                // Do Not Disturb switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Do Not Disturb", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Mute calls, notifications and sound alerts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = dnd, onCheckedChange = { dnd = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        settings.copy(
                            bedtimeEnabled = enabled,
                            bedtimeStart = startTime,
                            bedtimeEnd = endTime,
                            bedtimeDays = selectedDays,
                            bedtimeGrayscale = grayscale,
                            bedtimeDnd = dnd
                        )
                    )
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==================== FOCUS MODE DIALOG ====================

@Composable
fun FocusModeDialog(
    settings: WellbeingSettings,
    appUsageList: List<AppUsageItem>,
    onDismiss: () -> Unit,
    onToggleApp: (String) -> Unit,
    onSave: (WellbeingSettings) -> Unit
) {
    var enabled by remember { mutableStateOf(settings.focusModeEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Focus Mode Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Focus Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                Text(
                    text = "Select distracting apps to pause when Focus Mode is turned on:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(appUsageList.take(20)) { item ->
                        val isPaused = settings.focusPausedApps.contains(item.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isPaused) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .clickable { onToggleApp(item.packageName) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (item.iconBitmap != null) {
                                    Image(bitmap = item.iconBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(24.dp))
                                } else {
                                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                        Text(item.appName.take(1), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(item.appName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                            Checkbox(checked = isPaused, onCheckedChange = { onToggleApp(item.packageName) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(settings.copy(focusModeEnabled = enabled))
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==================== SCREEN TIME REMINDER DIALOG ====================

@Composable
fun ScreenTimeReminderDialog(
    settings: WellbeingSettings,
    onDismiss: () -> Unit,
    onSave: (WellbeingSettings) -> Unit
) {
    var enabled by remember { mutableStateOf(settings.reminderEnabled) }
    var interval by remember { mutableStateOf(settings.reminderIntervalMinutes) }
    var eyeRest by remember { mutableStateOf(settings.eyeRestPrompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Take a Break Reminders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Continuous Reminders", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                Text("Reminder Frequency", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 45, 60, 90, 120).forEach { mins ->
                        val isSelected = interval == mins
                        Surface(
                            onClick = { interval = mins },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${mins}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Eye Rest & Posture Alert", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Prompts 20-20-20 rule for eye health", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = eyeRest, onCheckedChange = { eyeRest = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        settings.copy(
                            reminderEnabled = enabled,
                            reminderIntervalMinutes = interval,
                            eyeRestPrompt = eyeRest
                        )
                    )
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Reminders")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CompactSearchFilterRow(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFilter: String,
    onFilterSelect: (String) -> Unit,
    allCount: Int,
    userCount: Int,
    systemCount: Int,
    timersCount: Int,
    isLoading: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Compact Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            placeholder = {
                Text(
                    "Search apps...",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchChange("") },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        // Streamlined Horizontal Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All", "User", "System", "Timers").forEach { filter ->
                val isSelected = selectedFilter == filter
                val count = when (filter) {
                    "All" -> allCount
                    "User" -> userCount
                    "System" -> systemCount
                    "Timers" -> timersCount
                    else -> 0
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onFilterSelect(filter) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (!isLoading) {
                            Text(
                                text = "($count)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompactAppUsageRow(
    item: AppUsageItem,
    onClick: () -> Unit,
    onSetTimerClick: () -> Unit
) {
    val isExceeded = item.isTimerExceeded
    val hasTimer = item.timerLimitMinutes > 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isExceeded) Color(0xFFEF4444).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon (Compact 34dp)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.iconBitmap != null) {
                        Image(
                            bitmap = item.iconBitmap.asImageBitmap(),
                            contentDescription = "${item.appName} Icon",
                            modifier = Modifier.size(28.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.appName.take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // App Title + Info
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.appName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Duration text badge
                        Text(
                            text = formatDuration(item.totalTimeForegroundMs),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            ),
                            color = if (isExceeded) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${(item.usagePercentage * 100).toInt()}% share",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (item.notificationCount > 0) {
                                Text(
                                    text = "• ${item.notificationCount} notifs",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (hasTimer) {
                            Text(
                                text = if (isExceeded) "Limit Exceeded (${item.timerLimitMinutes}m)"
                                else "Limit: ${item.timerLimitMinutes}m",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isExceeded) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Compact Timer Icon Action Button
                IconButton(
                    onClick = onSetTimerClick,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (hasTimer) Icons.Filled.Timer else Icons.Outlined.Timer,
                        contentDescription = "Set App Timer",
                        tint = if (isExceeded) Color(0xFFEF4444)
                        else if (hasTimer) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // Compact Progress Indicator
            LinearProgressIndicator(
                progress = {
                    if (hasTimer) item.timerProgress
                    else item.usagePercentage.coerceIn(0.01f, 1f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = if (isExceeded) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
fun AppDetailDialog(
    item: AppUsageItem,
    onDismiss: () -> Unit,
    onSetTimer: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        icon = {
            if (item.iconBitmap != null) {
                Image(
                    bitmap = item.iconBitmap.asImageBitmap(),
                    contentDescription = item.appName,
                    modifier = Modifier.size(44.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.appName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        },
        title = {
            Text(
                text = item.appName,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Screen Time:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatDuration(item.totalTimeForegroundMs),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Usage Share:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${(item.usagePercentage * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Notifications:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${item.notificationCount}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3B82F6)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Last Active:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        formatTimestamp(item.lastTimeUsed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("App Type:", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (item.isSystemApp) "System App" else "Installed User App",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.timerLimitMinutes > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("App Limit:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${item.timerLimitMinutes} mins / day",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (item.isTimerExceeded) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        onSetTimer()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (item.timerLimitMinutes > 0) "Edit Limit" else "Set Limit",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Button(
                    onClick = {
                        SoundEffectManager.playClick()
                        launchApp(context, item.packageName)
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(item.packageName))
                    Toast.makeText(context, "Package copied", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Copy Package", style = MaterialTheme.typography.labelSmall)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimerDialog(
    item: AppUsageItem,
    onDismiss: () -> Unit,
    onSaveTimer: (Int) -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(if (item.timerLimitMinutes > 0) item.timerLimitMinutes else 30) }
    val presetDurations = listOf(15, 30, 45, 60, 90, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                text = "Set Daily Limit",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Configure daily screen time allowance for ${item.appName}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                // Selected Time Highlight
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (selectedMinutes >= 60) {
                                val hrs = selectedMinutes / 60
                                val mins = selectedMinutes % 60
                                if (mins > 0) "$hrs hr $mins min" else "$hrs hour${if (hrs > 1) "s" else ""}"
                            } else "$selectedMinutes minutes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "daily allowance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Preset Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetDurations.take(3).forEach { mins ->
                        val isSelected = selectedMinutes == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                SoundEffectManager.playClick()
                                selectedMinutes = mins
                            },
                            label = { Text("${mins}m", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetDurations.drop(3).forEach { mins ->
                        val isSelected = selectedMinutes == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                SoundEffectManager.playClick()
                                selectedMinutes = mins
                            },
                            label = {
                                Text(
                                    if (mins >= 60) "${mins / 60}h ${if (mins % 60 > 0) "${mins % 60}m" else ""}" else "${mins}m",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Slider
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Slider(
                        value = selectedMinutes.toFloat(),
                        onValueChange = { selectedMinutes = (it / 5).toInt() * 5 },
                        valueRange = 5f..180f,
                        steps = 34,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("5 mins", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("3 hours", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    SoundEffectManager.playClick()
                    onSaveTimer(selectedMinutes)
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Set Timer", style = MaterialTheme.typography.labelMedium)
            }
        },
        dismissButton = {
            if (item.timerLimitMinutes > 0) {
                TextButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        onSaveTimer(0) // Remove limit
                    }
                ) {
                    Text("Delete Timer", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    )
}

@Composable
fun PermissionRequiredView(onGrantClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QueryStats,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Usage Access Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "To track real-time screen duration, device unlock counts, app notifications, and configure app timers, Android requires the 'Usage Access' permission.\n\nTap below to open system settings and turn on access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = onGrantClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "Grant Usage Access",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==================== STATS ENGINE & UTILS ====================

fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    } else {
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

object WellbeingDataCache {
    private val summaryCache = java.util.concurrent.ConcurrentHashMap<UsageTimeframe, Pair<Long, WellbeingSummary>>()
    val globalIconCache = LruCache<String, Bitmap>(150)
    val appLabelCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    val systemAppFlagCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private const val CACHE_EXPIRY_MS = 45_000L // 45 seconds

    fun get(timeframe: UsageTimeframe): WellbeingSummary? {
        val entry = summaryCache[timeframe] ?: return null
        val now = System.currentTimeMillis()
        if (now - entry.first < CACHE_EXPIRY_MS) {
            return entry.second
        }
        return null
    }

    fun put(timeframe: UsageTimeframe, summary: WellbeingSummary) {
        summaryCache[timeframe] = Pair(System.currentTimeMillis(), summary)
    }

    fun invalidate() {
        summaryCache.clear()
    }
}

fun openUsageSettings(context: Context) {
    try {
        val directIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(directIntent)
    } catch (e: Exception) {
        try {
            val fallbackIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        } catch (e2: Exception) {
            val appSettingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(appSettingsIntent)
        }
    }
}

fun getSavedAppLimits(context: Context): Map<String, Int> {
    val prefs = context.getSharedPreferences(PREFS_APP_LIMITS, Context.MODE_PRIVATE)
    val map = mutableMapOf<String, Int>()
    prefs.all.forEach { (pkg, value) ->
        if (value is Int && value > 0) {
            map[pkg] = value
        }
    }
    return map
}

fun saveAppLimit(context: Context, packageName: String, limitMinutes: Int) {
    val prefs = context.getSharedPreferences(PREFS_APP_LIMITS, Context.MODE_PRIVATE)
    if (limitMinutes <= 0) {
        prefs.edit().remove(packageName).apply()
    } else {
        prefs.edit().putInt(packageName, limitMinutes).apply()
    }
}

fun launchApp(context: Context, packageName: String) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(appDetailsIntent)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open app", Toast.LENGTH_SHORT).show()
    }
}

data class UsageStatsHolder(
    val packageName: String,
    val totalTimeInForeground: Long,
    val lastTimeUsed: Long
)

suspend fun fetchWellbeingStatsInternal(
    context: Context,
    timeframe: UsageTimeframe,
    iconCache: LruCache<String, Bitmap>? = null,
    forceRefresh: Boolean = false
): WellbeingSummary = withContext(Dispatchers.IO) {
    if (!forceRefresh) {
        val cached = WellbeingDataCache.get(timeframe)
        if (cached != null) return@withContext cached
    }

    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        ?: return@withContext WellbeingSummary()

    val cal = Calendar.getInstance()
    val endTime = cal.timeInMillis

    // Start time for selected timeframe
    val startTime = when (timeframe) {
        UsageTimeframe.TODAY -> {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        UsageTimeframe.LAST_24_HOURS -> endTime - (24 * 60 * 60 * 1000L)
        UsageTimeframe.LAST_7_DAYS -> endTime - (7 * 24 * 60 * 60 * 1000L)
    }

    // Yesterday's start & end for comparison
    val calYesterday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayStart = calYesterday.timeInMillis
    val yesterdayStart = todayStart - (24 * 60 * 60 * 1000L)

    val todayStatsMap = try {
        val rawStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        val packageBuckets = mutableMapOf<String, MutableMap<Long, UsageStats>>()
        
        rawStatsList?.forEach { stats ->
            if (stats.totalTimeInForeground > 0) {
                val dayKey = stats.firstTimeStamp / (24 * 60 * 60 * 1000L)
                val bucketsForPkg = packageBuckets.getOrPut(stats.packageName) { mutableMapOf() }
                val existing = bucketsForPkg[dayKey]
                if (existing == null || stats.totalTimeInForeground > existing.totalTimeInForeground) {
                    bucketsForPkg[dayKey] = stats
                }
            }
        }
        
        val aggregated = mutableMapOf<String, UsageStatsHolder>()
        packageBuckets.forEach { (pkgName, buckets) ->
            var totalTime = 0L
            var maxLastUsed = 0L
            
            buckets.values.forEach { stats ->
                val lastUsed = stats.lastTimeUsed
                val fgTime = stats.totalTimeInForeground
                
                if (timeframe == UsageTimeframe.TODAY) {
                    if (lastUsed >= startTime) {
                        if (lastUsed > maxLastUsed) {
                            maxLastUsed = lastUsed
                        }
                        totalTime = fgTime
                    }
                } else {
                    totalTime += fgTime
                    if (lastUsed > maxLastUsed) {
                        maxLastUsed = lastUsed
                    }
                }
            }
            
            if (totalTime > 0) {
                aggregated[pkgName] = UsageStatsHolder(pkgName, totalTime, maxLastUsed)
            }
        }
        aggregated
    } catch (e: Exception) {
        emptyMap()
    }

    val yesterdayStatsMap = try {
        val rawStatsList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, yesterdayStart, todayStart)
        val packageBuckets = mutableMapOf<String, MutableMap<Long, UsageStats>>()
        
        rawStatsList?.forEach { stats ->
            if (stats.totalTimeInForeground > 0) {
                val dayKey = stats.firstTimeStamp / (24 * 60 * 60 * 1000L)
                val bucketsForPkg = packageBuckets.getOrPut(stats.packageName) { mutableMapOf() }
                val existing = bucketsForPkg[dayKey]
                if (existing == null || stats.totalTimeInForeground > existing.totalTimeInForeground) {
                    bucketsForPkg[dayKey] = stats
                }
            }
        }
        
        val aggregated = mutableMapOf<String, UsageStatsHolder>()
        packageBuckets.forEach { (pkgName, buckets) ->
            var totalTime = 0L
            var maxLastUsed = 0L
            
            buckets.values.forEach { stats ->
                val lastUsed = stats.lastTimeUsed
                val fgTime = stats.totalTimeInForeground
                
                if (lastUsed >= yesterdayStart && lastUsed < todayStart) {
                    totalTime = fgTime
                    if (lastUsed > maxLastUsed) {
                        maxLastUsed = lastUsed
                    }
                }
            }
            
            if (totalTime > 0) {
                aggregated[pkgName] = UsageStatsHolder(pkgName, totalTime, maxLastUsed)
            }
        }
        aggregated
    } catch (e: Exception) {
        emptyMap()
    }

    var yesterdayTotalMs = 0L
    yesterdayStatsMap.values.forEach {
        if (it.totalTimeInForeground > 0) {
            yesterdayTotalMs += it.totalTimeInForeground
        }
    }

    // Query Usage Events for Unlocks, Notifications, Opens, and Hourly Distributions
    var unlockCount = 0
    var totalNotificationCount = 0
    var totalTimesOpenedCount = 0
    val appNotificationCounts = mutableMapOf<String, Int>()
    val appOpensCounts = mutableMapOf<String, Int>()

    val hourlyActivityMap = mutableMapOf<Int, Long>()
    val hourlyNotificationMap = mutableMapOf<Int, Int>()
    val hourlyOpensMap = mutableMapOf<Int, Int>()

    for (h in 0..23) {
        hourlyActivityMap[h] = 0L
        hourlyNotificationMap[h] = 0
        hourlyOpensMap[h] = 0
    }

    try {
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var keyguardHiddenCount = 0
        var interactiveCount = 0
        val eventCal = Calendar.getInstance()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            eventCal.timeInMillis = event.timeStamp
            val hour = eventCal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)

            when (event.eventType) {
                18 /* KEYGUARD_HIDDEN */ -> {
                    keyguardHiddenCount++
                }
                15 /* SCREEN_INTERACTIVE */ -> {
                    interactiveCount++
                }
                1 /* ACTIVITY_RESUMED */, 7 /* USER_INTERACTION */ -> {
                    totalTimesOpenedCount++
                    hourlyOpensMap[hour] = (hourlyOpensMap[hour] ?: 0) + 1
                    event.packageName?.let { pkg ->
                        appOpensCounts[pkg] = (appOpensCounts[pkg] ?: 0) + 1
                    }
                }
                12 /* NOTIFICATION_INTERRUPTION */, 10 /* NOTIFICATION_SEEN */ -> {
                    totalNotificationCount++
                    hourlyNotificationMap[hour] = (hourlyNotificationMap[hour] ?: 0) + 1
                    event.packageName?.let { pkg ->
                        appNotificationCounts[pkg] = (appNotificationCounts[pkg] ?: 0) + 1
                    }
                }
            }
        }
        unlockCount = if (keyguardHiddenCount > 0) keyguardHiddenCount else interactiveCount
    } catch (e: Exception) {
        // Events querying may not be fully supported on some devices
    }

    // Populate hourlyActivityMap based on foreground times
    todayStatsMap.values.forEach { stats ->
        if (stats.totalTimeInForeground > 0) {
            val eventCal = Calendar.getInstance().apply { timeInMillis = stats.lastTimeUsed }
            val hour = eventCal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            hourlyActivityMap[hour] = (hourlyActivityMap[hour] ?: 0L) + stats.totalTimeInForeground
        }
    }

    val pm = context.packageManager
    var totalTimeMs = 0L

    todayStatsMap.values.forEach { stats ->
        if (stats.totalTimeInForeground > 0) {
            totalTimeMs += stats.totalTimeInForeground
        }
    }

    val limitsMap = getSavedAppLimits(context)
    val list = mutableListOf<AppUsageItem>()

    val effectiveIconCache = iconCache ?: WellbeingDataCache.globalIconCache

    todayStatsMap.values
        .filter { it.totalTimeInForeground >= 1000L }
        .sortedByDescending { it.totalTimeInForeground }
        .forEach { stats ->
            try {
                val pkgName = stats.packageName
                val cachedLabel = WellbeingDataCache.appLabelCache[pkgName]
                val cachedIsSystem = WellbeingDataCache.systemAppFlagCache[pkgName]

                val (appName, isSystem) = if (cachedLabel != null && cachedIsSystem != null) {
                    Pair(cachedLabel, cachedIsSystem)
                } else {
                    val appInfo = pm.getApplicationInfo(pkgName, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val sys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    WellbeingDataCache.appLabelCache[pkgName] = label
                    WellbeingDataCache.systemAppFlagCache[pkgName] = sys
                    Pair(label, sys)
                }

                val bitmap = if (effectiveIconCache.get(pkgName) != null) {
                    effectiveIconCache.get(pkgName)
                } else {
                    val iconDrawable = try {
                        val appInfo = pm.getApplicationInfo(pkgName, 0)
                        pm.getApplicationIcon(appInfo)
                    } catch (e: Exception) { null }
                    val decoded = try { iconDrawable?.toBitmap(width = 72, height = 72) } catch (e: Exception) { null }
                    if (decoded != null) {
                        effectiveIconCache.put(pkgName, decoded)
                    }
                    decoded
                }

                val percentage = if (totalTimeMs > 0) {
                    (stats.totalTimeInForeground.toFloat() / totalTimeMs.toFloat())
                } else 0f

                val notifCount = appNotificationCounts[pkgName] ?: 0
                val opensCount = appOpensCounts[pkgName] ?: (if (stats.totalTimeInForeground > 0) 1 else 0)
                val timerLimit = limitsMap[pkgName] ?: 0

                list.add(
                    AppUsageItem(
                        packageName = pkgName,
                        appName = appName,
                        iconBitmap = bitmap,
                        totalTimeForegroundMs = stats.totalTimeInForeground,
                        lastTimeUsed = stats.lastTimeUsed,
                        usagePercentage = percentage,
                        isSystemApp = isSystem,
                        notificationCount = notifCount,
                        timesOpenedCount = opensCount,
                        timerLimitMinutes = timerLimit
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // Ignore uninstalled packages
            }
        }

    val result = WellbeingSummary(
        totalScreenTimeMs = totalTimeMs,
        yesterdayScreenTimeMs = yesterdayTotalMs,
        unlockCount = unlockCount,
        notificationCount = totalNotificationCount,
        timesOpenedTotalCount = totalTimesOpenedCount,
        appUsageList = list,
        hourlyActivityMap = hourlyActivityMap,
        hourlyNotificationMap = hourlyNotificationMap,
        hourlyOpensMap = hourlyOpensMap
    )
    WellbeingDataCache.put(timeframe, result)
    result
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

fun formatDurationCompact(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

fun formatTimestamp(millis: Long): String {
    if (millis <= 0) return "Never"
    val diff = System.currentTimeMillis() - millis
    val minutes = diff / (60 * 1000)
    val hours = diff / (60 * 60 * 1000)
    val days = diff / (24 * 60 * 60 * 1000)

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            sdf.format(Date(millis))
        }
    }
}
