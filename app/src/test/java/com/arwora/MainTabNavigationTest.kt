package com.arwora

import com.arwora.ui.components.bottomNavItems
import com.arwora.ui.components.computeHopPageBeforeAnimate
import com.arwora.ui.components.indexOfMainTabRoute
import com.arwora.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainTabNavigationTest {

    @Test
    fun bottomNavItemsOrderMatchesFiveMainTabs() {
        assertEquals(5, bottomNavItems.size)
        assertEquals(Screen.Home.route, bottomNavItems[0].route)
        assertEquals(Screen.GenerateDashboard.route, bottomNavItems[1].route)
        assertEquals(Screen.Social.route, bottomNavItems[2].route)
        assertEquals(Screen.SystemDashboard.route, bottomNavItems[3].route)
        assertEquals(Screen.Settings.route, bottomNavItems[4].route)
    }

    @Test
    fun indexOfMainTabRouteResolvesTabsAndRejectsSubScreens() {
        assertEquals(0, indexOfMainTabRoute(Screen.Home.route))
        assertEquals(1, indexOfMainTabRoute(Screen.GenerateDashboard.route))
        assertEquals(2, indexOfMainTabRoute(Screen.Social.route))
        assertEquals(3, indexOfMainTabRoute(Screen.SystemDashboard.route))
        assertEquals(4, indexOfMainTabRoute(Screen.Settings.route))

        assertEquals(-1, indexOfMainTabRoute(Screen.AppOps.route))
        assertEquals(-1, indexOfMainTabRoute(Screen.About.route))
        assertEquals(-1, indexOfMainTabRoute(Screen.MainTabs.route))
        assertEquals(-1, indexOfMainTabRoute(null))
    }

    @Test
    fun computeHopPageBeforeAnimateOptimizesMultiTabJumps() {
        // Adjacent tabs animate directly without pre-hop
        assertNull(computeHopPageBeforeAnimate(currentPage = 0, targetPage = 1))
        assertNull(computeHopPageBeforeAnimate(currentPage = 3, targetPage = 2))
        assertNull(computeHopPageBeforeAnimate(currentPage = 2, targetPage = 2))

        // Forward distant jump pre-positions 1 page before target
        assertEquals(3, computeHopPageBeforeAnimate(currentPage = 0, targetPage = 4))
        assertEquals(2, computeHopPageBeforeAnimate(currentPage = 0, targetPage = 3))

        // Backward distant jump pre-positions 1 page after target
        assertEquals(1, computeHopPageBeforeAnimate(currentPage = 4, targetPage = 0))
        assertEquals(2, computeHopPageBeforeAnimate(currentPage = 4, targetPage = 1))
    }
}
