package com.arwora.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_text_items")
data class SavedTextItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
