package com.arwora.data.repository

import com.arwora.data.db.SavedTextDao
import com.arwora.data.db.SavedTextItem
import kotlinx.coroutines.flow.Flow

class SavedTextRepository(private val savedTextDao: SavedTextDao) {
    val allSavedText: Flow<List<SavedTextItem>> = savedTextDao.getAllSavedText()

    suspend fun insert(content: String) {
        savedTextDao.insert(SavedTextItem(content = content))
    }

    suspend fun delete(item: SavedTextItem) {
        savedTextDao.delete(item)
    }

    suspend fun clearAll() {
        savedTextDao.clearAll()
    }
}
