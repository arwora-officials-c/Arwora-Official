package com.arwora.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HistoryItem::class,
        FavoriteItem::class,
        AppHistoryEntity::class,
        SavedTextItem::class,
        DownloadHistoryEntity::class,
        NotificationEntity::class,
        LocationLogEntity::class,
        WeatherAlertPreferencesEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun appHistoryDao(): AppHistoryDao
    abstract fun savedTextDao(): SavedTextDao
    abstract fun downloadHistoryDao(): DownloadHistoryDao
    abstract fun notificationDao(): NotificationDao
    abstract fun locationLogDao(): LocationLogDao
    abstract fun weatherAlertPreferencesDao(): WeatherAlertPreferencesDao
}
