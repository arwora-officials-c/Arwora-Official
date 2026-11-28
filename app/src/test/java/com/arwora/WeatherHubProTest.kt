package com.arwora

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.arwora.data.db.AppDatabase
import com.arwora.feature.home.weather.AdvisoryLevel
import com.arwora.feature.home.weather.AirQualitySnapshot
import com.arwora.feature.home.weather.DailyWeatherPoint
import com.arwora.feature.home.weather.HomeWeatherCard
import com.arwora.feature.home.weather.HourlyWeatherPoint
import com.arwora.feature.home.weather.ProWeatherBundle
import com.arwora.feature.home.weather.SavedWeatherCity
import com.arwora.feature.home.weather.SmartAlertKind
import com.arwora.feature.home.weather.SmartWeatherAlertSettings
import com.arwora.feature.home.weather.WeatherAdvisoryEngine
import com.arwora.feature.home.weather.WeatherAlertSettingsScreen
import com.arwora.feature.home.weather.WeatherCameraHelper
import com.arwora.feature.home.weather.WeatherConditionType
import com.arwora.feature.home.weather.WeatherCoordinates
import com.arwora.feature.home.weather.WeatherHttpClient
import com.arwora.feature.home.weather.WeatherHubScreen
import com.arwora.feature.home.weather.WeatherLocationResolver
import com.arwora.feature.home.weather.WeatherLocationSource
import com.arwora.feature.home.weather.WeatherProParser
import com.arwora.feature.home.weather.WeatherProViewModel
import com.arwora.feature.home.weather.WeatherRepository
import com.arwora.feature.home.weather.WeatherSnapshot
import com.arwora.feature.home.weather.WeatherUiState
import com.arwora.ui.theme.NameGenProTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp")
class WeatherHubProTest {

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

    private fun richOpenMeteoProJson(): String {
        val hours = (0 until 24).joinToString(",") { "\"2026-11-27T${it.toString().padStart(2, '0')}:00\"" }
        val hTemps = (0 until 24).joinToString(",") { "${22 + (it % 8)}" }
        val hRainProb = (0 until 24).joinToString(",") { if (it in 1..2) "75" else "15" }
        val hPrecip = (0 until 24).joinToString(",") { if (it == 1) "2.4" else "0.0" }
        val hCodes = (0 until 24).joinToString(",") { if (it == 1) "61" else "2" }
        val hWinds = (0 until 24).joinToString(",") { "${10 + (it % 5)}" }
        val hHum = (0 until 24).joinToString(",") { "64" }
        val hUv = (0 until 24).joinToString(",") { if (it in 10..15) "6.2" else "0.5" }
        val hDay = (0 until 24).joinToString(",") { if (it in 6..17) "1" else "0" }

        val days = (1..14).joinToString(",") { "\"2026-11-${(10 + it).toString().padStart(2, '0')}\"" }
        val dHigh = (1..14).joinToString(",") { "${27 + (it % 4)}" }
        val dLow = (1..14).joinToString(",") { "${18 + (it % 3)}" }
        val dProb = (1..14).joinToString(",") { "${20 + it * 2}" }
        val dSum = (1..14).joinToString(",") { if (it <= 2) "6.5" else "0.5" }
        val dCodes = (1..14).joinToString(",") { if (it == 2) "63" else "2" }
        val dWinds = (1..14).joinToString(",") { "15.0" }
        val dUv = (1..14).joinToString(",") { "6.0" }

        return """
            {
              "current": {
                "temperature_2m": 24.4,
                "apparent_temperature": 25.8,
                "relative_humidity_2m": 65,
                "dew_point_2m": 17.2,
                "precipitation": 0.0,
                "is_day": 1,
                "weather_code": 0,
                "wind_speed_10m": 11.2,
                "wind_direction_10m": 135,
                "uv_index": 5.4,
                "surface_pressure": 1011.2,
                "visibility": 12000.0,
                "soil_moisture_0_to_1cm": 0.31
              },
              "hourly": {
                "time": [$hours],
                "temperature_2m": [$hTemps],
                "apparent_temperature": [$hTemps],
                "relative_humidity_2m": [$hHum],
                "precipitation_probability": [$hRainProb],
                "precipitation": [$hPrecip],
                "weather_code": [$hCodes],
                "wind_speed_10m": [$hWinds],
                "uv_index": [$hUv],
                "is_day": [$hDay]
              },
              "daily": {
                "time": [$days],
                "weather_code": [$dCodes],
                "temperature_2m_max": [$dHigh],
                "temperature_2m_min": [$dLow],
                "precipitation_probability_max": [$dProb],
                "precipitation_sum": [$dSum],
                "wind_speed_10m_max": [$dWinds],
                "uv_index_max": [$dUv]
              }
            }
        """.trimIndent()
    }

    @Test
    fun proRepositoryFetchesOfflineCacheAndEnforcesTenCityCap() = runBlocking {
        val fakeHttp = object : WeatherHttpClient {
            override suspend fun get(url: String): String = when {
                url.contains("air-quality-api") -> """{"current":{"us_aqi":115,"pm10":78.4,"pm2_5":34.2}}"""
                url.contains("rainviewer.com") -> """{"radar":{"past":[{"time":1700000000,"path":"/v2/radar/1700000000"}]}}"""
                url.contains("archive-api") -> """{"daily":{"weather_code":[61],"temperature_2m_max":[26.4],"temperature_2m_min":[18.1],"precipitation_sum":[4.2]}}"""
                url.contains("geocoding-api") -> """{"results":[{"id":1,"name":"Sylhet","admin1":"Sylhet","country":"Bangladesh","latitude":24.8949,"longitude":91.8687}]}"""
                else -> richOpenMeteoProJson()
            }
        }
        val fakeLoc = object : WeatherLocationResolver {
            override fun hasLocationPermission(): Boolean = true
            override suspend fun resolveDeviceCoordinates(): WeatherCoordinates = WeatherCoordinates(
                latitude = 25.0658,
                longitude = 91.3950,
                localityName = "Sunamganj",
                source = WeatherLocationSource.GPS
            )
        }
        val repo = WeatherRepository(
            context = app,
            httpClient = fakeHttp,
            locationResolver = fakeLoc,
            clock = { 10_000_000L }
        )

        val res = repo.fetchProWeather()
        assertTrue(res.isSuccess)
        val bundle = res.getOrThrow()
        assertEquals(24, bundle.hourly24.size)
        assertEquals(14, bundle.daily14.size)
        assertEquals(115, bundle.airQuality?.usAqi)
        assertEquals("/v2/radar/1700000000", bundle.rainViewerRadarPath)
        assertEquals(26, bundle.lastYearHistory?.highTempCelsius)

        // Offline bundle restores intact
        val cached = repo.loadCachedProWeather()
        assertNotNull(cached)
        assertTrue(cached!!.isFromOfflineCache)
        assertEquals(14, cached.daily14.size)
        assertEquals("Last updated 5 mins ago", WeatherProParser.formatRelativeMinutes(10_000_000L, 10_300_000L))

        // City search & 10-city cap
        val cities = repo.searchCities("Sylhet").getOrThrow()
        assertEquals("Sylhet", cities.first().name)
        for (i in 1..12) {
            repo.saveFavoriteCity(SavedWeatherCity("c_$i", "City$i", "Country", 24.0 + i * 0.1, 90.0))
        }
        assertEquals(10, repo.getSavedCities().size)
    }

    @Test
    fun advisoryEngineComputesHealthPlannerFarmingSportsAndSmartAlerts() {
        val current = WeatherSnapshot(
            temperatureCelsius = 36,
            highTempCelsius = 38,
            lowTempCelsius = 27,
            humidityPercent = 80,
            windSpeedKmh = 30,
            weatherCode = 95,
            conditionLabel = "Thunderstorm",
            conditionType = WeatherConditionType.THUNDERSTORM,
            isDay = true,
            localityName = "Sunamganj",
            locationSource = WeatherLocationSource.GPS,
            latitude = 25.0658,
            longitude = 91.3950,
            fetchedAtMillis = 1_000_000L,
            feelsLikeCelsius = 43,
            uvIndex = 8.0,
            pressureHpa = 1004,
            dewPointCelsius = 26,
            visibilityKm = 4.0,
            windDirectionDeg = 180,
            precipitationMm = 2.5,
            soilMoisture = 0.42
        )
        val hourly = listOf(
            HourlyWeatherPoint("2026-11-27T08:00", "Now", 8, 35, 41, 80, 3.0, 30, 80, 6.0, 95, "Thunderstorm", WeatherConditionType.THUNDERSTORM, true),
            HourlyWeatherPoint("2026-11-27T09:00", "9 AM", 9, 34, 40, 65, 1.2, 22, 78, 5.0, 63, "Moderate Rain", WeatherConditionType.RAIN, true)
        )
        val daily = listOf(
            DailyWeatherPoint("2026-11-27", "Today", 38, 27, 85, 14.0, 32, 8.0, 95, "Thunderstorm", WeatherConditionType.THUNDERSTORM),
            DailyWeatherPoint("2026-11-28", "Tomorrow", 34, 25, 70, 8.0, 20, 7.0, 61, "Light Rain", WeatherConditionType.RAIN)
        )
        val aqi = AirQualitySnapshot(usAqi = 160, pm10 = 135.0, pm25 = 62.0, dustLevelLabel = "Unhealthy")

        val heat = WeatherAdvisoryEngine.computeHeatStrokeRisk(current)
        assertEquals(AdvisoryLevel.DANGER, heat.level)

        val dust = WeatherAdvisoryEngine.computeDustAllergyMeter(aqi, current.windSpeedKmh)
        assertTrue(dust.advice.contains("mask", ignoreCase = true))

        val sleep = WeatherAdvisoryEngine.computeSleepComfortAdvice(current, 27)
        assertTrue(sleep.levelLabel.contains("AC", ignoreCase = true))

        val activities = WeatherAdvisoryEngine.computeBestTimeActivities(current, hourly)
        assertEquals(5, activities.size)

        val farming = WeatherAdvisoryEngine.computeFarmingAdvice(current, hourly, daily)
        assertTrue(farming.irrigationAction.contains("Pause", ignoreCase = true))
        assertTrue(farming.fertilizerAction.contains("Delay", ignoreCase = true))

        val sports = WeatherAdvisoryEngine.computeSportsAdvice(current, hourly)
        assertTrue(sports.rainInterruptionRisk.contains("High", ignoreCase = true))

        val bundle = ProWeatherBundle(current, hourly, daily, aqi)
        val alerts = WeatherAdvisoryEngine.evaluateSmartAlerts(
            bundle = bundle,
            settings = SmartWeatherAlertSettings(
                brishtiAlertEnabled = true,
                kaporShukanoAlertEnabled = true,
                bazarAlertEnabled = true,
                bikeRideAlertEnabled = true,
                extremeWarningEnabled = true
            ),
            nowHourOfDay = 8
        )
        val kinds = alerts.map { it.kind }.toSet()
        assertTrue(SmartAlertKind.EXTREME in kinds)
        assertTrue(SmartAlertKind.BRISHTI in kinds)
        assertTrue(SmartAlertKind.BAZAR in kinds)
        assertTrue(SmartAlertKind.BIKE_RIDE in kinds)

        val voiceBn = WeatherAdvisoryEngine.buildBanglaVoiceSummary(
            current,
            WeatherAdvisoryEngine.computeOutfitAdvice(current, 80)
        )
        assertTrue(voiceBn.contains("Sunamganj"))
    }

    @Test
    fun weatherCameraBitmapStamperProducesStampedOutput() {
        val raw = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val snap = WeatherSnapshot(
            temperatureCelsius = 24,
            highTempCelsius = 28,
            lowTempCelsius = 19,
            humidityPercent = 62,
            windSpeedKmh = 9,
            weatherCode = 0,
            conditionLabel = "Clear Sky",
            conditionType = WeatherConditionType.CLEAR,
            isDay = true,
            localityName = "Sunamganj",
            locationSource = WeatherLocationSource.GPS,
            latitude = 25.0658,
            longitude = 91.3950,
            fetchedAtMillis = 1_000L
        )
        val stamped = WeatherCameraHelper.stampWeatherOverlayOnBitmap(raw, snap, "Nov 27, 2026")
        assertEquals(640, stamped.width)
        assertEquals(480, stamped.height)
    }

    @Test
    fun homeWeatherCardIsClickableToOpenWeatherHubAndHubRendersAllFiveModules() {
        var openedHub = false
        val snap = WeatherSnapshot(
            temperatureCelsius = 24,
            highTempCelsius = 28,
            lowTempCelsius = 19,
            humidityPercent = 60,
            windSpeedKmh = 10,
            weatherCode = 0,
            conditionLabel = "Clear Sky",
            conditionType = WeatherConditionType.CLEAR,
            isDay = true,
            localityName = "Sunamganj",
            locationSource = WeatherLocationSource.IP_LOOKUP,
            latitude = 25.0658,
            longitude = 91.3950,
            fetchedAtMillis = 1_000_000L,
            feelsLikeCelsius = 25
        )

        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                HomeWeatherCard(
                    uiState = WeatherUiState.Success(snap),
                    onRefresh = {},
                    onRequestLocationPermission = {},
                    onOpenWeatherHub = { openedHub = true }
                )
            }
        }
        composeTestRule.onNodeWithTag("home_weather_card").assertIsDisplayed().performClick()
        assertTrue(openedHub)
    }

    @Test
    fun weatherHubScreenRendersAllFiveModuleTabsCleanly() {
        val fakeHttp = object : WeatherHttpClient {
            override suspend fun get(url: String): String = when {
                url.contains("air-quality-api") -> """{"current":{"us_aqi":55,"pm10":24.0,"pm2_5":11.0}}"""
                url.contains("rainviewer.com") -> """{"radar":{"past":[{"path":"/v2/radar/1700000000"}]}}"""
                url.contains("archive-api") -> """{"daily":{"weather_code":[0],"temperature_2m_max":[27.0],"temperature_2m_min":[19.0],"precipitation_sum":[0.0]}}"""
                else -> richOpenMeteoProJson()
            }
        }
        val repo = WeatherRepository(
            context = app,
            httpClient = fakeHttp,
            locationResolver = object : WeatherLocationResolver {
                override fun hasLocationPermission(): Boolean = true
                override suspend fun resolveDeviceCoordinates() = WeatherCoordinates(25.0658, 91.3950, "Sunamganj", WeatherLocationSource.GPS)
            },
            clock = { 10_000_000L }
        )
        var openedAlertSettings = false
        val proVm = WeatherProViewModel(app, repo)

        composeTestRule.setContent {
            NameGenProTheme(darkTheme = true) {
                WeatherHubScreen(
                    onBack = {},
                    onOpenWeatherCamera = {},
                    onOpenAlertSettings = { openedAlertSettings = true },
                    proViewModel = proVm
                )
            }
        }

        composeTestRule.waitUntil(4_000L) {
            composeTestRule.onAllNodesWithTag("weather_hub_tab_0").fetchSemanticsNodes().isNotEmpty()
        }

        // Tab 0: Live & Radar
        composeTestRule.onNodeWithText("24°C").assertIsDisplayed()
        composeTestRule.onNodeWithTag("weather_hub_list")
            .performScrollToNode(hasTestTag("weather_forecast_tab_14d"))
        composeTestRule.onNodeWithTag("weather_forecast_tab_14d").performClick()

        // Scroll back up to module tab chips
        composeTestRule.onNodeWithTag("weather_hub_list")
            .performScrollToNode(hasTestTag("weather_hub_tab_1"))

        // Tab 1: Alerts
        composeTestRule.onNodeWithTag("weather_hub_tab_1").performClick()
        composeTestRule.onNodeWithTag("weather_hub_alerts_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("weather_hub_list")
            .performScrollToNode(hasTestTag("weather_hub_open_alert_settings_btn"))
        composeTestRule.onNodeWithTag("weather_hub_open_alert_settings_btn").performClick()
        assertTrue(openedAlertSettings)

        // Tab 2: Planner & Farm
        composeTestRule.onNodeWithTag("weather_hub_list")
            .performScrollToNode(hasTestTag("weather_hub_tab_2"))
        composeTestRule.onNodeWithTag("weather_hub_tab_2").performClick()
        composeTestRule.onNodeWithTag("weather_hub_planner_section").assertIsDisplayed()

        // Tab 3: Health
        composeTestRule.onNodeWithTag("weather_hub_list")
            .performScrollToNode(hasTestTag("weather_hub_tab_3"))
        composeTestRule.onNodeWithTag("weather_hub_tab_3").performClick()
        composeTestRule.onNodeWithTag("weather_hub_health_section").assertIsDisplayed()

        // Tab 4: Utilities
        composeTestRule.onNodeWithTag("weather_hub_list")
            .performScrollToNode(hasTestTag("weather_hub_tab_4"))
        composeTestRule.onNodeWithTag("weather_hub_tab_4").performClick()
        composeTestRule.onNodeWithTag("weather_hub_utilities_section").assertIsDisplayed()
    }

    @Test
    fun weatherAlertPreferencesPersistInRoomAndSyncViewModel() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.weatherAlertPreferencesDao()
            val repo = WeatherRepository(
                context = app,
                weatherAlertDao = dao
            )

            // Default preferences seed into Room on first read
            val initial = repo.getAlertSettingsSuspend()
            assertTrue(initial.brishtiAlertEnabled)
            assertTrue(initial.kaporShukanoAlertEnabled)
            assertFalse(initial.bazarAlertEnabled)
            assertTrue(initial.bikeRideAlertEnabled)

            val custom = SmartWeatherAlertSettings(
                brishtiAlertEnabled = false,
                kaporShukanoAlertEnabled = false,
                bazarAlertEnabled = true,
                bikeRideAlertEnabled = false,
                extremeWarningEnabled = true
            )
            repo.saveAlertSettingsSuspend(custom)

            val savedRow = dao.getPreferences()
            assertNotNull(savedRow)
            assertFalse(savedRow!!.brishtiAlertEnabled)
            assertFalse(savedRow.kaporShukanoAlertEnabled)
            assertTrue(savedRow.bazarAlertEnabled)
            assertFalse(savedRow.bikeRideAlertEnabled)
            assertTrue(savedRow.extremeWarningEnabled)

            val observed = repo.observeAlertSettings().first()
            assertEquals(custom, observed)
        } finally {
            db.close()
        }
    }

    @Test
    fun weatherAlertSettingsScreenTogglesIndividualNotificationsAndSavesToRoom() {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.weatherAlertPreferencesDao()
            val repo = WeatherRepository(
                context = app,
                httpClient = object : WeatherHttpClient {
                    override suspend fun get(url: String): String = when {
                        url.contains("air-quality-api") -> """{"current":{"us_aqi":45,"pm10":18.0,"pm2_5":9.0}}"""
                        url.contains("rainviewer.com") -> """{"radar":{"past":[{"path":"/v2/radar/1700000000"}]}}"""
                        url.contains("archive-api") -> """{"daily":{"weather_code":[0],"temperature_2m_max":[27.0],"temperature_2m_min":[19.0],"precipitation_sum":[0.0]}}"""
                        else -> richOpenMeteoProJson()
                    }
                },
                locationResolver = object : WeatherLocationResolver {
                    override fun hasLocationPermission(): Boolean = true
                    override suspend fun resolveDeviceCoordinates() =
                        WeatherCoordinates(25.0658, 91.3950, "Sunamganj", WeatherLocationSource.GPS)
                },
                weatherAlertDao = dao
            )
            val proVm = WeatherProViewModel(app, repo)

            composeTestRule.setContent {
                NameGenProTheme(darkTheme = true) {
                    WeatherAlertSettingsScreen(
                        onBack = {},
                        proViewModel = proVm
                    )
                }
            }

            composeTestRule.waitUntil(3_000L) {
                composeTestRule.onAllNodesWithTag("weather_alerts_settings_card").fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText("Weather Alerts").assertIsDisplayed()
            composeTestRule.onNodeWithText("Rain (Brishti)").assertIsDisplayed()
            composeTestRule.onNodeWithText("Laundry (Kapor Shukano)").assertIsDisplayed()
            composeTestRule.onNodeWithText("Market (Bazar)").assertIsDisplayed()
            composeTestRule.onNodeWithText("Commuting (Bike Ride)").assertIsDisplayed()

            // Toggle all 4 individual notification switches
            composeTestRule.onNodeWithTag("weather_alert_setting_brishti").performClick()
            composeTestRule.onNodeWithTag("weather_alert_setting_kapor").performClick()
            composeTestRule.onNodeWithTag("weather_alert_setting_bazar").performClick()
            composeTestRule.onNodeWithTag("weather_alert_setting_bike").performClick()

            composeTestRule.waitUntil(3_000L) {
                runBlocking {
                    val entity = dao.getPreferences()
                    entity != null &&
                        !entity.brishtiAlertEnabled &&
                        !entity.kaporShukanoAlertEnabled &&
                        entity.bazarAlertEnabled &&
                        !entity.bikeRideAlertEnabled
                }
            }

            // Reset defaults updates Room back to defaults
            composeTestRule.onNodeWithTag("weather_alert_settings_list")
                .performScrollToNode(hasTestTag("weather_alerts_settings_reset_btn"))
            composeTestRule.onNodeWithTag("weather_alerts_settings_reset_btn").performClick()
            composeTestRule.waitUntil(3_000L) {
                runBlocking {
                    val entity = dao.getPreferences()
                    entity != null &&
                        entity.brishtiAlertEnabled &&
                        entity.kaporShukanoAlertEnabled &&
                        !entity.bazarAlertEnabled &&
                        entity.bikeRideAlertEnabled
                }
            }
        } finally {
            db.close()
        }
    }
}
