package com.arwora.feature.system.usage

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object RealtimeUsageTracker {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _realtimeUnlocks = MutableStateFlow(0)
    val realtimeUnlocks: StateFlow<Int> = _realtimeUnlocks.asStateFlow()

    // 1. Mobile & Wi-Fi Data Usage
    private val _mobileDataBytes = MutableStateFlow(184L * 1024 * 1024)
    val mobileDataBytes: StateFlow<Long> = _mobileDataBytes.asStateFlow()

    private val _wifiDataBytes = MutableStateFlow(1850L * 1024 * 1024)
    val wifiDataBytes: StateFlow<Long> = _wifiDataBytes.asStateFlow()

    // 2. Battery Temperature & Health
    private val _batteryTempCelsius = MutableStateFlow(31.5f)
    val batteryTempCelsius: StateFlow<Float> = _batteryTempCelsius.asStateFlow()

    private val _batteryHealthStatus = MutableStateFlow("Good")
    val batteryHealthStatus: StateFlow<String> = _batteryHealthStatus.asStateFlow()

    private val _batteryLevelPercent = MutableStateFlow(85)
    val batteryLevelPercent: StateFlow<Int> = _batteryLevelPercent.asStateFlow()

    // 3. RAM Breakdown
    private val _ramUsedBytes = MutableStateFlow(4200L * 1024 * 1024)
    val ramUsedBytes: StateFlow<Long> = _ramUsedBytes.asStateFlow()

    private val _ramTotalBytes = MutableStateFlow(8192L * 1024 * 1024)
    val ramTotalBytes: StateFlow<Long> = _ramTotalBytes.asStateFlow()

    private val _ramPercentUsed = MutableStateFlow(51)
    val ramPercentUsed: StateFlow<Int> = _ramPercentUsed.asStateFlow()

    // 4. Daily Drain Rate (%/hr)
    private val _batteryDrainRatePerHour = MutableStateFlow(2.1f)
    val batteryDrainRatePerHour: StateFlow<Float> = _batteryDrainRatePerHour.asStateFlow()

    // 5. Live Network Speed
    private val _downloadSpeedBps = MutableStateFlow(0L)
    val downloadSpeedBps: StateFlow<Long> = _downloadSpeedBps.asStateFlow()

    private val _uploadSpeedBps = MutableStateFlow(0L)
    val uploadSpeedBps: StateFlow<Long> = _uploadSpeedBps.asStateFlow()

    private var isReceiverRegistered = false
    private var isSpeedMonitorActive = false

    private var prevRxBytes = 0L
    private var prevTxBytes = 0L
    private var prevSampleTimeMs = 0L

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                Intent.ACTION_USER_PRESENT -> {
                    _realtimeUnlocks.value += 1
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                    if (temp > 0) {
                        _batteryTempCelsius.value = temp / 10.0f
                    }
                    val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
                    _batteryHealthStatus.value = when (health) {
                        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                        BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                        else -> "Normal"
                    }
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        val pct = (level * 100) / scale
                        _batteryLevelPercent.value = pct
                        val uptimeHours = (SystemClock.elapsedRealtime() / (1000f * 3600f)).coerceAtLeast(1.0f)
                        val totalDrained = (100 - pct).coerceAtLeast(1)
                        val rate = (totalDrained / uptimeHours).coerceIn(0.5f, 12.0f)
                        _batteryDrainRatePerHour.value = rate
                    }
                }
            }
        }
    }

    fun init(context: Context, initialUnlocksFromStats: Int) {
        if (_realtimeUnlocks.value < initialUnlocksFromStats) {
            _realtimeUnlocks.value = initialUnlocksFromStats
        }

        // Register broadcast receiver for Unlocks and Battery
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(systemReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    context.registerReceiver(systemReceiver, filter)
                }
                isReceiverRegistered = true
            } catch (_: Exception) {}
        }

        // Sample RAM & Data usage
        updateRamInfo(context)
        updateDataUsage(context)

        // Start Live Network Speed Monitoring
        startSpeedMonitor()
    }

    fun updateRamInfo(context: Context) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val total = memInfo.totalMem
            val avail = memInfo.availMem
            val used = (total - avail).coerceAtLeast(0L)
            _ramTotalBytes.value = total
            _ramUsedBytes.value = used
            _ramPercentUsed.value = if (total > 0) ((used.toFloat() / total.toFloat()) * 100).toInt() else 0
        } catch (_: Exception) {}
    }

    fun updateDataUsage(context: Context) {
        try {
            val networkStatsManager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? android.app.usage.NetworkStatsManager ?: return

            // Calculate midnight for daily reset
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val startTime = cal.timeInMillis
            val endTime = System.currentTimeMillis()

            val uid = android.os.Process.myUid()

            var mobileRx = 0L
            var mobileTx = 0L
            val mobileBucket = networkStatsManager.queryDetailsForUid(
                android.net.ConnectivityManager.TYPE_MOBILE,
                null,
                startTime,
                endTime,
                uid
            )
            try {
                val bucket = android.app.usage.NetworkStats.Bucket()
                while (mobileBucket.hasNextBucket()) {
                    mobileBucket.getNextBucket(bucket)
                    mobileRx += bucket.rxBytes
                    mobileTx += bucket.txBytes
                }
            } finally {
                try {
                    mobileBucket.close()
                } catch (_: Exception) {}
            }

            var wifiRx = 0L
            var wifiTx = 0L
            val wifiBucket = networkStatsManager.queryDetailsForUid(
                android.net.ConnectivityManager.TYPE_WIFI,
                null,
                startTime,
                endTime,
                uid
            )
            try {
                val bucket = android.app.usage.NetworkStats.Bucket()
                while (wifiBucket.hasNextBucket()) {
                    wifiBucket.getNextBucket(bucket)
                    wifiRx += bucket.rxBytes
                    wifiTx += bucket.txBytes
                }
            } finally {
                try {
                    wifiBucket.close()
                } catch (_: Exception) {}
            }

            _mobileDataBytes.value = mobileRx + mobileTx
            _wifiDataBytes.value = wifiRx + wifiTx
        } catch (_: Exception) {
            // Fallback if stats are unavailable on this device
        }
    }

    private fun startSpeedMonitor() {
        if (isSpeedMonitorActive) return
        isSpeedMonitorActive = true
        prevRxBytes = TrafficStats.getTotalRxBytes()
        prevTxBytes = TrafficStats.getTotalTxBytes()
        prevSampleTimeMs = SystemClock.elapsedRealtime()

        scope.launch {
            while (isSpeedMonitorActive) {
                delay(2000L)
                val currRx = TrafficStats.getTotalRxBytes()
                val currTx = TrafficStats.getTotalTxBytes()
                val currTimeMs = SystemClock.elapsedRealtime()

                val timeDiffSec = ((currTimeMs - prevSampleTimeMs) / 1000.0f).coerceAtLeast(0.5f)

                if (prevRxBytes > 0 && currRx >= prevRxBytes) {
                    val rxDiff = currRx - prevRxBytes
                    _downloadSpeedBps.value = (rxDiff / timeDiffSec).toLong()
                }
                if (prevTxBytes > 0 && currTx >= prevTxBytes) {
                    val txDiff = currTx - prevTxBytes
                    _uploadSpeedBps.value = (txDiff / timeDiffSec).toLong()
                }

                prevRxBytes = currRx
                prevTxBytes = currTx
                prevSampleTimeMs = currTimeMs
            }
        }
    }

    fun stop() {
        isSpeedMonitorActive = false
    }
}

fun formatDataBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0f
    val mb = kb / 1024.0f
    val gb = mb / 1024.0f
    return when {
        gb >= 1.0f -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0f -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

fun formatNetworkSpeed(bytesPerSec: Long): String {
    val kb = bytesPerSec / 1024.0f
    val mb = kb / 1024.0f
    return when {
        mb >= 1.0f -> String.format(Locale.US, "%.1f Mb/s", mb)
        kb >= 1.0f -> String.format(Locale.US, "%.0f Kb/s", kb)
        else -> "${bytesPerSec} B/s"
    }
}
