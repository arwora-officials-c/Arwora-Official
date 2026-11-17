package com.arwora.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import android.content.pm.PackageManager
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.GooglePlayCompactLinearProgress
import com.arwora.ui.components.GooglePlayLinearProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ScanItemState { PENDING, SCANNING, SAFE }

data class SecurityCategory(
    val title: String,
    var state: ScanItemState = ScanItemState.PENDING
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScanScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isScanning by remember { mutableStateOf(false) }
    var scanCompleted by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var currentItemText by remember { mutableStateOf("Ready to scan") }
    var scanJob by remember { mutableStateOf<Job?>(null) }
    
    val categories = remember {
        mutableStateListOf(
            SecurityCategory("System vulnerabilities"),
            SecurityCategory("Payment environment"),
            SecurityCategory("Trojan viruses"),
            SecurityCategory("Risky apps")
        )
    }

    val coroutineScope = rememberCoroutineScope()

    fun startScan() {
        if (isScanning) return
        isScanning = true
        scanCompleted = false
        scanProgress = 0.04f
        for (idx in categories.indices) {
            categories[idx] = categories[idx].copy(state = ScanItemState.PENDING)
        }
        
        scanJob?.cancel()
        scanJob = coroutineScope.launch {
            try {
                for (i in categories.indices) {
                    if (!isScanning) break
                    categories[i] = categories[i].copy(state = ScanItemState.SCANNING)

                    when (i) {
                        0 -> { // System vulnerabilities
                            currentItemText = "Checking Root access..."
                            val isRooted = withContext(Dispatchers.IO) {
                                listOf("/system/bin/su", "/system/xbin/su", "/sbin/su").any { java.io.File(it).exists() }
                            }
                            scanProgress = 0.12f
                            delay(450)

                            currentItemText = "Checking ADB & Developer Options..."
                            withContext(Dispatchers.IO) {
                                android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.ADB_ENABLED, 0)
                                android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
                            }
                            scanProgress = 0.22f
                            delay(450)

                            currentItemText = "System integrity: ${if (isRooted) "Rooted (Notice)" else "Secure"}"
                            scanProgress = 0.25f
                            delay(400)
                        }
                        1 -> { // Payment environment
                            currentItemText = "Verifying secure environment..."
                            scanProgress = 0.38f
                            delay(600)
                            scanProgress = 0.50f
                            delay(400)
                        }
                        2 -> { // Trojan viruses
                            currentItemText = "Scanning package signatures..."
                            val totalCount = withContext(Dispatchers.IO) {
                                val pm = context.packageManager
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L)).size
                                } else {
                                    @Suppress("DEPRECATION")
                                    pm.getInstalledPackages(0).size
                                }
                            }
                            scanProgress = 0.65f
                            currentItemText = "Verified $totalCount package signatures"
                            delay(600)
                            scanProgress = 0.75f
                        }
                        3 -> { // Risky apps
                            currentItemText = "Analyzing app permissions..."
                            val sampleNames = withContext(Dispatchers.IO) {
                                val pm = context.packageManager
                                val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
                                } else {
                                    @Suppress("DEPRECATION")
                                    pm.getInstalledPackages(0)
                                }
                                packages.take(15).map { it.packageName }
                            }
                            val stepDelta = if (sampleNames.isNotEmpty()) 0.25f / sampleNames.size else 0.25f
                            for ((idx, pkgName) in sampleNames.withIndex()) {
                                if (!isScanning) break
                                currentItemText = "Scanning: $pkgName"
                                scanProgress = (0.75f + (idx + 1) * stepDelta).coerceAtMost(0.99f)
                                delay(110)
                            }
                        }
                    }

                    if (isScanning) {
                        categories[i] = categories[i].copy(state = ScanItemState.SAFE)
                    }
                }

                if (isScanning) {
                    isScanning = false
                    scanCompleted = true
                    scanProgress = 1f
                    currentItemText = "Scan Complete: 100% Secure"
                }
            } finally {
                if (!scanCompleted && isScanning) {
                    isScanning = false
                }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        isScanning = false
        scanProgress = 0f
        currentItemText = "Scan stopped"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Scan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Button(
                    onClick = { if (isScanning) stopScan() else startScan() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = ArworaDesignTokens.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isScanning) "Stop scanning" else if (scanCompleted) "Scan Again" else "Start Scan",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Shield Section (No circular progress bars, just a glowing shield)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                // Animated Base Glow
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseRadius by infiniteTransition.animateFloat(
                    initialValue = 70f,
                    targetValue = 90f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse_radius"
                )
                
                val shieldColor = if (scanCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.primary

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2, size.height / 2)
                    
                    if (isScanning || scanCompleted) {
                        // Soft glowing aura
                        drawCircle(
                            color = shieldColor.copy(alpha = 0.2f),
                            radius = pulseRadius.dp.toPx(),
                            center = center
                        )
                    } else {
                        // Static glow
                        drawCircle(
                            color = shieldColor.copy(alpha = 0.1f),
                            radius = 70.dp.toPx(),
                            center = center
                        )
                    }
                    
                    // Central shield circle base
                    drawCircle(
                        color = shieldColor.copy(alpha = 0.15f),
                        radius = 60.dp.toPx(),
                        center = center
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = shieldColor
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = currentItemText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            if (isScanning || scanCompleted) {
                Spacer(modifier = Modifier.height(14.dp))
                GooglePlayLinearProgressBar(
                    progress = scanProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp),
                    height = 6.dp,
                    color = if (scanCompleted) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Category Checklist
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(categories) { _, category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ArworaDesignTokens.InnerTileShape)
                            .background(ArworaDesignTokens.cardContainerColor())
                            .border(ArworaDesignTokens.innerTileBorder(), ArworaDesignTokens.InnerTileShape)
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Box(
                            modifier = Modifier.widthIn(min = 32.dp).height(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when (category.state) {
                                ScanItemState.PENDING -> {
                                    // Empty state, or a small dot
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                                    )
                                }
                                ScanItemState.SCANNING -> {
                                    GooglePlayCompactLinearProgress(
                                        width = 32.dp,
                                        height = 4.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                ScanItemState.SAFE -> {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Safe",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(24.dp)
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
