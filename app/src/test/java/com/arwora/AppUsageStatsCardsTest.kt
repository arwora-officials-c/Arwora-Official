package com.arwora

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.arwora.ui.screens.CompactWellbeingDashboardCard
import com.arwora.ui.screens.UsageMetric
import com.arwora.ui.screens.UsageTimeframe
import com.arwora.ui.screens.WellbeingActivityGraphCard
import com.arwora.ui.screens.WellbeingSummary
import com.arwora.ui.theme.NameGenProTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUsageStatsCardsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `dashboard card excludes removed telemetry rows and preserves remaining stats cards`() {
        val summary = WellbeingSummary(
            totalScreenTimeMs = 3_600_000L,
            yesterdayScreenTimeMs = 4_200_000L,
            unlockCount = 18,
            notificationCount = 42,
            timesOpenedTotalCount = 65
        )

        composeTestRule.setContent {
            NameGenProTheme {
                CompactWellbeingDashboardCard(
                    summary = summary,
                    selectedTimeframe = UsageTimeframe.TODAY,
                    onTimeframeSelected = {},
                    hasPermission = true,
                    isLoading = false,
                    showExtendedTelemetry = true
                )
            }
        }

        // Remaining cards must remain present and visible
        composeTestRule.onNodeWithTag("compact_metric_data_usage").assertIsDisplayed()
        composeTestRule.onNodeWithText("Data Usage Today").assertIsDisplayed()
        composeTestRule.onNodeWithTag("compact_metric_battery_thermal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Battery Thermal").assertIsDisplayed()
        composeTestRule.onNodeWithTag("compact_metric_battery_drain").assertIsDisplayed()
        composeTestRule.onNodeWithText("Battery Drain Rate").assertIsDisplayed()
        composeTestRule.onNodeWithTag("compact_metric_ram_breakdown").assertIsDisplayed()
        composeTestRule.onNodeWithText("RAM Breakdown").assertIsDisplayed()

        // Removed 4 stats cards/rows must not exist
        composeTestRule.onNodeWithTag("compact_metric_steps_today").assertDoesNotExist()
        composeTestRule.onNodeWithText("Steps Today").assertDoesNotExist()
        composeTestRule.onNodeWithTag("compact_metric_notification").assertDoesNotExist()
        composeTestRule.onNodeWithText("received alerts").assertDoesNotExist()
        composeTestRule.onNodeWithTag("compact_metric_app_limits").assertDoesNotExist()
        composeTestRule.onNodeWithText("monitored").assertDoesNotExist()
        composeTestRule.onNodeWithTag("compact_metric_device_pickups").assertDoesNotExist()
        composeTestRule.onNodeWithText("Device Pickups").assertDoesNotExist()
    }

    @Test
    fun `app activity details graph card remains intact`() {
        val summary = WellbeingSummary(
            totalScreenTimeMs = 3_600_000L,
            notificationCount = 12,
            timesOpenedTotalCount = 24
        )

        composeTestRule.setContent {
            NameGenProTheme {
                WellbeingActivityGraphCard(
                    summary = summary,
                    selectedMetric = UsageMetric.SCREEN_TIME,
                    onMetricSelected = {}
                )
            }
        }

        composeTestRule.onNodeWithText("App Activity Details").assertIsDisplayed()
    }
}
