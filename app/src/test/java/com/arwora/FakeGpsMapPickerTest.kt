package com.arwora

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.navigation.compose.rememberNavController
import com.arwora.ui.screens.FakeGpsMapScreen
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.FakeGpsManager
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class FakeGpsMapPickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `redesigned fake gps map picker renders status pill search bar presets segmented controls and bottom sheet`() {
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                val navController = rememberNavController()
                FakeGpsMapScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Fake GPS Map Picker").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_picker_status_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_picker_search_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search city, address, or landmark...").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_picker_presets_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_picker_segmented_controls").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Select Map Type").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Zoom In").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Zoom Out").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Compass & Fine Tune D-Pad").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Recenter My Location").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_picker_action_fab").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_picker_bottom_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_picker_spoof_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("map_picker_dev_settings_button").assertIsDisplayed()

        // Selecting a preset chip updates the marker & bottom sheet target location cleanly
        val targetIndex = FakeGpsManager.popularPresets.lastIndex
        val targetPreset = FakeGpsManager.popularPresets[targetIndex]
        composeTestRule.onNodeWithTag("map_picker_presets_row").performScrollToIndex(targetIndex)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("map_preset_chip_${targetPreset.name}").performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onAllNodesWithText("${targetPreset.name}, ${targetPreset.country}", useUnmergedTree = true)
            .onFirst()
            .assertIsDisplayed()
    }
}
