package com.arwora.feature.system.wifi.password

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arwora.utils.AdbShellExecutor
import com.arwora.utils.NativeAdbConnectionState
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.PrivilegeEngine
import com.arwora.utils.ShizukuBridgeManager
import com.arwora.utils.ShizukuConnectOutcome
import com.arwora.utils.ShizukuUiStatus
import com.arwora.utils.WirelessPairingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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

    private val _individuallyMaskedIds = MutableStateFlow<Set<String>>(emptySet())

    private val _isGlobalRevealAll = MutableStateFlow(true)
    val isGlobalRevealAll: StateFlow<Boolean> = _isGlobalRevealAll.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedNetworkForQr = MutableStateFlow<SavedWifiNetwork?>(null)
    val selectedNetworkForQr: StateFlow<SavedWifiNetwork?> = _selectedNetworkForQr.asStateFlow()

    private val _isAddDialogOpen = MutableStateFlow(false)
    val isAddDialogOpen: StateFlow<Boolean> = _isAddDialogOpen.asStateFlow()

    val isElevatedModeAvailable: StateFlow<Boolean> = repository.isElevatedModeAvailable
    val adbState: StateFlow<NativeAdbConnectionState> = repository.adbState
    val activeEngine: StateFlow<PrivilegeEngine> = repository.activeEngine
    val shizukuStatus: StateFlow<ShizukuUiStatus> = ShizukuBridgeManager.connectionStatus

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
        viewModelScope.launch {
            NativeAdbManager.isConnected.collectLatest {
                refreshAdbStatus()
            }
        }
        viewModelScope.launch {
            NativeAdbManager.connectionState.collectLatest {
                refreshAdbStatus()
            }
        }
        viewModelScope.launch {
            AdbShellExecutor.activeEngine.collectLatest {
                refreshAdbStatus()
            }
        }
        viewModelScope.launch {
            ShizukuBridgeManager.connectionStatus.collectLatest {
                refreshAdbStatus()
            }
        }
        viewModelScope.launch {
            repository.networks.collectLatest { list ->
                if (_isGlobalRevealAll.value) {
                    val masked = _individuallyMaskedIds.value
                    _revealedPasswordIds.value = list
                        .map { it.id }
                        .filterNot { masked.contains(it) }
                        .toSet()
                }
            }
        }
    }

    fun refreshAdbStatus() {
        val prevElevated = isElevatedModeAvailable.value
        val prevEngine = activeEngine.value
        val state = repository.refreshAdbState()
        val nowElevated = isElevatedModeAvailable.value
        val nowEngine = activeEngine.value
        if ((!prevElevated && (nowElevated || state == NativeAdbConnectionState.CONNECTED)) ||
            (nowElevated && prevEngine != nowEngine)
        ) {
            refresh()
        }
    }

    fun refresh(revealAfter: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.refreshSavedNetworks()
                if (revealAfter) {
                    _isGlobalRevealAll.value = true
                    _individuallyMaskedIds.value = emptySet()
                    _revealedPasswordIds.value = repository.networks.value.map { it.id }.toSet()
                } else if (_isGlobalRevealAll.value) {
                    val masked = _individuallyMaskedIds.value
                    _revealedPasswordIds.value = repository.networks.value
                        .map { it.id }
                        .filterNot { masked.contains(it) }
                        .toSet()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun togglePasswordVisibility(networkId: String) {
        if (_isGlobalRevealAll.value) {
            val masked = _individuallyMaskedIds.value.toMutableSet()
            if (masked.contains(networkId)) {
                masked.remove(networkId)
            } else {
                masked.add(networkId)
            }
            _individuallyMaskedIds.value = masked
            _revealedPasswordIds.value = repository.networks.value
                .map { it.id }
                .filterNot { masked.contains(it) }
                .toSet()
        } else {
            val current = _revealedPasswordIds.value.toMutableSet()
            if (current.contains(networkId)) {
                current.remove(networkId)
            } else {
                current.add(networkId)
            }
            _revealedPasswordIds.value = current
        }
    }

    fun toggleRevealAll() {
        val newState = !_isGlobalRevealAll.value
        _isGlobalRevealAll.value = newState
        _individuallyMaskedIds.value = emptySet()
        if (newState) {
            val allIds = repository.networks.value.map { it.id }.toSet()
            _revealedPasswordIds.value = allIds
            repository.refreshAdbState()
            if (repository.networks.value.isEmpty() || isElevatedModeAvailable.value) {
                refresh(revealAfter = true)
            }
        } else {
            _revealedPasswordIds.value = emptySet()
        }
    }

    private val _isImportDialogOpen = MutableStateFlow(false)
    val isImportDialogOpen: StateFlow<Boolean> = _isImportDialogOpen.asStateFlow()

    private val _isForgetAllDialogOpen = MutableStateFlow(false)
    val isForgetAllDialogOpen: StateFlow<Boolean> = _isForgetAllDialogOpen.asStateFlow()

    fun isPasswordVisible(networkId: String): Boolean {
        return if (_isGlobalRevealAll.value) {
            !_individuallyMaskedIds.value.contains(networkId)
        } else {
            _revealedPasswordIds.value.contains(networkId)
        }
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

    fun connectAdb(context: Context) {
        viewModelScope.launch {
            val (ok, msg) = WirelessPairingManager.connectUnifiedWirelessAdb(context)
            repository.refreshAdbState()
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            if (ok) {
                refresh()
            }
        }
    }

    fun connectShizuku(context: Context) {
        val outcome = ShizukuBridgeManager.connectShizuku(context)
        refreshAdbStatus()
        when (outcome) {
            ShizukuConnectOutcome.ALREADY_CONNECTED -> {
                Toast.makeText(context, "Shizuku connected", Toast.LENGTH_SHORT).show()
                refresh()
            }
            ShizukuConnectOutcome.PERMISSION_REQUESTED -> {
                Toast.makeText(context, "Waiting for Shizuku authorization...", Toast.LENGTH_SHORT).show()
            }
            ShizukuConnectOutcome.SERVICE_STOPPED_OPENED_APP -> {
                Toast.makeText(context, "Start Shizuku service, then return to view Wi-Fi passwords", Toast.LENGTH_LONG).show()
            }
            ShizukuConnectOutcome.SERVICE_STOPPED_UNAVAILABLE -> {
                Toast.makeText(context, "Shizuku is not installed on this device", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
