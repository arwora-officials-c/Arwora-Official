package com.arwora.feature.system.wifi

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class WifiStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("wifi_controller_store", Context.MODE_PRIVATE)

    // Secure storage for router credentials
    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        
        EncryptedSharedPreferences.create(
            context,
            "secure_wifi_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _customLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    val customLabels: StateFlow<Map<String, String>> = _customLabels.asStateFlow()

    private val _knownMacs = MutableStateFlow<Set<String>>(emptySet())
    val knownMacs: StateFlow<Set<String>> = _knownMacs.asStateFlow()

    private val _alerts = MutableStateFlow<List<DeviceAlert>>(emptyList())
    val alerts: StateFlow<List<DeviceAlert>> = _alerts.asStateFlow()

    private val _alertsEnabled = MutableStateFlow(true)
    val alertsEnabled: StateFlow<Boolean> = _alertsEnabled.asStateFlow()

    private val _speedHistory = MutableStateFlow<List<SpeedTestRecord>>(emptyList())
    val speedHistory: StateFlow<List<SpeedTestRecord>> = _speedHistory.asStateFlow()

    private val _blockedMacs = MutableStateFlow<Set<String>>(emptySet())
    val blockedMacs: StateFlow<Set<String>> = _blockedMacs.asStateFlow()

    private val _firstSeenTimes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val firstSeenTimes: StateFlow<Map<String, Long>> = _firstSeenTimes.asStateFlow()

    init {
        loadCustomLabels()
        loadKnownMacs()
        loadAlerts()
        loadAlertsEnabled()
        loadSpeedHistory()
        loadBlockedMacs()
        loadFirstSeenTimes()
    }

    // --- Custom Device Labels ---
    private fun loadCustomLabels() {
        val raw = prefs.getString(KEY_CUSTOM_LABELS, "{}") ?: "{}"
        val map = mutableMapOf<String, String>()
        try {
            val json = JSONObject(raw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = json.optString(key)
            }
        } catch (_: Exception) {}
        _customLabels.value = map
    }

    fun setCustomLabel(mac: String, label: String) {
        val updated = _customLabels.value.toMutableMap()
        if (label.isBlank()) {
            updated.remove(mac)
        } else {
            updated[mac] = label.trim()
        }
        _customLabels.value = updated
        val json = JSONObject()
        updated.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_CUSTOM_LABELS, json.toString()).apply()
    }

    // --- Known MACs & Alerts ---
    private fun loadKnownMacs() {
        val raw = prefs.getStringSet(KEY_KNOWN_MACS, emptySet()) ?: emptySet()
        _knownMacs.value = raw.toSet()
    }

    private fun loadAlertsEnabled() {
        _alertsEnabled.value = prefs.getBoolean(KEY_ALERTS_ENABLED, true)
    }

    fun setAlertsEnabled(enabled: Boolean) {
        _alertsEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ALERTS_ENABLED, enabled).apply()
    }

    private fun loadAlerts() {
        val raw = prefs.getString(KEY_ALERTS, "[]") ?: "[]"
        val list = mutableListOf<DeviceAlert>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    DeviceAlert(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        mac = obj.optString("mac"),
                        ip = obj.optString("ip"),
                        vendor = obj.optString("vendor"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        message = obj.optString("message")
                    )
                )
            }
        } catch (_: Exception) {}
        _alerts.value = list
    }

    fun recordDiscoveredDevice(device: DeviceInfo): DeviceAlert? {
        val mac = device.mac
        if (mac.isBlank() || mac.equals("Unknown", ignoreCase = true)) return null

        val isKnown = _knownMacs.value.contains(mac)
        if (!isKnown) {
            val newKnown = _knownMacs.value.toMutableSet().apply { add(mac) }
            _knownMacs.value = newKnown
            prefs.edit().putStringSet(KEY_KNOWN_MACS, newKnown).apply()

            if (_alertsEnabled.value) {
                val alert = DeviceAlert(
                    id = UUID.randomUUID().toString(),
                    mac = mac,
                    ip = device.ip,
                    vendor = device.vendor,
                    timestamp = System.currentTimeMillis(),
                    message = "Unknown device connected: ${device.vendor} (${device.ip})"
                )
                val newAlerts = (_alerts.value.toMutableList().apply { add(0, alert) }).take(30)
                _alerts.value = newAlerts
                saveAlerts(newAlerts)
                return alert
            }
        }
        return null
    }

    fun clearAlerts() {
        _alerts.value = emptyList()
        prefs.edit().remove(KEY_ALERTS).apply()
    }

    private fun saveAlerts(list: List<DeviceAlert>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("mac", item.mac)
                    put("ip", item.ip)
                    put("vendor", item.vendor)
                    put("timestamp", item.timestamp)
                    put("message", item.message)
                }
            )
        }
        prefs.edit().putString(KEY_ALERTS, arr.toString()).apply()
    }

    // --- Speed Test History ---
    private fun loadSpeedHistory() {
        val raw = prefs.getString(KEY_SPEED_HISTORY, "[]") ?: "[]"
        val list = mutableListOf<SpeedTestRecord>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SpeedTestRecord(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        downloadMbps = obj.optDouble("downloadMbps", 0.0),
                        uploadMbps = obj.optDouble("uploadMbps", 0.0),
                        pingMs = obj.optLong("pingMs", 0),
                        jitterMs = obj.optLong("jitterMs", 0),
                        packetLossPercent = obj.optInt("packetLossPercent", 0),
                        networkSsid = obj.optString("networkSsid", "Wi-Fi")
                    )
                )
            }
        } catch (_: Exception) {}
        _speedHistory.value = list
    }

    fun addSpeedRecord(record: SpeedTestRecord) {
        val updated = (_speedHistory.value.toMutableList().apply { add(0, record) }).take(30)
        _speedHistory.value = updated
        val arr = JSONArray()
        updated.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("timestamp", r.timestamp)
                    put("downloadMbps", r.downloadMbps)
                    put("uploadMbps", r.uploadMbps)
                    put("pingMs", r.pingMs)
                    put("jitterMs", r.jitterMs)
                    put("packetLossPercent", r.packetLossPercent)
                    put("networkSsid", r.networkSsid)
                }
            )
        }
        prefs.edit().putString(KEY_SPEED_HISTORY, arr.toString()).apply()
    }

    fun clearSpeedHistory() {
        _speedHistory.value = emptyList()
        prefs.edit().remove(KEY_SPEED_HISTORY).apply()
    }

    fun deleteSpeedRecord(id: String) {
        val updated = _speedHistory.value.filter { it.id != id }
        _speedHistory.value = updated
        val arr = JSONArray()
        updated.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("timestamp", r.timestamp)
                    put("downloadMbps", r.downloadMbps)
                    put("uploadMbps", r.uploadMbps)
                    put("pingMs", r.pingMs)
                    put("jitterMs", r.jitterMs)
                    put("packetLossPercent", r.packetLossPercent)
                    put("networkSsid", r.networkSsid)
                }
            )
        }
        prefs.edit().putString(KEY_SPEED_HISTORY, arr.toString()).apply()
    }

    // --- Router Credentials (SSID-aware) ---
    fun hasRouterCredentials(ssid: String): Boolean {
        if (ssid.isBlank()) return false
        return securePrefs.contains("${KEY_ROUTER_PASSWORD}_$ssid")
    }

    fun saveRouterCredentials(ssid: String, username: String, password: String) {
        if (ssid.isBlank()) return
        securePrefs.edit()
            .putString("${KEY_ROUTER_USERNAME}_$ssid", username)
            .putString("${KEY_ROUTER_PASSWORD}_$ssid", password)
            .apply()
    }

    fun getRouterUsername(ssid: String): String = securePrefs.getString("${KEY_ROUTER_USERNAME}_$ssid", "admin") ?: "admin"
    fun getRouterPassword(ssid: String): String = securePrefs.getString("${KEY_ROUTER_PASSWORD}_$ssid", "") ?: ""

    fun clearRouterCredentials(ssid: String) {
        if (ssid.isBlank()) return
        securePrefs.edit()
            .remove("${KEY_ROUTER_USERNAME}_$ssid")
            .remove("${KEY_ROUTER_PASSWORD}_$ssid")
            .apply()
    }

    // --- Blocked MACs (Router / Access Control tracking) ---
    private fun loadBlockedMacs() {
        val raw = prefs.getStringSet(KEY_BLOCKED_MACS, emptySet()) ?: emptySet()
        _blockedMacs.value = raw.toSet()
    }

    fun toggleBlockMac(mac: String): Boolean {
        val current = _blockedMacs.value.toMutableSet()
        val isNowBlocked = if (current.contains(mac)) {
            current.remove(mac)
            false
        } else {
            current.add(mac)
            true
        }
        _blockedMacs.value = current
        prefs.edit().putStringSet(KEY_BLOCKED_MACS, current).apply()
        return isNowBlocked
    }

    // --- First Seen & Last Seen Tracking ---
    private fun loadFirstSeenTimes() {
        val raw = prefs.getString(KEY_FIRST_SEEN, "{}") ?: "{}"
        val map = mutableMapOf<String, Long>()
        try {
            val json = JSONObject(raw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = json.optLong(key)
            }
        } catch (_: Exception) {}
        _firstSeenTimes.value = map
    }

    fun getFirstSeenTime(key: String): Long? = _firstSeenTimes.value[key]

    fun setFirstSeenTime(key: String, time: Long) {
        val updated = _firstSeenTimes.value.toMutableMap()
        updated[key] = time
        _firstSeenTimes.value = updated
        saveFirstSeenTimes(updated)
    }

    fun clearFirstSeenTime(key: String) {
        val updated = _firstSeenTimes.value.toMutableMap()
        updated.remove(key)
        _firstSeenTimes.value = updated
        saveFirstSeenTimes(updated)
    }

    private fun saveFirstSeenTimes(map: Map<String, Long>) {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_FIRST_SEEN, json.toString()).apply()
    }

    fun getLastSeenTime(key: String): Long = prefs.getLong("${KEY_LAST_SEEN}_$key", 0L)
    fun setLastSeenTime(key: String, time: Long) = prefs.edit().putLong("${KEY_LAST_SEEN}_$key", time).apply()

    companion object {
        private const val KEY_CUSTOM_LABELS = "custom_labels"
        private const val KEY_KNOWN_MACS = "known_macs"
        private const val KEY_ALERTS = "device_alerts"
        private const val KEY_ALERTS_ENABLED = "alerts_enabled"
        private const val KEY_SPEED_HISTORY = "speed_history"
        private const val KEY_BLOCKED_MACS = "blocked_macs"
        private const val KEY_ROUTER_USERNAME = "router_user"
        private const val KEY_ROUTER_PASSWORD = "router_pass"
        private const val KEY_FIRST_SEEN = "first_seen_times"
        private const val KEY_LAST_SEEN = "last_seen_time"
    }
}
