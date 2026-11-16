package com.arwora

import com.arwora.feature.system.wifi.WifiSectionTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WifiControllerTabsTest {

    @Test
    fun wifiControllerScreenTabsExcludeAlertsAndPreserveCoreTabs() {
        val screenTabs = WifiSectionTab.entries.filter {
            it != WifiSectionTab.ALL && it != WifiSectionTab.OVERVIEW
        }

        assertEquals(
            listOf("Devices", "Speed Test", "History", "Signal & Channel", "Scanner"),
            screenTabs.map { it.label }
        )
        assertFalse(screenTabs.any { it.label.equals("Alerts", ignoreCase = true) })
        assertFalse(WifiSectionTab.entries.any { it.name == "ALERTS" })
    }
}
