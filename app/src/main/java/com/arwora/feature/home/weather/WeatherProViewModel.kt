package com.arwora.feature.home.weather

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.Locale

class WeatherProViewModel(
    application: Application,
    private val repository: WeatherRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, WeatherRepository(application))

    private val _uiState = MutableStateFlow<ProWeatherUiState>(ProWeatherUiState.Loading())
    val uiState: StateFlow<ProWeatherUiState> = _uiState.asStateFlow()

    private val _alertSettings = MutableStateFlow(repository.getAlertSettings())
    val alertSettings: StateFlow<SmartWeatherAlertSettings> = _alertSettings.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SavedWeatherCity>>(emptyList())
    val searchResults: StateFlow<List<SavedWeatherCity>> = _searchResults.asStateFlow()

    private val _isSearchingCity = MutableStateFlow(false)
    val isSearchingCity: StateFlow<Boolean> = _isSearchingCity.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _liveAlertsPreview = MutableStateFlow<List<TriggeredWeatherAlert>>(emptyList())
    val liveAlertsPreview: StateFlow<List<TriggeredWeatherAlert>> = _liveAlertsPreview.asStateFlow()

    private var initialized = false
    private var alertsObserverStarted = false
    private val alertWriteMutex = Mutex()
    private var pendingAlertWrites = 0
    private var fetchJob: Job? = null
    private var searchJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        startObservingAlertSettings()
    }

    private fun startObservingAlertSettings() {
        if (alertsObserverStarted) return
        alertsObserverStarted = true
        viewModelScope.launch {
            val initial = repository.getAlertSettingsSuspend()
            if (pendingAlertWrites == 0) {
                _alertSettings.value = initial
                (_uiState.value as? ProWeatherUiState.Success)?.bundle?.let { evaluateAlertsInternal(it) }
            }
            repository.observeAlertSettings().collect { latest ->
                if (pendingAlertWrites == 0) {
                    _alertSettings.value = latest
                    (_uiState.value as? ProWeatherUiState.Success)?.bundle?.let { evaluateAlertsInternal(it) }
                }
            }
        }
    }

    fun ensureInitialized() {
        startObservingAlertSettings()
        if (initialized) return
        initialized = true
        WeatherAlertWorker.schedulePeriodic(getApplication())

        viewModelScope.launch {
            val cached = repository.loadCachedProWeather()
            if (cached != null) {
                _uiState.value = ProWeatherUiState.Success(
                    bundle = cached,
                    isRefreshing = false,
                    statusBanner = WeatherProParser.formatRelativeMinutes(cached.fetchedAtMillis, repository.nowMillis())
                )
                evaluateAlertsInternal(cached)
            }
            refresh(force = true)
        }
    }

    fun refresh(force: Boolean = true) {
        if (fetchJob?.isActive == true) {
            if (!force) return
            fetchJob?.cancel()
        }
        fetchJob = viewModelScope.launch {
            val currentBundle = when (val s = _uiState.value) {
                is ProWeatherUiState.Success -> s.bundle
                is ProWeatherUiState.Loading -> s.cached
                is ProWeatherUiState.Error -> null
            }

            if (currentBundle != null) {
                _uiState.value = ProWeatherUiState.Success(
                    bundle = currentBundle,
                    isRefreshing = true,
                    statusBanner = "Updating live weather..."
                )
            } else {
                _uiState.value = ProWeatherUiState.Loading(cached = null)
            }

            val result = repository.fetchProWeather()
            result.onSuccess { bundle ->
                _uiState.value = ProWeatherUiState.Success(
                    bundle = bundle,
                    isRefreshing = false,
                    statusBanner = WeatherProParser.formatRelativeMinutes(bundle.fetchedAtMillis, repository.nowMillis())
                )
                evaluateAlertsInternal(bundle)
            }.onFailure { err ->
                if (currentBundle != null) {
                    val offlineBundle = currentBundle.copy(isFromOfflineCache = true)
                    val rel = WeatherProParser.formatRelativeMinutes(offlineBundle.fetchedAtMillis, repository.nowMillis())
                    _uiState.value = ProWeatherUiState.Success(
                        bundle = offlineBundle,
                        isRefreshing = false,
                        statusBanner = "Offline Mode • $rel"
                    )
                    evaluateAlertsInternal(offlineBundle)
                } else {
                    _uiState.value = ProWeatherUiState.Error(
                        message = err.message ?: "Unable to fetch weather forecast"
                    )
                }
            }
        }
    }

    fun selectCity(cityId: String) {
        repository.setActiveCityId(cityId)
        refresh(force = true)
    }

    fun searchCities(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _searchResults.value = emptyList()
            _isSearchingCity.value = false
            return
        }
        searchJob = viewModelScope.launch {
            _isSearchingCity.value = true
            repository.searchCities(query).onSuccess { list ->
                _searchResults.value = list
            }.onFailure {
                _searchResults.value = emptyList()
            }
            _isSearchingCity.value = false
        }
    }

    fun addAndSelectCity(city: SavedWeatherCity) {
        val updated = repository.saveFavoriteCity(city)
        repository.setActiveCityId(city.id)
        _searchResults.value = emptyList()
        val current = (_uiState.value as? ProWeatherUiState.Success)?.bundle
        if (current != null) {
            _uiState.value = ProWeatherUiState.Success(
                bundle = current.copy(savedCities = updated, activeCityId = city.id),
                isRefreshing = true
            )
        }
        refresh(force = true)
    }

    fun removeFavoriteCity(cityId: String) {
        val updated = repository.removeFavoriteCity(cityId)
        val active = repository.getActiveCityId()
        val current = (_uiState.value as? ProWeatherUiState.Success)?.bundle
        if (current != null) {
            _uiState.value = ProWeatherUiState.Success(
                bundle = current.copy(savedCities = updated, activeCityId = active),
                isRefreshing = false
            )
        }
        if (current?.activeCityId == cityId) {
            refresh(force = true)
        }
    }

    fun updateAlertSettings(transform: (SmartWeatherAlertSettings) -> SmartWeatherAlertSettings) {
        var updated = _alertSettings.value
        _alertSettings.update { current ->
            transform(current).also { updated = it }
        }
        pendingAlertWrites++
        viewModelScope.launch {
            alertWriteMutex.withLock {
                try {
                    repository.saveAlertSettingsSuspend(updated)
                } finally {
                    pendingAlertWrites = (pendingAlertWrites - 1).coerceAtLeast(0)
                }
            }
        }
        WeatherAlertWorker.schedulePeriodic(getApplication())
        (_uiState.value as? ProWeatherUiState.Success)?.bundle?.let { evaluateAlertsInternal(it) }
    }

    fun resetAlertSettingsToDefaults() {
        updateAlertSettings { SmartWeatherAlertSettings() }
    }

    fun runManualAlertCheckAndNotify(): Int {
        val bundle = (_uiState.value as? ProWeatherUiState.Success)?.bundle ?: return 0
        val triggered = evaluateAlertsInternal(bundle)
        return WeatherAlertWorker.postEligibleNotifications(getApplication(), triggered, forceAll = true)
    }

    private fun evaluateAlertsInternal(bundle: ProWeatherBundle): List<TriggeredWeatherAlert> {
        val nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val alerts = WeatherAdvisoryEngine.evaluateSmartAlerts(bundle, _alertSettings.value, nowHour)
        _liveAlertsPreview.value = alerts
        return alerts
    }

    fun toggleBanglaVoiceForecast() {
        val bundle = (_uiState.value as? ProWeatherUiState.Success)?.bundle ?: return
        if (_isSpeaking.value) {
            runCatching { tts?.stop() }
            _isSpeaking.value = false
            return
        }

        val next12Rain = bundle.hourly24.take(12).maxOfOrNull { it.precipitationProbabilityPercent } ?: 0
        val outfit = WeatherAdvisoryEngine.computeOutfitAdvice(bundle.current, next12Rain)
        val banglaText = WeatherAdvisoryEngine.buildBanglaVoiceSummary(bundle.current, outfit)
        val fallbackText = WeatherAdvisoryEngine.buildBanglishVoiceSummary(bundle.current)

        if (tts == null) {
            _isSpeaking.value = true
            tts = TextToSpeech(getApplication()) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    speakNow(banglaText, fallbackText)
                } else {
                    _isSpeaking.value = false
                }
            }
        } else if (ttsReady) {
            _isSpeaking.value = true
            speakNow(banglaText, fallbackText)
        }
    }

    private fun speakNow(banglaText: String, fallbackText: String) {
        val engine = tts ?: return
        val bnLocale = Locale("bn", "BD")
        val langResult = engine.setLanguage(bnLocale)
        val chosenText = if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.US)
            fallbackText
        } else {
            banglaText
        }
        engine.speak(chosenText, TextToSpeech.QUEUE_FLUSH, null, "arwora_weather_tts")
    }

    override fun onCleared() {
        fetchJob?.cancel()
        searchJob?.cancel()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        super.onCleared()
    }
}
