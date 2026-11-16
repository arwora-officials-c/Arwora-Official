package com.arwora

import com.arwora.ui.navigation.Screen
import com.arwora.ui.screens.PrivacyPolicyContent
import com.arwora.ui.screens.SPLASH_LOGO_ANIM_DURATION_MS
import com.arwora.ui.screens.SPLASH_POST_ANIM_HOLD_MS
import com.arwora.ui.screens.SPLASH_TEXT_ANIM_DURATION_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
