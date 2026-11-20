package com.arwora

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.arwora.ui.screens.FakeGpsLocationSpooferCard
import com.arwora.ui.theme.NameGenProTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class FakeGpsCardCollapsibleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `fake gps card starts collapsed shows IDLE header and toggles cleanly while preserving active state`() {
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                var expanded by rememberSaveable { mutableStateOf(false) }
                var mockActive by rememberSaveable { mutableStateOf(false) }
                var lat by rememberSaveable { mutableStateOf("37.7749") }
                var lng by rememberSaveable { mutableStateOf("-122.4194") }
                var preset by rememberSaveable { mutableStateOf("San Francisco") }

                FakeGpsLocationSpooferCard(
                    isExpanded = expanded,
                    onToggleExpand = { expanded = !expanded },
                    isMockActive = mockActive,
                    savedLat = 37.7749,
                    savedLng = -122.4194,
                    mockUpdateCount = if (mockActive) 3 else 0,
                    mockError = null,
                    currentPresetName = preset,
                    inputLat = lat,
                    inputLng = lng,
                    onInputLatChange = { lat = it },
                    onInputLngChange = { lng = it },
                    onSelectPreset = { preset = it.name },
                    onOpenMapPicker = {},
                    onShowSetupHelp = {},
                    onStartMockGps = { mockActive = true },
                    onStopMockGps = { mockActive = false },
                    onOpenExternalMap = {},
                    onOpenDeveloperSettings = {}
                )
            }
        }

        // Default collapsed state: header & IDLE badge visible, inner content hidden
        composeTestRule.onNodeWithTag("fake_gps_card_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fake_gps_card_chevron", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Fake GPS / Location Spoofer").assertIsDisplayed()
        composeTestRule.onNodeWithText("IDLE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mock Location Controller").assertDoesNotExist()
        composeTestRule.onNodeWithText("Start Mock GPS").assertDoesNotExist()

        // Tap header -> smoothly expands to show Mock Location Controller, map picker, presets, fields, and start CTA
        composeTestRule.onNodeWithTag("fake_gps_card_header").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fake_gps_card_content").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mock Location Controller").assertIsDisplayed()
        composeTestRule.onNodeWithText("Interactive Map Picker").assertIsDisplayed()
        composeTestRule.onNodeWithText("Worldwide Popular Presets").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Mock GPS").assertIsDisplayed()

        // Starting Mock GPS preserves expanded state and updates badge to ACTIVE
        composeTestRule.onNodeWithTag("fake_gps_start_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ACTIVE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stop Mock GPS").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fake_gps_card_content").assertIsDisplayed()

        // Tapping header again collapses the inner container cleanly while retaining ACTIVE badge
        composeTestRule.onNodeWithTag("fake_gps_card_header").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ACTIVE").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mock Location Controller").assertDoesNotExist()
    }
}
