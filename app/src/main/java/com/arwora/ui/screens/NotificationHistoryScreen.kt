package com.arwora.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.arwora.NameGenApplication
import com.arwora.data.db.NotificationEntity
import com.arwora.utils.SoundEffectManager
import com.arwora.utils.WirelessPairingManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationHistoryScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = LocalClipboardManager.current
    val repository = (context.applicationContext as NameGenApplication).notificationRepository
    val notifications by repository.allNotifications.collectAsStateWithLifecycle(initialValue = emptyList())
    val hasNotificationAccess by WirelessPairingManager.notificationAccessGranted.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }

    DisposableEffect(lifecycleOwner, context) {
        WirelessPairingManager.checkNotificationPermissionStatus(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                WirelessPairingManager.checkNotificationPermissionStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Active / Recent, 1: Dismissed History
    var showClearDialog by remember { mutableStateOf(false) }

    // Clear All Confirmation Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Notifications") },
            text = { Text("Are you sure you want to delete all notification history logs? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    SoundEffectManager.playClick()
                    coroutineScope.launch {
                        repository.clear()
                        Toast.makeText(context, "All notification history cleared", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val activeList = remember(notifications) { notifications.filter { !it.isDismissed } }
    val dismissedList = remember(notifications) { notifications.filter { it.isDismissed } }
    val currentList = if (selectedTab == 0) activeList else dismissedList

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notification History Log", fontWeight = FontWeight.Bold) },
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
                        showClearDialog = true
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear All")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Notification Access Permission Banner (live ON_RESUME sync)
            val isLight = MaterialTheme.colorScheme.background.red > 0.8f
            val cardOutlineAlpha = if (isLight) 0.25f else 0.4f
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("notification_history_permission_banner"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLight) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                    }
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = cardOutlineAlpha)),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.dp else 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasNotificationAccess) Icons.Default.CheckCircle else Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = if (hasNotificationAccess) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (hasNotificationAccess) "Notification Access Active" else "Notification Access Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (hasNotificationAccess) {
                                "Capturing notifications and pairing alerts automatically"
                            } else {
                                "Enable access to capture system notifications"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            SoundEffectManager.playClick()
                            WirelessPairingManager.openNotificationAccessSettings(context)
                        },
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("notification_history_permission_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasNotificationAccess) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Text(
                            text = if (hasNotificationAccess) "Manage" else "Grant",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bordered Tab Container
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = {
                            SoundEffectManager.playClick()
                            selectedTab = 0
                        },
                        text = {
                            Text(
                                "Active / Recent (${activeList.size})",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            SoundEffectManager.playClick()
                            selectedTab = 1
                        },
                        text = {
                            Text(
                                "Dismissed (${dismissedList.size})",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }

            // Notifications List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (currentList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (selectedTab == 0) "No active notifications recorded" else "No dismissed history logs",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(currentList, key = { it.id }) { notification ->
                        NotificationCard(
                            notification = notification,
                            dateFormat = dateFormat,
                            onCopy = {
                                SoundEffectManager.playClick()
                                clipboardManager.setText(AnnotatedString("${notification.title}\n${notification.text}"))
                                Toast.makeText(context, "Notification text copied", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                SoundEffectManager.playClick()
                                coroutineScope.launch {
                                    repository.deleteById(notification.id)
                                    Toast.makeText(context, "Notification deleted", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationCard(
    notification: NotificationEntity,
    dateFormat: SimpleDateFormat,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = notification.appName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                    Column {
                        Text(
                            text = notification.appName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = dateFormat.format(Date(notification.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy text",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete log",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (notification.text.isNotEmpty()) {
                Text(
                    text = notification.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
