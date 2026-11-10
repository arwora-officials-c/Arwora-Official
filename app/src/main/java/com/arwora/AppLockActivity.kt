package com.arwora

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.arwora.ui.components.PinAuthDialog
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.AppLockerManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class AppLockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_locked_package_name"
    }

    private var currentTargetPackage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goToHomeScreen()
            }
        })

        if (!processLockIntent(intent)) {
            finish()
            return
        }

        renderUi()
    }

    override fun onNewIntent(newIntent: Intent) {
        super.onNewIntent(newIntent)
        setIntent(newIntent)
        if (!processLockIntent(newIntent)) {
            finish()
            return
        }
        renderUi()
    }

    private fun processLockIntent(inIntent: Intent?): Boolean {
        val targetPackage = inIntent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        if (targetPackage.isBlank() ||
            AppLockerManager.isSelfPackageName(targetPackage) ||
            targetPackage == packageName ||
            targetPackage == applicationContext.packageName ||
            !AppLockerManager.isAppLocked(targetPackage) ||
            AppLockerManager.isPackageUnlocked(targetPackage)
        ) {
            return false
        }
        currentTargetPackage = targetPackage
        return true
    }

    private fun renderUi() {
        val pm = packageManager
        var appName = "Protected Application"
        var appIcon: Drawable? = null
        try {
            val appInfo = pm.getApplicationInfo(currentTargetPackage, 0)
            appName = pm.getApplicationLabel(appInfo).toString()
            appIcon = pm.getApplicationIcon(appInfo)
        } catch (_: Exception) {}

        val app = application as? NameGenApplication
        val repo = app?.settingsRepository
        val isDark = repo?.let { runBlocking { it.darkModeFlow.first() } }
        val whiteTheme = repo?.let { runBlocking { it.whiteThemeFlow.first() } } ?: false
        val uiColorVal = repo?.let { runBlocking { it.uiColorFlow.first() } } ?: 0xFF6C63FFL
        val buttonColorVal = repo?.let { runBlocking { it.buttonColorFlow.first() } } ?: 0xFF6C63FFL

        val isSystemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val resolvedDarkTheme = (isDark ?: isSystemDark) && !whiteTheme

        setContent {
            NameGenProTheme(
                darkTheme = resolvedDarkTheme,
                whiteTheme = whiteTheme,
                uiColor = Color(uiColorVal.toInt()),
                buttonColor = Color(buttonColorVal.toInt())
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        PinAuthDialog(
                            title = appName,
                            subtitle = "Enter 4-digit PIN to open application",
                            onDismiss = {
                                goToHomeScreen()
                            },
                            onSuccess = {
                                AppLockerManager.unlockSession(currentTargetPackage)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun goToHomeScreen() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
