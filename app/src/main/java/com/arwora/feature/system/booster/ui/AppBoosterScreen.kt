package com.arwora.feature.system.booster.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.arwora.feature.system.booster.service.AppBoosterManager
import com.arwora.ui.components.ArworaAnimatedSwitch
import com.arwora.ui.components.GooglePlayCompactLinearProgress
import com.arwora.ui.components.GooglePlayLinearProgressBar
import com.arwora.utils.SoundEffectManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBoosterScreen(
    navController: NavController
) {
    val context = LocalContext.current
    BackHandler { navController.popBackStack() }
    val haptic = LocalHapticFeedback.current

    val totalRam by AppBoosterManager.totalRamMb.collectAsStateWithLifecycle()
    val availRam by AppBoosterManager.availableRamMb.collectAsStateWithLifecycle()
    val ramPercent by AppBoosterManager.ramUsagePercent.collectAsStateWithLifecycle()
    val isScanning by AppBoosterManager.isScanning.collectAsStateWithLifecycle()
    val isBoosting by AppBoosterManager.isBoosting.collectAsStateWithLifecycle()
    val boostStage by AppBoosterManager.boostStage.collectAsStateWithLifecycle()
    val boostProgress by AppBoosterManager.boostProgress.collectAsStateWithLifecycle()
    val lastBoostResult by AppBoosterManager.lastBoostResult.collectAsStateWithLifecycle()
    val lastBoostStats by AppBoosterManager.lastBoostStats.collectAsStateWithLifecycle()
    val floatingWidgetActive by AppBoosterManager.floatingWidgetActive.collectAsStateWithLifecycle()
    val heavyApps by AppBoosterManager.heavyApps.collectAsStateWithLifecycle()
    val lastActionFeedback by AppBoosterManager.lastActionFeedback.collectAsStateWithLifecycle()

    val animatedGaugeProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = (ramPercent / 100f).coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 650, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "animatedGaugeProgress"
    )

    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val cardBgColor = if (isLight) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    val cardShape = RoundedCornerShape(22.dp)
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.25f else 0.4f))
    val cardElevation = CardDefaults.cardElevation(defaultElevation = if (isLight) 1.dp else 0.dp)

    LaunchedEffect(Unit) {
        AppBoosterManager.refreshMemoryStats(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "App Booster & RAM Cleaner",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Memory Usage: $ramPercent% (${availRam}MB Free)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ramPercent > 80) MaterialTheme.colorScheme.error else Color(0xFF10B981)
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
                            AppBoosterManager.refreshMemoryStats(context)
                        },
                        enabled = !isScanning
                    ) {
                        if (isScanning) {
                            GooglePlayCompactLinearProgress(width = 24.dp, height = 3.5.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Stats")
                        }
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
            // Feedback banner
            item {
                AnimatedVisibility(
                    visible = lastBoostResult != null || lastActionFeedback != null,
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
                                text = lastBoostResult ?: lastActionFeedback ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 1. ONE-TAP RAM OPTIMIZATION CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ram_optimization_card"),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = cardBorder,
                    elevation = cardElevation
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "RAM Performance Status",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "${totalRam}MB Total",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Sleek Google Play Style Linear RAM Usage Meter
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = "$ramPercent%",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "RAM Used",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            GooglePlayLinearProgressBar(
                                progress = animatedGaugeProgress,
                                modifier = Modifier.fillMaxWidth(),
                                height = 10.dp,
                                color = if (ramPercent > 80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        // Active Boost Stage Feedback during optimization
                        AnimatedVisibility(
                            visible = isBoosting && boostStage != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GooglePlayLinearProgressBar(
                                    progress = boostProgress,
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 6.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(
                                    text = boostStage ?: "Optimizing...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Accurate Memory Metrics Breakdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "${availRam} MB", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(text = "Available RAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "${totalRam - availRam} MB", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(text = "Used RAM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Detailed Boost Success Metrics Card
                        if (lastBoostStats != null && !isBoosting) {
                            val stats = lastBoostStats!!
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                        Text("Reclaimed ${stats.freedMb} MB Memory", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = Color(0xFF10B981))
                                    }
                                    Text(
                                        text = "Usage dropped from ${stats.beforePercent}% (${stats.beforeUsedMb} MB) to ${stats.afterPercent}% (${stats.afterUsedMb} MB)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 1-Tap Boost Button adhering to RULE[AGENTS_md]
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                SoundEffectManager.playClick()
                                AppBoosterManager.optimizeRam(context)
                            },
                            enabled = !isBoosting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("boost_ram_now_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isBoosting) {
                                GooglePlayCompactLinearProgress(
                                    width = 30.dp,
                                    height = 4.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Optimizing RAM...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Boost RAM Now", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 3. FLOATING WIDGET SUPPORT CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("floating_widget_card"),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = cardBorder,
                    elevation = cardElevation
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(text = "Floating Boost Shortcut", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(text = "Show 1-tap floating widget overlay for instant cleaning", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        ArworaAnimatedSwitch(
                            checked = floatingWidgetActive,
                            onCheckedChange = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                SoundEffectManager.playClick()
                                AppBoosterManager.toggleFloatingWidget(it)
                            },
                            modifier = Modifier.testTag("floating_widget_switch")
                        )
                    }
                }
            }

            // 2. SMART APP FREEZER / KILLER CARD
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_freezer_card"),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
                    border = cardBorder,
                    elevation = cardElevation
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "Smart App Freezer & Killer",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "${heavyApps.size} Processes",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "Hibernate background memory-heavy apps with 1 tap. Essential system services are safely whitelisted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            heavyApps.forEach { app ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(text = app.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                if (app.isSystem) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "System Safe",
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${app.memoryUsageMb} MB • CPU: ${app.cpuUsagePercent}%${if (app.isHibernate) " • [Hibernated]" else ""}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (app.isHibernate) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        if (app.isSystem) {
                                            Text(
                                                text = "Protected",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        } else if (app.isHibernate) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF10B981).copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "Hibernated",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF10B981),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else {
                                            FilledTonalButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                    SoundEffectManager.playClick()
                                                    AppBoosterManager.hibernateApp(app.packageName)
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                                    contentColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Text("Hibernate", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
