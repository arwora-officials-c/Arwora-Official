package com.arwora.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Pure scroll-delta calculation for progressive bottom bar translation.
 *
 * - Scrolling content downward (`consumedY < 0`) translates the bar downward (`+delta`)
 *   toward [maxOffsetPx] (fully hidden).
 * - Scrolling content upward (`consumedY > 0`) translates the bar upward (`-delta`)
 *   toward `0f` (fully visible).
 * - Pulling down at top overscroll (`consumedY == 0 && availableY > 0`) also reveals
 *   the bar cleanly, whereas attempting to scroll a non-scrollable screen downward
 *   (`consumedY == 0 && availableY < 0`) leaves the bar visible.
 */
internal fun computeNextBottomBarOffsetPx(
    currentOffsetPx: Float,
    consumedY: Float,
    availableY: Float,
    maxOffsetPx: Float
): Float {
    if (maxOffsetPx <= 0f) return 0f
    val effectiveDeltaY = when {
        consumedY != 0f -> consumedY
        availableY > 0f -> availableY
        else -> 0f
    }
    if (effectiveDeltaY == 0f) {
        return currentOffsetPx.coerceIn(0f, maxOffsetPx)
    }
    return (currentOffsetPx - effectiveDeltaY).coerceIn(0f, maxOffsetPx)
}

/**
 * Computes the resting snap target (`0f` = shown, [maxOffsetPx] = hidden) when scrolling
 * stops or flings settle. Returns `null` if the bar is already at a settled edge.
 */
internal fun computeSnapTargetOffsetPx(
    currentOffsetPx: Float,
    availableVelocityY: Float,
    maxOffsetPx: Float
): Float? {
    if (maxOffsetPx <= 0f) return null
    val clamped = currentOffsetPx.coerceIn(0f, maxOffsetPx)
    // Strong upward fling hitting the top boundary should always reveal navigation.
    if (availableVelocityY > 80f) {
        return if (clamped > 0.5f) 0f else null
    }
    if (clamped <= 0.5f || clamped >= maxOffsetPx - 0.5f) {
        return null
    }
    return if (clamped < maxOffsetPx * 0.5f) 0f else maxOffsetPx
}

/**
 * Scroll-linked controller that coordinates smooth, progressive hide/show translation
 * of the floating Material 3 bottom navigation bar across all ViewPager/HorizontalPager tabs.
 */
@Stable
class BottomBarScrollController(
    private val coroutineScope: CoroutineScope
) {
    private val offsetAnimatable = Animatable(0f)

    var maxOffsetPx by mutableFloatStateOf(0f)
        private set

    val offsetPx: Float
        get() = offsetAnimatable.value

    fun updateMaxOffsetPx(newMaxPx: Float) {
        val sanitized = newMaxPx.coerceAtLeast(0f)
        val previousMax = maxOffsetPx
        if (sanitized == previousMax) return
        val wasFullyHidden = previousMax > 0f && offsetAnimatable.value >= previousMax - 1f
        maxOffsetPx = sanitized
        coroutineScope.launch {
            val target = when {
                sanitized <= 0f -> 0f
                wasFullyHidden -> sanitized
                else -> offsetAnimatable.value.coerceIn(0f, sanitized)
            }
            offsetAnimatable.snapTo(target)
        }
    }

    fun reveal(animate: Boolean = true) {
        coroutineScope.launch {
            if (!animate || offsetAnimatable.value <= 0.5f) {
                offsetAnimatable.snapTo(0f)
            } else {
                offsetAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 240,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
    }

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            val maxPx = maxOffsetPx
            if (maxPx <= 0f) return Offset.Zero
            val current = offsetAnimatable.value
            val next = computeNextBottomBarOffsetPx(
                currentOffsetPx = current,
                consumedY = consumed.y,
                availableY = available.y,
                maxOffsetPx = maxPx
            )
            if (next != current) {
                coroutineScope.launch {
                    offsetAnimatable.snapTo(next)
                }
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(
            consumed: Velocity,
            available: Velocity
        ): Velocity {
            val target = computeSnapTargetOffsetPx(
                currentOffsetPx = offsetAnimatable.value,
                availableVelocityY = available.y,
                maxOffsetPx = maxOffsetPx
            )
            if (target != null) {
                offsetAnimatable.animateTo(
                    targetValue = target,
                    animationSpec = tween(
                        durationMillis = 220,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberBottomBarScrollController(): BottomBarScrollController {
    val coroutineScope = rememberCoroutineScope()
    return remember(coroutineScope) {
        BottomBarScrollController(coroutineScope)
    }
}
