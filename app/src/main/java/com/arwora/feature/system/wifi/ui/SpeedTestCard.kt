package com.arwora.feature.system.wifi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.feature.system.wifi.speedtest.SpeedTestPhase
import com.arwora.feature.system.wifi.speedtest.SpeedTestState
import java.util.Locale

@Composable
fun SpeedTestCard(
    speedTestState: SpeedTestState,
    networkInfo: com.arwora.feature.system.wifi.NetworkInfo,
    onStartTest: () -> Unit,
    onCancelTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = speedTestState.phase in listOf(
        SpeedTestPhase.PING,
        SpeedTestPhase.DOWNLOAD,
        SpeedTestPhase.UPLOAD
    )

    val canRunTest = networkInfo.isWifiConnected || networkInfo.isMobileDataConnected

    val connectionStatus = when {
        networkInfo.isWifiConnected -> "Wi-Fi available"
        networkInfo.isMobileDataConnected -> "Mobile data available"
        else -> "No connection available"
    }

    val unavailableMessage = if (!canRunTest) "Speed Benchmark unavailable — connect to Wi-Fi or mobile data" else null

    val phaseLabel = when (speedTestState.phase) {
        SpeedTestPhase.IDLE -> if (canRunTest) "Ready to benchmark" else "Connect to network"
        SpeedTestPhase.PING -> "Measuring Ping & Jitter..."
        SpeedTestPhase.DOWNLOAD -> "Testing Downstream Throughput..."
        SpeedTestPhase.UPLOAD -> "Testing Upstream Throughput..."
        SpeedTestPhase.COMPLETED -> "Test Complete"
        SpeedTestPhase.ERROR -> "Test Failed"
    }

    val badgeColor = when (speedTestState.phase) {
        SpeedTestPhase.COMPLETED -> Color(0xFF10B981)
        SpeedTestPhase.ERROR -> MaterialTheme.colorScheme.error
        SpeedTestPhase.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    // Metric value resolution with proper placeholders
    val displayDownload = when {
        speedTestState.phase in listOf(SpeedTestPhase.DOWNLOAD, SpeedTestPhase.UPLOAD, SpeedTestPhase.COMPLETED) ->
            String.format(Locale.US, "%.1f", speedTestState.downloadMbps)
        else -> "—"
    }

    val displayUpload = when {
        speedTestState.phase in listOf(SpeedTestPhase.UPLOAD, SpeedTestPhase.COMPLETED) ->
            String.format(Locale.US, "%.1f", speedTestState.uploadMbps)
        else -> "—"
    }

    val displayPingJitter = when {
        speedTestState.phase in listOf(SpeedTestPhase.PING, SpeedTestPhase.DOWNLOAD, SpeedTestPhase.UPLOAD, SpeedTestPhase.COMPLETED) -> {
            if (speedTestState.pingMs > 0 || speedTestState.jitterMs > 0) {
                "${speedTestState.pingMs} / ${speedTestState.jitterMs}"
            } else if (speedTestState.phase == SpeedTestPhase.PING) {
                "..."
            } else {
                "0 / 0"
            }
        }
        else -> "— / —"
    }

    val displayPacketLoss = when {
        speedTestState.phase in listOf(SpeedTestPhase.PING, SpeedTestPhase.DOWNLOAD, SpeedTestPhase.UPLOAD, SpeedTestPhase.COMPLETED) ->
            "${speedTestState.packetLossPercent}"
        else -> "—"
    }

    WifiSectionCard(
        title = "Speed Benchmark",
        description = "Measure real-time bandwidth, connection latency, jitter stability, and packet loss",
        icon = Icons.Default.Speed,
        iconTint = Color(0xFF3B82F6),
        statusBadge = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
            ) {
                Text(
                    text = if (isRunning) "Testing" else if (speedTestState.phase == SpeedTestPhase.COMPLETED) "Completed" else if (speedTestState.phase == SpeedTestPhase.ERROR) "Error" else "Ready",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        modifier = modifier
    ) {
        // Active Progress Bar and Phase Info
        if (isRunning || speedTestState.phase == SpeedTestPhase.ERROR || !canRunTest) {
            if (speedTestState.phase == SpeedTestPhase.ERROR || unavailableMessage != null) {
                Text(
                    text = unavailableMessage ?: speedTestState.errorMessage ?: "Test failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (unavailableMessage != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                LinearProgressIndicator(
                    progress = { speedTestState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = phaseLabel,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${(speedTestState.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 2x2 Clean Metrics Grid
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpeedMetricBox(
                    label = "DOWNLOAD",
                    value = displayDownload,
                    unit = "Mbps",
                    icon = Icons.Default.ArrowDownward,
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                SpeedMetricBox(
                    label = "UPLOAD",
                    value = displayUpload,
                    unit = "Mbps",
                    icon = Icons.Default.ArrowUpward,
                    color = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpeedMetricBox(
                    label = "PING / JITTER",
                    value = displayPingJitter,
                    unit = "ms",
                    icon = Icons.Default.Timer,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
                SpeedMetricBox(
                    label = "PACKET LOSS",
                    value = displayPacketLoss,
                    unit = "%",
                    icon = Icons.Default.NetworkCheck,
                    color = Color(0xFF8B5CF6),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Recent Result Summary Banner
        if (speedTestState.phase == SpeedTestPhase.COMPLETED) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF10B981).copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recent Result Summary",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                            color = Color(0xFF10B981)
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "%.1f Mbps Down • %.1f Mbps Up • %d ms Latency • Ideal for 4K Streaming & Conferencing",
                                speedTestState.downloadMbps,
                                speedTestState.uploadMbps,
                                speedTestState.pingMs
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Large Primary Run Button
        if (isRunning) {
            Button(
                onClick = onCancelTest,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cancel Speed Benchmark", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (speedTestState.phase == SpeedTestPhase.IDLE || speedTestState.phase == SpeedTestPhase.COMPLETED) {
                    Text(
                        text = connectionStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (canRunTest) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                Button(
                    onClick = onStartTest,
                    enabled = canRunTest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (speedTestState.phase == SpeedTestPhase.COMPLETED) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (speedTestState.phase == SpeedTestPhase.COMPLETED) "Retest Bandwidth" else "Run Speed Test",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedMetricBox(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
