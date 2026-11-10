package com.arwora.feature.system.appops.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arwora.feature.system.appops.data.AppOpsManager
import com.arwora.feature.system.appops.model.AppOpsFilter
import com.arwora.feature.system.appops.model.AppOpsItem
import com.arwora.feature.system.appops.model.AppOpsSort
import com.arwora.feature.system.appops.model.StandbyBucketMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppOpsCounts(
    val all: Int = 0,
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

    private val _isShizukuConnected = MutableStateFlow(false)
    val isShizukuConnected: StateFlow<Boolean> = _isShizukuConnected.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(AppOpsFilter.ALL)
    val selectedFilter: StateFlow<AppOpsFilter> = _selectedFilter.asStateFlow()

    private val _selectedSort = MutableStateFlow(AppOpsSort.NAME_ASC)
    val selectedSort: StateFlow<AppOpsSort> = _selectedSort.asStateFlow()

    private val _selectedApp = MutableStateFlow<AppOpsItem?>(null)
    val selectedApp: StateFlow<AppOpsItem?> = _selectedApp.asStateFlow()

    val counts: StateFlow<AppOpsCounts> = _rawApps.combine(_isLoading) { apps, loading ->
        if (loading) AppOpsCounts()
        else AppOpsCounts(
            all = apps.size,
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
        checkShizuku()
        loadApps()
    }

    fun checkShizuku() {
        viewModelScope.launch(Dispatchers.IO) {
            _isShizukuConnected.value = AppOpsManager.isShizukuAvailable()
        }
    }

    fun loadApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            checkShizuku()
            val apps = AppOpsManager.getInstalledApps(getApplication())
            _rawApps.value = apps
            _isLoading.value = false

            val currentSelected = _selectedApp.value
            if (currentSelected != null) {
                _selectedApp.value = apps.find { it.packageName == currentSelected.packageName }
            }
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

    fun selectApp(app: AppOpsItem) {
        _selectedApp.value = app
    }

    fun clearSelectedApp() {
        _selectedApp.value = null
    }

    fun updateStandbyBucketForApp(packageName: String, bucket: StandbyBucketMode) {
        _rawApps.value = _rawApps.value.map {
            if (it.packageName == packageName) {
                it.copy(standbyBucket = bucket)
            } else it
        }
        if (_selectedApp.value?.packageName == packageName) {
            _selectedApp.value = _selectedApp.value?.copy(standbyBucket = bucket)
        }
    }

    fun updateAppOpState(packageName: String, opName: String, mode: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = AppOpsManager.setAppOp(packageName, opName, mode)
            if (success) {
                // Refresh states
                val verifiedMode = AppOpsManager.getAppOp(packageName, opName)
                _rawApps.value = _rawApps.value.map { app ->
                    if (app.packageName == packageName) {
                        val newStates = app.appOpsStates.toMutableMap()
                        newStates[opName] = verifiedMode
                        app.copy(appOpsStates = newStates)
                    } else app
                }
                if (_selectedApp.value?.packageName == packageName) {
                    val app = _selectedApp.value!!
                    val newStates = app.appOpsStates.toMutableMap()
                    newStates[opName] = verifiedMode
                    _selectedApp.value = app.copy(appOpsStates = newStates)
                }
            }
            viewModelScope.launch(Dispatchers.Main) {
                onResult(success)
            }
        }
    }

    fun revokeAllSensitiveOps(packageName: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = AppOpsManager.revokeAllSensitiveOps(packageName)
            if (success) {
                loadApps()
            }
            viewModelScope.launch(Dispatchers.Main) {
                onResult(success)
            }
        }
    }
}
