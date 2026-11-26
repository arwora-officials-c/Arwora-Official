package com.arwora.feature.system.connect.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arwora.feature.system.connect.model.P2pMessageRetentionPolicy
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.utils.SoundEffectManager

/**
 * Reusable Arwora Connect configuration panel controlling automatic deletion of P2P messages
 * after 24 hours or 7 days (and optional peer auto-reconnect inside the settings modal).
 */
@Composable
fun ArworaConnectSettingsPanel(
    policy: P2pMessageRetentionPolicy,
    lastSelectedDuration: P2pMessageRetentionPolicy,
    onSelectPolicy: (P2pMessageRetentionPolicy) -> Unit,
    modifier: Modifier = Modifier,
    showOuterCard: Boolean = true,
    includeAutoReconnect: Boolean = false,
    autoReconnectEnabled: Boolean = true,
    onToggleAutoReconnect: ((Boolean) -> Unit)? = null
) {
    val isAutoDeleteEnabled = policy != P2pMessageRetentionPolicy.OFF
    val accentTint = if (isAutoDeleteEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (showOuterCard) 16.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaTonalIconBadge(
                    icon = Icons.Default.AutoDelete,
                    tint = if (isAutoDeleteEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    size = 42.dp,
                    contentDescription = "Arwora Connect Settings"
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Arwora Connect Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "P2P Message Auto-Delete",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ArworaPillBadge(
                    text = policy.shortBadge,
                    tint = if (isAutoDeleteEnabled) Color(0xFF10B981) else accentTint,
                    showDot = isAutoDeleteEnabled,
                    modifier = Modifier.testTag("connect_retention_badge")
                )
            }

            Surface(
                shape = ArworaDesignTokens.InnerTileShape,
                color = if (isAutoDeleteEnabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f)
                },
                border = if (isAutoDeleteEnabled) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                } else {
                    ArworaDesignTokens.innerTileBorder()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (isAutoDeleteEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "Auto-Delete P2P Messages",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = policy.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("connect_retention_status_text")
                        )
                    }
                    Switch(
                        checked = isAutoDeleteEnabled,
                        onCheckedChange = { enable ->
                            SoundEffectManager.playClick()
                            if (enable) {
                                val target = lastSelectedDuration.takeIf { it != P2pMessageRetentionPolicy.OFF }
                                    ?: P2pMessageRetentionPolicy.HOURS_24
                                onSelectPolicy(target)
                            } else {
                                onSelectPolicy(P2pMessageRetentionPolicy.OFF)
                            }
                        },
                        modifier = Modifier.testTag("connect_auto_delete_switch")
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Delete messages after",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RetentionOptionButton(
                        label = P2pMessageRetentionPolicy.HOURS_24.label,
                        isSelected = policy == P2pMessageRetentionPolicy.HOURS_24,
                        onClick = {
                            SoundEffectManager.playClick()
                            onSelectPolicy(P2pMessageRetentionPolicy.HOURS_24)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("connect_retention_24h_btn")
                    )

                    RetentionOptionButton(
                        label = P2pMessageRetentionPolicy.DAYS_7.label,
                        isSelected = policy == P2pMessageRetentionPolicy.DAYS_7,
                        onClick = {
                            SoundEffectManager.playClick()
                            onSelectPolicy(P2pMessageRetentionPolicy.DAYS_7)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("connect_retention_7d_btn")
                    )
                }
            }

            if (includeAutoReconnect && onToggleAutoReconnect != null) {
                Surface(
                    shape = ArworaDesignTokens.InnerTileShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
                    border = ArworaDesignTokens.innerTileBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Auto-Reconnect Trusted Peers",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Silently restore connections when paired peers return on Wi-Fi",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoReconnectEnabled,
                            onCheckedChange = { enabled ->
                                SoundEffectManager.playClick()
                                onToggleAutoReconnect(enabled)
                            },
                            modifier = Modifier.testTag("connect_dialog_auto_reconnect_switch")
                        )
                    }
                }
            }
        }
    }

    if (showOuterCard) {
        Card(
            shape = ArworaDesignTokens.CardShape,
            colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
            border = ArworaDesignTokens.cardBorder(),
            elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp()),
            modifier = modifier.fillMaxWidth()
        ) {
            content()
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun RetentionOptionButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (isSelected) {
        Button(
            onClick = onClick,
            shape = ArworaDesignTokens.InnerTileShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = modifier.heightIn(min = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = ArworaDesignTokens.InnerTileShape,
            border = ArworaDesignTokens.innerTileBorder(),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = modifier.heightIn(min = 48.dp)
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ArworaConnectSettingsDialog(
    policy: P2pMessageRetentionPolicy,
    lastSelectedDuration: P2pMessageRetentionPolicy,
    autoReconnectEnabled: Boolean,
    onSelectPolicy: (P2pMessageRetentionPolicy) -> Unit,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .padding(vertical = 16.dp)
                .testTag("connect_settings_dialog"),
            shape = ArworaDesignTokens.CardShape,
            color = MaterialTheme.colorScheme.surface,
            border = ArworaDesignTokens.cardBorder(),
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ArworaConnectSettingsPanel(
                    policy = policy,
                    lastSelectedDuration = lastSelectedDuration,
                    onSelectPolicy = onSelectPolicy,
                    showOuterCard = false,
                    includeAutoReconnect = true,
                    autoReconnectEnabled = autoReconnectEnabled,
                    onToggleAutoReconnect = onToggleAutoReconnect
                )

                Button(
                    onClick = {
                        SoundEffectManager.playClick()
                        onDismiss()
                    },
                    shape = ArworaDesignTokens.PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("connect_settings_done_btn")
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
