package com.arwora.feature.system.shell

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.ui.components.GooglePlayCompactLinearProgress
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.SoundEffectManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellCommandSheet(
    viewModel: ShellCommandViewModel,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    val adbConnected by NativeAdbManager.isConnected.collectAsStateWithLifecycle()
    val commandText by viewModel.commandText.collectAsStateWithLifecycle()
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val lastResult by viewModel.lastResult.collectAsStateWithLifecycle()
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val showDangerousConfirmation by viewModel.showDangerousConfirmation.collectAsStateWithLifecycle()
    val pendingDangerousCommand by viewModel.pendingDangerousCommand.collectAsStateWithLifecycle()
    val showHelpDialog by viewModel.showHelpDialog.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(adbConnected) {
        viewModel.refreshStatus()
    }

    DisposableEffect(lifecycleOwner) {
        viewModel.refreshStatus()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // ==================== 1. HEADER SECTION ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Shell Command",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                val badgeColor = when (status) {
                                    ShellStatus.READY -> Color(0xFF10B981)
                                    ShellStatus.ADB_REQUIRED -> MaterialTheme.colorScheme.error
                                    ShellStatus.WIFI_REQUIRED -> MaterialTheme.colorScheme.error
                                    ShellStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = badgeColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f)),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = status.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                SoundEffectManager.playClick()
                                viewModel.showHelp()
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Information",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Execute low-level system commands using built-in Wireless ADB. This allows accessing features typically restricted to system apps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Warning note
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GppMaybe,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Warning: Use only trusted commands. Misuse can affect system stability.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Wireless ADB not ready banner
            if (status != ShellStatus.READY) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (status == ShellStatus.WIFI_REQUIRED) "Wi-Fi Connection Required" else "Wireless ADB Offline",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (status == ShellStatus.WIFI_REQUIRED)
                                "Connect to a Wi-Fi network and turn on Wireless Debugging in Developer Options to bind local ADB."
                            else
                                "Built-in Wireless ADB is ready to bind. Ensure Wireless Debugging is enabled in Developer Options and tap Connect Now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    viewModel.connectAdb()
                                },
                                enabled = !isConnecting,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (isConnecting) "Connecting..." else "Connect Now",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    SoundEffectManager.playClick()
                                    viewModel.showHelp()
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Setup Guide")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== 2. COMMAND EDITOR ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Command Input",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (commandText.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            viewModel.clearCommand()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B), // Professional terminal background
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(2.dp)) {
                    TextField(
                        value = commandText,
                        onValueChange = { viewModel.updateCommand(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 200.dp),
                        placeholder = {
                            Text(
                                "Enter shell command...",
                                fontFamily = FontFamily.Monospace,
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Color(0xFF38BDF8), // Terminal cursor color
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        trailingIcon = {
                            if (commandText.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        SoundEffectManager.playClick()
                                        val clip = clipboardManager.getText()?.text
                                        if (!clip.isNullOrBlank()) {
                                            viewModel.updateCommand(clip)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color.Gray)
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ==================== 3. PRESET QUICK COMMANDS ====================
            Text(
                text = "Quick Command Chips",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(ShellPresets.list) { preset ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (commandText == preset.command) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (commandText == preset.command) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.clickable {
                            SoundEffectManager.playClick()
                            viewModel.selectPreset(preset)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (commandText == preset.command) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = preset.command,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (commandText == preset.command) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==================== 4. ACTION BUTTONS ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        SoundEffectManager.playClick()
                        viewModel.runCurrentCommand()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isRunning && commandText.isNotBlank(),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    if (isRunning) {
                        GooglePlayCompactLinearProgress(
                            width = 28.dp,
                            height = 4.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Executing...", style = MaterialTheme.typography.titleSmall)
                    } else {
                        Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run Command", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold)
                    }
                }

                if (lastResult != null) {
                    OutlinedButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            if (viewModel.copyOutputToClipboard(context)) {
                                Toast.makeText(context, "Output copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== 5. OUTPUT PANEL ====================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Console Output",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (lastResult != null) {
                    TextButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            viewModel.runCurrentCommand() // Re-run
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Re-run", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val currentResult = lastResult
            if (currentResult != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F172A), // Dark slate console
                    border = BorderStroke(1.5.dp, if (currentResult.isSuccess) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (currentResult.isSuccess) Color(0xFF065F46).copy(alpha = 0.8f) else Color(0xFF991B1B).copy(alpha = 0.8f)
                            ) {
                                Text(
                                    text = if (currentResult.isSuccess) "EXIT SUCCESS (0)" else "EXIT ERROR (${currentResult.exitCode})",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Text(
                                text = "t: ${currentResult.executionTimeMs}ms",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                if (currentResult.stdout.isNotEmpty()) {
                                    Text(
                                        text = currentResult.stdout,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = Color(0xFFF1F5F9),
                                        lineHeight = 20.sp
                                    )
                                }
                                if (currentResult.stderr.isNotEmpty()) {
                                    if (currentResult.stdout.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .padding(vertical = 12.dp)
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(Color.DarkGray)
                                        )
                                    }
                                    Text(
                                        text = "[STDERR]\n" + currentResult.stderr,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = Color(0xFFFDA4AF),
                                        lineHeight = 20.sp
                                    )
                                }
                                if (currentResult.stdout.isEmpty() && currentResult.stderr.isEmpty()) {
                                    Text(
                                        text = "(Execution finished with no output stream)",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Console is waiting for execution...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ==================== 6. RECENT HISTORY ====================
            val recentHistory = remember(historyList) { historyList.take(5) }
            val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Recent Commands",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (recentHistory.isNotEmpty()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "${recentHistory.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (historyList.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            viewModel.clearAllHistory()
                        }
                    ) {
                        Text("Purge History", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (recentHistory.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No command history recorded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recentHistory.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    SoundEffectManager.playClick()
                                    viewModel.updateCommand(item.command)
                                    Toast.makeText(context, "Restored: ${item.command}", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SubdirectoryArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = item.command,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${dateFormat.format(Date(item.timestamp))} • ${item.executionTimeMs}ms • exit:${item.exitCode}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        SoundEffectManager.playClick()
                                        viewModel.deleteHistoryItem(item.id)
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // ==================== DANGEROUS COMMAND CONFIRMATION ====================
    if (showDangerousConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDangerousConfirmation() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Potentially Dangerous Command",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "The command you are attempting to execute matches potentially destructive patterns:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = pendingDangerousCommand.orEmpty(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Text(
                        text = "Are you sure you want to proceed with executing this command?",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        SoundEffectManager.playClick()
                        viewModel.confirmDangerousExecution()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Execute Anyway")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        viewModel.dismissDangerousConfirmation()
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // ==================== WIRELESS ADB SETUP HELP DIALOG ====================
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissHelp() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Wireless ADB Console Setup",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "This tool executes shell commands directly through Arwora Hyper Toolkit's built-in Wireless ADB engine.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("1. Connect your device to an active Wi-Fi network.", style = MaterialTheme.typography.bodySmall)
                    Text("2. Enable Wireless Debugging in Developer Options.", style = MaterialTheme.typography.bodySmall)
                    Text("3. Pair once using the Wireless Debugging wizard if this is your first connection.", style = MaterialTheme.typography.bodySmall)
                    Text("4. Tap Connect Now to bind directly to the local ADB daemon.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        SoundEffectManager.playClick()
                        viewModel.dismissHelp()
                        viewModel.connectAdb()
                    }
                ) {
                    Text("Connect Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        SoundEffectManager.playClick()
                        viewModel.dismissHelp()
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}
