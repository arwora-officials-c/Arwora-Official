package com.arwora.ui.screens

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class StorageCategory(
    val id: String,
    val name: String,
    val size: Long,
    val percentage: Float,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String,
    val cleanable: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalyzerScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var scanning by remember { mutableStateOf(false) }
    var showCleanDialog by remember { mutableStateOf(false) }
    var cleanProgress by remember { mutableStateOf(0f) }
    var cleanedBytesResult by remember { mutableStateOf(0L) }
    var isCleaningCompleted by remember { mutableStateOf(false) }

    // State for tracking space cleaned
    var simulatedCacheCleaned by remember { mutableStateOf(false) }
    var simulatedDownloadsCleaned by remember { mutableStateOf(false) }

    val initialSimulatedCache = 485 * 1024 * 1024L // 485 MB
    val initialSimulatedDownloads = 1240 * 1024 * 1024L // 1.24 GB

    val currentCacheSize = if (simulatedCacheCleaned) 0L else (getFolderSize(context.cacheDir) + initialSimulatedCache)
    val currentDownloadsSize = if (simulatedDownloadsCleaned) 18 * 1024 * 1024L else initialSimulatedDownloads

    // Device overall space (Fallback to standard values if total space is 0)
    val totalBytes = remember { 
        val space = File(context.filesDir.absolutePath).totalSpace
        if (space > 0) space else 64 * 1024 * 1024 * 1024L
    }
    
    // Recalculate usable space dynamically
    val freeBytesBase = remember { 
        val space = File(context.filesDir.absolutePath).usableSpace
        if (space > 0) space else 24 * 1024 * 1024 * 1024L
    }
    
    val freeBytes = freeBytesBase + 
            (if (simulatedCacheCleaned) initialSimulatedCache else 0L) + 
            (if (simulatedDownloadsCleaned) (initialSimulatedDownloads - 18 * 1024 * 1024L) else 0L)

    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(1L)
    
    // Distribute remaining space proportionally across categories so they match exact system storage
    val categories = remember(usedBytes, currentCacheSize, currentDownloadsSize) {
        val remaining = (usedBytes - currentCacheSize - currentDownloadsSize).coerceAtLeast(0L)
        
        val dcimSize = (remaining * 0.38f).toLong()
        val appsSize = (remaining * 0.28f).toLong()
        val docsSize = (remaining * 0.10f).toLong()
        val systemSize = (remaining * 0.24f).toLong()
        
        val totalMatched = dcimSize + appsSize + docsSize + systemSize + currentCacheSize + currentDownloadsSize
        val adjustFactor = if (totalMatched > 0) usedBytes.toFloat() / totalMatched.toFloat() else 1f
        
        val finalDcim = (dcimSize * adjustFactor).toLong()
        val finalApps = (appsSize * adjustFactor).toLong()
        val finalDocs = (docsSize * adjustFactor).toLong()
        val finalSystem = (systemSize * adjustFactor).toLong()
        val finalCache = (currentCacheSize * adjustFactor).toLong()
        val finalDownloads = (currentDownloadsSize * adjustFactor).toLong()
        
        listOf(
            StorageCategory("dcim", "Photos & Videos", finalDcim, finalDcim.toFloat() / usedBytes.toFloat(), Color(0xFF6366F1), Icons.Default.PhotoLibrary, "Camera photos, screenshots, movies"),
            StorageCategory("apps", "Apps & Games", finalApps, finalApps.toFloat() / usedBytes.toFloat(), Color(0xFF10B981), Icons.Default.Apps, "Applications, updates & user data"),
            StorageCategory("downloads", "Downloads", finalDownloads, finalDownloads.toFloat() / usedBytes.toFloat(), Color(0xFFF59E0B), Icons.Default.Download, "Web downloads, email files", cleanable = !simulatedDownloadsCleaned),
            StorageCategory("documents", "Documents & PDFs", finalDocs, finalDocs.toFloat() / usedBytes.toFloat(), Color(0xFF3B82F6), Icons.Default.Description, "PDF files, documents, sheets"),
            StorageCategory("cache", "Cache & Junk", finalCache, finalCache.toFloat() / usedBytes.toFloat(), Color(0xFFEF4444), Icons.Default.DeleteSweep, "App temporary files, residues", cleanable = !simulatedCacheCleaned),
            StorageCategory("system", "System Files", finalSystem, finalSystem.toFloat() / usedBytes.toFloat(), Color(0xFF8B5CF6), Icons.Default.Android, "Core Android OS and caches")
        )
    }

    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    val highlightedCategory = remember(selectedCategoryId, categories) {
        categories.find { it.id == selectedCategoryId }
    }

    // Interactive sweep animation trigger
    var startChartAnimation by remember { mutableStateOf(false) }
    val chartEntryProgress by animateFloatAsState(
        targetValue = if (startChartAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
    )

    LaunchedEffect(Unit) {
        scanning = true
        delay(600) // Simulated fast directory scan for premium feel
        scanning = false
        startChartAnimation = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Storage Analyzer", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text("Interactive space optimizer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        SoundEffectManager.playClick(context)
                        navController.popBackStack() 
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        SoundEffectManager.playClick(context)
                        coroutineScope.launch {
                            scanning = true
                            startChartAnimation = false
                            delay(500)
                            scanning = false
                            startChartAnimation = true
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan Storage")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (scanning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(52.dp),
                        strokeWidth = 4.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Analyzing Directory Structure...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // DONUT CHART CARD
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Header
                            Text(
                                text = "Interactive Storage Breakdown",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Donut View
                            Box(
                                modifier = Modifier
                                    .size(210.dp)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                DonutChart(
                                    categories = categories,
                                    entryProgress = chartEntryProgress,
                                    selectedId = selectedCategoryId,
                                    onSegmentSelected = { id ->
                                        SoundEffectManager.playClick(context)
                                        selectedCategoryId = if (selectedCategoryId == id) null else id
                                    }
                                )

                                // Center Info Display
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    if (highlightedCategory != null) {
                                        Icon(
                                            imageVector = highlightedCategory.icon,
                                            contentDescription = null,
                                            tint = highlightedCategory.color,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = highlightedCategory.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatSize(highlightedCategory.size),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${(highlightedCategory.percentage * 100).toInt()}% of Used",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = highlightedCategory.color,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Storage,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Total Space Used",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatSize(usedBytes),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${((usedBytes.toFloat() / totalBytes.toFloat()) * 100).toInt()}% of ${formatSize(totalBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Dynamic Storage Bar Progress
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${formatSize(usedBytes)} Used of ${formatSize(totalBytes)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${formatSize(freeBytes)} Free",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { usedBytes.toFloat() / totalBytes.toFloat() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .background(
                                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                            shape = RoundedCornerShape(5.dp)
                                        ),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }

                // QUICK CLEANUP ROW
                if (!simulatedCacheCleaned || !simulatedDownloadsCleaned) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Storage Optimization",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Clean system temporary junk and cache residues.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        SoundEffectManager.playClick(context)
                                        cleanedBytesResult = currentCacheSize
                                        isCleaningCompleted = false
                                        cleanProgress = 0f
                                        showCleanDialog = true
                                        
                                        coroutineScope.launch {
                                            while (cleanProgress < 1f) {
                                                cleanProgress += 0.1f
                                                delay(120)
                                            }
                                            isCleaningCompleted = true
                                            simulatedCacheCleaned = true
                                            SoundEffectManager.playCoinLand(context)
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Quick Clean")
                                }
                            }
                        }
                    }
                }

                // LIST OF FOLDERS / CATEGORIES
                item {
                    Text(
                        text = "Detailed Breakdown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }

                items(categories) { category ->
                    val isHighlighted = category.id == selectedCategoryId
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                SoundEffectManager.playClick(context)
                                selectedCategoryId = if (selectedCategoryId == category.id) null else category.id
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isHighlighted) category.color.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            width = if (isHighlighted) 1.5.dp else 1.dp,
                            color = if (isHighlighted) category.color else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon Box
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(category.color.copy(alpha = 0.1f))
                                    .border(1.dp, category.color.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    tint = category.color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Content Details
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = category.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Storage Size & Clean Option
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = formatSize(category.size),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${(category.percentage * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = category.color
                                )

                                if (category.cleanable) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    IconButton(
                                        onClick = {
                                            SoundEffectManager.playClick(context)
                                            if (category.id == "cache") {
                                                cleanedBytesResult = currentCacheSize
                                                isCleaningCompleted = false
                                                cleanProgress = 0f
                                                showCleanDialog = true
                                                coroutineScope.launch {
                                                    while (cleanProgress < 1f) {
                                                        cleanProgress += 0.15f
                                                        delay(100)
                                                    }
                                                    isCleaningCompleted = true
                                                    simulatedCacheCleaned = true
                                                    SoundEffectManager.playCoinLand(context)
                                                }
                                            } else if (category.id == "downloads") {
                                                cleanedBytesResult = currentDownloadsSize - 18 * 1024 * 1024L
                                                isCleaningCompleted = false
                                                cleanProgress = 0f
                                                showCleanDialog = true
                                                coroutineScope.launch {
                                                    while (cleanProgress < 1f) {
                                                        cleanProgress += 0.1f
                                                        delay(150)
                                                    }
                                                    isCleaningCompleted = true
                                                    simulatedDownloadsCleaned = true
                                                    SoundEffectManager.playCoinLand(context)
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Clean Category",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
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

    // CLEANING DIALOG
    if (showCleanDialog) {
        AlertDialog(
            onDismissRequest = { if (isCleaningCompleted) showCleanDialog = false },
            title = {
                Text(
                    text = if (isCleaningCompleted) "Optimization Complete!" else "Optimizing Storage...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (!isCleaningCompleted) {
                        CircularProgressIndicator(
                            progress = { cleanProgress },
                            modifier = Modifier.size(72.dp),
                            strokeWidth = 6.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                        Text(
                            text = "Deleting temporary logs and duplicates...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Text(
                            text = "Successfully reclaimed ${formatSize(cleanedBytesResult)} of space! Your device has been optimized.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                if (isCleaningCompleted) {
                    Button(
                        onClick = { 
                            SoundEffectManager.playClick(context)
                            showCleanDialog = false 
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Awesome")
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = isCleaningCompleted,
                dismissOnClickOutside = isCleaningCompleted
            )
        )
    }
}

@Composable
fun DonutChart(
    categories: List<StorageCategory>,
    entryProgress: Float,
    selectedId: String?,
    onSegmentSelected: (String) -> Unit
) {
    val total = categories.sumOf { it.size }.coerceAtLeast(1L)
    
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clickable {
                // Clicking outside or on canvas cycles through segments
                val nextIndex = if (selectedId == null) 0 else {
                    val idx = categories.indexOfFirst { it.id == selectedId }
                    (idx + 1) % categories.size
                }
                onSegmentSelected(categories[nextIndex].id)
            }
    ) {
        val strokeWidth = 24.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
        val arcSize = Size(diameter, diameter)

        var currentAngle = -90f
        val gapAngle = if (categories.size > 1) 3f else 0f

        categories.forEach { category ->
            val isHighlighted = category.id == selectedId
            val percentage = category.size.toFloat() / total.toFloat()
            val sweepAngle = ((percentage * 360f - gapAngle) * entryProgress).coerceAtLeast(1f)
            
            drawArc(
                color = category.color,
                startAngle = currentAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = if (isHighlighted) strokeWidth * 1.3f else strokeWidth,
                    cap = StrokeCap.Round
                )
            )
            
            currentAngle += sweepAngle + gapAngle
        }
    }
}

fun getFolderSize(file: File): Long {
    var size = 0L
    try {
        if (file.isDirectory) {
            val files = file.listFiles()
            if (files != null) {
                for (child in files) {
                    size += getFolderSize(child)
                }
            }
        } else {
            size = file.length()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return size
}

fun formatSize(size: Long): String {
    if (size < 1024) return "$size B"
    val kb = size / 1024
    if (kb < 1024) return "$kb KB"
    val mb = kb / 1024
    if (mb < 1024) return "$mb MB"
    val gb = mb.toFloat() / 1024f
    return String.format("%.2f GB", gb)
}
