package com.arwora.feature.system.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.arwora.feature.system.wifi.resolve.MdnsResolver
import com.arwora.feature.system.wifi.resolve.MulticastHelper
import com.arwora.feature.system.wifi.resolve.NetbiosResolver
import com.arwora.feature.system.wifi.resolve.ReverseDnsResolver
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

object LanScanner {
    private val ouiMap = mapOf(
        "00:03:93" to "Apple", "00:05:02" to "Apple", "00:0a:27" to "Apple", "00:10:FA" to "Apple",
        "00:11:24" to "Apple", "00:14:51" to "Apple", "00:16:CB" to "Apple", "00:17:F2" to "Apple",
        "40:6C:8F" to "Apple", "BC:92:6B" to "Apple", "F8:FF:C2" to "Apple", "DC:A9:04" to "Apple",
        "3C:06:30" to "Apple", "A4:83:E7" to "Apple", "AC:BC:32" to "Apple", "F0:18:98" to "Apple",
        "00:12:3F" to "Samsung", "00:15:99" to "Samsung", "00:16:32" to "Samsung", "00:17:C4" to "Samsung",
        "28:BA:B2" to "Samsung", "3C:5A:F4" to "Samsung", "8C:73:4B" to "Samsung", "50:01:D9" to "Samsung",
        "00:90:4C" to "TP-Link", "14:CF:92" to "TP-Link", "50:3F:2D" to "TP-Link", "60:E3:27" to "TP-Link",
        "B0:95:75" to "TP-Link", "EC:08:6B" to "TP-Link", "C0:06:C3" to "TP-Link", "00:1D:0F" to "TP-Link",
        "28:6C:07" to "Xiaomi", "34:80:B3" to "Xiaomi", "50:EC:50" to "Xiaomi", "64:09:80" to "Xiaomi",
        "7C:49:EB" to "Xiaomi", "D4:97:0B" to "Xiaomi", "F0:B4:29" to "Xiaomi", "84:F3:EB" to "Xiaomi",
        "00:1E:67" to "Intel", "00:27:10" to "Intel", "34:13:E8" to "Intel", "80:86:F2" to "Intel",
        "00:10:18" to "Broadcom", "00:1B:DC" to "Broadcom", "20:A6:8D" to "Broadcom",
        "00:50:56" to "VMware", "00:0C:29" to "VMware", "08:00:27" to "VirtualBox",
        "B8:27:EB" to "Raspberry Pi", "DC:A6:32" to "Raspberry Pi", "E4:5F:01" to "Raspberry Pi",
        "2C:CF:67" to "Espressif (ESP32/8266)", "30:AE:A4" to "Espressif", "24:6F:28" to "Espressif",
        "D8:A0:1D" to "Google", "F4:F5:DB" to "Google", "54:60:09" to "Google", "70:EE:50" to "Netgear",
        "00:24:B2" to "ASUS", "78:24:AF" to "ASUS", "04:D9:F5" to "ASUS", "30:5A:3A" to "ASUS",
        "00:18:E7" to "Huawei", "00:25:9E" to "Huawei", "00:46:4B" to "Huawei", "00:E0:FC" to "Huawei"
    )

    fun getNetworkInfo(context: Context): NetworkInfo {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        
        val isWifi = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        val isMobile = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
        val isEthernet = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ?: false
        
        val isConnected = isWifi || isMobile || isEthernet || (networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ?: false)

        if (!isConnected) {
            return NetworkInfo("", "", "", "", 0, emptyList(), 0, false, false, false)
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val dhcpInfo = wifiManager.dhcpInfo
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork)

        val ssid = if (isWifi) {
            val name = wifiInfo.ssid?.removeSurrounding("\"")
            if (name == "<unknown ssid>" || name.isNullOrEmpty()) "Connected Wi-Fi" else name
        } else if (isMobile) {
            "Mobile Data"
        } else {
            "Local Network"
        }
        val bssid = if (isWifi) wifiInfo.bssid ?: "Unknown" else "N/A"
        
        // Try LinkProperties first for IP (more reliable on Android 10+)
        var localIp = "0.0.0.0"
        var subnetCidr = 24
        
        linkProperties?.linkAddresses?.forEach { linkAddr ->
            val addr = linkAddr.address
            if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                localIp = addr.hostAddress ?: "0.0.0.0"
                subnetCidr = linkAddr.prefixLength
            }
        }
        
        // Fallback to DhcpInfo if LinkProperties failed
        if (localIp == "0.0.0.0") {
            localIp = formatIpAddress(dhcpInfo.ipAddress)
            subnetCidr = if (dhcpInfo.netmask != 0) calculateCidr(dhcpInfo.netmask) else 24
        }
 
        val gateway = formatIpAddress(dhcpInfo.gateway).takeIf { it != "0.0.0.0" } 
            ?: linkProperties?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress 
            ?: "0.0.0.0"

        val dnsServers = mutableListOf<String>()
        linkProperties?.dnsServers?.forEach { dns ->
            dns.hostAddress?.let { dnsServers.add(it) }
        }
        if (dnsServers.isEmpty()) {
            val d1 = formatIpAddress(dhcpInfo.dns1)
            val d2 = formatIpAddress(dhcpInfo.dns2)
            if (d1 != "0.0.0.0") dnsServers.add(d1)
            if (d2 != "0.0.0.0") dnsServers.add(d2)
        }

        val linkSpeed = if (isWifi) wifiInfo.linkSpeed else 0
        val frequency = if (isWifi) wifiInfo.frequency else 0
        val rssi = if (isWifi) wifiInfo.rssi else -127

        return NetworkInfo(ssid, bssid, localIp, gateway, subnetCidr, dnsServers, linkSpeed, true, isWifi, isMobile, frequency, rssi)
    }

    private fun formatIpAddress(ipAddress: Int): String {
        if (ipAddress == 0) return "0.0.0.0"
        return "${ipAddress and 0xFF}.${ipAddress shr 8 and 0xFF}.${ipAddress shr 16 and 0xFF}.${ipAddress shr 24 and 0xFF}"
    }

    private fun calculateCidr(netmask: Int): Int {
        var cidr = 0
        var mask = netmask
        while (mask != 0) {
            cidr += mask and 1
            mask = mask ushr 1
        }
        return cidr
    }

    suspend fun getNetworkPrefix(context: Context): String {
        val info = getNetworkInfo(context)
        if (info.localIp.isBlank() || info.localIp == "0.0.0.0") return ""
        val parts = info.localIp.split(".")
        if (parts.size == 4) {
            return "${parts[0]}.${parts[1]}.${parts[2]}."
        }
        return ""
    }

    /**
     * Fully thread-safe streaming scan using channelFlow.
     * All concurrent child coroutines communicate solely through channelFlow's send/trySend.
     * Guaranteed cleanup and cancellation via awaitClose.
     */
    fun scanNetworkFlow(context: Context, scanMode: ScanMode): Flow<DeviceEvent> = channelFlow {
        val prefix = getNetworkPrefix(context)
        if (prefix.isBlank()) {
            send(DeviceEvent.OnDone("Not connected to Wi-Fi"))
            return@channelFlow
        }

        val networkInfo = getNetworkInfo(context)
        val gateway = networkInfo.gateway
        val localIp = networkInfo.localIp

        // Thread-safe deduplication set of discovered IP addresses
        val discoveredIps = ConcurrentHashMap.newKeySet<String>()
        val foundHosts = ConcurrentHashMap<String, Pair<String?, Long?>>()

        // Stage 1: Read ARP / Neighbor table first and emit immediate entries
        val arpTable = readArpTable()
        arpTable.forEach { (ip, mac) ->
            if (ip.startsWith(prefix)) {
                if (discoveredIps.add(ip)) {
                    val cleanMac = mac.takeIf { it.isNotBlank() && it != "00:00:00:00:00:00" }
                    foundHosts[ip] = Pair(cleanMac, null)
                    send(DeviceEvent.OnHostFound(ip, cleanMac, null))
                }
            }
        }

        // Stage 1.5: Discover via mDNS (reuse this map in Stage 3 instead of scanning twice)
        val bindAddress = try { InetAddress.getByName(localIp) } catch (_: Exception) { null }
        val mdnsMap = if (bindAddress != null) {
            try { MdnsResolver.resolveAll(context, bindAddress) } catch (_: Exception) { emptyMap() }
        } else {
            emptyMap()
        }

        mdnsMap.forEach { (ip, name) ->
            if (ip.startsWith(prefix)) {
                val mac = arpTable[ip]
                if (discoveredIps.add(ip)) {
                    foundHosts[ip] = Pair(mac, null)
                    send(DeviceEvent.OnHostFound(ip, mac, null))
                }
                if (name.isNotBlank()) {
                    send(DeviceEvent.OnNameResolved(ip, name, "mDNS Discovery"))
                }
            }
        }

        // Always check gateway if present and not yet added
        if (gateway.isNotBlank() && gateway != "0.0.0.0" && !discoveredIps.contains(gateway)) {
            val latency = measureLatencyMs(gateway, scanMode)
            if (latency != null) {
                val mac = arpTable[gateway]
                if (discoveredIps.add(gateway)) {
                    foundHosts[gateway] = Pair(mac, latency)
                    send(DeviceEvent.OnHostFound(gateway, mac, latency))
                }
            }
        }

        // Also add local phone IP (ensure self device model name & local MAC are always applied)
        if (localIp.isNotBlank() && localIp != "0.0.0.0") {
            val mac = getLocalMacAddress() ?: arpTable[localIp]
            if (discoveredIps.add(localIp)) {
                foundHosts[localIp] = Pair(mac, 1L)
                send(DeviceEvent.OnHostFound(localIp, mac, 1L))
            } else if (!mac.isNullOrBlank() && foundHosts[localIp]?.first.isNullOrBlank()) {
                foundHosts[localIp] = Pair(mac, foundHosts[localIp]?.second ?: 1L)
                send(DeviceEvent.OnMacResolved(localIp, mac))
            }
            val deviceModel = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}".trim()
            send(DeviceEvent.OnNameResolved(localIp, "$deviceModel (This Device)", "Self"))
        }

        // Stage 2: Live host discovery sweep with concurrency limit
        val semaphore = Semaphore(64)
        val totalIps = 254
        val scannedCounter = AtomicInteger(0)

        supervisorScope {
            val probeJobs = (1..254).map { i ->
                val targetIp = "$prefix$i"
                launch(Dispatchers.IO) {
                    semaphore.acquire()
                    try {
                        if (!isActive) return@launch
                        val alreadyFound = discoveredIps.contains(targetIp)
                        if (!alreadyFound) {
                            val latency = measureLatencyMs(targetIp, scanMode)
                            if (latency != null) {
                                // Re-read ARP / neighbor table after probe to capture MAC
                                val mac = readArpTable()[targetIp]
                                if (discoveredIps.add(targetIp)) {
                                    foundHosts[targetIp] = Pair(mac, latency)
                                    send(DeviceEvent.OnHostFound(targetIp, mac, latency))
                                }
                            }
                        } else if (targetIp != localIp && foundHosts[targetIp]?.second == null) {
                            // Measure real RTT for ARP/mDNS entries instead of static dummy values
                            val rtt = measureLatencyMs(targetIp, scanMode)
                            if (rtt != null) {
                                val currentMac = foundHosts[targetIp]?.first
                                foundHosts[targetIp] = Pair(currentMac, rtt)
                                send(DeviceEvent.OnLatencyUpdated(targetIp, rtt))
                            }
                        }
                    } finally {
                        semaphore.release()
                        val current = scannedCounter.incrementAndGet()
                        if (current % 16 == 0 || current == totalIps) {
                            send(DeviceEvent.OnProgress(current, totalIps))
                        }
                    }
                }
            }
            probeJobs.joinAll()
        }

        send(DeviceEvent.OnProgress(totalIps, totalIps))

        // Refresh ARP / neighbor table once more after subnet sweep to correlate any newly populated MACs
        val refreshedArp = readArpTable()
        foundHosts.forEach { (ip, hostInfo) ->
            val updatedMac = refreshedArp[ip]
            if (hostInfo.first.isNullOrBlank() && !updatedMac.isNullOrBlank()) {
                foundHosts[ip] = Pair(updatedMac, hostInfo.second)
                send(DeviceEvent.OnMacResolved(ip, updatedMac))
            }
        }

        // Stage 3: Non-blocking Name Resolution for discovered hosts (excluding self IP)
        try {
            val netbiosSemaphore = Semaphore(16)
            supervisorScope {
                val resolveJobs = foundHosts.filterKeys { it != localIp }.map { (ip, hostInfo) ->
                    val mac = hostInfo.first
                    launch(Dispatchers.IO) {
                        var resolvedName: String? = mdnsMap[ip]?.takeIf { it.isNotBlank() }
                        var source = if (resolvedName != null) "mDNS" else null

                        if (resolvedName == null) {
                            netbiosSemaphore.acquire()
                            try {
                                resolvedName = NetbiosResolver.resolve(ip)?.takeIf { it.isNotBlank() }
                                if (resolvedName != null) source = "NetBIOS"
                            } finally {
                                netbiosSemaphore.release()
                            }
                        }

                        if (resolvedName == null) {
                            resolvedName = ReverseDnsResolver.resolve(ip, gateway)?.takeIf { it.isNotBlank() }
                            if (resolvedName != null) source = "Reverse DNS"
                        }

                        if (resolvedName == null) {
                            val vendor = getVendor(mac ?: "")
                            resolvedName = when {
                                ip == gateway && vendor != "Generic" && vendor != "Unknown" -> "$vendor Router (Gateway)"
                                ip == gateway -> "Wi-Fi Router (Gateway)"
                                vendor != "Generic" && vendor != "Unknown" -> "$vendor Device ($ip)"
                                else -> "Host ($ip)"
                            }
                            source = "Vendor/IP"
                        }

                        send(DeviceEvent.OnNameResolved(ip, resolvedName, source ?: "Reverse DNS"))
                    }
                }
                resolveJobs.joinAll()
            }
        } catch (_: Exception) {
            // Non-fatal, keep found hosts intact
        }

        val totalFound = discoveredIps.size
        val summaryText = if (totalFound <= 1 && gateway.isNotBlank()) {
            "Scan complete: $totalFound device found (Client isolation may be enabled on this router)"
        } else {
            "Scan complete: Found $totalFound devices."
        }
        send(DeviceEvent.OnDone(summaryText))

        awaitClose {
            // Flow cancelled or completed - child jobs inside supervisorScope terminate cleanly
        }
    }.flowOn(Dispatchers.IO)

    private fun getLocalMacAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val mac = networkInterface.hardwareAddress
                if (mac != null) {
                    val sb = StringBuilder()
                    for (i in mac.indices) {
                        sb.append(String.format("%02X%s", mac[i], if (i < mac.size - 1) ":" else ""))
                    }
                    val macStr = sb.toString()
                    if (macStr != "00:00:00:00:00:00" && macStr != "02:00:00:00:00:00") return macStr
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Measures genuine network round-trip latency in milliseconds using:
     * 1. Native `ping -c 1 -W 1 <ip>` stdout RTT parsing (`time=X.Y ms`)
     * 2. Timed single-port TCP connect (`System.nanoTime()`) when ICMP is filtered
     * 3. Timed `InetAddress.isReachable(500)` fallback
     * Returns null if unreachable on all probes.
     */
    fun measureLatencyMs(ip: String, scanMode: ScanMode = ScanMode.FAST): Long? {
        // 1. Native ping command RTT (accurate kernel-level round-trip time)
        try {
            val process = ProcessBuilder("ping", "-c", "1", "-W", "1", ip)
                .redirectErrorStream(true)
                .start()
            val completed = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                process.waitFor(1400, java.util.concurrent.TimeUnit.MILLISECONDS)
            } else {
                process.waitFor()
                true
            }
            val output = try {
                process.inputStream.bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                ""
            }
            if (!completed) {
                process.destroyForcibly()
            }
            parsePingOutputMs(output)?.let { return it }
        } catch (_: Exception) {
        }

        // 2. TCP socket handshake RTT (measure ONLY the responsive port connect, not failed timeouts)
        val portsToProbe = if (scanMode == ScanMode.DEEP) {
            listOf(80, 443, 53, 554, 8080, 8443, 22, 139, 445, 5000, 7000, 8888)
        } else {
            listOf(80, 443, 53, 8080, 22)
        }

        for (port in portsToProbe) {
            try {
                Socket().use { socket ->
                    val startNs = System.nanoTime()
                    socket.connect(InetSocketAddress(ip, port), 350)
                    val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                    return elapsedMs.coerceAtLeast(1L)
                }
            } catch (_: Exception) {
            }
        }

        // 3. ICMP / TCP echo via InetAddress.isReachable
        try {
            val address = InetAddress.getByName(ip)
            val startNs = System.nanoTime()
            if (address.isReachable(500)) {
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
                return elapsedMs.coerceAtLeast(1L)
            }
        } catch (_: Exception) {
        }

        return null
    }

    internal fun parsePingOutputMs(output: String): Long? {
        if (output.isBlank()) return null
        val timeMatch = Regex("""time[=<]\s*([0-9]+(?:\.[0-9]+)?)\s*ms""", RegexOption.IGNORE_CASE).find(output)
        if (timeMatch != null) {
            val ms = timeMatch.groupValues[1].toDoubleOrNull()
            if (ms != null && ms.isFinite()) {
                return kotlin.math.round(ms).toLong().coerceAtLeast(1L)
            }
        }
        val rttSummaryMatch = Regex("""rtt\s+min/avg/max/[^=]*=\s*[0-9.]+/([0-9.]+)/""", RegexOption.IGNORE_CASE).find(output)
        if (rttSummaryMatch != null) {
            val avg = rttSummaryMatch.groupValues[1].toDoubleOrNull()
            if (avg != null && avg.isFinite()) {
                return kotlin.math.round(avg).toLong().coerceAtLeast(1L)
            }
        }
        return null
    }

    private fun readArpTable(): Map<String, String> {
        var arpText: String? = null
        var neighText: String? = null

        // 1. Direct /proc/net/arp (works on Android <= 9 and permissive builds)
        try {
            arpText = BufferedReader(FileReader("/proc/net/arp")).use { it.readText() }
        } catch (_: Exception) {
        }

        // 2. Direct `ip neigh show` (works on many Android 10-12 builds even when /proc/net/arp is blocked)
        try {
            val proc = ProcessBuilder("ip", "neigh", "show").redirectErrorStream(true).start()
            neighText = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor()
        } catch (_: Exception) {
        }

        val merged = parseArpAndNeighOutput(arpText, neighText).toMutableMap()

        // 3. If neighbor entries are empty and Wireless ADB is connected, read via elevated ADB shell
        if (merged.isEmpty() && com.arwora.utils.NativeAdbManager.isAdbReady()) {
            try {
                val adbOut = runBlocking {
                    com.arwora.feature.system.shell.ShellExecutor.executeCommand(
                        "ip neigh show 2>/dev/null; echo '---ARP---'; cat /proc/net/arp 2>/dev/null"
                    )
                }
                if (adbOut.stdout.isNotBlank()) {
                    val parts = adbOut.stdout.split("---ARP---", limit = 2)
                    val adbNeigh = parts.getOrNull(0)
                    val adbArp = parts.getOrNull(1)
                    merged.putAll(parseArpAndNeighOutput(adbArp, adbNeigh))
                }
            } catch (_: Exception) {
            }
        }

        return merged
    }

    internal fun parseArpAndNeighOutput(arpText: String?, neighText: String?): Map<String, String> {
        val arpMap = mutableMapOf<String, String>()
        val macRegex = Regex("""^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$""")

        fun acceptMac(raw: String): String? {
            val normalized = raw.trim().replace("-", ":").uppercase()
            if (!macRegex.matches(normalized)) return null
            if (normalized == "00:00:00:00:00:00" || normalized == "FF:FF:FF:FF:FF:FF") return null
            return normalized
        }

        if (!arpText.isNullOrBlank()) {
            arpText.lines().drop(1).forEach { line ->
                val tokens = line.trim().split(Regex("\\s+"))
                if (tokens.size >= 4) {
                    val ip = tokens[0].trim()
                    val mac = acceptMac(tokens[3])
                    if (ip.contains(".") && mac != null) {
                        arpMap[ip] = mac
                    }
                }
            }
        }

        if (!neighText.isNullOrBlank()) {
            val neighRegex = Regex("""^(\d+\.\d+\.\d+\.\d+)\s+.*?\blladdr\s+([0-9A-Fa-f:-]{17})\b""")
            neighText.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.contains("FAILED", ignoreCase = true) ||
                    trimmed.contains("INCOMPLETE", ignoreCase = true)
                ) {
                    return@forEach
                }
                val match = neighRegex.find(trimmed) ?: return@forEach
                val ip = match.groupValues[1]
                val mac = acceptMac(match.groupValues[2])
                if (mac != null) {
                    arpMap.putIfAbsent(ip, mac)
                }
            }
        }

        return arpMap
    }

    fun getVendor(mac: String): String {
        if (mac.isBlank() || mac.equals("Unknown", ignoreCase = true)) return "Unknown"
        val clean = mac.replace("-", ":").uppercase()
        val prefix = clean.split(":").take(3).joinToString(":")
        return ouiMap[prefix] ?: "Generic"
    }
}
