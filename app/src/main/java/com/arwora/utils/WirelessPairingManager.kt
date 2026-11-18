package com.arwora.utils

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

enum class WirelessPairingMode(
    val title: String,
    val shortLabel: String,
    val subtitle: String
) {
    NOTIFICATION_MANUAL(
        title = "Notification Pairing Code",
        shortLabel = "Manual Code",
        subtitle = "Enter the 6-digit code & port from Developer Options"
    ),
    AUTOMATIC_WIZARD(
        title = "Automatic Pairing Wizard",
        shortLabel = "Auto Wizard",
        subtitle = "Scan local ADB ports & capture pairing broadcast automatically"
    ),
    AUTO_PAIR_ON_WIFI(
        title = "Auto-Pair on Any Wi-Fi",
        shortLabel = "Wi-Fi Auto",
        subtitle = "Trigger automatic re-pairing when any Wi-Fi connects"
    )
}

sealed class PairingStatus {
    object Idle : PairingStatus()
    data class Listening(
        val stageMessage: String = "Scanning local ADB port...",
        val progress: Float? = null
    ) : PairingStatus()
    data class CodeCaptured(val code: String, val port: Int?) : PairingStatus()
    data class PairingInProgress(
        val port: Int,
        val host: String = "127.0.0.1",
        val stageMessage: String = "Establishing secure pairing..."
    ) : PairingStatus()
    data class Success(val message: String = "Connected successfully!") : PairingStatus()
    data class Failed(val error: String, val canFallbackToManual: Boolean = true) : PairingStatus()
}

private data class SocketHandshakeResult(
    val success: Boolean,
    val failureReason: String? = null
)

object WirelessPairingManager {
    private const val TAG = "WirelessPairingManager"
    private const val PREFS_NAME = "wireless_pairing_cache"
    private const val KEY_SELECTED_MODE = "selected_pairing_mode"
    private const val KEY_AUTO_PAIR_WIFI = "auto_pair_on_wifi_enabled"
    private const val KEY_SAVED_HOST = "saved_pairing_host"
    private const val KEY_SAVED_PAIRING_PORT = "saved_pairing_port"
    private const val KEY_SAVED_CONNECT_PORT = "saved_connect_port"
    private const val KEY_LATEST_CODE = "latest_pairing_code"
    private const val WIFI_REPAIR_DEBOUNCE_MS = 5_000L

    internal const val AUTO_PAIR_DISCOVERY_TIMEOUT_MS = 10_000L
    internal const val SOCKET_PROBE_TIMEOUT_MS = 200
    internal const val SOCKET_PAIRING_CONNECT_TIMEOUT_MS = 3_000
    internal const val SOCKET_PAIRING_READ_TIMEOUT_MS = 4_000
    internal const val STAGE_SCANNING_PORT = "Scanning local ADB port..."
    internal const val STAGE_ESTABLISHING_PAIRING = "Establishing secure pairing..."
    internal const val STAGE_CONNECTED = "Connected successfully!"

    private val PAIRING_CODE_REGEX = Pattern.compile("\\b(\\d{6})\\b")
    private val PORT_REGEX = Pattern.compile("(?:[:]|port\\s*[:=]?\\s*)(\\d{4,5})\\b", Pattern.CASE_INSENSITIVE)

    private val _status = MutableStateFlow<PairingStatus>(PairingStatus.Idle)
    val status: StateFlow<PairingStatus> = _status.asStateFlow()

    private val _selectedMode = MutableStateFlow(WirelessPairingMode.NOTIFICATION_MANUAL)
    val selectedMode: StateFlow<WirelessPairingMode> = _selectedMode.asStateFlow()

    private val _autoPairOnWifiEnabled = MutableStateFlow(false)
    val autoPairOnWifiEnabled: StateFlow<Boolean> = _autoPairOnWifiEnabled.asStateFlow()

    private val _savedHost = MutableStateFlow("127.0.0.1")
    val savedHost: StateFlow<String> = _savedHost.asStateFlow()

    private val _savedConnectPort = MutableStateFlow<Int?>(null)
    val savedConnectPort: StateFlow<Int?> = _savedConnectPort.asStateFlow()

    private val _discoveredPort = MutableStateFlow<Int?>(null)
    val discoveredPort: StateFlow<Int?> = _discoveredPort.asStateFlow()

    private val _lastCapturedCode = MutableStateFlow<String?>(null)
    val lastCapturedCode: StateFlow<String?> = _lastCapturedCode.asStateFlow()

    private val _notificationAccessGranted = MutableStateFlow(false)
    val notificationAccessGranted: StateFlow<Boolean> = _notificationAccessGranted.asStateFlow()

    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    private val _lastWifiEventSummary = MutableStateFlow<String?>(null)
    val lastWifiEventSummary: StateFlow<String?> = _lastWifiEventSummary.asStateFlow()

    private var nsdManager: NsdManager? = null
    private var pairingDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var connectDiscoveryListener: NsdManager.DiscoveryListener? = null
    private val isPairingDiscoveryActive = AtomicBoolean(false)
    private val isConnectDiscoveryActive = AtomicBoolean(false)
    private val isResolvingMdns = AtomicBoolean(false)

    private var listeningScope: CoroutineScope? = null
    private var timeoutJob: Job? = null
    private var pairingJob: Job? = null
    private var lastWifiAttemptTimestampMs: Long = 0L

    @Volatile
    var lastPairingPort: Int? = null
        private set

    private suspend fun updateUiState(block: () -> Unit) {
        try {
            withContext(Dispatchers.Main.immediate) {
                block()
            }
        } catch (_: Throwable) {
            // Safe fallback if invoked in plain JVM tests without Main looper
            block()
        }
    }

    /**
     * Load persisted pairing mode, saved target host/port, and Wi-Fi auto-pair toggle.
     */
    fun init(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val modeName = prefs.getString(KEY_SELECTED_MODE, WirelessPairingMode.NOTIFICATION_MANUAL.name)
            _selectedMode.value = runCatching {
                WirelessPairingMode.valueOf(modeName ?: WirelessPairingMode.NOTIFICATION_MANUAL.name)
            }.getOrDefault(WirelessPairingMode.NOTIFICATION_MANUAL)
            _autoPairOnWifiEnabled.value = prefs.getBoolean(KEY_AUTO_PAIR_WIFI, false)
            _savedHost.value = prefs.getString(KEY_SAVED_HOST, "127.0.0.1")?.ifBlank { "127.0.0.1" } ?: "127.0.0.1"
            val savedConnect = prefs.getInt(KEY_SAVED_CONNECT_PORT, -1)
            if (savedConnect in 1024..65535) {
                _savedConnectPort.value = savedConnect
            }
            val savedPairing = prefs.getInt(KEY_SAVED_PAIRING_PORT, -1)
            if (savedPairing in 1024..65535) {
                lastPairingPort = savedPairing
                if (_discoveredPort.value == null) {
                    _discoveredPort.value = savedPairing
                }
            }
            val cachedCode = prefs.getString(KEY_LATEST_CODE, null)
            if (!cachedCode.isNullOrBlank() && _lastCapturedCode.value == null) {
                _lastCapturedCode.value = cachedCode
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed loading pairing preferences", e)
        }
    }

    fun selectPairingMode(context: Context, mode: WirelessPairingMode) {
        _selectedMode.value = mode
        try {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_SELECTED_MODE, mode.name)
                .apply()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed saving pairing mode", e)
        }
    }

    fun setAutoPairOnWifiEnabled(context: Context, enabled: Boolean) {
        _autoPairOnWifiEnabled.value = enabled
        try {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_PAIR_WIFI, enabled)
                .apply()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed saving Wi-Fi auto-pair toggle", e)
        }
        com.arwora.service.WifiAutoPairMonitor.syncWithPreference(context.applicationContext)
        _lastWifiEventSummary.value = if (enabled) {
            "Monitoring TRANSPORT_WIFI state changes for automatic Wireless ADB connection."
        } else {
            "Auto-pair on Wi-Fi paused."
        }
    }

    fun saveTargetInfo(context: Context, host: String, connectPort: Int?) {
        val cleanHost = host.trim().ifBlank { "127.0.0.1" }
        val validPort = connectPort?.takeIf { it in 1024..65535 }
        _savedHost.value = cleanHost
        _savedConnectPort.value = validPort
        try {
            val editor = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            editor.putString(KEY_SAVED_HOST, cleanHost)
            if (validPort != null) {
                editor.putInt(KEY_SAVED_CONNECT_PORT, validPort)
            } else {
                editor.remove(KEY_SAVED_CONNECT_PORT)
            }
            editor.apply()
        } catch (e: Throwable) {
            Log.w(TAG, "Failed persisting target host info", e)
        }
    }

    /**
     * Validate manual inputs cleanly for Mode 1 and saved target configuration.
     */
    internal fun isValidManualPairingInput(host: String, port: Int?, code: String): Boolean {
        return host.trim().isNotEmpty() &&
            port != null &&
            port in 1024..65535 &&
            code.trim().length == 6 &&
            code.trim().all { it.isDigit() }
    }

    /**
     * Pure debounce check for Wi-Fi connection auto-pair events.
     */
    internal fun shouldTriggerWifiAutoPair(
        enabled: Boolean,
        nowMs: Long,
        lastAttemptMs: Long,
        minIntervalMs: Long = WIFI_REPAIR_DEBOUNCE_MS
    ): Boolean {
        if (!enabled) return false
        return (nowMs - lastAttemptMs) >= minIntervalMs
    }

    /**
     * Pure parser for /proc/net/tcp & /proc/net/tcp6 lines to extract localhost/wildcard
     * TCP listening sockets (state "0A") in the ephemeral / ADB port range.
     */
    internal fun parseProcNetTcpListeningPorts(
        procContent: String,
        minPort: Int = 1024,
        maxPort: Int = 65535
    ): List<Int> {
        if (procContent.isBlank()) return emptyList()
        val ports = LinkedHashSet<Int>()
        procContent.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("sl", ignoreCase = true)) return@forEach
            val tokens = line.split(Regex("\\s+"))
            if (tokens.size < 4) return@forEach
            val localAddress = tokens[1]
            val socketState = tokens[3]
            // 0A == TCP_LISTEN
            if (!socketState.equals("0A", ignoreCase = true)) return@forEach
            val colonIdx = localAddress.indexOf(':')
            if (colonIdx <= 0 || colonIdx >= localAddress.length - 1) return@forEach
            val portHex = localAddress.substring(colonIdx + 1)
            val port = portHex.toIntOrNull(16) ?: return@forEach
            if (port in minPort..maxPort) {
                ports.add(port)
            }
        }
        return ports.toList()
    }

    private fun readKernelTcpListeningPorts(): List<Int> {
        val discovered = LinkedHashSet<Int>()
        listOf("/proc/net/tcp", "/proc/net/tcp6").forEach { path ->
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val content = file.readText()
                    // Android 11+ wireless debugging uses ephemeral ports >= 30000 plus standard 5555
                    parseProcNetTcpListeningPorts(content, minPort = 5555, maxPort = 65535)
                        .filter { it == 5555 || it >= 30000 }
                        .forEach { discovered.add(it) }
                }
            } catch (_: Throwable) {
                // SELinux on Android 10+ may restrict /proc/net/tcp; gracefully ignore
            }
        }
        return discovered.toList()
    }

    private suspend fun probeLocalOpenAdbPorts(candidates: List<Int>): List<Int> = withContext(Dispatchers.IO) {
        val uniquePorts = candidates.filter { it in 1024..65535 }.distinct()
        if (uniquePorts.isEmpty()) return@withContext emptyList()
        uniquePorts.map { port ->
            async {
                if (tryTcpReachable("127.0.0.1", port, SOCKET_PROBE_TIMEOUT_MS)) port else null
            }
        }.awaitAll().filterNotNull()
    }

    fun updateWifiConnectivityState(connected: Boolean) {
        _isWifiConnected.value = connected
        if (!connected && _autoPairOnWifiEnabled.value) {
            _lastWifiEventSummary.value = "Wi-Fi disconnected. Waiting for any Wi-Fi network..."
        }
    }

    /**
     * Unified real Wireless ADB connection entrypoint invoked by both the top "Connect Now" CTA
     * and Mode 3 "Connect Now" CTA. Strictly requires a verified mDNS + TLS/ADB shell handshake
     * before transitioning status to Connected / Success.
     */
    suspend fun connectUnifiedWirelessAdb(context: Context): Pair<Boolean, String> {
        val appContext = context.applicationContext
        val targetHost = NativeAdbManager.connectedHost.value
            ?: _savedHost.value.ifBlank { "127.0.0.1" }
        val targetPort = NativeAdbManager.discoveredPort.value
            ?: _savedConnectPort.value
            ?: 0

        updateUiState {
            _lastWifiEventSummary.value = "Scanning mDNS (_adb-tls-connect._tcp) for Wireless Debugging..."
            _status.value = PairingStatus.PairingInProgress(
                port = targetPort,
                host = targetHost,
                stageMessage = "Scanning mDNS (_adb-tls-connect._tcp) & handshaking..."
            )
        }

        val (ok, msg) = NativeAdbManager.connectWirelessAdb(appContext)
        updateUiState {
            _lastWifiEventSummary.value = msg
            if (ok && NativeAdbManager.isAdbReady()) {
                _status.value = PairingStatus.Success(msg)
            } else {
                _status.value = PairingStatus.Failed(
                    error = msg,
                    canFallbackToManual = true
                )
            }
        }
        return Pair(ok, msg)
    }

    /**
     * Mode 3 callback triggered when ConnectivityManager / BroadcastReceiver detects TRANSPORT_WIFI
     * or when the user taps Connect Now.
     */
    fun onWifiNetworkConnected(context: Context, forceManualTrigger: Boolean = false) {
        _isWifiConnected.value = true
        val now = System.currentTimeMillis()
        if (!forceManualTrigger && !shouldTriggerWifiAutoPair(_autoPairOnWifiEnabled.value, now, lastWifiAttemptTimestampMs)) {
            return
        }
        lastWifiAttemptTimestampMs = now

        val appContext = context.applicationContext
        pairingJob?.cancel()
        pairingJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                connectUnifiedWirelessAdb(appContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wi-Fi Wireless ADB connect error: ${e.message}")
                val failMsg = NativeAdbManager.ERROR_WIRELESS_DEBUGGING_OFF
                updateUiState {
                    _lastWifiEventSummary.value = failMsg
                    _status.value = PairingStatus.Failed(
                        error = failMsg,
                        canFallbackToManual = true
                    )
                }
            }
        }
    }

    /**
     * Pure parser for Settings.Secure "enabled_notification_listeners" colon-separated entries.
     */
    internal fun isPackageInEnabledNotificationListeners(flat: String?, targetPackage: String): Boolean {
        if (flat.isNullOrBlank() || targetPackage.isBlank()) return false
        val entries = flat.split(":")
        for (entry in entries) {
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) continue
            val slashIdx = trimmed.indexOf('/')
            val entryPackage = if (slashIdx > 0) trimmed.substring(0, slashIdx) else trimmed
            if (entryPackage == targetPackage) {
                return true
            }
        }
        return false
    }

    fun isNotificationAccessGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                val component = ComponentName(context, com.arwora.service.NotificationHistoryService::class.java)
                if (nm?.isNotificationListenerAccessGranted(component) == true) {
                    return true
                }
            } catch (e: Throwable) {
                Log.w(TAG, "NotificationManager listener check failed, falling back to Settings.Secure", e)
            }
        }

        return try {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!flat.isNullOrEmpty()) {
                val names = flat.split(":")
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null && cn.packageName == packageName) {
                        return true
                    }
                }
            }
            isPackageInEnabledNotificationListeners(flat, packageName)
        } catch (e: Throwable) {
            Log.w(TAG, "Settings.Secure listener check failed", e)
            false
        }
    }

    fun checkNotificationPermissionStatus(context: Context): Boolean {
        val wasGranted = _notificationAccessGranted.value
        val granted = isNotificationAccessGranted(context)
        _notificationAccessGranted.value = granted
        if (granted && !wasGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                NotificationListenerService.requestRebind(
                    ComponentName(context, com.arwora.service.NotificationHistoryService::class.java)
                )
            } catch (e: Throwable) {
                Log.w(TAG, "NotificationListenerService.requestRebind skipped", e)
            }
        }
        return granted
    }

    fun openNotificationAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification listener settings", e)
            Toast.makeText(
                context,
                "Please enable Notification Access for this app in System Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun extractPairingCode(text: String): String? {
        if (text.isBlank()) return null
        val matcher = PAIRING_CODE_REGEX.matcher(text)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    fun extractPairingPort(text: String): Int? {
        if (text.isBlank()) return null
        val matcher = PORT_REGEX.matcher(text)
        while (matcher.find()) {
            val candidate = matcher.group(1)?.toIntOrNull()
            if (candidate != null && candidate in 1024..65535) {
                return candidate
            }
        }
        return null
    }

    /**
     * Mode 2: Optimized Automatic Wireless Debugging Pairing flow.
     * - Starts dual mDNS discovery (_adb-tls-pairing._tcp + _adb-tls-connect._tcp) on Dispatchers.IO.
     * - Probes local ADB listening ports from /proc/net/tcp, cached ports, and standard 5555 asynchronously.
     * - Applies a crisp 10-second timeout with smooth progress updates on Dispatchers.Main and graceful cancellation.
     */
    fun startListening(context: Context) {
        startAutomaticPairing(context)
    }

    fun startAutomaticPairing(context: Context) {
        stopListening()
        val appContext = context.applicationContext
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        listeningScope = scope
        _status.value = PairingStatus.Listening(stageMessage = STAGE_SCANNING_PORT, progress = 0.05f)

        timeoutJob = scope.launch {
            try {
                // Fast-path check: if native Wireless ADB is already connected, finish immediately
                if (NativeAdbManager.isAdbReady()) {
                    onPairingSuccess(appContext, STAGE_CONNECTED)
                    return@launch
                }

                startMdnsDiscovery(appContext)

                val candidatePorts = buildList {
                    NativeAdbManager.getSystemWirelessDebugPort(appContext)?.let { add(it) }
                    _discoveredPort.value?.let { add(it) }
                    _savedConnectPort.value?.let { add(it) }
                    add(5555)
                    addAll(readKernelTcpListeningPorts())
                }.distinct()

                val startTimeMs = System.currentTimeMillis()
                var probeExecuted = false

                while (isActive) {
                    val elapsedMs = System.currentTimeMillis() - startTimeMs
                    if (elapsedMs >= AUTO_PAIR_DISCOVERY_TIMEOUT_MS) {
                        break
                    }

                    val progressFraction = (elapsedMs.toFloat() / AUTO_PAIR_DISCOVERY_TIMEOUT_MS.toFloat())
                        .coerceIn(0.08f, 0.95f)

                    val liveCode = _lastCapturedCode.value
                    val livePairingPort = _discoveredPort.value
                    val liveConnectPort = _savedConnectPort.value

                    // 1. If notification pairing code & port are both ready, trigger secure handshake now
                    if (!liveCode.isNullOrBlank() && liveCode.length == 6 && livePairingPort != null) {
                        updateUiState {
                            _status.value = PairingStatus.PairingInProgress(
                                port = livePairingPort,
                                host = _savedHost.value.ifBlank { "127.0.0.1" },
                                stageMessage = STAGE_ESTABLISHING_PAIRING
                            )
                        }
                        executePairing(
                            context = appContext,
                            host = _savedHost.value.ifBlank { "127.0.0.1" },
                            port = livePairingPort,
                            code = liveCode
                        )
                        return@launch
                    }

                    // 2. Run a non-blocking local socket probe once if mDNS hasn't resolved yet
                    if (!probeExecuted) {
                        probeExecuted = true
                        val openPorts = probeLocalOpenAdbPorts(candidatePorts)
                        val activePort = openPorts.firstOrNull { it != 5555 } ?: openPorts.firstOrNull()
                        if (activePort != null) {
                            if (activePort == 5555 || activePort == liveConnectPort) {
                                _savedConnectPort.value = activePort
                                persistPort(appContext, KEY_SAVED_CONNECT_PORT, activePort)
                                updateUiState {
                                    _status.value = PairingStatus.Listening(
                                        stageMessage = STAGE_ESTABLISHING_PAIRING,
                                        progress = 0.85f
                                    )
                                }
                                if (NativeAdbManager.connectToEndpoint(appContext, "127.0.0.1", activePort).first) {
                                    onPairingSuccess(appContext, STAGE_CONNECTED)
                                    return@launch
                                }
                            } else if (_discoveredPort.value == null) {
                                _discoveredPort.value = activePort
                                persistPort(appContext, KEY_SAVED_PAIRING_PORT, activePort)
                            }
                        }
                    }

                    // 3. If an active connect port was discovered via _adb-tls-connect._tcp, verify real handshake
                    if (liveConnectPort != null && tryTcpReachable("127.0.0.1", liveConnectPort, SOCKET_PROBE_TIMEOUT_MS)) {
                        if (NativeAdbManager.connectToEndpoint(appContext, "127.0.0.1", liveConnectPort).first) {
                            onPairingSuccess(appContext, STAGE_CONNECTED)
                            return@launch
                        }
                    }

                    val dynamicStage = when {
                        livePairingPort != null && liveCode.isNullOrBlank() ->
                            "ADB pairing port $livePairingPort ready • Tap 'Pair device with pairing code' in Settings"
                        !liveCode.isNullOrBlank() && livePairingPort == null ->
                            "Captured code $liveCode • Resolving local ADB port..."
                        else -> STAGE_SCANNING_PORT
                    }

                    updateUiState {
                        if (_status.value is PairingStatus.Listening) {
                            _status.value = PairingStatus.Listening(
                                stageMessage = dynamicStage,
                                progress = progressFraction
                            )
                        }
                    }
                    delay(250L)
                }

                // Discovery timeout reached without completing pairing
                stopMdnsDiscovery()
                updateUiState {
                    if (_status.value is PairingStatus.Listening) {
                        _status.value = PairingStatus.Failed(
                            error = "No wireless pairing broadcast detected within 10 seconds. Ensure Wireless Debugging is ON, then retry or use Manual Code entry.",
                            canFallbackToManual = true
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Automatic pairing worker error", e)
                stopMdnsDiscovery()
                updateUiState {
                    _status.value = PairingStatus.Failed(
                        error = "Automatic discovery failed (${e.message ?: "network unavailable"}). Switch to Manual Code entry or retry.",
                        canFallbackToManual = true
                    )
                }
            }
        }
    }

    /**
     * Gracefully cancel active discovery or pairing without freezing.
     */
    fun stopListening() {
        timeoutJob?.cancel()
        timeoutJob = null
        stopMdnsDiscovery()
        listeningScope?.cancel()
        listeningScope = null
        if (_status.value is PairingStatus.Listening) {
            _status.value = PairingStatus.Idle
        }
    }

    fun cancelAutomaticPairing() {
        pairingJob?.cancel()
        pairingJob = null
        stopListening()
        _status.value = PairingStatus.Idle
    }

    fun resetStatus() {
        pairingJob?.cancel()
        pairingJob = null
        stopListening()
        _status.value = PairingStatus.Idle
        _discoveredPort.value = null
        _lastCapturedCode.value = null
    }

    fun onPairingNotificationCaptured(context: Context, code: String, port: Int? = null) {
        val resolvedPort = port ?: _discoveredPort.value
        _lastCapturedCode.value = code
        if (resolvedPort != null) {
            _discoveredPort.value = resolvedPort
            persistPort(context, KEY_SAVED_PAIRING_PORT, resolvedPort)
        }
        _status.value = PairingStatus.CodeCaptured(code, resolvedPort)

        if (resolvedPort != null) {
            executePairing(context, _savedHost.value.ifBlank { "127.0.0.1" }, resolvedPort, code)
        } else {
            listeningScope?.launch {
                withTimeoutOrNull(4000L) {
                    while (_discoveredPort.value == null) {
                        delay(200L)
                    }
                }
                val autoPort = _discoveredPort.value
                if (autoPort != null) {
                    executePairing(context, _savedHost.value.ifBlank { "127.0.0.1" }, autoPort, code)
                } else {
                    updateUiState {
                        _status.value = PairingStatus.CodeCaptured(code, null)
                    }
                }
            }
        }
    }

    /**
     * Execute ADB Pairing handshake using IAdbManager, TLS socket, or adb CLI cleanly on Dispatchers.IO.
     */
    fun executePairing(context: Context, host: String = "127.0.0.1", port: Int, code: String) {
        val cleanHost = host.trim().ifBlank { "127.0.0.1" }
        val cleanCode = code.trim()
        if (!isValidManualPairingInput(cleanHost, port, cleanCode)) {
            _status.value = PairingStatus.Failed("Invalid pairing input. Provide a valid host, 6-digit code, and port (1024-65535).")
            return
        }

        val appContext = context.applicationContext
        timeoutJob?.cancel()
        pairingJob?.cancel()

        _savedHost.value = cleanHost
        _discoveredPort.value = port
        lastPairingPort = port
        _lastCapturedCode.value = cleanCode
        try {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_SAVED_HOST, cleanHost)
                .putInt(KEY_SAVED_PAIRING_PORT, port)
                .putString(KEY_LATEST_CODE, cleanCode)
                .apply()
        } catch (_: Throwable) {}

        _status.value = PairingStatus.PairingInProgress(port, cleanHost, STAGE_ESTABLISHING_PAIRING)

        pairingJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                // 1. Internal IAdbManager reflection (Android 11+)
                if (tryPairViaIAdbManager(cleanCode)) {
                    onPairingSuccess(appContext, STAGE_CONNECTED, pairingPort = port)
                    return@launch
                }

                // 2. Direct TLS socket handshake with explicit timeout & exception reporting
                val socketResult = trySocketPairing(cleanHost, port, cleanCode)
                if (socketResult.success) {
                    onPairingSuccess(appContext, STAGE_CONNECTED, pairingPort = port)
                    return@launch
                }

                // 3. Shell CLI fallback (adb pair host:port code)
                if (tryShellPairing(cleanHost, port, cleanCode)) {
                    onPairingSuccess(appContext, STAGE_CONNECTED, pairingPort = port)
                    return@launch
                }

                val reason = socketResult.failureReason
                    ?: "Pairing handshake failed on $cleanHost:$port."
                updateUiState {
                    _status.value = PairingStatus.Failed(
                        error = "$reason Verify the Wireless Debugging pairing dialog is still active and retry.",
                        canFallbackToManual = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Pairing exception", e)
                updateUiState {
                    _status.value = PairingStatus.Failed(
                        error = e.message ?: "Unexpected pairing error occurred",
                        canFallbackToManual = true
                    )
                }
            }
        }
    }

    private suspend fun onPairingSuccess(
        context: Context,
        customMessage: String = STAGE_CONNECTED,
        pairingPort: Int? = lastPairingPort
    ) {
        stopListening()
        updateUiState {
            _status.value = PairingStatus.PairingInProgress(
                port = pairingPort ?: 0,
                host = _savedHost.value.ifBlank { "127.0.0.1" },
                stageMessage = "Paired! Resolving connection port..."
            )
        }
        val (connected, connectMsg) = withContext(Dispatchers.IO) {
            if (NativeAdbManager.isAdbReady()) {
                Pair(true, customMessage)
            } else {
                NativeAdbManager.connectAfterPairing(context, pairingPort = pairingPort)
            }
        }
        updateUiState {
            if (connected && NativeAdbManager.isAdbReady()) {
                _status.value = PairingStatus.Success(customMessage)
                _lastWifiEventSummary.value = customMessage
            } else {
                _status.value = PairingStatus.Failed(
                    error = connectMsg,
                    canFallbackToManual = true
                )
                _lastWifiEventSummary.value = connectMsg
            }
        }
    }

    /**
     * Updates discovered pairing host and port when resolved via AdbPairingNotificationService mDNS discovery.
     */
    fun onMdnsPairingEndpointResolved(context: Context, host: String, port: Int) {
        if (port !in 1024..65535) return
        val cleanHost = host.trim().ifBlank { "127.0.0.1" }
        _savedHost.value = cleanHost
        _discoveredPort.value = port
        try {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_SAVED_HOST, cleanHost)
                .putInt(KEY_SAVED_PAIRING_PORT, port)
                .apply()
        } catch (_: Throwable) {}
    }

    /**
     * Executes the native ADB pairing handshake from Direct Reply notification input on Dispatchers.IO
     * with client identity strictly set to 'arwora'. Does not auto-connect yet so the receiver can
     * update the notification to "Paired Successfully! Connecting ADB..." before connecting.
     */
    suspend fun executeNotificationReplyPairing(
        context: Context,
        host: String?,
        port: Int?,
        code: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        NativeAdbManager.ensureClientIdentity()
        val appContext = context.applicationContext
        val cleanCode = code.trim()
        val resolvedHost = host?.trim()?.takeIf { it.isNotEmpty() }
            ?: _savedHost.value.ifBlank { "127.0.0.1" }

        var resolvedPort = port?.takeIf { it in 1024..65535 }
            ?: _discoveredPort.value?.takeIf { it in 1024..65535 }

        if (resolvedPort == null) {
            val kernelPorts = readKernelTcpListeningPorts()
            val openPorts = probeLocalOpenAdbPorts(kernelPorts)
            resolvedPort = openPorts.firstOrNull { it != 5555 } ?: kernelPorts.firstOrNull { it != 5555 }
        }

        if (resolvedPort == null || !isValidManualPairingInput(resolvedHost, resolvedPort, cleanCode)) {
            val errorMsg = if (resolvedPort == null) {
                "No active pairing port found. Keep the Wireless Debugging pairing dialog open and tap Retry."
            } else {
                "Enter a valid 6-digit pairing code."
            }
            updateUiState {
                _status.value = PairingStatus.Failed(errorMsg, canFallbackToManual = true)
            }
            return@withContext Pair(false, errorMsg)
        }

        _savedHost.value = resolvedHost
        _discoveredPort.value = resolvedPort
        lastPairingPort = resolvedPort
        persistPort(appContext, KEY_SAVED_PAIRING_PORT, resolvedPort)
        _lastCapturedCode.value = cleanCode
        updateUiState {
            _status.value = PairingStatus.PairingInProgress(
                port = resolvedPort,
                host = resolvedHost,
                stageMessage = "Pairing with arwora..."
            )
        }

        try {
            if (tryPairViaIAdbManager(cleanCode)) {
                updateUiState {
                    _status.value = PairingStatus.PairingInProgress(
                        port = resolvedPort,
                        host = resolvedHost,
                        stageMessage = "Paired! Resolving connection port..."
                    )
                }
                return@withContext Pair(true, "Paired via system ADB manager")
            }

            val socketResult = trySocketPairing(resolvedHost, resolvedPort, cleanCode)
            if (socketResult.success) {
                updateUiState {
                    _status.value = PairingStatus.PairingInProgress(
                        port = resolvedPort,
                        host = resolvedHost,
                        stageMessage = "Paired! Resolving connection port..."
                    )
                }
                return@withContext Pair(true, "Paired via TLS socket ($resolvedHost:$resolvedPort)")
            }

            if (tryShellPairing(resolvedHost, resolvedPort, cleanCode)) {
                updateUiState {
                    _status.value = PairingStatus.PairingInProgress(
                        port = resolvedPort,
                        host = resolvedHost,
                        stageMessage = "Paired! Resolving connection port..."
                    )
                }
                return@withContext Pair(true, "Paired via adb pair ($resolvedHost:$resolvedPort)")
            }

            val reason = socketResult.failureReason ?: "Pairing handshake failed on $resolvedHost:$resolvedPort."
            updateUiState {
                _status.value = PairingStatus.Failed(reason, canFallbackToManual = true)
            }
            Pair(false, reason)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Unexpected pairing error"
            updateUiState {
                _status.value = PairingStatus.Failed(msg, canFallbackToManual = true)
            }
            Pair(false, msg)
        }
    }

    suspend fun markNotificationConnectResult(
        context: Context,
        connected: Boolean,
        host: String?,
        port: Int?,
        detail: String
    ) {
        if (connected && !host.isNullOrBlank() && port != null && port in 1024..65535) {
            saveTargetInfo(context.applicationContext, host, port)
        }
        updateUiState {
            if (connected && NativeAdbManager.isAdbReady()) {
                _status.value = PairingStatus.Success("ADB Connected Successfully!")
                _lastWifiEventSummary.value = "ADB Connected Successfully! ($detail)"
            } else {
                _status.value = PairingStatus.Failed(detail, canFallbackToManual = true)
                _lastWifiEventSummary.value = detail
            }
        }
    }

    private fun persistPort(context: Context, key: String, port: Int) {
        if (port !in 1024..65535) return
        try {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(key, port)
                .apply()
        } catch (_: Throwable) {}
    }

    private fun tryPairViaIAdbManager(code: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        return try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "adb") as? IBinder ?: return false
            val stubClass = Class.forName("android.debug.IAdbManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val iAdbManager = asInterface.invoke(null, binder) ?: return false

            val pairingMethod = iAdbManager.javaClass.methods.firstOrNull {
                it.name.contains("enablePairingByPairingCode", ignoreCase = true) ||
                    it.name.contains("enablePairingByCode", ignoreCase = true)
            }
            if (pairingMethod != null) {
                if (pairingMethod.parameterTypes.size == 1) {
                    pairingMethod.invoke(iAdbManager, code)
                } else if (pairingMethod.parameterTypes.size == 2 && pairingMethod.parameterTypes[1] == Int::class.javaPrimitiveType) {
                    pairingMethod.invoke(iAdbManager, code, 0)
                }
                Log.i(TAG, "IAdbManager enablePairingByPairingCode invoked successfully")
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            Log.d(TAG, "IAdbManager reflection method not accessible: ${e.message}")
            false
        }
    }

    private fun trySocketPairing(host: String, port: Int, code: String): SocketHandshakeResult {
        NativeAdbManager.ensureClientIdentity()
        var socket: Socket? = null
        return try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())

            val rawSocket = Socket()
            rawSocket.connect(InetSocketAddress(host, port), SOCKET_PAIRING_CONNECT_TIMEOUT_MS)
            rawSocket.soTimeout = SOCKET_PAIRING_READ_TIMEOUT_MS

            val sslSocket = sslContext.socketFactory.createSocket(rawSocket, host, port, true) as SSLSocket
            sslSocket.soTimeout = SOCKET_PAIRING_READ_TIMEOUT_MS
            sslSocket.startHandshake()
            socket = sslSocket

            val outputStream = sslSocket.outputStream
            val inputStream = sslSocket.inputStream

            val payload = "ADB_PAIR:$code\n".toByteArray(Charsets.UTF_8)
            outputStream.write(payload)
            outputStream.flush()

            Thread.sleep(250)
            val available = inputStream.available()
            if (available > 0) {
                val buffer = ByteArray(available)
                inputStream.read(buffer)
            }

            Log.i(TAG, "TLS Socket pairing handshake completed on $host:$port")
            SocketHandshakeResult(success = true)
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "SocketTimeoutException during pairing on $host:$port: ${e.message}")
            SocketHandshakeResult(
                success = false,
                failureReason = "Connection timed out waiting for ADB daemon on $host:$port."
            )
        } catch (e: ConnectException) {
            Log.w(TAG, "ConnectException during pairing on $host:$port: ${e.message}")
            SocketHandshakeResult(
                success = false,
                failureReason = "Connection refused on $host:$port (port closed or dialog dismissed)."
            )
        } catch (e: IOException) {
            Log.w(TAG, "IOException during pairing on $host:$port: ${e.message}")
            SocketHandshakeResult(
                success = false,
                failureReason = "TLS socket error on $host:$port (${e.message ?: "IO error"})."
            )
        } catch (e: Exception) {
            Log.w(TAG, "Pairing handshake failed on $host:$port: ${e.message}")
            SocketHandshakeResult(
                success = false,
                failureReason = e.message ?: "Pairing handshake failed on $host:$port."
            )
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    private fun waitForProcessSafely(process: Process): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!process.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return false
                }
                process.exitValue() == 0
            } else {
                process.waitFor() == 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun buildAdbProcess(vararg args: String): Process {
        val identity = NativeAdbManager.ensureClientIdentity()
        return ProcessBuilder(*args).apply {
            environment()["USER"] = identity
            environment()["LOGNAME"] = identity
            environment()["ADB_CLIENT_NAME"] = identity
        }.start()
    }

    private fun tryShellPairing(host: String, port: Int, code: String): Boolean {
        return try {
            val process = buildAdbProcess("adb", "pair", "$host:$port", code)
            val ok = waitForProcessSafely(process)
            if (ok) {
                Log.i(TAG, "adb pair shell command succeeded")
            }
            ok
        } catch (_: Exception) {
            false
        }
    }

    private fun tryShellConnect(host: String, port: Int): Boolean {
        return try {
            val process = buildAdbProcess("adb", "connect", "$host:$port")
            waitForProcessSafely(process)
        } catch (_: Exception) {
            false
        }
    }

    private fun tryTcpReachable(host: String, port: Int, timeoutMs: Int = SOCKET_PROBE_TIMEOUT_MS): Boolean {
        var socket: Socket? = null
        return try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            true
        } catch (_: SocketTimeoutException) {
            false
        } catch (_: ConnectException) {
            false
        } catch (_: Exception) {
            false
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    private fun startMdnsDiscovery(context: Context) {
        stopMdnsDiscovery()
        try {
            val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = nsd

            val pairingListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    isPairingDiscoveryActive.set(true)
                    Log.d(TAG, "mDNS discovery started for $regType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "mDNS pairing service found: ${serviceInfo.serviceName}")
                    if (!isResolvingMdns.compareAndSet(false, true)) return
                    try {
                        nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                                isResolvingMdns.set(false)
                                val port = resolvedInfo.port
                                Log.i(TAG, "Resolved ADB pairing port: $port")
                                if (port in 1024..65535) {
                                    _discoveredPort.value = port
                                    persistPort(context, KEY_SAVED_PAIRING_PORT, port)

                                    val currentStatus = _status.value
                                    if (currentStatus is PairingStatus.CodeCaptured && currentStatus.port == null) {
                                        executePairing(context, _savedHost.value.ifBlank { "127.0.0.1" }, port, currentStatus.code)
                                    }
                                }
                            }

                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                isResolvingMdns.set(false)
                                Log.w(TAG, "mDNS pairing resolve failed: $errorCode")
                            }
                        })
                    } catch (e: Exception) {
                        isResolvingMdns.set(false)
                        Log.e(TAG, "Error resolving pairing service", e)
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {
                    isPairingDiscoveryActive.set(false)
                }
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    isPairingDiscoveryActive.set(false)
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    isPairingDiscoveryActive.set(false)
                }
            }

            val connectListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    isConnectDiscoveryActive.set(true)
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (!isResolvingMdns.compareAndSet(false, true)) return
                    try {
                        nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                                isResolvingMdns.set(false)
                                val port = resolvedInfo.port
                                if (port in 1024..65535) {
                                    Log.i(TAG, "Resolved ADB connect port: $port")
                                    _savedConnectPort.value = port
                                    persistPort(context, KEY_SAVED_CONNECT_PORT, port)
                                }
                            }

                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                isResolvingMdns.set(false)
                            }
                        })
                    } catch (_: Exception) {
                        isResolvingMdns.set(false)
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {
                    isConnectDiscoveryActive.set(false)
                }
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    isConnectDiscoveryActive.set(false)
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    isConnectDiscoveryActive.set(false)
                }
            }

            pairingDiscoveryListener = pairingListener
            connectDiscoveryListener = connectListener
            nsd.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, pairingListener)
            nsd.discoverServices("_adb-tls-connect._tcp", NsdManager.PROTOCOL_DNS_SD, connectListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate mDNS discovery", e)
        }
    }

    private fun stopMdnsDiscovery() {
        val nsd = nsdManager
        try {
            val pListener = pairingDiscoveryListener
            if (nsd != null && pListener != null && isPairingDiscoveryActive.compareAndSet(true, false)) {
                nsd.stopServiceDiscovery(pListener)
            }
        } catch (_: Exception) {
            isPairingDiscoveryActive.set(false)
        }

        try {
            val cListener = connectDiscoveryListener
            if (nsd != null && cListener != null && isConnectDiscoveryActive.compareAndSet(true, false)) {
                nsd.stopServiceDiscovery(cListener)
            }
        } catch (_: Exception) {
            isConnectDiscoveryActive.set(false)
        }

        isResolvingMdns.set(false)
        pairingDiscoveryListener = null
        connectDiscoveryListener = null
        nsdManager = null
    }
}
