package com.arwora.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.arwora.feature.fakechat.AdvancedMessageComposerCard
import com.arwora.feature.fakechat.CallStatus
import com.arwora.feature.fakechat.ChatMessageType
import com.arwora.feature.fakechat.ChatPlatform
import com.arwora.feature.fakechat.FakeChatConfig
import com.arwora.feature.fakechat.FakeChatMessage
import com.arwora.feature.fakechat.GenericSocialChatPreview
import com.arwora.feature.fakechat.InstagramChatPreview
import com.arwora.feature.fakechat.InstagramOutgoingPalette
import com.arwora.feature.fakechat.PinterestChatPreview
import com.arwora.feature.fakechat.PinterestOutgoingStyle
import com.arwora.feature.fakechat.PlatformAppearanceOptionsCard
import com.arwora.feature.fakechat.PlatformSelectorGridCard
import com.arwora.feature.fakechat.WhatsAppChatPreview
import com.arwora.feature.fakechat.WhatsAppReadStatus
import com.arwora.feature.fakechat.defaultMessagesForPlatform
import com.arwora.feature.fakechat.formatVoiceDuration
import com.arwora.ui.components.AppCard
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPrimaryPillButton
import com.arwora.utils.SoundEffectManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeChatGeneratorScreen(
    navController: NavController,
    initialPlatform: String = "WhatsApp"
) {
    val context = LocalContext.current
    val initialChatPlatform = remember(initialPlatform) { ChatPlatform.fromLabel(initialPlatform) }

    var selectedPlatform by remember { mutableStateOf(initialChatPlatform) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Edit Chat, 1 = Live Preview

    // Contact & Device Chrome State
    var senderName by remember { mutableStateOf("Sarah Jenkins") }
    var statusText by remember { mutableStateOf(initialChatPlatform.defaultStatus) }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var batteryLevel by remember { mutableStateOf("88%") }
    var clockTime by remember { mutableStateOf("10:42 AM") }

    // Platform Theme Options State
    var isDarkPreview by remember { mutableStateOf(false) }
    var showWhatsAppDoodle by remember { mutableStateOf(true) }
    var defaultWhatsAppReadStatus by remember { mutableStateOf(WhatsAppReadStatus.READ) }
    var isVerifiedBadge by remember { mutableStateOf(true) }
    var isBusinessChat by remember { mutableStateOf(true) }
    var igOutgoingPalette by remember { mutableStateOf(InstagramOutgoingPalette.GRADIENT) }
    var showIgSeenIndicator by remember { mutableStateOf(true) }
    var showPinterestProfileCard by remember { mutableStateOf(true) }
    var isPinterestFollowing by remember { mutableStateOf(false) }
    var pinterestOutgoingStyle by remember { mutableStateOf(PinterestOutgoingStyle.NEUTRAL_GRAY) }

    // Messages List
    var messages by remember {
        mutableStateOf(defaultMessagesForPlatform(initialChatPlatform))
    }

    // Advanced Message Composer State
    var composerMessageType by remember { mutableStateOf(ChatMessageType.TEXT) }
    var newMessageText by remember { mutableStateOf("") }
    var isNewOutgoing by remember { mutableStateOf(true) }
    var newMessageTime by remember { mutableStateOf("10:42 AM") }
    var voiceDurationSeconds by remember { mutableIntStateOf(18) }
    var callStatus by remember { mutableStateOf(CallStatus.COMPLETED) }
    var callDurationText by remember { mutableStateOf("04:12") }
    var includeQuotedReply by remember { mutableStateOf(false) }
    var replyToAuthor by remember { mutableStateOf("Sarah Jenkins") }
    var replyToSnippet by remember { mutableStateOf("Loved your new studio reel!") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var mediaTitle by remember { mutableStateOf("Shared Reel Clip") }
    var isVideoMedia by remember { mutableStateOf(true) }
    var includeHeartReaction by remember { mutableStateOf(false) }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            avatarUri = uri
        }
    }

    val chatConfig = FakeChatConfig(
        platform = selectedPlatform,
        senderName = senderName,
        statusText = statusText,
        avatarUri = avatarUri,
        isDarkPreview = isDarkPreview,
        clockTime = clockTime,
        batteryLevel = batteryLevel,
        isVerifiedBadge = isVerifiedBadge,
        isBusinessChat = isBusinessChat,
        igOutgoingPalette = igOutgoingPalette,
        showIgSeenIndicator = showIgSeenIndicator,
        showWhatsAppDoodle = showWhatsAppDoodle,
        showPinterestProfileCard = showPinterestProfileCard,
        isPinterestFollowing = isPinterestFollowing,
        pinterestOutgoingStyle = pinterestOutgoingStyle
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${selectedPlatform.displayName} Chat Studio",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            selectedTab = if (selectedTab == 0) 1 else 0
                        },
                        modifier = Modifier.testTag("fake_chat_toggle_preview_action")
                    ) {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Default.Visibility else Icons.Default.Edit,
                            contentDescription = "Toggle Preview"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        SoundEffectManager.playClick()
                        selectedTab = 0
                    },
                    text = { Text("Edit Chat", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("fake_chat_tab_edit")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        SoundEffectManager.playClick()
                        selectedTab = 1
                    },
                    text = { Text("Live Preview", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("fake_chat_tab_preview")
                )
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("fake_chat_edit_list")
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Sleek Platform Selector Grid
                    item {
                        PlatformSelectorGridCard(
                            selectedPlatform = selectedPlatform,
                            onSelectPlatform = { nextPlatform ->
                                val prevDefault = selectedPlatform.defaultStatus
                                selectedPlatform = nextPlatform
                                if (statusText.isBlank() || statusText == prevDefault) {
                                    statusText = nextPlatform.defaultStatus
                                }
                            }
                        )
                    }

                    // 2. Contact Profile & Status Bar Metadata
                    item {
                        AppCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Contact & Header Profile",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(62.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .clickable {
                                                avatarPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (avatarUri != null) {
                                            AsyncImage(
                                                model = avatarUri,
                                                contentDescription = "Avatar",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = "Select Avatar",
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = senderName,
                                            onValueChange = { senderName = it },
                                            label = { Text("Contact Name") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = statusText,
                                            onValueChange = { statusText = it },
                                            label = { Text("Status / Handle") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = clockTime,
                                        onValueChange = { clockTime = it },
                                        label = { Text("Phone Clock") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = batteryLevel,
                                        onValueChange = { batteryLevel = it },
                                        label = { Text("Battery %") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Platform-Specific Theme Options
                    item {
                        PlatformAppearanceOptionsCard(
                            platform = selectedPlatform,
                            isDarkPreview = isDarkPreview,
                            onDarkPreviewChange = { isDarkPreview = it },
                            showWhatsAppDoodle = showWhatsAppDoodle,
                            onShowWhatsAppDoodleChange = { showWhatsAppDoodle = it },
                            defaultWhatsAppReadStatus = defaultWhatsAppReadStatus,
                            onDefaultWhatsAppReadStatusChange = { defaultWhatsAppReadStatus = it },
                            isVerifiedBadge = isVerifiedBadge,
                            onVerifiedBadgeChange = { isVerifiedBadge = it },
                            isBusinessChat = isBusinessChat,
                            onBusinessChatChange = { isBusinessChat = it },
                            igOutgoingPalette = igOutgoingPalette,
                            onIgOutgoingPaletteChange = { igOutgoingPalette = it },
                            showIgSeenIndicator = showIgSeenIndicator,
                            onShowIgSeenIndicatorChange = { showIgSeenIndicator = it },
                            showPinterestProfileCard = showPinterestProfileCard,
                            onShowPinterestProfileCardChange = { showPinterestProfileCard = it },
                            pinterestOutgoingStyle = pinterestOutgoingStyle,
                            onPinterestOutgoingStyleChange = { pinterestOutgoingStyle = it }
                        )
                    }

                    // 4. Advanced Message Type Builder
                    item {
                        AdvancedMessageComposerCard(
                            messageType = composerMessageType,
                            onMessageTypeChange = { composerMessageType = it },
                            newMessageText = newMessageText,
                            onNewMessageTextChange = { newMessageText = it },
                            isNewOutgoing = isNewOutgoing,
                            onIsNewOutgoingChange = { isNewOutgoing = it },
                            newMessageTime = newMessageTime,
                            onNewMessageTimeChange = { newMessageTime = it },
                            voiceDurationSeconds = voiceDurationSeconds,
                            onVoiceDurationSecondsChange = { voiceDurationSeconds = it },
                            callStatus = callStatus,
                            onCallStatusChange = { callStatus = it },
                            callDurationText = callDurationText,
                            onCallDurationTextChange = { callDurationText = it },
                            includeQuotedReply = includeQuotedReply,
                            onIncludeQuotedReplyChange = { includeQuotedReply = it },
                            replyToAuthor = replyToAuthor,
                            onReplyToAuthorChange = { replyToAuthor = it },
                            replyToSnippet = replyToSnippet,
                            onReplyToSnippetChange = { replyToSnippet = it },
                            mediaUri = mediaUri,
                            onMediaUriChange = { mediaUri = it },
                            mediaTitle = mediaTitle,
                            onMediaTitleChange = { mediaTitle = it },
                            isVideoMedia = isVideoMedia,
                            onIsVideoMediaChange = { isVideoMedia = it },
                            includeHeartReaction = includeHeartReaction,
                            onIncludeHeartReactionChange = { includeHeartReaction = it },
                            onAddMessage = {
                                if (composerMessageType == ChatMessageType.TEXT && newMessageText.isBlank()) {
                                    Toast.makeText(context, "Enter message text first", Toast.LENGTH_SHORT).show()
                                    return@AdvancedMessageComposerCard
                                }
                                val msg = FakeChatMessage(
                                    text = when (composerMessageType) {
                                        ChatMessageType.DELETED -> newMessageText.ifBlank { "You deleted a message" }
                                        else -> newMessageText.trim()
                                    },
                                    time = newMessageTime.ifBlank { "10:42 AM" },
                                    isOutgoing = isNewOutgoing,
                                    isRead = defaultWhatsAppReadStatus == WhatsAppReadStatus.READ,
                                    readStatus = defaultWhatsAppReadStatus,
                                    messageType = composerMessageType,
                                    voiceDurationSeconds = voiceDurationSeconds,
                                    callStatus = callStatus,
                                    callDurationText = callDurationText.ifBlank { "04:12" },
                                    replyToAuthor = if (composerMessageType == ChatMessageType.TEXT && includeQuotedReply) replyToAuthor.trim() else "",
                                    replyToSnippet = if (composerMessageType == ChatMessageType.TEXT && includeQuotedReply) replyToSnippet.trim() else "",
                                    mediaUri = mediaUri,
                                    mediaTitle = mediaTitle.trim(),
                                    isVideoMedia = isVideoMedia,
                                    reactionEmoji = if (includeHeartReaction && composerMessageType != ChatMessageType.DELETED) "❤️" else ""
                                )
                                messages = messages + msg
                                newMessageText = ""
                                SoundEffectManager.playClick()
                            }
                        )
                    }

                    // 5. Current Message List Header + Sample Reset
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current Messages (${messages.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    messages = defaultMessagesForPlatform(selectedPlatform)
                                },
                                shape = ArworaDesignTokens.PillShape,
                                modifier = Modifier.testTag("fake_chat_load_preset_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Load ${selectedPlatform.displayName} Preset", fontSize = 12.sp)
                            }
                        }
                    }

                    if (messages.isEmpty()) {
                        item {
                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "No messages yet. Add a message above or load a platform preset.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(messages, key = { it.id }) { msg ->
                            val directionLabel = if (msg.isOutgoing) "Outgoing" else "Incoming"
                            val summaryText = when (msg.messageType) {
                                ChatMessageType.TEXT -> msg.text.ifBlank { "Text message" }
                                ChatMessageType.VOICE_NOTE -> "Voice Note (${formatVoiceDuration(msg.voiceDurationSeconds)})"
                                ChatMessageType.VOICE_CALL -> "${msg.callStatus.label} Call (${msg.callDurationText})"
                                ChatMessageType.DELETED -> "Deleted: ${msg.text.ifBlank { "You deleted a message" }}"
                                ChatMessageType.MEDIA_ATTACHMENT -> "Media: ${msg.mediaTitle.ifBlank { "Attachment" }}"
                            }

                            AppCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$directionLabel · ${msg.messageType.label}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = selectedPlatform.accentColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = summaryText,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = msg.time,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(onClick = {
                                        messages = messages.filter { it.id != msg.id }
                                        SoundEffectManager.playClick()
                                    }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete message",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        ArworaPrimaryPillButton(
                            text = "Generate & Preview Chat",
                            onClick = {
                                SoundEffectManager.playClick()
                                selectedTab = 1
                            },
                            modifier = Modifier.testTag("generate_preview_chat_button")
                        )
                    }
                }
            } else {
                // LIVE PREVIEW TAB
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quick Light / Dark & Edit Switch Bar above preview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = isDarkPreview,
                            onClick = {
                                SoundEffectManager.playClick()
                                isDarkPreview = !isDarkPreview
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isDarkPreview) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(if (isDarkPreview) "Dark Mode" else "Light Mode", fontSize = 12.sp)
                            },
                            modifier = Modifier.testTag("preview_theme_mode_chip")
                        )

                        FilterChip(
                            selected = false,
                            onClick = {
                                SoundEffectManager.playClick()
                                selectedTab = 0
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            label = { Text("Edit Messages", fontSize = 12.sp) }
                        )
                    }

                    // Simulated Phone Device Chrome + Authentic Chat Engine
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        border = ArworaDesignTokens.cardBorder(),
                        color = if (isDarkPreview) Color(0xFF0A0A0A) else Color(0xFFF6F7F9)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Phone Status Bar Strip
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = clockTime.ifBlank { "10:42 AM" },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDarkPreview) Color.White else Color.Black
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SignalCellularAlt,
                                        contentDescription = null,
                                        tint = if (isDarkPreview) Color.White else Color.Black,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Wifi,
                                        contentDescription = null,
                                        tint = if (isDarkPreview) Color.White else Color.Black,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = batteryLevel.ifBlank { "88%" },
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDarkPreview) Color.White else Color.Black
                                    )
                                    Icon(
                                        imageVector = Icons.Default.BatteryFull,
                                        contentDescription = null,
                                        tint = if (isDarkPreview) Color.White else Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            RenderFakeChatPreview(
                                config = chatConfig,
                                messages = messages,
                                onTogglePinterestFollow = {
                                    SoundEffectManager.playClick()
                                    isPinterestFollowing = !isPinterestFollowing
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderFakeChatPreview(
    config: FakeChatConfig,
    messages: List<FakeChatMessage>,
    onTogglePinterestFollow: () -> Unit
) {
    when (config.platform) {
        ChatPlatform.WHATSAPP -> WhatsAppChatPreview(
            config = config,
            messages = messages
        )
        ChatPlatform.INSTAGRAM -> InstagramChatPreview(
            config = config,
            messages = messages
        )
        ChatPlatform.PINTEREST -> PinterestChatPreview(
            config = config,
            messages = messages,
            onToggleFollow = onTogglePinterestFollow
        )
        else -> GenericSocialChatPreview(
            config = config,
            messages = messages
        )
    }
}
