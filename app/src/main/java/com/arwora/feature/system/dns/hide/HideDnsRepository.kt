package com.arwora.feature.system.dns.hide

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.arwora.feature.system.shell.ShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class HideDnsRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isMasterEnabled = MutableStateFlow(prefs.getBoolean(KEY_MASTER_ENABLED, false))
    val isMasterEnabled: StateFlow<Boolean> = _isMasterEnabled.asStateFlow()

    private val _hiddenPackages = MutableStateFlow<Set<String>>(loadHiddenPackages())
    val hiddenPackages: StateFlow<Set<String>> = _hiddenPackages.asStateFlow()

    private var cachedInstalledApps: List<HideDnsAppItem>? = null

    companion object {
        private const val TAG = "HideDnsRepository"
        private const val PREFS_NAME = "hide_private_dns_prefs"
        private const val KEY_MASTER_ENABLED = "master_hide_dns_enabled"
        private const val KEY_HIDDEN_PACKAGES = "hidden_packages_set"
    }

    private fun loadHiddenPackages(): Set<String> {
        val stored = prefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet()) ?: emptySet()
        return stored.toSet()
    }

    private fun saveHiddenPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_HIDDEN_PACKAGES, packages).apply()
        _hiddenPackages.value = packages
    }

    suspend fun setMasterEnabled(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        prefs.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply()
        _isMasterEnabled.value = enabled
        applyDnsIsolationRules(enabled, _hiddenPackages.value)
        true
    }

    suspend fun setAppHidden(packageName: String, hide: Boolean): Boolean = withContext(Dispatchers.IO) {
        val current = _hiddenPackages.value.toMutableSet()
        val changed = if (hide) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        if (changed) {
            saveHiddenPackages(current)
            if (_isMasterEnabled.value) {
                applyDnsIsolationRules(true, current)
            }
        }
        true
    }

    suspend fun toggleAppHidden(packageName: String): Boolean {
        val current = _hiddenPackages.value
        val shouldHide = !current.contains(packageName)
        return setAppHidden(packageName, shouldHide)
    }

    suspend fun clearAllHidden(): Boolean = withContext(Dispatchers.IO) {
        saveHiddenPackages(emptySet())
        if (_isMasterEnabled.value) {
            applyDnsIsolationRules(true, emptySet())
        }
        true
    }

    /**
     * Loads installed apps efficiently in the background.
     */
    suspend fun getInstalledApps(forceRefresh: Boolean = false): List<HideDnsAppItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedInstalledApps != null) {
            val hiddenSet = _hiddenPackages.value
            return@withContext cachedInstalledApps!!.map { app ->
                app.copy(isHidden = hiddenSet.contains(app.packageName))
            }
        }

        val pm = context.packageManager
        val installed = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching installed applications", e)
            emptyList()
        }

        val hiddenSet = _hiddenPackages.value
        val appList = mutableListOf<HideDnsAppItem>()

        for (info in installed) {
            // Exclude our own package from hiding Private DNS from itself
            if (info.packageName == context.packageName) continue

            val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            val label = try {
                info.loadLabel(pm).toString()
            } catch (e: Exception) {
                info.packageName
            }

            val icon = try {
                info.loadIcon(pm)
            } catch (e: Exception) {
                null
            }

            val versionName = try {
                val pkgInfo = pm.getPackageInfo(info.packageName, 0)
                pkgInfo.versionName ?: ""
            } catch (e: Exception) {
                ""
            }

            appList.add(
                HideDnsAppItem(
                    packageName = info.packageName,
                    appName = label,
                    isSystemApp = isSystem,
                    isHidden = hiddenSet.contains(info.packageName),
                    icon = icon,
                    versionName = versionName,
                    category = if (isSystem) AppCategory.SYSTEM else AppCategory.USER
                )
            )
        }

        // Sort: user apps first, then alphabetically by app name
        appList.sortWith(
            compareBy<HideDnsAppItem> { it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )

        cachedInstalledApps = appList
        appList
    }

    /**
     * Core isolation logic: Uses Wireless ADB shell commands to apply iptables/routing
     * rules or drop DoT (port 853) traffic for target application UIDs.
     */
    private suspend fun applyDnsIsolationRules(masterEnabled: Boolean, hiddenPackages: Set<String>) {
        if (!masterEnabled || hiddenPackages.isEmpty()) {
            // Clear any previous isolation rules via shell if Wireless ADB is active
            try {
                if (ShellExecutor.checkAdbStatus(context) == com.arwora.feature.system.shell.ShellStatus.READY) {
                    ShellExecutor.executeCommand("iptables -F ARWORA_HIDE_DNS 2>/dev/null || true")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed resetting DNS isolation chains: ${e.message}")
            }
            return
        }

        try {
            val pm = context.packageManager
            val uids = hiddenPackages.mapNotNull { pkg ->
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    appInfo.uid
                } catch (e: Exception) {
                    null
                }
            }

            if (uids.isNotEmpty() && ShellExecutor.checkAdbStatus(context) == com.arwora.feature.system.shell.ShellStatus.READY) {
                val commands = StringBuilder()
                commands.append("iptables -N ARWORA_HIDE_DNS 2>/dev/null || iptables -F ARWORA_HIDE_DNS;\n")
                // Block DoT (port 853 - Private DNS) for specific UIDs so they fall back to standard DNS
                for (uid in uids) {
                    commands.append("iptables -I ARWORA_HIDE_DNS -m owner --uid-owner $uid -p tcp --dport 853 -j REJECT;\n")
                    commands.append("iptables -I ARWORA_HIDE_DNS -m owner --uid-owner $uid -p udp --dport 853 -j REJECT;\n")
                }
                ShellExecutor.executeCommand(commands.toString())
                Log.d(TAG, "Successfully configured DNS isolation rules for ${uids.size} app UIDs")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying DNS isolation rules", e)
        }
    }
}
