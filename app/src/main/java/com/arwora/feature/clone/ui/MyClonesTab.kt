package com.arwora.feature.clone.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.feature.clone.data.CloneRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyClonesTab(viewModel: CloneViewModel, onCreateNew: () -> Unit) {
    val myClones by viewModel.myClones.collectAsStateWithLifecycle(initialValue = emptyList())
    var cloneToDelete by remember { mutableStateOf<CloneRecord?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNew,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New clone")
            }
        }
    ) { paddingValues ->
        if (myClones.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No clones yet — create your first clone.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(myClones, key = { it.id }) { clone ->
                    val dismissState = rememberSwipeToDismissBoxState()
                    // Ignoring actual swipe handling logic for UI stub but setting up the visual
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val color = when (dismissState.dismissDirection) {
                                SwipeToDismissBoxValue.StartToEnd -> Color(0xFFE6A23C) // Clear cache
                                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error // Delete
                                SwipeToDismissBoxValue.Settled -> Color.Transparent
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(color)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = when (dismissState.dismissDirection) {
                                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                    SwipeToDismissBoxValue.Settled -> Alignment.Center
                                }
                            ) {
                                if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                    Text("Clear Cache", color = Color.White)
                                } else if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                    Text("Delete", color = Color.White)
                                }
                            }
                        },
                        content = {
                            CloneCard(clone = clone, onDeleteRequest = { cloneToDelete = clone })
                        }
                    )
                }
            }
        }
    }
    
    if (cloneToDelete != null) {
        AlertDialog(
            onDismissRequest = { cloneToDelete = null },
            title = { Text("Delete Clone?") },
            text = { Text("This will permanently remove ${cloneToDelete?.displayName} and all its private data.") },
            confirmButton = {
                TextButton(onClick = { 
                    // Stub delete
                    cloneToDelete = null 
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { cloneToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CloneCard(clone: CloneRecord, onDeleteRequest: () -> Unit) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val outlineAlpha = if (isLight) 0.25f else 0.45f
    val cardBgColor = if (isLight) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
    }

    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = outlineAlpha)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {}
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .background(Color(clone.badgeColor), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = clone.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (clone.locked) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Locked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                Text(
                    text = "${clone.engine} • 45MB • Just now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { /* Open clone */ },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 32.dp)
                ) {
                    Text("Open", style = MaterialTheme.typography.labelLarge)
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Clone options")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(text = { Text("Create shortcut") }, onClick = { menuExpanded = false })
                        DropdownMenuItem(text = { Text("Clear cache") }, onClick = { menuExpanded = false })
                        DropdownMenuItem(text = { Text("Backup/Restore") }, onClick = { menuExpanded = false })
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDeleteRequest()
                            }
                        )
                    }
                }
            }
        }
    }
}
