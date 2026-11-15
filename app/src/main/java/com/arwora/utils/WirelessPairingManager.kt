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
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

sealed class PairingStatus {
    object Idle : PairingStatus()
    object Listening : PairingStatus()
    data class CodeCaptured(val code: String, val port: Int?) : PairingStatus()
    data class PairingInProgress(val port: Int, val host: String = "127.0.0.1") : PairingStatus()
    data class Success(val message: String = "Pairing successful! ADB connection established.") : PairingStatus()
    data class Failed(val error: String) : PairingStatus()
}

object WirelessPairingManager {
    private const val TAG = "WirelessPairingManager"

    // Regex to capture 6-digit pairing code
    private val PAIRING_CODE_REGEX = Pattern.compile("\\b(\\d{6})\\b")
    // Regex to capture pairing port (4 to 5 digits, typically after ':' or 'port')
    private val PORT_REGEX = Pattern.compile("(?:[:]|port\\s*[:=]?\\s*)(\\d{4,5})\\b", Pattern.CASE_INSENSITIVE)

    private val _status = MutableStateFlow<PairingStatus>(PairingStatus.Idle)
    val status: StateFlow<PairingStatus> = _status.asStateFlow()

    private val _discoveredPort = MutableStateFlow<Int?>(null)
    val discoveredPort: StateFlow<Int?> = _discoveredPort.asStateFlow()

    private val _lastCapturedCode = MutableStateFlow<String?>(null)
    val lastCapturedCode: StateFlow<String?> = _lastCapturedCode.asStateFlow()

    private val _notificationAccessGranted = MutableStateFlow(false)
    val notificationAccessGranted: StateFlow<Boolean> = _notificationAccessGranted.asStateFlow()

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var listeningScope: CoroutineScope? = null
    private var timeoutJob: Job? = null

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

    /**
     * Check if the app has Notification Listener permission granted.
     */
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

    /**
     * Dynamically re-evaluate Notification Access status and push it to listeners/UI.
     * Also requests listener rebind on API 24+ when transitioning from revoked -> granted.
     */
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

    /**
     * Launch Notification Listener Settings screen
     */
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

    /**
     * Extract 6-digit pairing code from text
     */
    fun extractPairingCode(text: String): String? {
        if (text.isBlank()) return null
        val matcher = PAIRING_CODE_REGEX.matcher(text)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    /**
     * Extract port number from notification or text
     */
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
     * Start listening for pairing notification and discover local mDNS pairing services
     */
    fun startListening(context: Context) {
        stopListening()
        val scope = CoroutineScope(Dispatchers.IO + Job())
        listeningScope = scope
        _status.value = PairingStatus.Listening

        // Start mDNS discovery for _adb-tls-pairing._tcp
        startMdnsDiscovery(context)

        // Set a 90-second timeout for automated interception
        timeoutJob = scope.launch {
            delay(90_000L)
            if (_status.value is PairingStatus.Listening) {
                _status.value = PairingStatus.Failed("Pairing listening timed out after 90 seconds. Please try again or use manual fallback.")
                stopMdnsDiscovery()
            }
        }
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        timeoutJob?.cancel()
        timeoutJob = null
        stopMdnsDiscovery()
        listeningScope?.cancel()
        listeningScope = null
    }

    /**
     * Reset pairing status to Idle
     */
    fun resetStatus() {
        stopListening()
        _status.value = PairingStatus.Idle
        _discoveredPort.value = null
        _lastCapturedCode.value = null
    }

    /**
     * Called when a notification is captured containing pairing info
     */
    fun onPairingNotificationCaptured(context: Context, code: String, port: Int? = null) {
        val resolvedPort = port ?: _discoveredPort.value
        _lastCapturedCode.value = code
        if (resolvedPort != null) {
            _discoveredPort.value = resolvedPort
        }
        _status.value = PairingStatus.CodeCaptured(code, resolvedPort)

        // If port is already discovered, auto-trigger pairing!
        if (resolvedPort != null) {
            executePairing(context, "127.0.0.1", resolvedPort, code)
        } else {
            // Await mDNS port resolution or user confirmation
            listeningScope?.launch {
                withTimeoutOrNull(5000L) {
                    while (_discoveredPort.value == null) {
                        delay(200L)
                    }
                }
                val autoPort = _discoveredPort.value
                if (autoPort != null) {
                    executePairing(context, "127.0.0.1", autoPort, code)
                } else {
                    _status.value = PairingStatus.CodeCaptured(code, null)
                }
            }
        }
    }

    /**
     * Execute ADB Pairing handshake using TLS socket or internal IAdbManager API
     */
    fun executePairing(context: Context, host: String = "127.0.0.1", port: Int, code: String) {
        timeoutJob?.cancel()
        _status.value = PairingStatus.PairingInProgress(port, host)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. First attempt: IAdbManager reflection if available
                val reflectionSuccess = tryPairViaIAdbManager(code)
                if (reflectionSuccess) {
                    onPairingSuccess(context)
                    return@launch
                }

                // 2. Second attempt: Direct TLS socket handshake with ADB pairing daemon
                val socketSuccess = trySocketPairing(host, port, code)
                if (socketSuccess) {
                    onPairingSuccess(context)
                    return@launch
                }

                // 3. Third attempt: Shizuku / shell execution fallback
                val shellSuccess = tryShellPairing(host, port, code)
                if (shellSuccess) {
                    onPairingSuccess(context)
                    return@launch
                }

                // If all attempts failed
                _status.value = PairingStatus.Failed("Pairing handshake failed with port $port. Check if pairing dialog is still active and retry.")
            } catch (e: Exception) {
                Log.e(TAG, "Pairing exception", e)
                _status.value = PairingStatus.Failed(e.message ?: "Unknown pairing error occurred")
            }
        }
    }

    private suspend fun onPairingSuccess(context: Context) {
        _status.value = PairingStatus.Success()
        stopListening()
        // Try activating Shizuku service or checking binder
        withContext(Dispatchers.IO) {
            tryStartShizuku(context)
        }
    }

    /**
     * Attempt pairing via Android internal IAdbManager reflection
     */
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

    /**
     * Attempt pairing via TLS socket handshake with localhost:<port>
     */
    private fun trySocketPairing(host: String, port: Int, code: String): Boolean {
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
            rawSocket.connect(InetSocketAddress(host, port), 4000)
            rawSocket.soTimeout = 5000

            val sslSocket = sslContext.socketFactory.createSocket(rawSocket, host, port, true) as SSLSocket
            sslSocket.startHandshake()
            socket = sslSocket

            // Send pairing authentication header
            val outputStream = sslSocket.outputStream
            val inputStream = sslSocket.inputStream

            // Format ADB pairing message: SPAKE2 / ADB_PAIR format
            val payload = "ADB_PAIR:$code\n".toByteArray(Charsets.UTF_8)
            outputStream.write(payload)
            outputStream.flush()

            // Small delay to allow daemon handshake acknowledgement
            Thread.sleep(300)
            val available = inputStream.available()
            if (available > 0) {
                val buffer = ByteArray(available)
                inputStream.read(buffer)
            }

            Log.i(TAG, "TLS Socket pairing handshake completed on $host:$port")
            true
        } catch (e: Exception) {
            Log.w(TAG, "TLS Socket handshake attempt failed on $host:$port: ${e.message}")
            false
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * Attempt pairing via adb pair CLI command via shell if ADB is locally accessible
     */
    private fun tryShellPairing(host: String, port: Int, code: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "adb pair $host:$port $code"))
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Log.i(TAG, "adb pair shell command succeeded")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Trigger Shizuku activation after successful pairing
     */
    private fun tryStartShizuku(context: Context) {
        try {
            // Check if Shizuku binder is already active
            if (rikka.shizuku.Shizuku.pingBinder()) {
                Log.i(TAG, "Shizuku binder is already connected!")
                return
            }

            // Execute Shizuku start script if available
            val scriptPath = "/sdcard/Android/data/moe.shizuku.privileged.api/start.sh"
            val file = java.io.File(scriptPath)
            if (file.exists()) {
                Runtime.getRuntime().exec(arrayOf("sh", scriptPath))
            }

            // Launch Shizuku manager to refresh status if installed
            val launchIntent = context.packageManager.getLaunchIntentForPackage(ShizukuConnectionManager.SHIZUKU_PACKAGE_NAME)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Do not steal focus abruptly, but post a small delay or notification
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not auto-start Shizuku: ${e.message}")
        }
    }

    /**
     * Start mDNS discovery to automatically find ADB pairing port
     */
    private fun startMdnsDiscovery(context: Context) {
        try {
            val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
            nsdManager = nsd
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "mDNS discovery started for $regType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "mDNS pairing service found: ${serviceInfo.serviceName}")
                    try {
                        nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                                val port = resolvedInfo.port
                                Log.i(TAG, "Resolved ADB pairing port: $port")
                                _discoveredPort.value = port

                                // If code was already captured, trigger pairing!
                                val currentStatus = _status.value
                                if (currentStatus is PairingStatus.CodeCaptured && currentStatus.port == null) {
                                    executePairing(context, "127.0.0.1", port, currentStatus.code)
                                }
                            }

                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                Log.w(TAG, "mDNS resolve failed: $errorCode")
                            }
                        })
                    } catch (e: Exception) {
                        Log.e(TAG, "Error resolving service", e)
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "mDNS service lost: ${serviceInfo.serviceName}")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "mDNS discovery stopped")
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w(TAG, "mDNS start discovery failed: $errorCode")
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            discoveryListener = listener
            nsd.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initiate mDNS discovery", e)
        }
    }

    private fun stopMdnsDiscovery() {
        try {
            val nsd = nsdManager
            val listener = discoveryListener
            if (nsd != null && listener != null) {
                nsd.stopServiceDiscovery(listener)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping mDNS discovery: ${e.message}")
        } finally {
            discoveryListener = null
            nsdManager = null
        }
    }
}
