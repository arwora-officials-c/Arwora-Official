package com.arwora.feature.system.installassistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arwora.feature.system.installassistant.data.SmartInstallAssistantManager
import com.arwora.feature.system.installassistant.model.AppInstallCategory
import com.arwora.feature.system.installassistant.model.InstalledAppAssistantRecord
import com.arwora.feature.system.installassistant.model.SetupChecklistStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SmartInstallAssistantViewModel(application: Application) : AndroidViewModel(application) {

    val allRecords: StateFlow<List<InstalledAppAssistantRecord>> = SmartInstallAssistantManager.records
    val isLoading: StateFlow<Boolean> = SmartInstallAssistantManager.isLoading

    private val _selectedCategory = MutableStateFlow(AppInstallCategory.ALL)
    val selectedCategory: StateFlow<AppInstallCategory> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPackageName = MutableStateFlow<String?>(null)
    val selectedPackageName: StateFlow<String?> = _selectedPackageName.asStateFlow()

    private val _syncingPackages = MutableStateFlow<Set<String>>(emptySet())
    val syncingPackages: StateFlow<Set<String>> = _syncingPackages.asStateFlow()

    val filteredRecords: StateFlow<List<InstalledAppAssistantRecord>> = combine(
        allRecords,
        _selectedCategory,
        _searchQuery
    ) { records, category, query ->
        val q = query.trim().lowercase()
        records.filter { rec ->
            val matchesCategory = category == AppInstallCategory.ALL || rec.category == category
            val matchesQuery = q.isEmpty() ||
                rec.appName.lowercase().contains(q) ||
                rec.packageName.lowercase().contains(q) ||
                rec.category.label.lowercase().contains(q)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val selectedRecord: StateFlow<InstalledAppAssistantRecord?> = combine(
        allRecords,
        _selectedPackageName
    ) { records, selectedPkg ->
        if (selectedPkg == null) null else records.firstOrNull { it.packageName == selectedPkg }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    val categoryCounts: StateFlow<Map<AppInstallCategory, Int>> = combine(
        allRecords,
        _isLoadingOrIdle()
    ) { records, _ ->
        val map = mutableMapOf<AppInstallCategory, Int>()
        map[AppInstallCategory.ALL] = records.size
        AppInstallCategory.entries
            .filter { it != AppInstallCategory.ALL }
            .forEach { cat ->
                map[cat] = records.count { it.category == cat }
            }
        map
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyMap())

    private fun _isLoadingOrIdle(): StateFlow<Boolean> = isLoading

    init {
        viewModelScope.launch {
            SmartInstallAssistantManager.initIfNeeded(getApplication())
        }
    }

    fun refresh() {
        viewModelScope.launch {
            SmartInstallAssistantManager.refreshAll(getApplication())
        }
    }

    fun selectCategory(category: AppInstallCategory) {
        _selectedCategory.value = category
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectPackage(packageName: String?) {
        _selectedPackageName.value = packageName
        if (!packageName.isNullOrBlank()) {
            viewModelScope.launch {
                val existing = allRecords.value.any { it.packageName == packageName }
                if (!existing) {
                    SmartInstallAssistantManager.recordPackageEvent(
                        context = getApplication(),
                        packageName = packageName,
                        isUpdate = false
                    )
                } else {
                    SmartInstallAssistantManager.auditPackage(getApplication(), packageName)
                }
            }
        }
    }

    fun reAuditPackage(packageName: String) {
        viewModelScope.launch {
            SmartInstallAssistantManager.auditPackage(getApplication(), packageName)
        }
    }

    fun toggleChecklistStep(packageName: String, step: SetupChecklistStep, completed: Boolean) {
        viewModelScope.launch {
            SmartInstallAssistantManager.setChecklistStepCompleted(
                context = getApplication(),
                packageName = packageName,
                step = step,
                completed = completed
            )
        }
    }

    fun markStepReviewed(packageName: String, step: SetupChecklistStep) {
        viewModelScope.launch {
            SmartInstallAssistantManager.markStepReviewed(
                context = getApplication(),
                packageName = packageName,
                step = step
            )
        }
    }

    fun syncOnlineMetadata(packageName: String) {
        if (_syncingPackages.value.contains(packageName)) return
        viewModelScope.launch {
            _syncingPackages.value = _syncingPackages.value + packageName
            try {
                SmartInstallAssistantManager.syncOnlineAppInfo(getApplication(), packageName)
            } finally {
                _syncingPackages.value = _syncingPackages.value - packageName
            }
        }
    }
}
