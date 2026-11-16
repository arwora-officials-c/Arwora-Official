package com.arwora.feature.clone.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Apps
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
import com.arwora.ui.components.ArworaAnimatedSwitch
import com.arwora.ui.components.GooglePlayLinearProgressBar
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCloneTab(viewModel: CloneViewModel) {
    val installedApps by viewModel.installedApps.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showUserApps by remember { mutableStateOf(true) }
    var showSystemApps by remember { mutableStateOf(false) }

    val filteredApps = installedApps.filter { 
        (it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)) &&
        (if (showUserApps && showSystemApps) true else if (showUserApps) true /* stub logic for filter */ else true)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search installed apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = showUserApps,
                    onClick = { showUserApps = !showUserApps },
                    label = { Text("User apps") }
                )
                FilterChip(
                    selected = showSystemApps,
                    onClick = { showSystemApps = !showSystemApps },
                    label = { Text("System apps") }
                )
            }

            if (filteredApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No apps found. Adjust filters.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredApps) { app ->
                        AppTile(app = app, onClick = { selectedApp = app })
                    }
                }
            }
        }

        if (selectedApp != null) {
            ModalBottomSheet(
                onDismissRequest = { 
                    scope.launch { 
                        sheetState.hide() 
                        if (!sheetState.isVisible) {
                            selectedApp = null
                        }
                    }
                },
                sheetState = sheetState
            ) {
                CloneConfigurationSheet(
                    app = selectedApp!!,
                    isAdvancedMode = viewModel.isAdvancedModeEnabled.collectAsState().value,
                    onDismiss = {
                        scope.launch { 
                            sheetState.hide() 
                            if (!sheetState.isVisible) {
                                selectedApp = null
                            }
                        }
                    },
                    onCreate = { name, engine, color, locked, shortcut ->
                        scope.launch {
                            viewModel.createClone(selectedApp!!.packageName, name, engine, color, locked, shortcut) { success ->
                                scope.launch {
                                    sheetState.hide()
                                    selectedApp = null
                                    snackbarHostState.showSnackbar("Clone created")
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AppTile(app: InstalledApp, onClick: () -> Unit) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val outlineAlpha = if (isLight) 0.25f else 0.45f
    val cardBgColor = if (isLight) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = outlineAlpha)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.5.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {}
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = app.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CloneConfigurationSheet(
    app: InstalledApp,
    isAdvancedMode: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, Int, Boolean, Boolean) -> Unit
) {
    var cloneName by remember { mutableStateOf("${app.name} - 2") }
    var selectedEngine by remember { mutableStateOf("Virtual Container") }
    var isLocked by remember { mutableStateOf(false) }
    var hideNotifications by remember { mutableStateOf(false) }
    var createShortcut by remember { mutableStateOf(true) }
    var isCreating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val badgeColors = listOf(
        0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(),
        0xFFFFB300.toInt(), 0xFF8E24AA.toInt(), 0xFF00ACC1.toInt()
    )
    var selectedColor by remember { mutableStateOf(badgeColors[0]) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Create clone", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        OutlinedTextField(
            value = cloneName,
            onValueChange = { cloneName = it },
            label = { Text("Clone name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Text("Badge Color", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            badgeColors.forEach { colorInt ->
                val color = Color(colorInt)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = 2.dp,
                            color = if (selectedColor == colorInt) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable { selectedColor = colorInt }
                )
            }
        }

        Text("Engine", style = MaterialTheme.typography.labelLarge)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedEngine == "Virtual Container", onClick = { selectedEngine = "Virtual Container" })
                Text("Virtual Container (Default)", style = MaterialTheme.typography.bodyMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedEngine == "Work Profile", onClick = { selectedEngine = "Work Profile" })
                Text("Work Profile", style = MaterialTheme.typography.bodyMedium)
            }
            if (isAdvancedMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedEngine == "Repackage", onClick = { selectedEngine = "Repackage" })
                    Text("Repackage APK (Advanced)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text("Privacy", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Lock with PIN/Biometric", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            ArworaAnimatedSwitch(checked = isLocked, onCheckedChange = { isLocked = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Hide notifications", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            ArworaAnimatedSwitch(checked = hideNotifications, onCheckedChange = { hideNotifications = it })
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Create home screen shortcut", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            ArworaAnimatedSwitch(checked = createShortcut, onCheckedChange = { createShortcut = it })
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isCreating) {
            GooglePlayLinearProgressBar(modifier = Modifier.fillMaxWidth(), height = 5.dp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, enabled = !isCreating) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { 
                    isCreating = true
                    scope.launch {
                        delay(1000) // fake progress delay
                        onCreate(cloneName, selectedEngine, selectedColor, isLocked, createShortcut)
                    }
                },
                enabled = !isCreating
            ) {
                Text("Create clone")
            }
        }
    }
}
