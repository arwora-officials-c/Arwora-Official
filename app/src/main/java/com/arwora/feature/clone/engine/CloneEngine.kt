package com.arwora.feature.clone.engine

import android.content.Context
import com.arwora.feature.clone.data.CloneRecord

interface CloneEngine {
    fun isSupported(context: Context): Boolean
    fun createClone(context: Context, basePackage: String, options: CloneOptions): CloneRecord?
    fun launch(context: Context, clone: CloneRecord)
    fun clearCache(context: Context, clone: CloneRecord)
    fun delete(context: Context, clone: CloneRecord)
}

data class CloneOptions(
    val name: String,
    val badgeColor: Int,
    val isLocked: Boolean,
    val createShortcut: Boolean
)
