package com.arwora.feature.system.linkshortener

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class LinkShortenerRepository(
    private val api: LinkShortenerApi = LinkShortenerApi()
) {
    suspend fun shortenUrl(url: String, provider: ShortenerProvider): ShortenResult = withContext(Dispatchers.IO) {
        if (!isValidUrl(url)) {
            return@withContext ShortenResult(
                originalUrl = url,
                shortenedUrl = "",
                provider = provider,
                status = ShortenerStatus.INVALID_URL,
                errorMessage = "The provided URL is invalid. Please ensure it starts with http:// or https://"
            )
        }

        when (provider) {
            ShortenerProvider.TINYURL -> {
                try {
                    val result = api.shortenTinyUrl(url)
                    ShortenResult(url, result, ShortenerProvider.TINYURL, ShortenerStatus.SUCCESS)
                } catch (e: Exception) {
                    ShortenResult(url, "", ShortenerProvider.TINYURL, ShortenerStatus.FAILED, e.localizedMessage)
                }
            }
            ShortenerProvider.ISGD -> {
                try {
                    val result = api.shortenIsGd(url)
                    ShortenResult(url, result, ShortenerProvider.ISGD, ShortenerStatus.SUCCESS)
                } catch (e: Exception) {
                    ShortenResult(url, "", ShortenerProvider.ISGD, ShortenerStatus.FAILED, e.localizedMessage)
                }
            }
            ShortenerProvider.DAGD -> {
                try {
                    val result = api.shortenDaGd(url)
                    ShortenResult(url, result, ShortenerProvider.DAGD, ShortenerStatus.SUCCESS)
                } catch (e: Exception) {
                    ShortenResult(url, "", ShortenerProvider.DAGD, ShortenerStatus.FAILED, e.localizedMessage)
                }
            }
            ShortenerProvider.AUTO -> {
                var lastError: String? = null
                try {
                    val result = api.shortenTinyUrl(url)
                    return@withContext ShortenResult(url, result, ShortenerProvider.TINYURL, ShortenerStatus.SUCCESS)
                } catch (e: Exception) {
                    lastError = e.localizedMessage
                }

                try {
                    val result = api.shortenIsGd(url)
                    return@withContext ShortenResult(url, result, ShortenerProvider.ISGD, ShortenerStatus.SUCCESS)
                } catch (e: Exception) {
                    lastError = e.localizedMessage
                }

                try {
                    val result = api.shortenDaGd(url)
                    return@withContext ShortenResult(url, result, ShortenerProvider.DAGD, ShortenerStatus.SUCCESS)
                } catch (e: Exception) {
                    lastError = e.localizedMessage
                }

                ShortenResult(
                    originalUrl = url,
                    shortenedUrl = "",
                    provider = ShortenerProvider.AUTO,
                    status = ShortenerStatus.FAILED,
                    errorMessage = "All shortener providers failed. Details: $lastError"
                )
            }
        }
    }

    fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val regex = "^(https?://)?[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/\\S*)?$".toRegex()
        return url.matches(regex) && (url.startsWith("http://") || url.startsWith("https://"))
    }
}
