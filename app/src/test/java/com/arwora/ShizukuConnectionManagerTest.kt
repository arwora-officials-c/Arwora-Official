package com.arwora

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.arwora.ui.screens.PrivilegeEngineBridgeCard
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.NativeShellResult
import com.arwora.utils.PrivilegeEngine
import com.arwora.utils.ShizukuBridge
import com.arwora.utils.ShizukuBridgeManager
import com.arwora.utils.ShizukuConnectOutcome
import com.arwora.utils.ShizukuUiStatus
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
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class ShizukuConnectionManagerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ShizukuBridge.resetTestOverrides()
        AdbShellExecutor.resetTestOverrides()
    }

    @After
    fun tearDown() {
        ShizukuBridge.resetTestOverrides()
        AdbShellExecutor.resetTestOverrides()
        ShizukuBridgeManager.checkShizukuStatus(context)
    }

    @Test
    fun lifecycleStateTransitionsAndBinderDeathSyncManagerAndExecutor() = runTest {
        var binderAlive = false
        var granted = false
        val requestedCodes = mutableListOf<Int>()

        ShizukuBridge.binderProbeOverride = { binderAlive }
        ShizukuBridge.permissionProbeOverride = { granted }
        ShizukuBridge.rationaleProbeOverride = { false }
        ShizukuBridge.requestPermissionInvokerOverride = { code -> requestedCodes.add(code) }
        ShizukuBridge.installedProbeOverride = { true }
        AdbShellExecutor.adbReadyProbeOverride = { false }

        // 1. Service stopped state
        assertEquals(ShizukuUiStatus.SERVICE_STOPPED, ShizukuBridgeManager.initialize(context))
        assertEquals("Shizuku Service Stopped", ShizukuBridgeManager.connectionStatus.value.badgeText)
        assertFalse(ShizukuBridgeManager.isRemoteBinderInitialized.value)
        assertEquals(PrivilegeEngine.NONE, AdbShellExecutor.activeEngine.value)

        // 2. Binder arrives without permission -> Permission Required
        binderAlive = true
        ShizukuBridge.onBinderReceived()
        assertEquals(ShizukuUiStatus.PERMISSION_REQUIRED, ShizukuBridgeManager.connectionStatus.value)
        assertEquals("Permission Required", ShizukuBridgeManager.connectionStatus.value.badgeText)
        assertFalse(ShizukuBridgeManager.isRemoteBinderInitialized.value)

        // 3. Connect Shizuku requests permission using REQUEST_CODE
        val outcome = ShizukuBridgeManager.connectShizuku(context)
        assertEquals(ShizukuConnectOutcome.PERMISSION_REQUESTED, outcome)
        assertEquals(listOf(ShizukuBridgeManager.REQUEST_CODE), requestedCodes)

        // 4. User grants permission -> instant update to Active / Connected + remote binder initialized
        granted = true
        ShizukuBridge.onPermissionResult(ShizukuBridgeManager.REQUEST_CODE, PackageManager.PERMISSION_GRANTED)
        assertEquals(ShizukuUiStatus.ACTIVE_CONNECTED, ShizukuBridgeManager.connectionStatus.value)
        assertEquals("Active / Connected", ShizukuBridgeManager.connectionStatus.value.badgeText)
        assertTrue(ShizukuBridgeManager.isRemoteBinderInitialized.value)
        assertEquals(PrivilegeEngine.SHIZUKU, AdbShellExecutor.activeEngine.value)

        // Verify shell execution works over Shizuku
        AdbShellExecutor.shizukuCommandRunnerOverride = { cmd, _ ->
            NativeShellResult(success = true, output = "ok:$cmd", exitCode = 0)
        }
        val res = AdbShellExecutor.executeWithEngine("id", context = context)
        assertTrue(res.success)
        assertEquals(PrivilegeEngine.SHIZUKU, res.engineUsed)

        // 5. Binder death -> resets to Shizuku Service Stopped cleanly
        binderAlive = false
        granted = false
        ShizukuBridgeManager.handleBinderDeath()
        assertEquals(ShizukuUiStatus.SERVICE_STOPPED, ShizukuBridgeManager.connectionStatus.value)
        assertFalse(ShizukuBridgeManager.isRemoteBinderInitialized.value)
        assertEquals(PrivilegeEngine.NONE, AdbShellExecutor.activeEngine.value)
    }

    @Test
    fun privilegeCardConnectShizukuButtonRequestsPermissionAndUpdatesBadgeLive() {
        var binderAlive = true
        var granted = false
        var permissionRequestedCount = 0

        ShizukuBridge.binderProbeOverride = { binderAlive }
        ShizukuBridge.permissionProbeOverride = { granted }
        ShizukuBridge.rationaleProbeOverride = { false }
        ShizukuBridge.installedProbeOverride = { true }
        AdbShellExecutor.adbReadyProbeOverride = { false }
        ShizukuBridge.requestPermissionInvokerOverride = { _ ->
            permissionRequestedCount++
            granted = true
            ShizukuBridge.onPermissionResult(
                ShizukuBridgeManager.REQUEST_CODE,
                PackageManager.PERMISSION_GRANTED
            )
        }

        ShizukuBridgeManager.initialize(context)

        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                val engine by AdbShellExecutor.activeEngine.collectAsStateWithLifecycle()
                val shizukuStatus by ShizukuBridgeManager.connectionStatus.collectAsStateWithLifecycle()
                Column {
                    PrivilegeEngineBridgeCard(
                        engine = engine,
                        shizukuUiStatus = shizukuStatus,
                        onConnectShizukuClick = {
                            ShizukuBridgeManager.connectShizuku(context)
                        },
                        onClick = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("shizuku_connection_section", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("shizuku_status_badge", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNode(
            hasAnyAncestor(hasTestTag("shizuku_status_badge")) and hasText("Permission Required"),
            useUnmergedTree = true
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("connect_shizuku_button", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect Shizuku", useUnmergedTree = true).assertIsDisplayed()

        // Tap Connect Shizuku -> requests permission -> instant callback updates badge to Active / Connected
        composeTestRule.onNodeWithTag("connect_shizuku_button", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, permissionRequestedCount)
        composeTestRule.onNodeWithText("Active / Connected", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Connected", useUnmergedTree = true).assertIsDisplayed()

        // Simulate binder stopping -> badge immediately updates to Shizuku Service Stopped
        binderAlive = false
        granted = false
        ShizukuBridgeManager.handleBinderDeath()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Shizuku Service Stopped", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect Shizuku", useUnmergedTree = true).assertIsDisplayed()
    }
}
