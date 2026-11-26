package com.arwora.feature.home.weather

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeWeatherViewModel(
    application: Application,
    private val repository: WeatherRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(application, WeatherRepository(application))

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private var hasInitialized = false
    private var activeFetchJob: Job? = null

    fun ensureInitialized() {
        if (hasInitialized) return
        hasInitialized = true

        viewModelScope.launch {
            val cached = repository.getCachedWeather()
            val needsPermission = !repository.hasLocationPermission()

            if (cached != null) {
                _uiState.value = WeatherUiState.Success(
                    data = cached,
                    isRefreshing = false,
                    needsLocationPermission = needsPermission
                )
                val shouldUpgradeFromFallback =
                    !needsPermission && cached.locationSource != WeatherLocationSource.GPS
                if (!repository.isCacheFresh(cached) || shouldUpgradeFromFallback) {
                    refresh(force = false)
                }
            } else {
                _uiState.value = WeatherUiState.Loading(cached = null)
                refresh(force = true)
            }
        }
    }

    fun refresh(force: Boolean = true) {
        if (activeFetchJob?.isActive == true) {
            if (!force) return
            activeFetchJob?.cancel()
        }

        activeFetchJob = viewModelScope.launch {
            val needsPermission = !repository.hasLocationPermission()
            val currentData = when (val s = _uiState.value) {
                is WeatherUiState.Success -> s.data
                is WeatherUiState.Loading -> s.cached
                is WeatherUiState.Error -> null
            }

            if (currentData != null) {
                _uiState.value = WeatherUiState.Success(
                    data = currentData,
                    isRefreshing = true,
                    needsLocationPermission = needsPermission,
                    staleErrorMessage = null
                )
            } else {
                _uiState.value = WeatherUiState.Loading(cached = null)
            }

            val result = repository.fetchLiveWeather()
            val latestNeedsPermission = !repository.hasLocationPermission()

            result.onSuccess { snapshot ->
                _uiState.value = WeatherUiState.Success(
                    data = snapshot,
                    isRefreshing = false,
                    needsLocationPermission = latestNeedsPermission,
                    staleErrorMessage = null
                )
            }.onFailure { err ->
                val errorText = err.message?.takeIf { it.isNotBlank() }
                    ?: "Unable to reach weather service"
                if (currentData != null) {
                    _uiState.value = WeatherUiState.Success(
                        data = currentData,
                        isRefreshing = false,
                        needsLocationPermission = latestNeedsPermission,
                        staleErrorMessage = "Offline • Showing cached forecast"
                    )
                } else {
                    _uiState.value = WeatherUiState.Error(
                        message = "Unable to load live weather ($errorText)",
                        needsLocationPermission = latestNeedsPermission
                    )
                }
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        val current = _uiState.value
        if (current is WeatherUiState.Success) {
            _uiState.value = current.copy(needsLocationPermission = !granted)
        } else if (current is WeatherUiState.Error) {
            _uiState.value = current.copy(needsLocationPermission = !granted)
        }
        if (granted) {
            refresh(force = true)
        }
    }

    override fun onCleared() {
        activeFetchJob?.cancel()
        super.onCleared()
    }
}
