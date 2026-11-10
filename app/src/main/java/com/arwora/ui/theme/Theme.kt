package com.arwora.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

val LocalButtonColor = compositionLocalOf { AccentIndigo }

@Composable
fun NameGenProTheme(
    darkTheme: Boolean = true,
    whiteTheme: Boolean = false,
    uiColor: Color = AccentIndigo,
    buttonColor: Color = AccentIndigo,
    content: @Composable () -> Unit,
) {
    val animatedUiColor by animateColorAsState(
        targetValue = uiColor,
        animationSpec = tween(durationMillis = 250),
        label = "animatedUiColor"
    )
    val animatedButtonColor by animateColorAsState(
        targetValue = buttonColor,
        animationSpec = tween(durationMillis = 250),
        label = "animatedButtonColor"
    )

    val targetBg = when {
        whiteTheme -> Color(0xFFFFFFFF)
        darkTheme -> BackgroundDark
        else -> Color(0xFFF8F9FA)
    }
    val targetSurface = when {
        whiteTheme -> Color(0xFFFFFFFF)
        darkTheme -> BackgroundDark
        else -> Color(0xFFF8F9FA)
    }
    val targetSurfaceVariant = when {
        whiteTheme -> Color(0xFFF8FAFC)
        darkTheme -> SurfaceDark
        else -> Color(0xFFFFFFFF)
    }
    val targetOnSurface = when {
        whiteTheme -> Color(0xFF101828)
        darkTheme -> TextPrimary
        else -> Color(0xFF111111)
    }
    val targetOnBackground = when {
        whiteTheme -> Color(0xFF101828)
        darkTheme -> TextPrimary
        else -> Color(0xFF111111)
    }
    val targetOnSurfaceVariant = when {
        whiteTheme -> Color(0xFF475467)
        darkTheme -> TextSecondary
        else -> Color(0xFF555555)
    }
    val targetOutline = when {
        whiteTheme -> Color(0xFFD0D5DD)
        darkTheme -> OutlineColor
        else -> Color(0xFFE2E8F0)
    }
    val targetSecondaryContainer = when {
        whiteTheme -> Color(0xFFF2F4F7)
        darkTheme -> SurfaceVariant
        else -> Color(0xFFE2E8F0)
    }

    val animatedBg by animateColorAsState(targetValue = targetBg, animationSpec = tween(250), label = "animBg")
    val animatedSurface by animateColorAsState(targetValue = targetSurface, animationSpec = tween(250), label = "animSurface")
    val animatedSurfaceVariant by animateColorAsState(targetValue = targetSurfaceVariant, animationSpec = tween(250), label = "animSurfaceVariant")
    val animatedOnSurface by animateColorAsState(targetValue = targetOnSurface, animationSpec = tween(250), label = "animOnSurface")
    val animatedOnBackground by animateColorAsState(targetValue = targetOnBackground, animationSpec = tween(250), label = "animOnBackground")
    val animatedOnSurfaceVariant by animateColorAsState(targetValue = targetOnSurfaceVariant, animationSpec = tween(250), label = "animOnSurfaceVariant")
    val animatedOutline by animateColorAsState(targetValue = targetOutline, animationSpec = tween(250), label = "animOutline")
    val animatedSecondaryContainer by animateColorAsState(targetValue = targetSecondaryContainer, animationSpec = tween(250), label = "animSecondaryContainer")

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = animatedUiColor,
            onPrimary = TextPrimary,
            primaryContainer = animatedUiColor.copy(alpha = 0.7f),
            onPrimaryContainer = TextPrimary,
            secondary = animatedUiColor,
            onSecondary = TextPrimary,
            secondaryContainer = animatedSecondaryContainer,
            onSecondaryContainer = TextPrimary,
            background = animatedBg,
            onBackground = animatedOnBackground,
            surface = animatedSurface,
            onSurface = animatedOnSurface,
            surfaceVariant = animatedSurfaceVariant,
            onSurfaceVariant = animatedOnSurfaceVariant,
            outline = animatedOutline
        )
    } else {
        lightColorScheme(
            primary = animatedUiColor,
            onPrimary = Color.White,
            primaryContainer = if (whiteTheme) animatedUiColor.copy(alpha = 0.12f) else animatedUiColor.copy(alpha = 0.2f),
            onPrimaryContainer = if (whiteTheme) animatedUiColor else Color.Black,
            secondary = animatedUiColor,
            onSecondary = Color.White,
            secondaryContainer = animatedSecondaryContainer,
            onSecondaryContainer = if (whiteTheme) Color(0xFF1D2939) else Color.Black,
            background = animatedBg,
            onBackground = animatedOnBackground,
            surface = animatedSurface,
            onSurface = animatedOnSurface,
            surfaceVariant = animatedSurfaceVariant,
            onSurfaceVariant = animatedOnSurfaceVariant,
            outline = animatedOutline
        )
    }

    val finalButtonColor = if (whiteTheme) animatedUiColor else animatedButtonColor

    CompositionLocalProvider(LocalButtonColor provides finalButtonColor) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
