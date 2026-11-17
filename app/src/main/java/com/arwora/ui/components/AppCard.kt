package com.arwora.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Unified Design System Tokens matching the Wi-Fi Controller UI/UX language across
 * White, Light, and Dark themes in Arwora Hyper Toolkit.
 */
object ArworaDesignTokens {
    val CardCornerRadius: Dp = 24.dp
    val InnerTileCornerRadius: Dp = 14.dp
    val IconBadgeCornerRadius: Dp = 14.dp

    val CardShape: Shape = RoundedCornerShape(CardCornerRadius)
    val InnerTileShape: Shape = RoundedCornerShape(InnerTileCornerRadius)
    val IconBadgeShape: Shape = RoundedCornerShape(IconBadgeCornerRadius)
    val PillShape: Shape = RoundedCornerShape(50)

    val IconBadgeSize: Dp = 44.dp
    val PrimaryButtonHeight: Dp = 54.dp
    val SecondaryButtonHeight: Dp = 46.dp

    fun isLightTheme(colorScheme: ColorScheme): Boolean = colorScheme.background.red > 0.8f

    @Composable
    fun cardContainerColor(): Color {
        val isLight = isLightTheme(MaterialTheme.colorScheme)
        return if (isLight) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
        }
    }

    @Composable
    fun cardBorderColor(): Color {
        val isLight = isLightTheme(MaterialTheme.colorScheme)
        return MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.28f else 0.45f)
    }

    @Composable
    fun cardBorder(): BorderStroke = BorderStroke(1.dp, cardBorderColor())

    @Composable
    fun innerTileBorder(): BorderStroke {
        val isLight = isLightTheme(MaterialTheme.colorScheme)
        return BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = if (isLight) 0.22f else 0.35f)
        )
    }

    @Composable
    fun cardElevationDp(): Dp =
        if (isLightTheme(MaterialTheme.colorScheme)) 1.dp else 0.dp
}

/**
 * Standardized tonal rounded icon badge matching the Wi-Fi Controller cards.
 */
@Composable
fun ArworaTonalIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = tint.copy(alpha = 0.12f),
    borderColor: Color = tint.copy(alpha = 0.25f),
    size: Dp = ArworaDesignTokens.IconBadgeSize,
    iconSize: Dp = 22.dp,
    cornerRadius: Dp = ArworaDesignTokens.IconBadgeCornerRadius,
    contentDescription: String? = null
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Standardized pill-shaped tonal status or category badge.
 */
@Composable
fun ArworaPillBadge(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    showDot: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = ArworaDesignTokens.PillShape,
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(tint)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1
            )
        }
    }
}

/**
 * Standardized primary pill CTA button used across generators, network tools, and utilities.
 */
@Composable
fun ArworaPrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = ArworaDesignTokens.PrimaryButtonHeight,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    leadingContent: (@Composable RowScope.() -> Unit)? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = ArworaDesignTokens.PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        if (leadingContent != null) {
            leadingContent()
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Standardized bordered card used throughout Arwora Hyper Toolkit.
 * Ensures consistent 24.dp rounded corners, subtle theme-aware tonal fill,
 * and identical 1.dp outline stroke across White, Light, and Dark themes.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = ArworaDesignTokens.CardShape,
    containerColor: Color = ArworaDesignTokens.cardContainerColor(),
    borderColor: Color = ArworaDesignTokens.cardBorderColor(),
    borderWidth: Dp = 1.dp,
    elevationDp: Dp = ArworaDesignTokens.cardElevationDp(),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        modifier
    }

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevationDp),
        content = content
    )
}
