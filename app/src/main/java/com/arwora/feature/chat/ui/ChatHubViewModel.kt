package com.arwora.feature.chat.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arwora.feature.chat.data.ChatHubRepository
import com.arwora.feature.chat.model.ChatHubSettings
import com.arwora.feature.chat.model.ChatHubTab
import com.arwora.feature.chat.model.ChatMessage
import com.arwora.feature.chat.model.ChatMessageType
import com.arwora.feature.chat.model.ChatThread
import com.arwora.feature.chat.model.NearChatPeer
import com.arwora.feature.system.connect.data.ArworaConnectManager
import com.arwora.feature.system.connect.data.ArworaVoicePlayer
import com.arwora.feature.system.connect.data.ArworaVoiceRecorder
import com.arwora.feature.system.connect.model.P2pMessageRetentionPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatHubViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ChatHubRepository.getInstance(application)
    val voiceRecorder = ArworaVoiceRecorder(application)
    val voicePlayer = ArworaVoicePlayer(application)

    val allThreads: StateFlow<List<ChatThread>> = repo.observeAllThreads()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val settings: StateFlow<ChatHubSettings> = repo.settings
    val nearPeers: StateFlow<List<NearChatPeer>> = repo.nearPeers
    val isNearScanning: StateFlow<Boolean> = repo.nearTransport.isScanning
    val radarStatus: StateFlow<String> = repo.nearTransport.radarStatus
    val retentionPolicy: StateFlow<P2pMessageRetentionPolicy> = ArworaConnectManager.messageRetentionPolicy
    val lastRetentionDuration: StateFlow<P2pMessageRetentionPolicy> = ArworaConnectManager.lastNonOffRetentionPolicy

    private val _activeTab = MutableStateFlow(ChatHubTab.CHATS)
    val activeTab: StateFlow<ChatHubTab> = _activeTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _globalMatchedMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val globalMatchedMessages: StateFlow<List<ChatMessage>> = _globalMatchedMessages.asStateFlow()

    private val _activeThreadId = MutableStateFlow<String?>(null)
    val activeThreadId: StateFlow<String?> = _activeThreadId.asStateFlow()

    val activeThread: StateFlow<ChatThread?> = _activeThreadId
        .flatMapLatest { id -> if (id == null) flowOf(null) else repo.observeThread(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeMessages: StateFlow<List<ChatMessage>> = _activeThreadId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.observeMessagesForThread(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _replyToMessage = MutableStateFlow<ChatMessage?>(null)
    val replyToMessage: StateFlow<ChatMessage?> = _replyToMessage.asStateFlow()

    private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
    val editingMessage: StateFlow<ChatMessage?> = _editingMessage.asStateFlow()

    private val _inThreadSearchQuery = MutableStateFlow("")
    val inThreadSearchQuery: StateFlow<String> = _inThreadSearchQuery.asStateFlow()

    private val _inThreadDateFilterMillis = MutableStateFlow<Long?>(null)
    val inThreadDateFilterMillis: StateFlow<Long?> = _inThreadDateFilterMillis.asStateFlow()

    private val _inThreadSearchResults = MutableStateFlow<List<ChatMessage>>(emptyList())
    val inThreadSearchResults: StateFlow<List<ChatMessage>> = _inThreadSearchResults.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    val filteredThreads: StateFlow<List<ChatThread>> = combine(
        allThreads,
        _activeTab,
        _searchQuery,
        _isVaultUnlocked
    ) { list, tab, query, vaultUnlocked ->
        val clean = query.trim()
        list.filter { thread ->
            val tabMatches = when (tab) {
                ChatHubTab.CHATS -> !thread.isArchived && !thread.isHidden
                ChatHubTab.ARCHIVED -> thread.isArchived && !thread.isHidden
                ChatHubTab.VAULT -> thread.isHidden && vaultUnlocked
                ChatHubTab.NEAR_CHAT -> thread.peerDeviceId != null && !thread.isHidden
            }
            if (!tabMatches) return@filter false
            if (clean.isEmpty()) return@filter true
            thread.title.contains(clean, ignoreCase = true) ||
                thread.lastMessagePreview.contains(clean, ignoreCase = true) ||
                thread.participantNames.any { it.contains(clean, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(tab: ChatHubTab) {
        _activeTab.value = tab
    }

    fun setVaultUnlocked(unlocked: Boolean) {
        _isVaultUnlocked.value = unlocked
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            _globalMatchedMessages.value = if (query.isBlank()) {
                emptyList()
            } else {
                repo.searchGlobalMessages(query, includeHidden = _isVaultUnlocked.value)
            }
        }
    }

    fun openThread(threadId: String) {
        _activeThreadId.value = threadId
        _replyToMessage.value = null
        _editingMessage.value = null
        _inThreadSearchQuery.value = ""
        _inThreadDateFilterMillis.value = null
        viewModelScope.launch {
            repo.markThreadOpened(threadId)
            refreshInThreadSearch()
        }
    }

    fun closeThread() {
        val current = _activeThreadId.value
        if (current != null) {
            viewModelScope.launch { repo.sendTypingSignal(current, false) }
        }
        voicePlayer.stop()
        voiceRecorder.cancelRecording()
        _activeThreadId.value = null
        _replyToMessage.value = null
        _editingMessage.value = null
    }

    fun createDirectChat(name: String, onCreated: (ChatThread) -> Unit) {
        viewModelScope.launch {
            val thread = repo.createDirectThread(name)
            onCreated(thread)
        }
    }

    fun createGroupChat(title: String, members: List<String>, onCreated: (ChatThread) -> Unit) {
        viewModelScope.launch {
            val thread = repo.createGroupThread(title, members)
            onCreated(thread)
        }
    }

    fun missingNearbyPermissions(): List<String> = repo.nearTransport.missingNearbyPermissions()

    fun startNearDiscovery() {
        viewModelScope.launch {
            repo.nearTransport.startDiscovery()
        }
    }

    fun stopNearDiscovery() {
        repo.nearTransport.stopDiscovery()
    }

    fun connectAndOpenNearPeer(peer: NearChatPeer, onOpened: (ChatThread) -> Unit) {
        viewModelScope.launch {
            val res = repo.nearTransport.connectPeer(peer)
            res.onSuccess {
                val thread = repo.getOrCreatePeerThread(peer.copy(isConnected = true))
                _feedback.value = "Connected to ${peer.displayName}"
                onOpened(thread)
            }.onFailure { err ->
                _feedback.value = err.localizedMessage ?: "Unable to connect to ${peer.displayName}"
            }
        }
    }

    fun setReplyTarget(message: ChatMessage?) {
        _editingMessage.value = null
        _replyToMessage.value = message
    }

    fun startEditMessage(message: ChatMessage) {
        _replyToMessage.value = null
        _editingMessage.value = message
    }

    fun cancelComposerMode() {
        _replyToMessage.value = null
        _editingMessage.value = null
    }

    fun onComposerTextChanged(threadId: String, text: String) {
        viewModelScope.launch {
            repo.sendTypingSignal(threadId, text.isNotBlank())
        }
    }

    fun sendOrEditText(threadId: String, text: String, scheduledForMillis: Long? = null) {
        val editing = _editingMessage.value
        viewModelScope.launch {
            if (editing != null) {
                val ok = repo.editMessage(threadId, editing.id, text)
                if (ok) {
                    _editingMessage.value = null
                } else {
                    _feedback.value = "Unable to edit message"
                }
            } else {
                val reply = _replyToMessage.value
                val res = repo.sendTextMessage(
                    threadId = threadId,
                    text = text,
                    replyTo = reply,
                    scheduledForMillis = scheduledForMillis
                )
                res.onSuccess {
                    _replyToMessage.value = null
                    repo.sendTypingSignal(threadId, false)
                    if (scheduledForMillis != null) {
                        _feedback.value = "Message scheduled"
                    }
                }.onFailure {
                    _feedback.value = it.localizedMessage ?: "Failed to send message"
                }
            }
        }
    }

    fun sendSticker(threadId: String, stickerId: String) {
        viewModelScope.launch {
            val reply = _replyToMessage.value
            repo.sendStickerMessage(threadId, stickerId, reply).onSuccess {
                _replyToMessage.value = null
            }.onFailure {
                _feedback.value = it.localizedMessage ?: "Failed to send sticker"
            }
        }
    }

    fun sendPhoto(threadId: String, uri: Uri, isViewOnce: Boolean = false, scheduledForMillis: Long? = null) {
        viewModelScope.launch {
            val reply = _replyToMessage.value
            repo.sendImageMessage(
                threadId = threadId,
                uri = uri,
                isViewOnce = isViewOnce,
                replyTo = reply,
                scheduledForMillis = scheduledForMillis
            ).onSuccess {
                _replyToMessage.value = null
                if (isViewOnce) _feedback.value = "View-once photo sent"
            }.onFailure {
                _feedback.value = it.localizedMessage ?: "Unable to attach photo"
            }
        }
    }

    fun sendVideo(threadId: String, uri: Uri, isViewOnce: Boolean = false) {
        viewModelScope.launch {
            val reply = _replyToMessage.value
            repo.sendVideoMessage(
                threadId = threadId,
                uri = uri,
                isViewOnce = isViewOnce,
                replyTo = reply
            ).onSuccess {
                _replyToMessage.value = null
                if (isViewOnce) _feedback.value = "View-once video sent"
            }.onFailure {
                _feedback.value = it.localizedMessage ?: "Unable to attach video"
            }
        }
    }

    fun sendFile(threadId: String, uri: Uri) {
        viewModelScope.launch {
            val reply = _replyToMessage.value
            repo.sendFileMessage(threadId, uri, reply).onSuccess {
                _replyToMessage.value = null
            }.onFailure {
                _feedback.value = it.localizedMessage ?: "Unable to attach file"
            }
        }
    }

    fun sendCurrentLocation(threadId: String) {
        viewModelScope.launch {
            val reply = _replyToMessage.value
            repo.sendCurrentGpsLocation(threadId, reply).onSuccess {
                _replyToMessage.value = null
            }.onFailure {
                _feedback.value = it.localizedMessage ?: "Unable to fetch GPS location"
            }
        }
    }

    fun startVoiceRecording() {
        voiceRecorder.startRecording {
            val tid = _activeThreadId.value
            if (tid != null) stopAndSendVoice(tid)
        }.onFailure {
            _feedback.value = it.localizedMessage ?: "Microphone permission required"
        }
    }

    fun stopAndSendVoice(threadId: String) {
        viewModelScope.launch {
            val clipRes = voiceRecorder.stopRecordingAndEncode()
            clipRes.onSuccess { clip ->
                val reply = _replyToMessage.value
                repo.sendVoiceMessage(
                    threadId = threadId,
                    audioBase64 = clip.audioBase64,
                    durationMs = clip.durationMs,
                    waveform = clip.waveform,
                    replyTo = reply
                ).onSuccess {
                    _replyToMessage.value = null
                }
            }.onFailure {
                _feedback.value = it.localizedMessage ?: "Voice note cancelled"
            }
        }
    }

    fun cancelVoiceRecording() {
        voiceRecorder.cancelRecording()
    }

    fun toggleVoicePlayback(message: ChatMessage) {
        val b64 = message.mediaBase64 ?: return
        voicePlayer.togglePlayback(
            messageId = message.id,
            audioBase64 = b64,
            fallbackDurationMs = message.durationMs,
            onError = { _feedback.value = it }
        )
    }

    fun toggleReaction(threadId: String, messageId: String, emoji: String) {
        viewModelScope.launch {
            repo.toggleReaction(threadId, messageId, emoji)
        }
    }

    fun deleteForEveryone(threadId: String, messageId: String) {
        viewModelScope.launch {
            repo.deleteForEveryone(threadId, messageId)
        }
    }

    fun forwardMessage(message: ChatMessage, targetThreadId: String) {
        viewModelScope.launch {
            repo.forwardMessage(message, targetThreadId).onSuccess {
                _feedback.value = "Message forwarded"
            }.onFailure {
                _feedback.value = it.localizedMessage ?: "Forward failed"
            }
        }
    }

    fun consumeViewOnce(messageId: String) {
        viewModelScope.launch {
            repo.consumeViewOnceMedia(messageId)
        }
    }

    fun dispatchScheduledNow(messageId: String) {
        viewModelScope.launch {
            if (repo.dispatchScheduledMessageNow(messageId)) {
                _feedback.value = "Scheduled message sent now"
            }
        }
    }

    fun onScreenshotCaptured(threadId: String) {
        viewModelScope.launch {
            repo.recordScreenshotDetected(threadId, "You")
        }
    }

    fun updateInThreadSearch(query: String, dayMillis: Long?) {
        _inThreadSearchQuery.value = query
        _inThreadDateFilterMillis.value = dayMillis
        refreshInThreadSearch()
    }

    private fun refreshInThreadSearch() {
        val tid = _activeThreadId.value ?: return
        val q = _inThreadSearchQuery.value
        val day = _inThreadDateFilterMillis.value
        viewModelScope.launch {
            _inThreadSearchResults.value = repo.searchInThread(tid, q, day)
        }
    }

    fun toggleThreadLock(threadId: String) {
        viewModelScope.launch {
            repo.updateThread(threadId) { it.copy(isLocked = !it.isLocked) }
        }
    }

    fun toggleThreadHidden(threadId: String) {
        viewModelScope.launch {
            repo.updateThread(threadId) { it.copy(isHidden = !it.isHidden) }
        }
    }

    fun toggleThreadArchive(threadId: String) {
        viewModelScope.launch {
            repo.updateThread(threadId) { it.copy(isArchived = !it.isArchived) }
        }
    }

    fun toggleThreadMute(threadId: String) {
        viewModelScope.launch {
            repo.updateThread(threadId) { it.copy(isMuted = !it.isMuted) }
        }
    }

    fun setThreadCustomRingtone(threadId: String, uri: String?, name: String?) {
        viewModelScope.launch {
            repo.updateThread(threadId) { it.copy(customSoundUri = uri, customSoundName = name) }
        }
    }

    fun setThreadTheme(threadId: String, themeId: String) {
        viewModelScope.launch {
            repo.updateThread(threadId) { it.copy(themeId = themeId) }
        }
    }

    fun setThreadWallpaper(threadId: String, presetOrUri: String) {
        viewModelScope.launch {
            repo.updateThread(threadId) { it.copy(wallpaperPresetOrUri = presetOrUri) }
        }
    }

    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            if (_activeThreadId.value == threadId) {
                closeThread()
            }
            repo.deleteThread(threadId)
            _feedback.value = "Chat deleted"
        }
    }

    fun updateSettings(transform: (ChatHubSettings) -> ChatHubSettings) {
        repo.preferencesStore.update(transform)
    }

    fun setRetentionPolicy(policy: P2pMessageRetentionPolicy) {
        viewModelScope.launch {
            repo.applyRetentionPolicy(policy)
        }
    }

    fun isLowBatteryOrDataSaverActive(): Boolean = repo.preferencesStore.isLowBatteryOrDataSaverActive()
    fun isWithinDndNow(): Boolean = repo.preferencesStore.isWithinDndSchedule()

    fun clearFeedback() {
        _feedback.value = null
    }

    override fun onCleared() {
        voiceRecorder.release()
        voicePlayer.release()
        super.onCleared()
    }
}
