package com.arwora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.ui.screens.AnimationSettingsScreen
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.ui.theme.ThemeManager

class AnimationSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val app = application as? NameGenApplication
            if (app == null) {
                android.util.Log.e("AnimationSettingsActivity", "Application is not NameGenApplication")
                finish()
                return
            }
            val viewModel = app.sharedViewModel

            enableEdgeToEdge()
            val isSystemDarkNow = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            ThemeManager.applySystemBarsTheme(
                window,
                ThemeManager.getThemeConfig(viewModel.darkMode.value, viewModel.whiteTheme.value, isSystemDarkNow)
            )

            setContent {
                val isDark by viewModel.darkMode.collectAsStateWithLifecycle()
                val isWhite by viewModel.whiteTheme.collectAsStateWithLifecycle()
                val uiColorVal by viewModel.uiColor.collectAsStateWithLifecycle()
                val buttonColorVal by viewModel.buttonColor.collectAsStateWithLifecycle()
                val isSystemDark = isSystemInDarkTheme()
                val themeConfig = remember(isDark, isWhite, isSystemDark) {
                    ThemeManager.getThemeConfig(isDark, isWhite, isSystemDark)
                }

                LaunchedEffect(themeConfig) {
                    ThemeManager.applySystemBarsTheme(window, themeConfig)
                }

                NameGenProTheme(
                    darkTheme = themeConfig.isDark,
                    whiteTheme = isWhite,
                    uiColor = Color(uiColorVal.toInt()),
                    buttonColor = Color(buttonColorVal.toInt())
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
