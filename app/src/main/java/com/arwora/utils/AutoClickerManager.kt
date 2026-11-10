package com.arwora.utils

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.text.TextUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ClickMode(val label: String, val description: String) {
    SINGLE("Single Target", "Tap rapidly at one point with customizable speed"),
    MULTI("Multi-Target", "Execute a sequence of multiple points in continuous loop"),
    SWIPE("Swipe Gesture", "Automated scroll and swipe movements across screen")
}

enum class StopType(val label: String) {
    INFINITE("Run Infinitely"),
    TIMER("Stop by Timer"),
    REPEAT_COUNT("Stop by Repeat Cycles")
}

data class ClickPoint(
    val id: Int,
    var x: Float,
    var y: Float,
    var delayMs: Long = 100L
)

data class SwipePoint(
    var startX: Float,
    var startY: Float,
    var endX: Float,
    var endY: Float,
    var durationMs: Long = 300L
)

data class ClickerProfile(
    val id: String,
    val name: String,
    val mode: ClickMode,
    val intervalMs: Long,
    val tapDurationMs: Long,
    val stopType: StopType,
    val timerMins: Int,
    val repeatCount: Int,
    val targets: List<ClickPoint>
)

object AutoClickerManager {

    private const val PREFS_NAME = "auto_clicker_prefs"
    private const val KEY_INTERVAL = "click_interval"
    private const val KEY_TAP_DURATION = "tap_duration"
    private const val KEY_MODE = "click_mode"
    private const val KEY_STOP_TYPE = "stop_type"
    private const val KEY_TIMER_MINS = "timer_minutes"
    private const val KEY_REPEAT_COUNT = "repeat_count"
    private const val KEY_RANDOM_JITTER = "random_jitter"
    private const val KEY_PROFILES_JSON = "clicker_profiles_json"

    private lateinit var prefs: SharedPreferences

    private val _mode = MutableStateFlow(ClickMode.SINGLE)
    val mode: StateFlow<ClickMode> = _mode.asStateFlow()

    private val _clickIntervalMs = MutableStateFlow(100L)
    val clickIntervalMs: StateFlow<Long> = _clickIntervalMs.asStateFlow()

    private val _tapDurationMs = MutableStateFlow(50L)
    val tapDurationMs: StateFlow<Long> = _tapDurationMs.asStateFlow()

    private val _stopType = MutableStateFlow(StopType.INFINITE)
    val stopType: StateFlow<StopType> = _stopType.asStateFlow()

    private val _stopTimerMinutes = MutableStateFlow(5)
    val stopTimerMinutes: StateFlow<Int> = _stopTimerMinutes.asStateFlow()

    private val _stopRepeatCount = MutableStateFlow(500)
    val stopRepeatCount: StateFlow<Int> = _stopRepeatCount.asStateFlow()

    private val _randomizeJitter = MutableStateFlow(false)
    val randomizeJitter: StateFlow<Boolean> = _randomizeJitter.asStateFlow()

    private val _targets = MutableStateFlow<List<ClickPoint>>(listOf(ClickPoint(1, 400f, 600f, 100L)))
    val targets: StateFlow<List<ClickPoint>> = _targets.asStateFlow()

    private val _swipe = MutableStateFlow(SwipePoint(400f, 900f, 400f, 400f, 300L))
    val swipe: StateFlow<SwipePoint> = _swipe.asStateFlow()

    private val _isOverlayActive = MutableStateFlow(false)
    val isOverlayActive: StateFlow<Boolean> = _isOverlayActive.asStateFlow()

    private val _isClicking = MutableStateFlow(false)
    val isClicking: StateFlow<Boolean> = _isClicking.asStateFlow()

    private val _totalClicks = MutableStateFlow(0)
    val totalClicks: StateFlow<Int> = _totalClicks.asStateFlow()

    private val _profiles = MutableStateFlow<List<ClickerProfile>>(emptyList())
    val profiles: StateFlow<List<ClickerProfile>> = _profiles.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _clickIntervalMs.value = prefs.getLong(KEY_INTERVAL, 100L)
        _tapDurationMs.value = prefs.getLong(KEY_TAP_DURATION, 50L)
        val modeStr = prefs.getString(KEY_MODE, ClickMode.SINGLE.name)
        _mode.value = try { ClickMode.valueOf(modeStr ?: ClickMode.SINGLE.name) } catch (e: Exception) { ClickMode.SINGLE }
        val stopStr = prefs.getString(KEY_STOP_TYPE, StopType.INFINITE.name)
        _stopType.value = try { StopType.valueOf(stopStr ?: StopType.INFINITE.name) } catch (e: Exception) { StopType.INFINITE }
        _stopTimerMinutes.value = prefs.getInt(KEY_TIMER_MINS, 5)
        _stopRepeatCount.value = prefs.getInt(KEY_REPEAT_COUNT, 500)
        _randomizeJitter.value = prefs.getBoolean(KEY_RANDOM_JITTER, false)

        loadProfiles()
    }

    private fun loadProfiles() {
        val json = prefs.getString(KEY_PROFILES_JSON, null)
        if (json.isNullOrBlank()) {
            val defaults = listOf(
                ClickerProfile("fast_farm", "Rapid Tap (Gaming)", ClickMode.SINGLE, 40L, 20L, StopType.INFINITE, 5, 1000, listOf(ClickPoint(1, 400f, 600f, 40L))),
                ClickerProfile("social_scroll", "Feed Autoscroll", ClickMode.SWIPE, 1500L, 50L, StopType.TIMER, 10, 100, emptyList()),
                ClickerProfile("balanced_macro", "Dual Target Sequence", ClickMode.MULTI, 120L, 40L, StopType.REPEAT_COUNT, 5, 250, listOf(ClickPoint(1, 350f, 500f, 120L), ClickPoint(2, 650f, 500f, 120L)))
            )
            _profiles.value = defaults
            saveProfiles(defaults)
            return
        }

        try {
            val array = JSONArray(json)
            val list = mutableListOf<ClickerProfile>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pointsArr = obj.optJSONArray("targets") ?: JSONArray()
                val points = mutableListOf<ClickPoint>()
                for (j in 0 until pointsArr.length()) {
                    val pObj = pointsArr.getJSONObject(j)
                    points.add(ClickPoint(pObj.getInt("id"), pObj.getDouble("x").toFloat(), pObj.getDouble("y").toFloat(), pObj.optLong("delayMs", 100L)))
                }
                list.add(
                    ClickerProfile(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        mode = try { ClickMode.valueOf(obj.getString("mode")) } catch (_: Exception) { ClickMode.SINGLE },
                        intervalMs = obj.optLong("intervalMs", 100L),
                        tapDurationMs = obj.optLong("tapDurationMs", 50L),
                        stopType = try { StopType.valueOf(obj.getString("stopType")) } catch (_: Exception) { StopType.INFINITE },
                        timerMins = obj.optInt("timerMins", 5),
                        repeatCount = obj.optInt("repeatCount", 500),
                        targets = points
                    )
                )
            }
            _profiles.value = list
        } catch (_: Exception) {
            _profiles.value = emptyList()
        }
    }

    private fun saveProfiles(list: List<ClickerProfile>) {
        if (!::prefs.isInitialized) return
        try {
            val array = JSONArray()
            for (p in list) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("mode", p.mode.name)
                    put("intervalMs", p.intervalMs)
                    put("tapDurationMs", p.tapDurationMs)
                    put("stopType", p.stopType.name)
                    put("timerMins", p.timerMins)
                    put("repeatCount", p.repeatCount)
                    val pArr = JSONArray()
                    for (t in p.targets) {
                        val tObj = JSONObject().apply {
                            put("id", t.id)
                            put("x", t.x.toDouble())
                            put("y", t.y.toDouble())
                            put("delayMs", t.delayMs)
                        }
                        pArr.put(tObj)
                    }
                    put("targets", pArr)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_PROFILES_JSON, array.toString()).apply()
        } catch (_: Exception) {}
    }

    fun saveCurrentAsProfile(name: String) {
        val newProfile = ClickerProfile(
            id = "profile_${System.currentTimeMillis()}",
            name = name.ifBlank { "Profile ${_profiles.value.size + 1}" },
            mode = _mode.value,
            intervalMs = _clickIntervalMs.value,
            tapDurationMs = _tapDurationMs.value,
            stopType = _stopType.value,
            timerMins = _stopTimerMinutes.value,
            repeatCount = _stopRepeatCount.value,
            targets = _targets.value.map { it.copy() }
        )
        val updated = _profiles.value + newProfile
        _profiles.value = updated
        saveProfiles(updated)
    }

    fun applyProfile(profile: ClickerProfile) {
        setMode(profile.mode)
        setInterval(profile.intervalMs)
        setTapDuration(profile.tapDurationMs)
        setStopType(profile.stopType)
        setStopTimerMinutes(profile.timerMins)
        setStopRepeatCount(profile.repeatCount)
        if (profile.targets.isNotEmpty()) {
            _targets.value = profile.targets.map { it.copy() }
        }
    }

    fun deleteProfile(id: String) {
        val updated = _profiles.value.filterNot { it.id == id }
        _profiles.value = updated
        saveProfiles(updated)
    }

    fun setMode(newMode: ClickMode) {
        _mode.value = newMode
        if (::prefs.isInitialized) prefs.edit().putString(KEY_MODE, newMode.name).apply()
    }

    fun setInterval(ms: Long) {
        val safe = ms.coerceAtLeast(10L)
        _clickIntervalMs.value = safe
        if (::prefs.isInitialized) prefs.edit().putLong(KEY_INTERVAL, safe).apply()
    }

    fun setTapDuration(ms: Long) {
        val safe = ms.coerceIn(10L, 1000L)
        _tapDurationMs.value = safe
        if (::prefs.isInitialized) prefs.edit().putLong(KEY_TAP_DURATION, safe).apply()
    }

    fun setStopType(type: StopType) {
        _stopType.value = type
        if (::prefs.isInitialized) prefs.edit().putString(KEY_STOP_TYPE, type.name).apply()
    }

    fun setStopTimerMinutes(mins: Int) {
        _stopTimerMinutes.value = mins.coerceAtLeast(1)
        if (::prefs.isInitialized) prefs.edit().putInt(KEY_TIMER_MINS, _stopTimerMinutes.value).apply()
    }

    fun setStopRepeatCount(count: Int) {
        _stopRepeatCount.value = count.coerceAtLeast(1)
        if (::prefs.isInitialized) prefs.edit().putInt(KEY_REPEAT_COUNT, _stopRepeatCount.value).apply()
    }

    fun setRandomizeJitter(enabled: Boolean) {
        _randomizeJitter.value = enabled
        if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_RANDOM_JITTER, enabled).apply()
    }

    fun setOverlayActive(active: Boolean) {
        _isOverlayActive.value = active
    }

    fun setClickingState(clicking: Boolean) {
        _isClicking.value = clicking
    }

    fun incrementClicks(amount: Int = 1) {
        _totalClicks.value += amount
    }

    fun resetClicks() {
        _totalClicks.value = 0
    }

    fun addTarget(x: Float = 400f, y: Float = 600f) {
        val current = _targets.value.toMutableList()
        val nextId = (current.maxOfOrNull { it.id } ?: 0) + 1
        current.add(ClickPoint(nextId, x, y, _clickIntervalMs.value))
        _targets.value = current
    }

    fun removeLastTarget() {
        val current = _targets.value.toMutableList()
        if (current.size > 1) {
            current.removeAt(current.lastIndex)
            _targets.value = current
        }
    }

    fun updateTargetPosition(id: Int, x: Float, y: Float) {
        val current = _targets.value.toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx != -1) {
            current[idx] = current[idx].copy(x = x, y = y)
            _targets.value = current
        }
    }

    fun updateSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300L) {
        _swipe.value = SwipePoint(startX, startY, endX, endY, durationMs)
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val serviceName = "${context.packageName}/${com.arwora.service.AutoClickerAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(serviceName, ignoreCase = true) ||
                componentName.contains("AutoClickerAccessibilityService", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
}
