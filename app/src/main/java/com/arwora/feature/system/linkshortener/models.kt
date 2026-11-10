package com.arwora.feature.system.linkshortener

import java.util.UUID

enum class ShortenerProvider(val label: String) {
    AUTO("Auto"),
    TINYURL("TinyURL"),
    ISGD("is.gd"),
    DAGD("da.gd")
}

enum class ShortenerStatus {
    IDLE,
    LOADING,
    SUCCESS,
    FAILED,
    NO_INTERNET,
    INVALID_URL
}

data class ShortenResult(
    val originalUrl: String,
    val shortenedUrl: String,
    val provider: ShortenerProvider,
    val status: ShortenerStatus,
    val errorMessage: String? = null
)

data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val originalUrl: String,
    val shortenedUrl: String,
    val provider: String,
    val timestamp: Long = System.currentTimeMillis()
)
