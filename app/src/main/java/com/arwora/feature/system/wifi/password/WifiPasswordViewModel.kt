package com.arwora.feature.system.wifi.password

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arwora.feature.system.shell.ShellExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class WifiPasswordViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WifiPasswordRepository(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _revealedPasswordIds = MutableStateFlow<Set<String>>(emptySet())
    val revealedPasswordIds: StateFlow<Set<String>> = _revealedPasswordIds.asStateFlow()

    private val _isGlobalRevealAll = MutableStateFlow(false)
    val isGlobalRevealAll: StateFlow<Boolean> = _isGlobalRevealAll.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedNetworkForQr = MutableStateFlow<SavedWifiNetwork?>(null)
    val selectedNetworkForQr: StateFlow<SavedWifiNetwork?> = _selectedNetworkForQr.asStateFlow()

    private val _isAddDialogOpen = MutableStateFlow(false)
    val isAddDialogOpen: StateFlow<Boolean> = _isAddDialogOpen.asStateFlow()

    val isElevatedModeAvailable: StateFlow<Boolean> = repository.isElevatedModeAvailable

    val filteredNetworks: StateFlow<List<SavedWifiNetwork>> = combine(
        repository.networks,
        _searchQuery
    ) { networks, query ->
        if (query.isBlank()) {
            networks
        } else {
            val q = query.trim().lowercase()
            networks.filter {
                it.ssid.lowercase().contains(q) ||
                it.securityType.displayName.lowercase().contains(q) ||
                it.securityType.badge.lowercase().contains(q) ||
                (it.bssid?.lowercase()?.contains(q) == true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.refreshSavedNetworks()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun togglePasswordVisibility(networkId: String) {
        val current = _revealedPasswordIds.value.toMutableSet()
        if (current.contains(networkId)) {
            current.remove(networkId)
        } else {
            current.add(networkId)
        }
        _revealedPasswordIds.value = current
    }

    fun toggleRevealAll() {
        val newState = !_isGlobalRevealAll.value
        _isGlobalRevealAll.value = newState
        if (newState) {
            val allIds = filteredNetworks.value.map { it.id }.toSet()
            _revealedPasswordIds.value = allIds
        } else {
            _revealedPasswordIds.value = emptySet()
        }
    }

    private val _isImportDialogOpen = MutableStateFlow(false)
    val isImportDialogOpen: StateFlow<Boolean> = _isImportDialogOpen.asStateFlow()

    private val _isForgetAllDialogOpen = MutableStateFlow(false)
    val isForgetAllDialogOpen: StateFlow<Boolean> = _isForgetAllDialogOpen.asStateFlow()

    fun isPasswordVisible(networkId: String): Boolean {
        return _isGlobalRevealAll.value || _revealedPasswordIds.value.contains(networkId)
    }

    fun copyPasswordToClipboard(context: Context, network: SavedWifiNetwork) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val textToCopy = if (network.securityType == WifiSecurityType.OPEN) {
            "Open Network (No Password)"
        } else {
            network.password
        }
        val clip = ClipData.newPlainText("Wi-Fi Password", textToCopy)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Password copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun copySsidToClipboard(context: Context, ssid: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Wi-Fi SSID", ssid)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "SSID \"$ssid\" copied!", Toast.LENGTH_SHORT).show()
    }

    fun connectToNetwork(context: Context, network: SavedWifiNetwork) {
        Toast.makeText(context, "Connecting to \"${network.ssid}\"...", Toast.LENGTH_SHORT).show()
        viewModelScope.launch {
            repository.connectToNetwork(network)
        }
    }

    fun forgetNetwork(context: Context, network: SavedWifiNetwork) {
        viewModelScope.launch {
            val success = repository.forgetNetwork(network)
            if (success) {
                Toast.makeText(context, "Removed \"${network.ssid}\" from saved networks", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportNetworks(context: Context) {
        val json = repository.exportNetworksJson()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Exported Wi-Fi Networks", json)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Exported networks copied to clipboard", Toast.LENGTH_LONG).show()
    }

    fun openImportDialog() {
        _isImportDialogOpen.value = true
    }

    fun closeImportDialog() {
        _isImportDialogOpen.value = false
    }

    fun importNetworks(context: Context, data: String) {
        viewModelScope.launch {
            val count = repository.importNetworks(data)
            Toast.makeText(context, if (count > 0) "Imported $count networks" else "No networks imported", Toast.LENGTH_SHORT).show()
            closeImportDialog()
        }
    }

    fun openForgetAllDialog() {
        _isForgetAllDialogOpen.value = true
    }

    fun closeForgetAllDialog() {
        _isForgetAllDialogOpen.value = false
    }

    fun forgetAllNetworks(context: Context) {
        viewModelScope.launch {
            repository.forgetAllNetworks()
            Toast.makeText(context, "All saved networks forgotten", Toast.LENGTH_SHORT).show()
            closeForgetAllDialog()
        }
    }

    fun openQrDialog(network: SavedWifiNetwork) {
        _selectedNetworkForQr.value = network
    }

    fun closeQrDialog() {
        _selectedNetworkForQr.value = null
    }

    fun openAddDialog() {
        _isAddDialogOpen.value = true
    }

    fun closeAddDialog() {
        _isAddDialogOpen.value = false
    }

    fun saveCustomNetwork(ssid: String, password: String, securityType: WifiSecurityType) {
        if (ssid.isBlank()) return
        val newNet = SavedWifiNetwork(
            id = UUID.randomUUID().toString(),
            ssid = ssid.trim(),
            password = password.trim(),
            securityType = securityType,
            isCurrentlyConnected = false,
            lastConnectedTimestamp = System.currentTimeMillis(),
            source = WifiSource.LOCAL_VAULT
        )
        viewModelScope.launch {
            repository.saveOrUpdateNetwork(newNet)
            closeAddDialog()
        }
    }

    fun requestShizukuPermission() {
        ShellExecutor.requestPermission(1002)
    }
}
