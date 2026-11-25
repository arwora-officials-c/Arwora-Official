package com.arwora

import android.content.Context
import android.os.BatteryManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.arwora.feature.system.battery.service.BatteryMonitorManager
import com.arwora.feature.system.battery.service.BatteryTelemetrySnapshot
import com.arwora.feature.system.optimizer.service.ScreenOffOptimizerManager
import com.arwora.ui.screens.SystemDashboardScreen
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.AppLockerManager
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp")
class SystemToolsPolishAndBatteryMonitorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AdbShellExecutor.resetTestOverrides()
        BatteryMonitorManager.resetTestOverrides()
        AppLockerManager.init(context)
        AppLockerManager.setLockerEnabled(context, false)
        ScreenOffOptimizerManager.syncServiceState(false)
    }

    @After
    fun tearDown() {
        AdbShellExecutor.resetTestOverrides()
        BatteryMonitorManager.resetTestOverrides()
        AppLockerManager.setLockerEnabled(context, false)
        ScreenOffOptimizerManager.syncServiceState(false)
    }

    @Test
    fun batteryMonitorManagerRetainsCoreTelemetryForBatteryChargingAlarm() {
        assertEquals("Good", BatteryMonitorManager.parseBatteryHealth(BatteryManager.BATTERY_HEALTH_GOOD))
        assertEquals("Overheat", BatteryMonitorManager.parseBatteryHealth(BatteryManager.BATTERY_HEALTH_OVERHEAT))
        assertEquals("Cold", BatteryMonitorManager.parseBatteryHealth(BatteryManager.BATTERY_HEALTH_COLD))
        assertEquals("Over Voltage", BatteryMonitorManager.parseBatteryHealth(BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE))
        assertEquals("Unknown", BatteryMonitorManager.parseBatteryHealth(BatteryManager.BATTERY_HEALTH_UNKNOWN))
        assertEquals("AC", BatteryMonitorManager.parsePlugType(BatteryManager.BATTERY_PLUGGED_AC))
        assertEquals("USB", BatteryMonitorManager.parsePlugType(BatteryManager.BATTERY_PLUGGED_USB))
        assertEquals("Battery", BatteryMonitorManager.parsePlugType(0))

        BatteryMonitorManager.batteryTelemetryOverride = {
            BatteryTelemetrySnapshot(
                level = 76,
                isCharging = false,
                temperatureCelsius = 33.4f,
                voltageMv = 4080,
                healthLabel = "Good",
                plugTypeLabel = "Battery"
            )
        }

        BatteryMonitorManager.refreshStats(context)

        assertEquals(76, BatteryMonitorManager.batteryLevel.value)
        assertFalse(BatteryMonitorManager.isCharging.value)
        assertEquals(33.4f, BatteryMonitorManager.batteryTemperature.value, 0.01f)
        assertEquals(4080, BatteryMonitorManager.batteryVoltage.value)
        assertEquals("Good", BatteryMonitorManager.batteryHealth.value)
        assertEquals("Battery", BatteryMonitorManager.batteryPlugType.value)
    }

    @Test
    fun systemDashboardExcludesBatteryMonitorDeepSleepCardWhileKeepingPolishedSystemTools() {
        val app = ApplicationProvider.getApplicationContext<NameGenApplication>()
        AppLockerManager.setPin(context, "1234")

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

        // Ensure Battery Monitor & Deep Sleep is not rendered initially
        assertEquals(0, composeTestRule.onAllNodesWithTag("system_battery_monitor_card").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Battery Monitor & Deep Sleep").fetchSemanticsNodes().size)

        // 1. App Locker card polish + compact layout (no Configure sub-row) + toggle
        list.performScrollToNode(hasTestTag("system_app_locker_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("App Locker").assertIsDisplayed()
        composeTestRule.onNodeWithText("Secured apps monitored", substring = true).assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("app_locker_configure_pill", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Set PIN & select apps or tools to guard", useUnmergedTree = true).fetchSemanticsNodes().size)
        composeTestRule.onNodeWithTag("app_locker_dashboard_switch").performClick()
        composeTestRule.waitForIdle()
        assertTrue(AppLockerManager.isLockerEnabled.value)

        // 2. Smart Screen-Off Optimizer card polish + interactive switch
        list.performScrollToNode(hasTestTag("system_screen_off_optimizer_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Smart Screen-Off Optimizer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screen_off_optimizer_dashboard_switch").performClick()
        composeTestRule.waitForIdle()
        assertTrue(ScreenOffOptimizerManager.isServiceActive.value)

        // 3. Notification History Log follows Smart Screen-Off directly without Battery Monitor card
        list.performScrollToNode(hasTestTag("system_notification_history_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Notification History Log").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("system_battery_monitor_card").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Battery Monitor & Deep Sleep").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("CPU Deep Sleep Tracker").fetchSemanticsNodes().size)

        // 4. Battery Charging Alarm card compact layout (no inline limit chips) + status & toggle
        list.performScrollToNode(hasTestTag("system_battery_alarm_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Battery Charging Alarm").assertIsDisplayed()
        composeTestRule.onNodeWithTag("battery_alarm_threshold_summary", useUnmergedTree = true)
            .assertTextContains("Target: 80% high", substring = true)
        composeTestRule.onNodeWithTag("battery_alarm_status_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("battery_alarm_dashboard_switch", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("battery_alarm_chip_80", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("battery_alarm_chip_90", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithTag("battery_alarm_chip_100", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Limit", useUnmergedTree = true).fetchSemanticsNodes().size)

        // 5. Background & Wakelock Detector card polish remains intact
        list.performScrollToNode(hasTestTag("system_wakelock_detector_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Background & Wakelock Detector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wakelock_status_badge", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("system_battery_monitor_card").fetchSemanticsNodes().size)
    }
}
