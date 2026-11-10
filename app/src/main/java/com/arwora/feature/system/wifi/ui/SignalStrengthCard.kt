package com.arwora.feature.system.wifi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.feature.system.wifi.WifiSignalInfo

@Composable
fun SignalStrengthCard(
    signalInfo: WifiSignalInfo,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val qualityColor = when {
        signalInfo.isDeadZone -> Color(0xFFEF4444)
        signalInfo.rssiDbm >= -65 -> Color(0xFF10B981)
        signalInfo.rssiDbm >= -78 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    WifiSectionCard(
        title = "Signal Strength / RSSI",
        description = "Real-time dBm signal reception level, percentage meter, and dead-zone detection",
        icon = Icons.Default.SignalCellularAlt,
        iconTint = qualityColor,
        statusBadge = {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = qualityColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, qualityColor.copy(alpha = 0.3f))
            ) {
                Text(
                    text = signalInfo.qualityLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = qualityColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        actionButton = {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh Signal",
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        modifier = modifier
    ) {
        // Main Gauge Readout
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${signalInfo.rssiDbm}",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                                color = qualityColor
                            )
                            Text(
                                text = "dBm",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            text = "RSSI Received Signal Strength Indicator",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Percentage Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = qualityColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${signalInfo.signalPercent}% Quality",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = qualityColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress gauge bar
                LinearProgressIndicator(
                    progress = { signalInfo.signalPercent / 100f },
                    color = qualityColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                Spacer(modifier = Modifier.height(6.dp))

                // dBm Scale markers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "-90 dBm (Dead Zone)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "-65 dBm (Good)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "-30 dBm (Max)", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Spec Grid: Band, Frequency, Protocol Standard, Channel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpecPill(label = "BAND", value = signalInfo.band, modifier = Modifier.weight(1f))
            SpecPill(label = "FREQUENCY", value = "${signalInfo.frequencyMhz} MHz", modifier = Modifier.weight(1f))
            SpecPill(label = "CHANNEL", value = "Ch ${signalInfo.channel}", modifier = Modifier.weight(1f))
            SpecPill(label = "PROTOCOL", value = signalInfo.standard.substringBefore(" ("), modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dead Zone Alert Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = if (signalInfo.isDeadZone) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(
                1.dp,
                if (signalInfo.isDeadZone) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (signalInfo.isDeadZone) Icons.Default.Warning else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (signalInfo.isDeadZone) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (signalInfo.isDeadZone) {
                        "High signal attenuation detected (< -85 dBm). You are in a dead-zone area subject to packet dropouts."
                    } else if (signalInfo.rssiDbm >= -65) {
                        "Optimal signal level. Minimal packet degradation and high data rate transmission supported."
                    } else {
                        "Moderate signal level. Suitable for everyday browsing and video streaming."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SpecPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}
