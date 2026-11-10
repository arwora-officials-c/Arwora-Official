package com.arwora.feature.system.linkshortener

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LinkShortenerApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    fun shortenTinyUrl(url: String): String {
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val requestUrl = "https://tinyurl.com/api-create.php?url=$encodedUrl"
        
        val request = Request.Builder()
            .url(requestUrl)
            .header("User-Agent", "ArworaHyperToolkit/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("TinyURL request failed with code ${response.code}")
            }
            val result = response.body?.string()?.trim()
            if (result.isNullOrBlank() || !result.startsWith("http")) {
                throw IOException("Invalid response from TinyURL")
            }
            return result
        }
    }

    @Throws(IOException::class)
    fun shortenIsGd(url: String): String {
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val requestUrl = "https://is.gd/create.php?format=simple&url=$encodedUrl"
        
        val request = Request.Builder()
            .url(requestUrl)
            .header("User-Agent", "ArworaHyperToolkit/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("is.gd request failed with code ${response.code}")
            }
            val result = response.body?.string()?.trim()
            if (result.isNullOrBlank() || !result.startsWith("http")) {
                throw IOException("Invalid response from is.gd")
            }
            return result
        }
    }

    @Throws(IOException::class)
    fun shortenDaGd(url: String): String {
        val encodedUrl = URLEncoder.encode(url, "UTF-8")
        val requestUrl = "https://da.gd/s?url=$encodedUrl"
        
        val request = Request.Builder()
            .url(requestUrl)
            .header("User-Agent", "ArworaHyperToolkit/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("da.gd request failed with code ${response.code}")
            }
            val result = response.body?.string()?.trim()
            if (result.isNullOrBlank() || !result.startsWith("http")) {
                throw IOException("Invalid response from da.gd")
            }
            return result
        }
    }
}
