package com.arwora

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.arwora.ui.screens.SettingsScreen
import com.arwora.ui.theme.NameGenProTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsPermissionsUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var db: AppDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        db = null
    }

    @Test
    fun settingsScreenDisplaysEightPermissionsAndGrantAllMissingButtonWhenExpanded() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db = database

        val viewModel = MainViewModel(
            historyRepository = HistoryRepository(database.historyDao()),
            favoriteRepository = FavoriteRepository(database.favoriteDao()),
            savedTextRepository = SavedTextRepository(database.savedTextDao()),
            settingsRepository = SettingsRepository(context.dataStore)
        )

        composeTestRule.setContent {
            val navController = rememberNavController()
            NameGenProTheme(darkTheme = true) {
                SettingsScreen(
                    navController = navController,
                    viewModel = viewModel
                )
            }
        }

        composeTestRule.waitForIdle()

        // Preserve existing floating density chips
        composeTestRule.onNodeWithTag("settings_floating_size_compact").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_floating_size_medium").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_floating_size_large").performScrollTo().assertIsDisplayed()

        // Expand App Permissions & Status card
        composeTestRule.onNodeWithTag("settings_app_permissions_row")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        // Verify all 8 permissions including Camera, Wireless ADB, and Usage Stats
        listOf(
            "settings_permission_item_microphone",
            "settings_permission_item_storage",
            "settings_permission_item_notifications",
            "settings_permission_item_notification_listener",
            "settings_permission_item_overlay",
            "settings_permission_item_camera",
            "settings_permission_item_wireless_adb",
            "settings_permission_item_usage_stats"
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }

        // Verify the Grant All Missing quick-fix pill action button is visible
        composeTestRule.onNodeWithTag("settings_grant_all_missing_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Grant All Missing").assertIsDisplayed()
    }
}
