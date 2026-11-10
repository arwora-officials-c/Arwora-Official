package com.arwora.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationLogDao {
    @Query("SELECT * FROM location_logs ORDER BY id DESC")
    fun getAllLogs(): Flow<List<LocationLogEntity>>

    @Insert
    suspend fun insertLog(log: LocationLogEntity)

    @Query("DELETE FROM location_logs")
    suspend fun clearLogs()
}
