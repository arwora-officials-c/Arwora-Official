package com.arwora.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.arwora.utils.MediaDownloadUtils
import com.arwora.utils.SoundEffectManager
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class FakeChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val time: String,
    val isOutgoing: Boolean,
    val isRead: Boolean = true
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FakeChatGeneratorScreen(
    navController: NavController,
    initialPlatform: String = "WhatsApp"
) {
    val context = LocalContext.current
    var selectedPlatform by remember { mutableStateOf(initialPlatform) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Edit Form, 1 = Live Preview

    // Chat Form State
    var senderName by remember { mutableStateOf("Sarah Jenkins") }
    var statusText by remember { mutableStateOf("Online") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var batteryLevel by remember { mutableStateOf("88%") }
    var clockTime by remember { mutableStateOf("10:42 AM") }

    // Messages State
    var messages by remember {
        mutableStateOf(
            listOf(
                FakeChatMessage(text = "Hey! Did you check the new project specs?", time = "10:38 AM", isOutgoing = false),
                FakeChatMessage(text = "Yes, just reviewed them. Looks great! 👍", time = "10:40 AM", isOutgoing = true, isRead = true),
                FakeChatMessage(text = "Awesome, let's deploy the updates today.", time = "10:41 AM", isOutgoing = false)
            )
        )
    }

    // New Message Input State
    var newMessageText by remember { mutableStateOf("") }
    var isNewOutgoing by remember { mutableStateOf(true) }
    var newMessageTime by remember { mutableStateOf("10:42 AM") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            avatarUri = uri
        }
    }

    val platforms = listOf("WhatsApp", "Messenger", "Instagram", "Imo", "TikTok", "Twitter/X", "Snapchat")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$selectedPlatform Chat Generator", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick()
                        selectedTab = if (selectedTab == 0) 1 else 0
                    }) {
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
            // Tab Selector
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        SoundEffectManager.playClick()
                        selectedTab = 0
                    },
                    text = { Text("Edit Chat", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        SoundEffectManager.playClick()
                        selectedTab = 1
                    },
                    text = { Text("Live Preview", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            if (selectedTab == 0) {
                // EDIT FORM
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Platform Selector
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Platform Style", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    platforms.take(4).forEach { plat ->
                                        FilterChip(
                                            selected = selectedPlatform == plat,
                                            onClick = {
                                                selectedPlatform = plat
                                                SoundEffectManager.playClick()
                                            },
                                            label = { Text(plat, fontSize = 12.sp) }
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    platforms.drop(4).forEach { plat ->
                                        FilterChip(
                                            selected = selectedPlatform == plat,
                                            onClick = {
                                                selectedPlatform = plat
                                                SoundEffectManager.playClick()
                                            },
                                            label = { Text(plat, fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sender Profile
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Contact Information", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .clickable {
                                                photoPickerLauncher.launch(
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
                                            Icon(Icons.Default.Person, contentDescription = "Add Avatar", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        OutlinedTextField(
                                            value = senderName,
                                            onValueChange = { senderName = it },
                                            label = { Text("Contact Name") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedTextField(
                                            value = statusText,
                                            onValueChange = { statusText = it },
                                            label = { Text("Status (Online / Last seen)") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Add Message Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Add New Message", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = newMessageText,
                                    onValueChange = { newMessageText = it },
                                    label = { Text("Message Text") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FilterChip(
                                            selected = !isNewOutgoing,
                                            onClick = { isNewOutgoing = false },
                                            label = { Text("Incoming") }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        FilterChip(
                                            selected = isNewOutgoing,
                                            onClick = { isNewOutgoing = true },
                                            label = { Text("Outgoing") }
                                        )
                                    }

                                    OutlinedTextField(
                                        value = newMessageTime,
                                        onValueChange = { newMessageTime = it },
                                        label = { Text("Time") },
                                        singleLine = true,
                                        modifier = Modifier.width(110.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        if (newMessageText.isNotBlank()) {
                                            messages = messages + FakeChatMessage(
                                                text = newMessageText.trim(),
                                                time = newMessageTime.ifBlank { "10:42 AM" },
                                                isOutgoing = isNewOutgoing
                                            )
                                            newMessageText = ""
                                            SoundEffectManager.playClick()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add Message", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Message List Item Cards
                    item {
                        Text("Current Messages (${messages.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }

                    items(messages) { msg ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.isOutgoing) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (msg.isOutgoing) "Outgoing: ${msg.text}" else "Incoming: ${msg.text}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
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
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    item {
                        Button(
                            onClick = { selectedTab = 1 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Generate & Preview Chat", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // LIVE PREVIEW SCREEN
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RenderFakeChatPreview(
                        platform = selectedPlatform,
                        senderName = senderName,
                        statusText = statusText,
                        avatarUri = avatarUri,
                        messages = messages
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderFakeChatPreview(
    platform: String,
    senderName: String,
    statusText: String,
    avatarUri: Uri?,
    messages: List<FakeChatMessage>
) {
    val headerBgColor = when (platform) {
        "WhatsApp" -> Color(0xFF075E54)
        "Messenger" -> Color(0xFF0084FF)
        "Instagram" -> Color(0xFF121212)
        "Twitter/X" -> Color(0xFF000000)
        "Snapchat" -> Color(0xFFFFFC00)
        else -> MaterialTheme.colorScheme.primary
    }

    val headerTextColor = if (platform == "Snapchat") Color.Black else Color.White

    val chatBgColor = when (platform) {
        "WhatsApp" -> Color(0xFFECE5DD)
        "Instagram" -> Color(0xFF000000)
        "Twitter/X" -> Color(0xFF15202B)
        else -> MaterialTheme.colorScheme.surface
    }

    val outgoingBubbleColor = when (platform) {
        "WhatsApp" -> Color(0xFFDCF8C6)
        "Messenger" -> Color(0xFF0084FF)
        "Instagram" -> Color(0xFF3797F0)
        "Twitter/X" -> Color(0xFF1DA1F2)
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val outgoingTextColor = when (platform) {
        "Messenger", "Instagram", "Twitter/X" -> Color.White
        else -> Color.Black
    }

    val incomingBubbleColor = when (platform) {
        "WhatsApp" -> Color.White
        "Instagram" -> Color(0xFF262626)
        "Twitter/X" -> Color(0xFF253341)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val incomingTextColor = if (platform == "Instagram" || platform == "Twitter/X") Color.White else Color.Black

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 8.dp,
        color = chatBgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Platform Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBgColor)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = headerTextColor,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.Gray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUri != null) {
                        AsyncImage(
                            model = avatarUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, tint = headerTextColor)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = headerTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = headerTextColor.copy(alpha = 0.8f)
                    )
                }

                Icon(Icons.Default.Call, contentDescription = null, tint = headerTextColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(14.dp))
                Icon(Icons.Default.Videocam, contentDescription = null, tint = headerTextColor, modifier = Modifier.size(22.dp))
            }

            // Chat Messages Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                messages.forEach { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isOutgoing) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (msg.isOutgoing) 16.dp else 4.dp,
                                bottomEnd = if (msg.isOutgoing) 4.dp else 16.dp
                            ),
                            color = if (msg.isOutgoing) outgoingBubbleColor else incomingBubbleColor,
                            shadowElevation = 1.dp,
                            modifier = Modifier.widthIn(max = 260.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (msg.isOutgoing) outgoingTextColor else incomingTextColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(
                                        text = msg.time,
                                        fontSize = 10.sp,
                                        color = (if (msg.isOutgoing) outgoingTextColor else incomingTextColor).copy(alpha = 0.6f)
                                    )
                                    if (msg.isOutgoing) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = "Read",
                                            tint = if (platform == "WhatsApp") Color(0xFF34B7F1) else outgoingTextColor.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Input Bar Mock
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Message...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = headerBgColor)
            }
        }
    }
}
