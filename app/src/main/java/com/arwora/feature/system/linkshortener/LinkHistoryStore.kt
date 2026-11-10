package com.arwora.feature.system.linkshortener

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class LinkHistoryStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("link_shortener_history", Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    
    private val listType = Types.newParameterizedType(List::class.java, HistoryItem::class.java)
    private val adapter = moshi.adapter<List<HistoryItem>>(listType)

    fun getHistory(): List<HistoryItem> {
        val json = prefs.getString("history_list", null) ?: return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveHistory(list: List<HistoryItem>) {
        try {
            val json = adapter.toJson(list)
            prefs.edit().putString("history_list", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addHistoryItem(originalUrl: String, shortenedUrl: String, provider: String) {
        val currentList = getHistory().toMutableList()
        currentList.removeAll { it.originalUrl == originalUrl && it.shortenedUrl == shortenedUrl }
        currentList.add(0, HistoryItem(originalUrl = originalUrl, shortenedUrl = shortenedUrl, provider = provider))
        if (currentList.size > 50) {
            currentList.removeAt(currentList.lastIndex)
        }
        saveHistory(currentList)
    }

    fun deleteHistoryItem(id: String) {
        val currentList = getHistory().toMutableList()
        currentList.removeAll { it.id == id }
        saveHistory(currentList)
    }

    fun clearAllHistory() {
        prefs.edit().remove("history_list").apply()
    }
}
