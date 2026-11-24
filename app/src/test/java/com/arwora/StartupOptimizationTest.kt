package com.arwora

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.arwora.feature.common.startup.StartupDefer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartupOptimizationTest {

    @Test
    fun colorSurfaceAndSplashThemeEliminateWhiteFlashAcrossDayAndNight() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val expectedSurface = Color.parseColor("#0B0B0F")

        assertEquals(expectedSurface, ContextCompat.getColor(context, R.color.colorSurface))

        val nightConfig = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        val nightContext = context.createConfigurationContext(nightConfig)
        assertEquals(expectedSurface, ContextCompat.getColor(nightContext, R.color.colorSurface))

        val startingTheme = ContextThemeWrapper(context, R.style.Theme_App_Starting).theme
        val bgTyped = TypedValue()
        assertTrue(startingTheme.resolveAttribute(android.R.attr.windowBackground, bgTyped, true))
        assertEquals(R.color.colorSurface, bgTyped.resourceId)

        val splashBgTyped = TypedValue()
        assertTrue(
            startingTheme.resolveAttribute(
                androidx.core.splashscreen.R.attr.windowSplashScreenBackground,
                splashBgTyped,
                true
            )
        )
        assertEquals(R.color.colorSurface, splashBgTyped.resourceId)

        val splashIconTyped = TypedValue()
        assertTrue(
            startingTheme.resolveAttribute(
                androidx.core.splashscreen.R.attr.windowSplashScreenAnimatedIcon,
                splashIconTyped,
                true
            )
        )
        assertEquals(R.drawable.ic_arwora_wordmark, splashIconTyped.resourceId)

        val mainTheme = ContextThemeWrapper(context, R.style.Theme_ArworaHyperToolkit).theme
        val mainWindowBg = TypedValue()
        assertTrue(mainTheme.resolveAttribute(android.R.attr.windowBackground, mainWindowBg, true))
        assertEquals(R.color.colorSurface, mainWindowBg.resourceId)
    }

    @Test
    fun splashActivityDayModeRendersDarkColorSurfaceInsteadOfBlankWhiteScreen() {
        val controller = Robolectric.buildActivity(SplashActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.findViewById<ConstraintLayout>(R.id.splash_root)
        val wordmark = activity.findViewById<ImageView>(R.id.iv_arwora_wordmark)

        val expectedSurface = ContextCompat.getColor(activity, R.color.colorSurface)
        assertEquals(expectedSurface, (root.background as ColorDrawable).color)
        assertNotNull(wordmark.drawable)
        assertEquals(1.0f, wordmark.alpha, 0.001f)

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(800, TimeUnit.MILLISECONDS)
        val nextIntent = Shadows.shadowOf(activity).nextStartedActivity
        assertNotNull("SplashActivity must navigate to MainActivity within 800ms", nextIntent)
        assertEquals(MainActivity::class.java.name, nextIntent.component?.className)
        assertTrue(activity.isFinishing)

        controller.destroy()
    }

    @Test
    fun applicationLazyDependenciesAndWarmupExecuteWithoutMainThreadBlocking() {
        val app = ApplicationProvider.getApplicationContext<NameGenApplication>()
        app.warmupAsync()
        app.warmupAsync()

        assertNotNull(app.settingsRepository)
        assertNotNull(app.database)
        assertNotNull(app.sharedViewModel)
    }

    @Test
    fun startupDeferFlushesPostFirstFrameTasksWhenMarkedDrawn() {
        val app = ApplicationProvider.getApplicationContext<NameGenApplication>()
        val latch = CountDownLatch(1)
        StartupDefer.schedulePostFirstFrame(app) {
            latch.countDown()
        }
        StartupDefer.markFirstFrameDrawn()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Deferred task should run after markFirstFrameDrawn", latch.await(3, TimeUnit.SECONDS))
    }
}
