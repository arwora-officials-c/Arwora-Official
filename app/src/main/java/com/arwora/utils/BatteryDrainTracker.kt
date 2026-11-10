package com.arwora.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DrainStats(
    val sessionDurationMs: Long = 0L,
    val initialLevel: Int = 100,
    val currentLevel: Int = 100,
    val percentDropped: Int = 0,
    val drainRatePerHour: Float = 0f,
    val estimatedHoursRemaining: Float = 0f,
    val isCharging: Boolean = false,
    val screenOnDrainRatePerHour: Float = 0f,
    val standbyDrainRatePerHour: Float = 0f,
    val statusSummary: String = "Calibrating discharge baseline..."
)

object BatteryDrainTracker {
    private const val PREFS_NAME = "battery_drain_tracker_prefs"
    private const val KEY_START_TIME = "session_start_time"
    private const val KEY_INITIAL_LEVEL = "session_initial_level"
    private const val KEY_LAST_UPDATE_TIME = "last_update_time"
    private const val KEY_LAST_LEVEL = "last_level"

    private lateinit var prefs: SharedPreferences

    private val _drainStats = MutableStateFlow(DrainStats())
    val drainStats: StateFlow<DrainStats> = _drainStats.asStateFlow()

    fun init(context: Context, currentLevel: Int, isCharging: Boolean) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val startTime = prefs.getLong(KEY_START_TIME, 0L)
        val initLevel = prefs.getInt(KEY_INITIAL_LEVEL, -1)

        if (startTime == 0L || initLevel == -1) {
            resetSession(context, currentLevel, isCharging)
        } else {
            updateCurrentState(context, currentLevel, isCharging)
        }
    }

    fun resetSession(context: Context, currentLevel: Int, isCharging: Boolean) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        val now = System.currentTimeMillis()
        val safeLevel = if (currentLevel in 1..100) currentLevel else 100
        prefs.edit()
            .putLong(KEY_START_TIME, now)
            .putInt(KEY_INITIAL_LEVEL, safeLevel)
            .putLong(KEY_LAST_UPDATE_TIME, now)
            .putInt(KEY_LAST_LEVEL, safeLevel)
            .apply()

        recalculate(now, now, safeLevel, safeLevel, isCharging)
    }

    fun updateCurrentState(context: Context, currentLevel: Int, isCharging: Boolean) {
        if (!::prefs.isInitialized) {
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        val now = System.currentTimeMillis()
        val startTime = prefs.getLong(KEY_START_TIME, now)
        val initLevel = prefs.getInt(KEY_INITIAL_LEVEL, currentLevel)

        prefs.edit()
            .putLong(KEY_LAST_UPDATE_TIME, now)
            .putInt(KEY_LAST_LEVEL, currentLevel)
            .apply()

        recalculate(startTime, now, initLevel, currentLevel, isCharging)
    }

    private fun recalculate(startTime: Long, currentTime: Long, initialLevel: Int, currentLevel: Int, isCharging: Boolean) {
        val durationMs = (currentTime - startTime).coerceAtLeast(0L)
        val durationHours = durationMs / 3600000f

        val percentDropped = if (!isCharging) {
            (initialLevel - currentLevel).coerceAtLeast(0)
        } else {
            0
        }

        val ratePerHour = if (!isCharging && durationHours >= 0.0028f) { // after ~10 seconds
            val r = (percentDropped / durationHours)
            r.coerceIn(0f, 100f)
        } else {
            0f
        }

        val estimatedHoursLeft = if (!isCharging && ratePerHour > 0.5f) {
            (currentLevel / ratePerHour).coerceIn(0.5f, 120f)
        } else if (!isCharging) {
            (currentLevel / 8.5f).coerceIn(1f, 72f)
        } else {
            0f
        }

        val screenOnRate = if (ratePerHour > 0f) ratePerHour * 1.35f else 12.5f
        val standbyRate = if (ratePerHour > 0f) (ratePerHour * 0.25f).coerceAtLeast(0.4f) else 1.2f

        val summary = when {
            isCharging -> "Device is charging (discharging paused)"
            ratePerHour == 0f -> "Calibrating discharge baseline..."
            ratePerHour < 6.0f -> "Optimal efficiency: Ultra-low discharge"
            ratePerHour < 12.0f -> "Balanced: Standard everyday power consumption"
            ratePerHour < 20.0f -> "High drain: Intensive app, gaming, or screen load"
            else -> "Heavy drain: Thermal throttling or background services"
        }

        _drainStats.value = DrainStats(
            sessionDurationMs = durationMs,
            initialLevel = initialLevel,
            currentLevel = currentLevel,
            percentDropped = percentDropped,
            drainRatePerHour = ratePerHour,
            estimatedHoursRemaining = estimatedHoursLeft,
            isCharging = isCharging,
            screenOnDrainRatePerHour = screenOnRate,
            standbyDrainRatePerHour = standbyRate,
            statusSummary = summary
        )
    }
}
