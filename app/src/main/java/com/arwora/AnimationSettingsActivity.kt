package com.arwora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.arwora.ui.screens.AnimationSettingsScreen
import com.arwora.ui.theme.NameGenProTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AnimationSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Read styling preferences synchronously
            val app = application as? NameGenApplication
            if (app == null) {
                android.util.Log.e("AnimationSettingsActivity", "Application is not NameGenApplication")
                finish()
                return
            }
            val isDark = runBlocking { app.settingsRepository.darkModeFlow.first() }
            val isWhite = runBlocking { app.settingsRepository.whiteThemeFlow.first() }
            val uiColorVal = runBlocking { app.settingsRepository.uiColorFlow.first() }
            val buttonColorVal = runBlocking { app.settingsRepository.buttonColorFlow.first() }

            val isSystemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val resolvedDarkTheme = if (isWhite) false else (isDark ?: isSystemDark)

            enableEdgeToEdge()
            val themeConfig = com.arwora.ui.theme.ThemeManager.getThemeConfig(
                darkModeState = isDark,
                whiteThemeState = isWhite,
                isSystemDark = isSystemDark
            )
            com.arwora.ui.theme.ThemeManager.applySystemBarsTheme(window, themeConfig)

            setContent {
                NameGenProTheme(
                    darkTheme = resolvedDarkTheme,
                    whiteTheme = isWhite,
                    uiColor = Color(uiColorVal),
                    buttonColor = Color(buttonColorVal)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        AnimationSettingsScreen(
                            onBack = { finish() }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AnimationSettingsActivity", "Crash in onCreate", e)
            android.widget.Toast.makeText(this, "Failed to load Animation Settings", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
