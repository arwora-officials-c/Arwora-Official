package com.arwora

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.arwora.ui.navigation.Screen
import com.arwora.ui.screens.PrivacyPolicyContent
import com.arwora.ui.screens.SPLASH_LOGO_ANIM_DURATION_MS
import com.arwora.ui.screens.SPLASH_POST_ANIM_HOLD_MS
import com.arwora.ui.screens.SPLASH_TEXT_ANIM_DURATION_MS
import com.arwora.ui.theme.BackgroundDark
import com.arwora.ui.theme.ThemeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyPolicyAndSplashTest {

    @Test
    fun splashAnimationDurationFitsSmoothTargetRange() {
        assertTrue(
            "Splash logo fade/scale animation should stay between 400ms and 700ms",
            SPLASH_LOGO_ANIM_DURATION_MS in 400..700
        )
        assertTrue(
            "Splash brand text animation should stay between 600ms and 800ms",
            SPLASH_TEXT_ANIM_DURATION_MS in 600..800
        )
        assertTrue(
            "Post-animation hold should stay brief to avoid startup lag",
            SPLASH_POST_ANIM_HOLD_MS in 100L..500L
        )
        assertEquals("splash", Screen.Splash.route)
    }

    @Test
    fun darkStartupColorsMatchSeamlessDarkSurface() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedDarkInt = android.graphics.Color.parseColor("#0B0B0F")
        assertEquals(expectedDarkInt, ContextCompat.getColor(context, R.color.splash_bg))
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
