package com.arwora

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.arwora.service.AdbPairingNotifier
import com.arwora.service.AdbPairingReceiver
import com.arwora.ui.components.WirelessPairingWizard
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.NativeAdbConnectionState
import com.arwora.utils.NativeAdbEndpoint
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.NativeAdbProtocol
import com.arwora.utils.PairingStatus
import com.arwora.utils.WirelessPairingManager
import com.arwora.utils.WirelessPairingMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdbNotificationPairingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @After
    fun tearDown() {
        NativeAdbManager.nsdConnectResolverOverride = null
        AdbPairingReceiver.successCancelDelayMs = 1_500L
        NativeAdbManager.disconnect(userInitiated = false)
    }

    @Test
    fun `ready notification includes adb_pairing_channel port text and key_pairing_code RemoteInput`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notification = AdbPairingNotifier.buildReadyNotification(
            context = context,
            host = "192.168.1.42",
            port = 41835
        )

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(AdbPairingNotifier.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)

        assertEquals(AdbPairingNotifier.CHANNEL_ID, notification.channelId)
        assertEquals(
            "Wireless Debugging Pairing Ready",
            notification.extras.getString(Notification.EXTRA_TITLE)
        )
        assertEquals(
            "Pairing port detected (41835). Enter the 6-digit code from Developer Options below.",
            notification.extras.getString(Notification.EXTRA_TEXT)
        )
        assertTrue((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)

        val actionCount = NotificationCompat.getActionCount(notification)
        assertTrue(actionCount >= 1)
        val replyAction = NotificationCompat.getAction(notification, 0)
        assertEquals("Enter 6-Digit Code", replyAction?.title?.toString())
        val remoteInputs = replyAction?.remoteInputs
        assertNotNull(remoteInputs)
        assertEquals(1, remoteInputs!!.size)
        assertEquals("key_pairing_code", remoteInputs[0].resultKey)
        assertEquals("Enter 6-Digit Code", remoteInputs[0].label?.toString())
    }

    @Test
    fun `pairing progress resolving connected timeout and failure notifications expose expected states`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val pairingNotif = AdbPairingNotifier.buildPairingProgressNotification(context)
        assertEquals("Pairing with arwora...", pairingNotif.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(pairingNotif.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))

        val resolvingNotif = AdbPairingNotifier.buildSuccessConnectingNotification(
            context = context,
            connected = false
        )
        assertEquals(
            "Paired! Resolving connection port...",
            resolvingNotif.extras.getString(Notification.EXTRA_TEXT)
        )
        assertTrue(resolvingNotif.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))

        val connectedNotif = AdbPairingNotifier.buildSuccessConnectingNotification(
            context = context,
            connected = true,
            detail = "127.0.0.1:35855"
        )
        assertEquals(
            "ADB Connected Successfully!",
            connectedNotif.extras.getString(Notification.EXTRA_TEXT)
        )

        val timeoutNotif = AdbPairingNotifier.buildConnectTimeoutNotification(
            context = context,
            reason = "Timed out resolving connection port.",
            pairingPort = 41835
        )
        assertEquals("ADB Connection Timed Out", timeoutNotif.extras.getString(Notification.EXTRA_TITLE))
        val retryConnectAction = NotificationCompat.getAction(timeoutNotif, 0)
        assertEquals("Retry Connect", retryConnectAction?.title?.toString())
        val retryShadowIntent = Shadows.shadowOf(retryConnectAction?.actionIntent).savedIntent
        assertEquals(AdbPairingNotifier.ACTION_RETRY_CONNECT, retryShadowIntent.action)
        assertEquals(41835, retryShadowIntent.getIntExtra(AdbPairingNotifier.EXTRA_PAIRING_PORT, -1))

        val failureNotif = AdbPairingNotifier.buildFailureNotification(context, "Connection refused")
        assertEquals(
            "Pairing Failed — Tap to Retry",
            failureNotif.extras.getString(Notification.EXTRA_TITLE)
        )
        val retryAction = NotificationCompat.getAction(failureNotif, 0)
        assertEquals("Pairing Failed — Tap to Retry", retryAction?.title?.toString())
    }

    @Test
    fun `extractSixDigitReplyCode validates Direct Reply text accurately`() {
        assertEquals("482910", AdbPairingReceiver.extractSixDigitReplyCode("482910"))
        assertEquals("649201", AdbPairingReceiver.extractSixDigitReplyCode(" 649 201 "))
        assertEquals("123456", AdbPairingReceiver.extractSixDigitReplyCode("code: 123456"))
        assertNull(AdbPairingReceiver.extractSixDigitReplyCode("12345"))
        assertNull(AdbPairingReceiver.extractSixDigitReplyCode("abcdef"))
        assertNull(AdbPairingReceiver.extractSixDigitReplyCode("   "))
    }

    @Test
    fun `mode 1 ui displays Start Notification Pairing primary pill button alongside manual fallback`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WirelessPairingManager.selectPairingMode(context, WirelessPairingMode.NOTIFICATION_MANUAL)

        composeTestRule.setContent {
            NameGenProTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    WirelessPairingWizard()
                }
            }
        }

        composeTestRule.onNodeWithTag("pairing_card_manual").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_start_notification_pairing_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Notification Pairing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manual Input Fallback").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_pairing_host_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_pairing_code_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_pairing_port_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("wireless_pair_manually_button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `post pairing transition excludes pairing port resolves mdns connect port and cancels notification`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        simulateWifiTransport(context)
        AdbPairingReceiver.successCancelDelayMs = 0L

        val pairingPortSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val connectPortSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val pairingPort = pairingPortSocket.localPort
        val connectPort = connectPortSocket.localPort

        var connectServerUsed = false
        val connectThread = thread(start = true) {
            while (!connectPortSocket.isClosed) {
                val client = try {
                    connectPortSocket.accept()
                } catch (_: Exception) {
                    break
                }
                var handled = false
                try {
                    client.use { socket ->
                        val pushback = java.io.PushbackInputStream(socket.getInputStream(), 4)
                        val firstByte = pushback.read()
                        if (firstByte == -1 || firstByte == 0x16) {
                            return@use
                        }
                        pushback.unread(firstByte)
                        val output = socket.getOutputStream()
                        connectServerUsed = true
                        NativeAdbProtocol.readPacket(pushback) // A_CNXN
                        NativeAdbProtocol.writePacket(
                            output,
                            NativeAdbProtocol.A_CNXN,
                            NativeAdbProtocol.A_VERSION,
                            NativeAdbProtocol.MAX_PAYLOAD,
                            "device::\u0000".toByteArray(Charsets.UTF_8)
                        )
                        val openPkt = NativeAdbProtocol.readPacket(pushback) // A_OPEN
                        val remoteId = 55
                        val localId = openPkt.arg0
                        NativeAdbProtocol.writePacket(output, NativeAdbProtocol.A_OKAY, remoteId, localId)
                        NativeAdbProtocol.writePacket(
                            output,
                            NativeAdbProtocol.A_WRTE,
                            remoteId,
                            localId,
                            "connected\nuid=2000(shell)\n".toByteArray(Charsets.UTF_8)
                        )
                        NativeAdbProtocol.readPacket(pushback) // A_OKAY
                        NativeAdbProtocol.writePacket(output, NativeAdbProtocol.A_CLSE, remoteId, localId)
                        handled = true
                    }
                } catch (_: Exception) {
                    if (!handled) continue
                }
                if (handled) break
            }
        }

        try {
            // Even if mDNS returns pairingPort first, connectAfterPairing must exclude it and use connectPort
            NativeAdbManager.nsdConnectResolverOverride = { _, _ ->
                listOf(
                    NativeAdbEndpoint("127.0.0.1", pairingPort),
                    NativeAdbEndpoint("127.0.0.1", connectPort)
                )
            }

            val receiver = AdbPairingReceiver()
            val ok = runBlocking {
                receiver.resolveAndConnectAfterPairing(context, pairingPort = pairingPort)
            }
            connectThread.join(3_000)

            assertTrue(ok)
            assertTrue(connectServerUsed)
            assertTrue(NativeAdbManager.isConnected.value)
            assertEquals(NativeAdbConnectionState.CONNECTED, NativeAdbManager.connectionState.value)
            assertEquals(connectPort, NativeAdbManager.connectedPort.value)
            assertTrue(WirelessPairingManager.status.value is PairingStatus.Success)
            assertEquals(
                "ADB Connected Successfully!",
                (WirelessPairingManager.status.value as PairingStatus.Success).message
            )

            // Notification is cancelled upon successful transition
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val active = Shadows.shadowOf(nm).getNotification(AdbPairingNotifier.NOTIFICATION_ID)
            assertNull(active)
        } finally {
            pairingPortSocket.close()
            connectPortSocket.close()
        }
    }

    @Test
    fun `post pairing timeout posts Retry Connect notification excluding pairing port`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        simulateWifiTransport(context)

        val pairingPort = 41835
        NativeAdbManager.nsdConnectResolverOverride = { _, _ ->
            // Simulate only stale pairing port visible -> must be rejected/excluded
            listOf(NativeAdbEndpoint("127.0.0.1", pairingPort))
        }

        val receiver = AdbPairingReceiver()
        val ok = runBlocking {
            receiver.resolveAndConnectAfterPairing(context, pairingPort = pairingPort)
        }

        assertFalse(ok)
        assertFalse(NativeAdbManager.isConnected.value)
        assertTrue(WirelessPairingManager.status.value is PairingStatus.Failed)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val posted = Shadows.shadowOf(nm).getNotification(AdbPairingNotifier.NOTIFICATION_ID)
        assertNotNull(posted)
        assertEquals("ADB Connection Timed Out", posted.extras.getString(Notification.EXTRA_TITLE))
        val retryAction = NotificationCompat.getAction(posted, 0)
        assertEquals("Retry Connect", retryAction?.title?.toString())
    }

    private fun simulateWifiTransport(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = Shadows.shadowOf(cm)
        val caps = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowCm.setNetworkCapabilities(cm.activeNetwork, caps)
    }
}
