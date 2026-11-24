package com.arwora

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.arwora.feature.system.battery.model.CpuState
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
    fun batteryMonitorManagerComputesDeepSleepHealthAndCurrentRateAccurately() {
        assertEquals("Good", BatteryMonitorManager.parseBatteryHealth(BatteryManager.BATTERY_HEALTH_GOOD))
        assertEquals("Overheat", BatteryMonitorManager.parseBatteryHealth(BatteryManager.BATTERY_HEALTH_OVERHEAT))
        assertEquals("Cold", BatteryMonitorManager.parseBatteryHealth(BatteryManager.BATTERY_HEALTH_COLD))
        assertEquals("Over Voltage", BatteryMonitorManager.parseBatteryHealth(BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE))
        assertEquals("Unknown", BatteryMonitorManager.parseBatteryHealth(BatteryManager.BATTERY_HEALTH_UNKNOWN))

        // Negative discharge microamperes & positive microamperes normalize identically to mAh/h
        assertEquals(420, BatteryMonitorManager.normalizeCurrentMahPerHour(-420_000L))
        assertEquals(1850, BatteryMonitorManager.normalizeCurrentMahPerHour(1_850_000L))
        assertEquals(0, BatteryMonitorManager.normalizeCurrentMahPerHour(Int.MIN_VALUE.toLong()))
        assertEquals(0, BatteryMonitorManager.normalizeCurrentMahPerHour(0L))

        val diag = BatteryMonitorManager.computeSleepDiagnostics(
            elapsedMs = 10_000_000L,
            uptimeMs = 2_500_000L,
            isDozeActive = true,
            activeWakeLocksCount = 1,
            pendingAlarmsCount = 4
        )
        assertEquals(75, diag.deepSleepPercentage)
        assertEquals(7_500_000L, diag.deepSleepMs)
        assertEquals(2_500_000L, diag.awakeUptimeMs)
        assertEquals(CpuState.DEEP_SLEEP, diag.cpuState)
        assertTrue(diag.isDozeModeActive)

        assertEquals("2h 5m", BatteryMonitorManager.formatDurationCompact(7_500_000L))
    }

    @Test
    fun batteryMonitorManagerRefreshStatsReadsStickyIntentAndClockOverrides() {
        BatteryMonitorManager.batteryTelemetryOverride = {
            BatteryTelemetrySnapshot(
                level = 76,
                isCharging = false,
                temperatureCelsius = 33.4f,
                voltageMv = 4080,
                healthLabel = "Good",
                plugTypeLabel = "Battery",
                dischargeRateMahPerHour = 365,
                isCurrentAvailable = true
            )
        }
        BatteryMonitorManager.clockProviderOverride = {
            14_400_000L to 3_600_000L // 4h elapsed, 1h awake -> 3h deep sleep (75%)
        }

        BatteryMonitorManager.refreshStats(context, emitLog = false)

        assertEquals(76, BatteryMonitorManager.batteryLevel.value)
        assertFalse(BatteryMonitorManager.isCharging.value)
        assertEquals(33.4f, BatteryMonitorManager.batteryTemperature.value, 0.01f)
        assertEquals(4080, BatteryMonitorManager.batteryVoltage.value)
        assertEquals("Good", BatteryMonitorManager.batteryHealth.value)
        assertEquals(365, BatteryMonitorManager.dischargeRateMahPerHour.value)
        assertTrue(BatteryMonitorManager.isCurrentAvailable.value)
        assertEquals(75, BatteryMonitorManager.deepSleepDiagnostics.value.deepSleepPercentage)
        assertEquals(10_800_000L, BatteryMonitorManager.deepSleepDiagnostics.value.deepSleepMs)
    }

    @Test
    fun systemDashboardRendersPolishedCardsAndBatteryMonitorDeepSleepCard() {
        val app = ApplicationProvider.getApplicationContext<NameGenApplication>()
        BatteryMonitorManager.batteryTelemetryOverride = {
            BatteryTelemetrySnapshot(
                level = 64,
                isCharging = false,
                temperatureCelsius = 32.5f,
                voltageMv = 3950,
                healthLabel = "Good",
                plugTypeLabel = "Battery",
                dischargeRateMahPerHour = 290,
                isCurrentAvailable = true
            )
        }
        BatteryMonitorManager.clockProviderOverride = {
            7_200_000L to 1_800_000L // 2h elapsed, 30m awake -> 1h 30m deep sleep (75%)
        }
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

        // 1. App Locker card polish + toggle
        list.performScrollToNode(hasTestTag("system_app_locker_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("App Locker").assertIsDisplayed()
        composeTestRule.onNodeWithText("Secured apps monitored", substring = true).assertIsDisplayed()
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

        // 3. Battery Monitor & Deep Sleep card
        list.performScrollToNode(hasTestTag("system_battery_monitor_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Battery Monitor & Deep Sleep").assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Real-time power drain, battery health & CPU deep sleep tracking"
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText("64%", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("32.5°C", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("3.95V", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("battery_stat_health", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("75% Deep Sleep", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("1h 30m", substring = true, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("battery_drain_rate_value", useUnmergedTree = true).assertTextContains("290 mAh/h")

        // 4. Battery Charging Alarm card polish + threshold chips
        list.performScrollToNode(hasTestTag("system_battery_alarm_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Battery Charging Alarm").assertIsDisplayed()
        composeTestRule.onNodeWithTag("battery_alarm_chip_90", useUnmergedTree = true).performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000L) {
            composeTestRule
                .onAllNodes(hasTestTag("battery_alarm_threshold_summary") and hasText("Target: 90% high", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("battery_alarm_threshold_summary", useUnmergedTree = true)
            .assertTextContains("Target: 90% high", substring = true)

        // 5. Background & Wakelock Detector card polish
        list.performScrollToNode(hasTestTag("system_wakelock_detector_card"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Background & Wakelock Detector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("wakelock_status_badge", useUnmergedTree = true).assertIsDisplayed()
    }
}
