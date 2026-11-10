package com.arwora.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.arwora.NameGenApplication
import com.arwora.data.db.LocationLogEntity
import com.arwora.feature.system.location.LocationTrackerManager
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationHistoryScreen(
    onBack: () -> Unit,
    onOpenMap: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as NameGenApplication
    val repository = remember { com.arwora.data.repository.LocationLogRepository(app.database.locationLogDao()) }
    val logs by repository.allLogs.collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var isTrackingActive by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            LocationTrackerManager.startTracking(context, repository)
            isTrackingActive = true
            Toast.makeText(context, "GPS Location Tracking Started", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Location permission is required for GPS logging", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location History & GPS Logs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenMap) {
                        Icon(imageVector = Icons.Default.Map, contentDescription = "View Route Map")
                    }
                    if (logs.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showExportMenu = true }) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = "Export Logs")
                            }
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Export as CSV") },
                                    onClick = {
                                        showExportMenu = false
                                        exportAndShareCsv(context, logs)
                                    },
                                    leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as JSON") },
                                    onClick = {
                                        showExportMenu = false
                                        exportAndShareJson(context, logs)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) }
                                )
                            }
                        }

                        IconButton(onClick = {
                            coroutineScope.launch {
                                repository.clear()
                                Toast.makeText(context, "Location history cleared", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear History")
                        }
                    }
                }
            )
        }
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Periodic GPS Logging",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isTrackingActive) "Status: Active (Checking every 5 mins)" else "Status: Inactive",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isTrackingActive) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = isTrackingActive,
                            onCheckedChange = { active ->
                                if (active) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else {
                                    LocationTrackerManager.stopTracking()
                                    isTrackingActive = false
                                    Toast.makeText(context, "GPS Location Tracking Stopped", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    Text(
                        text = "Logs coordinates when the device stays within a 50m radius for 5+ minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.LocationOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No location logs recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        LocationLogItemCard(log = log)
                    }
                }
            }
        }
    }
}

@Composable
fun LocationLogItemCard(log: LocationLogEntity) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.address.ifBlank { "Lat: ${log.latitude}, Lng: ${log.longitude}" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Time: ${log.timestamp} • Duration: ${log.duration}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Coordinates", "${log.latitude}, ${log.longitude}")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Coordinates Copied", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun exportAndShareCsv(context: Context, logs: List<LocationLogEntity>) {
    try {
        val csvBuilder = StringBuilder()
        csvBuilder.append("ID,Latitude,Longitude,Timestamp,Duration,Address\n")
        for (log in logs) {
            csvBuilder.append("${log.id},${log.latitude},${log.longitude},\"${log.timestamp}\",\"${log.duration}\",\"${log.address}\"\n")
        }

        val file = File(context.cacheDir, "arwora_gps_logs.csv")
        file.writeText(csvBuilder.toString())

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Arwora GPS Logs CSV Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share GPS Logs (CSV)"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error exporting CSV: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

private fun exportAndShareJson(context: Context, logs: List<LocationLogEntity>) {
    try {
        val jsonBuilder = StringBuilder()
        jsonBuilder.append("[\n")
        logs.forEachIndexed { index, log ->
            jsonBuilder.append("  {\n")
            jsonBuilder.append("    \"id\": ${log.id},\n")
            jsonBuilder.append("    \"latitude\": ${log.latitude},\n")
            jsonBuilder.append("    \"longitude\": ${log.longitude},\n")
            jsonBuilder.append("    \"timestamp\": \"${log.timestamp}\",\n")
            jsonBuilder.append("    \"duration\": \"${log.duration}\",\n")
            jsonBuilder.append("    \"address\": \"${log.address}\"\n")
            jsonBuilder.append("  }${if (index < logs.size - 1) "," else ""}\n")
        }
        jsonBuilder.append("]\n")

        val file = File(context.cacheDir, "arwora_gps_logs.json")
        file.writeText(jsonBuilder.toString())

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Arwora GPS Logs JSON Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share GPS Logs (JSON)"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error exporting JSON: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
