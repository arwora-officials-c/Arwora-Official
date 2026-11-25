package com.arwora

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class AutoVideoScrollTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowSettings.setCanDrawOverlays(false)
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        AutoVideoScrollManager.init(context)
        AutoVideoScrollManager.setAccessibilityServiceRunning(false)
        AutoVideoScrollManager.setServiceActive(false)
        AutoVideoScrollManager.setMode(AutoScrollMode.TIME_BASED)
        AutoVideoScrollManager.setIntervalSeconds(AutoVideoScrollManager.DEFAULT_INTERVAL_SECONDS)
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ShadowSettings.setCanDrawOverlays(false)
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        AutoVideoScrollManager.setAccessibilityServiceRunning(false)
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
        assertEquals(0, AutoVideoScrollManager.countdownSecondsRemaining.value)
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
        assertEquals(14 to 15, VideoCompletionDetector.parseElapsedAndTotalSeconds("0:14 of 0:15"))
        assertEquals(14 to 15, VideoCompletionDetector.parseElapsedAndTotalSeconds("14s / 15s"))
        assertNull(VideoCompletionDetector.parseElapsedAndTotalSeconds("1 / 5"))
        assertNull(VideoCompletionDetector.parseElapsedAndTotalSeconds("no timestamp here"))

        // Near-end timestamp
        assertTrue(
            VideoCompletionDetector.isPlaybackCompletedOrLooped(
                previousRatio = 0.5f,
                current = VideoProgressSnapshot(elapsedSeconds = 19, totalSeconds = 20)
            )
        )
        // Elapsed timestamp wrap/loop reset
        assertTrue(
            VideoCompletionDetector.isPlaybackCompletedOrLooped(
                previousRatio = 0.5f,
                current = VideoProgressSnapshot(elapsedSeconds = 1, totalSeconds = 20),
                previousElapsedSeconds = 18
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
        // Long captions containing "replay" should not falsely trigger replay cue
        assertFalse(VideoCompletionDetector.hasReplayCue("Check out this awesome video and wait for the replay in the second half of the clip!"))
        assertTrue(VideoCompletionDetector.hasReplayCue("Watch again"))

        // Mid-playback should not trigger
        assertFalse(
            VideoCompletionDetector.isPlaybackCompletedOrLooped(
                previousRatio = 0.35f,
                current = VideoProgressSnapshot(ratio = 0.42f, elapsedSeconds = 8, totalSeconds = 20),
                previousElapsedSeconds = 7
            )
        )
    }

    @Test
    fun accessibilityServiceComputesBottomCenterToTopCenterSwipeAndCleansUpOnUnbind() {
        val coords = AutoScrollAccessibilityService.computeSwipeCoordinates(1080, 2400)
        assertEquals(540f, coords.startX, 0.1f)
        assertEquals(540f, coords.endX, 0.1f)
        assertTrue(coords.startY > coords.endY)
        assertEquals(2400f * 0.78f, coords.startY, 0.5f)
        assertEquals(2400f * 0.22f, coords.endY, 0.5f)

        val controller = Robolectric.buildService(AutoScrollAccessibilityService::class.java).create()
        val service = controller.get()
        service.performSwipeUpGesture()
        service.onUnbind(Intent())
        assertFalse(AutoScrollAccessibilityService.isRunning)
        controller.destroy()
        assertFalse(AutoScrollAccessibilityService.isRunning)
    }

    @Test
    fun completionBasedEvaluationDoesNotTriggerUntilPlaybackEndsOrLoops() {
        val controller = Robolectric.buildService(AutoScrollAccessibilityService::class.java).create()
        val service = controller.get()
        AutoVideoScrollManager.setMode(AutoScrollMode.COMPLETION_BASED)
        AutoVideoScrollManager.setServiceActive(true)

        // Simulate a 45-second video progressing slowly without finishing (25+ seconds of frames)
        for (sec in 1..25) {
            val triggered = service.evaluateCompletionSnapshotForTest(
                VideoProgressSnapshot(
                    ratio = sec / 45f,
                    elapsedSeconds = sec,
                    totalSeconds = 45
                )
            )
            assertFalse("Completion-Based mode must NOT trigger at ${sec}s of a 45s video", triggered)
        }
        assertEquals(0, AutoVideoScrollManager.scrollCount.value)
        assertEquals(0, AutoVideoScrollManager.countdownSecondsRemaining.value)

        // Advance to high progress then loop reset to 0:01
        assertFalse(
            service.evaluateCompletionSnapshotForTest(
                VideoProgressSnapshot(ratio = 0.85f, elapsedSeconds = 38, totalSeconds = 45)
            )
        )
        assertTrue(
            service.evaluateCompletionSnapshotForTest(
                VideoProgressSnapshot(ratio = 0.02f, elapsedSeconds = 1, totalSeconds = 45)
            )
        )

        controller.destroy()
    }

    @Test
    fun permissionChecksRejectStaleSecureSettingWhenMasterAccessibilityIsOff() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val component = ComponentName(context.packageName, AutoScrollAccessibilityService::class.java.name).flattenToString()

        // Even if ENABLED_ACCESSIBILITY_SERVICES has our component, master ACCESSIBILITY_ENABLED=0 must return false
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, component)
        assertFalse(AutoVideoScrollManager.isAccessibilityServiceEnabled(context))
        assertFalse(AutoVideoScrollManager.canDrawOverlays(context))

        // Enabling master accessibility + exact component returns true
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        assertTrue(AutoVideoScrollManager.isAccessibilityServiceEnabled(context))

        // Granting overlay permission returns true
        ShadowSettings.setCanDrawOverlays(true)
        assertTrue(AutoVideoScrollManager.canDrawOverlays(context))
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
    fun socialAutoVideoScrollCardRendersAccuratePermissionChipsSwitchesModesAndUpdatesInterval() {
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

        // Default ungranted permissions must show Enable Accessibility & Allow Overlay (never Ready)
        composeTestRule.onNodeWithTag("auto_scroll_accessibility_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auto_scroll_overlay_btn").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enable Accessibility").assertIsDisplayed()
        composeTestRule.onNodeWithText("Allow Overlay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Accessibility Ready").assertDoesNotExist()
        composeTestRule.onNodeWithText("Overlay Ready").assertDoesNotExist()
        composeTestRule.onNodeWithText("Setup Needed").assertIsDisplayed()

        // Change interval preset to 30s
        composeTestRule.onNodeWithTag("auto_scroll_interval_chip_30").performClick()
        composeTestRule.waitForIdle()
        assertEquals(30, AutoVideoScrollManager.intervalSeconds.value)
        composeTestRule.onNodeWithText("Time-Based (30s)").assertIsDisplayed()

        // Switch to Completion-Based mode
        composeTestRule.onNodeWithTag("auto_scroll_mode_completion").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AutoScrollMode.COMPLETION_BASED, AutoVideoScrollManager.mode.value)

        // Clicking Allow Overlay prompts user for Display Over Other Apps permission
        composeTestRule.onNodeWithTag("auto_scroll_overlay_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Display Over Other Apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()

        // When permissions aren't granted in Robolectric, toggling switch displays guidance dialog cleanly
        composeTestRule.onNodeWithTag("auto_scroll_master_switch").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Display Over Other Apps").assertIsDisplayed()
    }

    @Test
    fun socialAutoVideoScrollCardShowsReadyChipsOnlyWhenStrictlyGranted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val component = ComponentName(context.packageName, AutoScrollAccessibilityService::class.java.name).flattenToString()
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, component)
        ShadowSettings.setCanDrawOverlays(true)

        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                AutoVideoScrollCard(onToastMessage = {})
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Accessibility Ready").assertIsDisplayed()
        composeTestRule.onNodeWithText("Overlay Ready").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auto_scroll_accessibility_helper").assertDoesNotExist()
    }

    @Test
    fun enableAccessibilityButtonAndHelperOpenOnboardingPreviewDialog() {
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                AutoVideoScrollCard(onToastMessage = {})
            }
        }
        composeTestRule.waitForIdle()

        // Inline onboarding helper card is visible while accessibility is disabled
        composeTestRule.onNodeWithTag("auto_scroll_accessibility_helper").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auto_scroll_accessibility_guide_btn").assertIsDisplayed()

        // Clicking Enable Accessibility opens the visual settings preview dialog
        composeTestRule.onNodeWithTag("auto_scroll_accessibility_btn").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("accessibility_dialog_status_pill").assertIsDisplayed()
        composeTestRule.onNodeWithText("Permission Required").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accessibility_settings_preview_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings › Accessibility › Installed apps").assertIsDisplayed()
        composeTestRule.onNodeWithText("Auto Video Scroll Service").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accessibility_preview_toggle_badge").assertIsDisplayed()
        composeTestRule.onNodeWithText("ON").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accessibility_open_settings_btn").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Accessibility Settings").assertIsDisplayed()

        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("accessibility_settings_preview_card").assertDoesNotExist()

        // Clicking View Guide from inline helper also opens the preview dialog
        composeTestRule.onNodeWithTag("auto_scroll_accessibility_guide_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("accessibility_settings_preview_card").assertIsDisplayed()
    }

    @Test
    fun resumingFromSettingsInstantlyUpdatesAccessibilityAndReadyBadges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testLifecycleOwner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this).apply {
                currentState = Lifecycle.State.RESUMED
            }
            override val lifecycle: Lifecycle get() = registry
        }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides testLifecycleOwner) {
                NameGenProTheme(darkTheme = true) {
                    AutoVideoScrollCard(onToastMessage = {})
                }
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Enable Accessibility").assertIsDisplayed()
        composeTestRule.onNodeWithText("Allow Overlay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Setup Needed").assertIsDisplayed()
        composeTestRule.onNodeWithTag("auto_scroll_accessibility_helper").assertIsDisplayed()

        // Simulate user returning from Android Accessibility & Overlay settings after enabling both
        composeTestRule.runOnUiThread {
            testLifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            val component = ComponentName(
                context.packageName,
                AutoScrollAccessibilityService::class.java.name
            ).flattenToString()
            Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, component)
            ShadowSettings.setCanDrawOverlays(true)
            testLifecycleOwner.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Accessibility Ready").assertIsDisplayed()
        composeTestRule.onNodeWithText("Overlay Ready").assertIsDisplayed()
        composeTestRule.onNodeWithText("Ready").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enable Accessibility").assertDoesNotExist()
        composeTestRule.onNodeWithText("Setup Needed").assertDoesNotExist()
        composeTestRule.onNodeWithTag("auto_scroll_accessibility_helper").assertDoesNotExist()
    }

    @Test
    fun autoVideoScrollExecutesCompletionTriggerAcrossAllSupportedFeeds() {
        val controller = Robolectric.buildService(AutoScrollAccessibilityService::class.java).create()
        val service = controller.get()
        AutoVideoScrollManager.setMode(AutoScrollMode.COMPLETION_BASED)
        AutoVideoScrollManager.setServiceActive(true)

        val targetFeeds = listOf(
            "com.zhiliaoapp.musically" to "TikTok",
            "com.instagram.android" to "Instagram Reels",
            "com.facebook.katana" to "Facebook Reels",
            "com.google.android.youtube" to "YouTube Shorts"
        )

        targetFeeds.forEach { (pkg, expectedLabel) ->
            val midFrameTriggered = service.evaluateCompletionSnapshotForTest(
                snapshot = VideoProgressSnapshot(ratio = 0.52f, elapsedSeconds = 10, totalSeconds = 20),
                targetPackageName = pkg,
                dispatchGesture = false
            )
            assertFalse("$expectedLabel mid-playback should not trigger early scroll", midFrameTriggered)
            assertEquals(expectedLabel, AutoVideoScrollManager.detectedPlatform.value)

            val endFrameTriggered = service.evaluateCompletionSnapshotForTest(
                snapshot = VideoProgressSnapshot(ratio = 0.98f, elapsedSeconds = 20, totalSeconds = 20),
                targetPackageName = pkg,
                dispatchGesture = false
            )
            assertTrue("$expectedLabel end-of-playback should trigger scroll", endFrameTriggered)
            assertEquals(
                "$expectedLabel • Playback ended • Scrolling next",
                AutoVideoScrollManager.statusDetail.value
            )
        }

        // Verify swipe gesture construction & dispatch path runs cleanly without crashing
        service.performSwipeUpGesture()
        controller.destroy()
    }
}
