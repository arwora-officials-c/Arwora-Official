package com.arwora.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arwora.feature.home.weather.SmartWeatherAlertSettings

@Entity(tableName = "weather_alert_preferences")
data class WeatherAlertPreferencesEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val brishtiAlertEnabled: Boolean = true,
    val kaporShukanoAlertEnabled: Boolean = true,
    val bazarAlertEnabled: Boolean = false,
    val bikeRideAlertEnabled: Boolean = true,
    val extremeWarningEnabled: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toDomain(): SmartWeatherAlertSettings = SmartWeatherAlertSettings(
        brishtiAlertEnabled = brishtiAlertEnabled,
        kaporShukanoAlertEnabled = kaporShukanoAlertEnabled,
        bazarAlertEnabled = bazarAlertEnabled,
        bikeRideAlertEnabled = bikeRideAlertEnabled,
        extremeWarningEnabled = extremeWarningEnabled
    )

    companion object {
        const val SINGLETON_ID = 1

        fun fromDomain(
            settings: SmartWeatherAlertSettings,
            updatedAtMillis: Long = System.currentTimeMillis()
        ): WeatherAlertPreferencesEntity = WeatherAlertPreferencesEntity(
            id = SINGLETON_ID,
            brishtiAlertEnabled = settings.brishtiAlertEnabled,
            kaporShukanoAlertEnabled = settings.kaporShukanoAlertEnabled,
            bazarAlertEnabled = settings.bazarAlertEnabled,
            bikeRideAlertEnabled = settings.bikeRideAlertEnabled,
            extremeWarningEnabled = settings.extremeWarningEnabled,
            updatedAtMillis = updatedAtMillis
        )
    }
}
