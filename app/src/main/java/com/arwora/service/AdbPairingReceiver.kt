package com.arwora.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.WirelessPairingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that intercepts Direct Reply ([RemoteInput]) submissions containing
 * the 6-digit Wireless Debugging pairing code from [AdbPairingNotifier], executes the
 * native ADB pairing handshake asynchronously on [Dispatchers.IO] with client identity
 * strictly set to `arwora`, updates notification progress/result states, and triggers the
 * native wireless ADB connection (`_adb-tls-connect._tcp`) upon pairing success.
 */
class AdbPairingReceiver : BroadcastReceiver() {

    companion object {
        private val DIGIT_RUN_REGEX = Regex("\\d{6}")

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
                        host = WirelessPairingManager.savedHost.value.ifBlank { "127.0.0.1" },
                        port = WirelessPairingManager.discoveredPort.value
                    )
                )
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

                val extraHost = intent.getStringExtra(AdbPairingNotifier.EXTRA_HOST)
                val extraPort = intent.getIntExtra(AdbPairingNotifier.EXTRA_PORT, -1)
                    .takeIf { it in 1024..65535 }

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        NativeAdbManager.ensureClientIdentity()
                        WirelessPairingManager.init(appContext)

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
                            AdbPairingNotifier.notifySafely(
                                appContext,
                                AdbPairingNotifier.buildSuccessConnectingNotification(
                                    context = appContext,
                                    connected = false
                                )
                            )

                            val (connected, connectDetail) = NativeAdbManager.connectAutomatically(appContext)
                            AdbPairingNotifier.notifySafely(
                                appContext,
                                AdbPairingNotifier.buildSuccessConnectingNotification(
                                    context = appContext,
                                    connected = true,
                                    detail = if (connected) {
                                        "Paired & connected as ${NativeAdbManager.CLIENT_IDENTITY} ($connectDetail)"
                                    } else {
                                        "Paired Successfully! $connectDetail"
                                    }
                                )
                            )
                            try {
                                appContext.stopService(
                                    Intent(appContext, AdbPairingNotificationService::class.java)
                                )
                            } catch (_: Exception) {
                            }
                        } else {
                            AdbPairingNotifier.notifySafely(
                                appContext,
                                AdbPairingNotifier.buildFailureNotification(
                                    context = appContext,
                                    reason = message
                                )
                            )
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
