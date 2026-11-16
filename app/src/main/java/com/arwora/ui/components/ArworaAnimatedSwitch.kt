package com.arwora.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * App-wide unified Material Design 3 animated toggle switch.
 * Provides smooth sliding transitions, color tint interpolation tied to the active
 * MaterialTheme accent palette, tactile press response, and a spring-animated check thumb icon.
 */
@Composable
fun ArworaAnimatedSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors? = null,
    uncheckedIcon: ImageVector? = null,
    interactionSource: MutableInteractionSource? = null
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by resolvedInteractionSource.collectIsPressedAsState()

    val colorScheme = MaterialTheme.colorScheme
    val resolvedColors = colors ?: SwitchDefaults.colors(
        checkedThumbColor = colorScheme.onPrimary,
        checkedTrackColor = colorScheme.primary,
        checkedBorderColor = colorScheme.primary,
        checkedIconColor = colorScheme.primary,
        uncheckedThumbColor = colorScheme.outline,
        uncheckedTrackColor = colorScheme.surfaceVariant,
        uncheckedBorderColor = colorScheme.outline.copy(alpha = 0.7f)
    )

    val iconScale by animateFloatAsState(
        targetValue = if (checked) 1f else if (uncheckedIcon != null) 0.85f else 0.5f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "switchThumbIconScale"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (checked || uncheckedIcon != null) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "switchThumbIconAlpha"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "switchPressScale"
    )

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.scale(pressScale),
        enabled = enabled,
        interactionSource = resolvedInteractionSource,
        colors = resolvedColors,
        thumbContent = {
            if (checked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .size(SwitchDefaults.IconSize)
                        .scale(iconScale)
                        .alpha(iconAlpha)
                )
            } else if (uncheckedIcon != null) {
                Icon(
                    imageVector = uncheckedIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(SwitchDefaults.IconSize)
                        .scale(iconScale)
                )
            } else if (iconAlpha > 0.02f) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .size(SwitchDefaults.IconSize)
                        .scale(iconScale)
                        .alpha(iconAlpha)
                )
            }
        }
    )
}
