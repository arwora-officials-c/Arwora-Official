package com.arwora.feature.system.connect.ui

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.feature.system.connect.data.ArworaConnectChatManager
import com.arwora.feature.system.connect.data.ArworaVoicePlayer
import com.arwora.feature.system.connect.data.ArworaVoiceRecorder
import com.arwora.feature.system.connect.model.ChatMessageType
import com.arwora.feature.system.connect.model.ConnectChatMessage
import com.arwora.feature.system.connect.model.P2pMessageRetentionPolicy
import com.arwora.feature.system.connect.model.PeerConnectionState
import com.arwora.feature.system.connect.model.PeerUiState
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArworaConnectChatDialog(
    peerUi: PeerUiState,
    messages: List<ConnectChatMessage>,
    localDeviceId: String,
    isSendingImage: Boolean,
    retentionPolicy: P2pMessageRetentionPolicy = P2pMessageRetentionPolicy.OFF,
    onSendText: (String) -> Unit,
    onSendImageUri: (Uri) -> Unit,
    onSendVoiceNote: (audioBase64: String, durationMs: Long, waveform: List<Float>) -> Unit = { _, _, _ -> },
    onToggleReaction: (messageId: String, emoji: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val voiceRecorder = remember(context) { ArworaVoiceRecorder(context) }
    val voicePlayer = remember(context) { ArworaVoicePlayer(context) }

    val isRecording by voiceRecorder.isRecording.collectAsStateWithLifecycle()
    val recordingElapsedMs by voiceRecorder.elapsedMs.collectAsStateWithLifecycle()
    val liveWaveform by voiceRecorder.liveWaveform.collectAsStateWithLifecycle()
    val playingMessageId by voicePlayer.playingMessageId.collectAsStateWithLifecycle()
    val playbackProgress by voicePlayer.progressFraction.collectAsStateWithLifecycle()

    var draftText by remember { mutableStateOf("") }
    var isFinalizingVoice by remember { mutableStateOf(false) }
    var reactionTargetMessageId by remember { mutableStateOf<String?>(null) }
    var zoomedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val listState = rememberLazyListState()
    val isConnected = peerUi.connectionState == PeerConnectionState.CONNECTED

    val stopAndSendRecordedVoice: () -> Unit = {
        if (!isFinalizingVoice) {
            scope.launch {
                isFinalizingVoice = true
                val clipResult = voiceRecorder.stopRecordingAndEncode()
                isFinalizingVoice = false
                clipResult.onSuccess { clip ->
                    onSendVoiceNote(clip.audioBase64, clip.durationMs, clip.waveform)
                }.onFailure { err ->
                    Toast.makeText(
                        context,
                        err.localizedMessage ?: "Voice note not saved",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val startVoiceCapture: () -> Unit = {
        voicePlayer.stop()
        val res = voiceRecorder.startRecording {
            stopAndSendRecordedVoice()
        }
        res.onFailure { err ->
            Toast.makeText(
                context,
                err.localizedMessage ?: "Unable to start microphone recording",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceCapture()
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required to record voice notes",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSendImageUri(uri)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.release()
            voicePlayer.release()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    BackHandler {
        when {
            zoomedImageBitmap != null -> zoomedImageBitmap = null
            reactionTargetMessageId != null -> reactionTargetMessageId = null
            isRecording -> voiceRecorder.cancelRecording()
            else -> onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("connect_chat_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = peerUi.peer.deviceName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "P2P Live Chat • Local Wi-Fi Socket",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (retentionPolicy != P2pMessageRetentionPolicy.OFF) {
                                Text(
                                    text = "• Auto-delete ${retentionPolicy.shortBadge}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("connect_chat_retention_badge")
                                )
                            }
                        }
                    }
                    ArworaPillBadge(
                        text = if (isConnected) "Online" else "Offline",
                        tint = if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        showDot = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("connect_chat_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Chat Room")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f))

                // Message Feed
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forum,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Local P2P Chat Ready",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Send text messages, photos, voice notes, and emoji reactions directly over Wi-Fi with zero internet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("connect_chat_message_list")
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                val isPlayingThis = playingMessageId == msg.id
                                ChatMessageBubbleRow(
                                    message = msg,
                                    localDeviceId = localDeviceId,
                                    isPlayingVoice = isPlayingThis,
                                    voiceProgress = if (isPlayingThis) playbackProgress else 0f,
                                    showReactionPicker = reactionTargetMessageId == msg.id,
                                    onToggleReactionPicker = {
                                        reactionTargetMessageId = if (reactionTargetMessageId == msg.id) null else msg.id
                                    },
                                    onSelectReaction = { emoji ->
                                        reactionTargetMessageId = null
                                        onToggleReaction(msg.id, emoji)
                                    },
                                    onOpenImage = { bmp -> zoomedImageBitmap = bmp },
                                    onToggleVoicePlay = {
                                        val b64 = msg.audioBase64
                                        if (!b64.isNullOrBlank()) {
                                            voicePlayer.togglePlayback(
                                                messageId = msg.id,
                                                audioBase64 = b64,
                                                fallbackDurationMs = msg.durationMs
                                            ) { errMsg ->
                                                Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f))

                if (!isConnected) {
                    Text(
                        text = "Peer is offline. Reconnect to send new messages, photos, or voice notes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // Bottom Composer (Switches smoothly into Live Voice Recording Strip when active)
                if (isRecording) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connect_chat_recording_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    voiceRecorder.cancelRecording()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("connect_chat_cancel_voice_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Cancel Voice Note",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444))
                            )

                            Text(
                                text = ArworaVoiceRecorder.formatDurationMs(recordingElapsedMs),
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("connect_recording_timer")
                            )

                            VoiceWaveformCanvas(
                                amplitudes = ArworaVoiceRecorder.resampleWaveform(liveWaveform, 24),
                                progressFraction = 1f,
                                activeColor = Color(0xFFEF4444),
                                inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .testTag("connect_recording_waveform")
                            )

                            FilledIconButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    stopAndSendRecordedVoice()
                                },
                                enabled = !isFinalizingVoice,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("connect_chat_send_voice_btn")
                            ) {
                                if (isFinalizingVoice) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send Voice Note",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                photoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = isConnected && !isSendingImage,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("connect_chat_pick_image_btn")
                        ) {
                            if (isSendingImage) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Share Image",
                                    tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { draftText = it },
                            placeholder = { Text("Message ${peerUi.peer.deviceName}...") },
                            enabled = isConnected,
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("connect_chat_input")
                        )

                        if (draftText.isNotBlank()) {
                            FilledIconButton(
                                onClick = {
                                    val payload = draftText.trim()
                                    if (payload.isNotEmpty()) {
                                        SoundEffectManager.playClick()
                                        draftText = ""
                                        onSendText(payload)
                                    }
                                },
                                enabled = isConnected,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("connect_chat_send_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Message",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            FilledIconButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    if (ArworaVoiceRecorder.hasRecordAudioPermission(context)) {
                                        startVoiceCapture()
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                enabled = isConnected && !isFinalizingVoice,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("connect_chat_mic_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Record Voice Note",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    zoomedImageBitmap?.let { bmp ->
        Dialog(
            onDismissRequest = { zoomedImageBitmap = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { zoomedImageBitmap = null },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Shared Photo Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
                IconButton(
                    onClick = { zoomedImageBitmap = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Image", tint = Color.White)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageBubbleRow(
    message: ConnectChatMessage,
    localDeviceId: String,
    isPlayingVoice: Boolean,
    voiceProgress: Float,
    showReactionPicker: Boolean,
    onToggleReactionPicker: () -> Unit,
    onSelectReaction: (String) -> Unit,
    onOpenImage: (Bitmap) -> Unit,
    onToggleVoicePlay: () -> Unit
) {
    val isOutgoing = message.isOutgoing
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }
    val bubbleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    } else {
        ArworaDesignTokens.cardContainerColor()
    }
    val bubbleBorder = if (isOutgoing) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
    } else {
        ArworaDesignTokens.cardBorder()
    }
    val timeLabel = remember(message.timestampMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestampMillis))
    }

    val decodedBitmap by produceState<Bitmap?>(initialValue = null, key1 = message.imageBase64) {
        value = if (message.type == ChatMessageType.IMAGE && !message.imageBase64.isNullOrBlank()) {
            withContext(Dispatchers.Default) {
                ArworaConnectChatManager.decodeBase64ToBitmap(message.imageBase64)
            }
        } else {
            null
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        if (showReactionPicker) {
            Surface(
                shape = ArworaDesignTokens.PillShape,
                color = MaterialTheme.colorScheme.surface,
                border = ArworaDesignTokens.cardBorder(),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .testTag("connect_reaction_picker_${message.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConnectChatMessage.SUPPORTED_REACTIONS.forEach { emoji ->
                        Surface(
                            onClick = {
                                SoundEffectManager.playClick()
                                onSelectReaction(emoji)
                            },
                            shape = CircleShape,
                            color = if (message.reactions[emoji]?.contains(localDeviceId) == true) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            } else {
                                Color.Transparent
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("connect_reaction_option_${message.id}_$emoji")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = emoji, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isOutgoing) {
                IconButton(
                    onClick = onToggleReactionPicker,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("connect_react_trigger_${message.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddReaction,
                        contentDescription = "React to Message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                border = bubbleBorder,
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .combinedClickable(
                        onClick = { onToggleReactionPicker() },
                        onLongClick = { onToggleReactionPicker() }
                    )
                    .testTag("connect_chat_bubble_${message.id}")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (message.type == ChatMessageType.IMAGE) {
                        val bmp = decodedBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Shared Photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onOpenImage(bmp) }
                                    .testTag("connect_chat_image_${message.id}")
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Image Attachment",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (message.type == ChatMessageType.VOICE) {
                        val waveBars = remember(message.waveform) {
                            ArworaVoiceRecorder.resampleWaveform(message.waveform, ArworaVoiceRecorder.WAVEFORM_BAR_COUNT)
                        }
                        val playedMs = (message.durationMs * voiceProgress).toLong()
                        val durationText = if (isPlayingVoice) {
                            "${ArworaVoiceRecorder.formatDurationMs(playedMs)} / ${ArworaVoiceRecorder.formatDurationMs(message.durationMs)}"
                        } else {
                            ArworaVoiceRecorder.formatDurationMs(message.durationMs)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.widthIn(min = 210.dp)
                        ) {
                            FilledIconButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    onToggleVoicePlay()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("connect_voice_play_btn_${message.id}")
                            ) {
                                Icon(
                                    imageVector = if (isPlayingVoice) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlayingVoice) "Pause Voice Note" else "Play Voice Note",
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                VoiceWaveformCanvas(
                                    amplitudes = waveBars,
                                    progressFraction = if (isPlayingVoice) voiceProgress else 0f,
                                    activeColor = MaterialTheme.colorScheme.primary,
                                    inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(28.dp)
                                        .testTag("connect_voice_waveform_${message.id}")
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Voice Note",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Text(
                                        text = durationText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.testTag("connect_voice_duration_${message.id}")
                                    )
                                }
                            }
                        }
                    }

                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            if (!isOutgoing) {
                IconButton(
                    onClick = onToggleReactionPicker,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("connect_react_trigger_${message.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddReaction,
                        contentDescription = "React to Message",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        val activeReactions = message.reactionCounts
        if (activeReactions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                activeReactions.forEach { (emoji, count) ->
                    val reactedByMe = message.reactions[emoji]?.contains(localDeviceId) == true
                    Surface(
                        onClick = {
                            SoundEffectManager.playClick()
                            onSelectReaction(emoji)
                        },
                        shape = ArworaDesignTokens.PillShape,
                        color = if (reactedByMe) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (reactedByMe) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            }
                        ),
                        modifier = Modifier.testTag("connect_reaction_badge_${message.id}_$emoji")
                    ) {
                        Text(
                            text = if (count > 1) "$emoji $count" else emoji,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceWaveformCanvas(
    amplitudes: List<Float>,
    progressFraction: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    val bars = remember(amplitudes) {
        if (amplitudes.isEmpty()) ArworaVoiceRecorder.resampleWaveform(emptyList(), 28) else amplitudes
    }
    Canvas(modifier = modifier) {
        val count = bars.size.coerceAtLeast(1)
        val slotWidth = size.width / count.toFloat()
        val barWidth = (slotWidth * 0.56f).coerceIn(2.dp.toPx(), 5.dp.toPx())
        val radius = barWidth / 2f
        val maxHeight = size.height
        val minHeight = maxHeight * 0.16f

        for (i in 0 until count) {
            val amp = bars[i].coerceIn(0.12f, 1f)
            val barH = (maxHeight * amp).coerceAtLeast(minHeight)
            val left = i * slotWidth + (slotWidth - barWidth) / 2f
            val top = (maxHeight - barH) / 2f
            val barRatio = (i + 1).toFloat() / count.toFloat()
            val color = if (progressFraction > 0f && barRatio <= progressFraction) activeColor else inactiveColor
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
    }
}
