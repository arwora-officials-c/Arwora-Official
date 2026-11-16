package com.arwora.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Sleek Google Play Store Material Design 3 horizontal progress bar with rounded pill caps,
 * smooth determinate progress interpolation (when [progress] is non-null), or fluid indeterminate motion.
 */
@Composable
fun GooglePlayLinearProgressBar(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    height: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
) {
    val barModifier = modifier
        .height(height)
        .clip(RoundedCornerShape(50))

    if (progress != null) {
        val animatedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "google_linear_progress"
        )
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = barModifier,
            color = color,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round
        )
    } else {
        LinearProgressIndicator(
            modifier = barModifier,
            color = color,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round
        )
    }
}

/**
 * Compact horizontal Material Design 3 linear loader for tight slots such as buttons,
 * top-bar action icons, inline chips, and floating overlays.
 */
@Composable
fun GooglePlayCompactLinearProgress(
    modifier: Modifier = Modifier,
    width: Dp = 32.dp,
    height: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.25f)
) {
    LinearProgressIndicator(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(50)),
        color = color,
        trackColor = trackColor,
        strokeCap = StrokeCap.Round
    )
}

/**
 * Reusable Google Play Store style horizontal progress card with status label, optional percentage
 * counter, tonal icon badge, and full-width rounded Material 3 LinearProgressIndicator.
 */
@Composable
fun GoogleStyleLinearLoadingCard(
    title: String,
    subtitle: String = "Preparing diagnostics and data...",
    icon: ImageVector = Icons.Default.Apps,
    progress: Float? = null,
    modifier: Modifier = Modifier
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val outlineAlpha = if (isLight) 0.25f else 0.4f
    val cardBgColor = if (isLight) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    }
    val elevationDp = if (isLight) 1.dp else 0.dp
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = outlineAlpha))

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = elevationDp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (progress != null) {
                    val pct = (progress.coerceIn(0f, 1f) * 100).toInt()
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            GooglePlayLinearProgressBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_linear_progress"),
                progress = progress,
                height = 6.dp
            )
        }
    }
}

/**
 * Animated wrapper showing a clean Play Store horizontal loading bar + status text with fade/expand.
 */
@Composable
fun GoogleStyleStatusLinearLoader(
    visible: Boolean,
    statusText: String,
    progress: Float? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
        exit = fadeOut(tween(180)) + shrinkVertically(tween(180)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (progress != null) {
                    Text(
                        text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            GooglePlayLinearProgressBar(
                modifier = Modifier.fillMaxWidth(),
                progress = progress,
                height = 5.dp
            )
        }
    }
}

/**
 * Google / Play Store Material Design 3 linear loading view with:
 * - Tonal icon + status summary card with rounded LinearProgressIndicator
 * - Optional lightweight shimmering skeleton cards matching Arwora's unified card outline spec
 */
@Composable
fun GoogleStyleLoadingView(
    title: String,
    subtitle: String = "Preparing system diagnostics and package data...",
    icon: ImageVector = Icons.Default.Apps,
    showSkeletonRows: Boolean = true,
    skeletonRowCount: Int = 4,
    scrollable: Boolean = true,
    progress: Float? = null,
    modifier: Modifier = Modifier
) {
    val isLight = MaterialTheme.colorScheme.background.red > 0.8f
    val outlineAlpha = if (isLight) 0.25f else 0.4f
    val cardBgColor = if (isLight) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    }
    val elevationDp = if (isLight) 1.dp else 0.dp
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = outlineAlpha))

    val infiniteTransition = rememberInfiniteTransition(label = "google_shimmer")
    val shimmerProgress by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_sweep"
    )

    val shimmerBase = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isLight) 0.45f else 0.30f)
    val shimmerHighlight = MaterialTheme.colorScheme.primary.copy(alpha = if (isLight) 0.12f else 0.18f)

    val containerModifier = if (scrollable) {
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    }

    Column(
        modifier = containerModifier.testTag("google_style_loading_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GoogleStyleLinearLoadingCard(
            title = title,
            subtitle = subtitle,
            icon = icon,
            progress = progress
        )

        if (showSkeletonRows) {
            repeat(skeletonRowCount) {
                ShimmerAppSkeletonCard(
                    shimmerProgress = shimmerProgress,
                    baseColor = shimmerBase,
                    highlightColor = shimmerHighlight,
                    cardBgColor = cardBgColor,
                    cardBorder = cardBorder,
                    elevationDp = elevationDp
                )
            }
        }
    }
}

@Composable
private fun ShimmerAppSkeletonCard(
    shimmerProgress: Float,
    baseColor: Color,
    highlightColor: Color,
    cardBgColor: Color,
    cardBorder: BorderStroke,
    elevationDp: Dp
) {
    val brush = Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(x = (shimmerProgress - 0.4f) * 800f, y = 0f),
        end = Offset(x = shimmerProgress * 800f, y = 160f)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = elevationDp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // App Icon Skeleton
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush)
            )

            // Title & Package Skeleton Bars
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.56f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(brush)
                )
            }

            // Status Pill Skeleton
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .width(58.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush)
            )
        }
    }
}
