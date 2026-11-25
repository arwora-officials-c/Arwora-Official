package com.arwora.feature.system.battery.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

data class BatteryTelemetrySnapshot(
    val level: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Float,
    val voltageMv: Int,
    val healthLabel: String,
    val plugTypeLabel: String
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

    private val isReceiverRegistered = AtomicBoolean(false)
    @Volatile
    private var batteryReceiver: BroadcastReceiver? = null

    @VisibleForTesting
    var batteryTelemetryOverride: (() -> BatteryTelemetrySnapshot)? = null

    @VisibleForTesting
    fun resetTestOverrides() {
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
            refreshStats(appCtx)
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

    fun refreshStats(context: Context) {
        try {
            val override = batteryTelemetryOverride?.invoke()
            if (override != null) {
                _batteryLevel.value = override.level.coerceIn(0, 100)
                _isCharging.value = override.isCharging
                _batteryTemperature.value = override.temperatureCelsius
                _batteryVoltage.value = override.voltageMv
                _batteryHealth.value = override.healthLabel
                _batteryPlugType.value = override.plugTypeLabel
                return
            }

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
            }
        } catch (_: Exception) {}
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
}
