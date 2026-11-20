package com.arwora

import android.content.Context
import android.content.res.Configuration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.arwora.ui.navigation.Screen
import com.arwora.ui.screens.PrivacyPolicyContent
import com.arwora.ui.screens.SPLASH_LOGO_ANIM_DURATION_MS
import com.arwora.ui.screens.SPLASH_POST_ANIM_HOLD_MS
import com.arwora.ui.screens.SPLASH_TOTAL_DURATION_MS
import com.arwora.ui.screens.SplashScreen
import com.arwora.ui.screens.resolveSplashPalette
import com.arwora.ui.theme.BackgroundDark
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.ui.theme.ThemeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyPolicyAndSplashTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun splashAnimationDurationFitsSmoothTargetRange() {
        assertTrue(
            "Splash wordmark fade/scale animation should stay between 400ms and 700ms",
            SPLASH_LOGO_ANIM_DURATION_MS in 400..700
        )
        assertTrue(
            "Total splash hold duration should stay smoothly between 1200ms and 1500ms",
            SPLASH_TOTAL_DURATION_MS in 1200L..1500L
        )
        assertTrue(
            "Post-animation hold should be positive",
            SPLASH_POST_ANIM_HOLD_MS >= 500L
        )
        assertEquals("splash", Screen.Splash.route)
    }

    @Test
    fun splashPaletteAutoSwitchesBetweenPureBlackDarkModeAndPureWhiteLightMode() {
        val darkPalette = resolveSplashPalette(isSystemDark = true)
        assertEquals(Color.Black, darkPalette.backgroundColor)
        assertEquals(Color.White, darkPalette.foregroundColor)

        val lightPalette = resolveSplashPalette(isSystemDark = false)
        assertEquals(Color.White, lightPalette.backgroundColor)
        assertEquals(Color.Black, lightPalette.foregroundColor)

        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            android.graphics.Color.WHITE,
            ContextCompat.getColor(context, R.color.splash_bg)
        )

        val nightConfig = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        val nightContext = context.createConfigurationContext(nightConfig)
        assertEquals(
            android.graphics.Color.BLACK,
            ContextCompat.getColor(nightContext, R.color.splash_bg)
        )
    }

    @Test
    fun splashScreenDisplaysArworaWordmarkWithoutOldLogo() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                SplashScreen(onSplashFinished = {})
            }
        }

        // Step one frame so LaunchedEffect(Unit) starts the 600ms Animatable fade+scale,
        // then advance 650ms before the 1300ms auto-dismiss completes.
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(650L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("splash_screen_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_arwora_wordmark", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("ARWORA", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_app_logo", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("HYPER TOOLKIT", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun dashboardThemeColorsStayAlignedForDarkSurface() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedDarkInt = android.graphics.Color.parseColor("#0B0B0F")
        assertEquals(expectedDarkInt, ContextCompat.getColor(context, R.color.dark_surface_bg))

        val darkConfig = ThemeManager.getThemeConfig(
            darkModeState = true,
            whiteThemeState = false,
            isSystemDark = false
        )
        assertTrue(darkConfig.isDark)
        assertEquals(BackgroundDark, darkConfig.rootBackgroundColor)
        assertEquals(expectedDarkInt, darkConfig.statusBarColorInt)
        assertFalse(darkConfig.isLightStatusBar)
        assertFalse(darkConfig.isLightNavigationBar)
    }

    @Test
    fun privacyPolicyRouteAndOfflineClausesAreComplete() {
        assertEquals("privacy_policy", Screen.PrivacyPolicy.route)
        val sections = PrivacyPolicyContent.sections
        assertTrue(sections.size >= 4)

        val allText = sections.joinToString("\n") { section ->
            "${section.title}\n${section.summary}\n${section.bullets.joinToString("\n")}"
        }
        assertTrue(allText.contains("offline", ignoreCase = true))
        assertTrue(allText.contains("Wireless ADB", ignoreCase = true))
        assertTrue(allText.contains("Storage", ignoreCase = true))
        assertTrue(allText.contains("RECORD_AUDIO", ignoreCase = true))
        assertTrue(allText.contains("telemetry", ignoreCase = true))
    }
}
