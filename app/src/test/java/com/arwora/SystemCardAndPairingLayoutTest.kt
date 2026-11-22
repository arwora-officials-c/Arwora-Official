package com.arwora

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.compose.ui.unit.dp
import com.arwora.ui.components.WirelessPairingWizard
import com.arwora.ui.screens.FakeGpsLocationSpooferCard
import com.arwora.ui.theme.NameGenProTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemCardAndPairingLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun collapsedFakeGpsCardDoesNotClipIdleBadgeOrChevronOnCompactWidth() {
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                Box(modifier = Modifier.width(328.dp)) {
                    FakeGpsLocationSpooferCard(
                        isExpanded = false,
                        onToggleExpand = {},
                        isMockActive = false,
                        savedLat = 40.7128,
                        savedLng = -74.0060,
                        mockUpdateCount = 0,
                        mockError = null,
                        currentPresetName = "New York",
                        inputLat = "40.7128",
                        inputLng = "-74.0060",
                        onInputLatChange = {},
                        onInputLngChange = {},
                        onSelectPreset = {},
                        onOpenMapPicker = {},
                        onShowSetupHelp = {},
                        onStartMockGps = {},
                        onStopMockGps = {},
                        onOpenExternalMap = {},
                        onOpenDeveloperSettings = {}
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        val headerNode = composeTestRule.onNodeWithTag("fake_gps_card_header", useUnmergedTree = true).assertIsDisplayed()
        val badgeNode = composeTestRule.onNodeWithTag("fake_gps_card_badge", useUnmergedTree = true).assertIsDisplayed()
        val chevronNode = composeTestRule.onNodeWithTag("fake_gps_card_chevron", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("IDLE", useUnmergedTree = true).assertIsDisplayed()

        val headerBounds = headerNode.getUnclippedBoundsInRoot()
        val badgeBounds = badgeNode.getUnclippedBoundsInRoot()
        val chevronBounds = chevronNode.getUnclippedBoundsInRoot()

        assertTrue("Badge must fit strictly left of chevron", badgeBounds.right <= chevronBounds.left)
        assertTrue(
            "Chevron must keep right inset inside card header without clipping",
            chevronBounds.right <= headerBounds.right - 12.dp
        )
        assertTrue(
            "Badge must stay vertically within header bounds",
            badgeBounds.top >= headerBounds.top && badgeBounds.bottom <= headerBounds.bottom
        )
        assertTrue(
            "Chevron must stay vertically within header bounds",
            chevronBounds.top >= headerBounds.top && chevronBounds.bottom <= headerBounds.bottom
        )
    }

    @Test
    fun manualInputFallbackAlignsCodeAndPortFieldsWithExpectedWeightRatio() {
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                Column(
                    modifier = Modifier
                        .width(360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    WirelessPairingWizard(onPairingSuccess = {})
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pairing_mode_tab_notification_manual")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        val codeField = composeTestRule.onNodeWithTag("manual_pairing_code_input")
            .performScrollTo()
            .assertIsDisplayed()
        val portField = composeTestRule.onNodeWithTag("manual_pairing_port_input")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_pairing_host_input")
            .performScrollTo()
            .assertIsDisplayed()

        val codeBounds = codeField.getUnclippedBoundsInRoot()
        val portBounds = portField.getUnclippedBoundsInRoot()

        val codeWidth = codeBounds.right - codeBounds.left
        val portWidth = portBounds.right - portBounds.left

        assertTrue("6-Digit Code field must sit left of Port field", codeBounds.right < portBounds.left)
        assertTrue(
            "6-Digit Code and Port fields must share the same top alignment (diff=${codeBounds.top - portBounds.top})",
            kotlin.math.abs((codeBounds.top - portBounds.top).value) <= 1f
        )
        assertTrue(
            "6-Digit Code and Port fields must share the same bottom alignment (diff=${codeBounds.bottom - portBounds.bottom})",
            kotlin.math.abs((codeBounds.bottom - portBounds.bottom).value) <= 1f
        )
        assertTrue(
            "6-Digit Code field ($codeWidth) should be substantially wider (~1.5x) than Port field ($portWidth)",
            codeWidth > portWidth * 1.35f
        )
    }
}
