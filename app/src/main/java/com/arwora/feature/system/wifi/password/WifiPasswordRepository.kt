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
import com.arwora.utils.ShizukuConnectionManager
import com.arwora.utils.ShizukuConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.UUID

class WifiPasswordRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val prefs: SharedPreferences = appContext.getSharedPreferences("wifi_password_vault", Context.MODE_PRIVATE)

    private val _networks = MutableStateFlow<List<SavedWifiNetwork>>(emptyList())
    val networks: StateFlow<List<SavedWifiNetwork>> = _networks.asStateFlow()

    private val _isElevatedModeAvailable = MutableStateFlow(false)
    val isElevatedModeAvailable: StateFlow<Boolean> = _isElevatedModeAvailable.asStateFlow()

    private val _shizukuState = MutableStateFlow(ShizukuConnectionManager.getConnectionState(appContext))
    val shizukuState: StateFlow<ShizukuConnectionState> = _shizukuState.asStateFlow()

    companion object {
        private const val TAG = "WifiPasswordRepo"
        private const val PREFS_KEY_SAVED_NETWORKS = "saved_networks_json"
    }

    init {
        loadCachedVault()
    }

    fun refreshShizukuState(): ShizukuConnectionState {
        val state = ShizukuConnectionManager.getConnectionState(appContext)
        _shizukuState.value = state
        _isElevatedModeAvailable.value = (state == ShizukuConnectionState.CONNECTED)
        return state
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
                        existing != null && existing.password.isNotBlank() && existing.password != "WPA2-Protected" -> existing.password
                        else -> ""
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

        // 4. Query elevated system Wi-Fi stores and dumps via Shizuku / ADB
        try {
            val connectionState = refreshShizukuState()
            if (connectionState == ShizukuConnectionState.CONNECTED) {
                var parsedXmlOrSupplicant = false
                val storePaths = listOf(
                    "/data/misc/wifi/WifiConfigStore.xml",
                    "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml",
                    "/data/misc/wifi/wpa_supplicant.conf"
                )
                for (path in storePaths) {
                    var cmdResult = ShellExecutor.executeCommand("cat $path 2>/dev/null")
                    if (cmdResult.exitCode != 0 || cmdResult.stdout.isBlank()) {
                        // On rooted devices running Shizuku in shell mode, attempt su fallback
                        cmdResult = ShellExecutor.executeCommand("su -c 'cat $path' 2>/dev/null")
                    }
                    if (cmdResult.exitCode == 0 && cmdResult.stdout.isNotBlank()) {
                        val content = cmdResult.stdout
                        val parsedCount = if (path.endsWith(".xml")) {
                            parseWifiConfigStoreXml(content, results, currentSsid)
                        } else {
                            parseWpaSupplicantConf(content, results, currentSsid)
                        }
                        if (parsedCount > 0) {
                            parsedXmlOrSupplicant = true
                            break
                        }
                    }
                }

                // ADB shell (UID 2000) fallback/supplement: cmd wifi list-networks & dumpsys wifi
                val listNetworksResult = ShellExecutor.executeCommand("cmd wifi list-networks 2>/dev/null")
                if (listNetworksResult.stdout.isNotBlank()) {
                    parseCmdWifiListNetworks(listNetworksResult.stdout, results, currentSsid)
                }

                val dumpsysResult = ShellExecutor.executeCommand("dumpsys wifi 2>/dev/null")
                if (dumpsysResult.stdout.isNotBlank()) {
                    parseDumpsysWifi(dumpsysResult.stdout, results, currentSsid)
                }

                if (!parsedXmlOrSupplicant) {
                    Log.d(TAG, "WifiConfigStore.xml restricted in ADB shell; populated from cmd wifi / dumpsys wifi")
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Shizuku inspection error: ${e.message}")
        }

        // Clean any legacy synthetic placeholder password labels from old vault entries
        val sanitizedResults = results.values.map { net ->
            val cleanedPass = if (net.password == "WPA2-Protected") "" else net.password
            val isCurrent = if (net.ssid == currentSsid) true else net.isCurrentlyConnected
            net.copy(password = cleanedPass, isCurrentlyConnected = isCurrent)
        }

        // Update current connection status on all items
        val finalList = sanitizedResults.sortedWith(
            compareByDescending<SavedWifiNetwork> { it.isCurrentlyConnected }
                .thenBy { it.ssid.lowercase() }
        )

        // Save back to persistent vault
        saveVaultToPrefs(finalList)
        _networks.value = finalList
    }

    /**
     * Parses WifiConfigStore.xml content using XmlPullParser (with regex fallback)
     * to extract SSIDs, PreSharedKeys, networkIds, and security types from
     * <Network>, <WifiConfiguration>, or <WiFiConfiguration> blocks.
     */
    internal fun parseWifiConfigStoreXml(
        xmlContent: String,
        results: MutableMap<String, SavedWifiNetwork>,
        currentSsid: String?
    ): Int {
        var extractedCount = 0
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlContent))

            var inNetworkBlock = false
            var inWifiConfigBlock = false
            var currentTag: String? = null
            var currentAttrName: String? = null

            var ssid: String? = null
            var preSharedKey: String? = null
            var keyMgmtHint: String? = null
            var wepKey: String? = null
            var isEnterprise = false
            var hiddenSsid = false

            fun commitCurrentEntry() {
                val cleanSsid = ssid?.removeSurrounding("\"")?.trim().orEmpty()
                if (cleanSsid.isBlank()) return
                val cleanPsk = preSharedKey?.removeSurrounding("\"")?.takeUnless { it == "*" }
                    ?: wepKey?.removeSurrounding("\"")?.takeUnless { it == "*" }
                    ?: ""

                val secType = when {
                    isEnterprise -> WifiSecurityType.EAP
                    keyMgmtHint?.contains("SAE", ignoreCase = true) == true ||
                        keyMgmtHint?.contains("WPA3", ignoreCase = true) == true -> WifiSecurityType.WPA3
                    keyMgmtHint?.contains("WPA2", ignoreCase = true) == true -> WifiSecurityType.WPA2
                    keyMgmtHint?.contains("WPA", ignoreCase = true) == true -> WifiSecurityType.WPA2
                    !wepKey.isNullOrBlank() -> WifiSecurityType.WEP
                    keyMgmtHint?.contains("NONE", ignoreCase = true) == true && cleanPsk.isBlank() -> WifiSecurityType.OPEN
                    cleanPsk.isNotBlank() -> WifiSecurityType.WPA2
                    else -> WifiSecurityType.OPEN
                }

                mergeExtractedNetwork(
                    results = results,
                    ssid = cleanSsid,
                    password = cleanPsk,
                    securityType = secType,
                    networkId = -1,
                    isHiddenSsid = hiddenSsid,
                    currentSsid = currentSsid,
                    source = WifiSource.SHIZUKU_STORE
                )
                extractedCount++
            }

            fun resetEntryState() {
                ssid = null
                preSharedKey = null
                keyMgmtHint = null
                wepKey = null
                isEnterprise = false
                hiddenSsid = false
            }

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tagName = parser.name
                        currentTag = tagName
                        currentAttrName = parser.getAttributeValue(null, "name")

                        when {
                            tagName.equals("Network", ignoreCase = true) -> {
                                inNetworkBlock = true
                                resetEntryState()
                            }
                            tagName.equals("WifiConfiguration", ignoreCase = true) -> {
                                inWifiConfigBlock = true
                                if (!inNetworkBlock) {
                                    resetEntryState()
                                }
                            }
                            tagName.equals("WifiEnterpriseConfiguration", ignoreCase = true) -> {
                                isEnterprise = true
                            }
                            tagName.equals("boolean", ignoreCase = true) &&
                                currentAttrName.equals("HiddenSSID", ignoreCase = true) -> {
                                hiddenSsid = parser.getAttributeValue(null, "value")
                                    ?.equals("true", ignoreCase = true) == true
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim().orEmpty()
                        if ((inNetworkBlock || inWifiConfigBlock) && text.isNotEmpty()) {
                            when (currentAttrName) {
                                "SSID" -> ssid = text
                                "PreSharedKey" -> preSharedKey = text
                                "WEPKeys" -> if (text.isNotBlank() && text != "null") wepKey = text
                                "ConfigKey" -> keyMgmtHint = (keyMgmtHint ?: "") + " " + text
                            }
                            if (currentTag.equals("item", ignoreCase = true) && text != "null" && wepKey == null) {
                                // Some Android builds store WEP keys inside <string-array name="WEPKeys"><item>...</item>
                                if (text.length in 5..32 && preSharedKey == null && ssid != null) {
                                    wepKey = text
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        val tagName = parser.name
                        when {
                            tagName.equals("Network", ignoreCase = true) -> {
                                commitCurrentEntry()
                                inNetworkBlock = false
                                inWifiConfigBlock = false
                                resetEntryState()
                            }
                            tagName.equals("WifiConfiguration", ignoreCase = true) -> {
                                if (!inNetworkBlock) {
                                    commitCurrentEntry()
                                    resetEntryState()
                                }
                                inWifiConfigBlock = false
                            }
                        }
                        if (tagName.equals("string", ignoreCase = true) ||
                            tagName.equals("byte-array", ignoreCase = true) ||
                            tagName.equals("int-array", ignoreCase = true)
                        ) {
                            currentAttrName = null
                        }
                        currentTag = null
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "XmlPullParser fallback triggered for WifiConfigStore.xml: ${e.message}")
            extractedCount += parseWifiConfigStoreRegexFallback(xmlContent, results, currentSsid)
        }

        // If XML had non-standard formatting that yielded 0 entries via pull parser, try regex fallback
        if (extractedCount == 0 && (xmlContent.contains("<Network>") || xmlContent.contains("WifiConfiguration", ignoreCase = true))) {
            extractedCount += parseWifiConfigStoreRegexFallback(xmlContent, results, currentSsid)
        }
        return extractedCount
    }

    private fun parseWifiConfigStoreRegexFallback(
        xmlContent: String,
        results: MutableMap<String, SavedWifiNetwork>,
        currentSsid: String?
    ): Int {
        var count = 0
        val delimiter = when {
            xmlContent.contains("<Network>") -> "<Network>"
            xmlContent.contains("<WiFiConfiguration>") -> "<WiFiConfiguration>"
            else -> "<WifiConfiguration>"
        }
        for (block in xmlContent.split(delimiter)) {
            val ssidMatch = Regex("""<string name="SSID">(?:&quot;|")(.*?)(?:&quot;|")</string>""").find(block)
                ?: Regex("""<string name="SSID">([^<]+)</string>""").find(block)
            val rawSsid = ssidMatch?.groupValues?.get(1)?.trim() ?: continue
            val ssid = decodeXmlEntities(rawSsid).removeSurrounding("\"")
            if (ssid.isBlank()) continue

            val pskMatch = Regex("""<string name="PreSharedKey">(?:&quot;|")(.*?)(?:&quot;|")</string>""").find(block)
                ?: Regex("""<string name="PreSharedKey">([^<]+)</string>""").find(block)
            val psk = decodeXmlEntities(pskMatch?.groupValues?.get(1)?.trim().orEmpty()).removeSurrounding("\"")

            val secType = when {
                block.contains("WifiEnterpriseConfiguration", ignoreCase = true) -> WifiSecurityType.EAP
                block.contains("SAE", ignoreCase = true) || block.contains("WPA3", ignoreCase = true) -> WifiSecurityType.WPA3
                block.contains("WPA_PSK", ignoreCase = true) || block.contains("WPA2_PSK", ignoreCase = true) -> WifiSecurityType.WPA2
                block.contains("NONE", ignoreCase = true) || psk.isBlank() -> WifiSecurityType.OPEN
                else -> WifiSecurityType.WPA2
            }

            mergeExtractedNetwork(
                results = results,
                ssid = ssid,
                password = psk,
                securityType = secType,
                networkId = -1,
                isHiddenSsid = false,
                currentSsid = currentSsid,
                source = WifiSource.SHIZUKU_STORE
            )
            count++
        }
        return count
    }

    internal fun parseWpaSupplicantConf(
        confContent: String,
        results: MutableMap<String, SavedWifiNetwork>,
        currentSsid: String?
    ): Int {
        var count = 0
        val blockRegex = Regex("""network\s*=\s*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL)
        for (match in blockRegex.findAll(confContent)) {
            val body = match.groupValues[1]
            val ssid = Regex("""ssid\s*=\s*"([^"]+)"""").find(body)?.groupValues?.get(1)?.trim() ?: continue
            val psk = Regex("""psk\s*=\s*"([^"]+)"""").find(body)?.groupValues?.get(1)?.trim() ?: ""
            val keyMgmt = Regex("""key_mgmt\s*=\s*([^\s\n]+)""").find(body)?.groupValues?.get(1)?.trim()
            val secType = when {
                keyMgmt?.contains("EAP", ignoreCase = true) == true -> WifiSecurityType.EAP
                keyMgmt?.contains("SAE", ignoreCase = true) == true -> WifiSecurityType.WPA3
                keyMgmt?.contains("NONE", ignoreCase = true) == true && psk.isBlank() -> WifiSecurityType.OPEN
                psk.isNotBlank() -> WifiSecurityType.WPA2
                else -> WifiSecurityType.OPEN
            }
            mergeExtractedNetwork(
                results = results,
                ssid = ssid,
                password = psk,
                securityType = secType,
                networkId = -1,
                isHiddenSsid = body.contains("scan_ssid=1"),
                currentSsid = currentSsid,
                source = WifiSource.SHIZUKU_STORE
            )
            count++
        }
        return count
    }

    internal fun parseCmdWifiListNetworks(
        output: String,
        results: MutableMap<String, SavedWifiNetwork>,
        currentSsid: String?
    ): Int {
        var count = 0
        val rowRegex = Regex("""^\s*(\d+)\s+(.+?)\s{2,}([a-zA-Z0-9/_-]+)\s*$""")
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("Network Id", ignoreCase = true)) continue
            val match = rowRegex.find(line) ?: continue
            val networkId = match.groupValues[1].toIntOrNull() ?: -1
            val ssid = match.groupValues[2].trim().removeSurrounding("\"")
            val secRaw = match.groupValues[3].trim()
            if (ssid.isBlank()) continue

            val secType = WifiSecurityType.fromString(secRaw)
            mergeExtractedNetwork(
                results = results,
                ssid = ssid,
                password = "",
                securityType = secType,
                networkId = networkId,
                isHiddenSsid = false,
                currentSsid = currentSsid,
                source = WifiSource.SHIZUKU_STORE
            )
            count++
        }
        return count
    }

    internal fun parseDumpsysWifi(
        dumpOutput: String,
        results: MutableMap<String, SavedWifiNetwork>,
        currentSsid: String?
    ): Int {
        var count = 0
        var activeSsid: String? = null
        var activeNetId = -1
        var activeKeyMgmt = ""
        var activePsk = ""

        fun flushActive() {
            val ssid = activeSsid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" } ?: return
            val secType = when {
                activeKeyMgmt.contains("EAP", ignoreCase = true) -> WifiSecurityType.EAP
                activeKeyMgmt.contains("SAE", ignoreCase = true) || activeKeyMgmt.contains("WPA3", ignoreCase = true) -> WifiSecurityType.WPA3
                activeKeyMgmt.contains("WPA2", ignoreCase = true) -> WifiSecurityType.WPA2
                activeKeyMgmt.contains("PSK", ignoreCase = true) || activeKeyMgmt.contains("WPA", ignoreCase = true) -> WifiSecurityType.WPA2
                activeKeyMgmt.contains("NONE", ignoreCase = true) -> WifiSecurityType.OPEN
                activePsk.isNotBlank() -> WifiSecurityType.WPA2
                else -> WifiSecurityType.WPA2
            }
            val cleanPsk = activePsk.removeSurrounding("\"").takeUnless {
                it == "*" || it.equals("null", ignoreCase = true) || it.equals("<hidden>", ignoreCase = true)
            }.orEmpty()

            mergeExtractedNetwork(
                results = results,
                ssid = ssid,
                password = cleanPsk,
                securityType = secType,
                networkId = activeNetId,
                isHiddenSsid = false,
                currentSsid = currentSsid,
                source = WifiSource.SHIZUKU_STORE
            )
            count++
            activeSsid = null
            activeNetId = -1
            activeKeyMgmt = ""
            activePsk = ""
        }

        val idSsidRegex = Regex("""ID:\s*(\d+)\s+SSID:\s*"([^"]+)"""")
        val keyMgmtRegex = Regex("""KeyMgmt:\s*([A-Za-z0-9_ ,/-]+)""")
        val pskRegex = Regex("""(?:PreSharedKey|psk|passphrase)\s*[:=]\s*"([^"]+)"""")

        for (line in dumpOutput.lines()) {
            val idMatch = idSsidRegex.find(line)
            if (idMatch != null) {
                flushActive()
                activeNetId = idMatch.groupValues[1].toIntOrNull() ?: -1
                activeSsid = idMatch.groupValues[2].trim()
            }
            if (activeSsid != null) {
                keyMgmtRegex.find(line)?.let { km ->
                    activeKeyMgmt = km.groupValues[1].trim()
                }
                pskRegex.find(line)?.let { psk ->
                    activePsk = psk.groupValues[1].trim()
                }
            }
        }
        flushActive()
        return count
    }

    private fun mergeExtractedNetwork(
        results: MutableMap<String, SavedWifiNetwork>,
        ssid: String,
        password: String,
        securityType: WifiSecurityType,
        networkId: Int,
        isHiddenSsid: Boolean,
        currentSsid: String?,
        source: WifiSource
    ) {
        val existing = results[ssid]
        val existingValidPassword = existing?.password?.takeIf {
            it.isNotBlank() && it != "*" && it != "WPA2-Protected"
        }.orEmpty()
        val effectivePassword = when {
            password.isNotBlank() -> password
            existingValidPassword.isNotBlank() -> existingValidPassword
            else -> ""
        }
        val effectiveSecType = if (securityType == WifiSecurityType.OPEN && effectivePassword.isNotBlank()) {
            WifiSecurityType.WPA2
        } else {
            securityType
        }
        results[ssid] = SavedWifiNetwork(
            id = existing?.id ?: UUID.randomUUID().toString(),
            ssid = ssid,
            password = effectivePassword,
            securityType = effectiveSecType,
            networkId = if (networkId >= 0) networkId else (existing?.networkId ?: -1),
            bssid = existing?.bssid,
            isCurrentlyConnected = (ssid == currentSsid) || (existing?.isCurrentlyConnected == true),
            lastConnectedTimestamp = existing?.lastConnectedTimestamp ?: System.currentTimeMillis(),
            isHiddenSsid = isHiddenSsid || (existing?.isHiddenSsid == true),
            source = if (existing?.source == WifiSource.LOCAL_VAULT && password.isBlank()) {
                WifiSource.LOCAL_VAULT
            } else {
                source
            }
        )
    }

    private fun decodeXmlEntities(input: String): String {
        return input
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&apos;", "'")
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
