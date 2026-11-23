package com.arwora

import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.ui.screens.QrScannerScreen
import com.arwora.ui.theme.NameGenProTheme

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
            val viewModel = app.sharedViewModel

            setContent {
                val isDark by viewModel.darkMode.collectAsStateWithLifecycle()
                val isWhite by viewModel.whiteTheme.collectAsStateWithLifecycle()
                val uiColorVal by viewModel.uiColor.collectAsStateWithLifecycle()
                val buttonColorVal by viewModel.buttonColor.collectAsStateWithLifecycle()
                val isSystemDark = isSystemInDarkTheme()
                val resolvedDarkTheme = if (isWhite) false else (isDark ?: isSystemDark)

                NameGenProTheme(
                    darkTheme = resolvedDarkTheme,
                    whiteTheme = isWhite,
                    uiColor = Color(uiColorVal.toInt()),
                    buttonColor = Color(buttonColorVal.toInt())
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
