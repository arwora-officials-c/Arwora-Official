package com.arwora.feature.system.wifi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.arwora.ui.components.ArworaDesignTokens

@Composable
fun WifiSectionCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBgColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    statusBadge: (@Composable () -> Unit)? = null,
    actionButton: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(ArworaDesignTokens.IconBadgeSize)
                        .clip(ArworaDesignTokens.IconBadgeShape)
                        .background(iconBgColor)
                        .border(
                            width = 1.dp,
                            color = iconTint.copy(alpha = 0.25f),
                            shape = ArworaDesignTokens.IconBadgeShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
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
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (statusBadge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    statusBadge()
                }

                if (actionButton != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    actionButton()
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
