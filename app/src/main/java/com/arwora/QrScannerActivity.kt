package com.arwora

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.arwora.ui.screens.QrScannerScreen
import com.arwora.ui.theme.NameGenProTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class QrScannerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        try {
            val app = application as? NameGenApplication
            if (app == null) {
                finish()
                return
            }
            val isDark = runBlocking { app.settingsRepository.darkModeFlow.first() }
            val isWhite = runBlocking { app.settingsRepository.whiteThemeFlow.first() }
            val uiColorVal = runBlocking { app.settingsRepository.uiColorFlow.first() }
            val buttonColorVal = runBlocking { app.settingsRepository.buttonColorFlow.first() }

            val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val resolvedDarkTheme = if (isWhite) false else (isDark ?: isSystemDark)

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
                        QrScannerScreen(
                            onBack = { finish() }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("QrScannerActivity", "Failed to initialize QR Scanner", e)
            Toast.makeText(this, "Unable to open QR & Barcode Scanner", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
