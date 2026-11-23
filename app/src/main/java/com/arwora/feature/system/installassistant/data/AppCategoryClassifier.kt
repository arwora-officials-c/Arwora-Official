package com.arwora.feature.system.installassistant.data

import android.content.pm.ApplicationInfo
import android.os.Build
import com.arwora.feature.system.installassistant.model.AppInstallCategory

/**
 * Classifies installed applications into user-facing categories using Android's
 * native [ApplicationInfo.category] (API 26+) plus deterministic package/label heuristics.
 */
object AppCategoryClassifier {

    fun classify(
        applicationInfo: ApplicationInfo?,
        packageName: String,
        appName: String
    ): AppInstallCategory {
        if (applicationInfo != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (applicationInfo.category) {
                    ApplicationInfo.CATEGORY_GAME -> return AppInstallCategory.GAMES
                    ApplicationInfo.CATEGORY_SOCIAL -> return AppInstallCategory.SOCIAL
                    ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppInstallCategory.PRODUCTIVITY
                    ApplicationInfo.CATEGORY_AUDIO,
                    ApplicationInfo.CATEGORY_VIDEO,
                    ApplicationInfo.CATEGORY_IMAGE,
                    ApplicationInfo.CATEGORY_NEWS -> return AppInstallCategory.MEDIA
                    ApplicationInfo.CATEGORY_MAPS -> return AppInstallCategory.UTILITIES
                    ApplicationInfo.CATEGORY_ACCESSIBILITY -> return AppInstallCategory.UTILITIES
                }
            }

            @Suppress("DEPRECATION")
            val isLegacyGame = (applicationInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            if (isLegacyGame) return AppInstallCategory.GAMES

            val isSystemFlag = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystemFlag && isSystemPackagePrefix(packageName)) {
                return AppInstallCategory.SYSTEM
            }
        }

        return classifyByKeywords(packageName = packageName, appName = appName)
    }

    fun classifyByKeywords(packageName: String, appName: String): AppInstallCategory {
        val pkg = packageName.lowercase()
        val name = appName.lowercase()
        val combined = "$pkg $name"

        if (isSystemPackagePrefix(pkg)) {
            return AppInstallCategory.SYSTEM
        }

        if (GAME_KEYWORDS.any { combined.contains(it) }) {
            return AppInstallCategory.GAMES
        }

        if (SOCIAL_KEYWORDS.any { combined.contains(it) }) {
            return AppInstallCategory.SOCIAL
        }

        if (FINANCE_KEYWORDS.any { combined.contains(it) }) {
            return AppInstallCategory.FINANCE
        }

        if (PRODUCTIVITY_KEYWORDS.any { combined.contains(it) }) {
            return AppInstallCategory.PRODUCTIVITY
        }

        if (MEDIA_KEYWORDS.any { combined.contains(it) }) {
            return AppInstallCategory.MEDIA
        }

        if (UTILITY_KEYWORDS.any { combined.contains(it) }) {
            return AppInstallCategory.UTILITIES
        }

        return AppInstallCategory.OTHER
    }

    private fun isSystemPackagePrefix(pkg: String): Boolean {
        return pkg.startsWith("com.android.") ||
            pkg.startsWith("android.") ||
            pkg == "android" ||
            pkg.startsWith("com.qualcomm.") ||
            pkg.startsWith("com.mediatek.") ||
            pkg.startsWith("com.miui.") ||
            pkg.startsWith("com.samsung.android.app.")
    }

    private val GAME_KEYWORDS = listOf(
        "game", "gaming", "unity3d", "unreal", "roblox", "minecraft", "pubg",
        "freefire", "codm", "chess", "puzzle", "arcade", "racing", "casino", "ludo", "clash"
    )

    private val SOCIAL_KEYWORDS = listOf(
        "whatsapp", "instagram", "facebook", "messenger", "telegram", "discord",
        "snapchat", "tiktok", "musically", "twitter", "x.com", "reddit", "threads",
        "linkedin", "signal", "viber", "wechat", "social", "chat", "community"
    )

    private val FINANCE_KEYWORDS = listOf(
        "bank", "wallet", "pay", "finance", "bkash", "nagad", "paypal", "stripe",
        "crypto", "binance", "coin", "trading", "stock", "budget", "expense", "upi"
    )

    private val PRODUCTIVITY_KEYWORDS = listOf(
        "office", "docs", "sheets", "slides", "notion", "obsidian", "todo", "task",
        "calendar", "notes", "keep", "drive", "dropbox", "slack", "teams", "zoom",
        "meet", "mail", "gmail", "outlook", "trello", "jira", "pdf", "writer", "editor"
    )

    private val MEDIA_KEYWORDS = listOf(
        "youtube", "spotify", "netflix", "music", "player", "video", "camera",
        "gallery", "photo", "podcast", "radio", "stream", "vlc", "mxtech", "plex", "twitch"
    )

    private val UTILITY_KEYWORDS = listOf(
        "tool", "utility", "utilities", "toolkit", "cleaner", "booster", "files",
        "filemanager", "explorer", "scanner", "qr", "vpn", "dns", "adb", "shizuku",
        "terminal", "termux", "authenticator", "password", "browser", "chrome",
        "firefox", "maps", "weather", "clock", "alarm", "calculator", "compress", "zip"
    )
}
