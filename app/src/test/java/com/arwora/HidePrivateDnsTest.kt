package com.arwora

import com.arwora.feature.system.dns.hide.AppCategory
import com.arwora.feature.system.dns.hide.HideDnsAppItem
import com.arwora.feature.system.dns.hide.HideDnsTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidePrivateDnsTest {

    @Test
    fun testAppItemCategory() {
        val userApp = HideDnsAppItem(
            packageName = "com.cloudflare.onedotonedotonedotone",
            appName = "1.1.1.1",
            isSystemApp = false,
            isHidden = false
        )
        assertEquals(AppCategory.USER, userApp.category)
        assertFalse(userApp.isSystemApp)
        assertFalse(userApp.isHidden)

        val sysApp = HideDnsAppItem(
            packageName = "com.android.settings",
            appName = "Settings",
            isSystemApp = true,
            isHidden = true
        )
        assertEquals(AppCategory.SYSTEM, sysApp.category)
        assertTrue(sysApp.isSystemApp)
        assertTrue(sysApp.isHidden)
    }

    @Test
    fun testTabEnum() {
        assertEquals(HideDnsTab.VISIBLE, HideDnsTab.valueOf("VISIBLE"))
        assertEquals(HideDnsTab.HIDDEN, HideDnsTab.valueOf("HIDDEN"))
    }

    @Test
    fun testAppFilteringLogic() {
        val app1 = HideDnsAppItem("com.example.game", "Action Game", isSystemApp = false, isHidden = false)
        val app2 = HideDnsAppItem("com.example.bank", "Banking App", isSystemApp = false, isHidden = true)
        val app3 = HideDnsAppItem("com.android.phone", "Phone", isSystemApp = true, isHidden = false)

        val allApps = listOf(app1, app2, app3)
        val hiddenSet = setOf("com.example.bank")

        val visibleApps = allApps.filter { !hiddenSet.contains(it.packageName) }
        val hiddenApps = allApps.filter { hiddenSet.contains(it.packageName) }

        assertEquals(2, visibleApps.size)
        assertEquals(1, hiddenApps.size)
        assertEquals("Banking App", hiddenApps[0].appName)

        // Query test
        val query = "game"
        val queryFiltered = visibleApps.filter { it.appName.lowercase().contains(query) }
        assertEquals(1, queryFiltered.size)
        assertEquals("Action Game", queryFiltered[0].appName)
    }
}
