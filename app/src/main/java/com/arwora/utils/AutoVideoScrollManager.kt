package com.arwora.utils

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

enum class AutoScrollMode(val label: String) {
    TIME_BASED("Time-Based"),
    COMPLETION_BASED("Completion-Based")
}

data class VideoProgressSnapshot(
    val ratio: Float? = null,
    val elapsedSeconds: Int? = null,
    val totalSeconds: Int? = null,
    val hasReplayCue: Boolean = false
)

object VideoCompletionDetector {

    private val TIME_PAIR_REGEX = Regex("""(\d{1,2}):(\d{2})\s*(?:/|of|-)\s*(\d{1,2}):(\d{2})""", RegexOption.IGNORE_CASE)
    private val SECONDS_PAIR_REGEX = Regex("""(\d{1,3})s\s*/\s*(\d{1,3})s""", RegexOption.IGNORE_CASE)
    private val REPLAY_CUES = listOf("watch again", "replay", "play again", "rewatch")

    fun recognizePlatformLabel(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        return when (packageName) {
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically.go",
            "com.ss.android.ugc.aweme" -> "TikTok"
            "com.instagram.android",
            "com.instagram.lite" -> "Instagram Reels"
            "com.facebook.katana",
            "com.facebook.lite" -> "Facebook Reels"
            "com.google.android.youtube",
            "com.google.android.apps.youtube.kids" -> "YouTube Shorts"
            else -> null
        }
    }

    fun parseElapsedAndTotalSeconds(text: CharSequence?): Pair<Int, Int>? {
        if (text.isNullOrBlank()) return null
        TIME_PAIR_REGEX.find(text)?.let { match ->
            val eMin = match.groupValues[1].toIntOrNull() ?: return@let
            val eSec = match.groupValues[2].toIntOrNull() ?: return@let
            val tMin = match.groupValues[3].toIntOrNull() ?: return@let
            val tSec = match.groupValues[4].toIntOrNull() ?: return@let
            if (eSec in 0..59 && tSec in 0..59) {
                val elapsed = (eMin * 60) + eSec
                val total = (tMin * 60) + tSec
                if (total > 0 && elapsed in 0..total) {
                    return elapsed to total
                }
            }
        }
        SECONDS_PAIR_REGEX.find(text)?.let { match ->
            val elapsed = match.groupValues[1].toIntOrNull() ?: return@let
            val total = match.groupValues[2].toIntOrNull() ?: return@let
            if (total > 0 && elapsed in 0..total) {
                return elapsed to total
            }
        }
        return null
    }

    fun hasReplayCue(text: CharSequence?): Boolean {
        if (text.isNullOrBlank() || text.length > 48) return false
        val lower = text.toString().trim().lowercase()
        return REPLAY_CUES.any { lower.contains(it) }
    }

    @JvmOverloads
    fun isPlaybackCompletedOrLooped(
        previousRatio: Float?,
        current: VideoProgressSnapshot,
        previousElapsedSeconds: Int? = null
    ): Boolean {
        if (current.hasReplayCue) return true

        val ratio = current.ratio ?: run {
            val e = current.elapsedSeconds
            val t = current.totalSeconds
            if (e != null && t != null && t > 0) e.toFloat() / t.toFloat() else null
        }

        if (current.elapsedSeconds != null && current.totalSeconds != null) {
            val total = current.totalSeconds
            val elapsed = current.elapsedSeconds
            if (total >= 3 && elapsed >= (total - 1)) {
                return true
            }
            if (previousElapsedSeconds != null &&
                total >= 4 &&
                previousElapsedSeconds >= (total - 2) &&
                elapsed <= 2
            ) {
                return true
            }
        }

        if (ratio != null) {
            if (ratio >= 0.96f) return true
            if (previousRatio != null && previousRatio >= 0.72f && ratio <= 0.15f) {
                return true
            }
        }
        return false
    }
}

object AutoVideoScrollManager {

    private const val PREFS_NAME = "auto_video_scroll_prefs"
    private const val KEY_MODE = "auto_scroll_mode"
    private const val KEY_INTERVAL_SECONDS = "auto_scroll_interval_seconds"
    const val DEFAULT_INTERVAL_SECONDS = 20

    private val initialized = AtomicBoolean(false)
    @Volatile
    private var prefs: SharedPreferences? = null

    private val _mode = MutableStateFlow(AutoScrollMode.TIME_BASED)
    val mode: StateFlow<AutoScrollMode> = _mode.asStateFlow()

    private val _intervalSeconds = MutableStateFlow(DEFAULT_INTERVAL_SECONDS)
    val intervalSeconds: StateFlow<Int> = _intervalSeconds.asStateFlow()

    private val _isServiceActive = MutableStateFlow(false)
    val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _scrollCount = MutableStateFlow(0)
    val scrollCount: StateFlow<Int> = _scrollCount.asStateFlow()

    private val _countdownSecondsRemaining = MutableStateFlow(DEFAULT_INTERVAL_SECONDS)
    val countdownSecondsRemaining: StateFlow<Int> = _countdownSecondsRemaining.asStateFlow()

    private val _statusDetail = MutableStateFlow("Idle")
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    private val _detectedPlatform = MutableStateFlow<String?>(null)
    val detectedPlatform: StateFlow<String?> = _detectedPlatform.asStateFlow()

    private val _isAccessibilityRunning = MutableStateFlow(false)
    val isAccessibilityRunning: StateFlow<Boolean> = _isAccessibilityRunning.asStateFlow()

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val appPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = appPrefs
        val savedMode = appPrefs.getString(KEY_MODE, AutoScrollMode.TIME_BASED.name)
        _mode.value = runCatching {
            AutoScrollMode.valueOf(savedMode ?: AutoScrollMode.TIME_BASED.name)
        }.getOrDefault(AutoScrollMode.TIME_BASED)

        val savedSeconds = appPrefs.getInt(KEY_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS)
            .coerceIn(5, 180)
        _intervalSeconds.value = savedSeconds
        _countdownSecondsRemaining.value = if (_mode.value == AutoScrollMode.COMPLETION_BASED) 0 else savedSeconds
    }

    fun setMode(newMode: AutoScrollMode) {
        _mode.value = newMode
        _countdownSecondsRemaining.value = if (newMode == AutoScrollMode.COMPLETION_BASED) {
            0
        } else {
            _intervalSeconds.value
        }
        prefs?.edit()?.putString(KEY_MODE, newMode.name)?.apply()
        if (_isServiceActive.value && !_isPaused.value) {
            _statusDetail.value = if (newMode == AutoScrollMode.TIME_BASED) {
                "Next scroll in ${_intervalSeconds.value}s"
            } else {
                "Waiting for video completion..."
            }
        }
    }

    fun setIntervalSeconds(seconds: Int) {
        val safe = seconds.coerceIn(5, 180)
        _intervalSeconds.value = safe
        if (_mode.value == AutoScrollMode.TIME_BASED) {
            _countdownSecondsRemaining.value = safe
        }
        prefs?.edit()?.putInt(KEY_INTERVAL_SECONDS, safe)?.apply()
    }

    fun setServiceActive(active: Boolean) {
        _isServiceActive.value = active
        if (!active) {
            _isPaused.value = false
            _countdownSecondsRemaining.value = if (_mode.value == AutoScrollMode.COMPLETION_BASED) {
                0
            } else {
                _intervalSeconds.value
            }
            _detectedPlatform.value = null
            _statusDetail.value = "Idle"
        } else {
            _scrollCount.value = 0
            _countdownSecondsRemaining.value = if (_mode.value == AutoScrollMode.COMPLETION_BASED) {
                0
            } else {
                _intervalSeconds.value
            }
            _statusDetail.value = if (_mode.value == AutoScrollMode.TIME_BASED) {
                "Running • ${_intervalSeconds.value}s timer"
            } else {
                "Running • Waiting for video completion"
            }
        }
    }

    fun setPaused(paused: Boolean) {
        _isPaused.value = paused
        if (_isServiceActive.value) {
            _statusDetail.value = if (paused) {
                "Paused"
            } else if (_mode.value == AutoScrollMode.TIME_BASED) {
                "Next scroll in ${_countdownSecondsRemaining.value}s"
            } else {
                "Waiting for video completion..."
            }
        }
    }

    fun togglePaused() {
        setPaused(!_isPaused.value)
    }

    fun updateCountdown(secondsRemaining: Int) {
        _countdownSecondsRemaining.value = secondsRemaining.coerceAtLeast(0)
    }

    fun updateStatusDetail(detail: String) {
        _statusDetail.value = detail
    }

    fun updateDetectedPlatform(platform: String?) {
        _detectedPlatform.value = platform
    }

    fun incrementScrollCount() {
        _scrollCount.value += 1
    }

    fun setAccessibilityServiceRunning(running: Boolean) {
        _isAccessibilityRunning.value = running
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (com.arwora.service.AutoScrollAccessibilityService.isRunning) {
            return true
        }
        return runCatching {
            val expectedComponent = ComponentName(
                context.packageName,
                com.arwora.service.AutoScrollAccessibilityService::class.java.name
            )
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am != null && am.isEnabled) {
                val enabledList = am.getEnabledAccessibilityServiceList(
                    AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                ).orEmpty()
                val matchedInManager = enabledList.any { info ->
                    val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                    serviceInfo.packageName == expectedComponent.packageName &&
                        serviceInfo.name == expectedComponent.className
                }
                if (matchedInManager) {
                    return@runCatching true
                }
            }

            val accessibilityMasterEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1
            if (!accessibilityMasterEnabled && am?.isEnabled != true) {
                return@runCatching false
            }

            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return@runCatching false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentString = colonSplitter.next()
                val parsed = ComponentName.unflattenFromString(componentString)
                if (parsed == expectedComponent) {
                    return@runCatching true
                }
            }
            false
        }.getOrDefault(false)
    }

    fun canDrawOverlays(context: Context): Boolean {
        return runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)
    }
}
