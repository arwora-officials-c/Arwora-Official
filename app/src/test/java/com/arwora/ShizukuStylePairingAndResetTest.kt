package com.arwora

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.arwora.ui.components.AdbPairingSheetContent
import com.arwora.ui.components.AdbPairingSheetTab
import com.arwora.ui.screens.ShizukuDebuggingTogglesCard
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.EncryptedAdbCredentialStore
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.NativeAdbProtocol
import com.arwora.utils.NativeShellResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w380dp-h800dp")
class ShizukuStylePairingAndResetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AdbShellExecutor.resetTestOverrides()
        EncryptedAdbCredentialStore.resetCacheForTesting()
        EncryptedAdbCredentialStore.clearSession(context)
    }

    @After
    fun tearDown() {
        AdbShellExecutor.resetTestOverrides()
        EncryptedAdbCredentialStore.clearSession(context)
        EncryptedAdbCredentialStore.resetCacheForTesting()
    }

    @Test
    fun shizukuDebuggingTogglesCardRendersAllThreeControlsAndTriggersCallbacks() {
        var devOptionsTapped = false
        var usbToggled = false
        var wirelessToggled = false
        var pairOpened = false
        var newDeviceReset = false

        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                ShizukuDebuggingTogglesCard(
                    isUsbDebuggingEnabled = true,
                    isWirelessDebuggingEnabled = false,
                    isSessionForgotten = false,
                    onOpenDeveloperOptions = { devOptionsTapped = true },
                    onToggleUsbDebugging = { usbToggled = true },
                    onToggleWirelessDebugging = { wirelessToggled = true },
                    onOpenPairDialog = { pairOpened = true },
                    onResetAndPairNewDevice = { newDeviceReset = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("shizuku_debugging_toggles_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_pill_developer_options").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("toggle_pill_usb_debugging").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("toggle_pill_wireless_debugging").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("open_adb_pairing_sheet_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("quick_controls_pair_new_device_button").assertIsDisplayed().performClick()

        assertTrue(devOptionsTapped)
        assertTrue(usbToggled)
        assertTrue(wirelessToggled)
        assertTrue(pairOpened)
        assertTrue(newDeviceReset)
    }

    @Test
    fun adbPairingSheetSwitchesBetweenAutomaticAndManualModesWithExactInstructions() {
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = false) {
                AdbPairingSheetContent(
                    initialTab = AdbPairingSheetTab.AUTOMATIC,
                    onDismissRequest = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Pair with device").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adb_pairing_sheet_description").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Pair without typing a code:",
            substring = true
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("adb_pairing_automatic_panel").assertIsDisplayed()

        // Switch to Manual tab
        composeTestRule.onNodeWithTag("adb_pairing_tab_manual").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("adb_pairing_manual_panel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adb_pairing_sheet_host_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adb_pairing_sheet_code_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adb_pairing_sheet_port_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("adb_pairing_manual_submit_button").assertIsDisplayed()
    }

    @Test
    fun forgetPairedDeviceRegeneratesRsaKeysAndClearsStalePairingWhileKeepingTrustedWifi() {
        val keyDir = File(context.filesDir, "adb_keys")
        val initialKeyPair = NativeAdbProtocol.loadOrCreateKeyPair(keyDir)
        EncryptedAdbCredentialStore.markWifiNetworkTrusted(context, "ssid:MAHFUZ-5G", "MAHFUZ-5G")
        EncryptedAdbCredentialStore.savePairedSession(
            context = context,
            host = "192.168.1.44",
            connectPort = 41235,
            pairingPort = 39812,
            rsaFingerprint = EncryptedAdbCredentialStore.computeKeyFingerprint(initialKeyPair)
        )
        assertTrue(EncryptedAdbCredentialStore.loadState(context).isPaired)

        val newKeyPair = NativeAdbManager.forgetPairedDeviceAndReset(context)

        assertFalse(initialKeyPair.public.encoded.contentEquals(newKeyPair.public.encoded))
        val afterCreds = EncryptedAdbCredentialStore.loadState(context)
        assertFalse(afterCreds.isPaired)
        assertEquals(-1, afterCreds.connectPort)
        assertEquals(-1, afterCreds.pairingPort)
        assertEquals("127.0.0.1", afterCreds.host)
        assertTrue(afterCreds.trustedWifiNetworks.contains("ssid:MAHFUZ-5G"))
        assertFalse(NativeAdbManager.isConnected.value)
        assertFalse(NativeAdbManager.isPaired.value)
        assertFalse(NativeAdbManager.isSessionForgotten.value)
    }

    @Test
    fun usbAndWirelessDebuggingTogglesExecuteSettingsPutWhenPrivilegedEngineActive() = runTest {
        val executedCommands = mutableListOf<String>()
        AdbShellExecutor.shizukuBinderProbeOverride = { true }
        AdbShellExecutor.shizukuPermissionProbeOverride = { true }
        AdbShellExecutor.shizukuCommandRunnerOverride = { cmd, _ ->
            executedCommands.add(cmd)
            NativeShellResult(success = true, output = "", exitCode = 0)
        }

        Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
        Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 0)

        val (usbOk, _) = NativeAdbManager.toggleUsbDebugging(context, targetEnabled = true)
        val (wifiOk, _) = NativeAdbManager.toggleWirelessDebugging(context, targetEnabled = true)

        assertTrue(usbOk)
        assertTrue(wifiOk)
        assertTrue(executedCommands.contains("settings put global adb_enabled 1"))
        assertTrue(executedCommands.contains("settings put global adb_wifi_enabled 1"))
    }
}
