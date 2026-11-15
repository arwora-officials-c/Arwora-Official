package com.arwora.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

enum class AvailabilityState(val badgeLabel: String, val detailLabel: String) {
    IDLE("Ready", "Tap Check to scan"),
    CHECKING("Checking", "Querying platform..."),
    AVAILABLE("Available", "Available / Not Taken"),
    TAKEN("Taken", "Already Taken / In Use"),
    UNVERIFIED("Tap to Check", "Open in browser to confirm"),
    OFFLINE("Offline", "No internet or request timed out")
}

data class SocialPlatformSpec(
    val id: String,
    val name: String,
    val domain: String,
    val colorHex: Long,
    val profileUrlTemplate: String,
    val signupUrl: String,
    val checkUrlTemplate: String = profileUrlTemplate
) {
    fun profileUrl(handle: String): String = String.format(profileUrlTemplate, handle)
    fun checkUrl(handle: String): String = String.format(checkUrlTemplate, handle)
    fun targetActionUrl(state: AvailabilityState, handle: String): String =
        if (state == AvailabilityState.AVAILABLE) signupUrl else profileUrl(handle)
}

data class PlatformAvailabilityResult(
    val platform: SocialPlatformSpec,
    val state: AvailabilityState,
    val statusNote: String = state.detailLabel
)

object UsernameAvailabilityChecker {

    val supportedPlatforms: List<SocialPlatformSpec> = listOf(
        SocialPlatformSpec(
            id = "instagram",
            name = "Instagram",
            domain = "instagram.com",
            colorHex = 0xFFE1306C,
            profileUrlTemplate = "https://www.instagram.com/%s/",
            signupUrl = "https://www.instagram.com/accounts/emailsignup/"
        ),
        SocialPlatformSpec(
            id = "tiktok",
            name = "TikTok",
            domain = "tiktok.com",
            colorHex = 0xFF06B6D4,
            profileUrlTemplate = "https://www.tiktok.com/@%s",
            signupUrl = "https://www.tiktok.com/signup"
        ),
        SocialPlatformSpec(
            id = "github",
            name = "GitHub",
            domain = "github.com",
            colorHex = 0xFF2DBA4E,
            profileUrlTemplate = "https://github.com/%s",
            signupUrl = "https://github.com/signup"
        ),
        SocialPlatformSpec(
            id = "telegram",
            name = "Telegram",
            domain = "t.me",
            colorHex = 0xFF0088CC,
            profileUrlTemplate = "https://t.me/%s",
            signupUrl = "https://telegram.org/"
        ),
        SocialPlatformSpec(
            id = "youtube",
            name = "YouTube",
            domain = "youtube.com",
            colorHex = 0xFFFF0000,
            profileUrlTemplate = "https://www.youtube.com/@%s",
            signupUrl = "https://www.youtube.com/"
        ),
        SocialPlatformSpec(
            id = "twitter_x",
            name = "X / Twitter",
            domain = "x.com",
            colorHex = 0xFF1DA1F2,
            profileUrlTemplate = "https://x.com/%s",
            signupUrl = "https://x.com/i/flow/signup"
        ),
        SocialPlatformSpec(
            id = "twitch",
            name = "Twitch",
            domain = "twitch.tv",
            colorHex = 0xFF9146FF,
            profileUrlTemplate = "https://www.twitch.tv/%s",
            signupUrl = "https://www.twitch.tv/signup"
        ),
        SocialPlatformSpec(
            id = "reddit",
            name = "Reddit",
            domain = "reddit.com",
            colorHex = 0xFFFF4500,
            profileUrlTemplate = "https://www.reddit.com/user/%s",
            signupUrl = "https://www.reddit.com/register/",
            checkUrlTemplate = "https://www.reddit.com/user/%s/about.json"
        )
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun sanitizeHandle(rawUsername: String): String {
        return rawUsername
            .trim()
            .removePrefix("@")
            .replace(Regex("\\s+"), "")
    }

    fun isNetworkConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Evaluates a single platform's availability concurrently and invokes [onUpdate] as soon as resolved.
     */
    suspend fun checkAllPlatforms(
        context: Context,
        rawUsername: String,
        onUpdate: (PlatformAvailabilityResult) -> Unit
    ) {
        val handle = sanitizeHandle(rawUsername)
        if (handle.isEmpty()) return

        if (!isNetworkConnected(context)) {
            supportedPlatforms.forEach { spec ->
                onUpdate(
                    PlatformAvailabilityResult(
                        platform = spec,
                        state = AvailabilityState.OFFLINE,
                        statusNote = "Device offline — tap to check in browser"
                    )
                )
            }
            return
        }

        coroutineScope {
            supportedPlatforms.map { spec ->
                async {
                    val result = checkSinglePlatform(spec, handle)
                    onUpdate(result)
                }
            }.awaitAll()
        }
    }

    suspend fun checkSinglePlatform(
        platform: SocialPlatformSpec,
        rawHandle: String
    ): PlatformAvailabilityResult = withContext(Dispatchers.IO) {
        val handle = sanitizeHandle(rawHandle)
        if (handle.isEmpty()) {
            return@withContext PlatformAvailabilityResult(platform, AvailabilityState.IDLE)
        }

        // Fast format validation before network call for strict platforms
        val formatError = validateFormat(platform.id, handle)
        if (formatError != null) {
            return@withContext PlatformAvailabilityResult(
                platform = platform,
                state = AvailabilityState.UNVERIFIED,
                statusNote = formatError
            )
        }

        val url = platform.checkUrl(handle)
        val request = Request.Builder()
            .url(url)
            .get()
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
            .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Range", "bytes=0-32767")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val code = response.code
                val snippet = try {
                    val source = response.body?.source()
                    if (source != null) {
                        source.request(32_768L)
                        val bytes = source.buffer.readByteArray(minOf(source.buffer.size, 32_768L))
                        String(bytes, Charsets.UTF_8)
                    } else {
                        ""
                    }
                } catch (_: Exception) {
                    ""
                }
                classifyPlatformResponse(platform, handle, code, snippet)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: SocketTimeoutException) {
            PlatformAvailabilityResult(
                platform = platform,
                state = AvailabilityState.OFFLINE,
                statusNote = "Timed out — tap to check in browser"
            )
        } catch (_: IOException) {
            PlatformAvailabilityResult(
                platform = platform,
                state = AvailabilityState.OFFLINE,
                statusNote = "Connection failed — tap to verify"
            )
        } catch (_: Exception) {
            PlatformAvailabilityResult(
                platform = platform,
                state = AvailabilityState.UNVERIFIED,
                statusNote = "Tap to check in browser"
            )
        }
    }

    internal fun validateFormat(platformId: String, handle: String): String? {
        return when (platformId) {
            "github" -> {
                if (!handle.matches(Regex("^[a-zA-Z0-9]([a-zA-Z0-9-]{0,37}[a-zA-Z0-9])?$")) || handle.contains("--")) {
                    "GitHub allows letters, numbers, single hyphens"
                } else null
            }
            "telegram" -> {
                if (handle.length < 5 || !handle.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]{4,31}$"))) {
                    "Telegram requires 5+ chars (letters, digits, _)"
                } else null
            }
            else -> null
        }
    }

    internal fun classifyPlatformResponse(
        platform: SocialPlatformSpec,
        handle: String,
        httpCode: Int,
        bodySnippet: String
    ): PlatformAvailabilityResult {
        val lower = bodySnippet.lowercase()
        val lowerHandle = handle.lowercase()

        if (httpCode == 404) {
            return PlatformAvailabilityResult(
                platform = platform,
                state = AvailabilityState.AVAILABLE,
                statusNote = "Available / Not Taken"
            )
        }

        if (httpCode == 429 || httpCode == 403 || httpCode == 401 || httpCode >= 500) {
            return PlatformAvailabilityResult(
                platform = platform,
                state = AvailabilityState.UNVERIFIED,
                statusNote = "Rate limited (HTTP $httpCode) — tap to verify"
            )
        }

        return when (platform.id) {
            "github" -> {
                if (httpCode in 200..299) {
                    PlatformAvailabilityResult(platform, AvailabilityState.TAKEN, "Already Taken / In Use")
                } else {
                    PlatformAvailabilityResult(platform, AvailabilityState.UNVERIFIED)
                }
            }
            "telegram" -> {
                // t.me returns HTTP 200 even for non-existent handles, but omits tgme_page_title/og:description user bio
                val hasActiveEntity = lower.contains("tgme_page_title") ||
                    lower.contains("tgme_page_extra")
                val hasGenericFallback = lower.contains("you can contact") && !hasActiveEntity
                when {
                    hasActiveEntity -> PlatformAvailabilityResult(platform, AvailabilityState.TAKEN, "Already Taken / In Use")
                    hasGenericFallback || lower.contains("tgme_page_icon") -> PlatformAvailabilityResult(platform, AvailabilityState.AVAILABLE, "Available / Not Taken")
                    else -> PlatformAvailabilityResult(platform, AvailabilityState.UNVERIFIED)
                }
            }
            "youtube" -> {
                when {
                    lower.contains("this page isn't available") || lower.contains("404 not found") ->
                        PlatformAvailabilityResult(platform, AvailabilityState.AVAILABLE, "Available / Not Taken")
                    httpCode in 200..299 && (lower.contains("\"@$lowerHandle\"") || lower.contains("og:title") || lower.contains("channelid")) ->
                        PlatformAvailabilityResult(platform, AvailabilityState.TAKEN, "Already Taken / In Use")
                    else -> PlatformAvailabilityResult(platform, AvailabilityState.UNVERIFIED)
                }
            }
            "reddit" -> {
                when {
                    lower.contains("\"error\": 404") || lower.contains("\"message\": \"not found\"") ->
                        PlatformAvailabilityResult(platform, AvailabilityState.AVAILABLE, "Available / Not Taken")
                    httpCode in 200..299 && (lower.contains("\"kind\": \"t2\"") || lower.contains("\"name\": \"$lowerHandle\"")) ->
                        PlatformAvailabilityResult(platform, AvailabilityState.TAKEN, "Already Taken / In Use")
                    else -> PlatformAvailabilityResult(platform, AvailabilityState.UNVERIFIED)
                }
            }
            "instagram" -> {
                when {
                    lower.contains("sorry, this page isn't available") || lower.contains("page not found") ->
                        PlatformAvailabilityResult(platform, AvailabilityState.AVAILABLE, "Available / Not Taken")
                    lower.contains("property=\"og:title\"") && lower.contains("@$lowerHandle") ->
                        PlatformAvailabilityResult(platform, AvailabilityState.TAKEN, "Already Taken / In Use")
                    lower.contains("login") && !lower.contains("@$lowerHandle") ->
                        PlatformAvailabilityResult(platform, AvailabilityState.UNVERIFIED, "Login wall — tap to verify")
                    httpCode in 200..299 ->
                        PlatformAvailabilityResult(platform, AvailabilityState.UNVERIFIED)
                    else -> PlatformAvailabilityResult(platform, AvailabilityState.UNVERIFIED)
                }
            }
            "tiktok" -> {
                when {
                    lower.contains("couldn't find this account") || lower.contains("\"statuscode\":10202") ->
                        PlatformAvailabilityResult(platform, AvailabilityState.AVAILABLE, "Available / Not Taken")
                    lower.contains("\"uniqueid\":\"$lowerHandle\"") || (lower.contains("og:title") && lower.contains("@$lowerHandle")) ->
                        PlatformAvailabilityResult(platform, AvailabilityState.TAKEN, "Already Taken / In Use")
                    else -> PlatformAvailabilityResult(platform, AvailabilityState.UNVERIFIED)
                }
            }
            "twitter_x" -> {
                when {
                    lower.contains("this account doesn’t exist") || lower.contains("this account doesn't exist") ->
                        PlatformAvailabilityResult(platform, AvailabilityState.AVAILABLE, "Available / Not Taken")
                    lower.contains("profile") && lower.contains("@$lowerHandle") ->
                        PlatformAvailabilityResult(platform, AvailabilityState.TAKEN, "Already Taken / In Use")
                    else -> PlatformAvailabilityResult(platform, AvailabilityState.UNVERIFIED, "JS required — tap to verify")
                }
            }
            "twitch" -> {
                when {
                    lower.contains("sorry. unless you’ve got a time machine") || lower.contains("time machine, that content is unavailable") ->
                        PlatformAvailabilityResult(platform, AvailabilityState.AVAILABLE, "Available / Not Taken")
                    httpCode in 200..299 && lower.contains(lowerHandle) && lower.contains("twitch") ->
                        PlatformAvailabilityResult(platform, AvailabilityState.TAKEN, "Already Taken / In Use")
                    else -> PlatformAvailabilityResult(platform, AvailabilityState.UNVERIFIED)
                }
            }
            else -> PlatformAvailabilityResult(platform, AvailabilityState.UNVERIFIED)
        }
    }
}
