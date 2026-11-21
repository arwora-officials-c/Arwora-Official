package com.arwora.utils

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
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
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509KeyManager
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

data class WifiTrustState(
    val ssid: String?,
    val networkKey: String,
    val isTrusted: Boolean,
    val requiresPrompt: Boolean = false
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

    private const val NSD_CONNECT_SERVICE = "_adb-tls-connect._tcp"
    internal const val NSD_CONNECT_DISCOVERY_TIMEOUT_MS = 3_000L
    const val ERROR_WIRELESS_DEBUGGING_OFF =
        "Wireless Debugging is OFF or not paired yet. Turn on Wireless Debugging in Developer Options first."

    private const val CONNECT_TIMEOUT_MS = 1_500
    private const val PROBE_TIMEOUT_MS = 350
    private const val HANDSHAKE_TIMEOUT_MS = 3_000

    private val streamIdGen = AtomicInteger(1)
    private val connectMutex = Mutex()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var cachedKeyPair: KeyPair? = null

    // Test hook allowing unit tests to inject a deterministic mDNS resolver without mocking NsdManager internals
    internal var nsdConnectResolverOverride: (suspend (Context, Long) -> List<NativeAdbEndpoint>)? = null
    internal var wifiNetworkResolverOverride: ((Context) -> Pair<String?, String?>)? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _connectionState = MutableStateFlow(NativeAdbConnectionState.DISCONNECTED)
    val connectionState: StateFlow<NativeAdbConnectionState> = _connectionState.asStateFlow()

    private val _connectedHost = MutableStateFlow<String?>(null)
    val connectedHost: StateFlow<String?> = _connectedHost.asStateFlow()

    private val _connectedPort = MutableStateFlow<Int?>(null)
    val connectedPort: StateFlow<Int?> = _connectedPort.asStateFlow()

    private val _discoveredPort = MutableStateFlow<Int?>(null)
    val discoveredPort: StateFlow<Int?> = _discoveredPort.asStateFlow()

    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val _isSessionForgotten = MutableStateFlow(false)
    val isSessionForgotten: StateFlow<Boolean> = _isSessionForgotten.asStateFlow()

    private val _isUsbDebuggingEnabled = MutableStateFlow(false)
    val isUsbDebuggingEnabled: StateFlow<Boolean> = _isUsbDebuggingEnabled.asStateFlow()

    private val _isWirelessDebuggingEnabled = MutableStateFlow(false)
    val isWirelessDebuggingEnabled: StateFlow<Boolean> = _isWirelessDebuggingEnabled.asStateFlow()

    private val _isWirelessOnlyEnabled = MutableStateFlow(false)
    val isWirelessOnlyEnabled: StateFlow<Boolean> = _isWirelessOnlyEnabled.asStateFlow()

    private val _requiresDevOptionsGuidance = MutableStateFlow(false)
    val requiresDevOptionsGuidance: StateFlow<Boolean> = _requiresDevOptionsGuidance.asStateFlow()

    private val _activeWifiSsid = MutableStateFlow<String?>(null)
    val activeWifiSsid: StateFlow<String?> = _activeWifiSsid.asStateFlow()

    private val _isCurrentWifiTrusted = MutableStateFlow(false)
    val isCurrentWifiTrusted: StateFlow<Boolean> = _isCurrentWifiTrusted.asStateFlow()

    private val _pendingWifiTrustPrompt = MutableStateFlow<WifiTrustState?>(null)
    val pendingWifiTrustPrompt: StateFlow<WifiTrustState?> = _pendingWifiTrustPrompt.asStateFlow()

    private val _statusMessage = MutableStateFlow("Tap Connect Now when Wi-Fi & Wireless Debugging are enabled.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    fun ensureClientIdentity(): String {
        runCatching { System.setProperty("adb.client.name", CLIENT_IDENTITY) }
        return CLIENT_IDENTITY
    }

    fun init(context: Context) {
        ensureClientIdentity()
        val appCtx = context.applicationContext
        appContext = appCtx
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val creds = EncryptedAdbCredentialStore.loadState(appCtx)

        val savedHost = creds.host.takeIf { it.isNotBlank() }
            ?: prefs.getString(KEY_LAST_HOST, null)
        val savedPort = creds.connectPort.takeIf { it in 1024..65535 }
            ?: prefs.getInt(KEY_LAST_PORT, -1).takeIf { it in 1024..65535 }
            ?: -1

        if (_connectedHost.value == null && !savedHost.isNullOrBlank() && savedPort in 1024..65535) {
            _connectedHost.value = savedHost
            _connectedPort.value = savedPort
        }
        _isPaired.value = creds.isPaired
        checkWirelessDebuggingState(appCtx)
        refreshConnectionStatus(appCtx)
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

    fun isUsbDebuggingSettingEnabled(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    fun isWirelessOnlyDebuggingSettingEnabled(context: Context): Boolean {
        return try {
            val adbWifiEnabled = Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
            val sysPort = getSystemWirelessDebugPort(context) != null
            adbWifiEnabled || sysPort || _isConnected.value
        } catch (_: Exception) {
            false
        }
    }

    fun isWirelessDebuggingSettingEnabled(context: Context): Boolean {
        return isUsbDebuggingSettingEnabled(context) || isWirelessOnlyDebuggingSettingEnabled(context)
    }

    fun checkWirelessDebuggingState(context: Context): Boolean {
        val usbEnabled = isUsbDebuggingSettingEnabled(context)
        val wirelessOnlyEnabled = isWirelessOnlyDebuggingSettingEnabled(context)
        val enabled = usbEnabled || wirelessOnlyEnabled
        _isUsbDebuggingEnabled.value = usbEnabled
        _isWirelessOnlyEnabled.value = wirelessOnlyEnabled
        _isWirelessDebuggingEnabled.value = enabled
        val creds = runCatching { EncryptedAdbCredentialStore.loadState(context) }.getOrNull()
        if (creds != null) {
            _isPaired.value = creds.isPaired
        }
        if (enabled && _requiresDevOptionsGuidance.value) {
            _requiresDevOptionsGuidance.value = false
            if (!_isConnected.value && _connectionState.value != NativeAdbConnectionState.CONNECTING) {
                _statusMessage.value = "Wireless Debugging is Active / Ready."
            }
        }
        return enabled
    }

    fun openDeveloperOptions(context: Context): Boolean {
        return try {
            val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(devIntent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun toggleUsbDebugging(
        context: Context,
        targetEnabled: Boolean? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val desired = targetEnabled ?: !isUsbDebuggingSettingEnabled(appCtx)
        val targetInt = if (desired) 1 else 0

        val shellRes = if (AdbShellExecutor.isPrivilegedShellReady(appCtx)) {
            AdbShellExecutor.execute("settings put global adb_enabled $targetInt", context = appCtx)
        } else {
            NativeShellResult(false, "Privilege engine inactive", -1)
        }

        val secureSettingsOk = if (shellRes.success) {
            true
        } else {
            runCatching {
                Settings.Global.putInt(appCtx.contentResolver, Settings.Global.ADB_ENABLED, targetInt)
            }.getOrDefault(false)
        }

        checkWirelessDebuggingState(appCtx)
        if (secureSettingsOk) {
            val label = if (desired) "USB Debugging enabled" else "USB Debugging disabled"
            _statusMessage.value = label
            Pair(true, label)
        } else {
            val msg = "Connect Shizuku or Wireless ADB to toggle USB Debugging directly, or switch it in Developer Options."
            Pair(false, msg)
        }
    }

    suspend fun toggleWirelessDebugging(
        context: Context,
        targetEnabled: Boolean? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val desired = targetEnabled ?: !isWirelessOnlyDebuggingSettingEnabled(appCtx)
        val targetInt = if (desired) 1 else 0

        val shellRes = if (AdbShellExecutor.isPrivilegedShellReady(appCtx)) {
            AdbShellExecutor.execute("settings put global adb_wifi_enabled $targetInt", context = appCtx)
        } else {
            NativeShellResult(false, "Privilege engine inactive", -1)
        }

        val secureSettingsOk = if (shellRes.success) {
            true
        } else {
            runCatching {
                Settings.Global.putInt(appCtx.contentResolver, "adb_wifi_enabled", targetInt)
            }.getOrDefault(false)
        }

        checkWirelessDebuggingState(appCtx)
        if (secureSettingsOk) {
            val label = if (desired) "Wireless Debugging enabled" else "Wireless Debugging disabled"
            _statusMessage.value = label
            Pair(true, label)
        } else {
            val msg = "Connect Shizuku or Wireless ADB to toggle Wireless Debugging directly, or open Wireless Debugging settings."
            Pair(false, msg)
        }
    }

    internal fun isAuthRejectionError(detail: String?): Boolean {
        if (detail.isNullOrBlank()) return false
        val lower = detail.lowercase()
        return lower.contains("authentication rejected") ||
            lower.contains("bad_certificate") ||
            lower.contains("certificate_unknown") ||
            lower.contains("certificate_revoked") ||
            lower.contains("peer not authenticated") ||
            lower.contains("sslhandshake") ||
            lower.contains("untrusted")
    }

    private fun handleForgottenSessionDetected(context: Context, reason: String) {
        val appCtx = context.applicationContext
        appContext = appCtx
        Log.w(TAG, "ADB pairing rejected or forgotten by system ($reason); clearing stale pairing caches.")
        cachedKeyPair = null
        EncryptedAdbCredentialStore.clearPairingCredentials(appCtx)
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_LAST_HOST)
            .remove(KEY_LAST_PORT)
            .putBoolean(KEY_USER_STOPPED, false)
            .apply()
        com.arwora.service.AdbConnectionService.stop(appCtx)
        WirelessPairingManager.clearCachedPairingState(appCtx)
        _isConnected.value = false
        _isPaired.value = false
        _isSessionForgotten.value = true
        _connectedHost.value = null
        _connectedPort.value = null
        _discoveredPort.value = null
        _requiresDevOptionsGuidance.value = false
        _connectionState.value = resolveConnectionState(
            isWifiConnected = isWifiConnected(appCtx),
            isConnecting = false,
            isConnected = false
        )
        _statusMessage.value = "Device pairing was removed or rejected. Tap Pair New Device to generate a fresh pairing keypair."
    }

    /**
     * Explicitly clears stale ADB credentials, stops background reconnect loops,
     * deletes & regenerates the RSA-2048 pairing keypair (`arwora`), and resets state
     * so users can immediately re-pair after forgetting the device in Developer Options.
     */
    fun forgetPairedDeviceAndReset(context: Context): KeyPair {
        val appCtx = context.applicationContext
        appContext = appCtx
        com.arwora.service.AdbConnectionService.stop(appCtx)
        cachedKeyPair = null
        val freshKeyPair = NativeAdbProtocol.resetKeyPair(File(appCtx.filesDir, "adb_keys"))
        cachedKeyPair = freshKeyPair
        EncryptedAdbCredentialStore.clearPairingCredentials(appCtx)
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_LAST_HOST)
            .remove(KEY_LAST_PORT)
            .remove(KEY_USER_STOPPED)
            .apply()
        WirelessPairingManager.clearCachedPairingState(appCtx)

        _isConnected.value = false
        _isPaired.value = false
        _isSessionForgotten.value = false
        _connectedHost.value = null
        _connectedPort.value = null
        _discoveredPort.value = null
        _requiresDevOptionsGuidance.value = false
        _connectionState.value = resolveConnectionState(
            isWifiConnected = isWifiConnected(appCtx),
            isConnecting = false,
            isConnected = false
        )
        _statusMessage.value = "Pairing keys regenerated — ready to pair new device."
        return freshKeyPair
    }

    fun markPairedState(context: Context, host: String? = null, pairingPort: Int? = null) {
        val appCtx = context.applicationContext
        appContext = appCtx
        val keyPair = runCatching { getOrInitKeyPair(appCtx) }.getOrNull()
        val fingerprint = keyPair?.let { EncryptedAdbCredentialStore.computeKeyFingerprint(it) }
        EncryptedAdbCredentialStore.markPaired(
            context = appCtx,
            host = host,
            pairingPort = pairingPort,
            rsaFingerprint = fingerprint
        )
        _isPaired.value = true
        _isSessionForgotten.value = false
    }

    fun resolveActiveWifiIdentity(context: Context): Pair<String?, String?> {
        wifiNetworkResolverOverride?.let { overrideFn ->
            val (rawSsid, rawKey) = overrideFn(context)
            val cleanSsid = EncryptedAdbCredentialStore.normalizeWifiNetworkKey(rawSsid)
            val cleanKey = EncryptedAdbCredentialStore.normalizeWifiNetworkKey(rawKey)
                ?: cleanSsid?.let { "ssid:$it" }
            return Pair(cleanSsid, cleanKey)
        }

        if (!isWifiConnected(context)) {
            return Pair(null, null)
        }

        var rawSsid: String? = null
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val caps = if (network != null) cm.getNetworkCapabilities(network) else null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wifiInfo = caps?.transportInfo as? WifiInfo
                rawSsid = wifiInfo?.ssid
            }
        } catch (_: Throwable) {
        }

        if (EncryptedAdbCredentialStore.normalizeWifiNetworkKey(rawSsid) == null) {
            try {
                @Suppress("DEPRECATION")
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                rawSsid = wm?.connectionInfo?.ssid
            } catch (_: Throwable) {
            }
        }

        val cleanSsid = EncryptedAdbCredentialStore.normalizeWifiNetworkKey(rawSsid)
        val wifiIp = getLocalWifiIpAddress(context)
        val networkKey = when {
            cleanSsid != null -> "ssid:$cleanSsid"
            !wifiIp.isNullOrBlank() && wifiIp != "127.0.0.1" -> {
                val prefix = wifiIp.substringBeforeLast('.')
                "subnet:$prefix.0/24"
            }
            else -> "wifi_active_network"
        }
        return Pair(cleanSsid, networkKey)
    }

    fun refreshWifiTrustState(context: Context): WifiTrustState? {
        val appCtx = context.applicationContext
        val (ssid, networkKey) = resolveActiveWifiIdentity(appCtx)
        _activeWifiSsid.value = ssid
        if (networkKey == null) {
            _isCurrentWifiTrusted.value = false
            _pendingWifiTrustPrompt.value = null
            return null
        }
        val trusted = EncryptedAdbCredentialStore.isWifiNetworkTrusted(appCtx, networkKey)
        _isCurrentWifiTrusted.value = trusted
        if (trusted && _pendingWifiTrustPrompt.value?.networkKey == networkKey) {
            _pendingWifiTrustPrompt.value = null
        }
        return WifiTrustState(
            ssid = ssid,
            networkKey = networkKey,
            isTrusted = trusted,
            requiresPrompt = _pendingWifiTrustPrompt.value?.requiresPrompt == true && !trusted
        )
    }

    fun trustCurrentWifiNetwork(context: Context, alwaysAllow: Boolean = true): WifiTrustState? {
        val appCtx = context.applicationContext
        val (ssid, networkKey) = resolveActiveWifiIdentity(appCtx)
        if (networkKey != null && alwaysAllow) {
            EncryptedAdbCredentialStore.markWifiNetworkTrusted(appCtx, networkKey, ssid)
            _isCurrentWifiTrusted.value = true
        }
        _pendingWifiTrustPrompt.value = null
        return networkKey?.let {
            WifiTrustState(ssid = ssid, networkKey = it, isTrusted = alwaysAllow, requiresPrompt = false)
        }
    }

    fun requestWifiTrustPromptIfNeeded(context: Context): WifiTrustState? {
        val appCtx = context.applicationContext
        val (ssid, networkKey) = resolveActiveWifiIdentity(appCtx)
        if (networkKey == null) return null
        val alreadyTrusted = EncryptedAdbCredentialStore.isWifiNetworkTrusted(appCtx, networkKey)
        _activeWifiSsid.value = ssid
        _isCurrentWifiTrusted.value = alreadyTrusted
        if (alreadyTrusted) {
            _pendingWifiTrustPrompt.value = null
            return WifiTrustState(ssid = ssid, networkKey = networkKey, isTrusted = true, requiresPrompt = false)
        }
        val prompt = WifiTrustState(ssid = ssid, networkKey = networkKey, isTrusted = false, requiresPrompt = true)
        _pendingWifiTrustPrompt.value = prompt
        return prompt
    }

    fun dismissWifiTrustPrompt() {
        _pendingWifiTrustPrompt.value = null
    }

    fun openWirelessDebuggingSettings(context: Context): Boolean {
        return try {
            val wirelessDebugIntent = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS").apply {
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(wirelessDebugIntent)
            true
        } catch (_: Throwable) {
            try {
                val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                context.startActivity(devIntent)
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    fun refreshConnectionStatus(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        val wifiOk = isWifiConnected(appCtx)
        checkWirelessDebuggingState(appCtx)
        refreshWifiTrustState(appCtx)
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
        _requiresDevOptionsGuidance.value = false
        val ctx = appContext
        if (ctx != null) {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_USER_STOPPED, userInitiated)
                .apply()
            EncryptedAdbCredentialStore.setUserStopped(ctx, userInitiated)
            _connectionState.value = resolveConnectionState(
                isWifiConnected = isWifiConnected(ctx),
                isConnecting = false,
                isConnected = false
            )
            if (userInitiated) {
                runCatching {
                    ctx.stopService(android.content.Intent(ctx, com.arwora.service.AdbConnectionService::class.java))
                }
            }
        } else {
            _connectionState.value = NativeAdbConnectionState.DISCONNECTED
        }
        _statusMessage.value = if (userInitiated) {
            "Wireless ADB disconnected."
        } else {
            "Wireless ADB offline."
        }
    }

    /**
     * Post-pairing transition flow:
     * Explicitly excludes [pairingPort], queries `_adb-tls-connect._tcp` via NsdManager (with a brief retry
     * while adbd switches from pairing listener to wireless connect listener), connects with the saved RSA
     * keypair as `arwora`, and verifies a real ADB shell stream before marking Active / Connected.
     */
    suspend fun connectAfterPairing(
        context: Context,
        pairingPort: Int? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        init(context)
        connectMutex.withLock {
            if (!isWifiConnected(context)) {
                _isConnected.value = false
                _requiresDevOptionsGuidance.value = false
                _connectionState.value = NativeAdbConnectionState.WIFI_REQUIRED
                val msg = "Connect to Wi-Fi first to bind Wireless ADB."
                _statusMessage.value = msg
                return@withLock Pair(false, msg)
            }

            _isConnected.value = false
            _requiresDevOptionsGuidance.value = false
            _discoveredPort.value = null
            if (_connectedPort.value == pairingPort) {
                _connectedPort.value = null
            }
            _connectionState.value = NativeAdbConnectionState.CONNECTING
            _statusMessage.value = "Paired successfully! Connecting ADB..."

            // Give adbd a short window to publish _adb-tls-connect._tcp after pairing finishes, retrying cleanly on real devices.
            var candidates = discoverLocalAdbEndpoints(
                context = context,
                excludePort = pairingPort,
                nsdTimeoutMs = 2_500L
            )
            if (candidates.isEmpty()) {
                kotlinx.coroutines.delay(if (nsdConnectResolverOverride != null) 50L else 300L)
                candidates = discoverLocalAdbEndpoints(
                    context = context,
                    excludePort = pairingPort,
                    nsdTimeoutMs = NSD_CONNECT_DISCOVERY_TIMEOUT_MS
                )
            }
            if (candidates.isEmpty() && nsdConnectResolverOverride == null) {
                kotlinx.coroutines.delay(450L)
                candidates = discoverLocalAdbEndpoints(
                    context = context,
                    excludePort = pairingPort,
                    nsdTimeoutMs = NSD_CONNECT_DISCOVERY_TIMEOUT_MS
                )
            }

            val debugSettingOn = checkWirelessDebuggingState(context)

            if (candidates.isEmpty()) {
                _isConnected.value = false
                _discoveredPort.value = null
                _connectedPort.value = null
                _requiresDevOptionsGuidance.value = !debugSettingOn
                requestWifiTrustPromptIfNeeded(context)
                _connectionState.value = resolveConnectionState(
                    isWifiConnected = isWifiConnected(context),
                    isConnecting = false,
                    isConnected = false
                )
                val timeoutMsg = if (debugSettingOn) {
                    "Wireless Debugging is active, but the connection port timed out. Verify Wi-Fi network trust and tap Retry Connect."
                } else {
                    "Timed out resolving Wireless Debugging connection port after pairing."
                }
                _statusMessage.value = timeoutMsg
                return@withLock Pair(false, timeoutMsg)
            }

            val keyPair = getOrInitKeyPair(context)
            var lastFailureDetail: String? = null

            for (endpoint in candidates) {
                if (pairingPort != null && endpoint.port == pairingPort) continue
                _discoveredPort.value = endpoint.port
                _connectedHost.value = endpoint.host
                _connectedPort.value = endpoint.port
                _statusMessage.value = "Connecting to ${endpoint.host}:${endpoint.port} as $CLIENT_IDENTITY..."

                val (ok, detail) = performVerifiedHandshake(endpoint.host, endpoint.port, keyPair)
                if (ok) {
                    markConnected(context, endpoint.host, endpoint.port, pairingPort = pairingPort)
                    val msg = "Active / Connected (${endpoint.host}:${endpoint.port})"
                    _statusMessage.value = msg
                    return@withLock Pair(true, msg)
                }
                lastFailureDetail = detail
                Log.d(TAG, "Post-pairing candidate ${endpoint.host}:${endpoint.port} failed: $detail")
            }

            _isConnected.value = false
            _discoveredPort.value = null
            _connectedPort.value = null
            _requiresDevOptionsGuidance.value = !debugSettingOn
            _connectionState.value = resolveConnectionState(
                isWifiConnected = isWifiConnected(context),
                isConnecting = false,
                isConnected = false
            )
            val failMsg = lastFailureDetail ?: "Timed out connecting to Wireless Debugging port."
            val statusErr = if (debugSettingOn) {
                "Wireless Debugging is Active / Ready, but ADB socket refused connection ($failMsg)."
            } else {
                ERROR_WIRELESS_DEBUGGING_OFF
            }
            _statusMessage.value = statusErr
            Log.w(TAG, "Post-pairing connect failed across ${candidates.size} candidate(s): $failMsg")
            Pair(false, statusErr)
        }
    }

    /**
     * Unified real Wireless ADB connection flow used by both top "Connect Now" and Mode 3 "Connect Now".
     * 1. Scans `_adb-tls-connect._tcp` via NsdManager for up to 4 seconds to resolve active Wi-Fi IP & dynamic TLS port.
     * 2. Opens a real TLS/ADB socket using the saved RSA keypair with client identity `"arwora"`.
     * 3. Verifies a working ADB shell stream (`echo connected && id`) BEFORE marking state Connected / Active.
     */
    suspend fun connectWirelessAdb(
        context: Context,
        excludePairingPort: Int? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        init(context)
        connectMutex.withLock {
            if (!isWifiConnected(context)) {
                _isConnected.value = false
                _requiresDevOptionsGuidance.value = false
                _connectionState.value = NativeAdbConnectionState.WIFI_REQUIRED
                val msg = "Connect to Wi-Fi first to bind Wireless ADB."
                _statusMessage.value = msg
                return@withLock Pair(false, msg)
            }

            _isConnected.value = false
            _requiresDevOptionsGuidance.value = false
            _discoveredPort.value = null
            _connectionState.value = NativeAdbConnectionState.CONNECTING
            _statusMessage.value = "Scanning mDNS (_adb-tls-connect._tcp) for Wireless Debugging..."

            val debugSettingOn = checkWirelessDebuggingState(context)
            val candidates = discoverLocalAdbEndpoints(context, excludePort = excludePairingPort)
            if (candidates.isEmpty()) {
                _isConnected.value = false
                _discoveredPort.value = null
                _connectedPort.value = null
                _requiresDevOptionsGuidance.value = !debugSettingOn
                requestWifiTrustPromptIfNeeded(context)
                _connectionState.value = resolveConnectionState(
                    isWifiConnected = isWifiConnected(context),
                    isConnecting = false,
                    isConnected = false
                )
                val errMsg = if (debugSettingOn) {
                    val ssidLabel = _activeWifiSsid.value?.let { " on $it" } ?: ""
                    "Wireless Debugging is Active / Ready — allow Wireless Debugging$ssidLabel to expose the ADB connect port."
                } else {
                    ERROR_WIRELESS_DEBUGGING_OFF
                }
                _statusMessage.value = errMsg
                return@withLock Pair(false, errMsg)
            }

            val keyPair = getOrInitKeyPair(context)
            var lastFailureDetail: String? = null

            for (endpoint in candidates) {
                _discoveredPort.value = endpoint.port
                _connectedHost.value = endpoint.host
                _connectedPort.value = endpoint.port
                _statusMessage.value = "Handshaking ADB TLS with ${endpoint.host}:${endpoint.port} as $CLIENT_IDENTITY..."

                val (ok, detail) = performVerifiedHandshake(endpoint.host, endpoint.port, keyPair)
                if (ok) {
                    markConnected(context, endpoint.host, endpoint.port)
                    val msg = "Active / Connected (${endpoint.host}:${endpoint.port})"
                    _statusMessage.value = msg
                    return@withLock Pair(true, msg)
                }
                lastFailureDetail = detail
                Log.d(TAG, "Candidate ${endpoint.host}:${endpoint.port} failed: $detail")
            }

            _isConnected.value = false
            _discoveredPort.value = null
            _connectedPort.value = null
            if (isAuthRejectionError(lastFailureDetail)) {
                handleForgottenSessionDetected(context, lastFailureDetail ?: "authentication rejected")
                return@withLock Pair(false, _statusMessage.value)
            }
            _requiresDevOptionsGuidance.value = !debugSettingOn
            requestWifiTrustPromptIfNeeded(context)
            _connectionState.value = resolveConnectionState(
                isWifiConnected = isWifiConnected(context),
                isConnecting = false,
                isConnected = false
            )
            val errMsg = if (debugSettingOn) {
                "Wireless Debugging is Active / Ready — authorization pending or port rotated (${lastFailureDetail ?: "retry"})."
            } else {
                ERROR_WIRELESS_DEBUGGING_OFF
            }
            _statusMessage.value = errMsg
            Log.w(TAG, "Wireless ADB connection failed across ${candidates.size} candidate(s): $lastFailureDetail")
            Pair(false, errMsg)
        }
    }

    suspend fun connectAutomatically(context: Context, excludePairingPort: Int? = null): Pair<Boolean, String> {
        return connectWirelessAdb(context, excludePairingPort = excludePairingPort)
    }

    /**
     * Silently re-establishes an already-paired Wireless ADB session without requiring user re-pairing.
     * 1. Attempts direct TLS/ADB socket handshake against the persisted host:port from [EncryptedAdbCredentialStore].
     * 2. If the saved port was rotated by adbd, automatically triggers `_adb-tls-connect._tcp` mDNS discovery
     *    via [connectWirelessAdb] using the saved `arwora` RSA keypair.
     */
    suspend fun reconnectSavedSession(
        context: Context,
        force: Boolean = false
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        init(appCtx)
        val creds = EncryptedAdbCredentialStore.loadState(appCtx)
        if (!force && (!creds.isPaired || creds.userStopped)) {
            return@withContext Pair(false, "No active paired ADB session stored for silent reconnect.")
        }
        if (!isWifiConnected(appCtx) && creds.host != "127.0.0.1") {
            refreshConnectionStatus(appCtx)
            return@withContext Pair(false, "Waiting for Wi-Fi connection to resume Wireless ADB.")
        }

        val directOk: Pair<Boolean, String>? = connectMutex.withLock {
            val savedPort = creds.connectPort
            if (savedPort in 1024..65535) {
                val candidateHosts = buildList {
                    if (creds.host.isNotBlank()) add(creds.host)
                    getLocalWifiIpAddress(appCtx)?.takeIf { it.isNotBlank() }?.let { add(it) }
                    add("127.0.0.1")
                }.distinct()

                val keyPair = getOrInitKeyPair(appCtx)
                var rejectedError: String? = null
                for (candidateHost in candidateHosts) {
                    val (ok, detail) = performVerifiedHandshake(candidateHost, savedPort, keyPair)
                    if (ok) {
                        markConnected(appCtx, candidateHost, savedPort)
                        val msg = "Active / Connected ($candidateHost:$savedPort)"
                        _statusMessage.value = msg
                        return@withLock Pair(true, msg)
                    }
                    if (isAuthRejectionError(detail)) {
                        rejectedError = detail
                        break
                    }
                }
                if (rejectedError != null) {
                    handleForgottenSessionDetected(appCtx, rejectedError)
                    return@withLock Pair(false, _statusMessage.value)
                }
            }
            null
        }
        if (directOk != null) {
            return@withContext directOk
        }

        // Saved port rotated or unreachable -> fall back silently to _adb-tls-connect._tcp mDNS re-discovery
        connectWirelessAdb(appCtx, excludePairingPort = creds.pairingPort.takeIf { it in 1024..65535 })
    }

    suspend fun verifyConnectionAlive(): Boolean = withContext(Dispatchers.IO) {
        val ctx = appContext ?: return@withContext _isConnected.value
        val host = _connectedHost.value ?: return@withContext false
        val port = _connectedPort.value ?: return@withContext false
        if (port !in 1024..65535) return@withContext false
        if (isTcpPortReachable(host, port, PROBE_TIMEOUT_MS)) {
            return@withContext true
        }
        // Socket dropped; try silent reconnect if paired and not user-stopped
        val creds = EncryptedAdbCredentialStore.loadState(ctx)
        if (creds.isPaired && !creds.userStopped) {
            return@withContext reconnectSavedSession(ctx).first
        }
        _isConnected.value = false
        refreshConnectionStatus(ctx)
        false
    }

    suspend fun connectToEndpoint(
        context: Context,
        host: String,
        port: Int
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        init(context)
        connectMutex.withLock {
            val cleanHost = host.trim()
            if (cleanHost.isEmpty() || port !in 1024..65535) {
                return@withLock Pair(false, ERROR_WIRELESS_DEBUGGING_OFF)
            }
            val debugSettingOn = checkWirelessDebuggingState(context)
            _requiresDevOptionsGuidance.value = false
            _discoveredPort.value = port
            _connectionState.value = NativeAdbConnectionState.CONNECTING
            _statusMessage.value = "Connecting to $cleanHost:$port..."
            val keyPair = getOrInitKeyPair(context)
            val (ok, detail) = performVerifiedHandshake(cleanHost, port, keyPair)
            if (ok) {
                markConnected(context, cleanHost, port)
                val msg = "Active / Connected ($cleanHost:$port)"
                _statusMessage.value = msg
                Pair(true, msg)
            } else {
                _isConnected.value = false
                _requiresDevOptionsGuidance.value = !debugSettingOn
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
            if (ctx != null) {
                val creds = EncryptedAdbCredentialStore.loadState(ctx)
                if (creds.isPaired && !creds.userStopped && reconnectSavedSession(ctx).first) {
                    val reconnectedHost = _connectedHost.value ?: "127.0.0.1"
                    val reconnectedPort = _connectedPort.value
                    if (reconnectedPort != null && reconnectedPort in 1024..65535) {
                        val kp = getOrInitKeyPair(ctx)
                        return@withContext runCatching {
                            executeOverSocket(reconnectedHost, reconnectedPort, command, kp, useTls = false, timeoutMs = timeoutMs)
                        }.recoverCatching {
                            executeOverSocket(reconnectedHost, reconnectedPort, command, kp, useTls = true, timeoutMs = timeoutMs)
                        }.getOrElse { err ->
                            NativeShellResult(false, err.message ?: ERROR_WIRELESS_DEBUGGING_OFF, -1)
                        }
                    }
                }
            }
            return@withContext NativeShellResult(
                success = false,
                output = ERROR_WIRELESS_DEBUGGING_OFF,
                exitCode = -1
            )
        }

        val keyPair = if (ctx != null) getOrInitKeyPair(ctx) else NativeAdbProtocol.loadOrCreateKeyPair(File("/tmp/arwora_adb"))

        // Try plaintext A_CNXN (which handles inline A_STLS mTLS upgrade on Android 11+), then immediate TLS fallback
        val result = try {
            executeOverSocket(host, port, command, keyPair, useTls = false, timeoutMs = timeoutMs)
        } catch (primaryError: Exception) {
            try {
                executeOverSocket(host, port, command, keyPair, useTls = true, timeoutMs = timeoutMs)
            } catch (tlsError: Exception) {
                val errMsg = tlsError.message ?: primaryError.message ?: "ADB command failed"
                if (ctx != null && isAuthRejectionError(errMsg)) {
                    handleForgottenSessionDetected(ctx, errMsg)
                    return@withContext NativeShellResult(
                        success = false,
                        output = _statusMessage.value,
                        exitCode = -1
                    )
                }

                // Socket dropped -> attempt one silent reconnect via saved session / mDNS if paired
                if (ctx != null) {
                    val creds = EncryptedAdbCredentialStore.loadState(ctx)
                    if (creds.isPaired && !creds.userStopped && !isSessionForgotten.value && reconnectSavedSession(ctx).first) {
                        val newHost = _connectedHost.value ?: host
                        val newPort = _connectedPort.value ?: port
                        val retryResult = runCatching {
                            executeOverSocket(newHost, newPort, command, keyPair, useTls = false, timeoutMs = timeoutMs)
                        }.recoverCatching {
                            executeOverSocket(newHost, newPort, command, keyPair, useTls = true, timeoutMs = timeoutMs)
                        }.getOrNull()
                        if (retryResult != null) {
                            return@withContext retryResult
                        }
                    }
                }

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
                    output = errMsg,
                    exitCode = -1
                )
            }
        }
        result
    }

    suspend fun discoverLocalAdbEndpoints(
        context: Context,
        excludePort: Int? = null,
        nsdTimeoutMs: Long = NSD_CONNECT_DISCOVERY_TIMEOUT_MS
    ): List<NativeAdbEndpoint> = withContext(Dispatchers.IO) {
        val endpoints = LinkedHashSet<NativeAdbEndpoint>()
        val wifiIp = getLocalWifiIpAddress(context)

        fun isAllowedPort(port: Int): Boolean {
            return port in 1024..65535 && (excludePort == null || port != excludePort)
        }

        fun addForLocalHosts(port: Int) {
            if (!isAllowedPort(port)) return
            if (!wifiIp.isNullOrBlank() && wifiIp != "127.0.0.1") {
                endpoints.add(NativeAdbEndpoint(wifiIp, port))
            }
            endpoints.add(NativeAdbEndpoint("127.0.0.1", port))
        }

        // 1. Primary: Active mDNS discovery for `_adb-tls-connect._tcp`
        val mDnsEndpoints = discoverNsdConnectEndpoints(
            context = context,
            timeoutMs = nsdTimeoutMs,
            excludePort = excludePort
        )
        mDnsEndpoints.forEach { ep ->
            if (!isAllowedPort(ep.port)) return@forEach
            endpoints.add(ep)
            _discoveredPort.value = ep.port
            _connectedHost.value = ep.host
            _connectedPort.value = ep.port
            if (!wifiIp.isNullOrBlank() && wifiIp != ep.host && wifiIp != "127.0.0.1") {
                endpoints.add(NativeAdbEndpoint(wifiIp, ep.port))
            }
            if (ep.host != "127.0.0.1") {
                endpoints.add(NativeAdbEndpoint("127.0.0.1", ep.port))
            }
        }

        // 2. Live Wireless Debugging port from Settings.Global (Android 11+ if readable)
        getSystemWirelessDebugPort(context)?.let { port ->
            if (isAllowedPort(port)) {
                if (_discoveredPort.value == null) _discoveredPort.value = port
                addForLocalHosts(port)
            }
        }

        // 3. Previously resolved connect port cached in preferences (strictly excluding pairing port)
        val savedConnectPort = WirelessPairingManager.savedConnectPort.value
        if (savedConnectPort != null && isAllowedPort(savedConnectPort)) {
            addForLocalHosts(savedConnectPort)
        }
        val accessPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedHost = accessPrefs.getString(KEY_LAST_HOST, null)
        val cachedPort = accessPrefs.getInt(KEY_LAST_PORT, -1)
        if (!cachedHost.isNullOrBlank() && isAllowedPort(cachedPort)) {
            endpoints.add(NativeAdbEndpoint(cachedHost, cachedPort))
        }

        // 4. Only scan /proc/net/tcp listening sockets if mDNS / Settings returned nothing,
        //    and verify TCP reachability quickly so unrelated app sockets don't cause connect timeouts.
        if (endpoints.isEmpty()) {
            readProcTcpListeningPorts().forEach { port ->
                if (port >= 30000 && isAllowedPort(port)) {
                    val reachableHost = when {
                        !wifiIp.isNullOrBlank() && wifiIp != "127.0.0.1" && isTcpPortReachable(wifiIp, port) -> wifiIp
                        isTcpPortReachable("127.0.0.1", port) -> "127.0.0.1"
                        else -> null
                    }
                    if (reachableHost != null) {
                        endpoints.add(NativeAdbEndpoint(reachableHost, port))
                    }
                }
            }
        }

        endpoints.toList()
    }

    private fun isTcpPortReachable(host: String, port: Int, timeoutMs: Int = PROBE_TIMEOUT_MS): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun discoverNsdConnectEndpoints(
        context: Context,
        timeoutMs: Long,
        excludePort: Int? = null
    ): List<NativeAdbEndpoint> {
        nsdConnectResolverOverride?.let { overrideFn ->
            return overrideFn(context, timeoutMs).filter { ep ->
                ep.port in 1024..65535 && (excludePort == null || ep.port != excludePort)
            }
        }

        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return emptyList()
        val deferred = CompletableDeferred<NativeAdbEndpoint?>()
        val resolved = AtomicBoolean(false)
        val resolvingInFlight = AtomicBoolean(false)
        val pendingQueue = ArrayDeque<NsdServiceInfo>()
        val queueLock = Any()
        val wifiIpFallback = getLocalWifiIpAddress(context)

        fun resolveNext() {
            if (resolved.get()) return
            val nextInfo = synchronized(queueLock) {
                if (pendingQueue.isEmpty()) {
                    resolvingInFlight.set(false)
                    null
                } else {
                    pendingQueue.removeFirst()
                }
            } ?: return

            try {
                @Suppress("DEPRECATION")
                nsdManager.resolveService(nextInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                        Log.w(TAG, "NSD resolveService failed: $errorCode")
                        resolveNext()
                    }

                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                        val port = resolvedInfo?.port ?: -1
                        if (port !in 1024..65535 || (excludePort != null && port == excludePort)) {
                            Log.d(TAG, "Ignoring resolved NSD port=$port (excluded=$excludePort)")
                            resolveNext()
                            return
                        }

                        val host = extractPreferredHostIp(resolvedInfo, wifiIpFallback)
                        _discoveredPort.value = port
                        _connectedHost.value = host
                        _connectedPort.value = port
                        Log.i(TAG, "Resolved _adb-tls-connect._tcp endpoint: $host:$port")
                        synchronized(queueLock) {
                            pendingQueue.clear()
                            resolvingInFlight.set(false)
                        }
                        if (resolved.compareAndSet(false, true)) {
                            deferred.complete(NativeAdbEndpoint(host, port))
                        }
                    }
                })
            } catch (e: Exception) {
                Log.w(TAG, "Error resolving _adb-tls-connect._tcp service", e)
                resolveNext()
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {
                Log.d(TAG, "NSD connect discovery started: $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                val info = serviceInfo ?: return
                if (resolved.get()) return
                val startImmediately = synchronized(queueLock) {
                    pendingQueue.addLast(info)
                    resolvingInFlight.compareAndSet(false, true)
                }
                if (startImmediately) {
                    resolveNext()
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {}
            override fun onDiscoveryStopped(serviceType: String?) {}
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.w(TAG, "Start _adb-tls-connect._tcp discovery failed: $errorCode")
                if (resolved.compareAndSet(false, true)) deferred.complete(null)
            }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
        }

        val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val multicastLock = runCatching {
            wifiMgr?.createMulticastLock("arwora_adb_mdns_connect")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()

        return try {
            nsdManager.discoverServices(NSD_CONNECT_SERVICE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            val hit = withTimeoutOrNull(timeoutMs) { deferred.await() }
            if (hit != null) listOf(hit) else emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "mDNS _adb-tls-connect._tcp scan exception: ${e.message}")
            emptyList()
        } finally {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (_: Exception) {
            }
            runCatching {
                if (multicastLock?.isHeld == true) {
                    multicastLock.release()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    internal fun extractPreferredHostIp(resolvedInfo: NsdServiceInfo?, wifiIpFallback: String?): String {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val ipv4 = resolvedInfo?.hostAddresses?.firstOrNull { addr ->
                addr is Inet4Address && !addr.isLoopbackAddress
            }?.hostAddress
            if (!ipv4.isNullOrBlank()) return ipv4
        }
        val legacyHost = resolvedInfo?.host
        if (legacyHost is Inet4Address && !legacyHost.isLoopbackAddress) {
            legacyHost.hostAddress?.takeIf { it.isNotBlank() }?.let { return it }
        }
        if (!wifiIpFallback.isNullOrBlank()) return wifiIpFallback
        return legacyHost?.hostAddress?.takeIf { it.isNotBlank() } ?: "127.0.0.1"
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

    private fun markConnected(
        context: Context,
        host: String,
        port: Int,
        pairingPort: Int? = null
    ) {
        val appCtx = context.applicationContext
        _isConnected.value = true
        _isPaired.value = true
        _isSessionForgotten.value = false
        _requiresDevOptionsGuidance.value = false
        _connectedHost.value = host
        _connectedPort.value = port
        _discoveredPort.value = port
        _connectionState.value = NativeAdbConnectionState.CONNECTED

        val (ssid, networkKey) = resolveActiveWifiIdentity(appCtx)
        if (networkKey != null) {
            EncryptedAdbCredentialStore.markWifiNetworkTrusted(appCtx, networkKey, ssid)
            _activeWifiSsid.value = ssid
            _isCurrentWifiTrusted.value = true
        }
        _pendingWifiTrustPrompt.value = null

        val keyPair = runCatching { getOrInitKeyPair(appCtx) }.getOrNull()
        val fingerprint = keyPair?.let { EncryptedAdbCredentialStore.computeKeyFingerprint(it) }
        EncryptedAdbCredentialStore.savePairedSession(
            context = appCtx,
            host = host,
            connectPort = port,
            pairingPort = pairingPort,
            rsaFingerprint = fingerprint
        )

        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    /**
     * Opens a socket, completes `A_CNXN` -> `A_STLS` (mTLS upgrade with `arwora`'s RSA X.509 cert) /
     * `A_AUTH` handshake, and sends a real test shell command (`echo connected && id`) over an ADB stream
     * to verify the session is genuinely alive and authorized before reporting success.
     */
    internal fun performVerifiedHandshake(
        host: String,
        port: Int,
        keyPair: KeyPair
    ): Pair<Boolean, String> {
        return try {
            openAuthenticateAndVerifyShell(host, port, keyPair, startWithTls = false, HANDSHAKE_TIMEOUT_MS)
            Pair(true, "Active / Connected ($host:$port)")
        } catch (primaryEx: Exception) {
            try {
                openAuthenticateAndVerifyShell(host, port, keyPair, startWithTls = true, HANDSHAKE_TIMEOUT_MS)
                Pair(true, "Active / Connected via TLS ($host:$port)")
            } catch (tlsEx: Exception) {
                Pair(false, primaryEx.message ?: tlsEx.message ?: "Connection refused on $host:$port")
            }
        }
    }

    private fun openAuthenticateAndVerifyShell(
        host: String,
        port: Int,
        keyPair: KeyPair,
        startWithTls: Boolean,
        timeoutMs: Int
    ) {
        openAndAuthenticateSocket(host, port, keyPair, startWithTls, timeoutMs).use { socket ->
            val verifyResult = runShellCommandOnSocket(socket, "echo connected && id")
            if (!verifyResult.success) {
                throw IllegalStateException("ADB shell stream verification failed: ${verifyResult.output}")
            }
        }
    }

    private fun openAndAuthenticateSocket(
        host: String,
        port: Int,
        keyPair: KeyPair,
        startWithTls: Boolean,
        timeoutMs: Int
    ): Socket {
        val rawSocket = Socket()
        rawSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        rawSocket.soTimeout = timeoutMs

        val initialSocket: Socket = if (startWithTls) {
            val sslFactory = createClientSslContext(keyPair).socketFactory
            val sslSocket = sslFactory.createSocket(rawSocket, host, port, true) as SSLSocket
            sslSocket.soTimeout = timeoutMs
            sslSocket.startHandshake()
            sslSocket
        } else {
            rawSocket
        }

        var activeSocket: Socket = initialSocket
        return try {
            activeSocket = performAdbAuthHandshake(initialSocket, host, port, keyPair, timeoutMs)
            activeSocket
        } catch (e: Exception) {
            try {
                activeSocket.close()
            } catch (_: Exception) {
            }
            if (activeSocket !== initialSocket) {
                try {
                    initialSocket.close()
                } catch (_: Exception) {
                }
            }
            throw e
        }
    }

    private fun performAdbAuthHandshake(
        initialSocket: Socket,
        host: String,
        port: Int,
        keyPair: KeyPair,
        timeoutMs: Int
    ): Socket {
        val clientIdentity = ensureClientIdentity()
        var currentSocket = initialSocket
        var input = currentSocket.getInputStream()
        var output = currentSocket.getOutputStream()

        NativeAdbProtocol.writePacket(
            output,
            NativeAdbProtocol.A_CNXN,
            NativeAdbProtocol.A_VERSION,
            NativeAdbProtocol.MAX_PAYLOAD,
            "host::\u0000".toByteArray(Charsets.UTF_8)
        )

        var sentSignature = false
        var sentPublicKey = false
        var upgradedStls = false

        while (true) {
            val response = NativeAdbProtocol.readPacket(input)
            when (response.command) {
                NativeAdbProtocol.A_CNXN -> return currentSocket
                NativeAdbProtocol.A_STLS -> {
                    if (upgradedStls) {
                        throw IllegalStateException("Duplicate A_STLS challenge from ADB daemon")
                    }
                    upgradedStls = true
                    NativeAdbProtocol.writePacket(
                        output,
                        NativeAdbProtocol.A_STLS,
                        NativeAdbProtocol.A_STLS_VERSION,
                        0
                    )
                    val sslFactory = createClientSslContext(keyPair).socketFactory
                    val sslSocket = sslFactory.createSocket(currentSocket, host, port, true) as SSLSocket
                    sslSocket.soTimeout = timeoutMs
                    sslSocket.startHandshake()
                    currentSocket = sslSocket
                    input = sslSocket.getInputStream()
                    output = sslSocket.getOutputStream()
                }
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
            return runShellCommandOnSocket(socket, command)
        }
    }

    private fun runShellCommandOnSocket(socket: Socket, command: String): NativeShellResult {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        val localId = streamIdGen.getAndIncrement()

        val service = "shell:$command\u0000".toByteArray(Charsets.UTF_8)
        NativeAdbProtocol.writePacket(output, NativeAdbProtocol.A_OPEN, localId, 0, service)

        var remoteId = 0
        var streamAccepted = false
        val outBuffer = ByteArrayOutputStream()

        while (true) {
            val pkt = NativeAdbProtocol.readPacket(input)
            when (pkt.command) {
                NativeAdbProtocol.A_OKAY -> {
                    if (pkt.arg1 == localId) {
                        remoteId = pkt.arg0
                        streamAccepted = true
                    }
                }
                NativeAdbProtocol.A_WRTE -> {
                    if (pkt.arg1 == localId) {
                        remoteId = pkt.arg0
                        streamAccepted = true
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
        if (!streamAccepted) {
            return NativeShellResult(success = false, output = "ADB stream closed before A_OKAY", exitCode = -1)
        }
        return NativeShellResult(success = true, output = text, exitCode = 0)
    }

    internal fun createClientSslContext(keyPair: KeyPair? = null): SSLContext {
        val trustManagers = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        )
        val keyManagers: Array<KeyManager>? = keyPair?.let { kp ->
            runCatching {
                val cert = NativeAdbProtocol.createClientX509Certificate(kp, CLIENT_IDENTITY)
                val alias = CLIENT_IDENTITY
                arrayOf<KeyManager>(
                    object : X509KeyManager {
                        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> =
                            arrayOf(alias)
                        override fun chooseClientAlias(
                            keyType: Array<out String>?,
                            issuers: Array<out Principal>?,
                            socket: Socket?
                        ): String = alias
                        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
                        override fun chooseServerAlias(
                            keyType: String?,
                            issuers: Array<out Principal>?,
                            socket: Socket?
                        ): String? = null
                        override fun getCertificateChain(aliasParam: String?): Array<X509Certificate> = arrayOf(cert)
                        override fun getPrivateKey(aliasParam: String?): PrivateKey = kp.private
                    }
                )
            }.getOrNull()
        }
        return SSLContext.getInstance("TLS").apply {
            init(keyManagers, trustManagers, SecureRandom())
        }
    }
}
