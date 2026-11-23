package com.arwora

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.arwora.service.AutoScrollAccessibilityService
import com.arwora.service.AutoScrollOverlayService
import com.arwora.ui.components.AutoVideoScrollCard
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.AutoScrollMode
import com.arwora.utils.AutoVideoScrollManager
import com.arwora.utils.VideoCompletionDetector
import com.arwora.utils.VideoProgressSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class AutoVideoScrollTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AutoVideoScrollManager.init(context)
        AutoVideoScrollManager.setServiceActive(false)
        AutoVideoScrollManager.setMode(AutoScrollMode.TIME_BASED)
        AutoVideoScrollManager.setIntervalSeconds(AutoVideoScrollManager.DEFAULT_INTERVAL_SECONDS)
    }

    @After
    fun tearDown() {
        AutoVideoScrollManager.setServiceActive(false)
    }

    @Test
    fun managerHandlesModesIntervalsPauseAndScrollCounting() {
        assertEquals(AutoScrollMode.TIME_BASED, AutoVideoScrollManager.mode.value)
        assertEquals(20, AutoVideoScrollManager.intervalSeconds.value)
        assertFalse(AutoVideoScrollManager.isServiceActive.value)

        AutoVideoScrollManager.setIntervalSeconds(2) // Should clamp to 5
        assertEquals(5, AutoVideoScrollManager.intervalSeconds.value)

        AutoVideoScrollManager.setIntervalSeconds(45)
        assertEquals(45, AutoVideoScrollManager.intervalSeconds.value)

        AutoVideoScrollManager.setServiceActive(true)
        assertTrue(AutoVideoScrollManager.isServiceActive.value)
        assertFalse(AutoVideoScrollManager.isPaused.value)

        AutoVideoScrollManager.togglePaused()
        assertTrue(AutoVideoScrollManager.isPaused.value)
        assertEquals("Paused", AutoVideoScrollManager.statusDetail.value)

        AutoVideoScrollManager.incrementScrollCount()
        assertEquals(1, AutoVideoScrollManager.scrollCount.value)

        AutoVideoScrollManager.setMode(AutoScrollMode.COMPLETION_BASED)
        assertEquals(AutoScrollMode.COMPLETION_BASED, AutoVideoScrollManager.mode.value)
    }

    @Test
    fun videoCompletionDetectorRecognizesPlatformsTimestampsAndLoopResets() {
        assertEquals("TikTok", VideoCompletionDetector.recognizePlatformLabel("com.zhiliaoapp.musically"))
        assertEquals("Instagram Reels", VideoCompletionDetector.recognizePlatformLabel("com.instagram.android"))
        assertEquals("Facebook Reels", VideoCompletionDetector.recognizePlatformLabel("com.facebook.katana"))
        assertEquals("YouTube Shorts", VideoCompletionDetector.recognizePlatformLabel("com.google.android.youtube"))
        assertNull(VideoCompletionDetector.recognizePlatformLabel("com.example.unknown"))

        assertEquals(14 to 15, VideoCompletionDetector.parseElapsedAndTotalSeconds("0:14 / 0:15"))
        assertEquals(62 to 65, VideoCompletionDetector.parseElapsedAndTotalSeconds("01:02 / 01:05"))
        assertNull(VideoCompletionDetector.parseElapsedAndTotalSeconds("no timestamp here"))

        // Near-end timestamp
        assertTrue(
            VideoCompletionDetector.isPlaybackCompletedOrLooped(
                previousRatio = 0.5f,
                current = VideoProgressSnapshot(elapsedSeconds = 19, totalSeconds = 20)
            )
        )
        // High seekbar ratio
        assertTrue(
            VideoCompletionDetector.isPlaybackCompletedOrLooped(
                previousRatio = 0.9f,
                current = VideoProgressSnapshot(ratio = 0.97f)
            )
        )
        // Loop jump back from >=72% to <=15%
        assertTrue(
            VideoCompletionDetector.isPlaybackCompletedOrLooped(
                previousRatio = 0.84f,
                current = VideoProgressSnapshot(ratio = 0.04f)
            )
        )
        // Replay cue text
        assertTrue(
            VideoCompletionDetector.isPlaybackCompletedOrLooped(
                previousRatio = 0.3f,
                current = VideoProgressSnapshot(hasReplayCue = true)
            )
        )
        // Mid-playback should not trigger
        assertFalse(
            VideoCompletionDetector.isPlaybackCompletedOrLooped(
                previousRatio = 0.35f,
                current = VideoProgressSnapshot(ratio = 0.42f, elapsedSeconds = 8, totalSeconds = 20)
            )
        )
    }

    @Test
    fun accessibilityServiceComputesBottomCenterToTopCenterSwipeAndRunsCleanly() {
        val coords = AutoScrollAccessibilityService.computeSwipeCoordinates(1080, 2400)
        assertEquals(540f, coords.startX, 0.1f)
        assertEquals(540f, coords.endX, 0.1f)
        assertTrue(coords.startY > coords.endY)
        assertEquals(2400f * 0.78f, coords.startY, 0.5f)
        assertEquals(2400f * 0.22f, coords.endY, 0.5f)

        val controller = Robolectric.buildService(AutoScrollAccessibilityService::class.java).create()
        controller.get().performSwipeUpGesture()
        controller.destroy()
        assertFalse(AutoScrollAccessibilityService.isRunning)
    }

    @Test
    fun overlayServiceStartsHandlesPauseStopAndCleansUpOnSdk35() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = Robolectric.buildService(AutoScrollOverlayService::class.java)
            .create()
            .withIntent(Intent(context, AutoScrollOverlayService::class.java).setAction(AutoScrollOverlayService.ACTION_START_SCROLL_OVERLAY))
            .startCommand(0, 0)

        assertTrue(AutoScrollOverlayService.isRunning)
        assertTrue(AutoVideoScrollManager.isServiceActive.value)
        assertFalse(AutoVideoScrollManager.isPaused.value)

        controller.withIntent(Intent(context, AutoScrollOverlayService::class.java).setAction(AutoScrollOverlayService.ACTION_TOGGLE_PAUSE))
            .startCommand(0, 1)
        assertTrue(AutoVideoScrollManager.isPaused.value)

        controller.withIntent(Intent(context, AutoScrollOverlayService::class.java).setAction(AutoScrollOverlayService.ACTION_STOP_SCROLL_OVERLAY))
            .startCommand(0, 2)
        controller.destroy()

        assertFalse(AutoScrollOverlayService.isRunning)
        assertFalse(AutoVideoScrollManager.isServiceActive.value)
    }

    @Test
    fun socialAutoVideoScrollCardRendersControlsSwitchesModesAndUpdatesInterval() {
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                AutoVideoScrollCard(onToastMessage = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("auto_scroll_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Auto Video Scroll").assertIsDisplayed()
        composeTestRule.onNodeWithText("Auto-scroll TikTok, Reels & Shorts").assertIsDisplayed()
        composeTestRule.onNodeWithText("Idle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Time-Based (20s)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Completion-Based").assertIsDisplayed()

        // Change interval preset to 30s
        composeTestRule.onNodeWithTag("auto_scroll_interval_chip_30").performClick()
        composeTestRule.waitForIdle()
        assertEquals(30, AutoVideoScrollManager.intervalSeconds.value)
        composeTestRule.onNodeWithText("Time-Based (30s)").assertIsDisplayed()

        // Switch to Completion-Based mode
        composeTestRule.onNodeWithTag("auto_scroll_mode_completion").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AutoScrollMode.COMPLETION_BASED, AutoVideoScrollManager.mode.value)

        // When permissions aren't granted in Robolectric, toggling switch displays guidance dialog cleanly
        composeTestRule.onNodeWithTag("auto_scroll_master_switch").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Display Over Other Apps").assertIsDisplayed()
    }
}
