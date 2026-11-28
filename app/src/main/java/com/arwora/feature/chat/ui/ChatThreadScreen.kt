package com.arwora.feature.chat.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.arwora.feature.chat.model.ChatMessage
import com.arwora.feature.chat.model.ChatMessageType
import com.arwora.feature.chat.model.ChatStickerCatalog
import com.arwora.feature.chat.model.ChatThemePreset
import com.arwora.feature.chat.model.ChatThread
import com.arwora.feature.chat.model.ChatWallpaperPreset
import com.arwora.feature.chat.model.MessageDeliveryStatus
import com.arwora.feature.system.connect.data.ArworaVoiceRecorder
import com.arwora.feature.system.connect.data.ConnectNetworkUtils
import com.arwora.feature.system.connect.ui.VoiceWaveformCanvas
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    threadId: String,
    onBack: () -> Unit,
    vm: ChatHubViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val thread by vm.activeThread.collectAsStateWithLifecycle()
    val messages by vm.activeMessages.collectAsStateWithLifecycle()
    val allThreads by vm.allThreads.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val replyTo by vm.replyToMessage.collectAsStateWithLifecycle()
    val editingMsg by vm.editingMessage.collectAsStateWithLifecycle()
    val feedback by vm.feedback.collectAsStateWithLifecycle()
    val inThreadQuery by vm.inThreadSearchQuery.collectAsStateWithLifecycle()
    val inThreadDate by vm.inThreadDateFilterMillis.collectAsStateWithLifecycle()
    val inThreadResults by vm.inThreadSearchResults.collectAsStateWithLifecycle()

    val isRecording by vm.voiceRecorder.isRecording.collectAsStateWithLifecycle()
    val recElapsedMs by vm.voiceRecorder.elapsedMs.collectAsStateWithLifecycle()
    val liveWaveform by vm.voiceRecorder.liveWaveform.collectAsStateWithLifecycle()
    val playingMsgId by vm.voicePlayer.playingMessageId.collectAsStateWithLifecycle()
    val playProgress by vm.voicePlayer.progressFraction.collectAsStateWithLifecycle()

    var composerText by remember { mutableStateOf("") }
    var showStickerDrawer by remember { mutableStateOf(false) }
    var showAttachStrip by remember { mutableStateOf(false) }
    var viewOnceArmed by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showCustomizeDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var actionTargetMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var forwardTargetMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var infoTargetMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var activeViewOnceMessage by remember { mutableStateOf<ChatMessage?>(null) }

    LaunchedEffect(threadId) {
        vm.openThread(threadId)
    }

    LaunchedEffect(editingMsg?.id) {
        editingMsg?.let { composerText = it.text }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(feedback) {
        val msg = feedback
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            vm.clearFeedback()
        }
    }

    BackHandler {
        if (showStickerDrawer || showAttachStrip) {
            showStickerDrawer = false
            showAttachStrip = false
        } else {
            vm.closeThread()
            onBack()
        }
    }

    // FLAG_SECURE on locked / hidden vault threads
    val shouldForceFlagSecure = ((thread?.isLocked == true) || (thread?.isHidden == true)) &&
        settings.flagSecureLockedChats
    DisposableEffect(activity, shouldForceFlagSecure) {
        val window = activity?.window
        val hadFlagAlready = window != null &&
            (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        val addedByChat = window != null && shouldForceFlagSecure && !hadFlagAlready
        if (addedByChat) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (addedByChat) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    // Android 14+ ScreenCaptureCallback screenshot alert
    DisposableEffect(activity, threadId, settings.screenshotAlertsEnabled) {
        if (activity != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            settings.screenshotAlertsEnabled
        ) {
            val callback = Activity.ScreenCaptureCallback {
                vm.onScreenshotCaptured(threadId)
            }
            runCatching {
                activity.registerScreenCaptureCallback(
                    ContextCompat.getMainExecutor(activity),
                    callback
                )
            }
            onDispose {
                runCatching { activity.unregisterScreenCaptureCallback(callback) }
            }
        } else {
            onDispose { }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            vm.sendPhoto(threadId, uri, isViewOnce = viewOnceArmed)
            viewOnceArmed = false
            showAttachStrip = false
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            vm.sendVideo(threadId, uri, isViewOnce = viewOnceArmed)
            viewOnceArmed = false
            showAttachStrip = false
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.sendFile(threadId, uri)
            showAttachStrip = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            vm.sendCurrentLocation(threadId)
            showAttachStrip = false
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            vm.startVoiceRecording()
        } else {
            Toast.makeText(context, "Microphone permission is needed for voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    val themePreset = remember(thread?.themeId, settings.defaultThemeId) {
        ChatThemePreset.byId(thread?.themeId ?: settings.defaultThemeId)
    }
    val wallpaperKey = thread?.wallpaperPresetOrUri ?: settings.defaultWallpaperId
    val isCustomWallpaper = wallpaperKey.startsWith("content://") ||
        wallpaperKey.startsWith("file://") ||
        wallpaperKey.startsWith("/")
    val wallpaperPreset = remember(wallpaperKey) {
        ChatWallpaperPreset.byId(wallpaperKey)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            vm.closeThread()
                            onBack()
                        },
                        modifier = Modifier.testTag("chat_thread_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = thread?.title ?: "Chat",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (thread?.isLocked == true) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = themePreset.accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        val statusText = when {
                            thread?.isTyping == true -> "typing..."
                            settings.ghostModeEnabled -> "Ghost Mode • E2EE"
                            thread?.isOnline == true -> "Online • E2EE"
                            thread?.isGroup == true -> "${thread?.participantNames?.size ?: 0} members • E2EE"
                            else -> "Offline • E2EE"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (thread?.isTyping == true) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            showSearchDialog = true
                        },
                        modifier = Modifier.testTag("chat_thread_search_btn")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search & Media")
                    }
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            showCustomizeDialog = true
                        },
                        modifier = Modifier.testTag("chat_thread_options_btn")
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Chat Options & Themes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding()
        ) {
            if (isCustomWallpaper) {
                val model: Any = if (wallpaperKey.startsWith("/")) File(wallpaperKey) else Uri.parse(wallpaperKey)
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(wallpaperPreset.startColor, wallpaperPreset.endColor)
                            )
                        )
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                if (vm.isLowBatteryOrDataSaverActive()) {
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Data/Battery Saver active: photos auto-compressed",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFBBF24),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                    }
                }

                // Messages List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("chat_thread_messages_list"),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = ArworaDesignTokens.CardShape,
                                    color = Color.Black.copy(alpha = 0.35f),
                                    border = BorderStroke(1.dp, themePreset.accentColor.copy(alpha = 0.35f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Security,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Text(
                                            text = "SQLCipher AES-256 Protected",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Send text, voice notes, photos, stickers, files, or live GPS location.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.78f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(messages, key = { it.id }) { msg ->
                        ChatMessageBubbleRow(
                            message = msg,
                            themePreset = themePreset,
                            isPlayingVoice = playingMsgId == msg.id,
                            voiceProgress = if (playingMsgId == msg.id) playProgress else 0f,
                            disableHeavyAutoLoad = settings.disableHeavyAutoDownload,
                            onLongPress = { actionTargetMessage = msg },
                            onToggleVoice = { vm.toggleVoicePlayback(msg) },
                            onToggleReaction = { emoji -> vm.toggleReaction(threadId, msg.id, emoji) },
                            onOpenViewOnce = { activeViewOnceMessage = msg },
                            onDispatchScheduledNow = { vm.dispatchScheduledNow(msg.id) },
                            onCancelScheduled = { vm.deleteForEveryone(threadId, msg.id) }
                        )
                    }
                }

                // Composer Footer
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 4.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Reply / Edit Strip
                        val activeContextBanner = editingMsg ?: replyTo
                        if (activeContextBanner != null) {
                            Surface(
                                shape = ArworaDesignTokens.InnerTileShape,
                                color = themePreset.accentColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, themePreset.accentColor.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chat_composer_context_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (editingMsg != null) {
                                                "Editing message"
                                            } else {
                                                "Replying to ${activeContextBanner.senderName}"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = themePreset.accentColor
                                        )
                                        Text(
                                            text = activeContextBanner.displaySummary(),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            vm.cancelComposerMode()
                                            if (editingMsg != null) composerText = ""
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                                    }
                                }
                            }
                        }

                        // Built-in Sticker Drawer
                        AnimatedVisibility(visible = showStickerDrawer) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ChatStickerCatalog.stickers.forEach { stk ->
                                    Surface(
                                        onClick = {
                                            SoundEffectManager.playClick()
                                            vm.sendSticker(threadId, stk.id)
                                            showStickerDrawer = false
                                        },
                                        shape = ArworaDesignTokens.InnerTileShape,
                                        color = Color(stk.badgeColorHex).copy(alpha = 0.16f),
                                        border = BorderStroke(1.dp, Color(stk.badgeColorHex).copy(alpha = 0.4f)),
                                        modifier = Modifier.testTag("sticker_item_${stk.id}")
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(stk.emoji, fontSize = 24.sp)
                                            Text(stk.caption, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        // Attachment / Feature Strip
                        AnimatedVisibility(visible = showAttachStrip) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = viewOnceArmed,
                                        onClick = { viewOnceArmed = !viewOnceArmed },
                                        label = { Text("View Once") },
                                        leadingIcon = { Icon(Icons.Default.Visibility, null, Modifier.size(16.dp)) },
                                        modifier = Modifier.testTag("chat_attach_view_once_toggle")
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            photoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = ArworaDesignTokens.InnerTileShape,
                                        modifier = Modifier.testTag("chat_attach_photo_btn")
                                    ) {
                                        Icon(Icons.Default.Image, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Photo")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            videoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                            )
                                        },
                                        shape = ArworaDesignTokens.InnerTileShape,
                                        modifier = Modifier.testTag("chat_attach_video_btn")
                                    ) {
                                        Icon(Icons.Default.Videocam, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Video")
                                    }
                                    OutlinedButton(
                                        onClick = { filePicker.launch(arrayOf("*/*")) },
                                        shape = ArworaDesignTokens.InnerTileShape,
                                        modifier = Modifier.testTag("chat_attach_file_btn")
                                    ) {
                                        Icon(Icons.Default.AttachFile, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("File")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val hasFine = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.ACCESS_FINE_LOCATION
                                            ) == PackageManager.PERMISSION_GRANTED
                                            val hasCoarse = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (hasFine || hasCoarse) {
                                                vm.sendCurrentLocation(threadId)
                                                showAttachStrip = false
                                            } else {
                                                locationPermissionLauncher.launch(
                                                    arrayOf(
                                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                                    )
                                                )
                                            }
                                        },
                                        shape = ArworaDesignTokens.InnerTileShape,
                                        modifier = Modifier.testTag("chat_attach_location_btn")
                                    ) {
                                        Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("GPS")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            showAttachStrip = false
                                            showScheduleDialog = true
                                        },
                                        shape = ArworaDesignTokens.InnerTileShape,
                                        modifier = Modifier.testTag("chat_attach_schedule_btn")
                                    ) {
                                        Icon(Icons.Default.Schedule, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Schedule")
                                    }
                                }
                            }
                        }

                        // Voice Recording Bar or Text Composer
                        if (isRecording) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ArworaDesignTokens.InnerTileShape)
                                    .background(Color(0xFFEF4444).copy(alpha = 0.14f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { vm.cancelVoiceRecording() },
                                    modifier = Modifier.testTag("chat_voice_cancel_btn")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Cancel Voice", tint = Color(0xFFEF4444))
                                }
                                Text(
                                    text = ArworaVoiceRecorder.formatDurationMs(recElapsedMs),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                                VoiceWaveformCanvas(
                                    amplitudes = ArworaVoiceRecorder.resampleWaveform(liveWaveform, 24),
                                    progressFraction = 1f,
                                    activeColor = Color(0xFFEF4444),
                                    inactiveColor = Color(0xFFEF4444).copy(alpha = 0.35f),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(26.dp)
                                )
                                Button(
                                    onClick = { vm.stopAndSendVoice(threadId) },
                                    shape = ArworaDesignTokens.PillShape,
                                    modifier = Modifier.testTag("chat_voice_send_btn")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Voice Note", Modifier.size(18.dp))
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        showStickerDrawer = false
                                        showAttachStrip = !showAttachStrip
                                    },
                                    modifier = Modifier.testTag("chat_composer_attach_btn")
                                ) {
                                    Icon(
                                        imageVector = if (showAttachStrip) Icons.Default.Close else Icons.Default.Add,
                                        contentDescription = "Attachments"
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showAttachStrip = false
                                        showStickerDrawer = !showStickerDrawer
                                    },
                                    modifier = Modifier.testTag("chat_composer_sticker_btn")
                                ) {
                                    Icon(Icons.Default.EmojiEmotions, contentDescription = "Stickers")
                                }
                                OutlinedTextField(
                                    value = composerText,
                                    onValueChange = { next ->
                                        composerText = next
                                        vm.onComposerTextChanged(threadId, next)
                                    },
                                    placeholder = { Text("Message...") },
                                    maxLines = 4,
                                    shape = ArworaDesignTokens.PillShape,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("chat_composer_input")
                                )
                                if (composerText.isNotBlank() || editingMsg != null) {
                                    IconButton(
                                        onClick = {
                                            val payload = composerText.trim()
                                            if (payload.isNotEmpty()) {
                                                SoundEffectManager.playClick()
                                                vm.sendOrEditText(threadId, payload)
                                                composerText = ""
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(themePreset.outgoingBubbleColor)
                                            .testTag("chat_composer_send_btn")
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Send",
                                            tint = themePreset.outgoingTextColor
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                vm.startVoiceRecording()
                                            } else {
                                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(themePreset.outgoingBubbleColor.copy(alpha = 0.2f))
                                            .testTag("chat_composer_mic_btn")
                                    ) {
                                        Icon(
                                            Icons.Default.Mic,
                                            contentDescription = "Record Voice Note",
                                            tint = themePreset.accentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Long-press Messenger-style Action Dialog
    actionTargetMessage?.let { target ->
        MessageActionsDialog(
            message = target,
            onReact = { emoji ->
                vm.toggleReaction(threadId, target.id, emoji)
                actionTargetMessage = null
            },
            onReply = {
                vm.setReplyTarget(target)
                actionTargetMessage = null
            },
            onForward = {
                forwardTargetMessage = target
                actionTargetMessage = null
            },
            onEdit = {
                vm.startEditMessage(target)
                actionTargetMessage = null
            },
            onDeleteForEveryone = {
                vm.deleteForEveryone(threadId, target.id)
                actionTargetMessage = null
            },
            onMessageInfo = {
                infoTargetMessage = target
                actionTargetMessage = null
            },
            onDismiss = { actionTargetMessage = null }
        )
    }

    forwardTargetMessage?.let { target ->
        ForwardMessageDialog(
            threads = allThreads.filter { it.id != threadId && !it.isHidden },
            onSelectTarget = { dest ->
                vm.forwardMessage(target, dest.id)
                forwardTargetMessage = null
            },
            onDismiss = { forwardTargetMessage = null }
        )
    }

    infoTargetMessage?.let { target ->
        MessageInfoDialog(
            message = target,
            e2eeFingerprint = thread?.e2eeFingerprint ?: "",
            onDismiss = { infoTargetMessage = null }
        )
    }

    activeViewOnceMessage?.let { target ->
        ViewOnceMediaDialog(
            message = target,
            onCloseAndConsume = {
                vm.consumeViewOnce(target.id)
                activeViewOnceMessage = null
            }
        )
    }

    if (showScheduleDialog) {
        ScheduledMessageDialog(
            initialText = composerText,
            onSchedule = { text, triggerAt ->
                vm.sendOrEditText(threadId, text, scheduledForMillis = triggerAt)
                composerText = ""
                showScheduleDialog = false
            },
            onDismiss = { showScheduleDialog = false }
        )
    }

    if (showSearchDialog) {
        InChatSearchAndMediaDialog(
            messages = messages,
            searchQuery = inThreadQuery,
            selectedDateMillis = inThreadDate,
            searchResults = inThreadResults,
            onSearchChanged = { q, d -> vm.updateInThreadSearch(q, d) },
            onJumpToMessage = { msg ->
                showSearchDialog = false
                val idx = messages.indexOfFirst { it.id == msg.id }
                if (idx >= 0) {
                    coroutineScope.launch { listState.animateScrollToItem(idx) }
                }
            },
            onDismiss = { showSearchDialog = false }
        )
    }

    val currentThread = thread
    if (showCustomizeDialog && currentThread != null) {
        ThreadCustomizationDialog(
            thread = currentThread,
            onToggleMute = { vm.toggleThreadMute(threadId) },
            onToggleLock = { vm.toggleThreadLock(threadId) },
            onToggleHidden = { vm.toggleThreadHidden(threadId) },
            onToggleArchive = { vm.toggleThreadArchive(threadId) },
            onSetRingtone = { uri, name -> vm.setThreadCustomRingtone(threadId, uri, name) },
            onSelectTheme = { theme -> vm.setThreadTheme(threadId, theme) },
            onSelectWallpaper = { wp -> vm.setThreadWallpaper(threadId, wp) },
            onDismiss = { showCustomizeDialog = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatMessageBubbleRow(
    message: ChatMessage,
    themePreset: ChatThemePreset,
    isPlayingVoice: Boolean,
    voiceProgress: Float,
    disableHeavyAutoLoad: Boolean,
    onLongPress: () -> Unit,
    onToggleVoice: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onOpenViewOnce: () -> Unit,
    onDispatchScheduledNow: () -> Unit,
    onCancelScheduled: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    if (message.type == ChatMessageType.SYSTEM_ALERT) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = ArworaDesignTokens.PillShape,
                color = Color.Black.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFDE68A),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        return
    }

    val align = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isOutgoing) {
        themePreset.outgoingBubbleColor
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f)
    }
    val textColor = if (message.isOutgoing) {
        themePreset.outgoingTextColor
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val bubbleShape = if (message.isOutgoing) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.align(align),
            horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (message.isOutgoing) {
                        themePreset.accentColor.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    }
                ),
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(
                        onClick = {
                            if (message.isViewOnce && !message.viewOnceConsumed && !message.isDeletedForEveryone) {
                                onOpenViewOnce()
                            }
                        },
                        onLongClick = onLongPress
                    )
                    .testTag("chat_msg_bubble_${message.id}")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (!message.isOutgoing) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = themePreset.accentColor
                        )
                    }

                    if (!message.forwardedFrom.isNullOrBlank() && !message.isDeletedForEveryone) {
                        Text(
                            text = "Forwarded from ${message.forwardedFrom}",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.78f)
                        )
                    }

                    if (!message.replyPreviewText.isNullOrBlank() && !message.isDeletedForEveryone) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.22f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = message.replySenderName ?: "Reply",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = themePreset.accentColor
                                )
                                Text(
                                    text = message.replyPreviewText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textColor.copy(alpha = 0.85f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (message.isDeletedForEveryone) {
                        Text(
                            text = "This message was deleted",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    } else {
                        when (message.type) {
                            ChatMessageType.TEXT -> {
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor
                                )
                            }
                            ChatMessageType.STICKER -> {
                                val stk = ChatStickerCatalog.byId(message.stickerId)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stk?.emoji ?: "✨", fontSize = 42.sp)
                                    Text(
                                        text = stk?.caption ?: message.text,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textColor.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            ChatMessageType.VOICE -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = onToggleVoice,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.25f))
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingVoice) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlayingVoice) "Pause Voice" else "Play Voice",
                                            tint = textColor
                                        )
                                    }
                                    VoiceWaveformCanvas(
                                        amplitudes = ArworaVoiceRecorder.resampleWaveform(message.waveform, 24),
                                        progressFraction = if (isPlayingVoice) voiceProgress else 0f,
                                        activeColor = textColor,
                                        inactiveColor = textColor.copy(alpha = 0.35f),
                                        modifier = Modifier
                                            .width(130.dp)
                                            .height(26.dp)
                                    )
                                    Text(
                                        text = ArworaVoiceRecorder.formatDurationMs(message.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textColor
                                    )
                                }
                            }
                            ChatMessageType.IMAGE, ChatMessageType.VIDEO -> {
                                if (message.isViewOnce) {
                                    Surface(
                                        onClick = { if (!message.viewOnceConsumed) onOpenViewOnce() },
                                        shape = ArworaDesignTokens.InnerTileShape,
                                        color = Color.Black.copy(alpha = 0.28f),
                                        modifier = Modifier.testTag("chat_view_once_card_${message.id}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Visibility,
                                                contentDescription = null,
                                                tint = textColor
                                            )
                                            Text(
                                                text = if (message.viewOnceConsumed) {
                                                    "Opened • Media self-destructed"
                                                } else {
                                                    "Tap to view once ${if (message.type == ChatMessageType.VIDEO) "video" else "photo"}"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = textColor
                                            )
                                        }
                                    }
                                } else {
                                    var forceLoad by remember(message.id) { mutableStateOf(!disableHeavyAutoLoad) }
                                    val bitmap = remember(message.mediaBase64) {
                                        message.mediaBase64?.let { b64 ->
                                            runCatching {
                                                val bytes = Base64.decode(b64, Base64.DEFAULT)
                                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                            }.getOrNull()
                                        }
                                    }
                                    if (!forceLoad && bitmap == null) {
                                        OutlinedButton(onClick = { forceLoad = true }) {
                                            Text("Tap to load media (${ConnectNetworkUtils.formatBytes(message.fileSizeBytes)})")
                                        }
                                    } else if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = message.fileName ?: "Photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(170.dp)
                                                .clip(ArworaDesignTokens.InnerTileShape)
                                        )
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Default.Videocam, null, tint = textColor)
                                            Column {
                                                Text(message.fileName ?: "Video", fontWeight = FontWeight.SemiBold, color = textColor)
                                                Text(ConnectNetworkUtils.formatBytes(message.fileSizeBytes), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                                            }
                                        }
                                    }
                                    if (message.text.isNotBlank()) {
                                        Text(message.text, style = MaterialTheme.typography.bodySmall, color = textColor)
                                    }
                                }
                            }
                            ChatMessageType.FILE -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = textColor)
                                    Column {
                                        Text(
                                            text = message.fileName ?: "Attachment",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textColor
                                        )
                                        Text(
                                            text = ConnectNetworkUtils.formatBytes(message.fileSizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = textColor.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                            ChatMessageType.LOCATION -> {
                                Surface(
                                    onClick = {
                                        val lat = message.latitude ?: 0.0
                                        val lng = message.longitude ?: 0.0
                                        val label = Uri.encode(message.locationLabel ?: "Shared Location")
                                        runCatching {
                                            uriHandler.openUri("geo:$lat,$lng?q=$lat,$lng($label)")
                                        }.onFailure {
                                            Toast.makeText(context, "Coordinates: $lat, $lng", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = ArworaDesignTokens.InnerTileShape,
                                    color = Color.Black.copy(alpha = 0.22f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF34D399))
                                        Column {
                                            Text(
                                                text = message.locationLabel ?: "Live GPS Location",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = textColor
                                            )
                                            if (message.latitude != null && message.longitude != null) {
                                                Text(
                                                    text = String.format(Locale.US, "%.5f, %.5f", message.latitude, message.longitude),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = textColor.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            ChatMessageType.SYSTEM_ALERT -> Unit
                        }
                    }

                    if (message.deliveryStatus == MessageDeliveryStatus.SCHEDULED) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ArworaPillBadge(text = "Scheduled", tint = Color(0xFFF59E0B))
                            TextButton(
                                onClick = onDispatchScheduledNow,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Send Now", color = textColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = onCancelScheduled,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text("Cancel", color = textColor.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (message.isEdited && !message.isDeletedForEveryone) {
                            Text(
                                text = "Edited",
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.72f)
                            )
                        }
                        Text(
                            text = timeFmt.format(Date(message.timestampMillis)),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.78f)
                        )
                        if (message.isOutgoing) {
                            val icon = when (message.deliveryStatus) {
                                MessageDeliveryStatus.SCHEDULED,
                                MessageDeliveryStatus.SENDING -> Icons.Default.Schedule
                                MessageDeliveryStatus.SENT -> Icons.Default.Done
                                MessageDeliveryStatus.DELIVERED,
                                MessageDeliveryStatus.SEEN -> Icons.Default.DoneAll
                            }
                            val tint = if (message.deliveryStatus == MessageDeliveryStatus.SEEN) {
                                Color(0xFF34D399)
                            } else {
                                textColor.copy(alpha = 0.85f)
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = message.deliveryStatus.label,
                                tint = tint,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            if (message.reactionCounts.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.reactionCounts.forEach { (emoji, count) ->
                        Surface(
                            onClick = { onToggleReaction(emoji) },
                            shape = ArworaDesignTokens.PillShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = ArworaDesignTokens.innerTileBorder(),
                            modifier = Modifier.testTag("msg_reaction_pill_${message.id}_$emoji")
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
}

@Composable
private fun MessageActionsDialog(
    message: ChatMessage,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onEdit: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onMessageInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    var customEmoji by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message Actions", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ChatMessage.SUPPORTED_REACTIONS.forEach { emoji ->
                        Surface(
                            onClick = { onReact(emoji) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("action_react_$emoji")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(emoji, fontSize = 18.sp)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = customEmoji,
                        onValueChange = { customEmoji = it.take(4) },
                        label = { Text("Custom emoji") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("action_custom_emoji_input")
                    )
                    Button(
                        onClick = {
                            val clean = customEmoji.trim()
                            if (clean.isNotEmpty()) onReact(clean)
                        },
                        enabled = customEmoji.trim().isNotEmpty(),
                        modifier = Modifier.testTag("action_custom_emoji_btn")
                    ) { Text("React") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ActionRowItem(Icons.AutoMirrored.Filled.Reply, "Reply", onReply, "action_reply_btn")
                ActionRowItem(Icons.AutoMirrored.Filled.Forward, "Forward", onForward, "action_forward_btn")
                if (message.isOutgoing && !message.isDeletedForEveryone && message.type == ChatMessageType.TEXT) {
                    ActionRowItem(Icons.Default.Edit, "Edit", onEdit, "action_edit_btn")
                }
                if (!message.isDeletedForEveryone) {
                    ActionRowItem(Icons.Default.DeleteOutline, "Delete for Everyone", onDeleteForEveryone, "action_delete_btn")
                }
                ActionRowItem(Icons.Default.Info, "Message Info", onMessageInfo, "action_info_btn")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ActionRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ArworaDesignTokens.InnerTileShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
