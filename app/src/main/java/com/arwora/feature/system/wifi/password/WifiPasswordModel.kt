package com.arwora.feature.system.wifi.password

import androidx.compose.runtime.Immutable

/**
 * Security type classification for Wi-Fi networks.
 */
enum class WifiSecurityType(val displayName: String, val badge: String) {
    WPA3("WPA3-Personal", "WPA3"),
    WPA2_WPA3("WPA2/WPA3 Mixed", "WPA2/3"),
    WPA2("WPA2-PSK", "WPA2"),
    WPA("WPA-PSK", "WPA"),
    WEP("WEP", "WEP"),
    OPEN("Open / No Password", "OPEN"),
    EAP("WPA-Enterprise (802.1x)", "EAP");

    companion object {
        fun fromString(value: String?): WifiSecurityType {
            if (value == null) return WPA2
            val upper = value.uppercase()
            return when {
                upper.contains("WPA3") && upper.contains("WPA2") -> WPA2_WPA3
                upper.contains("WPA3") || upper.contains("SAE") -> WPA3
                upper.contains("WPA2") -> WPA2
                upper.contains("WPA") || upper.contains("PSK") -> WPA
                upper.contains("WEP") -> WEP
                upper.contains("EAP") || upper.contains("ENTERPRISE") -> EAP
                upper.contains("OPEN") || upper.contains("NONE") -> OPEN
                else -> WPA2
            }
        }
    }
}

/**
 * Represents a saved / previously connected Wi-Fi network and its password credentials.
 */
@Immutable
data class SavedWifiNetwork(
    val id: String,
    val ssid: String,
    val password: String,
    val securityType: WifiSecurityType = WifiSecurityType.WPA2,
    val networkId: Int = -1,
    val bssid: String? = null,
    val isCurrentlyConnected: Boolean = false,
    val lastConnectedTimestamp: Long = System.currentTimeMillis(),
    val signalDbm: Int? = null,
    val isHiddenSsid: Boolean = false,
    val source: WifiSource = WifiSource.SYSTEM
)

enum class WifiSource {
    SYSTEM,
    SHIZUKU_STORE,
    LOCAL_VAULT,
    MANUAL_IMPORT
}
