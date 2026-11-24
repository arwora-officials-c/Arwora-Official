package com.arwora.feature.system.battery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.feature.system.battery.service.BatteryMonitorManager
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.ui.screens.SystemCard
import com.arwora.utils.SoundEffectManager
import java.util.Locale

@Composable
fun BatteryMonitorDashboardCard(
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val batteryLevel by BatteryMonitorManager.batteryLevel.collectAsStateWithLifecycle()
    val isCharging by BatteryMonitorManager.isCharging.collectAsStateWithLifecycle()
    val temperature by BatteryMonitorManager.batteryTemperature.collectAsStateWithLifecycle()
    val voltage by BatteryMonitorManager.batteryVoltage.collectAsStateWithLifecycle()
    val batteryHealth by BatteryMonitorManager.batteryHealth.collectAsStateWithLifecycle()
    val dischargeRateMahPerHour by BatteryMonitorManager.dischargeRateMahPerHour.collectAsStateWithLifecycle()
    val isCurrentAvailable by BatteryMonitorManager.isCurrentAvailable.collectAsStateWithLifecycle()
    val diagnostics by BatteryMonitorManager.deepSleepDiagnostics.collectAsStateWithLifecycle()

    val healthTint = when (batteryHealth) {
        "Good" -> Color(0xFF10B981)
        "Cold" -> Color(0xFF3B82F6)
        "Overheat", "Over Voltage", "Dead", "Failure" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val chargeTint = when {
        isCharging -> Color(0xFF10B981)
        batteryLevel <= 20 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val drainLabel = if (isCharging) "Charge Rate" else "Power Drain"
    val drainValueText = if (isCurrentAvailable && dischargeRateMahPerHour > 0) {
        "$dischargeRateMahPerHour mAh/h"
    } else {
        "0 mAh/h"
    }

    val deepSleepFormatted = BatteryMonitorManager.formatDurationCompact(diagnostics.deepSleepMs)
    val awakeFormatted = BatteryMonitorManager.formatDurationCompact(diagnostics.awakeUptimeMs)
    val voltageFormatted = if (voltage >= 1000) {
        String.format(Locale.US, "%.2fV", voltage / 1000f)
    } else if (voltage > 0) {
        "${voltage}mV"
    } else {
        "--"
    }
    val tempFormatted = if (temperature > 0f) {
        String.format(Locale.US, "%.1f°C", temperature)
    } else {
        "--"
    }

    SystemCard(
        modifier = modifier.testTag("system_battery_monitor_card"),
        onClick = onOpenDetails
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaTonalIconBadge(
                    icon = if (isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryStd,
                    tint = chargeTint,
                    size = 46.dp,
                    iconSize = 22.dp,
                    contentDescription = "Battery Monitor & Deep Sleep"
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Battery Monitor & Deep Sleep",
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
                        text = "Real-time power drain, battery health & CPU deep sleep tracking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                ArworaPillBadge(
                    text = batteryHealth,
                    tint = healthTint,
                    showDot = true,
                    modifier = Modifier.testTag("battery_monitor_health_badge")
                )
            }

            // Live battery telemetry tiles: %, Temp, Voltage, Health
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BatteryStatTile(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("battery_stat_level"),
                    label = if (isCharging) "Charging" else "Level",
                    value = "$batteryLevel%",
                    valueColor = chargeTint
                )
                BatteryStatTile(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("battery_stat_temp"),
                    label = "Temp",
                    value = tempFormatted,
                    valueColor = if (temperature >= 42f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                BatteryStatTile(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("battery_stat_voltage"),
                    label = "Voltage",
                    value = voltageFormatted,
                    valueColor = MaterialTheme.colorScheme.onSurface
                )
                BatteryStatTile(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("battery_stat_health"),
                    label = "Health",
                    value = batteryHealth,
                    valueColor = healthTint
                )
            }

            // Deep Sleep State Tracker block
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ArworaDesignTokens.InnerTileShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                    .border(ArworaDesignTokens.innerTileBorder(), ArworaDesignTokens.InnerTileShape)
                    .padding(12.dp)
                    .testTag("battery_deep_sleep_tracker"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "CPU Deep Sleep Tracker",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${diagnostics.deepSleepPercentage}% Deep Sleep",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }

                // Linear ratio track
                val ratio = (diagnostics.deepSleepPercentage / 100f).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(ArworaDesignTokens.PillShape)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .clip(ArworaDesignTokens.PillShape)
                            .background(Color(0xFF10B981))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Deep Sleep: $deepSleepFormatted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Awake: $awakeFormatted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = ArworaDesignTokens.cardBorderColor().copy(alpha = 0.55f)
            )

            // Power Drain / Discharge Rate & Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = if (isCharging) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = drainLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = drainValueText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("battery_drain_rate_value")
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            SoundEffectManager.playClick(context)
                            BatteryMonitorManager.refreshStats(context, emitLog = false)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("battery_monitor_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh battery telemetry",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            SoundEffectManager.playClick(context)
                            onOpenDetails()
                        },
                        shape = ArworaDesignTokens.PillShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("battery_monitor_open_button")
                    ) {
                        Text(
                            text = "Diagnostics",
                            style = MaterialTheme.typography.labelMedium,
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

@Composable
private fun BatteryStatTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(ArworaDesignTokens.InnerTileShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .border(ArworaDesignTokens.innerTileBorder(), ArworaDesignTokens.InnerTileShape)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
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
