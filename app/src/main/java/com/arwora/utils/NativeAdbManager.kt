package com.arwora.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.security.KeyPair
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

enum class NativeAdbConnectionState(val label: String) {
    WIFI_REQUIRED("Wi-Fi Offline"),
    DISCONNECTED("ADB Offline"),
    CONNECTING("Connecting..."),
    CONNECTED("ADB Connected")
}

data class NativeShellResult(
    val success: Boolean,
    val output: String,
    val exitCode: Int = if (success) 0 else -1
)

data class NativeAdbEndpoint(
    val host: String,
    val port: Int
)

/**
 * Built-in native Wireless ADB connection and shell execution engine.
 * Eliminates external privileged helper apps by communicating directly with the
 * local Android adbd daemon over loopback/Wi-Fi TCP or TLS sockets.
 */
object NativeAdbManager {

    private const val TAG = "NativeAdbManager"
    const val CLIENT_IDENTITY = NativeAdbProtocol.DEFAULT_CLIENT_IDENTITY
    private const val PREFS_NAME = "adb_access_prefs"
    private const val KEY_LAST_HOST = "last_connected_host"
    private const val KEY_LAST_PORT = "last_connected_port"
    private const val KEY_USER_STOPPED = "is_user_stopped"

    private const val NSD_CONNECT_SERVICE = "_adb-tls-connect._tcp."
    private const val CONNECT_TIMEOUT_MS = 1800
    private const val HANDSHAKE_TIMEOUT_MS = 3500

    private val streamIdGen = AtomicInteger(1)
    private val connectMutex = Mutex()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedKeyPair: KeyPair? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionState = MutableStateFlow(NativeAdbConnectionState.DISCONNECTED)
    val connectionState: StateFlow<NativeAdbConnectionState> = _connectionState.asStateFlow()

    private val _connectedHost = MutableStateFlow<String?>(null)
    val connectedHost: StateFlow<String?> = _connectedHost.asStateFlow()

    private val _connectedPort = MutableStateFlow<Int?>(null)
    val connectedPort: StateFlow<Int?> = _connectedPort.asStateFlow()

    private val _statusMessage = MutableStateFlow("Tap Connect Now when Wi-Fi & Wireless Debugging are enabled.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    fun ensureClientIdentity(): String {
        runCatching { System.setProperty("adb.client.name", CLIENT_IDENTITY) }
        return CLIENT_IDENTITY
    }

    fun init(context: Context) {
        ensureClientIdentity()
        appContext = context.applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (_connectedHost.value == null) {
            val savedHost = prefs.getString(KEY_LAST_HOST, null)
            val savedPort = prefs.getInt(KEY_LAST_PORT, -1)
            if (!savedHost.isNullOrBlank() && savedPort in 1024..65535) {
                _connectedHost.value = savedHost
                _connectedPort.value = savedPort
            }
        }
        refreshConnectionStatus(context)
    }

    fun resolveConnectionState(
        isWifiConnected: Boolean,
        isConnecting: Boolean,
        isConnected: Boolean
    ): NativeAdbConnectionState {
        return when {
            isConnected -> NativeAdbConnectionState.CONNECTED
            isConnecting -> NativeAdbConnectionState.CONNECTING
            !isWifiConnected -> NativeAdbConnectionState.WIFI_REQUIRED
            else -> NativeAdbConnectionState.DISCONNECTED
        }
    }

    fun isAdbReady(): Boolean = _isConnected.value

    fun isWifiConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            false
        }
    }

    fun getLocalWifiIpAddress(context: Context): String? {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val linkProps: LinkProperties? = if (network != null) cm.getLinkProperties(network) else null
            linkProps?.linkAddresses?.forEach { linkAddr ->
                val addr = linkAddr.address
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val host = addr.hostAddress
                    if (!host.isNullOrBlank()) return host
                }
            }
        } catch (_: Exception) {
        }
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank()) return host
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun getSystemWirelessDebugPort(context: Context): Int? {
        return try {
            val port = Settings.Global.getInt(context.contentResolver, "adb_wifi_server_port", -1)
            if (port in 1024..65535) port else null
        } catch (_: Exception) {
            null
        }
    }

    fun isWirelessDebuggingSettingEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, "adb_enabled", 0) == 1 ||
                Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    fun refreshConnectionStatus(context: Context) {
        appContext = context.applicationContext
        val wifiOk = isWifiConnected(context)
        if (!wifiOk && _isConnected.value && _connectedHost.value != "127.0.0.1") {
            _isConnected.value = false
        }
        val state = resolveConnectionState(
            isWifiConnected = wifiOk,
            isConnecting = _connectionState.value == NativeAdbConnectionState.CONNECTING,
            isConnected = _isConnected.value
        )
        _connectionState.value = state
    }

    fun disconnect(userInitiated: Boolean = true) {
        _isConnected.value = false
        val ctx = appContext
        if (ctx != null) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_USER_STOPPED, userInitiated)
                .apply()
            _connectionState.value = resolveConnectionState(
                isWifiConnected = isWifiConnected(ctx),
                isConnecting = false,
                isConnected = false
            )
        } else {
            _connectionState.value = NativeAdbConnectionState.DISCONNECTED
        }
        _statusMessage.value = if (userInitiated) {
            "Wireless ADB disconnected."
        } else {
            "Wireless ADB offline."
        }
    }

    suspend fun connectAutomatically(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        init(context)
        connectMutex.withLock {
            if (!isWifiConnected(context)) {
                _isConnected.value = false
                _connectionState.value = NativeAdbConnectionState.WIFI_REQUIRED
                val msg = "Connect to Wi-Fi first to bind Wireless ADB."
                _statusMessage.value = msg
                return@withLock Pair(false, msg)
            }

            _connectionState.value = NativeAdbConnectionState.CONNECTING
            _statusMessage.value = "Detecting local ADB daemon..."

            val candidates = discoverLocalAdbEndpoints(context)
            val keyPair = getOrInitKeyPair(context)

            for (endpoint in candidates) {
                val (ok, detail) = performHandshakeOnly(endpoint.host, endpoint.port, keyPair)
                if (ok) {
                    markConnected(context, endpoint.host, endpoint.port)
                    val msg = "ADB Connected (${endpoint.host}:${endpoint.port})"
                    _statusMessage.value = msg
                    return@withLock Pair(true, msg)
                }
                Log.d(TAG, "Candidate ${endpoint.host}:${endpoint.port} failed: $detail")
            }

            _isConnected.value = false
            _connectionState.value = resolveConnectionState(
                isWifiConnected = isWifiConnected(context),
                isConnecting = false,
                isConnected = false
            )
            val failMsg = "No local ADB daemon accepted the connection. Enable Wireless Debugging in Developer Options or pair once in Mode 1/2."
            _statusMessage.value = failMsg
            Pair(false, failMsg)
        }
    }

    suspend fun connectToEndpoint(
        context: Context,
        host: String,
        port: Int
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        init(context)
        connectMutex.withLock {
            _connectionState.value = NativeAdbConnectionState.CONNECTING
            _statusMessage.value = "Connecting to $host:$port..."
            val keyPair = getOrInitKeyPair(context)
            val (ok, detail) = performHandshakeOnly(host.trim(), port, keyPair)
            if (ok) {
                markConnected(context, host.trim(), port)
                val msg = "ADB Connected (${host.trim()}:$port)"
                _statusMessage.value = msg
                Pair(true, msg)
            } else {
                _isConnected.value = false
                _connectionState.value = resolveConnectionState(
                    isWifiConnected = isWifiConnected(context),
                    isConnecting = false,
                    isConnected = false
                )
                _statusMessage.value = detail
                Pair(false, detail)
            }
        }
    }

    fun executeCommandBlocking(command: String, timeoutMs: Long = 8_000L): NativeShellResult {
        if (!_isConnected.value) {
            return NativeShellResult(false, "Wireless ADB is not connected")
        }
        return runBlocking(Dispatchers.IO) {
            executeShellCommand(command, timeoutMs)
        }
    }

    suspend fun executeShellCommand(
        command: String,
        timeoutMs: Long = 10_000L
    ): NativeShellResult = withContext(Dispatchers.IO) {
        val ctx = appContext
        val host = _connectedHost.value ?: "127.0.0.1"
        var port = _connectedPort.value

        if (port == null || port !in 1024..65535) {
            if (ctx != null) {
                port = getSystemWirelessDebugPort(ctx)
            }
        }
        if (port == null || port !in 1024..65535) {
            port = 5555
        }

        val keyPair = if (ctx != null) getOrInitKeyPair(ctx) else NativeAdbProtocol.loadOrCreateKeyPair(File("/tmp/arwora_adb"))

        // Try TLS first (Android 11+ wireless debugging), then plain TCP (legacy adbd 5555)
        val result = try {
            executeOverSocket(host, port, command, keyPair, useTls = true, timeoutMs = timeoutMs)
        } catch (tlsError: Exception) {
            try {
                executeOverSocket(host, port, command, keyPair, useTls = false, timeoutMs = timeoutMs)
            } catch (tcpError: Exception) {
                _isConnected.value = false
                if (ctx != null) {
                    _connectionState.value = resolveConnectionState(
                        isWifiConnected = isWifiConnected(ctx),
                        isConnecting = false,
                        isConnected = false
                    )
                } else {
                    _connectionState.value = NativeAdbConnectionState.DISCONNECTED
                }
                NativeShellResult(
                    success = false,
                    output = tcpError.message ?: tlsError.message ?: "ADB command failed",
                    exitCode = -1
                )
            }
        }
        result
    }

    suspend fun discoverLocalAdbEndpoints(context: Context): List<NativeAdbEndpoint> = withContext(Dispatchers.IO) {
        val endpoints = LinkedHashSet<NativeAdbEndpoint>()
        val wifiIp = getLocalWifiIpAddress(context)

        fun addForLocalHosts(port: Int) {
            if (port !in 1024..65535) return
            endpoints.add(NativeAdbEndpoint("127.0.0.1", port))
            if (!wifiIp.isNullOrBlank() && wifiIp != "127.0.0.1") {
                endpoints.add(NativeAdbEndpoint(wifiIp, port))
            }
        }

        // 1. Live Wireless Debugging port from Settings.Global (Android 11+)
        getSystemWirelessDebugPort(context)?.let { addForLocalHosts(it) }

        // 2. Previously successful ADB port cached in preferences
        val accessPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedHost = accessPrefs.getString(KEY_LAST_HOST, null)
        val cachedPort = accessPrefs.getInt(KEY_LAST_PORT, -1)
        if (!cachedHost.isNullOrBlank() && cachedPort in 1024..65535) {
            endpoints.add(NativeAdbEndpoint(cachedHost, cachedPort))
        }

        val pairingPrefs = context.getSharedPreferences("wireless_pairing_prefs", Context.MODE_PRIVATE)
        val savedPairingConnectPort = pairingPrefs.getInt("saved_connect_port", -1)
        if (savedPairingConnectPort in 1024..65535) {
            addForLocalHosts(savedPairingConnectPort)
        }

        // 3. Brief mDNS discovery for _adb-tls-connect._tcp.
        discoverNsdConnectEndpoints(context, timeoutMs = 1200L).forEach { endpoints.add(it) }

        // 4. Local socket proc table inspection (ports 5555 or ephemeral wireless debugging >= 30000)
        readProcTcpListeningPorts().forEach { port ->
            if (port == 5555 || port >= 30000) {
                addForLocalHosts(port)
            }
        }

        // 5. Standard TCP/IP fallback port 5555 on loopback & Wi-Fi IP
        addForLocalHosts(5555)

        endpoints.toList()
    }

    private suspend fun discoverNsdConnectEndpoints(
        context: Context,
        timeoutMs: Long
    ): List<NativeAdbEndpoint> {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return emptyList()
        val deferred = CompletableDeferred<NativeAdbEndpoint?>()
        val resolved = AtomicBoolean(false)

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                val info = serviceInfo ?: return
                try {
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                            val host = resolvedInfo?.host?.hostAddress ?: return
                            val port = resolvedInfo.port
                            if (port in 1024..65535 && resolved.compareAndSet(false, true)) {
                                deferred.complete(NativeAdbEndpoint(host, port))
                            }
                        }
                    })
                } catch (_: Exception) {
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
            override fun onDiscoveryStopped(serviceType: String?) {}
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                if (resolved.compareAndSet(false, true)) deferred.complete(null)
            }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
        }

        return try {
            nsdManager.discoverServices(NSD_CONNECT_SERVICE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            val hit = withTimeoutOrNull(timeoutMs) { deferred.await() }
            if (hit != null) listOf(hit) else emptyList()
        } catch (_: Exception) {
            emptyList()
        } finally {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (_: Exception) {
            }
        }
    }

    private fun readProcTcpListeningPorts(): List<Int> {
        val result = LinkedHashSet<Int>()
        val files = listOf("/proc/net/tcp", "/proc/net/tcp6")
        for (path in files) {
            try {
                val file = File(path)
                if (!file.exists() || !file.canRead()) continue
                val content = file.readText()
                WirelessPairingManager.parseProcNetTcpListeningPorts(content, minPort = 1024)
                    .forEach { result.add(it) }
            } catch (_: Exception) {
            }
        }
        return result.toList()
    }

    private fun markConnected(context: Context, host: String, port: Int) {
        _isConnected.value = true
        _connectedHost.value = host
        _connectedPort.value = port
        _connectionState.value = NativeAdbConnectionState.CONNECTED
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_HOST, host)
            .putInt(KEY_LAST_PORT, port)
            .putBoolean(KEY_USER_STOPPED, false)
            .apply()
    }

    private fun getOrInitKeyPair(context: Context): KeyPair {
        cachedKeyPair?.let { return it }
        val pair = NativeAdbProtocol.loadOrCreateKeyPair(File(context.filesDir, "adb_keys"))
        cachedKeyPair = pair
        return pair
    }

    private fun performHandshakeOnly(
        host: String,
        port: Int,
        keyPair: KeyPair
    ): Pair<Boolean, String> {
        // Prefer TLS for Android 11+ wireless debugging ports, fallback cleanly to plain TCP
        return try {
            openAndAuthenticateSocket(host, port, keyPair, useTls = true, HANDSHAKE_TIMEOUT_MS).use {
                Pair(true, "Connected via Wireless ADB TLS ($host:$port)")
            }
        } catch (tlsEx: Exception) {
            try {
                openAndAuthenticateSocket(host, port, keyPair, useTls = false, HANDSHAKE_TIMEOUT_MS).use {
                    Pair(true, "Connected via Local ADB TCP ($host:$port)")
                }
            } catch (tcpEx: Exception) {
                Pair(false, tcpEx.message ?: tlsEx.message ?: "Connection refused on $host:$port")
            }
        }
    }

    private fun openAndAuthenticateSocket(
        host: String,
        port: Int,
        keyPair: KeyPair,
        useTls: Boolean,
        timeoutMs: Int
    ): Socket {
        val rawSocket = Socket()
        rawSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        rawSocket.soTimeout = timeoutMs

        val socket: Socket = if (useTls) {
            val sslFactory = createTrustAllSslContext().socketFactory
            val sslSocket = sslFactory.createSocket(rawSocket, host, port, true) as SSLSocket
            sslSocket.soTimeout = timeoutMs
            sslSocket.startHandshake()
            sslSocket
        } else {
            rawSocket
        }

        try {
            performAdbAuthHandshake(socket.getInputStream(), socket.getOutputStream(), keyPair)
            return socket
        } catch (e: Exception) {
            try {
                socket.close()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    private fun performAdbAuthHandshake(
        input: InputStream,
        output: OutputStream,
        keyPair: KeyPair
    ) {
        val clientIdentity = ensureClientIdentity()
        NativeAdbProtocol.writePacket(
            output,
            NativeAdbProtocol.A_CNXN,
            NativeAdbProtocol.A_VERSION,
            NativeAdbProtocol.MAX_PAYLOAD,
            "host::\u0000".toByteArray(Charsets.UTF_8)
        )

        var sentSignature = false
        var sentPublicKey = false

        while (true) {
            val response = NativeAdbProtocol.readPacket(input)
            when (response.command) {
                NativeAdbProtocol.A_CNXN -> return
                NativeAdbProtocol.A_AUTH -> {
                    if (response.arg0 == NativeAdbProtocol.AUTH_TOKEN) {
                        if (!sentSignature) {
                            sentSignature = true
                            val sig = NativeAdbProtocol.signToken(response.data, keyPair.private)
                            NativeAdbProtocol.writePacket(
                                output,
                                NativeAdbProtocol.A_AUTH,
                                NativeAdbProtocol.AUTH_SIGNATURE,
                                0,
                                sig
                            )
                        } else if (!sentPublicKey) {
                            sentPublicKey = true
                            val rsaPub = keyPair.public as RSAPublicKey
                            val pubPayload = NativeAdbProtocol.formatAdbPublicKeyPayload(
                                publicKey = rsaPub,
                                comment = clientIdentity
                            )
                            NativeAdbProtocol.writePacket(
                                output,
                                NativeAdbProtocol.A_AUTH,
                                NativeAdbProtocol.AUTH_RSAPUBLICKEY,
                                0,
                                pubPayload
                            )
                        } else {
                            throw IllegalStateException("ADB authentication rejected")
                        }
                    } else {
                        throw IllegalStateException("Unexpected ADB AUTH arg0=${response.arg0}")
                    }
                }
                else -> throw IllegalStateException("Unexpected ADB handshake command=0x${response.command.toString(16)}")
            }
        }
    }

    private fun executeOverSocket(
        host: String,
        port: Int,
        command: String,
        keyPair: KeyPair,
        useTls: Boolean,
        timeoutMs: Long
    ): NativeShellResult {
        openAndAuthenticateSocket(host, port, keyPair, useTls, timeoutMs.toInt()).use { socket ->
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val localId = streamIdGen.getAndIncrement()

            val service = "shell:$command\u0000".toByteArray(Charsets.UTF_8)
            NativeAdbProtocol.writePacket(output, NativeAdbProtocol.A_OPEN, localId, 0, service)

            var remoteId = 0
            val outBuffer = ByteArrayOutputStream()

            while (true) {
                val pkt = NativeAdbProtocol.readPacket(input)
                when (pkt.command) {
                    NativeAdbProtocol.A_OKAY -> {
                        if (pkt.arg1 == localId) {
                            remoteId = pkt.arg0
                        }
                    }
                    NativeAdbProtocol.A_WRTE -> {
                        if (pkt.arg1 == localId) {
                            remoteId = pkt.arg0
                            if (pkt.data.isNotEmpty()) {
                                outBuffer.write(pkt.data)
                            }
                            NativeAdbProtocol.writePacket(output, NativeAdbProtocol.A_OKAY, localId, remoteId)
                        }
                    }
                    NativeAdbProtocol.A_CLSE -> {
                        if (pkt.arg1 == localId) {
                            if (remoteId != 0) {
                                NativeAdbProtocol.writePacket(output, NativeAdbProtocol.A_CLSE, localId, remoteId)
                            }
                            break
                        }
                    }
                }
            }

            val text = outBuffer.toString("UTF-8").trimEnd()
            return NativeShellResult(success = true, output = text, exitCode = 0)
        }
    }

    private fun createTrustAllSslContext(): SSLContext {
        val trustManagers = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
        return SSLContext.getInstance("TLS").apply {
            init(null, trustManagers, SecureRandom())
        }
    }
}
