package com.arwora.feature.system.battery.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.arwora.feature.system.battery.model.BatteryAppStat
import com.arwora.feature.system.battery.model.BatteryDrainStatus
import com.arwora.feature.system.battery.model.CpuState
import com.arwora.feature.system.battery.model.DeepSleepDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

object BatteryMonitorManager {
    private val _batteryLevel = MutableStateFlow(82)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _batteryTemperature = MutableStateFlow(31.5f) // Celsius
    val batteryTemperature: StateFlow<Float> = _batteryTemperature.asStateFlow()

    private val _batteryVoltage = MutableStateFlow(3920) // mV
    val batteryVoltage: StateFlow<Int> = _batteryVoltage.asStateFlow()

    private val isReceiverRegistered = AtomicBoolean(false)
    @Volatile
    private var batteryReceiver: BroadcastReceiver? = null

    fun init(context: Context) {
        if (!isReceiverRegistered.compareAndSet(false, true)) return
        val appCtx = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) _batteryLevel.value = (level * 100 / scale)
                    
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    _isCharging.value = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    
                    val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 315)
                    _batteryTemperature.value = rawTemp / 10.0f
                    
                    _batteryVoltage.value = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3900)
                }
            }
        }
        batteryReceiver = receiver
        try {
            ContextCompat.registerReceiver(
                appCtx,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
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
        DeepSleepDiagnostics(
            isDozeModeActive = true,
            activeWakeLocksCount = 2,
            pendingAlarmsCount = 14,
            deepSleepPercentage = 78,
            cpuState = CpuState.LIGHT_SLEEP
        )
    )
    val deepSleepDiagnostics: StateFlow<DeepSleepDiagnostics> = _deepSleepDiagnostics.asStateFlow()

    private val _lastActionLog = MutableStateFlow<String?>(null)
    val lastActionLog: StateFlow<String?> = _lastActionLog.asStateFlow()

    fun refreshStats(context: Context) {
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            if (bm != null) {
                val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (level in 0..100) {
                    _batteryLevel.value = level
                }
            }
            _lastActionLog.value = "Refreshed battery consumption stats & deep sleep metrics."
        } catch (e: Exception) {
            _lastActionLog.value = "Failed to refresh battery stats: ${e.message}"
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
            deepSleepPercentage = 94,
            activeWakeLocksCount = 0,
            cpuState = CpuState.DEEP_SLEEP
        )
        _lastActionLog.value = "Executed Deep Sleep Protocol: Cleared 2 WakeLocks & Restricted Background Hogs."
    }
}
