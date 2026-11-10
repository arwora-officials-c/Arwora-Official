package com.arwora.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val url: String,
    val platform: String,
    val isAudio: Boolean,
    val quality: String,
    val status: String, // "SUCCESS", "FAILED"
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
