package com.arwora.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.RemoteInput
import com.arwora.MainActivity
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.WirelessPairingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that intercepts Direct Reply ([RemoteInput]) submissions containing
 * the 6-digit Wireless Debugging pairing code from [AdbPairingNotifier], executes the
 * native ADB pairing handshake asynchronously on [Dispatchers.IO] with client identity
 * strictly set to `arwora`, resolves the separate `_adb-tls-connect._tcp` connection port
 * via `NsdManager`, and opens an active TLS ADB connection without reusing the pairing port.
 */
class AdbPairingReceiver : BroadcastReceiver() {

    companion object {
        private val DIGIT_RUN_REGEX = Regex("\\d{6}")
        private val HOST_PORT_REGEX = Regex("(?:[\\[(]?([\\w.:-]+)[\\])]?):\\[?(\\d{4,5})\\]?(?!\\d)")
        private val EXPLICIT_PORT_REGEX = Regex("(?i)port\\s*[:=]?\\s*\\[?(\\d{4,5})\\]?(?!\\d)")
        private val STANDALONE_PORT_REGEX = Regex("(?<!\\d)\\[?(\\d{4,5})\\]?(?!\\d)")
        internal var successCancelDelayMs: Long = 1_500L

        internal fun extractSixDigitReplyCode(rawInput: CharSequence?): String? {
            if (rawInput.isNullOrBlank()) return null
            val trimmed = rawInput.toString().trim()
            if (trimmed.length == 6 && trimmed.all { it.isDigit() }) {
                return trimmed
            }
            val digitsOnly = trimmed.filter { it.isDigit() }
            if (digitsOnly.length == 6) {
                return digitsOnly
            }
            return DIGIT_RUN_REGEX.find(trimmed)?.value
        }

        /**
         * Strictly parses a TCP port integer (1024..65535) from raw mDNS/notification strings or extras,
         * stripping brackets (`[38331]`), trailing punctuation (`38331]`), whitespace, and `host:port` prefixes,
         * while never truncating 6-digit pairing codes into 5-digit port numbers.
         */
        fun parseCleanPort(raw: CharSequence?): Int? {
            if (raw.isNullOrBlank()) return null
            val text = raw.toString().trim()

            val stripped = text.trim('[', ']', '(', ')', '"', '\'', ' ', '\t', '\r', '\n')
            stripped.toIntOrNull()?.let { direct ->
                if (direct in 1024..65535) return direct
            }

            EXPLICIT_PORT_REGEX.findAll(text).forEach { match ->
                val candidate = match.groupValues[1].toIntOrNull()
                if (candidate != null && candidate in 1024..65535) return candidate
            }

            HOST_PORT_REGEX.findAll(text).forEach { match ->
                val candidate = match.groupValues[2].toIntOrNull()
                if (candidate != null && candidate in 1024..65535) return candidate
            }

            STANDALONE_PORT_REGEX.findAll(text).forEach { match ->
                val candidate = match.groupValues[1].toIntOrNull()
                if (candidate != null && candidate in 1024..65535) return candidate
            }

            return null
        }

        fun parseCleanHost(raw: CharSequence?): String? {
            if (raw.isNullOrBlank()) return null
            var cleaned = raw.toString().trim()
                .trim('"', '\'', ' ')
            if (cleaned.startsWith("[") && cleaned.contains("]:")) {
                cleaned = cleaned.substringAfter("[").substringBefore("]:")
            } else {
                val lastColon = cleaned.lastIndexOf(':')
                if (lastColon > 0 && cleaned.count { it == ':' } == 1) {
                    val maybePort = cleaned.substring(lastColon + 1).trim('[', ']', ' ')
                    if (maybePort.all { it.isDigit() }) {
                        cleaned = cleaned.substring(0, lastColon)
                    }
                }
            }
            cleaned = cleaned.trim('[', ']', ' ')
            return cleaned.takeIf { it.isNotEmpty() }
        }

        internal fun extractPortFromIntent(
            intent: Intent,
            primaryKey: String = AdbPairingNotifier.EXTRA_PORT
        ): Int? {
            val intVal = intent.getIntExtra(primaryKey, -1)
            if (intVal in 1024..65535) return intVal

            val strVal = intent.getStringExtra(primaryKey)
                ?: intent.getCharSequenceExtra(primaryKey)
            parseCleanPort(strVal)?.let { return it }

            val fallbackInt = intent.getIntExtra(AdbPairingNotifier.EXTRA_PAIRING_PORT, -1)
            if (fallbackInt in 1024..65535) return fallbackInt

            parseCleanPort(intent.getStringExtra(AdbPairingNotifier.EXTRA_PAIRING_PORT))?.let { return it }
            parseCleanPort(intent.getStringExtra(AdbPairingNotifier.EXTRA_HOST))?.let { return it }
            parseCleanPort(intent.getStringExtra(Intent.EXTRA_TEXT))?.let { return it }
            return null
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val appContext = context.applicationContext

        when (intent.action) {
            AdbPairingNotifier.ACTION_STOP_PAIRING -> {
                AdbPairingNotificationService.stop(appContext)
                return
            }

            AdbPairingNotifier.ACTION_RETRY_PAIRING -> {
                NativeAdbManager.ensureClientIdentity()
                WirelessPairingManager.init(appContext)
                AdbPairingNotificationService.start(appContext)
                AdbPairingNotifier.notifySafely(
                    appContext,
                    AdbPairingNotifier.buildReadyNotification(
                        context = appContext,
                        host = parseCleanHost(WirelessPairingManager.savedHost.value) ?: "127.0.0.1",
                        port = WirelessPairingManager.discoveredPort.value
                    )
                )
                return
            }

            AdbPairingNotifier.ACTION_RETRY_CONNECT -> {
                val pairingPort = extractPortFromIntent(intent, AdbPairingNotifier.EXTRA_PAIRING_PORT)
                    ?: WirelessPairingManager.lastPairingPort

                val dispatchedToService = AdbPairingNotificationService.runWorkOnServiceScope {
                    resolveAndConnectAfterPairing(appContext, pairingPort)
                }
                if (!dispatchedToService) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                        try {
                            resolveAndConnectAfterPairing(appContext, pairingPort)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
                return
            }

            AdbPairingNotifier.ACTION_SUBMIT_PAIRING_CODE -> {
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val rawCode = remoteInput?.getCharSequence(AdbPairingNotifier.KEY_PAIRING_CODE)
                val pairingCode = extractSixDigitReplyCode(rawCode)

                if (pairingCode == null) {
                    AdbPairingNotifier.notifySafely(
                        appContext,
                        AdbPairingNotifier.buildFailureNotification(
                            context = appContext,
                            reason = "Enter a valid 6-digit pairing code."
                        )
                    )
                    return
                }

                val extraHost = parseCleanHost(intent.getStringExtra(AdbPairingNotifier.EXTRA_HOST))
                val extraPort = extractPortFromIntent(intent, AdbPairingNotifier.EXTRA_PORT)

                val work: suspend () -> Unit = {
                    NativeAdbManager.ensureClientIdentity()
                    WirelessPairingManager.init(appContext)

                    // Step 1: Pairing with arwora...
                    AdbPairingNotifier.notifySafely(
                        appContext,
                        AdbPairingNotifier.buildPairingProgressNotification(appContext)
                    )

                    val (paired, message) = WirelessPairingManager.executeNotificationReplyPairing(
                        context = appContext,
                        host = extraHost,
                        port = extraPort,
                        code = pairingCode
                    )

                    if (paired) {
                        AdbPairingNotificationService.pausePairingDiscovery()
                        val usedPairingPort = extraPort ?: WirelessPairingManager.lastPairingPort
                        // Brief pause so NsdManager finishes unregistering pairing listener cleanly
                        delay(120L)
                        resolveAndConnectAfterPairing(appContext, usedPairingPort)
                    } else {
                        AdbPairingNotifier.notifySafely(
                            appContext,
                            AdbPairingNotifier.buildFailureNotification(
                                context = appContext,
                                reason = message
                            )
                        )
                    }
                }

                val dispatchedToService = AdbPairingNotificationService.runWorkOnServiceScope { work() }
                if (!dispatchedToService) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                        try {
                            work()
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }

    internal suspend fun resolveAndConnectAfterPairing(
        appContext: Context,
        pairingPort: Int?
    ): Boolean {
        NativeAdbManager.ensureClientIdentity()
        WirelessPairingManager.init(appContext)

        // Step 2: Paired successfully! Connecting ADB...
        AdbPairingNotifier.notifySafely(
            appContext,
            AdbPairingNotifier.buildSuccessConnectingNotification(
                context = appContext,
                connected = false
            )
        )

        val (connected, connectDetail) = NativeAdbManager.connectAfterPairing(
            context = appContext,
            pairingPort = pairingPort
        )
        val connectedHost = parseCleanHost(NativeAdbManager.connectedHost.value) ?: "127.0.0.1"
        val connectedPort = NativeAdbManager.connectedPort.value

        WirelessPairingManager.markNotificationConnectResult(
            context = appContext,
            connected = connected,
            host = connectedHost,
            port = connectedPort,
            detail = connectDetail
        )

        if (connected && NativeAdbManager.isAdbReady()) {
            AdbConnectionService.startPersistentSession(appContext)
            // Step 3: ADB Connected Successfully!
            val cleanEndpoint = if (connectedPort != null && connectedPort in 1024..65535) {
                "$connectedHost:$connectedPort"
            } else {
                connectedHost
            }
            AdbPairingNotifier.notifySafely(
                appContext,
                AdbPairingNotifier.buildSuccessConnectingNotification(
                    context = appContext,
                    connected = true,
                    detail = cleanEndpoint
                )
            )
            if (successCancelDelayMs > 0L) {
                delay(successCancelDelayMs)
            }
            AdbPairingNotificationService.stop(appContext)
            AdbPairingNotifier.cancelNotification(appContext)

            // Safely bring app to foreground if overlay/foreground permission allows;
            // UI status badge already updates reactively to green "Active / Connected".
            runCatching {
                if (Settings.canDrawOverlays(appContext)) {
                    val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        )
                    }
                    appContext.startActivity(launchIntent)
                }
            }
            return true
        } else {
            // Stop foreground pairing service cleanly without dismissing the Retry Connect notification
            runCatching {
                appContext.stopService(Intent(appContext, AdbPairingNotificationService::class.java))
            }
            AdbPairingNotifier.notifySafely(
                appContext,
                AdbPairingNotifier.buildConnectTimeoutNotification(
                    context = appContext,
                    reason = connectDetail,
                    pairingPort = pairingPort
                )
            )
            return false
        }
    }
}
