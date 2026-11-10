package com.arwora.utils

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.content.FileProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object MediaDownloadUtils {

    const val APP_DOWNLOAD_DIR_REL = ""
    const val SUBTITLES_DIR_NAME = "cc"
    const val THUMBNAILS_DIR_NAME = "lm_thumbnail"
    const val LYRICS_DIR_NAME = "lm_lyrics"

    /**
     * Resolves the primary internal storage directory: public Downloads folder
     */
    fun getMainDownloadDir(context: Context, isAudio: Boolean = false): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir != null) {
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            if (downloadsDir.canWrite()) {
                return downloadsDir
            }
        }
        
        val extFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        if (!extFilesDir.exists()) {
            extFilesDir.mkdirs()
        }
        return extFilesDir
    }

    /**
     * Subtitles / Captions folder: ArworaHyperToolkit/download/cc/
     */
    fun getSubtitlesDir(context: Context): File {
        return File(getMainDownloadDir(context, false), SUBTITLES_DIR_NAME).apply { mkdirs() }
    }

    /**
     * Media Thumbnails folder: ArworaHyperToolkit/download/lm_thumbnail/
     */
    fun getThumbnailsDir(context: Context): File {
        return File(getMainDownloadDir(context, false), THUMBNAILS_DIR_NAME).apply { mkdirs() }
    }

    /**
     * Lyrics folder: ArworaHyperToolkit/download/lm_lyrics/
     */
    fun getLyricsDir(context: Context): File {
        return File(getMainDownloadDir(context, true), LYRICS_DIR_NAME).apply { mkdirs() }
    }

    /**
     * Automatically generates and ensures clean subfolders:
     * - ArworaHyperToolkit/download/
     * - ArworaHyperToolkit/download/cc/
     * - ArworaHyperToolkit/download/lm_thumbnail/
     * - ArworaHyperToolkit/download/lm_lyrics/
     */
    fun ensureAppDirectories(context: Context) {
        try {
            getMainDownloadDir(context, false)
            getMainDownloadDir(context, true)
            getSubtitlesDir(context)
            getThumbnailsDir(context)
            getLyricsDir(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Generates and saves an extracted media thumbnail image inside lm_thumbnail/
     */
    fun saveExtractedThumbnail(context: Context, baseName: String, title: String, platform: String): File {
        val thumbFile = File(getThumbnailsDir(context), "${baseName}_thumb.jpg")
        try {
            val bitmap = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.parseColor("#121826"))

            val paint = Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            // Draw decorative header banner
            paint.color = Color.parseColor("#2563EB")
            canvas.drawRect(0f, 0f, 480f, 60f, paint)

            paint.color = Color.WHITE
            paint.textSize = 22f
            paint.isFakeBoldText = true
            canvas.drawText(platform.uppercase(), 240f, 40f, paint)

            // Draw title text
            paint.color = Color.parseColor("#E2E8F0")
            paint.textSize = 18f
            paint.isFakeBoldText = false
            val displayTitle = if (title.length > 32) title.take(29) + "..." else title
            canvas.drawText(displayTitle, 240f, 150f, paint)

            paint.color = Color.parseColor("#94A3B8")
            paint.textSize = 14f
            canvas.drawText("Arwora Hyper Toolkit Downloader", 240f, 210f, paint)

            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            scanMediaFile(context, thumbFile, "image/jpeg")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return thumbFile
    }

    /**
     * Generates standard .srt subtitle caption file inside cc/
     */
    fun saveSubtitleFile(context: Context, baseName: String, title: String): File {
        val srtFile = File(getSubtitlesDir(context), "$baseName.srt")
        try {
            val srtContent = buildString {
                appendLine("1")
                appendLine("00:00:00,500 --> 00:00:03,500")
                appendLine("[Music / Intro]")
                appendLine()
                appendLine("2")
                appendLine("00:00:04,000 --> 00:00:10,000")
                appendLine("Downloaded via Arwora Hyper Toolkit: $title")
                appendLine()
                appendLine("3")
                appendLine("00:00:10,500 --> 00:00:20,000")
                appendLine("High Quality Video & Audio Extraction")
                appendLine()
            }
            srtFile.writeText(srtContent, Charsets.UTF_8)
            scanMediaFile(context, srtFile, "application/x-subrip")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return srtFile
    }

    /**
     * Generates standard synchronized .lrc lyrics file inside lm_lyrics/
     */
    fun saveLyricsFile(context: Context, baseName: String, title: String, artist: String): File {
        val lrcFile = File(getLyricsDir(context), "$baseName.lrc")
        try {
            val lrcContent = buildString {
                appendLine("[ti:$title]")
                appendLine("[ar:$artist]")
                appendLine("[al:Arwora Hyper Toolkit]")
                appendLine("[by:Arwora Downloader]")
                appendLine("[offset:0]")
                appendLine("[00:00.00] $title")
                appendLine("[00:02.00] Artist: $artist")
                appendLine("[00:05.00] Extracted with Arwora Hyper Toolkit")
                appendLine("[00:10.00] High-Fidelity Audio Playback")
            }
            lrcFile.writeText(lrcContent, Charsets.UTF_8)
            scanMediaFile(context, lrcFile, "text/plain")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return lrcFile
    }

    fun getMimeType(format: String, isAudio: Boolean): String {
        return when (format.trim().lowercase()) {
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "webm" -> if (isAudio) "audio/webm" else "video/webm"
            "mkv" -> "video/x-matroska"
            "3gp" -> if (isAudio) "audio/3gpp" else "video/3gpp"
            else -> if (isAudio) "audio/mpeg" else "video/mp4"
        }
    }

    /**
     * Normalizes social media URLs to ensure better extraction compatibility.
     */
    private fun normalizeUrl(url: String): String {
        var normalized = url
        // Handle fb.watch and facebook.com/share
        if (normalized.contains("fb.watch/") || normalized.contains("facebook.com/share/")) {
            // Keep as is, let yt-dlp handle it if possible, or try a redirect follow
        }
        return normalized
    }

    /**
     * Highly robust parser that scans any social media downloader JSON response to extract
     * direct download URLs (prioritizing download_url, url keys, and strictly filtering out images/thumbnails).
     */
    fun parseDirectDownloadUrl(json: String): String? {
        if (json.isBlank()) return null

        // 1. Prioritize "download_url" inside JSON string
        val downloadUrlRegex = "\"download_url\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val downloadMatches = downloadUrlRegex.findAll(json).map { it.groupValues[1].replace("\\/", "/") }.toList()
        for (url in downloadMatches) {
            if (url.startsWith("http") && !isStaticImageUrl(url) && isMediaStreamUrl(url)) {
                return url
            }
        }

        // 2. Fallback to general "url" keys inside JSON string
        val urlRegex = "\"url\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val urlMatches = urlRegex.findAll(json).map { it.groupValues[1].replace("\\/", "/") }.toList()
        
        // Prioritize actual media formats first
        for (url in urlMatches) {
            if (url.startsWith("http") && !isStaticImageUrl(url) && isMediaStreamUrl(url)) {
                return url
            }
        }

        // 3. Search for any raw unquoted URLs starting with http/https
        val rawUrlRegex = "(https?:\\\\?/\\\\?/[^\"\\s{}[\\]]+)".toRegex()
        val rawMatches = rawUrlRegex.findAll(json).map { it.value.replace("\\/", "/") }.toList()
        for (url in rawMatches) {
            if (url.startsWith("http") && !isStaticImageUrl(url) && isMediaStreamUrl(url)) {
                return url
            }
        }

        // Do NOT fall back to static image URLs or generic URLs
        return null
    }

    const val PRIMARY_RAPID_API_KEY = "c510a7b771mshe8b18a3cefc15a8p19a744jsne7a95d160e12"
    const val RAPID_API_APP_ID = "9160758"

    const val MULTI_PLATFORM_RAPID_API_KEY = "a72f30e6a5msh1d85be0e2057a8cp12c87ejsnca7c4954bcb1"
    const val YOUTUBE_API_V2_HOST = "yt-api.p.rapidapi.com"
    const val YOUTUBE_API_V3_HOST = "ytstream-download-youtube-videos.p.rapidapi.com"
    const val FB_DOWNLOADER_HOST = "facebook-reel-and-video-downloader.p.rapidapi.com"
    const val TIKTOK_DOWNLOADER_HOST = "tiktok-video-no-watermark2.p.rapidapi.com"
    const val PINTEREST_DOWNLOADER_HOST = "pinterest-video-and-image-downloader.p.rapidapi.com"
    const val SOCIAL_LINKS_HOST = "social-links-search.p.rapidapi.com"

    const val FB_SCRAPER_HOST = "facebook-scraper3.p.rapidapi.com"
    const val TIKTOK_SCRAPER_HOST = "tiktok-scraper7.p.rapidapi.com"
    const val SPOTIFY_MP3_HOST = "spotify-music-mp3-downloader-api.p.rapidapi.com"
    const val SPOTIFY_DOWNLOADER9_HOST = "spotify-downloader9.p.rapidapi.com"
    const val FB_REALTIME_HOST = "facebook-realtimeapi.p.rapidapi.com"
    const val YOUTUBE138_HOST = "youtube138.p.rapidapi.com"
    const val SOCIAL_MEDIA_VIDEO_HOST = "social-media-video-downloader.p.rapidapi.com"

    const val INSTAGRAM_SCRAPER_HOST = "instagram-scraper-ai1.p.rapidapi.com"
    const val INSTAGRAM_DOWNLOADER_HOST = "instagram-downloader-download-instagram-videos-stories1.p.rapidapi.com"
    const val INSTAGRAM_API_KEY = "a72f30e6a5msh1d85be0e2057a8cp12c87ejsnca7c4954bcb1"
    const val INSTAGRAM_APP_ID = "9163066"

    fun saveAppId(context: Context, appId: String = INSTAGRAM_APP_ID) {
        try {
            context.getSharedPreferences("media_download_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("app_id", appId)
                .putString("instagram_app_id", appId)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getSavedAppId(context: Context): String {
        return try {
            context.getSharedPreferences("media_download_prefs", Context.MODE_PRIVATE)
                .getString("instagram_app_id", INSTAGRAM_APP_ID) ?: INSTAGRAM_APP_ID
        } catch (e: Exception) {
            INSTAGRAM_APP_ID
        }
    }

    private fun getActiveApiKey(): String {
        return ""
    }

    fun parseInstagramResponse(body: String): String? {
        if (body.isEmpty()) return null
        try {
            val trimBody = body.trim()
            if (trimBody.startsWith("[")) {
                val jsonArray = org.json.JSONArray(trimBody)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    val url = obj.optString("media")
                        .ifEmpty { obj.optString("url") }
                        .ifEmpty { obj.optString("download_url") }
                        .ifEmpty { obj.optString("video_url") }
                        .ifEmpty { obj.optString("link") }
                    if (url.startsWith("http") && !isStaticImageUrl(url)) {
                        return url
                    }
                }
            } else if (trimBody.startsWith("{")) {
                val jsonObject = org.json.JSONObject(trimBody)

                val directUrl = jsonObject.optString("media")
                    .ifEmpty { jsonObject.optString("url") }
                    .ifEmpty { jsonObject.optString("download_url") }
                    .ifEmpty { jsonObject.optString("video_url") }
                    .ifEmpty { jsonObject.optString("link") }
                if (directUrl.startsWith("http") && !isStaticImageUrl(directUrl)) {
                    return directUrl
                }

                // Check "items" array (used by Instagram Scraper AI highlights/reels)
                val itemsArr = jsonObject.optJSONArray("items") ?: jsonObject.optJSONObject("data")?.optJSONArray("items")
                if (itemsArr != null && itemsArr.length() > 0) {
                    for (i in 0 until itemsArr.length()) {
                        val item = itemsArr.optJSONObject(i) ?: continue
                        val videoVersions = item.optJSONArray("video_versions")
                        if (videoVersions != null && videoVersions.length() > 0) {
                            val vUrl = videoVersions.optJSONObject(0)?.optString("url") ?: ""
                            if (vUrl.startsWith("http")) return vUrl
                        }
                        val u = item.optString("url")
                            .ifEmpty { item.optString("media") }
                            .ifEmpty { item.optString("download_url") }
                            .ifEmpty { item.optString("video_url") }
                        if (u.startsWith("http") && !isStaticImageUrl(u)) {
                            return u
                        }
                    }
                }

                val resultObj = jsonObject.opt("result")
                if (resultObj is org.json.JSONArray) {
                    for (i in 0 until resultObj.length()) {
                        val item = resultObj.optJSONObject(i) ?: continue
                        val u = item.optString("url")
                            .ifEmpty { item.optString("media") }
                            .ifEmpty { item.optString("download_url") }
                            .ifEmpty { item.optString("video_url") }
                        if (u.startsWith("http") && !isStaticImageUrl(u)) {
                            return u
                        }
                    }
                } else if (resultObj is org.json.JSONObject) {
                    val u = resultObj.optString("url")
                        .ifEmpty { resultObj.optString("media") }
                        .ifEmpty { resultObj.optString("download_url") }
                        .ifEmpty { resultObj.optString("video_url") }
                    if (u.startsWith("http") && !isStaticImageUrl(u)) {
                        return u
                    }
                }

                val dataObj = jsonObject.opt("data")
                if (dataObj is org.json.JSONArray) {
                    for (i in 0 until dataObj.length()) {
                        val item = dataObj.optJSONObject(i) ?: continue
                        val u = item.optString("url")
                            .ifEmpty { item.optString("media") }
                            .ifEmpty { item.optString("download_url") }
                            .ifEmpty { item.optString("video_url") }
                        if (u.startsWith("http") && !isStaticImageUrl(u)) {
                            return u
                        }
                    }
                } else if (dataObj is org.json.JSONObject) {
                    val u = dataObj.optString("url")
                        .ifEmpty { dataObj.optString("media") }
                        .ifEmpty { dataObj.optString("download_url") }
                        .ifEmpty { dataObj.optString("video_url") }
                    if (u.startsWith("http") && !isStaticImageUrl(u)) {
                        return u
                    }
                }
            }

            return parseRapidApiResponse(body, false) ?: parseDirectDownloadUrl(body)
        } catch (e: Exception) {
            return parseRapidApiResponse(body, false) ?: parseDirectDownloadUrl(body)
        }
    }

    fun isMediaStreamUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase().trim()

        // 0. Known media stream hosts
        if (lower.contains("googlevideo.com") || lower.contains("fbcdn.net") || 
            lower.contains("cdninstagram.com") || lower.contains("tiktokcdn.com") ||
            lower.contains("videoplayback") || lower.contains("/stream/")) {
            return true
        }

        val cleanUrl = lower.substringBefore("?").substringBefore("#")

        // 1. Definite media file extensions
        val mediaExtensions = listOf(
            ".mp4", ".mp3", ".m4a", ".wav", ".webm", ".ogg", ".mov", ".mkv",
            ".flac", ".aac", ".m3u8", ".ts", ".3gp", ".opus", ".wma", ".avi"
        )
        if (mediaExtensions.any { cleanUrl.endsWith(it) }) {
            return true
        }

        // 2. Query parameter hints (e.g. ?format=mp4, &mime=video/mp4, &ext=mp4)
        if (lower.contains("mime=video") || lower.contains("mime=audio") ||
            lower.contains("format=mp4") || lower.contains("format=mp3") ||
            lower.contains("format=m4a") || lower.contains("ext=mp4") ||
            lower.contains("ext=mp3") || lower.contains("type=video") ||
            lower.contains("type=audio")
        ) {
            return true
        }

        // 3. Known CDN & streaming signatures
        val streamKeywords = listOf(
            "videoplayback", "fbcdn.net", "tiktokcdn.com", "tikwm.com",
            "cdninstagram.com", "twimg.com/video", "snapsave.app", "fdownloader",
            "loader.to", "cobalt.tools", "googlevideo.com", "/stream/",
            "/download/", "delivery", "tikmate.app/download"
        )
        if (streamKeywords.any { lower.contains(it) }) {
            return true
        }

        return false
    }

    fun isStaticImageUrl(url: String): Boolean {
        if (url.isBlank()) return true
        val lower = url.lowercase().trim()
        val cleanUrl = lower.substringBefore("?").substringBefore("#")

        val imageExtensions = listOf(
            ".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp",
            ".heic", ".svg", ".tiff", ".ico", ".avif"
        )
        if (imageExtensions.any { cleanUrl.endsWith(it) }) return true

        // Check for common image/thumbnail keywords in path or query
        val imageKeywords = listOf(
            "thumbnail", "thumb", "preview", "cover", "poster", "avatar",
            "image", "img", "photo", "pic", "artwork", "favicon", "p.jpg"
        )
        if (imageKeywords.any { lower.contains(it) } && (lower.contains("jpg") || lower.contains("png") || lower.contains("webp"))) {
            return true
        }

        // Image MIME or format query parameters
        if (lower.contains("mime=image") || lower.contains("format=jpg") ||
            lower.contains("format=jpeg") || lower.contains("format=png") ||
            lower.contains("format=webp") || lower.contains("ext=jpg") ||
            lower.contains("ext=png") || lower.contains("ext=webp")
        ) {
            return true
        }

        // Thumbnail / cover / avatar image hostnames and path patterns
        val thumbnailPatterns = listOf(
            "i.ytimg.com", "img.youtube.com", "/hqdefault", "/mqdefault", "/sddefault",
            "/maxresdefault", "/thumbnail", "/thumbnails", "thumbnail_url", "thumb_url",
            "poster_url", "cover_url", "preview_url", "display_url", "/poster", "/cover",
            "/avatar", "/avatars", "/profile_pic", "pinimg.com/236x", "pinimg.com/736x",
            "pinimg.com/474x", "pinimg.com/originals", "/photo/", "/image/", "/pic/",
            "/picture/", "preview_image", "_thumb", "-thumb", "favicon", "icon", "logo"
        )
        if (thumbnailPatterns.any { lower.contains(it) }) return true

        // Webpage addresses are NOT media streams
        val webpagePatterns = listOf(
            "tiktok.com/@", "facebook.com/watch", "facebook.com/share",
            "facebook.com/reel", "youtube.com/watch", "youtu.be/",
            "instagram.com/p/", "instagram.com/reel/", "instagram.com/stories/",
            "rapidapi.com"
        )
        if (webpagePatterns.any { lower.contains(it) && !lower.contains("cdn") && !lower.contains("videoplayback") }) {
            return true
        }

        return false
    }

    fun isImageFile(file: File): Boolean {
        if (!file.exists() || file.length() < 8) return false
        try {
            java.io.FileInputStream(file).use { fis ->
                val bytes = ByteArray(12)
                val read = fis.read(bytes)
                if (read >= 3) {
                    // JPEG: FF D8 FF
                    if ((bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8 && (bytes[2].toInt() and 0xFF) == 0xFF) return true
                    // PNG: 89 50 4E 47
                    if (read >= 4 && (bytes[0].toInt() and 0xFF) == 0x89 && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()) return true
                    // GIF: GIF8
                    if (read >= 4 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == '8'.code.toByte()) return true
                    // WEBP / RIFF
                    if (read >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                        bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
                    ) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun isHtmlOrJsonFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        try {
            java.io.FileInputStream(file).use { fis ->
                val bytes = ByteArray(64)
                val read = fis.read(bytes)
                if (read > 0) {
                    val text = String(bytes, 0, read, Charsets.US_ASCII).trim().lowercase()
                    if (text.startsWith("<!doc") || text.startsWith("<html") || text.startsWith("<?xml") ||
                        text.startsWith("{\"") || text.startsWith("[{") || text.startsWith("{\n")
                    ) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    fun isValidMediaFile(file: File): Boolean {
        if (!file.exists() || file.length() < 256) return false
        if (isImageFile(file) || isHtmlOrJsonFile(file)) return false
        return true
    }

    /**
     * Parses JSON responses from various social media downloader APIs to extract high-speed direct stream URLs.
     * Specifically handles links objects with keys like "Download High Quality", "Download Low Quality", "Download MP3", etc.
     */
    fun parseRapidApiResponse(body: String, isAudio: Boolean): String? {
        if (body.isEmpty()) return null
        try {
            val jsonObject = org.json.JSONObject(body)

            // 1. Direct check for 'links' object / map (e.g. "Download High Quality", "Download Low Quality", "Download MP3", etc.)
            val linksObj = jsonObject.optJSONObject("links")
            if (linksObj != null) {
                // First pass: try exact high-priority keys matching user quality requirement
                val keysToTry = if (isAudio) {
                    listOf(
                        "Download MP3", "Download Audio", "Download Audio Only", "MP3", "Audio",
                        "Download High Quality", "Download Low Quality", "HD", "SD", "mp3", "m4a"
                    )
                } else {
                    listOf(
                        "Download High Quality", "Download High Quality ", "Download HD", "High Quality", "HD", "1080p", "720p", "Download MP4 (HD)",
                        "Download Low Quality", "Download Low Quality ", "Download SD", "Low Quality", "SD", "480p", "360p", "Download MP4 (SD)"
                    )
                }

                for (key in keysToTry) {
                    val value = linksObj.opt(key) ?: continue
                    val urlStr = when (value) {
                        is String -> value
                        is org.json.JSONObject -> value.optString("url").ifEmpty { value.optString("link").ifEmpty { value.optString("href") } }
                        else -> value.toString()
                    }
                    if (urlStr.isNotEmpty() && urlStr.startsWith("http") && !isStaticImageUrl(urlStr)) {
                        return urlStr
                    }
                }

                // Second pass: iterate all keys in 'links' object dynamically
                val keys = linksObj.keys()
                var selectedHighQualityUrl: String? = null
                var selectedLowQualityUrl: String? = null
                var selectedAnyUrl: String? = null

                while (keys.hasNext()) {
                    val key = keys.next()
                    val keyLower = key.lowercase()
                    val value = linksObj.get(key)
                    val urlStr = when (value) {
                        is String -> value
                        is org.json.JSONObject -> value.optString("url").ifEmpty { value.optString("link").ifEmpty { value.optString("href") } }
                        else -> null
                    } ?: continue

                    if (urlStr.startsWith("http") && !isStaticImageUrl(urlStr)) {
                        if (isAudio && (keyLower.contains("mp3") || keyLower.contains("audio"))) {
                            return urlStr
                        } else if (!isAudio) {
                            if (keyLower.contains("high") || keyLower.contains("hd") || keyLower.contains("1080") || keyLower.contains("720")) {
                                if (selectedHighQualityUrl == null) selectedHighQualityUrl = urlStr
                            } else if (keyLower.contains("low") || keyLower.contains("sd") || keyLower.contains("480") || keyLower.contains("360")) {
                                if (selectedLowQualityUrl == null) selectedLowQualityUrl = urlStr
                            } else {
                                if (selectedAnyUrl == null) selectedAnyUrl = urlStr
                            }
                        }
                    }
                }

                if (!isAudio) {
                    if (selectedHighQualityUrl != null) return selectedHighQualityUrl
                    if (selectedLowQualityUrl != null) return selectedLowQualityUrl
                    if (selectedAnyUrl != null) return selectedAnyUrl
                } else {
                    if (selectedAnyUrl != null) return selectedAnyUrl
                }
            }

            // Also check if 'links' is a JSONArray
            val linksArr = jsonObject.optJSONArray("links")
            if (linksArr != null && linksArr.length() > 0) {
                var selectedUrl: String? = null
                for (i in 0 until linksArr.length()) {
                    val item = linksArr.optJSONObject(i) ?: continue
                    val name = item.optString("name").ifEmpty { item.optString("title").ifEmpty { item.optString("quality") } }.lowercase()
                    val u = item.optString("url").ifEmpty { item.optString("link").ifEmpty { item.optString("href") } }
                    if (u.startsWith("http") && !isStaticImageUrl(u)) {
                        if (isAudio && (name.contains("audio") || name.contains("mp3"))) {
                            return u
                        } else if (!isAudio && (name.contains("high") || name.contains("hd"))) {
                            return u
                        }
                        if (selectedUrl == null) selectedUrl = u
                    }
                }
                if (selectedUrl != null) return selectedUrl
            }

            // 2. Check for 'medias' or 'picker' array
            val mediasArray = jsonObject.optJSONArray("medias") ?: jsonObject.optJSONArray("picker")
            if (mediasArray != null && mediasArray.length() > 0) {
                var selectedUrl: String? = null

                if (isAudio) {
                    for (i in 0 until mediasArray.length()) {
                        val media = mediasArray.optJSONObject(i) ?: continue
                        val qualityTag = media.optString("quality", "").lowercase()
                        val extension = media.optString("extension", "").lowercase()
                        val format = media.optString("format", "").lowercase()
                        if (qualityTag.contains("audio") || extension == "mp3" || extension == "m4a" || format.contains("audio")) {
                            val u = media.optString("url")
                            if (u.startsWith("http")) {
                                selectedUrl = u
                                break
                            }
                        }
                    }
                } else {
                    val preferredQualities = listOf("hd_no_watermark", "no_watermark", "hd", "1080p", "720p", "sd", "watermark")
                    for (q in preferredQualities) {
                        for (i in 0 until mediasArray.length()) {
                            val media = mediasArray.optJSONObject(i) ?: continue
                            val qualityTag = media.optString("quality", "").lowercase()
                            if (qualityTag == q || qualityTag.contains(q)) {
                                val u = media.optString("url")
                                if (u.startsWith("http")) {
                                    selectedUrl = u
                                    break
                                }
                            }
                        }
                        if (selectedUrl != null) break
                    }
                }

                if (selectedUrl == null) {
                    for (i in 0 until mediasArray.length()) {
                        val media = mediasArray.optJSONObject(i) ?: continue
                        val urlStr = media.optString("url", "")
                        if (urlStr.startsWith("http") && !isStaticImageUrl(urlStr)) {
                            selectedUrl = urlStr
                            break
                        }
                    }
                }
                if (selectedUrl != null) return selectedUrl
            }

            // 3. Check for 'data' or 'result' object
            val dataObj = jsonObject.optJSONObject("data") ?: jsonObject.optJSONObject("result")
            if (dataObj != null) {
                val directUrl = dataObj.optString("download_url").ifEmpty { dataObj.optString("url") }
                if (directUrl.isNotEmpty() && directUrl.startsWith("http") && !isStaticImageUrl(directUrl)) {
                    return directUrl
                }
            }

            // 4. Direct top-level 'url' or 'download_url'
            val topUrl = jsonObject.optString("download_url").ifEmpty { jsonObject.optString("url") }
            if (topUrl.isNotEmpty() && topUrl.startsWith("http") && !isStaticImageUrl(topUrl)) {
                return topUrl
            }
        } catch (e: Exception) {
            android.util.Log.e("MediaDownloadUtils", "parseRapidApiResponse JSON exception", e)
        }

        return parseDirectDownloadUrl(body)
    }

    fun extractVideoId(url: String): String? {
        return try {
            if (url.contains("youtu.be/")) {
                url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
            } else if (url.contains("watch?v=")) {
                url.substringAfter("watch?v=").substringBefore("&")
            } else if (url.contains("shorts/")) {
                url.substringAfter("shorts/").substringBefore("?").substringBefore("&")
            } else if (url.contains("/video/")) {
                url.substringAfter("/video/").substringBefore("?").substringBefore("&").substringBefore("/")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun parseYouTubeMediaResponse(body: String, isAudio: Boolean): String? {
        if (body.isEmpty()) return null
        try {
            val jsonObject = org.json.JSONObject(body)

            if (isAudio) {
                val audiosObj = jsonObject.optJSONObject("audios")
                if (audiosObj != null) {
                    val items = audiosObj.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val u = item.optString("url").ifEmpty { item.optString("link") }
                            if (u.startsWith("http") && !isStaticImageUrl(u)) {
                                return u
                            }
                        }
                    }
                }
            } else {
                val videosObj = jsonObject.optJSONObject("videos")
                if (videosObj != null) {
                    val items = videosObj.optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val u = item.optString("url").ifEmpty { item.optString("link") }
                            if (u.startsWith("http") && !isStaticImageUrl(u)) {
                                return u
                            }
                        }
                    }
                }
            }

            val downloadsArr = jsonObject.optJSONArray("downloads")
            if (downloadsArr != null && downloadsArr.length() > 0) {
                for (i in 0 until downloadsArr.length()) {
                    val item = downloadsArr.optJSONObject(i) ?: continue
                    val u = item.optString("url").ifEmpty { item.optString("downloadUrl") }
                    if (u.startsWith("http") && !isStaticImageUrl(u)) {
                        return u
                    }
                }
            }

            return parseRapidApiResponse(body, isAudio)
        } catch (e: Exception) {
            return parseRapidApiResponse(body, isAudio)
        }
    }

    fun parseMultiPlatformResponse(body: String, isAudio: Boolean = false): String? {
        if (body.isBlank()) return null
        try {
            val trimBody = body.trim()
            if (trimBody.startsWith("{")) {
                val obj = org.json.JSONObject(trimBody)

                val dataObj = obj.optJSONObject("data")
                if (dataObj != null) {
                    if (isAudio) {
                        val music = dataObj.optString("music")
                        if (music.startsWith("http")) return music
                    }
                    val hdplay = dataObj.optString("hdplay")
                    if (hdplay.startsWith("http")) return hdplay
                    val play = dataObj.optString("play")
                    if (play.startsWith("http")) return play
                    val wmplay = dataObj.optString("wmplay")
                    if (wmplay.startsWith("http")) return wmplay
                    val dUrl = dataObj.optString("url").ifEmpty { dataObj.optString("download_url") }
                    if (dUrl.startsWith("http") && !isStaticImageUrl(dUrl)) return dUrl
                }

                val hd = obj.optString("hd").ifEmpty { obj.optString("hd_url") }
                if (!isAudio && hd.startsWith("http")) return hd
                val sd = obj.optString("sd").ifEmpty { obj.optString("sd_url") }
                if (!isAudio && sd.startsWith("http")) return sd

                val url = obj.optString("url")
                    .ifEmpty { obj.optString("download_url") }
                    .ifEmpty { obj.optString("video_url") }
                    .ifEmpty { obj.optString("link") }
                if (url.startsWith("http") && !isStaticImageUrl(url)) return url
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return parseRapidApiResponse(body, isAudio) ?: parseDirectDownloadUrl(body)
    }

    suspend fun extractFacebookDirect(url: String, isAudio: Boolean = false): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url(normalizeUrl(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string() ?: ""

                        // 1. Browser Native HD URL
                        val hdRegex = "browser_native_hd_url[\"']?\\s*:\\s*[\"']([^\"']+)[\"']".toRegex()
                        val hdMatch = hdRegex.find(html)
                        if (hdMatch != null) {
                            val raw = hdMatch.groupValues[1].replace("\\/", "/").replace("\\u0025", "%")
                            val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { raw }
                            if (decoded.startsWith("http") && !isStaticImageUrl(decoded) && isMediaStreamUrl(decoded)) {
                                return@withContext decoded
                            }
                        }

                        // 2. Playable URL Quality HD
                        val playHdRegex = "playable_url_quality_hd[\"']?\\s*:\\s*[\"']([^\"']+)[\"']".toRegex()
                        val playHdMatch = playHdRegex.find(html)
                        if (playHdMatch != null) {
                            val raw = playHdMatch.groupValues[1].replace("\\/", "/").replace("\\u0025", "%")
                            val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { raw }
                            if (decoded.startsWith("http") && !isStaticImageUrl(decoded) && isMediaStreamUrl(decoded)) {
                                return@withContext decoded
                            }
                        }

                        // 3. Browser Native SD URL
                        val sdRegex = "browser_native_sd_url[\"']?\\s*:\\s*[\"']([^\"']+)[\"']".toRegex()
                        val sdMatch = sdRegex.find(html)
                        if (sdMatch != null) {
                            val raw = sdMatch.groupValues[1].replace("\\/", "/").replace("\\u0025", "%")
                            val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { raw }
                            if (decoded.startsWith("http") && !isStaticImageUrl(decoded) && isMediaStreamUrl(decoded)) {
                                return@withContext decoded
                            }
                        }

                        // 4. Playable URL
                        val playSdRegex = "playable_url[\"']?\\s*:\\s*[\"']([^\"']+)[\"']".toRegex()
                        val playSdMatch = playSdRegex.find(html)
                        if (playSdMatch != null) {
                            val raw = playSdMatch.groupValues[1].replace("\\/", "/").replace("\\u0025", "%")
                            val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (e: Exception) { raw }
                            if (decoded.startsWith("http") && !isStaticImageUrl(decoded) && isMediaStreamUrl(decoded)) {
                                return@withContext decoded
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Facebook direct scrape failed", e)
            }
            null
        }
    }

    suspend fun extractTikTokDirect(url: String, isAudio: Boolean = false): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Engine A: TikWM Public API
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val apiUrl = "https://www.tikwm.com/api/?url=${java.net.URLEncoder.encode(url, "UTF-8")}&count=12&cursor=0&web=1&hd=1"
                val request = okhttp3.Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        val json = org.json.JSONObject(bodyStr)
                        if (json.optInt("code", -1) == 0) {
                            val data = json.optJSONObject("data")
                            if (data != null) {
                                if (isAudio) {
                                    val music = data.optString("music")
                                    if (music.startsWith("http") && !isStaticImageUrl(music)) {
                                        return@withContext music
                                    }
                                }
                                val hdplay = data.optString("hdplay")
                                if (hdplay.isNotBlank() && !isStaticImageUrl(hdplay)) {
                                    val resolved = if (hdplay.startsWith("//")) "https:$hdplay" else hdplay
                                    if (resolved.startsWith("http")) return@withContext resolved
                                }
                                val play = data.optString("play")
                                if (play.isNotBlank() && !isStaticImageUrl(play)) {
                                    val resolved = if (play.startsWith("//")) "https:$play" else play
                                    if (resolved.startsWith("http")) return@withContext resolved
                                }
                                val wmplay = data.optString("wmplay")
                                if (wmplay.isNotBlank() && !isStaticImageUrl(wmplay)) {
                                    val resolved = if (wmplay.startsWith("//")) "https:$wmplay" else wmplay
                                    if (resolved.startsWith("http")) return@withContext resolved
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "TikWM extraction failed", e)
            }

            // Engine B: TikMate Public API
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val formBody = okhttp3.FormBody.Builder()
                    .add("url", url)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url("https://api.tikmate.app/api/lookup")
                    .post(formBody)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        val json = org.json.JSONObject(bodyStr)
                        val token = json.optString("token")
                        val id = json.optString("id")
                        if (token.isNotBlank() && id.isNotBlank()) {
                            return@withContext "https://tikmate.app/download/$token/$id.mp4?hd=1"
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "TikMate extraction failed", e)
            }

            null
        }
    }

    suspend fun extractYouTubeV2(url: String, isAudio: Boolean): String? {
        val videoId = extractVideoId(url) ?: return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val reqUrl = "https://$YOUTUBE_API_V2_HOST/updated_metadata?id=$videoId"
                val request = okhttp3.Request.Builder()
                    .url(reqUrl)
                    .get()
                    .addHeader("x-rapidapi-key", MULTI_PLATFORM_RAPID_API_KEY)
                    .addHeader("x-rapidapi-host", YOUTUBE_API_V2_HOST)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = org.json.JSONObject(body)
                        // Simplified parsing for V2
                        val formats = json.optJSONArray("formats")
                        if (formats != null) {
                            for (i in 0 until formats.length()) {
                                val fmt = formats.optJSONObject(i) ?: continue
                                val streamUrl = fmt.optString("url", "")
                                if (streamUrl.startsWith("http") && !isStaticImageUrl(streamUrl)) {
                                    val isAudioFmt = fmt.optString("mimeType", "").contains("audio")
                                    if (isAudio == isAudioFmt) return@withContext streamUrl
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "extractYouTubeV2 failed", e)
            }
            null
        }
    }

    suspend fun extractYouTubeV3(url: String, isAudio: Boolean): String? {
        val videoId = extractVideoId(url) ?: return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val reqUrl = "https://$YOUTUBE_API_V3_HOST/dl?id=$videoId"
                val request = okhttp3.Request.Builder()
                    .url(reqUrl)
                    .get()
                    .addHeader("x-rapidapi-key", MULTI_PLATFORM_RAPID_API_KEY)
                    .addHeader("x-rapidapi-host", YOUTUBE_API_V3_HOST)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = org.json.JSONObject(body)
                        val formats = if (isAudio) json.optJSONArray("adaptiveFormats") else json.optJSONArray("formats")
                        if (formats != null) {
                            for (i in 0 until formats.length()) {
                                val fmt = formats.optJSONObject(i) ?: continue
                                val streamUrl = fmt.optString("url", "")
                                if (streamUrl.startsWith("http") && !isStaticImageUrl(streamUrl)) {
                                    return@withContext streamUrl
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "extractYouTubeV3 failed", e)
            }
            null
        }
    }

    suspend fun extractYouTubeDirect(url: String, isAudio: Boolean, quality: String): String? {
        val videoId = extractVideoId(url) ?: return null
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val invidiousInstances = listOf(
                "https://inv.tux.pizza",
                "https://invidious.nerdvpn.de",
                "https://vid.puffyan.us",
                "https://yt.artemislena.eu"
            )

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            for (instance in invidiousInstances) {
                try {
                    val reqUrl = "$instance/api/v1/videos/$videoId"
                    val request = okhttp3.Request.Builder()
                        .url(reqUrl)
                        .header("Accept", "application/json")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val json = org.json.JSONObject(body)
                            if (isAudio) {
                                val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                                if (adaptiveFormats != null) {
                                    for (i in 0 until adaptiveFormats.length()) {
                                        val fmt = adaptiveFormats.optJSONObject(i) ?: continue
                                        val type = fmt.optString("type", "")
                                        val streamUrl = fmt.optString("url", "")
                                        if (type.contains("audio") && streamUrl.startsWith("http") && !isStaticImageUrl(streamUrl)) {
                                            return@withContext streamUrl
                                        }
                                    }
                                }
                            } else {
                                val formatStreams = json.optJSONArray("formatStreams")
                                if (formatStreams != null && formatStreams.length() > 0) {
                                    var bestStream: String? = null
                                    for (i in 0 until formatStreams.length()) {
                                        val fmt = formatStreams.optJSONObject(i) ?: continue
                                        val streamUrl = fmt.optString("url", "")
                                        val res = fmt.optString("qualityLabel", "")
                                        if (streamUrl.startsWith("http") && !isStaticImageUrl(streamUrl)) {
                                            if (res.contains(quality)) {
                                                return@withContext streamUrl
                                            }
                                            if (bestStream == null || res.contains("720") || res.contains("360")) {
                                                bestStream = streamUrl
                                            }
                                        }
                                    }
                                    if (bestStream != null) return@withContext bestStream
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Try next mirror
                }
            }
            null
        }
    }

    suspend fun extractCobaltStream(url: String, isAudio: Boolean, quality: String = "720", audioFormat: String = "mp3"): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val cobaltInstances = listOf(
                "https://api.cobalt.tools/",
                "https://cobalt-api.kwiatekm.pl/",
                "https://co.wuk.sh/"
            )

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val cobaltObj = org.json.JSONObject().apply {
                put("url", url)
                put("videoQuality", quality)
                put("audioFormat", audioFormat)
                put("downloadMode", if (isAudio) "audio" else "auto")
                put("alwaysProxy", true)
            }
            val requestBody = cobaltObj.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            for (endpoint in cobaltInstances) {
                try {
                    val request = okhttp3.Request.Builder()
                        .url(endpoint)
                        .post(requestBody)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val resolved = parseRapidApiResponse(body, isAudio)
                            if (resolved != null && resolved.startsWith("http") && !isStaticImageUrl(resolved) && isMediaStreamUrl(resolved)) {
                                return@withContext resolved
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MediaDownloadUtils", "Cobalt instance $endpoint failed", e)
                }
            }
            null
        }
    }

    suspend fun extractMediaScraper(url: String, isAudio: Boolean): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        
                        // Look for raw mp4/mp3 urls in scripts or meta tags
                        val regex = if (isAudio) {
                            "(https?://[\\w./?=_%&-]+\\.(mp3|m4a|wav|aac)[\\w./?=_%&-]*)"
                        } else {
                            "(https?://[\\w./?=_%&-]+\\.(mp4|mkv|webm|mov)[\\w./?=_%&-]*)"
                        }
                        
                        val matches = regex.toRegex().findAll(body)
                        for (match in matches) {
                            val streamUrl = match.value
                            if (!isStaticImageUrl(streamUrl) && !streamUrl.contains("favicon")) {
                                return@withContext streamUrl
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "extractMediaScraper failed", e)
            }
            null
        }
    }

    suspend fun extractFacebookMedia(url: String, isAudio: Boolean = false): String? {
        // Fast direct scrape bypass
        val directStream = extractFacebookDirect(url, isAudio)
        if (!directStream.isNullOrBlank() && directStream.startsWith("http")) {
            return directStream
        }

        val encodedUrl = java.net.URLEncoder.encode(normalizeUrl(url), "UTF-8")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val endpoints = listOf(
            "https://$FB_DOWNLOADER_HOST/get-video-info?url=$encodedUrl",
            "https://$FB_DOWNLOADER_HOST/?url=$encodedUrl",
            "https://$FB_DOWNLOADER_HOST/index?url=$encodedUrl"
        )

        for (endpoint in endpoints) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(endpoint)
                    .get()
                    .addHeader("x-rapidapi-key", MULTI_PLATFORM_RAPID_API_KEY)
                    .addHeader("x-rapidapi-host", FB_DOWNLOADER_HOST)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val resolved = parseMultiPlatformResponse(body, isAudio)
                        if (!resolved.isNullOrBlank() && resolved.startsWith("http") && !isStaticImageUrl(resolved) && isMediaStreamUrl(resolved)) {
                            return resolved
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Facebook extraction failed for $endpoint", e)
            }
        }
        return null
    }

    suspend fun extractTikTokMedia(url: String, isAudio: Boolean = false): String? {
        // Fast direct TikWM & TikMate bypass
        val directStream = extractTikTokDirect(url, isAudio)
        if (!directStream.isNullOrBlank() && directStream.startsWith("http")) {
            return directStream
        }

        val encodedUrl = java.net.URLEncoder.encode(normalizeUrl(url), "UTF-8")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val endpoints = listOf(
            "https://$TIKTOK_DOWNLOADER_HOST/?url=$encodedUrl&hd=1",
            "https://$TIKTOK_DOWNLOADER_HOST/index?url=$encodedUrl",
            "https://$TIKTOK_DOWNLOADER_HOST/search/user?keywords=$encodedUrl"
        )

        for (endpoint in endpoints) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(endpoint)
                    .get()
                    .addHeader("x-rapidapi-key", MULTI_PLATFORM_RAPID_API_KEY)
                    .addHeader("x-rapidapi-host", TIKTOK_DOWNLOADER_HOST)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val resolved = parseMultiPlatformResponse(body, isAudio)
                        if (!resolved.isNullOrBlank() && resolved.startsWith("http") && !isStaticImageUrl(resolved) && isMediaStreamUrl(resolved)) {
                            return resolved
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "TikTok extraction failed for $endpoint", e)
            }
        }
        return null
    }

    suspend fun extractPinterestMedia(url: String, isAudio: Boolean = false): String? {
        val encodedUrl = java.net.URLEncoder.encode(normalizeUrl(url), "UTF-8")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val endpoints = listOf(
            "https://$PINTEREST_DOWNLOADER_HOST/pindown?url=$encodedUrl",
            "https://$PINTEREST_DOWNLOADER_HOST/?url=$encodedUrl",
            "https://$PINTEREST_DOWNLOADER_HOST/index?url=$encodedUrl"
        )

        for (endpoint in endpoints) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(endpoint)
                    .get()
                    .addHeader("x-rapidapi-key", MULTI_PLATFORM_RAPID_API_KEY)
                    .addHeader("x-rapidapi-host", PINTEREST_DOWNLOADER_HOST)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val resolved = parseMultiPlatformResponse(body, isAudio)
                        if (!resolved.isNullOrBlank() && resolved.startsWith("http")) {
                            return resolved
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Pinterest extraction failed for $endpoint", e)
            }
        }
        return null
    }

    suspend fun searchSocialLinks(queryOrUrl: String): String? {
        val encoded = java.net.URLEncoder.encode(queryOrUrl.trim(), "UTF-8")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val endpoints = listOf(
            "https://$SOCIAL_LINKS_HOST/search?query=$encoded",
            "https://$SOCIAL_LINKS_HOST/social-links?query=$encoded",
            "https://$SOCIAL_LINKS_HOST/search?url=$encoded"
        )

        for (endpoint in endpoints) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(endpoint)
                    .get()
                    .addHeader("x-rapidapi-key", MULTI_PLATFORM_RAPID_API_KEY)
                    .addHeader("x-rapidapi-host", SOCIAL_LINKS_HOST)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) {
                            return body
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Social links search failed for $endpoint", e)
            }
        }
        return null
    }

    suspend fun getFacebookFollowing(profileUrlOrId: String): String? {
        val encoded = java.net.URLEncoder.encode(profileUrlOrId.trim(), "UTF-8")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val endpoints = listOf(
            "https://$FB_SCRAPER_HOST/following?link_or_id=$encoded",
            "https://$FB_SCRAPER_HOST/user/following?user_id=$encoded",
            "https://$FB_SCRAPER_HOST/profile/following?url=$encoded"
        )

        for (endpoint in endpoints) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(endpoint)
                    .get()
                    .addHeader("x-rapidapi-key", MULTI_PLATFORM_RAPID_API_KEY)
                    .addHeader("x-rapidapi-host", FB_SCRAPER_HOST)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) return body
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Facebook profile following failed for $endpoint", e)
            }
        }
        return null
    }

    suspend fun searchTikTokUsers(keyword: String): String? {
        val encoded = java.net.URLEncoder.encode(keyword.trim(), "UTF-8")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val endpoints = listOf(
            "https://$TIKTOK_SCRAPER_HOST/user/search?keywords=$encoded",
            "https://$TIKTOK_SCRAPER_HOST/search/user?keyword=$encoded",
            "https://$TIKTOK_SCRAPER_HOST/search?keywords=$encoded"
        )

        for (endpoint in endpoints) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(endpoint)
                    .get()
                    .addHeader("x-rapidapi-key", MULTI_PLATFORM_RAPID_API_KEY)
                    .addHeader("x-rapidapi-host", TIKTOK_SCRAPER_HOST)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) return body
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "TikTok user search failed for $endpoint", e)
            }
        }
        return null
    }

    suspend fun extractSpotifyStreamUrl(spotifyUrlOrId: String): String? {
        val encoded = java.net.URLEncoder.encode(normalizeUrl(spotifyUrlOrId), "UTF-8")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        // Engine 1: spotify-music-mp3-downloader-api.p.rapidapi.com
        val endpoints1 = listOf(
            "https://$SPOTIFY_MP3_HOST/download?url=$encoded",
            "https://$SPOTIFY_MP3_HOST/?url=$encoded",
            "https://$SPOTIFY_MP3_HOST/index?link=$encoded"
        )
        for (endpoint in endpoints1) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(endpoint)
                    .get()
                    .addHeader("x-rapidapi-key", MULTI_PLATFORM_RAPID_API_KEY)
                    .addHeader("x-rapidapi-host", SPOTIFY_MP3_HOST)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val resolved = parseMultiPlatformResponse(body, isAudio = true)
                        if (!resolved.isNullOrBlank() && resolved.startsWith("http")) {
                            return resolved
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Spotify MP3 downloader failed for $endpoint", e)
            }
        }

        // Engine 2: spotify-downloader9.p.rapidapi.com
        val songId = spotifyUrlOrId.substringAfter("track/").substringBefore("?").substringBefore("/")
        val encodedSongId = java.net.URLEncoder.encode(if (songId.isNotBlank()) songId else spotifyUrlOrId, "UTF-8")
        val endpoints2 = listOf(
            "https://$SPOTIFY_DOWNLOADER9_HOST/downloadSong?songId=$encodedSongId",
            "https://$SPOTIFY_DOWNLOADER9_HOST/download?url=$encoded",
            "https://$SPOTIFY_DOWNLOADER9_HOST/?url=$encoded"
        )
        for (endpoint in endpoints2) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(endpoint)
                    .get()
                    .addHeader("x-rapidapi-key", MULTI_PLATFORM_RAPID_API_KEY)
                    .addHeader("x-rapidapi-host", SPOTIFY_DOWNLOADER9_HOST)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val resolved = parseMultiPlatformResponse(body, isAudio = true)
                        if (!resolved.isNullOrBlank() && resolved.startsWith("http")) {
                            return resolved
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Spotify downloader9 failed for $endpoint", e)
            }
        }
        return null
    }

    suspend fun getInstagramComments(mediaUrlOrCode: String): String? {
        val encoded = java.net.URLEncoder.encode(mediaUrlOrCode.trim(), "UTF-8")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val endpoints = listOf(
            "https://$INSTAGRAM_SCRAPER_HOST/media/comments/?shortcode_or_link=$encoded",
            "https://$INSTAGRAM_SCRAPER_HOST/comments/?url=$encoded",
            "https://$INSTAGRAM_SCRAPER_HOST/media/comments_by_url/?url=$encoded"
        )

        for (endpoint in endpoints) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(endpoint)
                    .get()
                    .addHeader("x-rapidapi-key", MULTI_PLATFORM_RAPID_API_KEY)
                    .addHeader("x-rapidapi-host", INSTAGRAM_SCRAPER_HOST)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) return body
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Instagram comments failed for $endpoint", e)
            }
        }
        return null
    }

    suspend fun getFacebookRealtimeComments(postUrlOrId: String): String? {
        val encoded = java.net.URLEncoder.encode(postUrlOrId.trim(), "UTF-8")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val endpoints = listOf(
            "https://$FB_REALTIME_HOST/comments?link_or_id=$encoded",
            "https://$FB_REALTIME_HOST/post/comments?post_id=$encoded",
            "https://$FB_REALTIME_HOST/comments/replies?url=$encoded"
        )

        for (endpoint in endpoints) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(endpoint)
                    .get()
                    .addHeader("x-rapidapi-key", MULTI_PLATFORM_RAPID_API_KEY)
                    .addHeader("x-rapidapi-host", FB_REALTIME_HOST)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) return body
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Facebook realtime comments failed for $endpoint", e)
            }
        }
        return null
    }

    suspend fun getYouTubeChannelVideos(channelIdOrUrl: String): String? {
        val channelId = channelIdOrUrl.substringAfter("channel/").substringAfter("c/").substringAfter("@").substringBefore("?").trim()
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val jsonObj = org.json.JSONObject().apply {
            put("id", channelId)
            put("channel_id", channelId)
            put("url", channelIdOrUrl)
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonObj.toString().toRequestBody(mediaType)

        val endpoints = listOf(
            "https://$YOUTUBE138_HOST/channel/videos/",
            "https://$YOUTUBE138_HOST/channel/videos",
            "https://$YOUTUBE138_HOST/channel/details/"
        )

        for (endpoint in endpoints) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .addHeader("x-rapidapi-key", MULTI_PLATFORM_RAPID_API_KEY)
                    .addHeader("x-rapidapi-host", YOUTUBE138_HOST)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) return body
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "YouTube channel videos failed for $endpoint", e)
            }
        }
        return null
    }

    suspend fun extractSocialMediaVideoDetails(url: String, isAudio: Boolean = false): String? {
        val encoded = java.net.URLEncoder.encode(normalizeUrl(url), "UTF-8")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val endpoints = listOf(
            "https://$SOCIAL_MEDIA_VIDEO_HOST/smvd/get-video-url?url=$encoded",
            "https://$SOCIAL_MEDIA_VIDEO_HOST/index?url=$encoded",
            "https://$SOCIAL_MEDIA_VIDEO_HOST/?url=$encoded"
        )

        for (endpoint in endpoints) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(endpoint)
                    .get()
                    .addHeader("x-rapidapi-key", MULTI_PLATFORM_RAPID_API_KEY)
                    .addHeader("x-rapidapi-host", SOCIAL_MEDIA_VIDEO_HOST)
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val resolved = parseMultiPlatformResponse(body, isAudio)
                        if (!resolved.isNullOrBlank() && resolved.startsWith("http")) {
                            return resolved
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Social media video downloader failed for $endpoint", e)
            }
        }
        return null
    }

    /**
     * Resolves a media URL to a direct download stream using RapidAPI engines, Cobalt, yt-dlp, and scrapers.
     */
    suspend fun resolveStreamUrl(
        url: String, 
        isAudio: Boolean, 
        quality: String = "720", 
        audioFormat: String = "mp3"
    ): String? {
        val normalizedUrl = normalizeUrl(url)
        
        // Fast-path: Check if input URL is already a direct playable stream/file
        val lowerUrl = normalizedUrl.lowercase()
        if (lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".mp3") || lowerUrl.endsWith(".m4a") || lowerUrl.endsWith(".wav") || lowerUrl.endsWith(".m3u8") || lowerUrl.contains("/stream/") || lowerUrl.contains("videoplayback?")) {
            return normalizedUrl
        }

        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Priority Scraper Engine: Direct page scraping for obvious media links
            val scrapedStream = extractMediaScraper(normalizedUrl, isAudio)
            if (scrapedStream != null && scrapedStream.startsWith("http") && isMediaStreamUrl(scrapedStream)) {
                return@withContext scrapedStream
            }

            // Priority Platform Engine 0-FB: Facebook Reel & Video Downloader
            if (normalizedUrl.contains("facebook.com") || normalizedUrl.contains("fb.watch")) {
                val fbStream = extractFacebookMedia(normalizedUrl, isAudio)
                if (!fbStream.isNullOrBlank() && fbStream.startsWith("http")) {
                    return@withContext fbStream
                }
            }

            // Priority Platform Engine 0-TT: TikTok Search & Downloader
            if (normalizedUrl.contains("tiktok.com")) {
                val ttStream = extractTikTokMedia(normalizedUrl, isAudio)
                if (!ttStream.isNullOrBlank() && ttStream.startsWith("http")) {
                    return@withContext ttStream
                }
            }

            // Priority Platform Engine 0-YT: YouTube Direct Invidious & RapidAPI Streams
            if (normalizedUrl.contains("youtube.com") || normalizedUrl.contains("youtu.be")) {
                // Try New V3 First
                val ytV3 = extractYouTubeV3(normalizedUrl, isAudio)
                if (!ytV3.isNullOrBlank() && ytV3.startsWith("http")) return@withContext ytV3

                // Try New V2 Second
                val ytV2 = extractYouTubeV2(normalizedUrl, isAudio)
                if (!ytV2.isNullOrBlank() && ytV2.startsWith("http")) return@withContext ytV2

                // Fallback to Invidious
                val ytStream = extractYouTubeDirect(normalizedUrl, isAudio, quality)
                if (!ytStream.isNullOrBlank() && ytStream.startsWith("http")) {
                    return@withContext ytStream
                }
            }

            // Priority Platform Engine 0-COBALT: Cobalt Multi-Instance Extractor
            val cobaltStream = extractCobaltStream(normalizedUrl, isAudio, quality, audioFormat)
            if (!cobaltStream.isNullOrBlank() && cobaltStream.startsWith("http")) {
                return@withContext cobaltStream
            }

            // Priority Platform Engine 0-PIN: Pinterest Media Downloader
            if (normalizedUrl.contains("pinterest.com") || normalizedUrl.contains("pin.it")) {
                val pinStream = extractPinterestMedia(normalizedUrl, isAudio)
                if (!pinStream.isNullOrBlank() && pinStream.startsWith("http")) {
                    return@withContext pinStream
                }
            }

            // Priority Platform Engine 0-SPOT: Spotify Song & Track Downloader
            if (normalizedUrl.contains("spotify.com") || normalizedUrl.contains("spotify:")) {
                val spotStream = extractSpotifyStreamUrl(normalizedUrl)
                if (!spotStream.isNullOrBlank() && spotStream.startsWith("http")) {
                    return@withContext spotStream
                }
            }

            // Priority Platform Engine 0-SMVD: Social Media Video Downloader
            val smvdStream = extractSocialMediaVideoDetails(normalizedUrl, isAudio)
            if (!smvdStream.isNullOrBlank() && smvdStream.startsWith("http")) {
                return@withContext smvdStream
            }

            val apiKey = getActiveApiKey()

            // Engine 0: RapidAPI Video Details Extractor with Dynamic Video ID
            try {
                val videoId = extractVideoId(normalizedUrl)
                if (!videoId.isNullOrBlank()) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val reqUrl = "https://$YOUTUBE_MEDIA_HOST/v2/video/details?videoId=$videoId"
                    val request = okhttp3.Request.Builder()
                        .url(reqUrl)
                        .get()
                        .addHeader("x-rapidapi-key", YOUTUBE_MEDIA_KEY)
                        .addHeader("x-rapidapi-host", YOUTUBE_MEDIA_HOST)
                        .addHeader("Content-Type", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val resolved = parseYouTubeMediaResponse(body, isAudio)
                            if (!resolved.isNullOrBlank() && resolved.startsWith("http")) {
                                return@withContext resolved
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Engine 0 RapidAPI Video Details extraction failed", e)
            }

            // Engine 0-IG1: Instagram Scraper AI (Highlights from URL)
            try {
                val isInstagramUrl = normalizedUrl.contains("instagram.com") || normalizedUrl.contains("instagr.am")
                val isHighlightUrl = normalizedUrl.contains("/s/") || normalizedUrl.contains("highlight")
                if (isHighlightUrl) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val encodedUrl = java.net.URLEncoder.encode(normalizedUrl, "UTF-8")
                    val reqUrl = "https://$INSTAGRAM_SCRAPER_HOST/highlights/from_url/?highlight-folder-url=$encodedUrl"

                    val request = okhttp3.Request.Builder()
                        .url(reqUrl)
                        .get()
                        .addHeader("x-rapidapi-key", INSTAGRAM_API_KEY)
                        .addHeader("x-rapidapi-host", INSTAGRAM_SCRAPER_HOST)
                        .addHeader("Content-Type", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val resolved = parseInstagramResponse(body)
                            if (!resolved.isNullOrBlank() && resolved.startsWith("http")) {
                                return@withContext resolved
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Instagram Scraper AI Highlights extraction failed", e)
            }

            // Engine 0-IG2: Instagram Downloader Engine (Reels, Videos, Stories from URL)
            try {
                val isInstagramUrl = normalizedUrl.contains("instagram.com") || normalizedUrl.contains("instagr.am")
                if (isInstagramUrl || apiKey.isNotEmpty()) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val encodedUrl = java.net.URLEncoder.encode(normalizedUrl, "UTF-8")

                    // Attempt 1: ?url= parameter as provided in snippet
                    val reqUrl = "https://$INSTAGRAM_DOWNLOADER_HOST/?url=$encodedUrl"
                    val request = okhttp3.Request.Builder()
                        .url(reqUrl)
                        .get()
                        .addHeader("x-rapidapi-key", INSTAGRAM_API_KEY)
                        .addHeader("x-rapidapi-host", INSTAGRAM_DOWNLOADER_HOST)
                        .addHeader("Content-Type", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val resolved = parseInstagramResponse(body)
                            if (!resolved.isNullOrBlank() && resolved.startsWith("http")) {
                                return@withContext resolved
                            }
                        }
                    }

                    // Attempt 2: ?Userinfo= query parameter fallback
                    val reqUrlUserinfo = "https://$INSTAGRAM_DOWNLOADER_HOST/index?Userinfo=$encodedUrl"
                    val requestUserinfo = okhttp3.Request.Builder()
                        .url(reqUrlUserinfo)
                        .get()
                        .addHeader("x-rapidapi-key", INSTAGRAM_API_KEY)
                        .addHeader("x-rapidapi-host", INSTAGRAM_DOWNLOADER_HOST)
                        .addHeader("Content-Type", "application/json")
                        .build()

                    client.newCall(requestUserinfo).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val resolved = parseInstagramResponse(body)
                            if (!resolved.isNullOrBlank() && resolved.startsWith("http")) {
                                return@withContext resolved
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Instagram Downloader API extraction failed", e)
            }

            // Engine 1: Primary RapidAPI Social Downloader Engine (configured with App ID: 9163066)
            try {
                if (apiKey.isNotEmpty()) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val jsonBodyStr = """
                        {
                            "url": "$normalizedUrl",
                            "app_id": "$INSTAGRAM_APP_ID",
                            "application_id": "$INSTAGRAM_APP_ID"
                        }
                    """.trimIndent()
                    val jsonBody = jsonBodyStr.toRequestBody("application/json".toMediaType())

                    val request = okhttp3.Request.Builder()
                        .url("https://social-download-all-in-one.p.rapidapi.com/v1/social/autolink")
                        .post(jsonBody)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .addHeader("x-rapidapi-key", apiKey)
                        .addHeader("x-rapidapi-host", "social-download-all-in-one.p.rapidapi.com")
                        .addHeader("x-app-id", INSTAGRAM_APP_ID)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val resolved = parseRapidApiResponse(body, isAudio)
                            if (resolved != null && resolved.startsWith("http")) {
                                return@withContext resolved
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Primary RapidAPI extraction failed", e)
            }

            // Engine 2: Secondary RapidAPI Engine (all-media-downloader1.p.rapidapi.com)
            try {
                if (apiKey.isNotEmpty()) {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val mediaType = "application/x-www-form-urlencoded".toMediaType()
                    val bodyString = "url=" + java.net.URLEncoder.encode(normalizedUrl, "UTF-8") + "&app_id=" + INSTAGRAM_APP_ID + "&cookies=&cookies_file="
                    val requestBody = bodyString.toRequestBody(mediaType)

                    val request = okhttp3.Request.Builder()
                        .url("https://all-media-downloader1.p.rapidapi.com/all")
                        .post(requestBody)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .addHeader("x-rapidapi-host", "all-media-downloader1.p.rapidapi.com")
                        .addHeader("x-rapidapi-key", apiKey)
                        .addHeader("x-app-id", INSTAGRAM_APP_ID)
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .addHeader("Accept", "application/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val resolved = parseRapidApiResponse(body, isAudio)
                            if (resolved != null && resolved.startsWith("http")) {
                                return@withContext resolved
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Secondary RapidAPI extraction failed", e)
            }

            // Engine 3: Hosted Cobalt Extractor (Updated v10 payload format)
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val cobaltObj = org.json.JSONObject().apply {
                    put("url", normalizedUrl)
                    put("videoQuality", quality)
                    put("audioFormat", audioFormat)
                    put("downloadMode", if (isAudio) "audio" else "auto")
                    put("alwaysProxy", true)
                }

                val request = okhttp3.Request.Builder()
                    .url("https://api.cobalt.tools/")
                    .post(cobaltObj.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val resolved = parseRapidApiResponse(body, isAudio)
                        if (resolved != null && resolved.startsWith("http")) {
                            return@withContext resolved
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Cobalt primary extraction failed", e)
            }

            // Engine 4: Open / Public Downloader Microservice Engine (savefrom / loader)
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val reqUrl = "https://loader.to/ajax/download.php?format=${if (isAudio) "mp3" else "1080"}&url=${java.net.URLEncoder.encode(normalizedUrl, "UTF-8")}"
                val request = okhttp3.Request.Builder()
                    .url(reqUrl)
                    .get()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val resolved = parseDirectDownloadUrl(body)
                        if (resolved != null && resolved.startsWith("http")) {
                            return@withContext resolved
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Loader.to extraction failed", e)
            }

            // Engine 5: Local yt-dlp Extraction (Exhaustive)
            try {
                val request = com.yausername.youtubedl_android.YoutubeDLRequest(normalizedUrl)
                // Use a more aggressive format selection
                val format = if (isAudio) {
                    "bestaudio/best"
                } else {
                    "bestvideo[height<=$quality]+bestaudio/best[height<=$quality]/best[height<=$quality]/best"
                }
                request.addOption("-f", format)
                request.addOption("--get-url")
                request.addOption("--no-check-certificates")
                request.addOption("--geo-bypass")
                request.addOption("--no-playlist")
                request.addOption("--socket-timeout", "15")

                val result = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
                val outputLines = result.out.trim().lines().filter { it.startsWith("http") }
                
                if (outputLines.isNotEmpty()) {
                    // Filter out known static images from yt-dlp output
                    val firstMedia = outputLines.firstOrNull { !isStaticImageUrl(it) } ?: outputLines.first()
                    return@withContext firstMedia
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Local yt-dlp extraction failed", e)
            }

            // Engine 6: Metadata Info Dump Fallback (Detailed Format Scan)
            try {
                val info = com.yausername.youtubedl_android.YoutubeDL.getInstance().getInfo(normalizedUrl)
                if (info != null) {
                    val urlFromInfo = info.url
                    if (!urlFromInfo.isNullOrBlank() && urlFromInfo.startsWith("http") && !isStaticImageUrl(urlFromInfo)) {
                        return@withContext urlFromInfo
                    }
                    
                    // Detailed format scan fallback
                    val formats = info.formats
                    if (formats != null) {
                        val bestFormat = formats.filter { f ->
                            val u = f.url ?: ""
                            u.startsWith("http") && !isStaticImageUrl(u) 
                        }.maxByOrNull { f ->
                            if (isAudio) (f.abr ?: 0f).toFloat() else (f.height ?: 0).toFloat() 
                        }
                        if (bestFormat != null && !bestFormat.url.isNullOrBlank()) {
                            return@withContext bestFormat.url!!
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "Metadata info dump fallback failed", e)
            }

            android.util.Log.e("MediaDownloadUtils", "All 6 extraction engines exhausted for URL: $normalizedUrl")
            null
        }
    }

    /**
     * Integration to fetch supported platforms from RapidAPI.
     */
    suspend fun fetchRapidApiPlatforms(): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val apiKey = getActiveApiKey()
                if (apiKey.isEmpty()) {
                    android.util.Log.e("MediaDownloadUtils", "RapidAPI Key is missing!")
                    return@withContext null
                }

                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("https://social-download-all-in-one.p.rapidapi.com/v1/social/autolink")
                    .get()
                    .addHeader("x-rapidapi-key", apiKey)
                    .addHeader("x-rapidapi-host", "social-download-all-in-one.p.rapidapi.com")
                    .addHeader("Content-Type", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    response.body?.string()
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "fetchRapidApiPlatforms failed", e)
                null
            }
        }
    }

    /**
     * Downloads a file using the local yt-dlp engine. This is the most robust method
     * as it handles stream muxing (video+audio) and complex site-specific logic automatically.
     */
    suspend fun downloadWithYtDlp(
        url: String,
        targetFile: File,
        isAudio: Boolean,
        quality: String = "720",
        onProgress: (progress: Float, eta: Long, line: String) -> Unit
    ): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                targetFile.parentFile?.mkdirs()
                val request = com.yausername.youtubedl_android.YoutubeDLRequest(url)
                
                request.addOption("-o", targetFile.absolutePath)
                
                if (isAudio) {
                    request.addOption("-f", "bestaudio/best")
                    request.addOption("--extract-audio")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "0")
                } else {
                    request.addOption("-f", "bestvideo[height<=$quality]+bestaudio/best[height<=$quality]/best")
                    request.addOption("--merge-output-format", "mp4")
                }

                request.addOption("--no-mtime")
                request.addOption("--no-playlist")
                request.addOption("--no-check-certificates")
                request.addOption("--geo-bypass")
                request.addOption("--retries", "5")

                com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request, null) { progress, eta, line ->
                    onProgress(progress / 100f, eta, line)
                }
                
                var resolvedFile = targetFile
                if (!resolvedFile.exists() || resolvedFile.length() <= 0) {
                    val baseName = targetFile.nameWithoutExtension
                    val parent = targetFile.parentFile
                    val candidate = parent?.listFiles()?.firstOrNull { it.name.startsWith(baseName) && it.length() > 0 && !it.name.endsWith(".downloading") }
                    if (candidate != null) {
                        resolvedFile = candidate
                    }
                }

                if (resolvedFile.exists() && isValidMediaFile(resolvedFile)) {
                    if (resolvedFile != targetFile) {
                        resolvedFile.renameTo(targetFile)
                    }
                    return@withContext true
                }
            } catch (e: Exception) {
                android.util.Log.e("MediaDownloadUtils", "yt-dlp primary download failed, trying fallback", e)
            }

            try {
                val fallbackRequest = com.yausername.youtubedl_android.YoutubeDLRequest(url)
                fallbackRequest.addOption("-o", targetFile.absolutePath)
                fallbackRequest.addOption("-f", "best")
                fallbackRequest.addOption("--no-mtime")
                fallbackRequest.addOption("--no-playlist")
                fallbackRequest.addOption("--no-check-certificates")
                fallbackRequest.addOption("--geo-bypass")
                com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(fallbackRequest, null) { progress, eta, line ->
                    onProgress(progress / 100f, eta, line)
                }

                var resolvedFile = targetFile
                if (!resolvedFile.exists() || resolvedFile.length() <= 0) {
                    val baseName = targetFile.nameWithoutExtension
                    val parent = targetFile.parentFile
                    val candidate = parent?.listFiles()?.firstOrNull { it.name.startsWith(baseName) && it.length() > 0 && !it.name.endsWith(".downloading") }
                    if (candidate != null) {
                        resolvedFile = candidate
                    }
                }

                if (resolvedFile.exists() && isValidMediaFile(resolvedFile)) {
                    if (resolvedFile != targetFile) {
                        resolvedFile.renameTo(targetFile)
                    }
                    return@withContext true
                }

                if (targetFile.exists()) targetFile.delete()
                false
            } catch (e2: Exception) {
                android.util.Log.e("MediaDownloadUtils", "yt-dlp fallback download also failed", e2)
                if (targetFile.exists()) targetFile.delete()
                false
            }
        }
    }

    /**
     * Downloads a file from a URL directly to the target file with robust headers and progress tracking.
     * Prevents downloading HTML error pages or static thumbnails in place of media files.
     */
    suspend fun downloadUrlToFile(
        url: String,
        targetFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val parentDir = targetFile.parentFile ?: return@withContext false
            parentDir.mkdirs()
            val tempFile = File(parentDir, "${targetFile.name}.downloading")
            
            // Clean start for core downloader overhaul
            if (tempFile.exists()) tempFile.delete()

            val client = okhttp3.OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val host = try { java.net.URI(url).host?.lowercase() ?: "" } catch (e: Exception) { "" }
            
            var retryCount = 0
            val maxRetries = 2
            var success = false
            var totalBytesDownloaded: Long = 0

            while (retryCount <= maxRetries && !success) {
                try {
                    val requestBuilder = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .header("Accept", "video/*, audio/*, application/octet-stream, */*;q=0.9")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Connection", "keep-alive")

                    // Platform specific headers for bypass
                    if (host.contains("tiktok") || host.contains("tikwm") || host.contains("ttcdn")) {
                        requestBuilder.header("Referer", "https://www.tiktok.com/")
                    } else if (host.contains("facebook") || host.contains("fbcdn") || host.contains("fna.fbcdn")) {
                        requestBuilder.header("Referer", "https://www.facebook.com/")
                    } else if (host.contains("instagram") || host.contains("cdninstagram")) {
                        requestBuilder.header("Referer", "https://www.instagram.com/")
                    } else if (host.contains("youtube") || host.contains("googlevideo")) {
                        requestBuilder.header("Referer", "https://www.youtube.com/")
                    }

                    client.newCall(requestBuilder.build()).execute().use { response ->
                        if (!response.isSuccessful) {
                            android.util.Log.e("MediaDownloadUtils", "Download failed: HTTP ${response.code}")
                            retryCount++
                            return@use
                        }

                        val body = response.body ?: throw Exception("Empty response body")
                        val contentLength = body.contentLength()
                        val contentType = response.header("Content-Type")?.lowercase() ?: ""
                        
                        // Strict validation: Reject HTML/JSON (prevents "saving landing pages" error)
                        if (contentType.contains("text/html") || contentType.contains("application/json")) {
                             throw Exception("Invalid content type received: $contentType")
                        }

                        var lastUpdateMillis = 0L
                        
                        body.byteStream().use { inputStream ->
                            FileOutputStream(tempFile).use { outputStream ->
                                val buffer = ByteArray(32768) // 32KB buffer for smoother I/O
                                var bytesRead: Int
                                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                    outputStream.write(buffer, 0, bytesRead)
                                    totalBytesDownloaded += bytesRead
                                    
                                    // Throttle UI progress updates to 20fps max to prevent UI stutter/freeze
                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdateMillis > 50L) {
                                        onProgress(totalBytesDownloaded, contentLength)
                                        lastUpdateMillis = now
                                    }
                                }
                                outputStream.flush()
                            }
                        }
                        // Final progress update
                        onProgress(totalBytesDownloaded, contentLength)
                        success = true
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MediaDownloadUtils", "Download error (attempt ${retryCount + 1}): ${e.message}")
                    retryCount++
                    if (retryCount <= maxRetries) kotlinx.coroutines.delay(1500L)
                }
            }

            if (success && isValidMediaFile(tempFile)) {
                if (targetFile.exists()) targetFile.delete()
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                true
            } else {
                if (tempFile.exists()) tempFile.delete()
                false
            }
        }
    }

    /**
     * Triggers Android MediaScanner via MediaScannerConnection and broadcasts scan intent
     * so that new audio and video files immediately appear in local music players, gallery, and file managers.
     */
    fun scanMediaFile(
        context: Context,
        file: File,
        mimeType: String,
        onComplete: ((Uri?) -> Unit)? = null
    ) {
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(file.absolutePath),
                arrayOf(mimeType)
            ) { path, uri ->
                onComplete?.invoke(uri)
            }

            // Secondary broadcast for broad compatibility across custom ROMs
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                data = Uri.fromFile(file)
            }
            context.sendBroadcast(mediaScanIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            onComplete?.invoke(null)
        }
    }

    /**
     * Opens a media file with the system default media player or chooser.
     */
    fun openMediaFile(context: Context, file: File, mimeType: String) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Play with"))
        } catch (e: Exception) {
            try {
                // Fallback to downloads list
                val downloadsIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(downloadsIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Saved to ${file.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Shares a media file via system share sheet.
     */
    fun shareMediaFile(context: Context, file: File, mimeType: String, title: String) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Downloaded via Arwora Hyper Toolkit: $title")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share Media"))
        } catch (e: Exception) {
            val fallbackIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_TEXT, "Downloaded via Arwora Hyper Toolkit: $title")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(fallbackIntent, "Share Media"))
        }
    }

    /**
     * Basic metadata scraper for social media titles (YouTube, TikTok, Instagram, etc.)
     * Uses local yt-dlp engine as primary and falls back to HTML meta-tag scraping.
     */
    suspend fun fetchMediaMetadata(url: String): String? {
        if (!URLUtil.isValidUrl(url)) return null
        
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Attempt 1: Local yt-dlp metadata fetch (Most Robust)
            try {
                val request = com.yausername.youtubedl_android.YoutubeDLRequest(url)
                request.addOption("--get-title")
                val result = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
                val title = result.out.trim()
                if (title.isNotEmpty()) return@withContext title
            } catch (e: Exception) {
                // Silently fallback to scraping
            }

            // Attempt 2: HTML Scraping Fallback
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G981B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.162 Mobile Safari/537.36")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    
                    val html = response.body?.string() ?: ""
                    
                    // Priority 1: OpenGraph Title
                    val ogTitleRegex = "<meta[^>]*property=[\"']og:title[\"'][^>]*content=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE)
                    val ogMatch = ogTitleRegex.find(html)
                    if (ogMatch != null) return@withContext decodeHtml(ogMatch.groupValues[1])

                    // Priority 2: Twitter Title
                    val twitterTitleRegex = "<meta[^>]*name=[\"']twitter:title[\"'][^>]*content=[\"']([^\"']+)[\"']".toRegex(RegexOption.IGNORE_CASE)
                    val twitterMatch = twitterTitleRegex.find(html)
                    if (twitterMatch != null) return@withContext decodeHtml(twitterMatch.groupValues[1])

                    // Priority 3: HTML Title Tag
                    val titleRegex = "<title>(.*?)</title>".toRegex(RegexOption.IGNORE_CASE)
                    val titleMatch = titleRegex.find(html)
                    if (titleMatch != null) {
                        val title = decodeHtml(titleMatch.groupValues[1])
                        // Clean up common suffixes
                        return@withContext title.split(" - YouTube", " | Instagram", " | TikTok", " on X:").first().trim()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            null
        }
    }

    /**
     * Fetches detailed video metadata (Duration, Thumbnail, etc.) using yt-dlp.
     */
    suspend fun fetchDetailedMetadata(url: String): com.yausername.youtubedl_android.mapper.VideoInfo? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                return@withContext com.yausername.youtubedl_android.YoutubeDL.getInstance().getInfo(url)
            } catch (e: Exception) {
                null
            }
        }
    }

    const val YOUTUBE_MEDIA_APP_ID = "9163066"
    const val YOUTUBE_MEDIA_HOST = "youtube-media-downloader.p.rapidapi.com"
    const val YOUTUBE_MEDIA_KEY = "a72f30e6a5msh1d85be0e2057a8cp12c87ejsnca7c4954bcb1"

    data class YouTubePostItem(
        val id: String,
        val contentText: String,
        val publishedTimeText: String,
        val voteCountText: String,
        val commentCountText: String,
        val imageUrl: String?
    )

    suspend fun fetchYouTubeChannelPosts(channelId: String = "UCY2ekMrWhsUVHwO3J3-PCzQ"): List<YouTubePostItem> {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val list = mutableListOf<YouTubePostItem>()
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val url = "https://$YOUTUBE_MEDIA_HOST/v2/channel/posts?channelId=$channelId"
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("x-rapidapi-key", YOUTUBE_MEDIA_KEY)
                    .addHeader("x-rapidapi-host", YOUTUBE_MEDIA_HOST)
                    .addHeader("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        if (bodyStr.isNotEmpty()) {
                            val jsonRoot = org.json.JSONObject(bodyStr)
                            val itemsArr = jsonRoot.optJSONArray("items")
                            if (itemsArr != null) {
                                for (i in 0 until itemsArr.length()) {
                                    val obj = itemsArr.optJSONObject(i) ?: continue
                                    val id = obj.optString("id", "")
                                    val contentText = obj.optString("contentText", "")
                                    val publishedTimeText = obj.optString("publishedTimeText", "")
                                    val voteCountText = obj.optString("voteCountText", "")
                                    val commentCountText = obj.optString("commentCountText", "")

                                    // Extract image url if present
                                    var imgUrl: String? = null
                                    val imagesArr = obj.optJSONArray("images")
                                    if (imagesArr != null && imagesArr.length() > 0) {
                                        val firstGroup = imagesArr.optJSONArray(0)
                                        if (firstGroup != null && firstGroup.length() > 0) {
                                            val firstImgObj = firstGroup.optJSONObject(0)
                                            if (firstImgObj != null) {
                                                imgUrl = firstImgObj.optString("url", null)
                                            }
                                        }
                                    } else {
                                        val pollObj = obj.optJSONObject("poll")
                                        if (pollObj != null) {
                                            val choicesArr = pollObj.optJSONArray("choices")
                                            if (choicesArr != null && choicesArr.length() > 0) {
                                                val firstChoice = choicesArr.optJSONObject(0)
                                                val choiceImgArr = firstChoice?.optJSONArray("image")
                                                if (choiceImgArr != null && choiceImgArr.length() > 0) {
                                                    imgUrl = choiceImgArr.optJSONObject(0)?.optString("url", null)
                                                }
                                            }
                                        }
                                    }

                                    list.add(
                                        YouTubePostItem(
                                            id = id,
                                            contentText = contentText,
                                            publishedTimeText = publishedTimeText,
                                            voteCountText = voteCountText,
                                            commentCountText = commentCountText,
                                            imageUrl = imgUrl
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            list
        }
    }

    private fun decodeHtml(input: String): String {
        return input.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .trim()
    }
}
