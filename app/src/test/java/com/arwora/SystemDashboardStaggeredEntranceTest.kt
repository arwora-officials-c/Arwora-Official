package com.arwora

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.arwora.ui.screens.AnimatedListItemProgressKey
import com.arwora.ui.screens.SystemDashboardScreen
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.AdbShellExecutor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp")
class SystemDashboardStaggeredEntranceTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        AdbShellExecutor.resetTestOverrides()
    }

    @After
    fun tearDown() {
        AdbShellExecutor.resetTestOverrides()
    }

    private fun readProgress(tag: String): Float {
        return composeTestRule
            .onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[AnimatedListItemProgressKey]
    }

    @Test
    fun staggeredEntranceWaitsForFirstVisibilityCascadesAcrossCardsAndDoesNotReplay() {
        val app = ApplicationProvider.getApplicationContext<NameGenApplication>()
        val isVisibleState = mutableStateOf(false)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            val navController = rememberNavController()
            NameGenProTheme(darkTheme = true) {
                SystemDashboardScreen(
                    navController = navController,
                    appManagerViewModel = app.appManagerViewModel,
                    isVisible = isVisibleState.value
                )
            }
        }

        // Advance time while pre-composed offscreen: cards must hold at 0f until first load
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.waitForIdle()

        assertEquals(0f, readProgress("dashboard_staggered_item_2"), 0.001f)
        assertEquals(0f, readProgress("dashboard_staggered_item_3"), 0.001f)
        assertEquals(0f, readProgress("dashboard_staggered_item_4"), 0.001f)

        // First load activation: switch to System Dashboard tab
        isVisibleState.value = true
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(160L)

        val pHero = readProgress("dashboard_staggered_item_2")
        val pBridge = readProgress("dashboard_staggered_item_3")
        val pToggles = readProgress("dashboard_staggered_item_4")

        assertTrue("Hero card (index 2) must already be animating at 150ms (was $pHero)", pHero > 0f)
        assertTrue(
            "Staggered order: index 2 ($pHero) should be strictly ahead of index 4 ($pToggles)",
            pHero > pToggles
        )
        assertTrue(
            "Staggered order: index 2 ($pHero) >= index 3 ($pBridge) >= index 4 ($pToggles)",
            pHero >= pBridge && pBridge >= pToggles
        )

        // Settle initial burst
        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()

        assertEquals(1f, readProgress("dashboard_staggered_item_2"), 0.001f)
        assertEquals(1f, readProgress("dashboard_staggered_item_3"), 0.001f)
        assertEquals(1f, readProgress("dashboard_staggered_item_4"), 0.001f)

        // Switch away and back: cards must stay at 1f and not replay entrance animation
        isVisibleState.value = false
        composeTestRule.mainClock.advanceTimeByFrame()
        isVisibleState.value = true
        composeTestRule.mainClock.advanceTimeByFrame()

        assertEquals(1f, readProgress("dashboard_staggered_item_2"), 0.001f)
        assertEquals(1f, readProgress("dashboard_staggered_item_3"), 0.001f)
        assertEquals(1f, readProgress("dashboard_staggered_item_4"), 0.001f)
    }

    @Test
    fun allDashboardCardsAcrossLazyColumnAreWrappedAndCompleteEntrance() {
        val app = ApplicationProvider.getApplicationContext<NameGenApplication>()
        composeTestRule.setContent {
            val navController = rememberNavController()
            NameGenProTheme(darkTheme = true) {
                SystemDashboardScreen(
                    navController = navController,
                    appManagerViewModel = app.appManagerViewModel,
                    isVisible = true
                )
            }
        }
        composeTestRule.waitForIdle()

        val list = composeTestRule.onNodeWithTag("system_dashboard_lazy_column")

        val expectedCardCheckpoints = listOf(
            "Centralized Control Panel" to "dashboard_staggered_item_2",
            "Privilege Engine" to "dashboard_staggered_item_3",
            "Debugging Quick Controls" to "dashboard_staggered_item_4",
            "Device Model" to "dashboard_staggered_item_7",
            "DNS Changer" to "dashboard_staggered_item_9",
            "App Locker" to "dashboard_staggered_item_10",
            "Permission Inspector" to "dashboard_staggered_item_20",
            "Animation Speed" to "dashboard_staggered_item_21",
            "App Ops" to "dashboard_staggered_item_23",
            "Html To Apk" to "dashboard_staggered_item_26",
            "Arwora Connect" to "dashboard_staggered_item_29"
        )

        expectedCardCheckpoints.forEach { (title, wrapperTag) ->
            list.performScrollToNode(hasText(title))
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(wrapperTag, useUnmergedTree = true).assertIsDisplayed()
            assertEquals(1f, readProgress(wrapperTag), 0.001f)
        }
    }
}
