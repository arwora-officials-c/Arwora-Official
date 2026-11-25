package com.arwora

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.arwora.ui.screens.AppLockerScreen
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.AppLockerManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h915dp-xxhdpi")
class AppLockerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppLockerManager.init(context)
        AppLockerManager.setLockerEnabled(context, false)
        AppLockerManager.unlockAllTools(context)
        AppLockerManager.clearUnlockSessions()
    }

    @Test
    fun testPinSetupAndVerification() {
        assertFalse(AppLockerManager.isLockerEnabled.value)
        assertFalse(AppLockerManager.isToolLockedByRoute("wifi_controller"))

        // Set PIN
        AppLockerManager.setPin(context, "1234", "Favorite color?", "blue")
        assertTrue(AppLockerManager.hasPin.value)

        // Verify correct PIN
        assertTrue(AppLockerManager.verifyPin(context, "1234"))

        // Verify incorrect PIN
        assertFalse(AppLockerManager.verifyPin(context, "9999"))
    }

    @Test
    fun testToolLockingAndSessionUnlock() {
        AppLockerManager.setPin(context, "4321")
        AppLockerManager.setLockerEnabled(context, true)
        assertTrue(AppLockerManager.isLockerEnabled.value)

        // Tool is initially unlocked
        assertFalse(AppLockerManager.isToolLockedByRoute("wifi_controller"))

        // Lock wifi_controller
        AppLockerManager.toggleToolLock(context, "wifi_ctrl")
        assertTrue(AppLockerManager.isToolLockedByRoute("wifi_controller"))

        // Unlock session temporarily
        AppLockerManager.unlockSession("wifi_controller")
        assertFalse(AppLockerManager.isToolLockedByRoute("wifi_controller"))

        // Clear unlock session
        AppLockerManager.clearUnlockSessions()
        assertTrue(AppLockerManager.isToolLockedByRoute("wifi_controller"))
    }

    @Test
    fun testSecurityAnswerRecovery() {
        AppLockerManager.setPin(context, "1111", "Pet name?", "Fluffy")
        assertTrue(AppLockerManager.verifyPin(context, "1111"))

        // Reset with correct answer
        val success = AppLockerManager.resetPinWithSecurityAnswer(context, "Fluffy", "2222")
        assertTrue(success)
        assertTrue(AppLockerManager.verifyPin(context, "2222"))
        assertFalse(AppLockerManager.verifyPin(context, "1111"))

        // Attempt reset with wrong answer
        val failed = AppLockerManager.resetPinWithSecurityAnswer(context, "Spot", "3333")
        assertFalse(failed)
        assertTrue(AppLockerManager.verifyPin(context, "2222"))
    }

    @Test
    fun testSelfPackageExclusion() {
        // Verify self-packages cannot be locked
        assertTrue(AppLockerManager.isSelfPackageName("com.arworahyper.toolkit"))
        assertTrue(AppLockerManager.isSelfPackageName("com.arwora"))
        assertTrue(AppLockerManager.isSelfPackageName("com.arworahyper.toolkit.sub"))
        assertFalse(AppLockerManager.isSelfPackageName("com.google.android.apps.photos"))
        assertFalse(AppLockerManager.isSelfPackageName("com.android.gallery3d"))

        // Attempting to lock self-package must return false and not add to locked list
        val result = AppLockerManager.toggleAppLock(context, "com.arworahyper.toolkit")
        assertFalse(result)
        assertFalse(AppLockerManager.isAppLocked("com.arworahyper.toolkit"))
        assertFalse(AppLockerManager.isAppLocked("com.arwora"))

        // Service isSelfPackage helper
        assertTrue(com.arwora.service.AppLockerService.isSelfPackage("com.arworahyper.toolkit", context))
        assertTrue(com.arwora.service.AppLockerService.isSelfPackage("com.arwora", context))
        assertFalse(com.arwora.service.AppLockerService.isSelfPackage("com.google.android.apps.photos", context))
    }

    @Test
    fun testExternalAppLockingAndForegroundSession() {
        AppLockerManager.setPin(context, "5555")
        AppLockerManager.setLockerEnabled(context, true)
        AppLockerManager.setTimeoutMinutes(context, 0) // Immediate timeout

        val albumsPackage = "com.google.android.apps.photos"
        val willLock = AppLockerManager.toggleAppLock(context, albumsPackage)
        assertTrue(willLock)
        assertTrue(AppLockerManager.isAppLocked(albumsPackage))

        // Initially locked
        assertFalse(AppLockerManager.isPackageUnlocked(albumsPackage))

        // Session unlock
        AppLockerManager.unlockSession(albumsPackage)
        assertTrue(AppLockerManager.isPackageUnlocked(albumsPackage))

        // Transitioning to self-package (Toolkit) does NOT revoke immediate session
        AppLockerManager.onForegroundPackageChanged("com.arworahyper.toolkit")
        assertTrue(AppLockerManager.isPackageUnlocked(albumsPackage))

        // Transitioning away to another app revokes immediate session
        AppLockerManager.onForegroundPackageChanged("com.android.calculator2")
        assertFalse(AppLockerManager.isPackageUnlocked(albumsPackage))
    }

    @Test
    fun appLockerScreenRendersUnifiedHeroCardSegmentedTabsAndNonWrappingCategoryPills() {
        val app = ApplicationProvider.getApplicationContext<NameGenApplication>()
        AppLockerManager.setPin(context, "1234")
        AppLockerManager.setLockerEnabled(context, true)

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

        // 1. Unified Hero Protection Banner Card & inline status pills
        composeTestRule.onNodeWithTag("master_lock_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("App Locker Active").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_locker_pin_status_pill", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("PIN: Configured", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_locker_biometric_status_pill", useUnmergedTree = true).assertIsDisplayed()

        // 2. Segmented Pill Tab Selector
        composeTestRule.onNodeWithTag("app_locker_tab_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_tools").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_apps").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_security").assertIsDisplayed()

        // 3. Tools section header, bulk actions & single-line non-wrapping Location badge on Fake GPS Location
        composeTestRule.onNodeWithText("System Tools & Features").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_locker_lock_all_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_locker_unlock_all_btn").assertIsDisplayed()

        composeTestRule.onNodeWithTag("tool_lock_item_fake_gps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fake GPS Location", useUnmergedTree = true).assertIsDisplayed()
        val locationBadgeNode = composeTestRule.onNodeWithText("Location", useUnmergedTree = true)
        locationBadgeNode.assertIsDisplayed()
        val layoutResults = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        locationBadgeNode.fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action?.invoke(layoutResults)
        assertTrue("Expected TextLayoutResult for Location pill", layoutResults.isNotEmpty())
        assertEquals(
            "Location category pill should render on a single line without wrapping",
            1,
            layoutResults.first().lineCount
        )
        assertFalse(
            "Location category pill must not be ellipsized",
            layoutResults.first().isLineEllipsized(0)
        )
        assertTrue(
            "Location category pill glyphs must fit within measured width",
            layoutResults.first().getLineRight(0) <= layoutResults.first().size.width + 1f
        )
        val pillHeightDp = with(composeTestRule.density) {
            locationBadgeNode.fetchSemanticsNode().boundsInRoot.height.toDp().value
        }
        assertTrue(
            "Location category pill height should remain compact single-line (< 28dp, was ${pillHeightDp}dp)",
            pillHeightDp in 8f..28f
        )

        // Lock All & Unlock All actions work cleanly
        composeTestRule.onNodeWithTag("app_locker_lock_all_btn").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AppLockerManager.availableTools.size, AppLockerManager.lockedToolIds.value.size)

        composeTestRule.onNodeWithTag("app_locker_unlock_all_btn").performClick()
        composeTestRule.waitForIdle()
        assertTrue(AppLockerManager.lockedToolIds.value.isEmpty())

        // Switch to Apps tab
        composeTestRule.onNodeWithTag("tab_apps").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("app_locker_search_input").assertIsDisplayed()

        // Switch to Security tab
        composeTestRule.onNodeWithTag("tab_security").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Security PIN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Biometric Authentication").assertIsDisplayed()
        composeTestRule.onNodeWithText("Auto-Relock Timeout").assertIsDisplayed()
        composeTestRule.onNodeWithText("Protection Options").assertIsDisplayed()
    }

    @Test
    fun appLockerScreenSupportsLightThemeCleanly() {
        val app = ApplicationProvider.getApplicationContext<NameGenApplication>()
        AppLockerManager.setPin(context, "1234")
        AppLockerManager.setLockerEnabled(context, false)

        composeTestRule.setContent {
            val navController = rememberNavController()
            NameGenProTheme(darkTheme = false, whiteTheme = true) {
                AppLockerScreen(
                    navController = navController,
                    appManagerViewModel = app.appManagerViewModel
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("master_lock_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("App Locker Inactive").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_locker_tab_container").assertIsDisplayed()
    }
}
