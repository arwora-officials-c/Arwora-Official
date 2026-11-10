package com.arwora.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedTextDao {
    @Query("SELECT * FROM saved_text_items ORDER BY timestamp DESC")
    fun getAllSavedText(): Flow<List<SavedTextItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SavedTextItem)

    @Delete
    suspend fun delete(item: SavedTextItem)

    @Query("DELETE FROM saved_text_items")
    suspend fun clearAll()
}
