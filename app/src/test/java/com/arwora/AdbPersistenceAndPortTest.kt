package com.arwora

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.arwora.service.AdbPairingNotifier
import com.arwora.service.AdbPairingReceiver
import com.arwora.ui.components.WirelessPairingWizard
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.EncryptedAdbCredentialStore
import com.arwora.utils.NativeAdbConnectionState
import com.arwora.utils.NativeAdbEndpoint
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.NativeAdbProtocol
import com.arwora.utils.WirelessPairingManager
import com.arwora.utils.WirelessPairingMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.PushbackInputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdbPersistenceAndPortTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NativeAdbManager.nsdConnectResolverOverride = null
        EncryptedAdbCredentialStore.clearSession(context)
        EncryptedAdbCredentialStore.resetCacheForTesting()
        NativeAdbManager.disconnect(userInitiated = false)
        WirelessPairingManager.selectPairingMode(context, WirelessPairingMode.NOTIFICATION_MANUAL)
    }

    @Test
    fun `parseCleanPort and extractPairingPort strip brackets spaces and host prefixes cleanly`() {
        assertEquals(38331, AdbPairingReceiver.parseCleanPort("38331"))
        assertEquals(38331, AdbPairingReceiver.parseCleanPort("[38331]"))
        assertEquals(38331, AdbPairingReceiver.parseCleanPort(" 38331] "))
        assertEquals(38331, AdbPairingReceiver.parseCleanPort("192.168.1.42:38331"))
        assertEquals(38331, AdbPairingReceiver.parseCleanPort("[192.168.1.42]:[38331]"))
        assertEquals(
            38331,
            WirelessPairingManager.extractPairingPort("Wireless debugging pairing code: 649201 on port: [38331]")
        )
        // Must NOT chop 6-digit code into a 5-digit port
        assertNull(WirelessPairingManager.extractPairingPort("Your pairing code is 649201"))
        // Reject out-of-range TCP ports
        assertNull(AdbPairingReceiver.parseCleanPort("[80]"))
        assertNull(AdbPairingReceiver.parseCleanPort("70000"))

        assertEquals("192.168.1.42", AdbPairingReceiver.parseCleanHost("[192.168.1.42]:38331"))
        assertEquals("127.0.0.1", AdbPairingReceiver.parseCleanHost(" 127.0.0.1 "))
    }

    @Test
    fun `extractPortFromIntent handles int and bracketed string extras reliably`() {
        val intIntent = Intent().putExtra(AdbPairingNotifier.EXTRA_PORT, 38331)
        assertEquals(38331, AdbPairingReceiver.extractPortFromIntent(intIntent))

        val bracketIntent = Intent().putExtra(AdbPairingNotifier.EXTRA_PORT, "[38331]")
        assertEquals(38331, AdbPairingReceiver.extractPortFromIntent(bracketIntent))

        val hostPortIntent = Intent().putExtra(AdbPairingNotifier.EXTRA_HOST, "192.168.1.15:41835")
        assertEquals(41835, AdbPairingReceiver.extractPortFromIntent(hostPortIntent))
        assertEquals(
            "192.168.1.15",
            AdbPairingReceiver.parseCleanHost(hostPortIntent.getStringExtra(AdbPairingNotifier.EXTRA_HOST))
        )
    }

    @Test
    fun `wireless debugging settings active flips state to Active Ready and suppresses false OFF warning`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        simulateWifiTransport(context)
        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1)
        Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 1)

        NativeAdbManager.nsdConnectResolverOverride = { _, _ -> emptyList() }
        val (ok, msg) = runBlocking {
            WirelessPairingManager.connectUnifiedWirelessAdb(context)
        }

        assertFalse(ok)
        assertTrue(NativeAdbManager.isWirelessDebuggingEnabled.value)
        assertFalse(NativeAdbManager.requiresDevOptionsGuidance.value)
        assertFalse(msg.contains("Wireless Debugging is OFF", ignoreCase = true))

        composeTestRule.setContent {
            NameGenProTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    WirelessPairingWizard()
                }
            }
        }

        composeTestRule.onNodeWithTag("pairing_mode_tab_auto_pair_on_wifi").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("wifi_auto_adb_status_badge").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Active / Ready").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("wifi_auto_dev_options_guidance").assertDoesNotExist()
    }

    @Test
    fun `persistent reconnect resumes saved port and falls back to mdns when port rotates`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        simulateWifiTransport(context)

        val rotatedServer = startSingleUseFakeAdbd()
        try {
            // Save previously paired session pointing to a closed/stale port
            EncryptedAdbCredentialStore.savePairedSession(
                context = context,
                host = "127.0.0.1",
                connectPort = 49999,
                pairingPort = 41111,
                rsaFingerprint = "AA:BB:CC"
            )
            val loaded = EncryptedAdbCredentialStore.loadState(context)
            assertTrue(loaded.isPaired)
            assertEquals(49999, loaded.connectPort)
            assertFalse(loaded.userStopped)

            NativeAdbManager.nsdConnectResolverOverride = { _, _ ->
                listOf(NativeAdbEndpoint("127.0.0.1", rotatedServer.localPort))
            }

            val (reconnected, detail) = runBlocking {
                NativeAdbManager.reconnectSavedSession(context)
            }
            assertTrue("Expected silent reconnect to succeed, got: $detail", reconnected)
            assertTrue(NativeAdbManager.isConnected.value)
            assertEquals(NativeAdbConnectionState.CONNECTED, NativeAdbManager.connectionState.value)
            assertEquals(rotatedServer.localPort, NativeAdbManager.connectedPort.value)

            val updatedCreds = EncryptedAdbCredentialStore.loadState(context)
            assertEquals(rotatedServer.localPort, updatedCreds.connectPort)

            // User-initiated disconnect pauses silent auto-reconnect until next explicit connect
            NativeAdbManager.disconnect(userInitiated = true)
            assertTrue(EncryptedAdbCredentialStore.loadState(context).userStopped)
            val (afterStopOk, _) = runBlocking {
                NativeAdbManager.reconnectSavedSession(context)
            }
            assertFalse(afterStopOk)
        } finally {
            rotatedServer.close()
        }
    }

    private fun startSingleUseFakeAdbd(): ServerSocket {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        thread(start = true) {
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                var handled = false
                try {
                    client.use { socket ->
                        val pushback = PushbackInputStream(socket.getInputStream(), 4)
                        val firstByte = pushback.read()
                        if (firstByte == -1 || firstByte == 0x16) {
                            return@use
                        }
                        pushback.unread(firstByte)
                        val output = socket.getOutputStream()
                        NativeAdbProtocol.readPacket(pushback) // A_CNXN
                        NativeAdbProtocol.writePacket(
                            output,
                            NativeAdbProtocol.A_CNXN,
                            NativeAdbProtocol.A_VERSION,
                            NativeAdbProtocol.MAX_PAYLOAD,
                            "device::\u0000".toByteArray(Charsets.UTF_8)
                        )
                        val openPkt = NativeAdbProtocol.readPacket(pushback) // A_OPEN
                        val remoteId = 88
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
        return server
    }

    private fun simulateWifiTransport(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = Shadows.shadowOf(cm)
        val caps = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowCm.setNetworkCapabilities(cm.activeNetwork, caps)
    }
}
