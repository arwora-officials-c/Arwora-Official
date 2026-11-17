package com.arwora

import android.content.Context
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
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.NativeAdbProtocol
import com.arwora.utils.WirelessPairingManager
import com.arwora.utils.WirelessPairingMode
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import kotlin.io.path.createTempDirectory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NativeAdbManagerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
}
