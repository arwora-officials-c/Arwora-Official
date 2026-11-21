package com.arwora

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.arwora.feature.system.appops.data.AppOpsManager
import com.arwora.feature.system.appops.model.AppOpsCategory
import com.arwora.feature.system.appops.model.AppOpsItem
import com.arwora.feature.system.appops.model.StandbyBucketMode
import com.arwora.feature.system.appops.ui.AppOpsDetailView
import com.arwora.feature.system.appops.ui.AppOpsPrivilegeWarningCard
import com.arwora.feature.system.appops.ui.AppOpsRowCard
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.NativeShellResult
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
class AppOpsEngineBridgeTest {

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
    fun setAppOpReturnsFalseWhenUnprivileged_NoEmptyOutputFalsePositive() = runTest {
        AdbShellExecutor.shizukuBinderProbeOverride = { false }
        AdbShellExecutor.shizukuPermissionProbeOverride = { false }
        AdbShellExecutor.adbReadyProbeOverride = { false }

        assertFalse(AppOpsManager.isAdbAvailable(context))
        val result = AppOpsManager.setAppOp(
            packageName = "com.example.demo",
            opName = "SYSTEM_ALERT_WINDOW",
            mode = "allow",
            context = context
        )
        assertFalse(result)
    }

    @Test
    fun setAppOpRoutesThroughShizukuAndVerifiesResult() = runTest {
        val executedCommands = mutableListOf<String>()
        var storedMode = "default"

        AdbShellExecutor.shizukuBinderProbeOverride = { true }
        AdbShellExecutor.shizukuPermissionProbeOverride = { true }
        AdbShellExecutor.adbReadyProbeOverride = { true }
        AdbShellExecutor.shizukuCommandRunnerOverride = { cmd, _ ->
            executedCommands.add(cmd)
            when {
                cmd.startsWith("appops set ") -> {
                    storedMode = cmd.substringAfterLast(" ").trim()
                    NativeShellResult(success = true, output = "", exitCode = 0)
                }
                cmd.startsWith("appops get ") -> {
                    NativeShellResult(
                        success = true,
                        output = "SYSTEM_ALERT_WINDOW: $storedMode; time=+1s ago",
                        exitCode = 0
                    )
                }
                else -> NativeShellResult(success = true, output = "", exitCode = 0)
            }
        }

        val ok = AppOpsManager.setAppOp(
            packageName = "com.example.demo",
            opName = "OP_SYSTEM_ALERT_WINDOW",
            mode = "ignore",
            context = context
        )
        assertTrue(ok)
        assertEquals(
            listOf(
                "appops set com.example.demo SYSTEM_ALERT_WINDOW ignore",
                "appops get com.example.demo SYSTEM_ALERT_WINDOW"
            ),
            executedCommands
        )
        assertEquals("ignore", AppOpsManager.getAppOp("com.example.demo", "SYSTEM_ALERT_WINDOW", context))
    }

    @Test
    fun setAppOpFallsBackToWirelessAdbAndExecutesStandbyBucket() = runTest {
        val adbCommands = mutableListOf<String>()
        AdbShellExecutor.shizukuBinderProbeOverride = { false }
        AdbShellExecutor.shizukuPermissionProbeOverride = { false }
        AdbShellExecutor.adbReadyProbeOverride = { true }
        AdbShellExecutor.adbCommandRunnerOverride = { cmd, _ ->
            adbCommands.add(cmd)
            when {
                cmd.startsWith("appops get ") -> {
                    NativeShellResult(success = true, output = "OP_WRITE_SETTINGS: allow", exitCode = 0)
                }
                else -> NativeShellResult(success = true, output = "", exitCode = 0)
            }
        }

        assertTrue(AppOpsManager.setAppOp("com.example.demo", "OP_WRITE_SETTINGS", "allow", context))
        assertTrue(AppOpsManager.setStandbyBucket("com.example.demo", StandbyBucketMode.RARE, context))
        assertTrue(adbCommands.contains("appops set com.example.demo WRITE_SETTINGS allow"))
        assertTrue(adbCommands.contains("am set-standby-bucket com.example.demo rare"))
    }

    @Test
    fun batchDumpAndSinglePackageParsersExtractAllStandardOpModes() {
        val sampleDump = """
            Uid u0a214:
              Package com.example.alpha:
                COARSE_LOCATION: allow; time=+5m ago
                OP_READ_PHONE_STATE: deny
                CAMERA: ignore
              Package com.example.beta:
                Uid mode: SYSTEM_ALERT_WINDOW: allow
                WRITE_SETTINGS: default
        """.trimIndent()

        val parsedBatch = AppOpsManager.parseBatchAppOpsDump(sampleDump)
        assertEquals("allow", parsedBatch["com.example.alpha"]?.get("COARSE_LOCATION"))
        assertEquals("deny", parsedBatch["com.example.alpha"]?.get("READ_PHONE_STATE"))
        assertEquals("ignore", parsedBatch["com.example.alpha"]?.get("CAMERA"))
        assertEquals("allow", parsedBatch["com.example.beta"]?.get("SYSTEM_ALERT_WINDOW"))
        assertEquals("default", parsedBatch["com.example.beta"]?.get("WRITE_SETTINGS"))

        val singleOutput = """
            Uid mode: RECORD_AUDIO: ignore
            CAMERA: allow; time=+12s ago
            OP_WRITE_SETTINGS: deny
        """.trimIndent()
        val parsedSingle = AppOpsManager.parseSinglePackageAppOps(singleOutput)
        assertEquals("ignore", parsedSingle["RECORD_AUDIO"])
        assertEquals("allow", parsedSingle["CAMERA"])
        assertEquals("deny", parsedSingle["WRITE_SETTINGS"])
        assertTrue(AppOpsManager.parseSinglePackageAppOps("No operations.").isEmpty())
    }

    @Test
    fun warningCardShowsRequiredCopyAndLaunchesPairingWizardOnClick() {
        var pairingClicks = 0
        composeTestRule.setContent {
            NameGenProTheme {
                AppOpsPrivilegeWarningCard(
                    isShizukuRunning = true,
                    onOpenPairingWizard = { pairingClicks++ },
                    onAuthorizeShizuku = {}
                )
            }
        }

        composeTestRule
            .onNodeWithText("ADB or Shizuku permission required for App Ops execution. Tap to connect.")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_ops_connect_button").performClick()
        assertEquals(1, pairingClicks)
        composeTestRule.onNodeWithTag("app_ops_authorize_shizuku_button").assertIsDisplayed()
    }

    @Test
    fun rowCardAndDetailViewDisplayExpressiveAllowedRestrictedBadgesAndGroupedOps() {
        val sampleApp = AppOpsItem(
            appName = "Arwora Demo App",
            packageName = "com.example.demo",
            versionName = "2.4.0",
            versionCode = 24,
            uid = 10240,
            minSdk = 24,
            targetSdk = 35,
            dataDir = "/data/data/com.example.demo",
            sourceDir = "/data/app/com.example.demo/base.apk",
            firstInstallTime = 1_700_000_000_000L,
            lastUpdateTime = 1_700_000_100_000L,
            firstInstallFormatted = "2024-01-01 12:00",
            lastUpdateFormatted = "2024-01-02 12:00",
            isSystem = false,
            isDisabled = false,
            isHidden = false,
            hasLocationPermission = true,
            hasCameraPermission = true,
            hasAudioPermission = true,
            hasSystemOpsPermission = true,
            appOpsStates = mapOf(
                "CAMERA" to "allow",
                "COARSE_LOCATION" to "allow",
                "RECORD_AUDIO" to "ignore",
                "WRITE_SETTINGS" to "deny",
                "READ_PHONE_STATE" to "default",
                "SYSTEM_ALERT_WINDOW" to "allow"
            )
        )

        var lastOpUpdate: Pair<String, String>? = null
        var activeCategory = AppOpsCategory.ALL

        composeTestRule.setContent {
            NameGenProTheme {
                AppOpsDetailView(
                    app = sampleApp,
                    isAdbConnected = true,
                    selectedCategory = activeCategory,
                    onCategorySelect = { activeCategory = it },
                    onStandbyBucketChange = {},
                    onUpdateAppOp = { op, mode -> lastOpUpdate = op to mode },
                    onRevokeAll = {},
                    onOpenPairingWizard = {}
                )
            }
        }

        composeTestRule.onNodeWithText("3 Allowed").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 Restricted").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_ops_detail_category_location").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_ops_detail_category_camera").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_ops_detail_category_audio").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_ops_detail_category_system").assertIsDisplayed()

        composeTestRule.onNodeWithTag("app_op_CAMERA_ignore").performScrollTo().performClick()
        assertEquals("CAMERA" to "ignore", lastOpUpdate)

        // Verify row card also surfaces Allowed & Restricted badges cleanly
        composeTestRule.setContent {
            NameGenProTheme {
                AppOpsRowCard(app = sampleApp, onClick = {})
            }
        }
        composeTestRule.onNodeWithText("3 Allowed").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 Restricted").assertIsDisplayed()
    }
}
