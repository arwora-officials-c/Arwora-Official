package com.arwora.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.utils.AnimationTweakManager
import com.arwora.utils.NativeAdbManager
import com.arwora.utils.RefreshRateManager
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentScale by remember { mutableStateOf(AnimationTweakManager.getCurrentAnimationScale(context)) }
    var currentPeakRate by remember { mutableStateOf(RefreshRateManager.getCurrentPeakRefreshRate(context)) }
    var currentMinRate by remember { mutableStateOf(RefreshRateManager.getCurrentMinRefreshRate(context)) }

    val isAdbConnected by NativeAdbManager.isConnected.collectAsStateWithLifecycle()
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Custom peak & min slider states
    var customPeak by remember { mutableStateOf(currentPeakRate) }
    var customMin by remember { mutableStateOf(currentMinRate) }

    val lifecycleOwner = LocalLifecycleOwner.current

    fun refreshAdbAndMetrics() {
        NativeAdbManager.refreshConnectionStatus(context)
        currentScale = AnimationTweakManager.getCurrentAnimationScale(context)
        currentPeakRate = RefreshRateManager.getCurrentPeakRefreshRate(context)
        currentMinRate = RefreshRateManager.getCurrentMinRefreshRate(context)
        customPeak = currentPeakRate
        customMin = currentMinRate
    }

    DisposableEffect(lifecycleOwner) {
        refreshAdbAndMetrics()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAdbAndMetrics()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Animation & Refresh Rate Tweak", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "System Status",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "Anim Scale: ${String.format("%.1f", currentScale)}x | Peak: ${currentPeakRate.toInt()}Hz",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isAdbConnected) {
                            "Wireless ADB active. Changes apply instantly to system settings."
                        } else {
                            "Wireless ADB connection required for global ADB settings tuning."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    if (!isAdbConnected) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                SoundEffectManager.playClick(context)
                                coroutineScope.launch {
                                    val (ok, msg) = NativeAdbManager.connectAutomatically(context)
                                    refreshAdbAndMetrics()
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Connect Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Display Refresh Rate Manager Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Display Refresh Rate Manager",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Current Peak Rate: ${currentPeakRate.toInt()} Hz | Min Rate: ${currentMinRate.toInt()} Hz",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Preset Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentPeakRate == 60f && currentMinRate == 60f,
                            onClick = {
                                SoundEffectManager.playClick(context)
                                if (!isAdbConnected) {
                                    showPermissionDialog = true
                                    return@FilterChip
                                }
                                val success = RefreshRateManager.setRefreshRates(context, 60f, 60f)
                                if (success) {
                                    currentPeakRate = 60f
                                    currentMinRate = 60f
                                    customPeak = 60f
                                    customMin = 60f
                                    Toast.makeText(context, "Refresh Rate set to 60 Hz (Power Saving)", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to apply refresh rate via Wireless ADB", Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = { Text("60 Hz") },
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = currentPeakRate == 90f && currentMinRate == 90f,
                            onClick = {
                                SoundEffectManager.playClick(context)
                                if (!isAdbConnected) {
                                    showPermissionDialog = true
                                    return@FilterChip
                                }
                                val success = RefreshRateManager.setRefreshRates(context, 90f, 90f)
                                if (success) {
                                    currentPeakRate = 90f
                                    currentMinRate = 90f
                                    customPeak = 90f
                                    customMin = 90f
                                    Toast.makeText(context, "Refresh Rate set to 90 Hz (Smooth)", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to apply refresh rate via Wireless ADB", Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = { Text("90 Hz") },
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = currentPeakRate >= 120f,
                            onClick = {
                                SoundEffectManager.playClick(context)
                                if (!isAdbConnected) {
                                    showPermissionDialog = true
                                    return@FilterChip
                                }
                                val success = RefreshRateManager.setRefreshRates(context, 120f, 120f)
                                if (success) {
                                    currentPeakRate = 120f
                                    currentMinRate = 120f
                                    customPeak = 120f
                                    customMin = 120f
                                    Toast.makeText(context, "Refresh Rate set to 120 Hz (Ultra Smooth)", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to apply refresh rate via Wireless ADB", Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = { Text("120 Hz") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Auto / Dynamic Chip
                    OutlinedButton(
                        onClick = {
                            SoundEffectManager.playClick(context)
                            if (!isAdbConnected) {
                                showPermissionDialog = true
                                return@OutlinedButton
                            }
                            val success = RefreshRateManager.setRefreshRates(context, null, null)
                            if (success) {
                                currentPeakRate = RefreshRateManager.getCurrentPeakRefreshRate(context)
                                currentMinRate = RefreshRateManager.getCurrentMinRefreshRate(context)
                                customPeak = currentPeakRate
                                customMin = currentMinRate
                                Toast.makeText(context, "Restored Auto / Dynamic Stock Refresh Rate", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to reset refresh rate", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Auto / Dynamic (Stock System Behavior)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Custom Peak Refresh Rate: ${customPeak.toInt()} Hz",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = customPeak,
                        onValueChange = { customPeak = it },
                        onValueChangeFinished = {
                            if (isAdbConnected) {
                                RefreshRateManager.setRefreshRates(context, customPeak, customMin)
                                currentPeakRate = customPeak
                                Toast.makeText(context, "Peak Refresh Rate set to ${customPeak.toInt()} Hz", Toast.LENGTH_SHORT).show()
                            } else {
                                showPermissionDialog = true
                            }
                        },
                        valueRange = 60f..120f,
                        steps = 5
                    )
                }
            }

            // Animation Speed Presets Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Animation Speed Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Current Scale: ${String.format("%.1f", currentScale)}x",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 1. Super Fast (0.5x)
                    Button(
                        onClick = {
                            SoundEffectManager.playClick(context)
                            if (!isAdbConnected) {
                                showPermissionDialog = true
                                return@Button
                            }
                            val success = AnimationTweakManager.setAnimationScales(context, 0.5f)
                            if (success) {
                                currentScale = 0.5f
                                Toast.makeText(context, "Animations set to Super Fast (0.5x)", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to apply animation scales.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentScale == 0.5f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = if (currentScale == 0.5f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Super Fast (0.5x)",
                            color = if (currentScale == 0.5f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 2. No Animation (0.0x)
                    Button(
                        onClick = {
                            SoundEffectManager.playClick(context)
                            if (!isAdbConnected) {
                                showPermissionDialog = true
                                return@Button
                            }
                            val success = AnimationTweakManager.setAnimationScales(context, 0.0f)
                            if (success) {
                                currentScale = 0.0f
                                Toast.makeText(context, "Animations disabled (0.0x)", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to apply animation scales.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentScale == 0.0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Animation, contentDescription = null, tint = if (currentScale == 0.0f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "No Animation / Off (0.0x)",
                            color = if (currentScale == 0.0f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 3. Stock Default (1.0x)
                    Button(
                        onClick = {
                            SoundEffectManager.playClick(context)
                            if (!isAdbConnected) {
                                showPermissionDialog = true
                                return@Button
                            }
                            val success = AnimationTweakManager.setAnimationScales(context, 1.0f)
                            if (success) {
                                currentScale = 1.0f
                                Toast.makeText(context, "Animations reset to Stock Default (1.0x)", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to apply animation scales.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentScale == 1.0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = if (currentScale == 1.0f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Stock Default (1.0x)",
                            color = if (currentScale == 1.0f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Wireless ADB is Offline") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Privileged actions require an active local Wireless ADB connection.", style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("1. Connect to Wi-Fi and enable Wireless Debugging in Developer Options.", style = MaterialTheme.typography.bodySmall)
                    Text("2. Tap 'Connect Now' below or in System Dashboard -> Wireless Debugging.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDialog = false
                        coroutineScope.launch {
                            val (_, msg) = NativeAdbManager.connectAutomatically(context)
                            refreshAdbAndMetrics()
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Connect Now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }
}
