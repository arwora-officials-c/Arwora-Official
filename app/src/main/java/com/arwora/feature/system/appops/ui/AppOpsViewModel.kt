package com.arwora.feature.system.appops.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arwora.feature.system.appops.data.AppOpsManager
import com.arwora.feature.system.appops.model.AppOpsCategory
import com.arwora.feature.system.appops.model.AppOpsFilter
import com.arwora.feature.system.appops.model.AppOpsItem
import com.arwora.feature.system.appops.model.AppOpsSort
import com.arwora.feature.system.appops.model.StandbyBucketMode
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.PrivilegeEngine
import com.arwora.utils.WirelessPairingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppOpsCounts(
    val all: Int = 0,
    val location: Int = 0,
    val camera: Int = 0,
    val audio: Int = 0,
    val systemOps: Int = 0,
    val user: Int = 0,
    val system: Int = 0,
    val disabled: Int = 0,
    val hidden: Int = 0,
    val alertWindow: Int = 0,
    val bgLocation: Int = 0
)

class AppOpsViewModel(application: Application) : AndroidViewModel(application) {

    private val _rawApps = MutableStateFlow<List<AppOpsItem>>(emptyList())
    val rawApps: StateFlow<List<AppOpsItem>> = _rawApps.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshingSelectedOps = MutableStateFlow(false)
    val isRefreshingSelectedOps: StateFlow<Boolean> = _isRefreshingSelectedOps.asStateFlow()

    private val _isAdbConnected = MutableStateFlow(false)
    val isAdbConnected: StateFlow<Boolean> = _isAdbConnected.asStateFlow()

    val activeEngine: StateFlow<PrivilegeEngine> = AdbShellExecutor.activeEngine

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(AppOpsFilter.ALL)
    val selectedFilter: StateFlow<AppOpsFilter> = _selectedFilter.asStateFlow()

    private val _selectedSort = MutableStateFlow(AppOpsSort.NAME_ASC)
    val selectedSort: StateFlow<AppOpsSort> = _selectedSort.asStateFlow()

    private val _selectedApp = MutableStateFlow<AppOpsItem?>(null)
    val selectedApp: StateFlow<AppOpsItem?> = _selectedApp.asStateFlow()

    private val _selectedDetailCategory = MutableStateFlow(AppOpsCategory.ALL)
    val selectedDetailCategory: StateFlow<AppOpsCategory> = _selectedDetailCategory.asStateFlow()

    private val _pendingFocusPackageName = MutableStateFlow<String?>(null)

    val counts: StateFlow<AppOpsCounts> = _rawApps.combine(_isLoading) { apps, loading ->
        if (loading) AppOpsCounts()
        else AppOpsCounts(
            all = apps.size,
            location = apps.count { it.hasLocationPermission },
            camera = apps.count { it.hasCameraPermission },
            audio = apps.count { it.hasAudioPermission },
            systemOps = apps.count { it.hasSystemOpsPermission || it.hasAlertWindowPermission },
            user = apps.count { !it.isSystem },
            system = apps.count { it.isSystem },
            disabled = apps.count { it.isDisabled },
            hidden = apps.count { it.isHidden },
            alertWindow = apps.count { it.hasAlertWindowPermission },
            bgLocation = apps.count { it.hasBackgroundLocationPermission }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppOpsCounts())

    val filteredApps: StateFlow<List<AppOpsItem>> = combine(
        _rawApps,
        _searchQuery,
        _selectedFilter,
        _selectedSort
    ) { apps, query, filter, sort ->
        val trimmedQuery = query.trim().lowercase()
        val filterFiltered = apps.filter { app ->
            val matchesFilter = when (filter) {
                AppOpsFilter.ALL -> true
                AppOpsFilter.LOCATION -> app.hasLocationPermission
                AppOpsFilter.CAMERA -> app.hasCameraPermission
                AppOpsFilter.AUDIO -> app.hasAudioPermission
                AppOpsFilter.SYSTEM_OPS -> app.hasSystemOpsPermission || app.hasAlertWindowPermission
                AppOpsFilter.USER -> !app.isSystem
                AppOpsFilter.SYSTEM -> app.isSystem
                AppOpsFilter.DISABLED -> app.isDisabled
                AppOpsFilter.HIDDEN -> app.isHidden
                AppOpsFilter.ALERT_WINDOW -> app.hasAlertWindowPermission
                AppOpsFilter.BACKGROUND_LOCATION -> app.hasBackgroundLocationPermission
            }
            val matchesQuery = if (trimmedQuery.isEmpty()) true else {
                app.appName.lowercase().contains(trimmedQuery) ||
                    app.packageName.lowercase().contains(trimmedQuery)
            }
            matchesFilter && matchesQuery
        }

        when (sort) {
            AppOpsSort.NAME_ASC -> filterFiltered.sortedBy { it.appName.lowercase() }
            AppOpsSort.NAME_DESC -> filterFiltered.sortedByDescending { it.appName.lowercase() }
            AppOpsSort.PACKAGE_NAME -> filterFiltered.sortedBy { it.packageName.lowercase() }
            AppOpsSort.INSTALL_TIME -> filterFiltered.sortedByDescending { it.firstInstallTime }
            AppOpsSort.TARGET_SDK -> filterFiltered.sortedByDescending { it.targetSdk }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkAdbStatus()
        loadApps()
        viewModelScope.launch {
            combine(
                NativeAdbManager.isConnected,
                AdbShellExecutor.isAnyEngineReady
            ) { nativeConnected, executorReady ->
                nativeConnected || executorReady || AdbShellExecutor.isPrivilegedShellReady(getApplication())
            }
                .distinctUntilChanged()
                .collectLatest { anyReady ->
                    val previous = _isAdbConnected.value
                    _isAdbConnected.value = anyReady
                    if (anyReady && !previous) {
                        loadApps()
                        _selectedApp.value?.let { refreshOpsForSelectedApp(it.packageName) }
                    }
                }
        }
    }

    fun checkAdbStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            AdbShellExecutor.refreshEngineStatus(getApplication())
            _isAdbConnected.value = AppOpsManager.isAdbAvailable(getApplication())
        }
    }

    fun connectAdb(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val (ok, msg) = WirelessPairingManager.connectUnifiedWirelessAdb(getApplication())
            checkAdbStatus()
            if (ok) {
                loadApps()
                _selectedApp.value?.let { refreshOpsForSelectedApp(it.packageName) }
            }
            onResult(ok, msg)
        }
    }

    fun requestShizukuAuthorization(onUnavailable: () -> Unit = {}) {
        viewModelScope.launch {
            if (AdbShellExecutor.isShizukuRunning()) {
                val triggered = AdbShellExecutor.requestShizukuPermission()
                checkAdbStatus()
                if (!triggered) onUnavailable()
            } else {
                onUnavailable()
            }
        }
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            AdbShellExecutor.refreshEngineStatus(getApplication())
            _isAdbConnected.value = AppOpsManager.isAdbAvailable(getApplication())
            val apps = AppOpsManager.getInstalledApps(getApplication())
            _rawApps.value = apps
            _isLoading.value = false

            val focusPkg = _pendingFocusPackageName.value
            val currentSelected = _selectedApp.value
            if (!focusPkg.isNullOrBlank()) {
                val match = apps.find { it.packageName == focusPkg }
                if (match != null) {
                    _pendingFocusPackageName.value = null
                    _selectedDetailCategory.value = AppOpsCategory.ALL
                    _selectedApp.value = match
                    if (_isAdbConnected.value) {
                        refreshOpsForSelectedApp(match.packageName)
                    }
                }
            } else if (currentSelected != null) {
                val updated = apps.find { it.packageName == currentSelected.packageName }
                _selectedApp.value = updated
                if (updated != null && _isAdbConnected.value) {
                    refreshOpsForSelectedApp(updated.packageName)
                }
            }
        }
    }

    fun focusPackage(packageName: String) {
        if (packageName.isBlank()) return
        _searchQuery.value = packageName
        val match = _rawApps.value.find { it.packageName == packageName }
        if (match != null) {
            _pendingFocusPackageName.value = null
            selectApp(match)
        } else {
            _pendingFocusPackageName.value = packageName
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: AppOpsFilter) {
        _selectedFilter.value = filter
    }

    fun setSort(sort: AppOpsSort) {
        _selectedSort.value = sort
    }

    fun setDetailCategory(category: AppOpsCategory) {
        _selectedDetailCategory.value = category
    }

    fun selectApp(app: AppOpsItem) {
        _selectedDetailCategory.value = AppOpsCategory.ALL
        _selectedApp.value = app
        refreshOpsForSelectedApp(app.packageName)
    }

    fun clearSelectedApp() {
        _selectedApp.value = null
    }

    fun refreshOpsForSelectedApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshingSelectedOps.value = true
            try {
                val liveOps = AppOpsManager.queryOpsForPackage(packageName, getApplication())
                mergePackageOpsIntoState(packageName, liveOps)
            } finally {
                _isRefreshingSelectedOps.value = false
            }
        }
    }

    private fun mergePackageOpsIntoState(packageName: String, updatedOps: Map<String, String>) {
        if (updatedOps.isEmpty()) return
        _rawApps.value = _rawApps.value.map { item ->
            if (item.packageName == packageName) {
                val merged = item.appOpsStates.toMutableMap().apply { putAll(updatedOps) }
                item.copy(appOpsStates = merged)
            } else {
                item
            }
        }
        val current = _selectedApp.value
        if (current?.packageName == packageName) {
            val merged = current.appOpsStates.toMutableMap().apply { putAll(updatedOps) }
            _selectedApp.value = current.copy(appOpsStates = merged)
        }
    }

    fun updateStandbyBucketForApp(
        packageName: String,
        bucket: StandbyBucketMode,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = AppOpsManager.setStandbyBucket(packageName, bucket, getApplication())
            if (success) {
                _rawApps.value = _rawApps.value.map {
                    if (it.packageName == packageName) it.copy(standbyBucket = bucket) else it
                }
                if (_selectedApp.value?.packageName == packageName) {
                    _selectedApp.value = _selectedApp.value?.copy(standbyBucket = bucket)
                }
            }
            viewModelScope.launch(Dispatchers.Main) {
                onResult(success)
            }
        }
    }

    fun updateAppOpState(
        packageName: String,
        opName: String,
        mode: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val success = AppOpsManager.setAppOp(packageName, opName, mode, context)
            if (success) {
                val refreshedOps = AppOpsManager.queryOpsForPackage(packageName, context)
                val normalizedOp = AppOpsManager.normalizeOpName(opName)
                val verifiedMode = refreshedOps[normalizedOp] ?: AppOpsManager.getAppOp(packageName, opName, context)
                val merged = refreshedOps.toMutableMap().apply {
                    this[normalizedOp] = verifiedMode
                }
                mergePackageOpsIntoState(packageName, merged)
            }
            viewModelScope.launch(Dispatchers.Main) {
                onResult(success)
            }
        }
    }

    fun revokeAllSensitiveOps(packageName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val success = AppOpsManager.revokeAllSensitiveOps(packageName, context)
            val refreshedOps = AppOpsManager.queryOpsForPackage(packageName, context)
            mergePackageOpsIntoState(packageName, refreshedOps)
            viewModelScope.launch(Dispatchers.Main) {
                onResult(success)
            }
        }
    }
}
