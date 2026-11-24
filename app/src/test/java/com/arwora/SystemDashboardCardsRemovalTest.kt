package com.arwora

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.arwora.ui.screens.SystemDashboardScreen
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.AdbShellExecutor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w380dp-h800dp")
class SystemDashboardCardsRemovalTest {

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

    @Test
    fun systemDashboardExcludesWirelessDebuggingAndAdvancedAppManagerCardsWhileKeepingRemainingTools() {
        val app = ApplicationProvider.getApplicationContext<NameGenApplication>()
        composeTestRule.setContent {
            val navController = rememberNavController()
            NameGenProTheme(darkTheme = true) {
                SystemDashboardScreen(
                    navController = navController,
                    appManagerViewModel = app.appManagerViewModel
                )
            }
        }
        composeTestRule.waitForIdle()

        // Core header & debugging controls remain intact
        composeTestRule.onNodeWithText("System Dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shizuku_debugging_toggles_card").assertIsDisplayed()

        val list = composeTestRule.onNodeWithTag("system_dashboard_lazy_column")

        // Verify removed cards do not appear before or while scrolling
        assertEquals(0, composeTestRule.onAllNodesWithText("Advanced App Manager & APK Extractor").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("APK Extractor & Backup", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Status: Active / Connected", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Status: Inactive / Disconnected", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("IP Address Lookup").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Network Diagnostics Toolkit", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Link Shortener", substring = true).fetchSemanticsNodes().size)

        // Scroll through remaining System Dashboard tools and verify they remain intact
        list.performScrollToNode(hasText("Permission Inspector"))
        composeTestRule.onNodeWithText("Permission Inspector").assertIsDisplayed()

        list.performScrollToNode(hasText("Animation Speed"))
        composeTestRule.onNodeWithText("Animation Speed").assertIsDisplayed()

        list.performScrollToNode(hasText("App Ops"))
        composeTestRule.onNodeWithText("App Ops").assertIsDisplayed()

        list.performScrollToNode(hasText("Html To Apk"))
        composeTestRule.onNodeWithText("Html To Apk").assertIsDisplayed()

        list.performScrollToNode(hasText("Smart App Installation Assistant"))
        composeTestRule.onNodeWithText("Smart App Installation Assistant").assertIsDisplayed()

        list.performScrollToNode(hasText("Smart Download Workflow"))
        composeTestRule.onNodeWithText("Smart Download Workflow").assertIsDisplayed()

        list.performScrollToNode(hasText("Arwora Connect"))
        composeTestRule.onNodeWithText("Arwora Connect").assertIsDisplayed()

        assertEquals(0, composeTestRule.onAllNodesWithText("Advanced App Manager & APK Extractor").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("APK Extractor & Backup", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("IP Address Lookup").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Link Shortener", substring = true).fetchSemanticsNodes().size)
    }
}
