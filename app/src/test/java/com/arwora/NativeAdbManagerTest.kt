package com.arwora

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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import kotlin.concurrent.thread
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeAdbManagerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NativeAdbManager.nsdConnectResolverOverride = null
        WirelessPairingManager.selectPairingMode(context, WirelessPairingMode.NOTIFICATION_MANUAL)
        NativeAdbManager.disconnect(userInitiated = false)
    }

    @Test
    fun `adb protocol packet encode decode and unsigned checksum roundtrip`() {
        val payload = "host::\u0000".toByteArray(Charsets.UTF_8)
        val encoded = NativeAdbProtocol.encodePacket(
            command = NativeAdbProtocol.A_CNXN,
            arg0 = NativeAdbProtocol.A_VERSION,
            arg1 = NativeAdbProtocol.MAX_PAYLOAD,
            data = payload
        )

        val decoded = NativeAdbProtocol.decodePacketBytes(encoded)
        assertEquals(NativeAdbProtocol.A_CNXN, decoded.command)
        assertEquals(NativeAdbProtocol.A_VERSION, decoded.arg0)
        assertEquals(NativeAdbProtocol.MAX_PAYLOAD, decoded.arg1)
        assertArrayEquals(payload, decoded.data)

        // Verify little-endian magic header = command xor -1
        val header = ByteBuffer.wrap(encoded, 0, 24).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = header.getInt()
        header.getInt()
        header.getInt()
        val len = header.getInt()
        val check = header.getInt()
        val magic = header.getInt()
        assertEquals(payload.size, len)
        assertEquals(NativeAdbProtocol.calculateChecksum(payload), check)
        assertEquals(cmd xor -1, magic)
    }

    @Test
    fun `rsa keypair persists formats 524 byte android struct and signs auth tokens`() {
        val tempDir = createTempDirectory("adb_keys_test").toFile()
        try {
            val firstPair = NativeAdbProtocol.loadOrCreateKeyPair(tempDir)
            val secondPair = NativeAdbProtocol.loadOrCreateKeyPair(tempDir)

            assertTrue(File(tempDir, "adbkey").exists())
            assertTrue(File(tempDir, "adbkey.pub").exists())
            assertArrayEquals(firstPair.public.encoded, secondPair.public.encoded)
            assertArrayEquals(firstPair.private.encoded, secondPair.private.encoded)

            val rsaPub = firstPair.public as RSAPublicKey
            val structBytes = NativeAdbProtocol.encodeAndroidRsaPublicKeyStruct(rsaPub)
            assertEquals(524, structBytes.size)
            val lenWord = ByteBuffer.wrap(structBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()
            assertEquals(64, lenWord)

            assertEquals("arwora", NativeAdbProtocol.DEFAULT_CLIENT_IDENTITY)
            assertEquals("arwora", NativeAdbManager.ensureClientIdentity())

            val defaultPubPayload = NativeAdbProtocol.formatAdbPublicKeyPayload(rsaPub)
            val defaultPubText = String(defaultPubPayload, Charsets.UTF_8)
            assertTrue(defaultPubText.endsWith(" arwora\u0000"))
            org.junit.Assert.assertFalse(defaultPubText.contains("@android"))

            val pubPayload = NativeAdbProtocol.formatAdbPublicKeyPayload(rsaPub, "test@arwora")
            val pubText = String(pubPayload, Charsets.UTF_8)
            assertTrue(pubText.endsWith(" test@arwora\u0000"))

            val token = ByteArray(20) { (it + 1).toByte() }
            val sigBytes = NativeAdbProtocol.signToken(token, firstPair.private)
            val sha1Prefix = byteArrayOf(
                0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e,
                0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14
            )
            val digestInfo = ByteArrayOutputStream().apply {
                write(sha1Prefix)
                write(token)
            }.toByteArray()

            val verifier = Signature.getInstance("NONEwithRSA")
            verifier.initVerify(firstPair.public)
            verifier.update(digestInfo)
            assertTrue(verifier.verify(sigBytes))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `resolveConnectionState maps all four native adb states accurately`() {
        assertEquals(
            NativeAdbConnectionState.CONNECTED,
            NativeAdbManager.resolveConnectionState(
                isWifiConnected = true,
                isConnecting = false,
                isConnected = true
            )
        )
        assertEquals(
            NativeAdbConnectionState.CONNECTING,
            NativeAdbManager.resolveConnectionState(
                isWifiConnected = true,
                isConnecting = true,
                isConnected = false
            )
        )
        assertEquals(
            NativeAdbConnectionState.WIFI_REQUIRED,
            NativeAdbManager.resolveConnectionState(
                isWifiConnected = false,
                isConnecting = false,
                isConnected = false
            )
        )
        assertEquals(
            NativeAdbConnectionState.DISCONNECTED,
            NativeAdbManager.resolveConnectionState(
                isWifiConnected = true,
                isConnecting = false,
                isConnected = false
            )
        )
    }

    @Test
    fun `mode 3 wifi auto card hides host and port inputs and shows connect now button`() {
        composeTestRule.setContent {
            NameGenProTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    WirelessPairingWizard()
                }
            }
        }

        // Switch to Mode 3 (Auto-Pair on Any Wi-Fi)
        composeTestRule.onNodeWithTag("pairing_mode_tab_auto_pair_on_wifi").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("pairing_card_wifi_auto").performScrollTo().assertIsDisplayed()

        // Simplified primary button must be displayed
        composeTestRule.onNodeWithTag("wifi_auto_save_trigger_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect Now").performScrollTo().assertIsDisplayed()

        // Removed host/port/cached target fields and Shizuku references must not exist
        composeTestRule.onNodeWithText("Cached Wireless Debugging Target").assertDoesNotExist()
        composeTestRule.onNodeWithText("Target Host").assertDoesNotExist()
        composeTestRule.onNodeWithText("ADB Port (Opt.)").assertDoesNotExist()
        composeTestRule.onNodeWithText("Save & Trigger Check").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hero_shizuku_status_chip").assertDoesNotExist()
    }

    @Test
    fun `verified handshake requires shell echo stream and uses arwora client identity`() {
        val tempDir = createTempDirectory("adb_handshake_test").toFile()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        var receivedPubComment: String? = null
        var receivedShellService: String? = null

        val serverThread = thread(start = true) {
            server.accept().use { client ->
                val input = client.getInputStream()
                val output = client.getOutputStream()

                val cnxn = NativeAdbProtocol.readPacket(input)
                assertEquals(NativeAdbProtocol.A_CNXN, cnxn.command)

                // Request signature first, then RSA public key, then approve A_CNXN
                val token = ByteArray(20) { 0x42 }
                NativeAdbProtocol.writePacket(output, NativeAdbProtocol.A_AUTH, NativeAdbProtocol.AUTH_TOKEN, 0, token)
                val sigPkt = NativeAdbProtocol.readPacket(input)
                assertEquals(NativeAdbProtocol.A_AUTH, sigPkt.command)

                NativeAdbProtocol.writePacket(output, NativeAdbProtocol.A_AUTH, NativeAdbProtocol.AUTH_TOKEN, 0, token)
                val pubPkt = NativeAdbProtocol.readPacket(input)
                assertEquals(NativeAdbProtocol.A_AUTH, pubPkt.command)
                receivedPubComment = String(pubPkt.data, Charsets.UTF_8)

                NativeAdbProtocol.writePacket(
                    output,
                    NativeAdbProtocol.A_CNXN,
                    NativeAdbProtocol.A_VERSION,
                    NativeAdbProtocol.MAX_PAYLOAD,
                    "device::\u0000".toByteArray(Charsets.UTF_8)
                )

                val openPkt = NativeAdbProtocol.readPacket(input)
                assertEquals(NativeAdbProtocol.A_OPEN, openPkt.command)
                receivedShellService = String(openPkt.data, Charsets.UTF_8)
                val remoteId = 77
                val localId = openPkt.arg0

                NativeAdbProtocol.writePacket(output, NativeAdbProtocol.A_OKAY, remoteId, localId)
                NativeAdbProtocol.writePacket(
                    output,
                    NativeAdbProtocol.A_WRTE,
                    remoteId,
                    localId,
                    "connected\nuid=2000(shell)\n".toByteArray(Charsets.UTF_8)
                )
                val clientOkay = NativeAdbProtocol.readPacket(input)
                assertEquals(NativeAdbProtocol.A_OKAY, clientOkay.command)
                NativeAdbProtocol.writePacket(output, NativeAdbProtocol.A_CLSE, remoteId, localId)
            }
        }

        try {
            val keyPair = NativeAdbProtocol.loadOrCreateKeyPair(tempDir)
            val (ok, detail) = NativeAdbManager.performVerifiedHandshake("127.0.0.1", server.localPort, keyPair)
            serverThread.join(3_000)

            assertTrue("Expected handshake to succeed but got: $detail", ok)
            assertTrue(receivedPubComment?.endsWith(" arwora\u0000") == true)
            assertFalse(receivedPubComment?.contains("@android") == true)
            assertEquals("shell:echo connected && id\u0000", receivedShellService)
        } finally {
            server.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `verified handshake fails when adb closes shell stream without A_OKAY`() {
        val tempDir = createTempDirectory("adb_reject_test").toFile()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val serverThread = thread(start = true) {
            server.accept().use { client ->
                val input = client.getInputStream()
                val output = client.getOutputStream()
                NativeAdbProtocol.readPacket(input) // A_CNXN
                NativeAdbProtocol.writePacket(
                    output,
                    NativeAdbProtocol.A_CNXN,
                    NativeAdbProtocol.A_VERSION,
                    NativeAdbProtocol.MAX_PAYLOAD,
                    "device::\u0000".toByteArray(Charsets.UTF_8)
                )
                val openPkt = NativeAdbProtocol.readPacket(input) // A_OPEN
                NativeAdbProtocol.writePacket(output, NativeAdbProtocol.A_CLSE, 0, openPkt.arg0)
            }
        }

        try {
            val keyPair = NativeAdbProtocol.loadOrCreateKeyPair(tempDir)
            val (ok, _) = NativeAdbManager.performVerifiedHandshake("127.0.0.1", server.localPort, keyPair)
            serverThread.join(3_000)
            assertFalse(ok)
            assertFalse(NativeAdbManager.isAdbReady())
        } finally {
            server.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `unified wireless adb connect fails loudly without fake success when mdns returns empty`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        simulateWifiTransport(context)
        NativeAdbManager.nsdConnectResolverOverride = { _, _ -> emptyList() }

        val (ok, msg) = runBlocking {
            WirelessPairingManager.connectUnifiedWirelessAdb(context)
        }

        assertFalse(ok)
        assertEquals(NativeAdbManager.ERROR_WIRELESS_DEBUGGING_OFF, msg)
        assertFalse(NativeAdbManager.isConnected.value)
        assertTrue(NativeAdbManager.requiresDevOptionsGuidance.value)
        assertTrue(WirelessPairingManager.status.value is PairingStatus.Failed)
    }

    @Test
    fun `unified wireless adb connect discovers port verifies shell and flips connected state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        simulateWifiTransport(context)

        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val serverThread = thread(start = true) {
            server.accept().use { client ->
                val input = client.getInputStream()
                val output = client.getOutputStream()
                NativeAdbProtocol.readPacket(input) // A_CNXN
                NativeAdbProtocol.writePacket(
                    output,
                    NativeAdbProtocol.A_CNXN,
                    NativeAdbProtocol.A_VERSION,
                    NativeAdbProtocol.MAX_PAYLOAD,
                    "device::\u0000".toByteArray(Charsets.UTF_8)
                )
                val openPkt = NativeAdbProtocol.readPacket(input) // A_OPEN
                val remoteId = 99
                val localId = openPkt.arg0
                NativeAdbProtocol.writePacket(output, NativeAdbProtocol.A_OKAY, remoteId, localId)
                NativeAdbProtocol.writePacket(
                    output,
                    NativeAdbProtocol.A_WRTE,
                    remoteId,
                    localId,
                    "connected\nuid=2000(shell)\n".toByteArray(Charsets.UTF_8)
                )
                NativeAdbProtocol.readPacket(input) // A_OKAY
                NativeAdbProtocol.writePacket(output, NativeAdbProtocol.A_CLSE, remoteId, localId)
            }
        }

        try {
            NativeAdbManager.nsdConnectResolverOverride = { _, _ ->
                listOf(NativeAdbEndpoint("127.0.0.1", server.localPort))
            }

            val (ok, msg) = runBlocking {
                WirelessPairingManager.connectUnifiedWirelessAdb(context)
            }
            serverThread.join(3_000)

            assertTrue("Expected unified connect success but got: $msg", ok)
            assertTrue(NativeAdbManager.isConnected.value)
            assertEquals(NativeAdbConnectionState.CONNECTED, NativeAdbManager.connectionState.value)
            assertEquals(server.localPort, NativeAdbManager.discoveredPort.value)
            assertEquals("127.0.0.1", NativeAdbManager.connectedHost.value)
            assertFalse(NativeAdbManager.requiresDevOptionsGuidance.value)
            assertTrue(WirelessPairingManager.status.value is PairingStatus.Success)
        } finally {
            server.close()
        }
    }

    @Test
    fun `mode 3 shows open developer options guidance when wireless debugging connection fails`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        simulateWifiTransport(context)
        NativeAdbManager.nsdConnectResolverOverride = { _, _ -> emptyList() }
        runBlocking {
            WirelessPairingManager.connectUnifiedWirelessAdb(context)
        }

        composeTestRule.setContent {
            NameGenProTheme {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    WirelessPairingWizard()
                }
            }
        }

        composeTestRule.onNodeWithTag("pairing_mode_tab_auto_pair_on_wifi").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("wifi_auto_dev_options_guidance").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("wifi_auto_open_dev_options_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("wifi_auto_adb_status_badge").assertDoesNotExist()
    }

    private fun simulateWifiTransport(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = Shadows.shadowOf(cm)
        val caps = ShadowNetworkCapabilities.newInstance()
        Shadows.shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowCm.setNetworkCapabilities(cm.activeNetwork, caps)
    }
}
