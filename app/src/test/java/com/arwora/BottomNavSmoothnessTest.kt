package com.arwora

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import com.arwora.ui.components.CustomBottomNavigationBar
import com.arwora.ui.components.bottomNavItems
import com.arwora.ui.theme.NameGenProTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h915dp-xxhdpi")
class BottomNavSmoothnessTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `bottom navigation bar displays pill indicators and switches all five tabs`() {
        var latestSelectedIndex = 0
        var latestSelectedRoute = bottomNavItems[0].route

        composeTestRule.setContent {
            NameGenProTheme {
                val navController = rememberNavController()
                var selectedTab by remember { mutableIntStateOf(0) }
                CustomBottomNavigationBar(
                    navController = navController,
                    currentDestination = null,
                    isDarkTheme = true,
                    selectedTabIndex = selectedTab,
                    onTabSelected = { index, item ->
                        selectedTab = index
                        latestSelectedIndex = index
                        latestSelectedRoute = item.route
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        bottomNavItems.forEachIndexed { index, item ->
            val key = item.title.lowercase()
            composeTestRule.onNodeWithTag("nav_item_$key").assertIsDisplayed()
            composeTestRule.onNodeWithTag("nav_pill_$key", useUnmergedTree = true).assertIsDisplayed()
            composeTestRule.onNodeWithText(item.title).assertIsDisplayed()

            composeTestRule.onNodeWithTag("nav_item_$key").performClick()
            composeTestRule.waitForIdle()

            assertEquals(index, latestSelectedIndex)
            assertEquals(item.route, latestSelectedRoute)
        }
    }
}
