package com.arwora

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.arwora.feature.system.wifi.password.SavedWifiNetwork
import com.arwora.feature.system.wifi.password.WifiPasswordRepository
import com.arwora.feature.system.wifi.password.WifiSecurityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WifiPasswordParserTest {

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
}
