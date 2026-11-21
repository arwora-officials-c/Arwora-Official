package com.arwora

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.arwora.feature.system.apps.capability.AppManagerCapability
import com.arwora.feature.system.apps.capability.Capabilities
import com.arwora.feature.system.shell.ShellExecutor
import com.arwora.feature.system.shell.ShellStatus
import com.arwora.ui.screens.PrivilegeEngineBridgeCard
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.AppPermissionsHelper
import com.arwora.utils.NativeShellResult
import com.arwora.utils.PrivilegeEngine
import kotlinx.coroutines.test.runTest
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
@Config(sdk = [36], qualifiers = "w380dp-h800dp")
class AdbShellExecutorBridgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AdbShellExecutor.resetTestOverrides()
    }

    @After
    fun tearDown() {
        AdbShellExecutor.resetTestOverrides()
        AdbShellExecutor.refreshEngineStatus(null)
    }

    @Test
    fun priorityOneRoutesThroughShizukuEvenWhenWirelessAdbAlsoReady() = runTest {
        var shizukuCalls = 0
        var adbCalls = 0

        AdbShellExecutor.shizukuBinderProbeOverride = { true }
        AdbShellExecutor.shizukuPermissionProbeOverride = { true }
        AdbShellExecutor.adbReadyProbeOverride = { true }
        AdbShellExecutor.shizukuCommandRunnerOverride = { cmd, _ ->
            shizukuCalls++
            NativeShellResult(success = true, output = "shizuku:$cmd", exitCode = 0)
        }
        AdbShellExecutor.adbCommandRunnerOverride = { cmd, _ ->
            adbCalls++
            NativeShellResult(success = true, output = "adb:$cmd", exitCode = 0)
        }

        assertEquals(PrivilegeEngine.SHIZUKU, AdbShellExecutor.resolveActiveEngine(context))
        assertEquals("Active via Shizuku", AdbShellExecutor.getEngineStatusBadge(context))
        assertEquals(ShellStatus.READY, ShellExecutor.checkAdbStatus(context))
        assertEquals(AppManagerCapability.WIRELESS_ADB, Capabilities.detectCapability(context))
        assertTrue(AppPermissionsHelper.isWirelessAdbGranted(context))

        val res = AdbShellExecutor.executeWithEngine("pm grant pkg ${Manifest.permission.CAMERA}", context = context)
        assertTrue(res.success)
        assertEquals(PrivilegeEngine.SHIZUKU, res.engineUsed)
        assertEquals("shizuku:pm grant pkg ${Manifest.permission.CAMERA}", res.output)
        assertEquals(1, shizukuCalls)
        assertEquals(0, adbCalls)

        val shellRes = ShellExecutor.executeCommand("getprop ro.build.version.release")
        assertEquals(0, shellRes.exitCode)
        assertEquals("shizuku:getprop ro.build.version.release", shellRes.stdout)
        assertEquals(2, shizukuCalls)
        assertEquals(0, adbCalls)
    }

    @Test
    fun priorityTwoFallsBackToWirelessAdbWhenShizukuUnavailableOrUnpermitted() = runTest {
        var adbCalls = 0

        // Shizuku binder running, but user has not granted Shizuku permission yet
        AdbShellExecutor.shizukuBinderProbeOverride = { true }
        AdbShellExecutor.shizukuPermissionProbeOverride = { false }
        AdbShellExecutor.adbReadyProbeOverride = { true }
        AdbShellExecutor.adbCommandRunnerOverride = { cmd, _ ->
            adbCalls++
            NativeShellResult(success = true, output = "adb:$cmd", exitCode = 0)
        }

        assertEquals(PrivilegeEngine.WIRELESS_ADB, AdbShellExecutor.resolveActiveEngine(context))
        assertEquals("Active via Wireless ADB", AdbShellExecutor.getEngineStatusBadge(context))
        assertEquals(ShellStatus.READY, ShellExecutor.checkAdbStatus(context))

        val res = AdbShellExecutor.executeWithEngine("appops set pkg SYSTEM_ALERT_WINDOW allow", context = context)
        assertTrue(res.success)
        assertEquals(PrivilegeEngine.WIRELESS_ADB, res.engineUsed)
        assertEquals("adb:appops set pkg SYSTEM_ALERT_WINDOW allow", res.output)
        assertEquals(1, adbCalls)
    }

    @Test
    fun returnsNoneGracefullyWhenNoPrivilegeEngineIsActive() = runTest {
        AdbShellExecutor.shizukuBinderProbeOverride = { false }
        AdbShellExecutor.shizukuPermissionProbeOverride = { false }
        AdbShellExecutor.adbReadyProbeOverride = { false }

        assertEquals(PrivilegeEngine.NONE, AdbShellExecutor.resolveActiveEngine(context))
        assertEquals("Permission Required", AdbShellExecutor.getEngineStatusBadge(context))
        assertFalse(AdbShellExecutor.isPrivilegedShellReady(context))

        val res = AdbShellExecutor.executeWithEngine("id", context = context)
        assertFalse(res.success)
        assertEquals(-1, res.exitCode)
        assertEquals(PrivilegeEngine.NONE, res.engineUsed)
        assertTrue(res.output.contains("privilege engine", ignoreCase = true))

        val shellRes = ShellExecutor.executeCommand("id")
        assertEquals(-1, shellRes.exitCode)
        assertTrue(shellRes.stderr.isNotBlank())
    }

    @Test
    fun privilegeEngineBridgeCardRendersAllThreeStatusBadges() {
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                PrivilegeEngineBridgeCard(
                    engine = PrivilegeEngine.SHIZUKU,
                    onClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("system_privilege_engine_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("Active via Shizuku").assertIsDisplayed()
    }

    @Test
    fun privilegeEngineBridgeCardRendersWirelessAdbAndRequiredBadges() {
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                androidx.compose.foundation.layout.Column {
                    PrivilegeEngineBridgeCard(
                        engine = PrivilegeEngine.WIRELESS_ADB,
                        onClick = {}
                    )
                    PrivilegeEngineBridgeCard(
                        engine = PrivilegeEngine.NONE,
                        onClick = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Active via Wireless ADB").assertIsDisplayed()
        composeTestRule.onNodeWithText("Permission Required").assertIsDisplayed()
    }
}
