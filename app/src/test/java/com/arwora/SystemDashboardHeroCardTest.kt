package com.arwora

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.arwora.ui.screens.CentralizedControlPanelHeroCard
import com.arwora.ui.theme.NameGenProTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemDashboardHeroCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `centralized control panel card renders without status badges`() {
        composeTestRule.setContent {
            NameGenProTheme {
                CentralizedControlPanelHeroCard()
            }
        }

        composeTestRule.onNodeWithTag("centralized_control_panel_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Centralized Control Panel").assertIsDisplayed()

        // Verify all three status badges and their tags are completely removed
        composeTestRule.onNodeWithTag("hero_system_status_chip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hero_notification_status_chip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("hero_shizuku_status_chip").assertDoesNotExist()
        composeTestRule.onNodeWithText("System Status: Online").assertDoesNotExist()
        composeTestRule.onNodeWithText("Notifications: Granted").assertDoesNotExist()
        composeTestRule.onNodeWithText("Notifications: Off").assertDoesNotExist()
        composeTestRule.onNodeWithText("Shizuku: Connected").assertDoesNotExist()
    }
}
