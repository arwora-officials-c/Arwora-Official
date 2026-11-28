package com.arwora

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.arwora.deviceadmin.AppDeviceAdminReceiver
import com.arwora.receiver.AppInstallReceiver
import com.arwora.receiver.SecretCodeReceiver
import com.arwora.receiver.UninstallReceiver
import com.arwora.service.AppInstallMonitorService
import com.arwora.service.ArworaCoreAccessibilityService
import com.arwora.ui.screens.AppLockerScreen
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.AppHiderManager
import com.arwora.utils.AppLockerManager
import com.arwora.utils.HiderPrivilegeMode
import com.arwora.utils.NativeShellResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h915dp-xxhdpi")
class AppAdvancedSecurityTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AdbShellExecutor.resetTestOverrides()
        AppLockerManager.resetForTests(context)
        AppHiderManager.resetForTests(context)
        shadowOf(context as Application).clearNextStartedActivities()
    }

    @After
    fun tearDown() {
        AdbShellExecutor.resetTestOverrides()
        AppLockerManager.resetForTests(context)
        AppHiderManager.resetForTests(context)
    }

    @Test
    fun appHiderExecutesPrivilegedHideAndUnhideCommandsAndSelfStealth() = runBlocking {
        val executedCommands = mutableListOf<String>()
        AdbShellExecutor.shizukuBinderProbeOverride = { true }
        AdbShellExecutor.shizukuPermissionProbeOverride = { true }
        AdbShellExecutor.shizukuCommandRunnerOverride = { cmd, _ ->
            executedCommands.add(cmd)
            NativeShellResult(success = true, output = "success", exitCode = 0)
        }

        val targetPkg = "com.example.secretchat"
        val hideResult = AppHiderManager.hideApp(context, targetPkg)
        assertTrue(hideResult.success)
        assertTrue(hideResult.systemLevelHidden)
        assertEquals(HiderPrivilegeMode.SHIZUKU, hideResult.privilegeMode)
        assertTrue(AppHiderManager.isAppHidden(targetPkg))
        assertTrue(executedCommands.any { it == "pm hide $targetPkg" })

        val unhideResult = AppHiderManager.unhideApp(context, targetPkg)
        assertTrue(unhideResult.success)
        assertFalse(AppHiderManager.isAppHidden(targetPkg))
        assertTrue(executedCommands.any { it == "pm unhide $targetPkg" })

        // Self-package cannot be hidden via hideApp
        val selfHide = AppHiderManager.hideApp(context, context.packageName)
        assertFalse(selfHide.success)

        // Self-Stealth toggles SplashActivity disabled while keeping SecretCodeReceiver enabled
        val stealthOn = AppHiderManager.setSelfStealthEnabled(context, true)
        assertTrue(stealthOn.success)
        assertTrue(AppLockerManager.isSelfStealthEnabled.value)
        val pm = context.packageManager
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            pm.getComponentEnabledSetting(ComponentName(context.packageName, "${context.packageName}.SplashActivity"))
        )
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            pm.getComponentEnabledSetting(ComponentName(context, SecretCodeReceiver::class.java))
        )

        // Turning stealth off restores launcher component
        val stealthOff = AppHiderManager.setSelfStealthEnabled(context, false)
        assertTrue(stealthOff.success)
        assertFalse(AppLockerManager.isSelfStealthEnabled.value)
        assertEquals(
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            pm.getComponentEnabledSetting(ComponentName(context.packageName, "${context.packageName}.SplashActivity"))
        )
    }

    @Test
    fun secretCodeReceiverValidatesCodeAndLaunchesHiddenVault() {
        assertTrue(AppHiderManager.isSecretDialerCode("*#*#2796#*#*"))
        assertFalse(AppHiderManager.isSecretDialerCode("*#*#0000#*#*"))

        val receiver = SecretCodeReceiver()
        val intent = Intent("android.provider.Telephony.SECRET_CODE").apply {
            data = Uri.parse("android_secret_code://2796")
        }
        receiver.onReceive(context, intent)

        val launched = shadowOf(context as Application).nextStartedActivity
        assertNotNull(launched)
        assertEquals(MainActivity::class.java.name, launched.component?.className)
        assertTrue(launched.getBooleanExtra(SecretCodeReceiver.EXTRA_OPEN_HIDDEN_VAULT, false))
        assertTrue(launched.getBooleanExtra("skip_splash", false))
    }

    @Test
    fun uninstallProtectionInterceptsDeleteAndDeviceAdminDisableUntilAuthorized() {
        AppLockerManager.setPin(context, "1234")
        AppLockerManager.setUninstallProtectionEnabled(context, true)

        // Intercept self uninstall attempt
        val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        UninstallReceiver().onReceive(context, deleteIntent)

        val lockActivity = shadowOf(context as Application).nextStartedActivity
        assertNotNull(lockActivity)
        assertEquals(AppLockActivity::class.java.name, lockActivity.component?.className)
        assertEquals(AppLockActivity.MODE_UNINSTALL_GUARD, lockActivity.getStringExtra(AppLockActivity.EXTRA_LOCK_MODE))
        assertEquals(context.packageName, lockActivity.getStringExtra(AppLockActivity.EXTRA_PACKAGE_NAME))

        // Device Admin disable prompt triggers guard warning and AppLockActivity
        shadowOf(context as Application).clearNextStartedActivities()
        val warningMsg = AppDeviceAdminReceiver().onDisableRequested(context, Intent())
        assertTrue(warningMsg.toString().contains("Uninstall Protection", ignoreCase = true))
        val adminGuard = shadowOf(context as Application).nextStartedActivity
        assertNotNull(adminGuard)
        assertEquals(AppLockActivity.MODE_UNINSTALL_GUARD, adminGuard.getStringExtra(AppLockActivity.EXTRA_LOCK_MODE))

        // Granting temporary pass allows uninstallation cleanly
        AppLockerManager.grantTemporaryUninstallPass()
        assertFalse(UninstallReceiver.interceptUninstallIntent(context, context.packageName, bypassCooldown = true))
    }

    @Test
    fun installBlockInterceptsNewPackageAdditionAndRejectsOrAuthorizesInstall() = runBlocking {
        AppLockerManager.setPin(context, "1234")
        AppLockerManager.setInstallBlockEnabled(context, true)

        val newApkPkg = "com.unknown.sideload.app"
        val installIntent = Intent(Intent.ACTION_PACKAGE_ADDED).apply {
            data = Uri.parse("package:$newApkPkg")
        }
        AppInstallReceiver().onReceive(context, installIntent)

        val lockIntent = shadowOf(context as Application).nextStartedActivity
        assertNotNull("Expected AppLockActivity to launch on blocked install", lockIntent)
        assertEquals(AppLockActivity::class.java.name, lockIntent.component?.className)
        assertEquals(AppLockActivity.MODE_INSTALL_BLOCK, lockIntent.getStringExtra(AppLockActivity.EXTRA_LOCK_MODE))
        assertEquals(newApkPkg, lockIntent.getStringExtra(AppLockActivity.EXTRA_PACKAGE_NAME))

        // Authorized install bypasses block
        AppLockerManager.authorizeInstall(newApkPkg)
        assertFalse(AppInstallMonitorService.handleNewInstallDetected(context, newApkPkg, bypassCooldown = true))

        // Reject and uninstall invokes pm uninstall when privileged shell is active
        val commands = mutableListOf<String>()
        AdbShellExecutor.shizukuBinderProbeOverride = { true }
        AdbShellExecutor.shizukuPermissionProbeOverride = { true }
        AdbShellExecutor.shizukuCommandRunnerOverride = { cmd, _ ->
            commands.add(cmd)
            NativeShellResult(success = true, output = "Success", exitCode = 0)
        }
        val rejected = AppInstallMonitorService.rejectAndUninstallPackage(context, newApkPkg)
        assertTrue(rejected)
        assertTrue(commands.contains("pm uninstall $newApkPkg"))
    }

    @Test
    fun appLockerScreenRendersAdvancedSecurityCardsAndOpensHiddenVault() {
        val app = ApplicationProvider.getApplicationContext<NameGenApplication>()
        AppLockerManager.setLockerEnabled(context, true)
        // Leave PIN unset so toggling Vault opens directly without modal PinAuth blocker in this UI smoke check

        composeTestRule.setContent {
            val navController = rememberNavController()
            NameGenProTheme(darkTheme = true) {
                AppLockerScreen(
                    navController = navController,
                    appManagerViewModel = app.appManagerViewModel
                )
            }
        }
        composeTestRule.waitForIdle()

        // Switch to Security tab
        composeTestRule.onNodeWithTag("tab_security").performClick()
        composeTestRule.waitForIdle()

        // Scroll to Advanced Security cards
        composeTestRule.onNodeWithTag("advanced_security_section_header").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("advanced_hide_apps_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("advanced_uninstall_protect_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("advanced_install_block_card").performScrollTo().assertIsDisplayed()

        // Open Hidden Apps Vault sheet (PIN is unset so it opens directly without PinAuthDialog)
        composeTestRule.onNodeWithTag("open_hidden_apps_vault_button").performScrollTo().performClick()
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasTestTag("self_stealth_card"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("hidden_apps_vault_sheet").assertExists()
        composeTestRule.onNodeWithTag("self_stealth_card").assertExists()
        composeTestRule.onNodeWithText("Hide Arwora Icon (Self-Stealth)", useUnmergedTree = true).assertExists()

        // Close Vault cleanly
        composeTestRule.onNodeWithTag("close_hidden_vault_btn").performClick()
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        // Toggle Uninstall Protection & Install Block (toggling on without PIN prompts PinSetupDialog)
        composeTestRule.onNodeWithTag("toggle_uninstall_protection").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(AppLockerManager.isUninstallProtectionEnabled.value)
        composeTestRule.onNodeWithText("Set New PIN").assertIsDisplayed()
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Close")).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("toggle_install_block").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(AppLockerManager.isInstallBlockEnabled.value)
        composeTestRule.onNode(androidx.compose.ui.test.hasContentDescription("Close")).performClick()
        composeTestRule.waitForIdle()

        // Configure PIN and verify disabling Uninstall Protection prompts PinAuthDialog
        AppLockerManager.setPin(context, "1234")
        composeTestRule.onNodeWithTag("toggle_uninstall_protection").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        // Protection must stay enabled and prompt PinAuthDialog
        assertTrue(AppLockerManager.isUninstallProtectionEnabled.value)
        composeTestRule.onNodeWithText("Disable Uninstall Guard").assertIsDisplayed()
    }

    @Test
    fun unifiedCoreAccessibilityServiceEnforcesLockedAppsAndReportsSharedStatus() {
        val targetPkg = "com.example.lockedchat"
        AppLockerManager.setPin(context, "1234")
        AppLockerManager.setLockerEnabled(context, true)
        AppLockerManager.toggleAppLock(context, targetPkg)
        assertTrue(AppLockerManager.isAppLocked(targetPkg))

        val controller = Robolectric.buildService(ArworaCoreAccessibilityService::class.java).create()
        val service = controller.get()
        assertFalse(ArworaCoreAccessibilityService.isRunning)

        // Dispatch foreground window state change for locked package
        val event = android.view.accessibility.AccessibilityEvent.obtain(
            android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ).apply {
            packageName = targetPkg
        }
        service.onAccessibilityEvent(event)
        @Suppress("DEPRECATION")
        event.recycle()

        val launched = shadowOf(context as Application).nextStartedActivity
        assertNotNull("Expected ArworaCoreAccessibilityService to launch AppLockActivity", launched)
        assertEquals(AppLockActivity::class.java.name, launched.component?.className)
        assertEquals(targetPkg, launched.getStringExtra(AppLockActivity.EXTRA_PACKAGE_NAME))

        // Verify shared enabled-state check matches Settings.Secure entry
        val component = ComponentName(context.packageName, ArworaCoreAccessibilityService::class.java.name).flattenToString()
        android.provider.Settings.Secure.putInt(context.contentResolver, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        android.provider.Settings.Secure.putString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, component)
        assertTrue(ArworaCoreAccessibilityService.isAccessibilityEnabled(context))

        controller.destroy()
    }
}
