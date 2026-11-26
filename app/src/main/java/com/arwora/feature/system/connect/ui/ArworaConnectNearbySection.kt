package com.arwora.feature.system.connect.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.feature.system.connect.model.DiscoveredNearbyPeer
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.utils.SoundEffectManager

/**
 * Zero-Config Nearby Devices Found radar banner & one-tap connect list
 * matching [ArworaDesignTokens] uniform 1.dp card borders and minimal bold action buttons.
 */
@Composable
fun NearbyDevicesRadarCard(
    nearbyPeers: List<DiscoveredNearbyPeer>,
    isScanning: Boolean,
    isHubOnline: Boolean,
    connectingPeerIds: Set<String>,
    onOneTapConnect: (DiscoveredNearbyPeer) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFF38BDF8)

    Card(
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp()),
        modifier = modifier
            .fillMaxWidth()
            .testTag("connect_nearby_radar_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Subtle Radar Scanning Header Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadarPulseBadge(
                    isScanning = isScanning && isHubOnline,
                    tint = accent,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nearby Devices Found",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            !isHubOnline -> "Enable Arwora Connect to scan local Wi-Fi automatically"
                            nearbyPeers.isNotEmpty() -> "Tap Connect to pair without a 6-digit code or QR"
                            else -> "Scanning local Wi-Fi (_arworaconnect._tcp.)..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ArworaPillBadge(
                    text = when {
                        nearbyPeers.isNotEmpty() -> "${nearbyPeers.size} Found"
                        isScanning && isHubOnline -> "Scanning"
                        else -> "Idle"
                    },
                    tint = if (nearbyPeers.isNotEmpty()) Color(0xFF10B981) else accent,
                    showDot = isScanning && isHubOnline
                )
            }

            if (nearbyPeers.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("connect_nearby_list")
                ) {
                    nearbyPeers.forEach { peer ->
                        val isConnecting = connectingPeerIds.contains(peer.deviceId)
                        Surface(
                            shape = ArworaDesignTokens.InnerTileShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
                            border = ArworaDesignTokens.innerTileBorder(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("connect_nearby_item_${peer.deviceId}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = peer.deviceName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${peer.hostIp}:${peer.port}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = {
                                        SoundEffectManager.playClick()
                                        onOneTapConnect(peer)
                                    },
                                    enabled = !isConnecting,
                                    shape = ArworaDesignTokens.PillShape,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("connect_nearby_btn_${peer.deviceId}")
                                ) {
                                    if (isConnecting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Connecting",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    } else {
                                        Text(
                                            text = "Connect",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
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
}

@Composable
private fun RadarPulseBadge(
    isScanning: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "nearby_radar_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_progress"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerRadius = size.minDimension * 0.28f
            drawCircle(
                color = tint.copy(alpha = 0.14f),
                radius = size.minDimension * 0.45f
            )
            if (isScanning) {
                val r1 = centerRadius + (size.minDimension * 0.22f) * pulse
                val a1 = (1f - pulse).coerceIn(0f, 1f) * 0.55f
                drawCircle(
                    color = tint.copy(alpha = a1),
                    radius = r1,
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }
        }
        Icon(
            imageVector = Icons.Default.WifiTethering,
            contentDescription = "Nearby Radar",
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
