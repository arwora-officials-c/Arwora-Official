package com.arwora.feature.system.wifi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.feature.system.wifi.NetworkInfo
import com.arwora.feature.system.wifi.WifiSignalInfo
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge

@Composable
fun WifiOverviewCard(
    networkInfo: NetworkInfo,
    signalInfo: WifiSignalInfo,
    deviceCount: Int,
    onRefresh: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = networkInfo.isConnected
    val statusBadgeColor = if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.error

    val signalQualityColor = when {
        signalInfo.isDeadZone -> Color(0xFFEF4444)
        signalInfo.rssiDbm >= -65 -> Color(0xFF10B981)
        signalInfo.rssiDbm >= -78 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Icon + Title + Status Chip + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaTonalIconBadge(
                    icon = if (isConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    tint = statusBadgeColor,
                    modifier = Modifier,
                    size = ArworaDesignTokens.IconBadgeSize,
                    iconSize = 22.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isConnected) networkInfo.ssid.ifBlank { "Connected Wi-Fi" } else "Not Connected",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.2).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isConnected) "BSSID: ${networkInfo.bssid} • /${networkInfo.subnetCidr}" else "No active WLAN connection",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Connection status pill chip
                ArworaPillBadge(
                    text = if (isConnected) "Connected" else "Offline",
                    tint = statusBadgeColor,
                    showDot = true
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Compact Metric Cards (2x2 Grid)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OverviewStatTile(
                        icon = Icons.Default.Devices,
                        iconTint = Color(0xFF10B981),
                        label = "CONNECTED HOSTS",
                        value = "$deviceCount",
                        subtext = if (deviceCount > 0) "Active on Subnet" else "Ready to scan",
                        badge = if (deviceCount > 0) "Active" else "Idle",
                        badgeColor = if (deviceCount > 0) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    OverviewStatTile(
                        icon = Icons.Default.Speed,
                        iconTint = Color(0xFF3B82F6),
                        label = "CURRENT SPEED",
                        value = "${networkInfo.linkSpeedMbps}",
                        unit = "Mbps",
                        subtext = "WLAN Link Speed",
                        badge = if (networkInfo.linkSpeedMbps > 100) "Fast" else "Standard",
                        badgeColor = Color(0xFF3B82F6),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OverviewStatTile(
                        icon = Icons.Default.SignalCellularAlt,
                        iconTint = signalQualityColor,
                        label = "SIGNAL QUALITY",
                        value = "${signalInfo.rssiDbm}",
                        unit = "dBm",
                        subtext = "${signalInfo.signalPercent}% Reception",
                        badge = signalInfo.qualityLabel,
                        badgeColor = signalQualityColor,
                        modifier = Modifier.weight(1f)
                    )

                    OverviewStatTile(
                        icon = Icons.Default.Router,
                        iconTint = Color(0xFF8B5CF6),
                        label = "GATEWAY STATUS",
                        value = networkInfo.gateway.ifBlank { "0.0.0.0" },
                        subtext = "Local: ${networkInfo.localIp}",
                        badge = if (networkInfo.gateway.isNotBlank() && networkInfo.gateway != "0.0.0.0") "Online" else "Unavailable",
                        badgeColor = if (networkInfo.gateway.isNotBlank() && networkInfo.gateway != "0.0.0.0") Color(0xFF8B5CF6) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Subnet: /${networkInfo.subnetCidr} • IP: ${networkInfo.localIp}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onRefresh,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp),
                        shape = ArworaDesignTokens.PillShape
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Refresh", style = MaterialTheme.typography.labelSmall)
                    }

                    OutlinedButton(
                        onClick = onExportCsv,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp),
                        shape = ArworaDesignTokens.PillShape
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewStatTile(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    unit: String? = null,
    subtext: String,
    badge: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(5.dp),
                        color = badgeColor.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (unit != null) {
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
