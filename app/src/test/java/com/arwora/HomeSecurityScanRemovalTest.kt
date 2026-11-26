package com.arwora

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
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
import com.arwora.feature.home.weather.HomeWeatherViewModel
import com.arwora.feature.home.weather.WeatherCacheStore
import com.arwora.feature.home.weather.WeatherConditionType
import com.arwora.feature.home.weather.WeatherCoordinates
import com.arwora.feature.home.weather.WeatherHttpClient
import com.arwora.feature.home.weather.WeatherLocationResolver
import com.arwora.feature.home.weather.WeatherLocationSource
import com.arwora.feature.home.weather.WeatherRepository
import com.arwora.feature.home.weather.WeatherSnapshot
import com.arwora.ui.MainViewModel
import com.arwora.ui.screens.HomeScreen
import com.arwora.ui.theme.NameGenProTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class HomeSecurityScanRemovalTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeDashboardExcludesSystemSecurityScanCardAndPlacesWeatherDirectlyBelowHeader() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val viewModel = MainViewModel(
            HistoryRepository(db.historyDao()),
            FavoriteRepository(db.favoriteDao()),
            SettingsRepository(context.dataStore),
            SavedTextRepository(db.savedTextDao())
        )
        val cachedSnapshot = WeatherSnapshot(
            temperatureCelsius = 28,
            highTempCelsius = 31,
            lowTempCelsius = 23,
            humidityPercent = 66,
            windSpeedKmh = 11,
            weatherCode = 2,
            conditionLabel = "Scattered Clouds",
            conditionType = WeatherConditionType.PARTLY_CLOUDY,
            isDay = true,
            localityName = "Sunamganj",
            locationSource = WeatherLocationSource.GPS,
            latitude = 25.0658,
            longitude = 91.3950,
            fetchedAtMillis = 1_000_000L
        )
        val weatherRepo = WeatherRepository(
            context = context,
            httpClient = object : WeatherHttpClient {
                override suspend fun get(url: String): String = error("Should use fresh cache")
            },
            locationResolver = object : WeatherLocationResolver {
                override fun hasLocationPermission(): Boolean = true
                override suspend fun resolveDeviceCoordinates(): WeatherCoordinates? = null
            },
            cacheStore = object : WeatherCacheStore {
                override suspend fun load(): WeatherSnapshot = cachedSnapshot
                override suspend fun save(snapshot: WeatherSnapshot) {}
            },
            clock = { 1_001_000L }
        )
        val weatherViewModel = HomeWeatherViewModel(context, weatherRepo)

        try {
            composeTestRule.setContent {
                NameGenProTheme(darkTheme = true) {
                    val navController = rememberNavController()
                    HomeScreen(
                        viewModel = viewModel,
                        navController = navController,
                        weatherViewModel = weatherViewModel
                    )
                }
            }
            composeTestRule.waitForIdle()

            // Header and immediate next card (Live Weather) render cleanly
            composeTestRule.onNodeWithText("Arwora Hyper Toolkit").assertIsDisplayed()
            composeTestRule.onNodeWithTag("home_weather_card").assertIsDisplayed()
            composeTestRule.onNodeWithTag("home_weather_temperature").assertIsDisplayed()

            // Verify System Security Scan card and subtitle remain completely absent
            assertEquals(
                0,
                composeTestRule.onAllNodesWithText("System Security Scan", substring = true)
                    .fetchSemanticsNodes().size
            )
            assertEquals(
                0,
                composeTestRule.onAllNodesWithText("Tap to run a deep security scan", substring = true)
                    .fetchSemanticsNodes().size
            )

            // Verify Live Weather card occupies the exact top slot below the header subtitle
            val headerBottom = composeTestRule.onNodeWithText(
                "Deep hardware diagnostics, Wi-Fi security, battery thermals, junk cleaner & system utilities."
            ).fetchSemanticsNode().boundsInRoot.bottom
            val weatherCardBounds = composeTestRule.onNodeWithTag("home_weather_card")
                .fetchSemanticsNode().boundsInRoot
            val gapDp = with(composeTestRule.density) { (weatherCardBounds.top - headerBottom).toDp().value }
            assertTrue(
                "Expected Live Weather card directly below header (~16dp gap), got ${gapDp}dp",
                gapDp in 8f..32f
            )

            // Verify other Home Dashboard cards remain intact below Weather Card
            composeTestRule.onNodeWithTag("home_screen_list")
                .performScrollToNode(hasTestTag("home_qr_scanner_card"))
            composeTestRule.onNodeWithTag("home_qr_scanner_card").assertIsDisplayed()

            composeTestRule.onNodeWithTag("home_screen_list")
                .performScrollToNode(hasTestTag("home_app_usage_stats_card"))
            composeTestRule.onNodeWithTag("home_app_usage_stats_card").assertIsDisplayed()

            composeTestRule.onNodeWithTag("home_screen_list")
                .performScrollToNode(hasText("App Ops Manager"))
            composeTestRule.onNodeWithText("App Ops Manager").assertIsDisplayed()
        } finally {
            db.close()
        }
    }
}
