package com.arwora

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.arwora.feature.system.wifi.password.SavedWifiNetwork
import com.arwora.feature.system.wifi.password.WifiPasswordRepository
import com.arwora.feature.system.wifi.password.WifiPasswordScreen
import com.arwora.feature.system.wifi.password.WifiPasswordViewModel
import com.arwora.feature.system.wifi.password.WifiSecurityType
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.NativeShellResult
import com.arwora.utils.PrivilegeEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h850dp")
class WifiPasswordParserTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        AdbShellExecutor.resetTestOverrides()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("wifi_password_vault", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        AdbShellExecutor.resetTestOverrides()
    }

    @Test
    fun parseWifiConfigStoreXmlExtractsNetworkAndWifiConfigurationTags() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = WifiPasswordRepository(context)
        val xml = """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <WifiConfigStoreData>
                <NetworkList>
                    <Network>
                        <WifiConfiguration>
                            <string name="ConfigKey">&quot;HomeFiber_5G&quot;WPA_PSK</string>
                            <string name="SSID">&quot;HomeFiber_5G&quot;</string>
                            <string name="PreSharedKey">&quot;Sup3rS3cret!&amp;2026&quot;</string>
                            <boolean name="HiddenSSID" value="false" />
                        </WifiConfiguration>
                    </Network>
                    <Network>
                        <WiFiConfiguration>
                            <string name="ConfigKey">&quot;Studio6E&quot;SAE</string>
                            <string name="SSID">&quot;Studio6E&quot;</string>
                            <string name="PreSharedKey">&quot;Wpa3PassPhrase&quot;</string>
                            <boolean name="HiddenSSID" value="true" />
                        </WiFiConfiguration>
                    </Network>
                    <Network>
                        <WifiConfiguration>
                            <string name="ConfigKey">&quot;AirportFreeWifi&quot;NONE</string>
                            <string name="SSID">&quot;AirportFreeWifi&quot;</string>
                            <null name="PreSharedKey" />
                        </WifiConfiguration>
                    </Network>
                </NetworkList>
            </WifiConfigStoreData>
        """.trimIndent()

        val results = mutableMapOf<String, SavedWifiNetwork>()
        val count = repo.parseWifiConfigStoreXml(xml, results, currentSsid = "HomeFiber_5G")

        assertEquals(3, count)
        val home = results["HomeFiber_5G"]
        assertNotNull(home)
        assertEquals("Sup3rS3cret!&2026", home!!.password)
        assertEquals(WifiSecurityType.WPA2, home.securityType)
        assertTrue(home.isCurrentlyConnected)

        val studio = results["Studio6E"]
        assertNotNull(studio)
        assertEquals("Wpa3PassPhrase", studio!!.password)
        assertEquals(WifiSecurityType.WPA3, studio.securityType)
        assertTrue(studio.isHiddenSsid)

        val open = results["AirportFreeWifi"]
        assertNotNull(open)
        assertEquals("", open!!.password)
        assertEquals(WifiSecurityType.OPEN, open.securityType)
    }

    @Test
    fun parseCmdWifiListNetworksAndDumpsysWifiExtractConfiguredNetworksWithoutOverwritingPasswords() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = WifiPasswordRepository(context)
        val results = mutableMapOf<String, SavedWifiNetwork>()

        val cmdOutput = """
            Network Id      SSID                         Security type
            0               OfficeMesh                   wpa2-psk
            1               CoffeeShop                   open
            2               Lab_WiFi6                    sae
        """.trimIndent()
        val listCount = repo.parseCmdWifiListNetworks(cmdOutput, results, currentSsid = "OfficeMesh")
        assertEquals(3, listCount)
        assertEquals(0, results["OfficeMesh"]?.networkId)
        assertEquals(WifiSecurityType.WPA2, results["OfficeMesh"]?.securityType)
        assertTrue(results["OfficeMesh"]?.isCurrentlyConnected == true)
        assertEquals(WifiSecurityType.OPEN, results["CoffeeShop"]?.securityType)
        assertEquals(WifiSecurityType.WPA3, results["Lab_WiFi6"]?.securityType)

        val dumpsysOutput = """
            WifiConfigManager - Configured networks Begin ----
            ID: 0 SSID: "OfficeMesh" PROVIDER-NAME: null BSSID: null FQDN: null PRIO: 0
            KeyMgmt: WPA_PSK Protocols: WPA RSN
            PreSharedKey: "OfficeSecret99"
            ID: 2 SSID: "Lab_WiFi6" PROVIDER-NAME: null
            KeyMgmt: SAE Protocols: RSN
            PreSharedKey: "*"
            WifiConfigManager - Configured networks End ----
        """.trimIndent()
        val dumpCount = repo.parseDumpsysWifi(dumpsysOutput, results, currentSsid = "OfficeMesh")
        assertEquals(2, dumpCount)
        assertEquals("OfficeSecret99", results["OfficeMesh"]?.password)
        assertEquals("", results["Lab_WiFi6"]?.password)
    }

    @Test
    fun parseWpaSupplicantConfExtractsLegacySavedPasswords() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = WifiPasswordRepository(context)
        val conf = """
            network={
                ssid="LegacyRouter"
                psk="oldschool123"
                key_mgmt=WPA-PSK
                scan_ssid=1
            }
        """.trimIndent()

        val results = mutableMapOf<String, SavedWifiNetwork>()
        val count = repo.parseWpaSupplicantConf(conf, results, currentSsid = null)
        assertEquals(1, count)
        assertEquals("oldschool123", results["LegacyRouter"]?.password)
        assertEquals(WifiSecurityType.WPA2, results["LegacyRouter"]?.securityType)
        assertTrue(results["LegacyRouter"]?.isHiddenSsid == true)
    }

    @Test
    fun refreshSavedNetworksExtractsPlainTextPskViaShizukuWithoutSuOrRoot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val commandsRun = mutableListOf<String>()

        AdbShellExecutor.shizukuBinderProbeOverride = { true }
        AdbShellExecutor.shizukuPermissionProbeOverride = { true }
        AdbShellExecutor.shizukuCommandRunnerOverride = { command, _ ->
            commandsRun.add(command)
            when {
                command.contains("/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml") -> NativeShellResult(
                    success = true,
                    output = """
                        <WifiConfigStoreData>
                            <NetworkList>
                                <Network>
                                    <WifiConfiguration>
                                        <string name="SSID">&quot;HomeFiber_5G&quot;</string>
                                        <string name="PreSharedKey">&quot;RootlessPsk2026!&quot;</string>
                                    </WifiConfiguration>
                                    <SecurityParamsList>
                                        <SecurityParams>
                                            <int name="SecurityType" value="4" />
                                        </SecurityParams>
                                    </SecurityParamsList>
                                </Network>
                            </NetworkList>
                        </WifiConfigStoreData>
                    """.trimIndent(),
                    exitCode = 0
                )
                else -> NativeShellResult(success = true, output = "", exitCode = 0)
            }
        }

        val repo = WifiPasswordRepository(context)
        repo.refreshSavedNetworks()

        assertTrue(repo.isElevatedModeAvailable.value)
        assertEquals(PrivilegeEngine.SHIZUKU, repo.activeEngine.value)
        assertFalse("su must not be invoked when rootless shell read succeeds", commandsRun.any { it.contains("su -c") })

        val extracted = repo.networks.value.firstOrNull { it.ssid == "HomeFiber_5G" }
        assertNotNull(extracted)
        assertEquals("RootlessPsk2026!", extracted!!.password)
        assertEquals(WifiSecurityType.WPA3, extracted.securityType)
    }

    @Test
    fun refreshSavedNetworksExtractsPlainTextPskViaAdbShellWithoutSu() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val commandsRun = mutableListOf<String>()

        AdbShellExecutor.shizukuBinderProbeOverride = { false }
        AdbShellExecutor.shizukuPermissionProbeOverride = { false }
        AdbShellExecutor.adbReadyProbeOverride = { true }
        AdbShellExecutor.adbCommandRunnerOverride = { command, _ ->
            commandsRun.add(command)
            when {
                command.contains("/data/misc/wifi/WifiConfigStore.xml") -> NativeShellResult(
                    success = true,
                    output = """
                        <WifiConfigStoreData>
                            <NetworkList>
                                <Network>
                                    <WifiConfiguration>
                                        <string name="ConfigKey">&quot;StudioNet&quot;WPA_PSK</string>
                                        <string name="SSID">&quot;StudioNet&quot;</string>
                                        <string name="PreSharedKey">&quot;AdbExtracted99&quot;</string>
                                    </WifiConfiguration>
                                </Network>
                            </NetworkList>
                        </WifiConfigStoreData>
                    """.trimIndent(),
                    exitCode = 0
                )
                else -> NativeShellResult(success = true, output = "", exitCode = 0)
            }
        }

        val repo = WifiPasswordRepository(context)
        repo.refreshSavedNetworks()

        assertTrue(repo.isElevatedModeAvailable.value)
        assertEquals(PrivilegeEngine.WIRELESS_ADB, repo.activeEngine.value)
        assertFalse(commandsRun.any { it.contains("su -c") })

        val extracted = repo.networks.value.firstOrNull { it.ssid == "StudioNet" }
        assertNotNull(extracted)
        assertEquals("AdbExtracted99", extracted!!.password)
    }

    @Test
    fun wifiPasswordScreenOfflinePromptsConnectAdbOrShizuku() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AdbShellExecutor.shizukuBinderProbeOverride = { false }
        AdbShellExecutor.shizukuPermissionProbeOverride = { false }
        AdbShellExecutor.adbReadyProbeOverride = { false }

        val vm = WifiPasswordViewModel(app)

        composeRule.setContent {
            NameGenProTheme(darkTheme = true) {
                WifiPasswordScreen(onBack = {}, viewModel = vm)
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("adb_warning_banner").assertIsDisplayed()
        composeRule.onNodeWithText("Connect ADB or Shizuku to view saved Wi-Fi passwords").assertIsDisplayed()
        composeRule.onNodeWithTag("wifi_connect_shizuku_button").assertIsDisplayed()
        composeRule.onNodeWithTag("connect_adb_button").assertIsDisplayed()
    }

    @Test
    fun wifiPasswordScreenDisplaysExtractedPlainTextPskAndTogglesMaskAllUnmaskAll() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AdbShellExecutor.shizukuBinderProbeOverride = { true }
        AdbShellExecutor.shizukuPermissionProbeOverride = { true }
        AdbShellExecutor.shizukuCommandRunnerOverride = { command, _ ->
            if (command.contains("WifiConfigStore.xml")) {
                NativeShellResult(
                    success = true,
                    output = """
                        <WifiConfigStoreData>
                            <NetworkList>
                                <Network>
                                    <WifiConfiguration>
                                        <string name="ConfigKey">&quot;HomeFiber_5G&quot;WPA_PSK</string>
                                        <string name="SSID">&quot;HomeFiber_5G&quot;</string>
                                        <string name="PreSharedKey">&quot;Sup3rS3cret!&quot;</string>
                                    </WifiConfiguration>
                                </Network>
                            </NetworkList>
                        </WifiConfigStoreData>
                    """.trimIndent(),
                    exitCode = 0
                )
            } else {
                NativeShellResult(success = true, output = "", exitCode = 0)
            }
        }

        val vm = WifiPasswordViewModel(app)

        composeRule.setContent {
            NameGenProTheme(darkTheme = true) {
                WifiPasswordScreen(onBack = {}, viewModel = vm)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Sup3rS3cret!").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("wifi_loading_progress_card").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText("Shizuku Connected").assertIsDisplayed()
        composeRule.onNodeWithText("Sup3rS3cret!").assertIsDisplayed()
        composeRule.onNodeWithTag("copy_password_button_HomeFiber_5G").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Protected by system keystore (Root needed for PSK)").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("adb_warning_banner").fetchSemanticsNodes().size)

        // Mask All -> hides plain text behind •••••••• and flips button to Unmask All
        composeRule.onNodeWithTag("toggle_reveal_all_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("••••••••").assertIsDisplayed()
        composeRule.onNodeWithText("Unmask All").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("Sup3rS3cret!").fetchSemanticsNodes().size)

        // Unmask All -> restores plain text and flips button back to Mask All
        composeRule.onNodeWithTag("toggle_reveal_all_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Sup3rS3cret!").assertIsDisplayed()
        composeRule.onNodeWithText("Mask All").assertIsDisplayed()
    }
}

