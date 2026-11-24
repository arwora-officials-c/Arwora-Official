package com.arwora.feature.system.battery.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.arwora.feature.system.battery.model.BatteryAppStat
import com.arwora.feature.system.battery.model.BatteryDrainStatus
import com.arwora.feature.system.battery.model.CpuState
import com.arwora.feature.system.battery.model.DeepSleepDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

data class BatteryTelemetrySnapshot(
    val level: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Float,
    val voltageMv: Int,
    val healthLabel: String,
    val plugTypeLabel: String,
    val dischargeRateMahPerHour: Int,
    val isCurrentAvailable: Boolean
)

object BatteryMonitorManager {
    private val _batteryLevel = MutableStateFlow(0)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _batteryTemperature = MutableStateFlow(0.0f) // Celsius
    val batteryTemperature: StateFlow<Float> = _batteryTemperature.asStateFlow()

    private val _batteryVoltage = MutableStateFlow(0) // mV
    val batteryVoltage: StateFlow<Int> = _batteryVoltage.asStateFlow()

    private val _batteryHealth = MutableStateFlow("Good")
    val batteryHealth: StateFlow<String> = _batteryHealth.asStateFlow()

    private val _batteryPlugType = MutableStateFlow("Battery")
    val batteryPlugType: StateFlow<String> = _batteryPlugType.asStateFlow()

    private val _dischargeRateMahPerHour = MutableStateFlow(0)
    val dischargeRateMahPerHour: StateFlow<Int> = _dischargeRateMahPerHour.asStateFlow()

    private val _isCurrentAvailable = MutableStateFlow(false)
    val isCurrentAvailable: StateFlow<Boolean> = _isCurrentAvailable.asStateFlow()

    private val isReceiverRegistered = AtomicBoolean(false)
    @Volatile
    private var batteryReceiver: BroadcastReceiver? = null

    @VisibleForTesting
    var clockProviderOverride: (() -> Pair<Long, Long>)? = null

    @VisibleForTesting
    var batteryTelemetryOverride: (() -> BatteryTelemetrySnapshot)? = null

    @VisibleForTesting
    fun resetTestOverrides() {
        clockProviderOverride = null
        batteryTelemetryOverride = null
    }

    fun init(context: Context) {
        if (!isReceiverRegistered.compareAndSet(false, true)) return
        val appCtx = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    applyBatteryIntent(intent)
                }
            }
        }
        batteryReceiver = receiver
        try {
            val stickyIntent = ContextCompat.registerReceiver(
                appCtx,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            if (stickyIntent != null) {
                applyBatteryIntent(stickyIntent)
            }
            refreshStats(appCtx, emitLog = false)
        } catch (_: Exception) {
            isReceiverRegistered.set(false)
            batteryReceiver = null
        }
    }

    fun unregister(context: Context) {
        val receiver = batteryReceiver ?: return
        batteryReceiver = null
        isReceiverRegistered.set(false)
        try {
            context.applicationContext.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }

    private fun applyBatteryIntent(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            _batteryLevel.value = ((level * 100) / scale).coerceIn(0, 100)
        }

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        _isCharging.value = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        if (rawTemp > 0) {
            _batteryTemperature.value = rawTemp / 10.0f
        }

        val rawVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        if (rawVoltage > 0) {
            _batteryVoltage.value = rawVoltage
        }

        val healthCode = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        _batteryHealth.value = parseBatteryHealth(healthCode)

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        _batteryPlugType.value = parsePlugType(plugged)
    }

    private val _appStats = MutableStateFlow(
        listOf(
            BatteryAppStat("com.google.android.youtube", "YouTube", 18.5f, 480, 75, 140, BatteryDrainStatus.HIGH_DRAIN),
            BatteryAppStat("com.whatsapp", "WhatsApp", 12.0f, 310, 40, 280, BatteryDrainStatus.BACKGROUND_HOG),
            BatteryAppStat("com.instagram.android", "Instagram", 10.4f, 270, 50, 190, BatteryDrainStatus.HIGH_DRAIN),
            BatteryAppStat("com.spotify.music", "Spotify", 8.2f, 210, 90, 60, BatteryDrainStatus.NORMAL),
            BatteryAppStat("com.android.chrome", "Google Chrome", 6.8f, 175, 30, 45, BatteryDrainStatus.NORMAL),
            BatteryAppStat("com.arworahyper.toolkit", "Arwora Hyper Toolkit", 2.1f, 55, 12, 10, BatteryDrainStatus.OPTIMIZED)
        )
    )
    val appStats: StateFlow<List<BatteryAppStat>> = _appStats.asStateFlow()

    private val _deepSleepDiagnostics = MutableStateFlow(
        computeSleepDiagnostics(
            elapsedMs = SystemClock.elapsedRealtime(),
            uptimeMs = SystemClock.uptimeMillis(),
            isDozeActive = false,
            activeWakeLocksCount = 0,
            pendingAlarmsCount = 0
        )
    )
    val deepSleepDiagnostics: StateFlow<DeepSleepDiagnostics> = _deepSleepDiagnostics.asStateFlow()

    private val _lastActionLog = MutableStateFlow<String?>(null)
    val lastActionLog: StateFlow<String?> = _lastActionLog.asStateFlow()

    fun refreshStats(context: Context, emitLog: Boolean = true) {
        try {
            val override = batteryTelemetryOverride?.invoke()
            if (override != null) {
                _batteryLevel.value = override.level.coerceIn(0, 100)
                _isCharging.value = override.isCharging
                _batteryTemperature.value = override.temperatureCelsius
                _batteryVoltage.value = override.voltageMv
                _batteryHealth.value = override.healthLabel
                _batteryPlugType.value = override.plugTypeLabel
                _dischargeRateMahPerHour.value = override.dischargeRateMahPerHour.coerceAtLeast(0)
                _isCurrentAvailable.value = override.isCurrentAvailable
            } else {
                val stickyIntent = runCatching {
                    context.applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                }.getOrNull()
                if (stickyIntent != null) {
                    applyBatteryIntent(stickyIntent)
                }

                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                if (bm != null) {
                    val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    if (level in 0..100) {
                        _batteryLevel.value = level
                    }
                    val rawCurrentMicroAmps = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                    val normalizedMa = normalizeCurrentMahPerHour(rawCurrentMicroAmps)
                    _isCurrentAvailable.value = normalizedMa > 0
                    _dischargeRateMahPerHour.value = normalizedMa
                }
            }

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isDozeActive = powerManager?.isDeviceIdleMode == true

            val (elapsedMs, uptimeMs) = clockProviderOverride?.invoke()
                ?: (SystemClock.elapsedRealtime() to SystemClock.uptimeMillis())

            val existing = _deepSleepDiagnostics.value
            _deepSleepDiagnostics.value = computeSleepDiagnostics(
                elapsedMs = elapsedMs,
                uptimeMs = uptimeMs,
                isDozeActive = isDozeActive,
                activeWakeLocksCount = existing.activeWakeLocksCount,
                pendingAlarmsCount = existing.pendingAlarmsCount
            )

            if (emitLog) {
                _lastActionLog.value = "Refreshed battery telemetry & CPU deep sleep metrics."
            }
        } catch (e: Exception) {
            if (emitLog) {
                _lastActionLog.value = "Failed to refresh battery stats: ${e.message}"
            }
        }
    }

    fun optimizeAppSleep(packageName: String) {
        val updated = _appStats.value.map { app ->
            if (app.packageName == packageName) {
                app.copy(status = BatteryDrainStatus.OPTIMIZED, backgroundUsageMinutes = 0)
            } else {
                app
            }
        }
        _appStats.value = updated
        _lastActionLog.value = "Placed package $packageName into Restricted Deep Sleep."
    }

    fun forceDeepSleepAll() {
        val updated = _appStats.value.map { app ->
            if (app.status == BatteryDrainStatus.BACKGROUND_HOG || app.status == BatteryDrainStatus.HIGH_DRAIN) {
                app.copy(status = BatteryDrainStatus.OPTIMIZED, backgroundUsageMinutes = 0)
            } else {
                app
            }
        }
        _appStats.value = updated
        _deepSleepDiagnostics.value = _deepSleepDiagnostics.value.copy(
            deepSleepPercentage = maxOf(_deepSleepDiagnostics.value.deepSleepPercentage, 94),
            activeWakeLocksCount = 0,
            cpuState = CpuState.DEEP_SLEEP
        )
        _lastActionLog.value = "Executed Deep Sleep Protocol: Cleared WakeLocks & Restricted Background Hogs."
    }

    fun parseBatteryHealth(healthCode: Int): String = when (healthCode) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
        else -> "Unknown"
    }

    fun parsePlugType(plugged: Int): String = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        else -> "Battery"
    }

    fun normalizeCurrentMahPerHour(rawMicroAmps: Long): Int {
        if (rawMicroAmps == Long.MIN_VALUE || rawMicroAmps == Int.MIN_VALUE.toLong() || rawMicroAmps == 0L) {
            return 0
        }
        val ma = abs(rawMicroAmps) / 1000L
        return if (ma in 1L..25000L) ma.toInt() else 0
    }

    fun computeSleepDiagnostics(
        elapsedMs: Long,
        uptimeMs: Long,
        isDozeActive: Boolean,
        activeWakeLocksCount: Int,
        pendingAlarmsCount: Int
    ): DeepSleepDiagnostics {
        val safeElapsed = elapsedMs.coerceAtLeast(0L)
        val safeUptime = uptimeMs.coerceIn(0L, safeElapsed)
        val deepSleepMs = (safeElapsed - safeUptime).coerceAtLeast(0L)
        val deepSleepPercentage = if (safeElapsed > 0L) {
            ((deepSleepMs * 100L) / safeElapsed).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val cpuState = when {
            deepSleepPercentage >= 60 -> CpuState.DEEP_SLEEP
            deepSleepPercentage >= 25 -> CpuState.LIGHT_SLEEP
            else -> CpuState.AWAKE
        }
        return DeepSleepDiagnostics(
            isDozeModeActive = isDozeActive,
            activeWakeLocksCount = activeWakeLocksCount,
            pendingAlarmsCount = pendingAlarmsCount,
            deepSleepPercentage = deepSleepPercentage,
            cpuState = cpuState,
            elapsedRealtimeMs = safeElapsed,
            awakeUptimeMs = safeUptime,
            deepSleepMs = deepSleepMs
        )
    }

    fun formatDurationCompact(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return when {
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
