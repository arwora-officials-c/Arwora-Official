package com.arwora

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.arwora.feature.home.weather.HomeWeatherCard
import com.arwora.feature.home.weather.HomeWeatherViewModel
import com.arwora.feature.home.weather.PrefsWeatherCacheStore
import com.arwora.feature.home.weather.WeatherCacheStore
import com.arwora.feature.home.weather.WeatherConditionType
import com.arwora.feature.home.weather.WeatherCoordinates
import com.arwora.feature.home.weather.WeatherHttpClient
import com.arwora.feature.home.weather.WeatherLocationResolver
import com.arwora.feature.home.weather.WeatherLocationSource
import com.arwora.feature.home.weather.WeatherRepository
import com.arwora.feature.home.weather.WeatherSnapshot
import com.arwora.feature.home.weather.WeatherUiState
import com.arwora.feature.home.weather.WmoWeatherMapper
import com.arwora.ui.theme.NameGenProTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h850dp")
class HomeWeatherTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("arwora_weather_cache", Application.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun openMeteoJson(
        temp: Double = 28.4,
        humidity: Int = 68,
        wind: Double = 12.2,
        code: Int = 2,
        isDay: Int = 1,
        maxTemp: Double = 31.3,
        minTemp: Double = 22.8
    ): String = """
        {
          "current": {
            "temperature_2m": $temp,
            "relative_humidity_2m": $humidity,
            "is_day": $isDay,
            "weather_code": $code,
            "wind_speed_10m": $wind
          },
          "daily": {
            "temperature_2m_max": [$maxTemp],
            "temperature_2m_min": [$minTemp]
          }
        }
    """.trimIndent()

    @Test
    fun repositoryFetchesGpsWeatherAndPersistsCacheInPrefs() = runBlocking {
        val fakeHttp = object : WeatherHttpClient {
            override suspend fun get(url: String): String {
                assertTrue(url.contains("api.open-meteo.com"))
                assertTrue(url.contains("latitude=25.0658"))
                return openMeteoJson()
            }
        }
        val fakeLocation = object : WeatherLocationResolver {
            override fun hasLocationPermission(): Boolean = true
            override suspend fun resolveDeviceCoordinates(): WeatherCoordinates = WeatherCoordinates(
                latitude = 25.0658,
                longitude = 91.3950,
                localityName = "Sunamganj",
                source = WeatherLocationSource.GPS
            )
        }
        val cacheStore = PrefsWeatherCacheStore(app)
        val repo = WeatherRepository(
            context = app,
            httpClient = fakeHttp,
            locationResolver = fakeLocation,
            cacheStore = cacheStore,
            clock = { 5_000_000L }
        )

        val result = repo.fetchLiveWeather()
        assertTrue(result.isSuccess)
        val snapshot = result.getOrThrow()
        assertEquals(28, snapshot.temperatureCelsius)
        assertEquals(68, snapshot.humidityPercent)
        assertEquals(12, snapshot.windSpeedKmh)
        assertEquals(31, snapshot.highTempCelsius)
        assertEquals(23, snapshot.lowTempCelsius)
        assertEquals("Scattered Clouds", snapshot.conditionLabel)
        assertEquals(WeatherConditionType.PARTLY_CLOUDY, snapshot.conditionType)
        assertEquals("Sunamganj", snapshot.localityName)
        assertEquals(WeatherLocationSource.GPS, snapshot.locationSource)

        val cached = repo.getCachedWeather()
        assertNotNull(cached)
        assertEquals(snapshot, cached)
    }

    @Test
    fun repositoryFallsBackToIpLookupAndDefaultCityWhenLocationDeniedOrOffline() = runBlocking {
        // 1. Permission denied -> falls back to IP lookup
        val ipHttp = object : WeatherHttpClient {
            override suspend fun get(url: String): String = when {
                url.contains("ipwho.is") -> """{"success":true,"city":"Sylhet","latitude":24.8949,"longitude":91.8687}"""
                else -> openMeteoJson(temp = 30.0, code = 0)
            }
        }
        val deniedLocation = object : WeatherLocationResolver {
            override fun hasLocationPermission(): Boolean = false
            override suspend fun resolveDeviceCoordinates(): WeatherCoordinates? = null
        }
        val ipRepo = WeatherRepository(
            context = app,
            httpClient = ipHttp,
            locationResolver = deniedLocation,
            cacheStore = object : WeatherCacheStore {
                override suspend fun load(): WeatherSnapshot? = null
                override suspend fun save(snapshot: WeatherSnapshot) {}
            }
        )
        val ipResult = ipRepo.fetchLiveWeather().getOrThrow()
        assertEquals("Sylhet", ipResult.localityName)
        assertEquals(WeatherLocationSource.IP_LOOKUP, ipResult.locationSource)
        assertEquals("Clear Sky", ipResult.conditionLabel)

        // 2. IP lookup fails -> falls back to default city (Sunamganj)
        val defaultCityHttp = object : WeatherHttpClient {
            override suspend fun get(url: String): String = when {
                url.contains("ipwho.is") -> throw IOException("IP service unreachable")
                else -> openMeteoJson(temp = 26.0, code = 63)
            }
        }
        val defaultRepo = WeatherRepository(
            context = app,
            httpClient = defaultCityHttp,
            locationResolver = deniedLocation,
            cacheStore = object : WeatherCacheStore {
                override suspend fun load(): WeatherSnapshot? = null
                override suspend fun save(snapshot: WeatherSnapshot) {}
            }
        )
        val defaultResult = defaultRepo.fetchLiveWeather().getOrThrow()
        assertEquals("Sunamganj", defaultResult.localityName)
        assertEquals(WeatherLocationSource.DEFAULT_CITY, defaultResult.locationSource)
        assertEquals("Moderate Rain", defaultResult.conditionLabel)
    }

    @Test
    fun wmoMapperHandlesAllMajorWeatherCodes() {
        assertEquals("Clear Sky", WmoWeatherMapper.map(0).label)
        assertEquals("Scattered Clouds", WmoWeatherMapper.map(2).label)
        assertEquals("Overcast", WmoWeatherMapper.map(3).label)
        assertEquals("Foggy", WmoWeatherMapper.map(45).label)
        assertEquals("Light Drizzle", WmoWeatherMapper.map(51).label)
        assertEquals("Heavy Rain", WmoWeatherMapper.map(65).label)
        assertEquals("Snowfall", WmoWeatherMapper.map(73).label)
        assertEquals("Thunderstorm", WmoWeatherMapper.map(95).label)
    }

    @Test
    fun homeWeatherCardRendersLiveMetricsPermissionPromptRefreshAndErrorStates() {
        var permissionRequested = 0
        var refreshCalls = 0
        val fakeRepo = WeatherRepository(
            context = app,
            httpClient = object : WeatherHttpClient {
                override suspend fun get(url: String): String {
                    refreshCalls++
                    return openMeteoJson(temp = 29.0, humidity = 72, wind = 14.0, code = 0, maxTemp = 33.0, minTemp = 24.0)
                }
            },
            locationResolver = object : WeatherLocationResolver {
                override fun hasLocationPermission(): Boolean = false
                override suspend fun resolveDeviceCoordinates(): WeatherCoordinates? = null
            },
            cacheStore = object : WeatherCacheStore {
                private var stored: WeatherSnapshot? = WeatherSnapshot(
                    temperatureCelsius = 27,
                    highTempCelsius = 30,
                    lowTempCelsius = 22,
                    humidityPercent = 65,
                    windSpeedKmh = 9,
                    weatherCode = 2,
                    conditionLabel = "Scattered Clouds",
                    conditionType = WeatherConditionType.PARTLY_CLOUDY,
                    isDay = true,
                    localityName = "Sunamganj",
                    locationSource = WeatherLocationSource.DEFAULT_CITY,
                    latitude = 25.0658,
                    longitude = 91.3950,
                    fetchedAtMillis = 1_000_000L
                )
                override suspend fun load(): WeatherSnapshot? = stored
                override suspend fun save(snapshot: WeatherSnapshot) { stored = snapshot }
            },
            clock = { 1_005_000L }
        )
        val vm = HomeWeatherViewModel(app, fakeRepo)
        vm.ensureInitialized()

        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                val uiState = vm.uiState.collectAsStateWithLifecycle().value
                HomeWeatherCard(
                    uiState = uiState,
                    onRefresh = { vm.refresh(force = true) },
                    onRequestLocationPermission = { permissionRequested++ }
                )
            }
        }

        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithTag("home_weather_temperature").fetchSemanticsNodes().isNotEmpty()
        }

        // Cached weather renders instantaneously
        composeTestRule.onNodeWithText("27°C").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scattered Clouds").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sunamganj").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_weather_details_strip").assertIsDisplayed()
        composeTestRule.onNodeWithText("65%").assertIsDisplayed()
        composeTestRule.onNodeWithText("9 km/h").assertIsDisplayed()
        composeTestRule.onNodeWithText("30° / 22°").assertIsDisplayed()

        // Permission fallback prompt is visible & interactive
        composeTestRule.onNodeWithTag("home_weather_enable_location_button").assertIsDisplayed().performClick()
        assertEquals(1, permissionRequested)

        // Manual refresh fetches updated forecast
        composeTestRule.onNodeWithTag("home_weather_refresh_button").performClick()
        composeTestRule.waitUntil(3_000L) {
            composeTestRule.onAllNodesWithTag("home_weather_temperature").fetchSemanticsNodes().isNotEmpty() &&
                refreshCalls >= 1
        }
        composeTestRule.onNodeWithText("29°C").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear Sky").assertIsDisplayed()
        composeTestRule.onNodeWithText("72%").assertIsDisplayed()
        composeTestRule.onNodeWithText("14 km/h").assertIsDisplayed()
    }

    @Test
    fun homeWeatherCardDisplaysOfflineRetryStateWhenNoCacheAvailable() {
        var retried = false
        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                HomeWeatherCard(
                    uiState = WeatherUiState.Error(
                        message = "Network unreachable",
                        needsLocationPermission = true
                    ),
                    onRefresh = { retried = true },
                    onRequestLocationPermission = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("home_weather_error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Live Weather Offline").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_weather_enable_location_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_weather_retry_button").assertIsDisplayed().performClick()
        assertTrue(retried)
    }
}
