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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.ui.components.PinAuthDialog
import com.arwora.ui.theme.NameGenProTheme
import com.arwora.utils.AppLockerManager

class AppLockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_locked_package_name"
        const val EXTRA_LOCK_MODE = "extra_lock_mode"
        const val MODE_APP_LOCK = "mode_app_lock"
        const val MODE_UNINSTALL_GUARD = "mode_uninstall_guard"
        const val MODE_INSTALL_BLOCK = "mode_install_block"
    }

    private var currentTargetPackage: String = ""
    private var currentMode: String = MODE_APP_LOCK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleDismissOrReject()
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
        AppLockerManager.init(applicationContext)
        val mode = inIntent?.getStringExtra(EXTRA_LOCK_MODE) ?: MODE_APP_LOCK
        val targetPackage = inIntent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""

        currentMode = mode
        return when (mode) {
            MODE_UNINSTALL_GUARD -> {
                if (!AppLockerManager.isUninstallProtectionEnabled.value || AppLockerManager.isUninstallAuthorized()) {
                    false
                } else {
                    currentTargetPackage = targetPackage.ifBlank { packageName }
                    true
                }
            }
            MODE_INSTALL_BLOCK -> {
                if (!AppLockerManager.isInstallBlockEnabled.value ||
                    (targetPackage.isNotBlank() && AppLockerManager.isInstallAuthorized(targetPackage))
                ) {
                    false
                } else {
                    currentTargetPackage = targetPackage.ifBlank {
                        com.arwora.service.AppInstallMonitorService.INSTALLER_PLACEHOLDER_PKG
                    }
                    true
                }
            }
            else -> {
                if (targetPackage.isBlank() ||
                    AppLockerManager.isSelfPackageName(targetPackage) ||
                    targetPackage == packageName ||
                    targetPackage == applicationContext.packageName ||
                    !AppLockerManager.isAppLocked(targetPackage) ||
                    AppLockerManager.isPackageUnlocked(targetPackage)
                ) {
                    false
                } else {
                    currentTargetPackage = targetPackage
                    true
                }
            }
        }
    }

    private fun renderUi() {
        val pm = packageManager
        var appName = "Protected Application"
        try {
            if (currentTargetPackage != com.arwora.service.AppInstallMonitorService.INSTALLER_PLACEHOLDER_PKG) {
                val appInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(currentTargetPackage, PackageManager.ApplicationInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(currentTargetPackage, 0)
                }
                appName = pm.getApplicationLabel(appInfo).toString()
            } else {
                appName = "Package Installer"
            }
        } catch (_: Exception) {
            if (currentMode == MODE_INSTALL_BLOCK && currentTargetPackage.isNotBlank()) {
                appName = currentTargetPackage
            }
        }

        val dialogTitle = when (currentMode) {
            MODE_UNINSTALL_GUARD -> "Uninstall Blocked: $appName"
            MODE_INSTALL_BLOCK -> "Install Blocked: $appName"
            else -> appName
        }

        val dialogSubtitle = when (currentMode) {
            MODE_UNINSTALL_GUARD -> "Verify PIN or biometric before uninstalling protected app"
            MODE_INSTALL_BLOCK -> "Authenticate to allow installing this application"
            else -> "Enter 4-digit PIN to open application"
        }

        val sharedViewModel = (application as? NameGenApplication)?.sharedViewModel

        setContent {
            val isDark = sharedViewModel?.darkMode?.collectAsStateWithLifecycle()?.value
            val whiteTheme = sharedViewModel?.whiteTheme?.collectAsStateWithLifecycle()?.value ?: false
            val uiColorVal = sharedViewModel?.uiColor?.collectAsStateWithLifecycle()?.value ?: 0xFF6C63FFL
            val buttonColorVal = sharedViewModel?.buttonColor?.collectAsStateWithLifecycle()?.value ?: 0xFF6C63FFL
            val isSystemDark = isSystemInDarkTheme()
            val resolvedDarkTheme = (isDark ?: isSystemDark) && !whiteTheme

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
                            title = dialogTitle,
                            subtitle = dialogSubtitle,
                            onDismiss = {
                                handleDismissOrReject()
                            },
                            onSuccess = {
                                when (currentMode) {
                                    MODE_UNINSTALL_GUARD -> {
                                        AppLockerManager.grantTemporaryUninstallPass()
                                    }
                                    MODE_INSTALL_BLOCK -> {
                                        AppLockerManager.authorizeInstall(currentTargetPackage)
                                    }
                                    else -> {
                                        AppLockerManager.unlockSession(currentTargetPackage)
                                    }
                                }
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun handleDismissOrReject() {
        if (currentMode == MODE_INSTALL_BLOCK) {
            AppLockerManager.temporarilyExemptUninstall(currentTargetPackage)
            com.arwora.service.AppInstallMonitorService.rejectAndUninstallAsync(
                applicationContext,
                currentTargetPackage
            )
        }
        goToHomeScreen()
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
