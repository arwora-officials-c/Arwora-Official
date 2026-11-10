package com.arwora.feature.system.wifi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.feature.system.wifi.GatewayPingResult

@Composable
fun GatewayPingCard(
    gatewayIp: String,
    pingResult: GatewayPingResult?,
    isPinging: Boolean,
    onRunPing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetIp = gatewayIp.ifBlank { "192.168.1.1" }

    val statusBadgeColor = when {
        pingResult == null -> MaterialTheme.colorScheme.onSurfaceVariant
        !pingResult.isReachable -> Color(0xFFEF4444)
        pingResult.packetLossPercent > 0 -> Color(0xFFF59E0B)
        else -> Color(0xFF10B981)
    }

    WifiSectionCard(
        title = "Gateway Ping",
        description = "Measure packet latency, connection jitter, and stability to local gateway",
        icon = Icons.Default.NetworkPing,
        iconTint = if (pingResult != null && pingResult.isReachable) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
        statusBadge = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusBadgeColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, statusBadgeColor.copy(alpha = 0.3f))
            ) {
                Text(
                    text = pingResult?.stabilityStatus?.substringBefore(" (") ?: "Untested",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = statusBadgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        actionButton = {
            Button(
                onClick = onRunPing,
                enabled = !isPinging,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = if (pingResult != null) Icons.Default.Refresh else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isPinging) "Testing..." else if (pingResult != null) "Re-ping" else "Run Ping", style = MaterialTheme.typography.labelSmall)
            }
        },
        modifier = modifier
    ) {
        if (isPinging) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Sending 4 echo probes to $targetIp...",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (pingResult != null) {
            // Stability rating headline
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = statusBadgeColor.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, statusBadgeColor.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (pingResult.isReachable && pingResult.packetLossPercent == 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = statusBadgeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = pingResult.stabilityStatus,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusBadgeColor
                        )
                        Text(
                            text = "Target: ${pingResult.gatewayIp} • 4 probes evaluated",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4 latency boxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LatencyBox(label = "MIN LATENCY", value = "${pingResult.minLatencyMs}", unit = "ms", color = Color(0xFF10B981), modifier = Modifier.weight(1f))
                LatencyBox(label = "AVG LATENCY", value = "${pingResult.avgLatencyMs}", unit = "ms", color = Color(0xFF3B82F6), modifier = Modifier.weight(1f))
                LatencyBox(label = "MAX LATENCY", value = "${pingResult.maxLatencyMs}", unit = "ms", color = Color(0xFFF59E0B), modifier = Modifier.weight(1f))
                LatencyBox(label = "PACKET LOSS", value = "${pingResult.packetLossPercent}", unit = "%", color = if (pingResult.packetLossPercent == 0) Color(0xFF10B981) else Color(0xFFEF4444), modifier = Modifier.weight(1f))
            }
        } else {
            // Prompt Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.NetworkPing,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Target Gateway: $targetIp",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap 'Run Ping' above to probe local router response times, measure jitter, and verify packet integrity.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LatencyBox(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = color
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
        }
    }
}
