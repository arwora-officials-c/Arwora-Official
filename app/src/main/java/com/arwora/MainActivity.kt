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

class MainActivity : ComponentActivity() {

    fun checkNotificationPermissionStatus() {
        com.arwora.utils.WirelessPairingManager.checkNotificationPermissionStatus(this)
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermissionStatus()
        com.arwora.utils.NativeAdbManager.refreshConnectionStatus(this)
        com.arwora.utils.AdbShellExecutor.refreshEngineStatus(this)
        val creds = com.arwora.utils.EncryptedAdbCredentialStore.loadState(this)
        if (creds.isPaired && !creds.userStopped && !com.arwora.utils.NativeAdbManager.isAdbReady()) {
            com.arwora.service.AdbConnectionService.startPersistentSession(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            splashScreenViewProvider.remove()
        }
        super.onCreate(savedInstanceState)
        com.arwora.feature.common.metrics.StartupMetrics.onActivityCreate()

        com.arwora.utils.NativeAdbManager.init(this)
        com.arwora.utils.AdbShellExecutor.init(this)
        com.arwora.service.WifiAutoPairMonitor.syncWithPreference(this)
        
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val app = application as NameGenApplication
        val viewModel = app.sharedViewModel
        val skipSplash = (intent?.action == Intent.ACTION_SEND) ||
            (intent?.getBooleanExtra("skip_splash", false) == true)

        val nightMask = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isSystemDarkNow = nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val initialThemeConfig = com.arwora.ui.theme.ThemeManager.getThemeConfig(
            darkModeState = viewModel.darkMode.value,
            whiteThemeState = viewModel.whiteTheme.value,
            isSystemDark = isSystemDarkNow
        )
        if (!skipSplash) {
            val splashBgInt = if (isSystemDarkNow) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(splashBgInt))
            @Suppress("DEPRECATION")
            window.statusBarColor = splashBgInt
            @Suppress("DEPRECATION")
            window.navigationBarColor = splashBgInt
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = !isSystemDarkNow
            insetsController.isAppearanceLightNavigationBars = !isSystemDarkNow
        } else {
            com.arwora.ui.theme.ThemeManager.applySystemBarsTheme(window, initialThemeConfig)
        }

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
            val isSplashDismissedState = remember(skipSplash) {
                androidx.compose.runtime.mutableStateOf(skipSplash)
            }
            val uiColorLong by viewModel.uiColor.collectAsStateWithLifecycle()
            val buttonColorLong by viewModel.buttonColor.collectAsStateWithLifecycle()
            val uiColor = Color(uiColorLong.toInt())
            val buttonColor = Color(buttonColorLong.toInt())

            val context = androidx.compose.ui.platform.LocalContext.current
            val activity = context as? android.app.Activity
            LaunchedEffect(themeConfig, isSplashDismissedState.value) {
                if (isSplashDismissedState.value) {
                    activity?.window?.let { win ->
                        com.arwora.ui.theme.ThemeManager.applySystemBarsTheme(win, themeConfig)
                    }
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
                        skipSplash = skipSplash,
                        onSplashDismissed = { isSplashDismissedState.value = true }
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
