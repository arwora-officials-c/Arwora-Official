package com.arwora

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.arwora.data.datastore.SettingsRepository
import com.arwora.data.datastore.dataStore
import com.arwora.data.db.AppDatabase
import com.arwora.data.repository.FavoriteRepository
import com.arwora.data.repository.HistoryRepository
import com.arwora.data.repository.SavedTextRepository
import com.arwora.ui.MainViewModel
import com.arwora.ui.screens.GenerateDashboardScreen
import com.arwora.ui.screens.allGlobalTools
import com.arwora.ui.theme.NameGenProTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp")
class GenerateDashboardRemovalTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun generateDashboardExcludesUsernameBuilderAndFakeDataGeneratorWhileKeepingRemainingTools() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val viewModel = MainViewModel(
            HistoryRepository(db.historyDao()),
            FavoriteRepository(db.favoriteDao()),
            SettingsRepository(context.dataStore),
            SavedTextRepository(db.savedTextDao())
        )

        try {
            composeTestRule.setContent {
                NameGenProTheme(darkTheme = true) {
                    val navController = rememberNavController()
                    GenerateDashboardScreen(navController = navController, viewModel = viewModel)
                }
            }
            composeTestRule.waitForIdle()

            // Verify removed tools are absent from Home global search catalog too
            assertFalse(allGlobalTools.any { it.title.contains("Username Builder", ignoreCase = true) })
            assertFalse(allGlobalTools.any { it.title.contains("Fake Data", ignoreCase = true) })

            val grid = composeTestRule.onNodeWithTag("generate_dashboard_grid")

            // Core cards remain visible
            composeTestRule.onNodeWithText("Generation History").assertIsDisplayed()
            composeTestRule.onNodeWithTag("generate_compressor_hub_card").assertIsDisplayed()

            // Verify removed tools do not appear before scrolling
            assertEquals(0, composeTestRule.onAllNodesWithText("Username Builder", substring = true).fetchSemanticsNodes().size)
            assertEquals(0, composeTestRule.onAllNodesWithText("Fake Data Generator", substring = true).fetchSemanticsNodes().size)

            // Scroll through all remaining tools and verify they still render intact
            grid.performScrollToNode(hasText("Stylish Text Generator"))
            composeTestRule.onNodeWithText("Stylish Text Generator").assertIsDisplayed()

            grid.performScrollToNode(hasText("Name Mixer"))
            composeTestRule.onNodeWithText("Name Mixer").assertIsDisplayed()

            grid.performScrollToNode(hasText("Fake Chat & Bill"))
            composeTestRule.onNodeWithText("Fake Chat & Bill").assertIsDisplayed()

            grid.performScrollToNode(hasText("Coin Toss"))
            composeTestRule.onNodeWithText("Coin Toss").assertIsDisplayed()

            assertEquals(0, composeTestRule.onAllNodesWithText("Username Builder", substring = true).fetchSemanticsNodes().size)
            assertEquals(0, composeTestRule.onAllNodesWithText("Fake Data Generator", substring = true).fetchSemanticsNodes().size)
        } finally {
            db.close()
        }
    }
}
