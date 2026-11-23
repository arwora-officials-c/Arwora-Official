package com.arwora.feature.system.installassistant.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arwora.feature.system.installassistant.data.SmartInstallAssistantManager
import com.arwora.feature.system.installassistant.model.InstalledAppAssistantRecord
import com.arwora.ui.components.ArworaDesignTokens
import com.arwora.ui.components.ArworaPillBadge
import com.arwora.ui.components.ArworaTonalIconBadge
import com.arwora.utils.SoundEffectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads an application icon asynchronously on [Dispatchers.IO] capped at 96x96px
 * to keep scrolling smooth and memory usage low on budget devices.
 */
@Composable
fun AppPackageIcon(
    packageName: String,
    contentDescription: String?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            loadScaledPackageIcon(context, packageName)
        }
    }

    val shape = RoundedCornerShape(12.dp)
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            modifier = modifier
                .size(size)
                .clip(shape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

private fun loadScaledPackageIcon(context: Context, packageName: String): ImageBitmap? {
    return try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        drawable.toBitmap(width = 96, height = 96).asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

/**
 * System Dashboard entry card for the Smart App Installation Assistant.
 * Uses [ArworaDesignTokens] to strictly match all standard System Dashboard cards.
 */
@Composable
fun SmartInstallAssistantCard(
    onOpenAssistant: (initialPackage: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val records by SmartInstallAssistantManager.records.collectAsStateWithLifecycle()
    val isLoading by SmartInstallAssistantManager.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        SmartInstallAssistantManager.initIfNeeded(context)
    }

    val recentPreview = records.take(3)
    val pendingCount = records.count { !it.isFullyReviewed }
    val badgeColor = if (pendingCount > 0) Color(0xFFF59E0B) else Color(0xFF10B981)
    val badgeText = when {
        records.isEmpty() && isLoading -> "Scanning"
        pendingCount > 0 -> "$pendingCount Pending"
        records.isNotEmpty() -> "All Reviewed"
        else -> "Active"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_install_assistant_card"),
        shape = ArworaDesignTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = ArworaDesignTokens.cardContainerColor()),
        border = ArworaDesignTokens.cardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = ArworaDesignTokens.cardElevationDp())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        SoundEffectManager.playClick()
                        onOpenAssistant(null)
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArworaTonalIconBadge(
                    icon = Icons.Default.InstallMobile,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 46.dp,
                    contentDescription = "Smart App Installation Assistant"
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = "Smart App Installation Assistant",
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
                        text = "Track new installs, review permissions & setup checklist",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                ArworaPillBadge(
                    text = badgeText,
                    tint = badgeColor,
                    showDot = true,
                    modifier = Modifier.testTag("smart_install_assistant_badge")
                )
            }

            if (recentPreview.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("smart_install_assistant_recent_preview")
                ) {
                    recentPreview.forEach { item ->
                        RecentInstallPreviewRow(
                            record = item,
                            onClick = {
                                SoundEffectManager.playClick()
                                onOpenAssistant(item.packageName)
                            }
                        )
                    }
                }
            } else if (!isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = ArworaDesignTokens.InnerTileShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                    border = ArworaDesignTokens.innerTileBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Monitoring newly installed & updated apps automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    SoundEffectManager.playClick()
                    onOpenAssistant(null)
                },
                shape = ArworaDesignTokens.PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("smart_install_assistant_open_hub_button")
            ) {
                Text(
                    text = "Open Assistant Hub",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RecentInstallPreviewRow(
    record: InstalledAppAssistantRecord,
    onClick: () -> Unit
) {
    val statusColor = if (record.isFullyReviewed) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = ArworaDesignTokens.InnerTileShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
        border = ArworaDesignTokens.innerTileBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("install_preview_row_${record.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppPackageIcon(
                packageName = record.packageName,
                contentDescription = record.appName,
                size = 36.dp
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.appName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = ArworaDesignTokens.PillShape,
                        color = record.category.accentColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, record.category.accentColor.copy(alpha = 0.30f))
                    ) {
                        Text(
                            text = record.category.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = record.category.accentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = if (record.isUpdate) "Updated" else "Installed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = ArworaDesignTokens.PillShape,
                color = statusColor.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.30f))
            ) {
                Text(
                    text = record.statusBadgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Inspect ${record.appName}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
