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
    val fetchedAtMillis: Long
)

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
