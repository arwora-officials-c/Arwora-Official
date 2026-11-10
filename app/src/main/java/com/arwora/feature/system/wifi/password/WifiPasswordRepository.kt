package com.arwora.feature.system.wifi.password

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.arwora.feature.system.shell.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class WifiPasswordRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val prefs: SharedPreferences = appContext.getSharedPreferences("wifi_password_vault", Context.MODE_PRIVATE)

    private val _networks = MutableStateFlow<List<SavedWifiNetwork>>(emptyList())
    val networks: StateFlow<List<SavedWifiNetwork>> = _networks.asStateFlow()

    private val _isElevatedModeAvailable = MutableStateFlow(false)
    val isElevatedModeAvailable: StateFlow<Boolean> = _isElevatedModeAvailable.asStateFlow()

    companion object {
        private const val TAG = "WifiPasswordRepo"
        private const val PREFS_KEY_SAVED_NETWORKS = "saved_networks_json"
    }

    init {
        loadCachedVault()
    }

    /**
     * Refreshes the saved Wi-Fi networks from System WifiManager, Shizuku/Root store,
     * and the local persistent vault.
     */
    suspend fun refreshSavedNetworks() = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, SavedWifiNetwork>()

        // 1. Check current Wi-Fi connection info
        val currentInfo = wifiManager?.connectionInfo
        val currentSsid = currentInfo?.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" && it.isNotBlank() }

        // 2. Load from local persistent vault first
        val vaultList = loadVaultFromPrefs()
        for (item in vaultList) {
            results[item.ssid] = item
        }

        // 3. Try reading from WifiManager configuredNetworks
        try {
            @Suppress("DEPRECATION")
            val configuredList = wifiManager?.configuredNetworks
            if (configuredList != null && configuredList.isNotEmpty()) {
                for (config in configuredList) {
                    val rawSsid = config.SSID ?: continue
                    val cleanSsid = rawSsid.removeSurrounding("\"")
                    if (cleanSsid.isBlank()) continue

                    val rawPsk = config.preSharedKey?.removeSurrounding("\"")
                    val secType = determineSecurityType(config)
                    val isConnected = cleanSsid == currentSsid

                    val existing = results[cleanSsid]
                    val effectivePassword = when {
                        !rawPsk.isNullOrBlank() && rawPsk != "*" -> rawPsk
                        existing != null && existing.password.isNotBlank() -> existing.password
                        secType == WifiSecurityType.OPEN -> ""
                        else -> "WPA2-Protected"
                    }

                    results[cleanSsid] = SavedWifiNetwork(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        ssid = cleanSsid,
                        password = effectivePassword,
                        securityType = secType,
                        networkId = config.networkId,
                        bssid = config.BSSID,
                        isCurrentlyConnected = isConnected,
                        lastConnectedTimestamp = existing?.lastConnectedTimestamp ?: System.currentTimeMillis(),
                        source = WifiSource.SYSTEM
                    )
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Cannot read configuredNetworks directly: ${e.message}")
        }

        // 4. Try Shizuku / Root elevated read to extract plaintext passwords from WifiConfigStore.xml
        try {
            val hasShizuku = ShellExecutor.checkShizukuStatus(appContext).name == "READY"
            _isElevatedModeAvailable.value = hasShizuku

            if (hasShizuku) {
                val paths = listOf(
                    "/data/misc/wifi/WifiConfigStore.xml",
                    "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml",
                    "/data/misc/wifi/wpa_supplicant.conf"
                )
                for (path in paths) {
                    val cmdResult = ShellExecutor.executeCommand("cat $path 2>/dev/null")
                    if (cmdResult.exitCode == 0 && cmdResult.stdout.isNotBlank()) {
                        parseWifiConfigStoreXml(cmdResult.stdout, results, currentSsid)
                        break
                    }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Shizuku inspection error: ${e.message}")
        }

        // If vault and system sources are empty, keep empty state awaiting root/Shizuku or user-added networks

        // Update current connection status on all items
        val finalList = results.values.map { net ->
            if (net.ssid == currentSsid) net.copy(isCurrentlyConnected = true) else net
        }.sortedWith(
            compareByDescending<SavedWifiNetwork> { it.isCurrentlyConnected }
                .thenBy { it.ssid.lowercase() }
        )

        // Save back to persistent vault
        saveVaultToPrefs(finalList)
        _networks.value = finalList
    }

    /**
     * Parses WifiConfigStore.xml content to extract SSIDs and PreSharedKeys
     */
    private fun parseWifiConfigStoreXml(
        xmlContent: String,
        results: MutableMap<String, SavedWifiNetwork>,
        currentSsid: String?
    ) {
        try {
            val networkBlocks = xmlContent.split("<Network>")
            for (block in networkBlocks) {
                if (!block.contains("</Network>")) continue
                val ssidMatch = Regex("""<string name="SSID">&quot;(.*?)&quot;</string>""").find(block)
                    ?: Regex("""<string name="SSID">"(.*?)"</string>""").find(block)
                val ssid = ssidMatch?.groupValues?.get(1) ?: continue

                val pskMatch = Regex("""<string name="PreSharedKey">&quot;(.*?)&quot;</string>""").find(block)
                    ?: Regex("""<string name="PreSharedKey">"(.*?)"</string>""").find(block)
                val psk = pskMatch?.groupValues?.get(1) ?: ""

                val secType = when {
                    block.contains("WPA_PSK") || block.contains("WPA2_PSK") -> WifiSecurityType.WPA2
                    block.contains("SAE") || block.contains("WPA3") -> WifiSecurityType.WPA3
                    block.contains("NONE") || psk.isBlank() -> WifiSecurityType.OPEN
                    else -> WifiSecurityType.WPA2
                }

                val existing = results[ssid]
                results[ssid] = SavedWifiNetwork(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    ssid = ssid,
                    password = if (psk.isNotBlank()) psk else existing?.password ?: "",
                    securityType = secType,
                    networkId = existing?.networkId ?: -1,
                    isCurrentlyConnected = ssid == currentSsid,
                    lastConnectedTimestamp = existing?.lastConnectedTimestamp ?: System.currentTimeMillis(),
                    source = WifiSource.SHIZUKU_STORE
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse WifiConfigStore.xml", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun determineSecurityType(config: WifiConfiguration): WifiSecurityType {
        return try {
            val keyMgmt = config.allowedKeyManagement
            when {
                keyMgmt.get(WifiConfiguration.KeyMgmt.WPA_PSK) -> WifiSecurityType.WPA2
                keyMgmt.get(WifiConfiguration.KeyMgmt.WPA_EAP) -> WifiSecurityType.EAP
                keyMgmt.get(WifiConfiguration.KeyMgmt.NONE) -> WifiSecurityType.OPEN
                else -> WifiSecurityType.WPA2
            }
        } catch (_: Exception) {
            WifiSecurityType.WPA2
        }
    }

    suspend fun saveOrUpdateNetwork(network: SavedWifiNetwork) = withContext(Dispatchers.IO) {
        val currentList = _networks.value.toMutableList()
        val index = currentList.indexOfFirst { it.ssid.equals(network.ssid, ignoreCase = true) }
        if (index >= 0) {
            currentList[index] = network
        } else {
            currentList.add(network)
        }
        val sorted = currentList.sortedWith(
            compareByDescending<SavedWifiNetwork> { it.isCurrentlyConnected }
                .thenBy { it.ssid.lowercase() }
        )
        saveVaultToPrefs(sorted)
        _networks.value = sorted
    }

    suspend fun forgetNetwork(network: SavedWifiNetwork): Boolean = withContext(Dispatchers.IO) {
        var forgottenSystem = false
        try {
            // Remove from WifiManager if possible
            if (network.networkId >= 0 && wifiManager != null) {
                @Suppress("DEPRECATION")
                forgottenSystem = wifiManager.removeNetwork(network.networkId)
                @Suppress("DEPRECATION")
                wifiManager.saveConfiguration()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove network from WifiManager: ${e.message}")
        }

        // Also try shell command if Shizuku is active
        try {
            if (_isElevatedModeAvailable.value) {
                ShellExecutor.executeCommand("cmd -w wifi forget-network \"${network.ssid}\"")
            }
        } catch (_: Exception) {}

        // Remove from local persistent vault
        val currentList = _networks.value.filterNot { it.id == network.id || it.ssid == network.ssid }
        saveVaultToPrefs(currentList)
        _networks.value = currentList
        true
    }

    suspend fun forgetAllNetworks(): Boolean = withContext(Dispatchers.IO) {
        val all = _networks.value
        for (net in all) {
            try {
                if (net.networkId >= 0 && wifiManager != null) {
                    @Suppress("DEPRECATION")
                    wifiManager.removeNetwork(net.networkId)
                }
            } catch (_: Exception) {}
        }
        try {
            @Suppress("DEPRECATION")
            wifiManager?.saveConfiguration()
        } catch (_: Exception) {}

        saveVaultToPrefs(emptyList())
        _networks.value = emptyList()
        true
    }

    fun exportNetworksJson(): String {
        val list = _networks.value
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("ssid", item.ssid)
                put("password", item.password)
                put("securityType", item.securityType.name)
                put("bssid", item.bssid ?: "")
            }
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    suspend fun importNetworks(input: String): Int = withContext(Dispatchers.IO) {
        var count = 0
        val currentList = _networks.value.toMutableList()
        try {
            if (input.trim().startsWith("[")) {
                val jsonArray = JSONArray(input.trim())
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val ssid = obj.optString("ssid", "").trim()
                    if (ssid.isNotBlank()) {
                        val pass = obj.optString("password", "")
                        val sec = WifiSecurityType.fromString(obj.optString("securityType", "WPA2"))
                        val existingIndex = currentList.indexOfFirst { it.ssid.equals(ssid, ignoreCase = true) }
                        val net = SavedWifiNetwork(
                            id = UUID.randomUUID().toString(),
                            ssid = ssid,
                            password = pass,
                            securityType = sec,
                            source = WifiSource.MANUAL_IMPORT
                        )
                        if (existingIndex >= 0) {
                            currentList[existingIndex] = net
                        } else {
                            currentList.add(net)
                        }
                        count++
                    }
                }
            } else {
                // Parse line by line: SSID:Password or SSID,Password
                val lines = input.lines()
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isBlank() || trimmed.startsWith("#")) continue
                    val parts = when {
                        trimmed.contains(":") -> trimmed.split(":", limit = 2)
                        trimmed.contains(",") -> trimmed.split(",", limit = 2)
                        trimmed.contains("\t") -> trimmed.split("\t", limit = 2)
                        else -> null
                    }
                    if (parts != null && parts[0].isNotBlank()) {
                        val ssid = parts[0].trim()
                        val pass = if (parts.size > 1) parts[1].trim() else ""
                        val net = SavedWifiNetwork(
                            id = UUID.randomUUID().toString(),
                            ssid = ssid,
                            password = pass,
                            securityType = if (pass.isEmpty()) WifiSecurityType.OPEN else WifiSecurityType.WPA2,
                            source = WifiSource.MANUAL_IMPORT
                        )
                        val existingIndex = currentList.indexOfFirst { it.ssid.equals(ssid, ignoreCase = true) }
                        if (existingIndex >= 0) {
                            currentList[existingIndex] = net
                        } else {
                            currentList.add(net)
                        }
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import networks: ${e.message}", e)
        }

        if (count > 0) {
            val sorted = currentList.sortedWith(
                compareByDescending<SavedWifiNetwork> { it.isCurrentlyConnected }
                    .thenBy { it.ssid.lowercase() }
            )
            saveVaultToPrefs(sorted)
            _networks.value = sorted
        }
        count
    }

    suspend fun connectToNetwork(network: SavedWifiNetwork): Boolean = withContext(Dispatchers.IO) {
        var success = false
        try {
            if (network.networkId >= 0 && wifiManager != null) {
                @Suppress("DEPRECATION")
                success = wifiManager.enableNetwork(network.networkId, true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiManager enableNetwork failed: ${e.message}")
        }

        // Try elevated shell connect if available
        if (!success && _isElevatedModeAvailable.value) {
            try {
                val res = ShellExecutor.executeCommand("cmd -w wifi connect-network \"${network.ssid}\"")
                if (res.exitCode == 0) success = true
            } catch (_: Exception) {}
        }

        // Launch system Wi-Fi settings if direct connect fails
        if (!success) {
            withContext(Dispatchers.Main) {
                try {
                    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    appContext.startActivity(intent)
                    success = true
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot open Wi-Fi settings: ${e.message}")
                }
            }
        }
        success
    }

    private fun loadCachedVault() {
        _networks.value = loadVaultFromPrefs()
    }

    private fun loadVaultFromPrefs(): List<SavedWifiNetwork> {
        val jsonStr = prefs.getString(PREFS_KEY_SAVED_NETWORKS, null) ?: return emptyList()
        val list = mutableListOf<SavedWifiNetwork>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    SavedWifiNetwork(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        ssid = obj.optString("ssid", ""),
                        password = obj.optString("password", ""),
                        securityType = WifiSecurityType.fromString(obj.optString("securityType", "WPA2")),
                        networkId = obj.optInt("networkId", -1),
                        bssid = obj.optString("bssid", null),
                        isCurrentlyConnected = obj.optBoolean("isCurrentlyConnected", false),
                        lastConnectedTimestamp = obj.optLong("lastConnectedTimestamp", System.currentTimeMillis()),
                        source = WifiSource.valueOf(obj.optString("source", WifiSource.LOCAL_VAULT.name))
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved networks from JSON", e)
        }
        return list
    }

    private fun saveVaultToPrefs(list: List<SavedWifiNetwork>) {
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("ssid", item.ssid)
                    put("password", item.password)
                    put("securityType", item.securityType.name)
                    put("networkId", item.networkId)
                    if (item.bssid != null) put("bssid", item.bssid)
                    put("isCurrentlyConnected", item.isCurrentlyConnected)
                    put("lastConnectedTimestamp", item.lastConnectedTimestamp)
                    put("source", item.source.name)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(PREFS_KEY_SAVED_NETWORKS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving networks to JSON", e)
        }
    }
}
