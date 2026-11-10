package com.arwora.feature.clone.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CloneDao {
    @Query("SELECT * FROM clones")
    fun getAllClones(): Flow<List<CloneRecord>>

    @Insert
    suspend fun insertClone(clone: CloneRecord): Long

    @Update
    suspend fun updateClone(clone: CloneRecord)

    @Delete
    suspend fun deleteClone(clone: CloneRecord)
    
    @Query("DELETE FROM clones")
    suspend fun deleteAll()
}
