package com.arwora.feature.system.downloadworkflow.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arwora.feature.system.downloadworkflow.data.DownloadWorkflowManager
import com.arwora.feature.system.downloadworkflow.model.DownloadFileCategory
import com.arwora.feature.system.downloadworkflow.model.DownloadWorkflowConfig
import com.arwora.feature.system.downloadworkflow.model.ProcessedDownloadEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DownloadWorkflowFilter(val id: String, val label: String) {
    ALL("all", "All"),
    APKS("apks", "APKs"),
    ARCHIVES("archives", "Archives"),
    MEDIA("media", "Media"),
    DOCUMENTS("documents", "Documents"),
    DUPLICATES("duplicates", "Duplicates")
}

class SmartDownloadWorkflowViewModel(application: Application) : AndroidViewModel(application) {

    val config: StateFlow<DownloadWorkflowConfig> = DownloadWorkflowManager.config
    val isServiceRunning: StateFlow<Boolean> = DownloadWorkflowManager.isServiceRunning
    val allEvents: StateFlow<List<ProcessedDownloadEvent>> = DownloadWorkflowManager.processedEvents
    val isScanning: StateFlow<Boolean> = DownloadWorkflowManager.isScanning
    val statusMessage: StateFlow<String?> = DownloadWorkflowManager.statusMessage

    private val _selectedFilter = MutableStateFlow(DownloadWorkflowFilter.ALL)
    val selectedFilter: StateFlow<DownloadWorkflowFilter> = _selectedFilter.asStateFlow()

    private val _selectedEventId = MutableStateFlow<String?>(null)
    val selectedEventId: StateFlow<String?> = _selectedEventId.asStateFlow()

    val filteredEvents: StateFlow<List<ProcessedDownloadEvent>> = combine(
        allEvents,
        _selectedFilter
    ) { events, filter ->
        when (filter) {
            DownloadWorkflowFilter.ALL -> events
            DownloadWorkflowFilter.APKS -> events.filter { it.category == DownloadFileCategory.APKS }
            DownloadWorkflowFilter.ARCHIVES -> events.filter { it.category == DownloadFileCategory.ARCHIVES }
            DownloadWorkflowFilter.MEDIA -> events.filter { it.category == DownloadFileCategory.MEDIA }
            DownloadWorkflowFilter.DOCUMENTS -> events.filter { it.category == DownloadFileCategory.DOCUMENTS }
            DownloadWorkflowFilter.DUPLICATES -> events.filter { it.isDuplicate }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, allEvents.value)

    val selectedEvent: StateFlow<ProcessedDownloadEvent?> = combine(
        allEvents,
        _selectedEventId
    ) { events, eventId ->
        if (eventId.isNullOrBlank()) null else events.firstOrNull { it.id == eventId }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            DownloadWorkflowManager.initIfNeeded(getApplication())
        }
    }

    fun selectFilter(filter: DownloadWorkflowFilter) {
        _selectedFilter.value = filter
    }

    fun selectEvent(eventId: String?) {
        _selectedEventId.value = eventId
    }

    fun setMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            DownloadWorkflowManager.setMasterEnabled(getApplication(), enabled)
        }
    }

    fun setCategorizationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            DownloadWorkflowManager.setCategorizationEnabled(getApplication(), enabled)
        }
    }

    fun setAutoRenameEnabled(enabled: Boolean) {
        viewModelScope.launch {
            DownloadWorkflowManager.setAutoRenameEnabled(getApplication(), enabled)
        }
    }

    fun setDuplicateDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            DownloadWorkflowManager.setDuplicateDetectionEnabled(getApplication(), enabled)
        }
    }

    fun setInstantNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            DownloadWorkflowManager.setInstantNotificationEnabled(getApplication(), enabled)
        }
    }

    fun scanNow() {
        viewModelScope.launch {
            DownloadWorkflowManager.scanDownloadDirectoryNow(
                context = getApplication(),
                stabilityDurationMs = 0L,
                requireMasterEnabled = false
            )
        }
    }

    fun deleteFile(eventId: String, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = DownloadWorkflowManager.deleteProcessedFile(getApplication(), eventId)
            if (ok && _selectedEventId.value == eventId) {
                _selectedEventId.value = null
            }
            onDone(ok)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            DownloadWorkflowManager.clearWorkflowLogs(getApplication())
            _selectedEventId.value = null
        }
    }
}
