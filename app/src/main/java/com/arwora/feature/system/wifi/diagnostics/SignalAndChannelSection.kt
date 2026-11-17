package com.arwora.feature.system.wifi.diagnostics

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.feature.system.wifi.ChannelCongestion
import com.arwora.feature.system.wifi.WifiSignalInfo
import com.arwora.ui.components.ExpressiveWavyLinearProgressIndicator

@Composable
fun SignalAndChannelSection(
    signalInfo: WifiSignalInfo,
    channels: List<ChannelCongestion>,
    recommendedChannel: String,
    onRefreshSignal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalWifi4Bar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Signal & Channel Analyzer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "RSSI diagnostics & interference checker",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onRefreshSignal) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh Signal",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. SIGNAL STRENGTH / RSSI METER
            val statusColor = when {
                signalInfo.isDeadZone -> MaterialTheme.colorScheme.error
                signalInfo.rssiDbm >= -60 -> Color(0xFF10B981) // Strong green
                signalInfo.rssiDbm >= -75 -> Color(0xFF0EA5E9) // Good blue
                else -> Color(0xFFF59E0B) // Weak amber
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Signal Strength (RSSI)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = statusColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = signalInfo.qualityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "${signalInfo.rssiDbm} dBm",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = statusColor
                            )
                            Text(
                                text = "Current RSSI Value",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${signalInfo.signalPercent}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Signal Quality Score",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Gauge Bar
                    ExpressiveWavyLinearProgressIndicator(
                        progress = (signalInfo.signalPercent / 100f).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                        color = statusColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        height = 10.dp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Scale markers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "-90 dBm (Dead Zone)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "-75 dBm (Weak)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = Color(0xFFF59E0B)
                        )
                        Text(
                            text = "-30 dBm (Strong)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = Color(0xFF10B981)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Area assessment badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (signalInfo.isDeadZone) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (signalInfo.isDeadZone) MaterialTheme.colorScheme.error else Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (signalInfo.isDeadZone) {
                                "Dead-Zone Detected: Signal drops below -85 dBm. Move closer to the router or install an AP extender."
                            } else if (signalInfo.rssiDbm >= -60) {
                                "Strong Signal Zone: Optimal distance from router. Suitable for high-speed downloads & low-latency gaming."
                            } else {
                                "Fair Signal Zone: Good connection. Minor obstruction or distance from the router."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. CHANNEL ANALYZER & INTERFERENCE CHECKER
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Channel & Spectrum Analyzer",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Channel ${signalInfo.channel} (${signalInfo.band})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Current Frequency: ${signalInfo.frequencyMhz} MHz • Standard: ${signalInfo.standard}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Channel congestion bars
                    Text(
                        text = "Nearby Channel Congestion",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    channels.forEach { ch ->
                        val congestionColor = when (ch.congestionLevel) {
                            "Low" -> Color(0xFF10B981)
                            "Moderate" -> Color(0xFFF59E0B)
                            else -> MaterialTheme.colorScheme.error
                        }

                        Column(modifier = Modifier.padding(vertical = 3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Ch ${ch.channel} (${ch.band})",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        fontWeight = if (ch.isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (ch.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (ch.isCurrent) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "Current",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "${ch.congestionLevel} Congestion",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.SemiBold,
                                    color = congestionColor
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            ExpressiveWavyLinearProgressIndicator(
                                progress = (ch.congestionScore / 100f).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth(),
                                color = congestionColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                height = 8.dp,
                                amplitude = 1.8.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Channel Recommendation
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "Optimization Recommendation",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = recommendedChannel,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
