package com.arwora.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arwora.ui.components.ArworaAnimatedSwitch
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge

@Composable
internal fun AppLockerDashboardCard(
    isLockerEnabled: Boolean,
    hasPin: Boolean,
    totalSecured: Int,
    onToggleLocker: (Boolean) -> Unit,
    onOpenAppLocker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeGreen = Color(0xFF10B981)
    val accentColor = if (isLockerEnabled) activeGreen else MaterialTheme.colorScheme.primary
    val statusBadgeLabel = when {
        isLockerEnabled -> "Protected"
        hasPin -> "Paused"
        else -> "Setup PIN"
    }
    val subtext = if (totalSecured > 0) {
        "Secured apps monitored • $totalSecured protected"
    } else {
        "Secured apps monitored"
    }

    SystemCard(
        modifier = modifier.testTag("system_app_locker_card"),
        onClick = onOpenAppLocker
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaTonalIconBadge(
                    icon = if (isLockerEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                    tint = accentColor,
                    size = 46.dp,
                    iconSize = 22.dp,
                    contentDescription = "App Locker"
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "App Locker",
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
                        text = subtext,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                ArworaPillBadge(
                    text = statusBadgeLabel,
                    tint = accentColor,
                    showDot = true,
                    modifier = Modifier.testTag("app_locker_status_badge")
                )

                Spacer(modifier = Modifier.width(10.dp))

                ArworaAnimatedSwitch(
                    checked = isLockerEnabled,
                    onCheckedChange = onToggleLocker,
                    modifier = Modifier.testTag("app_locker_dashboard_switch")
                )
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = ArworaDesignTokens.cardBorderColor().copy(alpha = 0.55f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isLockerEnabled) {
                        "PIN & Biometric guard active"
                    } else if (hasPin) {
                        "Tap Configure or switch on to lock apps"
                    } else {
                        "Set PIN & select apps or tools to guard"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    onClick = onOpenAppLocker,
                    shape = ArworaDesignTokens.PillShape,
                    color = accentColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.32f)),
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .testTag("app_locker_configure_pill")
                ) {
                    Text(
                        text = "Configure",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
internal fun SmartScreenOffDashboardCard(
    isServiceActive: Boolean,
    delaySeconds: Long,
    onToggleService: (Boolean) -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeGreen = Color(0xFF10B981)
    val accentColor = if (isServiceActive) activeGreen else MaterialTheme.colorScheme.primary
    val delayLabel = if (delaySeconds <= 0L) "Immediate" else "${delaySeconds}s delay"
    val subtitle = if (isServiceActive) {
        "Active • Running cleanup after $delayLabel on screen-off"
    } else {
        "Automated background cleanup routines on device screen-off"
    }

    SystemCard(
        modifier = modifier.testTag("system_screen_off_optimizer_card"),
        onClick = onOpenDetails
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArworaTonalIconBadge(
                icon = Icons.Default.Bedtime,
                tint = accentColor,
                size = 46.dp,
                iconSize = 22.dp,
                contentDescription = "Smart Screen-Off Optimizer"
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Smart Screen-Off Optimizer",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.2).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            ArworaPillBadge(
                text = if (isServiceActive) "Active" else "Standby",
                tint = accentColor,
                showDot = true,
                modifier = Modifier.testTag("screen_off_optimizer_status_badge")
            )

            Spacer(modifier = Modifier.width(10.dp))

            ArworaAnimatedSwitch(
                checked = isServiceActive,
                onCheckedChange = onToggleService,
                modifier = Modifier.testTag("screen_off_optimizer_dashboard_switch")
            )
        }
    }
}

@Composable
internal fun BatteryChargingAlarmDashboardCard(
    chargingAlarmEnabled: Boolean,
    chargingThreshold: Int,
    lowAlarmEnabled: Boolean,
    lowThreshold: Int,
    onToggleChargingAlarm: (Boolean) -> Unit,
    onSelectThreshold: (Int) -> Unit,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeGreen = Color(0xFF10B981)
    val isAnyGuardActive = chargingAlarmEnabled || lowAlarmEnabled
    val accentColor = if (isAnyGuardActive) activeGreen else MaterialTheme.colorScheme.primary
    val presetThresholds = listOf(80, 90, 100)

    SystemCard(
        modifier = modifier.testTag("system_battery_alarm_card"),
        onClick = onOpenDetails
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaTonalIconBadge(
                    icon = if (isAnyGuardActive) Icons.Default.NotificationsActive else Icons.Default.BatteryChargingFull,
                    tint = accentColor,
                    size = 46.dp,
                    iconSize = 22.dp,
                    contentDescription = "Battery Charging Alarm"
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Battery Charging Alarm",
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
                        text = "Target: $chargingThreshold% high • $lowThreshold% low alert",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("battery_alarm_threshold_summary")
                    )
                }

                ArworaPillBadge(
                    text = if (isAnyGuardActive) "Guard On" else "Standby",
                    tint = accentColor,
                    showDot = true,
                    modifier = Modifier.testTag("battery_alarm_status_badge")
                )

                Spacer(modifier = Modifier.width(10.dp))

                ArworaAnimatedSwitch(
                    checked = chargingAlarmEnabled,
                    onCheckedChange = onToggleChargingAlarm,
                    modifier = Modifier.testTag("battery_alarm_dashboard_switch")
                )
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = ArworaDesignTokens.cardBorderColor().copy(alpha = 0.55f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Limit",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                presetThresholds.forEach { preset ->
                    val isSelected = chargingThreshold == preset
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectThreshold(preset) },
                        label = {
                            Text(
                                text = "$preset%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        },
                        shape = ArworaDesignTokens.PillShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = ArworaDesignTokens.cardBorderColor(),
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("battery_alarm_chip_$preset")
                    )
                }
            }
        }
    }
}
