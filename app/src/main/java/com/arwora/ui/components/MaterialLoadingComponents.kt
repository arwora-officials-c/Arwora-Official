package com.arwora.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Material 3 Expressive Morphing Circular Loading Indicator.
 * Renders a smoothly rotating and breathing scalloped multi-lobe organic shape
 * matching Material Design 3 Expressive LoadingIndicator motion specs.
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
    showContainer: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "m3_expressive_loader")
    val rotationDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "expressive_rotation"
    )
    val morphPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "expressive_morph"
    )

    val path = remember { Path() }

    Box(
        modifier = modifier
            .size(size)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val cx = w / 2f
            val cy = h / 2f
            val outerRadius = min(w, h) * 0.48f

            if (showContainer) {
                drawCircle(
                    color = containerColor,
                    radius = min(w, h) * 0.5f,
                    center = Offset(cx, cy)
                )
            }

            // Animate smoothly between 7-lobe and soft rounded star/pill geometry
            val lobeMix = 0.5f + 0.5f * sin(morphPhase)
            val baseRadius = outerRadius * (if (showContainer) 0.56f else 0.72f)
            val waveAmp = baseRadius * (0.11f + 0.11f * lobeMix)
            val lobes = 7
            val rotationRad = Math.toRadians(rotationDeg.toDouble()).toFloat()
            val samples = 84

            path.reset()
            for (i in 0 until samples) {
                val theta = (i.toFloat() / samples) * (2f * PI.toFloat())
                val modulation = sin(lobes * (theta + rotationRad) + morphPhase * 0.5f)
                val r = baseRadius + waveAmp * modulation
                val x = cx + r * cos(theta + rotationRad * 0.35f)
                val y = cy + r * sin(theta + rotationRad * 0.35f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            drawPath(
                path = path,
                color = color
            )

            // Crisp tonal inner accent dot for depth when rendered large without overlaid icon
            if (!showContainer && min(w, h) >= 32f) {
                drawCircle(
                    color = color.copy(alpha = 0.22f),
                    radius = baseRadius * 0.34f,
                    center = Offset(cx, cy)
                )
            }
        }
    }
}

/**
 * Compact Material 3 Expressive Circular Loader for tight slots (badges, icon buttons, scan rows).
 */
@Composable
fun ExpressiveCompactLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    ExpressiveLoadingIndicator(
        modifier = modifier,
        size = size,
        color = color,
        showContainer = false
    )
}

/**
 * Material 3 Expressive Wavy Linear Progress Indicator.
 * Renders a smooth animated sinusoidal wave for the active progress segment alongside
 * a pill-capped inactive track and expressive endpoint stop dot.
 *
 * - When [progress] is non-null: determinate wavy progress cleanly interpolating across [0f..1f].
 * - When [progress] is null: indeterminate traveling wavy segment sweeping smoothly across the track.
 */
@Composable
fun ExpressiveWavyLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
    height: Dp = 10.dp,
    strokeWidth: Dp = 3.5.dp,
    wavelength: Dp = 24.dp,
    amplitude: Dp = 2.4.dp
) {
    val clampedTarget = progress?.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedTarget ?: 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "expressive_wavy_determinate"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "m3_expressive_wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val indeterminateSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "indeterminate_sweep"
    )

    val path = remember { Path() }
    val semanticsRange = if (clampedTarget != null) {
        ProgressBarRangeInfo(current = clampedTarget, range = 0f..1f)
    } else {
        ProgressBarRangeInfo.Indeterminate
    }

    Canvas(
        modifier = modifier
            .height(height)
            .semantics { progressBarRangeInfo = semanticsRange }
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val strokePx = strokeWidth.toPx().coerceIn(1.5f, h * 0.72f)
        val halfStroke = strokePx / 2f
        val centerY = h / 2f
        val maxAmpPx = ((h - strokePx) / 2f).coerceAtLeast(0f)
        val ampPx = min(amplitude.toPx(), maxAmpPx)
        val wavelengthPx = wavelength.toPx().coerceAtLeast(8f)
        val twoPiOverLambda = (2.0 * PI / wavelengthPx).toFloat()

        val startPad = halfStroke
        val endPad = (w - halfStroke).coerceAtLeast(startPad)
        val usableWidth = (endPad - startPad).coerceAtLeast(0f)
        if (usableWidth <= 0f) return@Canvas

        fun drawSinusoid(segStart: Float, segEnd: Float) {
            val length = segEnd - segStart
            if (length <= 0.5f) return

            path.reset()
            val stepPx = 2.5f
            val steps = (length / stepPx).toInt().coerceAtLeast(16)
            val edgeTaperPx = min(length * 0.25f, wavelengthPx * 0.35f).coerceAtLeast(1f)

            for (i in 0..steps) {
                val x = segStart + (i.toFloat() / steps) * length
                val distFromStart = x - segStart
                val distFromEnd = segEnd - x
                val taper = min(
                    (distFromStart / edgeTaperPx).coerceIn(0f, 1f),
                    (distFromEnd / edgeTaperPx).coerceIn(0f, 1f)
                )
                val y = centerY + ampPx * taper * sin(x * twoPiOverLambda - wavePhase)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        if (clampedTarget != null) {
            val activeEnd = startPad + usableWidth * animatedProgress
            val gapPx = (strokePx * 1.4f).coerceAtLeast(4f)
            val trackStart = if (animatedProgress > 0.01f) {
                (activeEnd + gapPx).coerceAtMost(endPad)
            } else {
                startPad
            }

            // Draw remaining rounded pill track
            if (trackStart < endPad - 0.5f) {
                drawLine(
                    color = trackColor,
                    start = Offset(trackStart, centerY),
                    end = Offset(endPad, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }

            // Expressive stop indicator dot at end of track while in progress
            if (animatedProgress < 0.985f && usableWidth > strokePx * 3f) {
                drawCircle(
                    color = color.copy(alpha = 0.7f),
                    radius = halfStroke * 0.72f,
                    center = Offset(endPad, centerY)
                )
            }

            if (animatedProgress > 0.005f) {
                if (activeEnd - startPad <= strokePx * 1.5f) {
                    drawCircle(
                        color = color,
                        radius = halfStroke,
                        center = Offset(startPad, centerY)
                    )
                } else {
                    drawSinusoid(startPad, activeEnd)
                }
            }
        } else {
            // Full background pill track
            drawLine(
                color = trackColor,
                start = Offset(startPad, centerY),
                end = Offset(endPad, centerY),
                strokeWidth = strokePx,
                cap = StrokeCap.Round
            )

            // Smooth breathing sweeping active wave segment
            val segmentFraction = 0.28f + 0.16f * sin(indeterminateSweep * PI.toFloat())
            val travelSpan = 1f + segmentFraction
            val headProgress = indeterminateSweep * travelSpan
            val tailProgress = headProgress - segmentFraction

            val rawStart = startPad + usableWidth * tailProgress.coerceIn(0f, 1f)
            val rawEnd = startPad + usableWidth * headProgress.coerceIn(0f, 1f)
            if (rawEnd > rawStart + 1f) {
                drawSinusoid(rawStart, rawEnd)
            }
        }
    }
}

/**
 * Sleek Material Design 3 Expressive Wavy horizontal progress bar with smooth determinate
 * interpolation (when [progress] is non-null) or fluid indeterminate wave motion.
 */
@Composable
fun GooglePlayLinearProgressBar(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    height: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
) {
    val effectiveHeight = height.coerceAtLeast(7.dp)
    val effectiveStroke = (effectiveHeight * 0.42f).coerceIn(2.6.dp, 4.5.dp)
    val effectiveAmp = ((effectiveHeight - effectiveStroke) * 0.45f).coerceIn(1.5.dp, 3.2.dp)

    ExpressiveWavyLinearProgressIndicator(
        modifier = modifier,
        progress = progress,
        color = color,
        trackColor = trackColor,
        height = effectiveHeight,
        strokeWidth = effectiveStroke,
        amplitude = effectiveAmp,
        wavelength = 24.dp
    )
}

/**
 * Compact Material Design 3 Expressive loader for tight slots such as buttons,
 * top-bar action icons, inline chips, and scan status rows.
 * Renders the morphing [ExpressiveCompactLoadingIndicator] for square/icon-sized slots
 * and a compact wavy bar for wider linear indicator slots.
 */
@Composable
fun GooglePlayCompactLinearProgress(
    modifier: Modifier = Modifier,
    width: Dp = 32.dp,
    height: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.25f)
) {
    if (width <= 26.dp) {
        ExpressiveCompactLoadingIndicator(
            modifier = modifier,
            size = width.coerceIn(16.dp, 24.dp),
            color = color
        )
    } else {
        ExpressiveWavyLinearProgressIndicator(
            modifier = modifier.width(width),
            progress = null,
            color = color,
            trackColor = trackColor,
            height = height.coerceAtLeast(7.dp),
            strokeWidth = 2.6.dp,
            wavelength = 16.dp,
            amplitude = 1.8.dp
        )
    }
}

/**
 * Reusable Material 3 Expressive horizontal progress card matching Arwora's Wi-Fi Controller
 * card styling: 24.dp rounded card, unified 1.dp outline, tonal icon badge with animated
 * [ExpressiveLoadingIndicator], optional pill percentage badge, and [ExpressiveWavyLinearProgressIndicator].
 */
@Composable
fun GoogleStyleLinearLoadingCard(
    title: String,
    subtitle: String = "Preparing diagnostics and data...",
    icon: ImageVector = Icons.Default.Apps,
    progress: Float? = null,
    modifier: Modifier = Modifier
) {
    val cardBgColor = ArworaDesignTokens.cardContainerColor()
    val elevationDp = ArworaDesignTokens.cardElevationDp()
    val cardBorder = ArworaDesignTokens.cardBorder()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ArworaDesignTokens.CardShape,
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
                    shape = ArworaDesignTokens.IconBadgeShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ExpressiveLoadingIndicator(
                            size = 38.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            showContainer = false
                        )
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(21.dp)
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
                        fontWeight = FontWeight.Bold,
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
                    ArworaPillBadge(
                        text = "$pct%",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            ExpressiveWavyLinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("google_linear_progress"),
                progress = progress,
                height = 10.dp
            )
        }
    }
}

/**
 * Animated wrapper showing a Material 3 Expressive wavy horizontal loading bar + status text.
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
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpressiveCompactLoadingIndicator(
                        size = 16.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (progress != null) {
                    Text(
                        text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            ExpressiveWavyLinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                progress = progress,
                height = 8.dp
            )
        }
    }
}

/**
 * Full-screen / inline Material Design 3 Expressive loading view with:
 * - Tonal icon + animated ExpressiveLoadingIndicator + ExpressiveWavyLinearProgressIndicator
 * - Lightweight shimmering skeleton cards matching Arwora's unified 24.dp bordered card spec
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
    val isLight = ArworaDesignTokens.isLightTheme(MaterialTheme.colorScheme)
    val cardBgColor = ArworaDesignTokens.cardContainerColor()
    val elevationDp = ArworaDesignTokens.cardElevationDp()
    val cardBorder = ArworaDesignTokens.cardBorder()

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
        shape = ArworaDesignTokens.CardShape,
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
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ArworaDesignTokens.IconBadgeShape)
                    .background(brush)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.56f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(brush)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(brush)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .width(58.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(brush)
            )
        }
    }
}
