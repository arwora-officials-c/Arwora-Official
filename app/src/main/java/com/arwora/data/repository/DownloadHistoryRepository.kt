package com.arwora.data.repository

import com.arwora.data.db.DownloadHistoryDao
import com.arwora.data.db.DownloadHistoryEntity
import kotlinx.coroutines.flow.Flow

class DownloadHistoryRepository(private val downloadHistoryDao: DownloadHistoryDao) {
    val allHistory: Flow<List<DownloadHistoryEntity>> = downloadHistoryDao.getAllDownloadHistory()

    suspend fun insert(history: DownloadHistoryEntity) {
        downloadHistoryDao.insertDownloadHistory(history)
    }

    suspend fun deleteById(id: Long) {
        downloadHistoryDao.deleteDownloadHistoryById(id)
    }

    suspend fun clearHistory() {
        downloadHistoryDao.clearAllHistory()
    }
}
