package com.arwora.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_history ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Insert
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notification_history SET isDismissed = 1 WHERE packageName = :packageName AND isDismissed = 0")
    suspend fun markDismissedByPackage(packageName: String)

    @Query("DELETE FROM notification_history WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM notification_history")
    suspend fun deleteAll()
}
