package com.arwora.feature.system.dns.hide

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arwora.feature.system.shell.ShellExecutor
import com.arwora.feature.system.shell.ShellStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HideDnsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HideDnsRepository(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTab = MutableStateFlow(HideDnsTab.VISIBLE)
    val selectedTab: StateFlow<HideDnsTab> = _selectedTab.asStateFlow()

    private val _allApps = MutableStateFlow<List<HideDnsAppItem>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isShizukuAvailable = MutableStateFlow(false)
    val isShizukuAvailable: StateFlow<Boolean> = _isShizukuAvailable.asStateFlow()

    private val _hasShizukuPermission = MutableStateFlow(false)
    val hasShizukuPermission: StateFlow<Boolean> = _hasShizukuPermission.asStateFlow()

    val isMasterEnabled: StateFlow<Boolean> = repository.isMasterEnabled
    val hiddenPackages: StateFlow<Set<String>> = repository.hiddenPackages

    init {
        checkShizukuStatus()
        loadApps()
    }

    fun checkShizukuStatus() {
        val status = ShellExecutor.checkShizukuStatus(getApplication())
        _isShizukuAvailable.value = (status != ShellStatus.SHIZUKU_REQUIRED)
        _hasShizukuPermission.value = (status == ShellStatus.READY)
    }

    fun requestShizukuPermission() {
        ShellExecutor.requestPermission(1005)
    }

    fun loadApps(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val apps = repository.getInstalledApps(forceRefresh)
                _allApps.value = apps
            } catch (e: Exception) {
                _allApps.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTab(tab: HideDnsTab) {
        _selectedTab.value = tab
    }

    fun setMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setMasterEnabled(enabled)
        }
    }

    fun toggleAppHidden(packageName: String) {
        viewModelScope.launch {
            val hiddenSet = repository.hiddenPackages.value
            val currentlyHidden = hiddenSet.contains(packageName)
            val newHiddenState = !currentlyHidden
            repository.setAppHidden(packageName, newHiddenState)

            // Update in-memory app list for instantaneous UI feedback
            _allApps.value = _allApps.value.map { app ->
                if (app.packageName == packageName) {
                    app.copy(isHidden = newHiddenState)
                } else {
                    app
                }
            }
        }
    }

    fun setAppHidden(packageName: String, hide: Boolean) {
        viewModelScope.launch {
            repository.setAppHidden(packageName, hide)
            _allApps.value = _allApps.value.map { app ->
                if (app.packageName == packageName) {
                    app.copy(isHidden = hide)
                } else {
                    app
                }
            }
        }
    }

    fun unhideAllApps() {
        viewModelScope.launch {
            repository.clearAllHidden()
            _allApps.value = _allApps.value.map { it.copy(isHidden = false) }
        }
    }

    // Filtered lists for the two tabs
    val visibleApps: StateFlow<List<HideDnsAppItem>> = combine(
        _allApps,
        _searchQuery,
        repository.hiddenPackages
    ) { apps, query, hiddenSet ->
        val q = query.trim().lowercase()
        apps.filter { app ->
            !hiddenSet.contains(app.packageName) &&
                    (q.isEmpty() || app.appName.lowercase().contains(q) || app.packageName.lowercase().contains(q))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hiddenApps: StateFlow<List<HideDnsAppItem>> = combine(
        _allApps,
        _searchQuery,
        repository.hiddenPackages
    ) { apps, query, hiddenSet ->
        val q = query.trim().lowercase()
        apps.filter { app ->
            hiddenSet.contains(app.packageName) &&
                    (q.isEmpty() || app.appName.lowercase().contains(q) || app.packageName.lowercase().contains(q))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalVisibleCount: StateFlow<Int> = combine(_allApps, repository.hiddenPackages) { apps, hiddenSet ->
        apps.count { !hiddenSet.contains(it.packageName) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalHiddenCount: StateFlow<Int> = repository.hiddenPackages.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
