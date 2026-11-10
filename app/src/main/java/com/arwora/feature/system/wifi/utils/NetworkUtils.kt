package com.arwora.feature.system.wifi.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import android.util.Log
import com.arwora.feature.system.wifi.DeviceInfo
import com.arwora.feature.system.wifi.WifiNetwork
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import kotlin.math.pow
import kotlin.random.Random

class NetworkUtils(private val context: Context) {

    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun getCurrentWifiInfo(): RealWifiInfo {
        val connectionInfo = wifiManager.connectionInfo
        
        // On Android 10+, SSID/BSSID require ACCESS_FINE_LOCATION
        // If not granted, ssid will be "<unknown ssid>" or "02:00:00:00:00:00"
        val ssid = connectionInfo.ssid.removeSurrounding("\"")
        val bssid = connectionInfo.bssid ?: "00:00:00:00:00:00"
        
        val rssi = connectionInfo.rssi
        val linkSpeed = connectionInfo.linkSpeed // Mbps
        val frequency = connectionInfo.frequency // MHz
        val band = when {
            frequency in 2400..2500 -> "2.4 GHz"
            frequency in 5000..6000 -> "5 GHz"
            frequency > 6000 -> "6 GHz"
            else -> "Unknown"
        }
        
        val ipAddress = Formatter.formatIpAddress(connectionInfo.ipAddress)
        val gateway = getGatewayIp()

        return RealWifiInfo(
            ssid = if (ssid == "<unknown ssid>" || ssid.isEmpty()) "Connected Network" else ssid,
            bssid = bssid,
            rssi = rssi,
            linkSpeed = linkSpeed,
            frequency = frequency,
            band = band,
            ipAddress = ipAddress,
            gateway = gateway
        )
    }

    suspend fun fetchRouterClients(ssid: String, user: String, pass: String): List<DeviceInfo> = withContext(Dispatchers.IO) {
        // In a real production app, this would perform a REST call or Scraping 
        // to the router admin portal (e.g. 192.168.1.1) using the provided credentials.
        // For this implementation, we simulate the results that would be returned by such an API.
        
        delay(1500) // Simulate network latency to router admin portal
        
        val gateway = getGatewayIp()
        val prefix = gateway.substringBeforeLast(".")
        
        listOf(
            DeviceInfo(
                ip = "$prefix.101",
                mac = "F0:18:98:C2:55:A1",
                vendor = "Apple Inc.",
                friendlyName = "iPhone 15 Pro",
                hostname = "iphone-15-pro",
                nameSource = "Router DHCP",
                latencyMs = 45,
                band = "5 GHz",
                connectionDuration = "02h 45m",
                downloadUsageBytes = 1024L * 1024 * 450, // 450MB
                uploadUsageBytes = 1024L * 1024 * 28,
                accessSN = "SN-${Random.nextInt(1000, 9999)}",
                accessName = ssid
            ),
            DeviceInfo(
                ip = "$prefix.105",
                mac = "74:DA:38:91:E2:B4",
                vendor = "Samsung Electronics",
                friendlyName = "Galaxy Tab S9",
                hostname = "galaxy-tab",
                nameSource = "Router DHCP",
                latencyMs = 12,
                band = "5 GHz",
                connectionDuration = "05h 12m",
                downloadUsageBytes = 1024L * 1024 * 890,
                uploadUsageBytes = 1024L * 1024 * 56,
                accessSN = "SN-${Random.nextInt(1000, 9999)}",
                accessName = ssid
            ),
            DeviceInfo(
                ip = "$prefix.110",
                mac = "D8:F2:CA:3F:88:99",
                vendor = "Sony Interactive Ent.",
                friendlyName = "PlayStation 5",
                hostname = "ps5-console",
                nameSource = "Router DHCP",
                latencyMs = 8,
                band = "5 GHz",
                connectionDuration = "12h 05m",
                downloadUsageBytes = 1024L * 1024 * 1024 * 12, // 12GB
                uploadUsageBytes = 1024L * 1024 * 1024 * 1,
                accessSN = "SN-${Random.nextInt(1000, 9999)}",
                accessName = ssid
            )
        )
    }

    private fun getGatewayIp(): String {
        val dhcpInfo = wifiManager.dhcpInfo
        return Formatter.formatIpAddress(dhcpInfo.gateway)
    }

    suspend fun discoverDevices(onProgress: (Int) -> Unit): List<DeviceInfo> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<DeviceInfo>()
        val ipAddress = wifiManager.connectionInfo.ipAddress
        if (ipAddress == 0) return@withContext emptyList()

        val prefix = Formatter.formatIpAddress(ipAddress).substringBeforeLast(".")
        val jobs = mutableListOf<Job>()

        // Scan the 1-254 range (typical /24 subnet)
        for (i in 1..254) {
            val testIp = "$prefix.$i"
            jobs.add(launch {
                try {
                    val address = InetAddress.getByName(testIp)
                    if (address.isReachable(500)) {
                        val hostName = address.hostName
                        val device = DeviceInfo(
                            ip = testIp,
                            mac = getMacFromArp(testIp) ?: "00:00:00:00:00:00",
                            vendor = "Generic",
                            friendlyName = if (hostName == testIp) null else hostName,
                            hostname = hostName,
                            nameSource = "Ping Discovery",
                            latencyMs = ping(testIp),
                            lastSeenTime = System.currentTimeMillis()
                        )
                        synchronized(devices) {
                            devices.add(device)
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
                onProgress(i)
            })
        }
        jobs.joinAll()
        devices
    }

    fun ping(host: String): Long {
        val start = System.currentTimeMillis()
        return try {
            val address = InetAddress.getByName(host)
            if (address.isReachable(1000)) {
                System.currentTimeMillis() - start
            } else {
                -1
            }
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Note: Accessing ARP table is restricted since Android 10.
     * This is a legacy method that might return null on modern Android.
     */
    private fun getMacFromArp(ip: String): String? {
        // In real Android 11+, this often returns null or 02:00:00:00:00:00
        // unless the app is a system app or uses specific workarounds not available here.
        return null 
    }

    @SuppressLint("MissingPermission")
    fun getScanResults(): List<WifiNetwork> {
        val results = try {
            wifiManager.scanResults ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        return results.map { result ->
            val rssi = result.level
            val frequency = result.frequency
            val distance = calculateDistance(rssi.toDouble(), frequency.toDouble())
            
            WifiNetwork(
                ssid = result.SSID,
                bssid = result.BSSID,
                rssiDbm = rssi,
                frequencyMhz = frequency,
                channel = getChannelFromFrequency(frequency),
                capabilities = result.capabilities,
                isHidden = result.SSID.isEmpty(),
                distanceMeters = distance,
                securityType = parseCapabilities(result.capabilities),
                signalPercent = WifiManager.calculateSignalLevel(rssi, 100)
            )
        }
    }

    private fun getChannelFromFrequency(freq: Int): Int {
        return when {
            freq == 2484 -> 14
            freq < 2484 -> (freq - 2407) / 5
            freq in 5000..5900 -> (freq - 5000) / 5
            else -> 0
        }
    }

    private fun parseCapabilities(caps: String): String {
        return when {
            caps.contains("WPA3") -> "WPA3"
            caps.contains("WPA2") -> "WPA2"
            caps.contains("WPA") -> "WPA"
            caps.contains("WEP") -> "WEP"
            else -> "Open"
        }
    }

    /**
     * FSPL (Free Space Path Loss) based distance estimation
     * d = 10 ^ ((27.55 - (20 * log10(f)) + |RSSI|) / 20)
     */
    private fun calculateDistance(rssi: Double, freqMhz: Double): Double {
        val exp = (27.55 - (20 * Math.log10(freqMhz)) + Math.abs(rssi)) / 20.0
        return 10.0.pow(exp)
    }
}

data class RealWifiInfo(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val linkSpeed: Int,
    val frequency: Int,
    val band: String,
    val ipAddress: String,
    val gateway: String
)
