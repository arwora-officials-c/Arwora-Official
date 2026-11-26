package com.arwora

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasText
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w380dp-h800dp")
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
    fun settingsScreenDisplaysNinePermissionsIncludingNearbyDevicesAndAllOnAllOffButtons() {
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

        // Verify all 9 permissions including Camera, Wireless ADB, Usage Stats, and Nearby Devices
        listOf(
            "settings_permission_item_microphone",
            "settings_permission_item_storage",
            "settings_permission_item_notifications",
            "settings_permission_item_notification_listener",
            "settings_permission_item_overlay",
            "settings_permission_item_camera",
            "settings_permission_item_wireless_adb",
            "settings_permission_item_usage_stats",
            "settings_permission_item_nearby_devices"
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }

        composeTestRule.onNodeWithText("Nearby Devices").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(
            "Required for Arwora Connect P2P file sharing, Wi-Fi & peer discovery"
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_permission_status_nearby_devices", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(hasAnyChild(hasText("Not Granted")))

        // Verify both side-by-side pill controls: 1-Click All ON & 1-Click All OFF
        val allOnBtn = composeTestRule.onNodeWithTag("settings_permissions_all_on_button")
            .performScrollTo()
            .assertIsDisplayed()
        val allOffBtn = composeTestRule.onNodeWithTag("settings_permissions_all_off_button")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("1-Click All ON").assertIsDisplayed()
        composeTestRule.onNodeWithText("1-Click All OFF").assertIsDisplayed()

        val onBounds = allOnBtn.getUnclippedBoundsInRoot()
        val offBounds = allOffBtn.getUnclippedBoundsInRoot()
        assertTrue("1-Click All ON must sit left of 1-Click All OFF", onBounds.right < offBounds.left)
        assertTrue(
            "Both pill buttons must align at the same vertical top edge",
            abs((onBounds.top - offBounds.top).value) <= 1f
        )

        // Verify clicking 1-Click All OFF opens the confirmation Material 3 dialog
        allOffBtn.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Disable App Permissions?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_cancel_all_off_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun nearbyDevicesPermissionBadgeReflectsGrantedState() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(
            android.Manifest.permission.NEARBY_WIFI_DEVICES,
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_CONNECT
        )

        val database = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db = database

        val viewModel = MainViewModel(
            historyRepository = HistoryRepository(database.historyDao()),
            favoriteRepository = FavoriteRepository(database.favoriteDao()),
            savedTextRepository = SavedTextRepository(database.savedTextDao()),
            settingsRepository = SettingsRepository(app.dataStore)
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
        composeTestRule.onNodeWithTag("settings_app_permissions_row")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_permission_item_nearby_devices")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_permission_status_nearby_devices", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(hasAnyChild(hasText("Granted")))
        val expectedGranted = com.arwora.utils.AppPermissionsHelper.inspectPermissions(app).count { it.isGranted }
        assertTrue("Nearby Devices should count toward granted permissions", expectedGranted >= 1)
        // Verify summary reflects granted Nearby Devices permission out of 9 total permissions
        composeTestRule.onNodeWithText("$expectedGranted of 9 permissions granted")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
