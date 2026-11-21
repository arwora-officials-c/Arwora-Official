package com.arwora

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.arwora.ui.navigation.Screen
import com.arwora.ui.screens.PrivacyPolicyContent
import com.arwora.ui.screens.SPLASH_INITIAL_SCALE
import com.arwora.ui.screens.SPLASH_INITIAL_TRANSLATION_Y_DP
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyPolicyAndSplashTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun splashAnimationDurationFitsSmoothTargetRange() {
        assertEquals(500, SPLASH_LOGO_ANIM_DURATION_MS)
        assertEquals(1000L, SPLASH_POST_ANIM_HOLD_MS)
        assertEquals(1500L, SPLASH_TOTAL_DURATION_MS)
        assertEquals(0.90f, SPLASH_INITIAL_SCALE, 0.001f)
        assertEquals(0f, SPLASH_INITIAL_TRANSLATION_Y_DP, 0.001f)
        assertEquals("splash", Screen.Splash.route)

        assertEquals(500L, SplashActivity.ANIM_DURATION_MS)
        assertEquals(1000L, SplashActivity.HOLD_DURATION_MS)
        assertEquals(1500L, SplashActivity.TOTAL_DURATION_MS)
        assertEquals(0.90f, SplashActivity.INITIAL_SCALE, 0.001f)
        assertEquals(0f, SplashActivity.INITIAL_TRANSLATION_Y_DP, 0.001f)
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
            ContextCompat.getColor(context, R.color.splash_background)
        )

        val nightConfig = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        val nightContext = context.createConfigurationContext(nightConfig)
        assertEquals(
            android.graphics.Color.BLACK,
            ContextCompat.getColor(nightContext, R.color.splash_background)
        )

        val wordmarkDrawable = ContextCompat.getDrawable(context, R.drawable.ic_arwora_wordmark)
        assertNotNull("Custom ARWORA vector wordmark drawable must resolve", wordmarkDrawable)
    }

    @Test
    @Config(qualifiers = "night")
    fun splashActivityAppliesPureBlackBackgroundCenteredConstraintLayoutAndNavigatesToMain() {
        val controller = Robolectric.buildActivity(SplashActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.findViewById<ConstraintLayout>(R.id.splash_root)
        val wordmark = activity.findViewById<ImageView>(R.id.iv_arwora_wordmark)

        assertEquals(android.graphics.Color.BLACK, (root.background as ColorDrawable).color)
        assertNotNull(wordmark.drawable)
        assertNotNull(wordmark.colorFilter)

        val lp = wordmark.layoutParams as ConstraintLayout.LayoutParams
        val widthDp = lp.width / activity.resources.displayMetrics.density
        assertTrue("Width $widthDp dp must be within 220dp..260dp", widthDp in 220f..260f)
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, lp.height)
        assertEquals(ConstraintLayout.LayoutParams.PARENT_ID, lp.startToStart)
        assertEquals(ConstraintLayout.LayoutParams.PARENT_ID, lp.endToEnd)
        assertEquals(ConstraintLayout.LayoutParams.PARENT_ID, lp.topToTop)
        assertEquals(ConstraintLayout.LayoutParams.PARENT_ID, lp.bottomToBottom)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1600, TimeUnit.MILLISECONDS)
        val nextIntent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull("SplashActivity must launch MainActivity after hold", nextIntent)
        assertEquals(MainActivity::class.java.name, nextIntent.component?.className)
        assertTrue(activity.isFinishing)

        controller.destroy()
    }

    @Test
    fun splashScreenDisplaysArworaWordmarkWithoutOldLogoAndCompletesAfterHold() {
        var finished = false
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                SplashScreen(onSplashFinished = { finished = true })
            }
        }

        // Step one frame so LaunchedEffect(Unit) starts the 500ms Animatable fade+scale,
        // then advance 650ms before the 1500ms auto-dismiss completes.
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(650L)
        composeTestRule.waitForIdle()
        assertFalse(finished)
        composeTestRule.onNodeWithTag("splash_screen_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_arwora_wordmark", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("ARWORA", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("splash_app_logo", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("HYPER TOOLKIT", useUnmergedTree = true).assertDoesNotExist()

        // Advance past 500ms animation + 1000ms hold (1500ms total) to verify completion callback triggers smoothly.
        composeTestRule.mainClock.advanceTimeBy(950L)
        composeTestRule.waitForIdle()
        assertTrue(finished)
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
