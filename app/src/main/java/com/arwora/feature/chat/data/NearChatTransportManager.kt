package com.arwora.feature.chat.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.arwora.feature.chat.model.NearChatPeer
import com.arwora.feature.chat.model.NearDiscoverySource
import com.arwora.feature.system.connect.data.ArworaConnectEngine
import com.arwora.feature.system.connect.data.ArworaConnectManager
import com.arwora.feature.system.connect.model.DiscoveredNearbyPeer
import com.arwora.feature.system.connect.model.PeerConnectionState
import com.arwora.feature.system.connect.model.TrustedPeerRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Multi-transport Near Chat P2P discovery coordinator combining:
 * 1. Wi-Fi mDNS / LAN socket peers (via ArworaConnectEngine & NearbyDiscoveryService)
 * 2. Bluetooth Low Energy (BLE) Arwora Near Chat service advertising & scanning
 * 3. Wi-Fi Direct (WifiP2pManager) peer discovery
 */
class NearChatTransportManager(
    context: Context,
    private val connectEngine: ArworaConnectEngine = ArworaConnectManager.getEngine(context)
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val blePeersMap = ConcurrentHashMap<String, NearChatPeer>()
    private val wifiDirectPeersMap = ConcurrentHashMap<String, NearChatPeer>()

    private val _bleAndDirectPeers = MutableStateFlow<List<NearChatPeer>>(emptyList())
    private val _nearPeers = MutableStateFlow<List<NearChatPeer>>(emptyList())
    val nearPeers: StateFlow<List<NearChatPeer>> = _nearPeers.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _radarStatus = MutableStateFlow("Tap Scan Nearby to discover offline peers over Wi-Fi Direct, BLE & LAN")
    val radarStatus: StateFlow<String> = _radarStatus.asStateFlow()

    private var bleScanner: BluetoothLeScanner? = null
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleScanCallback: ScanCallback? = null
    private var bleAdvertiseCallback: AdvertiseCallback? = null

    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null
    private var wifiP2pReceiver: BroadcastReceiver? = null

    init {
        scope.launch {
            combine(
                connectEngine.peers,
                connectEngine.nearbyPeers,
                _bleAndDirectPeers
            ) { trustedPeers, nsdPeers, radioPeers ->
                mergePeers(trustedPeers.map { it.peer to (it.connectionState == PeerConnectionState.CONNECTED) }, nsdPeers, radioPeers)
            }.collect { merged ->
                _nearPeers.value = merged
            }
        }
    }

    private fun mergePeers(
        trusted: List<Pair<TrustedPeerRecord, Boolean>>,
        nsdPeers: List<DiscoveredNearbyPeer>,
        radioPeers: List<NearChatPeer>
    ): List<NearChatPeer> {
        val byId = LinkedHashMap<String, NearChatPeer>()
        trusted.forEach { (record, connected) ->
            byId[record.deviceId] = NearChatPeer(
                id = record.deviceId,
                displayName = record.deviceName,
                source = NearDiscoverySource.WIFI_NSD,
                hostIp = record.lastKnownIp,
                port = record.port,
                isConnected = connected,
                lastSeenMillis = record.lastSeenMillis
            )
        }
        nsdPeers.forEach { nsd ->
            val existing = byId[nsd.deviceId]
            byId[nsd.deviceId] = NearChatPeer(
                id = nsd.deviceId,
                displayName = nsd.deviceName,
                source = NearDiscoverySource.WIFI_NSD,
                hostIp = nsd.hostIp,
                port = nsd.port,
                isConnected = existing?.isConnected == true,
                lastSeenMillis = nsd.discoveredAtMillis
            )
        }
        radioPeers.forEach { peer ->
            if (!byId.containsKey(peer.id)) {
                byId[peer.id] = peer
            }
        }
        return byId.values.sortedWith(
            compareByDescending<NearChatPeer> { it.isConnected }
                .thenByDescending { it.lastSeenMillis }
        )
    }

    fun missingNearbyPermissions(): List<String> {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)) {
                needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        } else if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)) {
                needed.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            }
        }
        return needed
    }

    suspend fun startDiscovery() {
        _isScanning.value = true
        val channels = mutableListOf<String>()
        runCatching {
            val id = connectEngine.localIdentity.value
            if (!id.isServerRunning) {
                connectEngine.startServer()
            } else {
                connectEngine.nearbyDiscovery.stop()
                connectEngine.nearbyDiscovery.start(port = id.listeningPort, deviceName = id.deviceName)
            }
            channels.add("Wi-Fi mDNS")
        }
        if (startBleDiscoveryAndAdvertising()) {
            channels.add("BLE")
        }
        if (startWifiDirectDiscovery()) {
            channels.add("Wi-Fi Direct")
        }
        _radarStatus.value = if (channels.isNotEmpty()) {
            "Scanning via ${channels.joinToString(" + ")}..."
        } else {
            "Grant Nearby / Bluetooth permissions to scan offline peers"
        }
    }

    fun stopDiscovery() {
        _isScanning.value = false
        stopBle()
        stopWifiDirect()
        _radarStatus.value = "Nearby radar paused"
    }

    suspend fun connectPeer(peer: NearChatPeer): Result<TrustedPeerRecord?> {
        if (!peer.hostIp.isNullOrBlank() && peer.port in 1..65535) {
            val existingTrusted = connectEngine.securityStore.loadTrustedPeers()
                .firstOrNull { it.deviceId == peer.id }
            if (existingTrusted != null) {
                val ok = connectEngine.reconnectPeer(existingTrusted.deviceId)
                return if (ok) Result.success(existingTrusted)
                else Result.failure(IllegalStateException("Peer ${peer.displayName} unreachable"))
            }
            val res = connectEngine.pairWithNearbyPeer(
                DiscoveredNearbyPeer(
                    serviceName = "Arwora_${peer.id.take(6)}",
                    deviceId = peer.id,
                    deviceName = peer.displayName,
                    hostIp = peer.hostIp,
                    port = peer.port
                )
            )
            return res.map { it }
        }
        if (peer.source == NearDiscoverySource.WIFI_DIRECT && !peer.bluetoothAddress.isNullOrBlank()) {
            initiateWifiDirectConnect(peer.bluetoothAddress)
            return Result.success(null)
        }
        return Result.failure(IllegalStateException("Awaiting socket endpoint from ${peer.source.label} peer"))
    }

    @SuppressLint("MissingPermission")
    private fun startBleDiscoveryAndAdvertising(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN) || !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                return false
            }
        } else if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return false
        }

        val btManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
        val adapter: BluetoothAdapter = btManager.adapter ?: return false
        if (!adapter.isEnabled) return false

        val scanner = adapter.bluetoothLeScanner
        if (scanner != null && bleScanCallback == null) {
            val callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    handleBleResult(result)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    results.forEach(::handleBleResult)
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.d(TAG, "BLE scan failed: $errorCode")
                }
            }
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BLE_SERVICE_UUID))
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .build()
            runCatching {
                scanner.startScan(listOf(filter), settings, callback)
                bleScanner = scanner
                bleScanCallback = callback
            }.onFailure {
                Log.d(TAG, "Unable to start BLE scan: ${it.message}")
            }
        }

        val canAdvertise = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
        if (canAdvertise && bleAdvertiseCallback == null) {
            val advertiser = adapter.bluetoothLeAdvertiser
            if (advertiser != null) {
                val advSettings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                    .setConnectable(false)
                    .setTimeout(0)
                    .build()
                val advData = AdvertiseData.Builder()
                    .setIncludeDeviceName(true)
                    .addServiceUuid(ParcelUuid(BLE_SERVICE_UUID))
                    .build()
                val advCallback = object : AdvertiseCallback() {
                    override fun onStartFailure(errorCode: Int) {
                        Log.d(TAG, "BLE advertise failed: $errorCode")
                    }
                }
                runCatching {
                    advertiser.startAdvertising(advSettings, advData, advCallback)
                    bleAdvertiser = advertiser
                    bleAdvertiseCallback = advCallback
                }
            }
        }
        return bleScanCallback != null || bleAdvertiseCallback != null
    }

    @SuppressLint("MissingPermission")
    private fun handleBleResult(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        val name = runCatching {
            result.scanRecord?.deviceName ?: device.name
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "BLE Peer (${address.takeLast(5)})"

        blePeersMap["ble_$address"] = NearChatPeer(
            id = "ble_$address",
            displayName = name,
            source = NearDiscoverySource.BLUETOOTH_LE,
            bluetoothAddress = address,
            rssiDbm = result.rssi,
            isConnected = false,
            lastSeenMillis = System.currentTimeMillis()
        )
        publishRadioPeers()
    }

    @SuppressLint("MissingPermission")
    private fun stopBle() {
        bleScanCallback?.let { cb ->
            runCatching { bleScanner?.stopScan(cb) }
        }
        bleAdvertiseCallback?.let { cb ->
            runCatching { bleAdvertiser?.stopAdvertising(cb) }
        }
        bleScanCallback = null
        bleAdvertiseCallback = null
    }

    @SuppressLint("MissingPermission")
    private fun startWifiDirectDiscovery(): Boolean {
        val hasDirectPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
        } else {
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!hasDirectPerm) return false

        val manager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager ?: return false
        val channel = wifiP2pChannel ?: manager.initialize(appContext, Looper.getMainLooper(), null) ?: return false
        wifiP2pManager = manager
        wifiP2pChannel = channel

        if (wifiP2pReceiver == null) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION) {
                        runCatching {
                            manager.requestPeers(channel) { peersList: WifiP2pDeviceList? ->
                                val list = peersList?.deviceList.orEmpty()
                                wifiDirectPeersMap.clear()
                                val now = System.currentTimeMillis()
                                list.forEach { dev ->
                                    val addr = dev.deviceAddress ?: return@forEach
                                    wifiDirectPeersMap["p2p_$addr"] = NearChatPeer(
                                        id = "p2p_$addr",
                                        displayName = dev.deviceName?.ifBlank { "Wi-Fi Direct Peer" } ?: "Wi-Fi Direct Peer",
                                        source = NearDiscoverySource.WIFI_DIRECT,
                                        bluetoothAddress = addr,
                                        isConnected = dev.status == android.net.wifi.p2p.WifiP2pDevice.CONNECTED,
                                        lastSeenMillis = now
                                    )
                                }
                                publishRadioPeers()
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            runCatching {
                ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                wifiP2pReceiver = receiver
            }
        }

        return runCatching {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Wi-Fi Direct peer discovery started")
                }

                override fun onFailure(reason: Int) {
                    Log.d(TAG, "Wi-Fi Direct discovery failed: $reason")
                }
            })
            true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun initiateWifiDirectConnect(deviceAddress: String) {
        val manager = wifiP2pManager ?: return
        val channel = wifiP2pChannel ?: return
        val config = android.net.wifi.p2p.WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        runCatching {
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    _radarStatus.value = "Wi-Fi Direct handshake initiated"
                }

                override fun onFailure(reason: Int) {
                    _radarStatus.value = "Wi-Fi Direct connect failed ($reason)"
                }
            })
        }
    }

    private fun stopWifiDirect() {
        wifiP2pReceiver?.let { rec ->
            runCatching { appContext.unregisterReceiver(rec) }
        }
        wifiP2pReceiver = null
        runCatching { wifiP2pManager?.stopPeerDiscovery(wifiP2pChannel, null) }
    }

    private fun publishRadioPeers() {
        val merged = ArrayList<NearChatPeer>(blePeersMap.size + wifiDirectPeersMap.size)
        merged.addAll(wifiDirectPeersMap.values)
        merged.addAll(blePeersMap.values)
        _bleAndDirectPeers.value = merged
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "NearChatTransportMgr"
        val BLE_SERVICE_UUID: UUID = UUID.fromString("0000A7C0-0000-1000-8000-00805F9B34FB")
    }
}
