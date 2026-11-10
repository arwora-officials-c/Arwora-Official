package com.arwora.feature.system.battery.model

data class BatteryAppStat(
    val packageName: String,
    val appName: String,
    val batteryPercentage: Float, // e.g. 14.2%
    val mahConsumed: Int, // e.g. 340 mAh
    val cpuTimeMinutes: Int, // e.g. 45 min
    val backgroundUsageMinutes: Int, // e.g. 120 min
    val status: BatteryDrainStatus
)

enum class BatteryDrainStatus {
    NORMAL, HIGH_DRAIN, BACKGROUND_HOG, OPTIMIZED
}

data class DeepSleepDiagnostics(
    val isDozeModeActive: Boolean,
    val activeWakeLocksCount: Int,
    val pendingAlarmsCount: Int,
    val deepSleepPercentage: Int, // e.g. 84%
    val cpuState: CpuState
)

enum class CpuState {
    DEEP_SLEEP, LIGHT_SLEEP, AWAKE, BOOSTED
}
