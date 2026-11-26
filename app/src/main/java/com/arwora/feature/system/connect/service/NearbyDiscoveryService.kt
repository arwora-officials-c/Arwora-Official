package com.arwora.feature.system.connect.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.arwora.feature.system.connect.data.ConnectSecurityStore
import com.arwora.feature.system.connect.model.DiscoveredNearbyPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Zero-Config Network Service Discovery (NSD / mDNS) controller for Arwora Connect.
 * Registers `_arworaconnect._tcp.` on the active LAN port and discovers nearby peers automatically.
 */
class NearbyDiscoveryService(
    context: Context,
    private val securityStore: ConnectSecurityStore
) {
    private val appContext = context.applicationContext
    private val nsdManager: NsdManager? =
        runCatching { appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager }.getOrNull()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isActiveRunning = AtomicBoolean(false)

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredNearbyPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredNearbyPeer>> = _discoveredPeers.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val resolveQueue = Channel<NsdServiceInfo>(capacity = 32)
    private var resolveWorkerJob: Job? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    @Volatile
    private var registeredServiceName: String? = null

    @Synchronized
    fun start(port: Int, deviceName: String) {
        if (port !in 1..65535) return
        if (isActiveRunning.get()) {
            // Ensure discovery stays active; refresh registration only if needed
            return
        }
        isActiveRunning.set(true)
        _isScanning.value = true

        acquireMulticastLock()
        startResolveWorker()
        registerLocalService(port, deviceName)
        startDiscoveryListener()
    }

    @Synchronized
    fun updateLocalDeviceName(newName: String, port: Int) {
        if (!isActiveRunning.get() || port !in 1..65535) return
        unregisterLocalService()
        registerLocalService(port, newName)
    }

    @Synchronized
    fun stop() {
        isActiveRunning.set(false)
        _isScanning.value = false
        stopDiscoveryListener()
        unregisterLocalService()
        resolveWorkerJob?.cancel()
        resolveWorkerJob = null
        releaseMulticastLock()
        _discoveredPeers.value = emptyList()
    }

    fun shutdown() {
        stop()
        scope.cancel()
        resolveQueue.close()
    }

    fun upsertDiscoveredPeer(peer: DiscoveredNearbyPeer) {
        if (peer.deviceId == securityStore.getOrCreateDeviceId()) return
        synchronized(_discoveredPeers) {
            val current = _discoveredPeers.value.toMutableList()
            val existingIdx = current.indexOfFirst {
                (it.deviceId.isNotBlank() && it.deviceId == peer.deviceId) ||
                    (it.serviceName == peer.serviceName && it.hostIp == peer.hostIp)
            }
            if (existingIdx >= 0) {
                current[existingIdx] = peer
            } else {
                current.add(0, peer)
            }
            _discoveredPeers.value = current.sortedByDescending { it.discoveredAtMillis }
        }
    }

    fun removeByServiceName(serviceName: String) {
        synchronized(_discoveredPeers) {
            _discoveredPeers.value = _discoveredPeers.value.filterNot {
                it.serviceName == serviceName
            }
        }
    }

    fun removeByDeviceId(deviceId: String) {
        synchronized(_discoveredPeers) {
            _discoveredPeers.value = _discoveredPeers.value.filterNot {
                it.deviceId == deviceId
            }
        }
    }

    private fun acquireMulticastLock() {
        try {
            val wm = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
            if (multicastLock == null) {
                multicastLock = wm.createMulticastLock("ArworaConnectNsdLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
            }
        } catch (e: Exception) {
            Log.d(TAG, "MulticastLock unavailable: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    private fun registerLocalService(port: Int, deviceName: String) {
        val manager = nsdManager ?: return
        val localId = securityStore.getOrCreateDeviceId()
        val desiredName = buildServiceInstanceName(localId)
        val info = NsdServiceInfo().apply {
            serviceName = desiredName
            serviceType = SERVICE_TYPE
            this.port = port
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                runCatching { setAttribute(ATTR_DEVICE_ID, localId) }
                runCatching { setAttribute(ATTR_DEVICE_NAME, deviceName.take(36)) }
                runCatching { setAttribute(ATTR_FINGERPRINT, securityStore.getCertFingerprint().take(16)) }
            }
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                registeredServiceName = serviceInfo.serviceName
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "NSD service registration failed: code=$errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                if (registeredServiceName == serviceInfo.serviceName) {
                    registeredServiceName = null
                }
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.d(TAG, "NSD unregistration failed: code=$errorCode")
            }
        }
        registrationListener = listener
        try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register NSD service: ${e.message}")
        }
    }

    private fun unregisterLocalService() {
        val manager = nsdManager ?: return
        val listener = registrationListener ?: return
        registrationListener = null
        registeredServiceName = null
        runCatching { manager.unregisterService(listener) }
    }

    private fun startDiscoveryListener() {
        val manager = nsdManager ?: return
        if (discoveryListener != null) return

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                _isScanning.value = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!isActiveRunning.get()) return
                if (!matchesArworaServiceType(serviceInfo.serviceType)) return
                val selfService = registeredServiceName ?: buildServiceInstanceName(securityStore.getOrCreateDeviceId())
                if (serviceInfo.serviceName == selfService) return
                resolveQueue.trySend(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                removeByServiceName(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _isScanning.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD discovery start failed: code=$errorCode")
                _isScanning.value = false
                runCatching { manager.stopServiceDiscovery(this) }
                discoveryListener = null
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
                discoveryListener = null
            }
        }
        discoveryListener = listener
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to start NSD discovery: ${e.message}")
            discoveryListener = null
            _isScanning.value = false
        }
    }

    private fun stopDiscoveryListener() {
        val manager = nsdManager ?: return
        val listener = discoveryListener ?: return
        discoveryListener = null
        runCatching { manager.stopServiceDiscovery(listener) }
    }

    private fun startResolveWorker() {
        resolveWorkerJob?.cancel()
        resolveWorkerJob = scope.launch {
            for (serviceInfo in resolveQueue) {
                if (!isActive || !isActiveRunning.get()) break
                val resolved = resolveSingleService(serviceInfo)
                if (resolved != null) {
                    upsertDiscoveredPeer(resolved)
                }
                // Small spacing avoids NsdManager FAILURE_ALREADY_ACTIVE on older Android stacks
                delay(120L)
            }
        }
    }

    private suspend fun resolveSingleService(serviceInfo: NsdServiceInfo): DiscoveredNearbyPeer? {
        val manager = nsdManager ?: return null
        val localId = securityStore.getOrCreateDeviceId()
        return withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val done = AtomicBoolean(false)
                @Suppress("DEPRECATION")
                val listener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        if (done.compareAndSet(false, true) && cont.isActive) {
                            cont.resume(null)
                        }
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        if (done.compareAndSet(false, true) && cont.isActive) {
                            val hostIp = info.host?.hostAddress.orEmpty()
                            val attrs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                info.attributes?.mapValues { (_, bytes) ->
                                    bytes?.let { String(it, Charsets.UTF_8) }.orEmpty()
                                }.orEmpty()
                            } else {
                                emptyMap()
                            }
                            cont.resume(
                                parseResolvedService(
                                    serviceName = info.serviceName,
                                    hostIp = hostIp,
                                    port = info.port,
                                    attributes = attrs,
                                    localDeviceId = localId
                                )
                            )
                        }
                    }
                }
                try {
                    @Suppress("DEPRECATION")
                    manager.resolveService(serviceInfo, listener)
                } catch (_: Exception) {
                    if (done.compareAndSet(false, true) && cont.isActive) {
                        cont.resume(null)
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "NearbyDiscoveryService"
        const val SERVICE_TYPE = "_arworaconnect._tcp."
        const val SERVICE_NAME_PREFIX = "ArworaConnect-"
        private const val ATTR_DEVICE_ID = "id"
        private const val ATTR_DEVICE_NAME = "name"
        private const val ATTR_FINGERPRINT = "fp"
        private const val RESOLVE_TIMEOUT_MS = 4_000L

        fun buildServiceInstanceName(deviceId: String): String {
            val clean = deviceId.replace("-", "").take(8).ifBlank { "node" }
            return "$SERVICE_NAME_PREFIX$clean"
        }

        fun matchesArworaServiceType(serviceType: String?): Boolean {
            if (serviceType.isNullOrBlank()) return false
            val normalized = serviceType.trim().lowercase()
            return normalized == "_arworaconnect._tcp" ||
                normalized == "_arworaconnect._tcp." ||
                normalized == "_arworaconnect._tcp.local." ||
                normalized.contains("_arworaconnect._tcp")
        }

        fun parseResolvedService(
            serviceName: String,
            hostIp: String,
            port: Int,
            attributes: Map<String, String>,
            localDeviceId: String
        ): DiscoveredNearbyPeer? {
            val cleanIp = hostIp.trim().removePrefix("/")
            if (cleanIp.isEmpty() || port !in 1..65535) return null
            val peerId = attributes[ATTR_DEVICE_ID]?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "nsd_${serviceName}_${cleanIp}_$port"
            if (peerId == localDeviceId) return null

            val peerName = attributes[ATTR_DEVICE_NAME]?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: serviceName.removePrefix(SERVICE_NAME_PREFIX).ifBlank { "Nearby Arwora Device" }

            val fpPrefix = attributes[ATTR_FINGERPRINT]?.trim().orEmpty()
            return DiscoveredNearbyPeer(
                serviceName = serviceName,
                deviceId = peerId,
                deviceName = peerName,
                hostIp = cleanIp,
                port = port,
                certFingerprintPrefix = fpPrefix,
                discoveredAtMillis = System.currentTimeMillis()
            )
        }
    }
}
