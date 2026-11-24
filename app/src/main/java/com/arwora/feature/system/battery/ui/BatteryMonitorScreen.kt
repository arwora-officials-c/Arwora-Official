package com.arwora.feature.system.battery.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.arwora.feature.system.battery.model.BatteryDrainStatus
import com.arwora.feature.system.battery.model.CpuState
import com.arwora.feature.system.battery.service.BatteryMonitorManager
import com.arwora.utils.SoundEffectManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryMonitorScreen(
    navController: NavController
) {
    val context = LocalContext.current
    BackHandler { navController.popBackStack() }
    val haptic = LocalHapticFeedback.current

    val batteryLevel by BatteryMonitorManager.batteryLevel.collectAsStateWithLifecycle()
    val isCharging by BatteryMonitorManager.isCharging.collectAsStateWithLifecycle()
    val temperature by BatteryMonitorManager.batteryTemperature.collectAsStateWithLifecycle()
    val voltage by BatteryMonitorManager.batteryVoltage.collectAsStateWithLifecycle()
    val batteryHealth by BatteryMonitorManager.batteryHealth.collectAsStateWithLifecycle()
    val dischargeRateMah by BatteryMonitorManager.dischargeRateMahPerHour.collectAsStateWithLifecycle()
    val isCurrentAvailable by BatteryMonitorManager.isCurrentAvailable.collectAsStateWithLifecycle()
    val appStats by BatteryMonitorManager.appStats.collectAsStateWithLifecycle()
    val diagnostics by BatteryMonitorManager.deepSleepDiagnostics.collectAsStateWithLifecycle()
    val lastLog by BatteryMonitorManager.lastActionLog.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        BatteryMonitorManager.refreshStats(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Battery Monitor & Deep Sleep",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "$batteryLevel% • ${if (isCharging) "Charging" else "Discharging"} • ${String.format(java.util.Locale.US, "%.1f", temperature)}°C • $batteryHealth",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (batteryLevel > 20) Color(0xFF10B981) else Color(0xFFEF4444)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick()
                            BatteryMonitorManager.refreshStats(context)
                            Toast.makeText(context, "Refreshed battery telemetry", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Action log banner
            item {
                AnimatedVisibility(
                    visible = lastLog != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = lastLog ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 1. BATTERY OVERVIEW GAUGE CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("battery_overview_card"),
                    shape = com.arwora.ui.components.ArworaDesignTokens.CardShape,
                    colors = CardDefaults.cardColors(containerColor = com.arwora.ui.components.ArworaDesignTokens.cardContainerColor()),
                    border = com.arwora.ui.components.ArworaDesignTokens.cardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "System Battery Status",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = com.arwora.ui.components.ArworaDesignTokens.PillShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = if (isCharging) "AC Charging" else "Battery Powered",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "$batteryLevel%", fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                                Text(text = "Charge Level", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "${voltage} mV", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(text = "Voltage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "${String.format(java.util.Locale.US, "%.1f", temperature)}°C", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(text = "Temperature", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        HorizontalDivider(color = com.arwora.ui.components.ArworaDesignTokens.cardBorderColor().copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = batteryHealth,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (batteryHealth == "Good") Color(0xFF10B981) else MaterialTheme.colorScheme.onSurface
                                )
                                Text(text = "Battery Health", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isCurrentAvailable && dischargeRateMah > 0) "$dischargeRateMah mAh/h" else "0 mAh/h",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(text = if (isCharging) "Charge Rate" else "Power Drain", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // 2. DEEP SLEEP & WAKE LOCK DIAGNOSTIC TOOL
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deep_sleep_diagnostic_card"),
                    shape = com.arwora.ui.components.ArworaDesignTokens.CardShape,
                    colors = CardDefaults.cardColors(containerColor = com.arwora.ui.components.ArworaDesignTokens.cardContainerColor()),
                    border = com.arwora.ui.components.ArworaDesignTokens.cardBorder()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                text = "Deep Sleep & WakeLock Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "Analyzes CPU idle states (${BatteryMonitorManager.formatDurationCompact(diagnostics.deepSleepMs)} deep sleep vs ${BatteryMonitorManager.formatDurationCompact(diagnostics.awakeUptimeMs)} awake), active WakeLocks, and Doze efficiency.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(text = "${diagnostics.deepSleepPercentage}%", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge, color = Color(0xFF10B981))
                                Text(text = "Deep Sleep Ratio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column {
                                Text(text = "${diagnostics.activeWakeLocksCount}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = if (diagnostics.activeWakeLocksCount > 0) Color(0xFFEF4444) else Color(0xFF10B981))
                                Text(text = "Active WakeLocks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column {
                                Text(text = diagnostics.cpuState.name.replace("_", " "), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text(text = "CPU State", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Force Deep Sleep Button adhering to RULE[AGENTS_md]
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                SoundEffectManager.playClick()
                                BatteryMonitorManager.forceDeepSleepAll()
                                Toast.makeText(context, "Executed Deep Sleep & Released WakeLocks", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("force_deep_sleep_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Force Deep Sleep & Release WakeLocks",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // 3. REAL-TIME APP CONSUMPTION STATISTICS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Real-Time App Consumption",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${appStats.size} Apps Monitored",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(appStats) { app ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = app.appName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = when (app.status) {
                                        BatteryDrainStatus.HIGH_DRAIN -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                        BatteryDrainStatus.BACKGROUND_HOG -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                        BatteryDrainStatus.OPTIMIZED -> Color(0xFF10B981).copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    }
                                ) {
                                    Text(
                                        text = app.status.name.replace("_", " "),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = when (app.status) {
                                            BatteryDrainStatus.HIGH_DRAIN -> Color(0xFFEF4444)
                                            BatteryDrainStatus.BACKGROUND_HOG -> Color(0xFFF59E0B)
                                            BatteryDrainStatus.OPTIMIZED -> Color(0xFF10B981)
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${app.batteryPercentage}% battery • ${app.mahConsumed} mAh • ${app.cpuTimeMinutes}m CPU active",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (app.status != BatteryDrainStatus.OPTIMIZED) {
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    SoundEffectManager.playClick()
                                    BatteryMonitorManager.optimizeAppSleep(app.packageName)
                                    Toast.makeText(context, "Put ${app.appName} to sleep", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Put to Sleep", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Asleep",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
