package com.arwora

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.service.FloatingOverlayService
import com.arwora.ui.MainViewModel
import com.arwora.ui.MainViewModelFactory
import com.arwora.ui.theme.NameGenProTheme
import androidx.core.view.WindowCompat
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity(), Shizuku.OnRequestPermissionResultListener {
    
    fun checkAndRequestShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(1001)
                } else {
                    // Permission already granted
                }
            } else {
                // android.widget.Toast.makeText(this, "Shizuku service is not running!", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.widget.Toast.makeText(this, "Shizuku Permission Granted", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(this, "Shizuku Permission Denied", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun checkNotificationPermissionStatus() {
        com.arwora.utils.WirelessPairingManager.checkNotificationPermissionStatus(this)
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermissionStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(this)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            splashScreenViewProvider.remove()
        }
        super.onCreate(savedInstanceState)
        com.arwora.feature.common.metrics.StartupMetrics.onActivityCreate()
        
        // Register Shizuku permission listener without auto-requesting permission prompts on startup
        com.arwora.utils.DnsSecureSettingsHelper.registerPermissionListener()
        try {
            Shizuku.addRequestPermissionResultListener(this)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val app = application as NameGenApplication
        val viewModel = app.sharedViewModel
        val skipSplash = (intent?.action == Intent.ACTION_SEND)

        handleIncomingIntent(intent, viewModel)

        setContent {
            com.arwora.feature.common.metrics.StartupMetrics.onFirstCompose()
            
            LaunchedEffect(Unit) {
                com.arwora.feature.common.metrics.StartupMetrics.onFirstDraw()
                com.arwora.feature.common.metrics.StartupMetrics.onFullContentRendered()
                com.arwora.feature.common.startup.StartupDefer.postFirstFrame {
                    // Preload light caches if needed
                }
            }
            val darkModeState by viewModel.darkMode.collectAsStateWithLifecycle()
            val whiteThemeState by viewModel.whiteTheme.collectAsStateWithLifecycle()
            val systemInDark = isSystemInDarkTheme()
            val themeConfig = remember(darkModeState, whiteThemeState, systemInDark) {
                com.arwora.ui.theme.ThemeManager.getThemeConfig(
                    darkModeState = darkModeState,
                    whiteThemeState = whiteThemeState,
                    isSystemDark = systemInDark
                )
            }
            val uiColorLong by viewModel.uiColor.collectAsStateWithLifecycle()
            val buttonColorLong by viewModel.buttonColor.collectAsStateWithLifecycle()
            val uiColor = Color(uiColorLong.toInt())
            val buttonColor = Color(buttonColorLong.toInt())

            val context = androidx.compose.ui.platform.LocalContext.current
            val activity = context as? android.app.Activity
            LaunchedEffect(themeConfig) {
                activity?.window?.let { win ->
                    com.arwora.ui.theme.ThemeManager.applySystemBarsTheme(win, themeConfig)
                }
            }

            NameGenProTheme(
                darkTheme = themeConfig.isDark,
                whiteTheme = (themeConfig.mode == com.arwora.ui.theme.ThemeMode.WHITE),
                uiColor = uiColor,
                buttonColor = buttonColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.arwora.ui.navigation.MainAppScreen(
                        viewModel = viewModel,
                        skipSplash = skipSplash
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val app = application as? NameGenApplication ?: return
        handleIncomingIntent(intent, app.sharedViewModel)
    }

    private fun handleIncomingIntent(intent: Intent?, viewModel: MainViewModel) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
                ?: intent.dataString
                ?: ""
            if (sharedText.isNotBlank()) {
                viewModel.handleSharedUrl(sharedText)
            }
        }
    }
}
