package com.arwora.feature.system.process

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaMotionTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.ui.navigation.Screen
import com.arwora.ui.screens.SystemCard
import com.arwora.utils.ProcessInfo
import com.arwora.utils.ProcessWakelockManager
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun WakelockDetectorCard(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isExpanded by remember { mutableStateOf(false) }
    var processes by remember { mutableStateOf<List<ProcessInfo>>(emptyList()) }
    var isOptimizing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        processes = ProcessWakelockManager.getRunningProcesses(context)
    }

    val totalRamMb = remember(processes) {
        processes.sumOf { it.memoryMb }
    }
    val wakelockCount = remember(processes) {
        processes.count { it.hasWakelock }
    }
    val hasActiveWakelocks = wakelockCount > 0 || processes.isNotEmpty()
    val statusBadgeText = if (wakelockCount > 0) {
        "Active ($wakelockCount)"
    } else if (processes.isNotEmpty()) {
        "Active"
    } else {
        "Idle"
    }
    val statusBadgeTint = when {
        wakelockCount > 0 -> Color(0xFFF59E0B)
        processes.isNotEmpty() -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    SystemCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("system_wakelock_detector_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        SoundEffectManager.playClick(context)
                        isExpanded = !isExpanded
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaTonalIconBadge(
                    icon = Icons.Default.Bolt,
                    tint = if (hasActiveWakelocks) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 46.dp,
                    iconSize = 22.dp,
                    contentDescription = "Background & Wakelock Detector"
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Background & Wakelock Detector",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Inspect background services, RAM load & wake locks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                ArworaPillBadge(
                    text = statusBadgeText,
                    tint = statusBadgeTint,
                    showDot = true,
                    modifier = Modifier.testTag("wakelock_status_badge")
                )

                IconButton(
                    onClick = {
                        SoundEffectManager.playClick(context)
                        isExpanded = !isExpanded
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("wakelock_expand_button")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse Background & Wakelock Detector" else "Expand Background & Wakelock Detector",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CardSummaryMetric(
                    modifier = Modifier.weight(1f),
                    label = "Processes",
                    value = "${processes.size}",
                    color = MaterialTheme.colorScheme.primary
                )
                CardSummaryMetric(
                    modifier = Modifier.weight(1f),
                    label = "Active RAM",
                    value = "${String.format(Locale.US, "%.0f", totalRamMb)} MB",
                    color = MaterialTheme.colorScheme.secondary
                )
                CardSummaryMetric(
                    modifier = Modifier.weight(1f),
                    label = "Wakelocks",
                    value = "$wakelockCount",
                    color = if (wakelockCount > 0) MaterialTheme.colorScheme.error else Color(0xFF10B981)
                )
            }

            // Expanded Diagnostic Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = ArworaMotionTokens.standardExpandTransition(),
                exit = ArworaMotionTokens.standardCollapseTransition()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = ArworaDesignTokens.cardBorderColor().copy(alpha = 0.55f)
                    )

                    Text(
                        text = "Top Background RAM Consumers",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val topProcesses = remember(processes) { processes.take(3) }
                    if (topProcesses.isEmpty()) {
                        Text(
                            text = "No active third-party background consumers detected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        topProcesses.forEach { proc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ArworaDesignTokens.InnerTileShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                    .border(ArworaDesignTokens.innerTileBorder(), ArworaDesignTokens.InnerTileShape)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = proc.appName,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${proc.packageName} • ${proc.importanceLabel}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    shape = ArworaDesignTokens.PillShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "${proc.memoryMb} MB",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                SoundEffectManager.playClick(context)
                                isOptimizing = true
                                coroutineScope.launch {
                                    val count = ProcessWakelockManager.optimizeAllBackgroundProcesses(context)
                                    Toast.makeText(context, "Optimized $count processes", Toast.LENGTH_SHORT).show()
                                    processes = ProcessWakelockManager.getRunningProcesses(context)
                                    isOptimizing = false
                                }
                            },
                            enabled = !isOptimizing,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("wakelock_trim_button"),
                            shape = ArworaDesignTokens.PillShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (isOptimizing) "Optimizing..." else "Trim Processes",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = {
                                SoundEffectManager.playClick(context)
                                navController.navigate(Screen.WakelockDetector.route)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("wakelock_inspector_button"),
                            shape = ArworaDesignTokens.PillShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Full Inspector",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardSummaryMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Column(
        modifier = modifier
            .clip(ArworaDesignTokens.InnerTileShape)
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.22f), ArworaDesignTokens.InnerTileShape)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
