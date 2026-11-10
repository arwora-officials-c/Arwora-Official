package com.arwora

import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsControllerCompat
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.arwora.ui.screens.NetworkSettingsScreen
import com.arwora.ui.theme.NameGenProTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class DnsChangerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Read styling preferences synchronously
            val app = application as? NameGenApplication
            if (app == null) {
                android.util.Log.e("DnsChangerActivity", "Application is not NameGenApplication")
                finish()
                return
            }
            
            val isDark = runBlocking { app.settingsRepository.darkModeFlow.first() }
            val whiteTheme = runBlocking { app.settingsRepository.whiteThemeFlow.first() }
            val uiColorVal = runBlocking { app.settingsRepository.uiColorFlow.first() }
            val buttonColorVal = runBlocking { app.settingsRepository.buttonColorFlow.first() }

            val isSystemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val resolvedDarkTheme = (isDark ?: isSystemDark) && !whiteTheme

            // Fix Status Bar Color Mismatch: Match light background (#F8F9FE) and set dark icons
            val bgColor = if (resolvedDarkTheme) 0xFF1A1C1E.toInt() else 0xFFF8F9FE.toInt()
            window.statusBarColor = bgColor
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !resolvedDarkTheme

            setContent {
                NameGenProTheme(
                    darkTheme = resolvedDarkTheme,
                    whiteTheme = whiteTheme,
                    uiColor = Color(uiColorVal),
                    buttonColor = Color(buttonColorVal)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NetworkSettingsScreen(
                            onBack = { finish() }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DnsChangerActivity", "Crash in onCreate", e)
            android.widget.Toast.makeText(this, "Failed to load DNS Changer", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
