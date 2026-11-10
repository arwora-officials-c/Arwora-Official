package com.arwora.feature.clone.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clones")
data class CloneRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val basePackage: String,
    val displayName: String,
    val engine: String,
    val createdAt: Long,
    val badgeColor: Int,
    val locked: Boolean,
    val shortcutId: String?,
    val dataDir: String,
    val status: String
)
