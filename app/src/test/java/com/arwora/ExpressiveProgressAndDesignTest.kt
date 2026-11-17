package com.arwora

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.arwora.ui.components.AppCard
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaPrimaryPillButton
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.ui.components.ExpressiveCompactLoadingIndicator
import com.arwora.ui.components.ExpressiveLoadingIndicator
import com.arwora.ui.components.ExpressiveWavyLinearProgressIndicator
import com.arwora.ui.components.GoogleStyleLinearLoadingCard
import com.arwora.ui.components.GoogleStyleLoadingView
import com.arwora.ui.screens.CentralizedControlPanelHeroCard
import com.arwora.ui.theme.NameGenProTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpressiveProgressAndDesignTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `expressive wavy linear progress exposes determinate and indeterminate semantics`() {
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    ExpressiveWavyLinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wavy_determinate"),
                        progress = 0.65f
                    )
                    ExpressiveWavyLinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("wavy_indeterminate"),
                        progress = null
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("wavy_determinate")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(current = 0.65f, range = 0f..1f)
                )
            )

        composeTestRule.onNodeWithTag("wavy_indeterminate")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate
                )
            )
    }

    @Test
    fun `expressive circular loading indicators render with indeterminate semantics`() {
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = false, whiteTheme = true) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ExpressiveLoadingIndicator(
                        modifier = Modifier.testTag("expressive_circle_full"),
                        showContainer = true
                    )
                    ExpressiveCompactLoadingIndicator(
                        modifier = Modifier.testTag("expressive_circle_compact"),
                        size = 20.dp
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("expressive_circle_full")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate
                )
            )

        composeTestRule.onNodeWithTag("expressive_circle_compact")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo.Indeterminate
                )
            )
    }

    @Test
    fun `loading cards and design system components render across dark light and white themes`() {
        val themeConfigs = listOf(
            true to false,  // Dark
            false to false, // Light
            false to true   // White
        )
        val activeThemeIndex = mutableIntStateOf(0)
        var clickCount = 0

        composeTestRule.setContent {
            val (dark, white) = themeConfigs[activeThemeIndex.intValue]
            NameGenProTheme(darkTheme = dark, whiteTheme = white) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    GoogleStyleLinearLoadingCard(
                        title = "System Audit ${activeThemeIndex.intValue}",
                        subtitle = "Scanning modules",
                        icon = Icons.Default.Security,
                        progress = 0.65f
                    )
                    GoogleStyleLoadingView(
                        title = "Loading Apps",
                        scrollable = false,
                        skeletonRowCount = 2
                    )
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("design_app_card_${activeThemeIndex.intValue}")
                    ) {
                        ArworaTonalIconBadge(icon = Icons.Default.Wifi)
                        ArworaPillBadge(text = "Online", showDot = true)
                        Text("Card Content")
                    }
                    ArworaPrimaryPillButton(
                        text = "Generate Name",
                        onClick = { clickCount++ },
                        modifier = Modifier.testTag("pill_cta_${activeThemeIndex.intValue}")
                    )
                    CentralizedControlPanelHeroCard()
                }
            }
        }

        themeConfigs.indices.forEach { idx ->
            composeTestRule.runOnIdle { activeThemeIndex.intValue = idx }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText("System Audit $idx")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("65%")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithTag("google_style_loading_view")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithTag("design_app_card_$idx")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithTag("centralized_control_panel_card")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithTag("pill_cta_$idx")
                .performScrollTo()
                .performClick()
            assertTrue(clickCount == idx + 1)
        }
    }
}
