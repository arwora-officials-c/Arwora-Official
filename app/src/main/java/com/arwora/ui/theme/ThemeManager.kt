package com.arwora.ui.theme

import android.graphics.Color as AndroidColor
import android.view.Window
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowInsetsControllerCompat
import com.arwora.ui.MainViewModel

enum class ThemeMode(val title: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
    WHITE("White")
}

data class ThemeConfig(
    val mode: ThemeMode,
    val isDark: Boolean,
    val rootBackgroundColor: Color,
    val headerBackgroundColor: Color,
    val statusBarColorInt: Int,
    val navigationBarColorInt: Int,
    val isLightStatusBar: Boolean,
    val isLightNavigationBar: Boolean
)

object ThemeManager {

    /**
     * Resolves the current ThemeConfig based on user preference states.
     */
    fun getThemeConfig(
        darkModeState: Boolean?,
        whiteThemeState: Boolean,
        isSystemDark: Boolean
    ): ThemeConfig {
        val mode = when {
            whiteThemeState -> ThemeMode.WHITE
            darkModeState == null -> ThemeMode.SYSTEM
            darkModeState == true -> ThemeMode.DARK
            else -> ThemeMode.LIGHT
        }

        return when (mode) {
            ThemeMode.WHITE -> ThemeConfig(
                mode = ThemeMode.WHITE,
                isDark = false,
                rootBackgroundColor = Color(0xFFFFFFFF),
                headerBackgroundColor = Color(0xFFFFFFFF),
                statusBarColorInt = AndroidColor.WHITE,
                navigationBarColorInt = AndroidColor.WHITE,
                isLightStatusBar = true,
                isLightNavigationBar = true
            )
            ThemeMode.LIGHT -> ThemeConfig(
                mode = ThemeMode.LIGHT,
                isDark = false,
                rootBackgroundColor = Color(0xFFF8F9FA),
                headerBackgroundColor = Color(0xFFF8F9FA),
                statusBarColorInt = AndroidColor.parseColor("#F8F9FA"),
                navigationBarColorInt = AndroidColor.parseColor("#F8F9FA"),
                isLightStatusBar = true,
                isLightNavigationBar = true
            )
            ThemeMode.DARK -> ThemeConfig(
                mode = ThemeMode.DARK,
                isDark = true,
                rootBackgroundColor = Color(0xFF0B0B0F),
                headerBackgroundColor = Color(0xFF0B0B0F),
                statusBarColorInt = AndroidColor.parseColor("#0B0B0F"),
                navigationBarColorInt = AndroidColor.parseColor("#0B0B0F"),
                isLightStatusBar = false,
                isLightNavigationBar = false
            )
            ThemeMode.SYSTEM -> {
                if (isSystemDark) {
                    ThemeConfig(
                        mode = ThemeMode.SYSTEM,
                        isDark = true,
                        rootBackgroundColor = Color(0xFF0B0B0F),
                        headerBackgroundColor = Color(0xFF0B0B0F),
                        statusBarColorInt = AndroidColor.parseColor("#0B0B0F"),
                        navigationBarColorInt = AndroidColor.parseColor("#0B0B0F"),
                        isLightStatusBar = false,
                        isLightNavigationBar = false
                    )
                } else {
                    ThemeConfig(
                        mode = ThemeMode.SYSTEM,
                        isDark = false,
                        rootBackgroundColor = Color(0xFFF8F9FA),
                        headerBackgroundColor = Color(0xFFF8F9FA),
                        statusBarColorInt = AndroidColor.parseColor("#F8F9FA"),
                        navigationBarColorInt = AndroidColor.parseColor("#F8F9FA"),
                        isLightStatusBar = true,
                        isLightNavigationBar = true
                    )
                }
            }
        }
    }

    /**
     * Dynamically applies the theme configuration to the Activity Window,
     * including statusBarColor, decorView background, and WindowInsetsControllerCompat appearance.
     */
    fun applySystemBarsTheme(window: Window, themeConfig: ThemeConfig) {
        // 1. Update status bar and navigation bar background colors
        window.statusBarColor = themeConfig.statusBarColorInt
        window.navigationBarColor = themeConfig.navigationBarColorInt

        // 2. Update decorView background to eliminate letterbox/bleed color artifacts
        window.decorView.setBackgroundColor(themeConfig.statusBarColorInt)

        // 3. Update light/dark status bar icon appearances using WindowInsetsControllerCompat
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = themeConfig.isLightStatusBar
        controller.isAppearanceLightNavigationBars = themeConfig.isLightNavigationBar
    }

    /**
     * Updates theme preference states in ViewModel atomically without UI flickering.
     */
    fun selectThemeMode(viewModel: MainViewModel, mode: ThemeMode) {
        when (mode) {
            ThemeMode.SYSTEM -> viewModel.setThemeMode(isDark = null, isWhite = false)
            ThemeMode.LIGHT -> viewModel.setThemeMode(isDark = false, isWhite = false)
            ThemeMode.DARK -> viewModel.setThemeMode(isDark = true, isWhite = false)
            ThemeMode.WHITE -> viewModel.setThemeMode(isDark = false, isWhite = true)
        }
    }
}
