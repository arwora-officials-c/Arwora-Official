package com.arwora.feature.system.wifi

sealed interface DeviceEvent {
    data class OnHostFound(val ip: String, val mac: String?, val latencyMs: Long?) : DeviceEvent
    data class OnNameResolved(val ip: String, val friendlyName: String, val source: String) : DeviceEvent
    data class OnMacResolved(val ip: String, val mac: String) : DeviceEvent
    data class OnLatencyUpdated(val ip: String, val latencyMs: Long) : DeviceEvent
    data class OnProgress(val scanned: Int, val total: Int) : DeviceEvent
    data class OnDone(val summary: String) : DeviceEvent
}

enum class ScanMode {
    FAST, DEEP
}

data class NetworkInfo(
    val ssid: String,
    val bssid: String,
    val localIp: String,
    val gateway: String,
    val subnetCidr: Int,
    val dnsServers: List<String>,
    val linkSpeedMbps: Int,
    val isConnected: Boolean,
    val isWifiConnected: Boolean = false,
    val isMobileDataConnected: Boolean = false,
    val frequency: Int = 0,
    val rssi: Int = -127
)

data class DeviceInfo(
    val ip: String,
    val mac: String,
    val vendor: String,
    val friendlyName: String?,
    val customLabel: String? = null,
    val hostname: String?,
    val nameSource: String?,
    val latencyMs: Long?,
    val openPorts: List<Int> = emptyList(),
    val lastSeenTime: Long = System.currentTimeMillis(),
    val downloadUsageBytes: Long = 0L,
    val uploadUsageBytes: Long = 0L,
    val isBlocked: Boolean = false,
    
    // New fields for detailed display
    val band: String? = null,
    val ssid: String? = null,
    val rssi: Int? = null,
    val signalQuality: String? = null,
    val channel: Int? = null,
    val connectionDuration: String? = null,
    val linkSpeedMbps: Int? = null,
    val accessSN: String? = null,
    val accessName: String? = null,
    val estimatedDistanceMeters: Double? = null
)

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val frequencyMhz: Int,
    val channel: Int,
    val capabilities: String,
    val isHidden: Boolean = false,
    val distanceMeters: Double = 0.0,
    val securityType: String = "Open",
    val signalPercent: Int = 0
)

data class DeviceAlert(
    val id: String = java.util.UUID.randomUUID().toString(),
    val mac: String,
    val ip: String,
    val vendor: String,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String
)

data class SpeedTestRecord(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val downloadMbps: Double,
    val uploadMbps: Double,
    val pingMs: Long,
    val jitterMs: Long,
    val packetLossPercent: Int,
    val networkSsid: String
)

data class WifiSignalInfo(
    val rssiDbm: Int = -60,
    val signalPercent: Int = 75,
    val qualityLabel: String = "Good",
    val frequencyMhz: Int = 2437,
    val channel: Int = 6,
    val band: String = "2.4 GHz",
    val standard: String = "Wi-Fi 4 / 5",
    val isDeadZone: Boolean = false
)

data class ChannelCongestion(
    val channel: Int,
    val frequencyMhz: Int,
    val band: String,
    val congestionScore: Int,
    val congestionLevel: String, // "Low", "Moderate", "High"
    val isCurrent: Boolean,
    val isRecommended: Boolean
)

data class GatewayPingResult(
    val gatewayIp: String,
    val isReachable: Boolean,
    val minLatencyMs: Long,
    val avgLatencyMs: Long,
    val maxLatencyMs: Long,
    val packetLossPercent: Int,
    val stabilityStatus: String,
    val timestamp: Long = System.currentTimeMillis()
)

