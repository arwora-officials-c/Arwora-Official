package com.arwora

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.service.FloatingOverlayService
import com.arwora.ui.MainViewModel
import com.arwora.ui.MainViewModelFactory
import com.arwora.ui.theme.NameGenProTheme
import androidx.core.view.WindowCompat

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    fun checkNotificationPermissionStatus() {
        com.arwora.utils.WirelessPairingManager.checkNotificationPermissionStatus(this)
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermissionStatus()
        lifecycleScope.launch(Dispatchers.IO) {
            val appCtx = applicationContext
            com.arwora.utils.NativeAdbManager.refreshConnectionStatus(appCtx)
            com.arwora.utils.ShizukuBridgeManager.checkShizukuStatus(appCtx)
            com.arwora.utils.AdbShellExecutor.refreshEngineStatus(appCtx)
            val creds = com.arwora.utils.EncryptedAdbCredentialStore.loadState(appCtx)
            if (creds.isPaired && !creds.userStopped && !com.arwora.utils.NativeAdbManager.isAdbReady()) {
                com.arwora.service.AdbConnectionService.startPersistentSession(appCtx)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.arwora.feature.common.metrics.StartupMetrics.onActivityCreate()

        if (savedInstanceState == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val appCtx = applicationContext
                com.arwora.utils.ShizukuBridge.init(appCtx, autoRequestIfNeeded = true)
                com.arwora.utils.NativeAdbManager.init(appCtx)
                com.arwora.utils.AdbShellExecutor.init(appCtx)
                com.arwora.service.WifiAutoPairMonitor.syncWithPreference(appCtx)
            }
        }
        
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val app = application as NameGenApplication
        app.warmupAsync()
        val viewModel = app.sharedViewModel
        val showComposeSplash = intent?.getBooleanExtra("show_compose_splash", false) == true
        val openInstallAssistant = intent?.getBooleanExtra(
            com.arwora.feature.system.installassistant.data.SmartInstallAssistantManager.EXTRA_OPEN_INSTALL_ASSISTANT,
            false
        ) == true
        val openDownloadWorkflow = intent?.getBooleanExtra(
            com.arwora.feature.system.downloadworkflow.data.DownloadWorkflowManager.EXTRA_OPEN_DOWNLOAD_WORKFLOW,
            false
        ) == true
        val openArworaConnect = intent?.getBooleanExtra(
            com.arwora.feature.system.connect.data.ArworaConnectManager.EXTRA_OPEN_ARWORA_CONNECT,
            false
        ) == true
        val openHiddenVault = intent?.getBooleanExtra(
            com.arwora.receiver.SecretCodeReceiver.EXTRA_OPEN_HIDDEN_VAULT,
            false
        ) == true
        val skipSplash = !showComposeSplash ||
            openInstallAssistant ||
            openDownloadWorkflow ||
            openArworaConnect ||
            openHiddenVault ||
            (intent?.action == Intent.ACTION_SEND) ||
            (intent?.getBooleanExtra("skip_splash", false) == true)

        val nightMask = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isSystemDarkNow = nightMask == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val initialThemeConfig = com.arwora.ui.theme.ThemeManager.getThemeConfig(
            darkModeState = viewModel.darkMode.value,
            whiteThemeState = viewModel.whiteTheme.value,
            isSystemDark = isSystemDarkNow
        )
        com.arwora.ui.theme.ThemeManager.applySystemBarsTheme(window, initialThemeConfig)

        handleIncomingIntent(intent, viewModel)

        setContent {
            com.arwora.feature.common.metrics.StartupMetrics.onFirstCompose()
            
            LaunchedEffect(Unit) {
                withFrameNanos { }
                com.arwora.feature.common.metrics.StartupMetrics.onFirstDraw()
                com.arwora.feature.common.metrics.StartupMetrics.onFullContentRendered()
                runCatching { reportFullyDrawn() }
                com.arwora.feature.common.startup.StartupDefer.markFirstFrameDrawn()
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
        if (intent.getBooleanExtra(
                com.arwora.feature.system.installassistant.data.SmartInstallAssistantManager.EXTRA_OPEN_INSTALL_ASSISTANT,
                false
            )
        ) {
            val pkg = intent.getStringExtra(
                com.arwora.feature.system.installassistant.data.SmartInstallAssistantManager.EXTRA_INSTALL_ASSISTANT_PKG
            )
            viewModel.requestOpenInstallAssistant(pkg)
            return
        }
        if (intent.getBooleanExtra(
                com.arwora.feature.system.downloadworkflow.data.DownloadWorkflowManager.EXTRA_OPEN_DOWNLOAD_WORKFLOW,
                false
            )
        ) {
            val action = intent.getStringExtra(
                com.arwora.feature.system.downloadworkflow.data.DownloadWorkflowManager.EXTRA_WORKFLOW_ACTION
            ) ?: com.arwora.feature.system.downloadworkflow.data.DownloadWorkflowManager.ACTION_VIEW_DETAILS
            val eventId = intent.getStringExtra(
                com.arwora.feature.system.downloadworkflow.data.DownloadWorkflowManager.EXTRA_WORKFLOW_EVENT_ID
            )
            val folderPath = intent.getStringExtra(
                com.arwora.feature.system.downloadworkflow.data.DownloadWorkflowManager.EXTRA_WORKFLOW_FOLDER_PATH
            )
            viewModel.requestOpenDownloadWorkflow(action, eventId, folderPath)
            return
        }
        if (intent.getBooleanExtra(
                com.arwora.feature.system.connect.data.ArworaConnectManager.EXTRA_OPEN_ARWORA_CONNECT,
                false
            )
        ) {
            viewModel.requestOpenArworaConnect()
            return
        }
        if (intent.getBooleanExtra(
                com.arwora.receiver.SecretCodeReceiver.EXTRA_OPEN_HIDDEN_VAULT,
                false
            )
        ) {
            viewModel.requestOpenHiddenVault()
            return
        }
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
