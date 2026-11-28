package com.arwora.feature.home.weather

enum class WeatherConditionType {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    THUNDERSTORM,
    SNOW
}

enum class WeatherLocationSource(val label: String) {
    GPS("GPS"),
    IP_LOOKUP("IP Location"),
    DEFAULT_CITY("Default")
}

data class WeatherCoordinates(
    val latitude: Double,
    val longitude: Double,
    val localityName: String,
    val source: WeatherLocationSource
)

data class WeatherSnapshot(
    val temperatureCelsius: Int,
    val highTempCelsius: Int,
    val lowTempCelsius: Int,
    val humidityPercent: Int,
    val windSpeedKmh: Int,
    val weatherCode: Int,
    val conditionLabel: String,
    val conditionType: WeatherConditionType,
    val isDay: Boolean,
    val localityName: String,
    val locationSource: WeatherLocationSource,
    val latitude: Double,
    val longitude: Double,
    val fetchedAtMillis: Long,
    val feelsLikeCelsius: Int = temperatureCelsius,
    val uvIndex: Double = 0.0,
    val pressureHpa: Int = 1013,
    val dewPointCelsius: Int = temperatureCelsius - 2,
    val visibilityKm: Double = 10.0,
    val windDirectionDeg: Int = 0,
    val precipitationMm: Double = 0.0,
    val soilMoisture: Double = 0.25
)

data class HourlyWeatherPoint(
    val isoTime: String,
    val hourLabel: String,
    val hourOfDay: Int,
    val temperatureCelsius: Int,
    val feelsLikeCelsius: Int,
    val precipitationProbabilityPercent: Int,
    val precipitationMm: Double,
    val windSpeedKmh: Int,
    val humidityPercent: Int,
    val uvIndex: Double,
    val weatherCode: Int,
    val conditionLabel: String,
    val conditionType: WeatherConditionType,
    val isDay: Boolean
)

data class DailyWeatherPoint(
    val isoDate: String,
    val dayLabel: String,
    val highTempCelsius: Int,
    val lowTempCelsius: Int,
    val precipitationProbabilityMax: Int,
    val precipitationSumMm: Double,
    val windSpeedMaxKmh: Int,
    val uvIndexMax: Double,
    val weatherCode: Int,
    val conditionLabel: String,
    val conditionType: WeatherConditionType,
    val sunriseLabel: String = "06:00",
    val sunsetLabel: String = "18:00"
)

data class AirQualitySnapshot(
    val usAqi: Int,
    val pm10: Double,
    val pm25: Double,
    val dustLevelLabel: String
)

data class HistoricalWeatherDay(
    val dateLabel: String,
    val highTempCelsius: Int,
    val lowTempCelsius: Int,
    val precipitationSumMm: Double,
    val weatherCode: Int,
    val conditionLabel: String,
    val conditionType: WeatherConditionType
)

data class SavedWeatherCity(
    val id: String,
    val name: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val isCurrentLocation: Boolean = false
)

data class SmartWeatherAlertSettings(
    val brishtiAlertEnabled: Boolean = true,
    val kaporShukanoAlertEnabled: Boolean = true,
    val bazarAlertEnabled: Boolean = false,
    val bikeRideAlertEnabled: Boolean = true,
    val extremeWarningEnabled: Boolean = true
)

data class ProWeatherBundle(
    val current: WeatherSnapshot,
    val hourly24: List<HourlyWeatherPoint>,
    val daily14: List<DailyWeatherPoint>,
    val airQuality: AirQualitySnapshot? = null,
    val lastYearHistory: HistoricalWeatherDay? = null,
    val savedCities: List<SavedWeatherCity> = emptyList(),
    val activeCityId: String = CURRENT_LOCATION_CITY_ID,
    val rainViewerRadarPath: String? = null,
    val fetchedAtMillis: Long = current.fetchedAtMillis,
    val isFromOfflineCache: Boolean = false
) {
    companion object {
        const val CURRENT_LOCATION_CITY_ID = "current_gps_auto"
    }
}

sealed class WeatherUiState {
    data class Loading(val cached: WeatherSnapshot? = null) : WeatherUiState()

    data class Success(
        val data: WeatherSnapshot,
        val isRefreshing: Boolean = false,
        val needsLocationPermission: Boolean = false,
        val staleErrorMessage: String? = null
    ) : WeatherUiState()

    data class Error(
        val message: String,
        val needsLocationPermission: Boolean = false
    ) : WeatherUiState()
}

sealed class ProWeatherUiState {
    data class Loading(val cached: ProWeatherBundle? = null) : ProWeatherUiState()
    data class Success(
        val bundle: ProWeatherBundle,
        val isRefreshing: Boolean = false,
        val statusBanner: String? = null
    ) : ProWeatherUiState()
    data class Error(val message: String) : ProWeatherUiState()
}

object WmoWeatherMapper {
    data class ConditionInfo(
        val label: String,
        val type: WeatherConditionType
    )

    fun map(code: Int): ConditionInfo = when (code) {
        0 -> ConditionInfo("Clear Sky", WeatherConditionType.CLEAR)
        1 -> ConditionInfo("Mainly Clear", WeatherConditionType.CLEAR)
        2 -> ConditionInfo("Scattered Clouds", WeatherConditionType.PARTLY_CLOUDY)
        3 -> ConditionInfo("Overcast", WeatherConditionType.CLOUDY)
        45, 48 -> ConditionInfo("Foggy", WeatherConditionType.FOG)
        51 -> ConditionInfo("Light Drizzle", WeatherConditionType.DRIZZLE)
        53 -> ConditionInfo("Moderate Drizzle", WeatherConditionType.DRIZZLE)
        55, 56, 57 -> ConditionInfo("Dense Drizzle", WeatherConditionType.DRIZZLE)
        61 -> ConditionInfo("Light Rain", WeatherConditionType.RAIN)
        63 -> ConditionInfo("Moderate Rain", WeatherConditionType.RAIN)
        65, 66, 67 -> ConditionInfo("Heavy Rain", WeatherConditionType.RAIN)
        71, 73, 75, 77, 85, 86 -> ConditionInfo("Snowfall", WeatherConditionType.SNOW)
        80 -> ConditionInfo("Light Showers", WeatherConditionType.RAIN)
        81 -> ConditionInfo("Rain Showers", WeatherConditionType.RAIN)
        82 -> ConditionInfo("Heavy Showers", WeatherConditionType.RAIN)
        95, 96, 99 -> ConditionInfo("Thunderstorm", WeatherConditionType.THUNDERSTORM)
        else -> ConditionInfo("Partly Cloudy", WeatherConditionType.PARTLY_CLOUDY)
    }
}
