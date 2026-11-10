package com.arwora.feature.system.linkshortener

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LinkShortenerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LinkShortenerRepository()
    private val historyStore = LinkHistoryStore(application)

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _selectedProvider = MutableStateFlow(ShortenerProvider.AUTO)
    val selectedProvider: StateFlow<ShortenerProvider> = _selectedProvider.asStateFlow()

    private val _isShortening = MutableStateFlow(false)
    val isShortening: StateFlow<Boolean> = _isShortening.asStateFlow()

    private val _lastResult = MutableStateFlow<ShortenResult?>(null)
    val lastResult: StateFlow<ShortenResult?> = _lastResult.asStateFlow()

    private val _historyList = MutableStateFlow<List<HistoryItem>>(emptyList())
    val historyList: StateFlow<List<HistoryItem>> = _historyList.asStateFlow()

    init {
        refreshHistory()
    }

    fun updateUrlInput(input: String) {
        _urlInput.value = input
    }

    fun updateProvider(provider: ShortenerProvider) {
        _selectedProvider.value = provider
    }

    fun clearInput() {
        _urlInput.value = ""
    }

    fun clearResult() {
        _lastResult.value = null
    }

    fun refreshHistory() {
        _historyList.value = historyStore.getHistory()
    }

    fun shortenCurrentUrl() {
        val input = _urlInput.value.trim()
        if (input.isEmpty()) return

        _isShortening.value = true
        _lastResult.value = null

        viewModelScope.launch {
            try {
                val result = repository.shortenUrl(input, _selectedProvider.value)
                _lastResult.value = result
                
                if (result.status == ShortenerStatus.SUCCESS) {
                    historyStore.addHistoryItem(
                        originalUrl = result.originalUrl,
                        shortenedUrl = result.shortenedUrl,
                        provider = result.provider.label
                    )
                    refreshHistory()
                }
            } catch (e: Exception) {
                _lastResult.value = ShortenResult(
                    originalUrl = input,
                    shortenedUrl = "",
                    provider = _selectedProvider.value,
                    status = ShortenerStatus.FAILED,
                    errorMessage = e.localizedMessage
                )
            } finally {
                _isShortening.value = false
            }
        }
    }

    fun deleteHistoryItem(id: String) {
        historyStore.deleteHistoryItem(id)
        refreshHistory()
    }

    fun clearAllHistory() {
        historyStore.clearAllHistory()
        refreshHistory()
    }
}
