package com.arwora.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherAlertPreferencesDao {
    @Query("SELECT * FROM weather_alert_preferences WHERE id = 1 LIMIT 1")
    fun observePreferences(): Flow<WeatherAlertPreferencesEntity?>

    @Query("SELECT * FROM weather_alert_preferences WHERE id = 1 LIMIT 1")
    suspend fun getPreferences(): WeatherAlertPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreferences(entity: WeatherAlertPreferencesEntity)
}
