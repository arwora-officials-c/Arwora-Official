package com.arwora.data.repository

import com.arwora.data.db.LocationLogDao
import com.arwora.data.db.LocationLogEntity
import kotlinx.coroutines.flow.Flow

class LocationLogRepository(private val dao: LocationLogDao) {
    val allLogs: Flow<List<LocationLogEntity>> = dao.getAllLogs()

    suspend fun insert(log: LocationLogEntity) {
        dao.insertLog(log)
    }

    suspend fun clear() {
        dao.clearLogs()
    }
}
