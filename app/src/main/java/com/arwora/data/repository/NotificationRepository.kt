package com.arwora.data.repository

import com.arwora.data.db.NotificationDao
import com.arwora.data.db.NotificationEntity
import kotlinx.coroutines.flow.Flow

class NotificationRepository(private val dao: NotificationDao) {
    val allNotifications: Flow<List<NotificationEntity>> = dao.getAllNotifications()
    suspend fun insert(notification: NotificationEntity) = dao.insertNotification(notification)
    suspend fun deleteById(id: Int) = dao.deleteById(id)
    suspend fun clear() = dao.deleteAll()
}
