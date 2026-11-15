package com.arwora

import com.arwora.ui.components.computeNextBottomBarOffsetPx
import com.arwora.ui.components.computeSnapTargetOffsetPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BottomBarScrollControllerTest {

    private val barHeightPx = 180f

    @Test
    fun scrollingDownwardProgressivelyHidesBottomBarAndClampsAtHeight() {
        val step1 = computeNextBottomBarOffsetPx(
            currentOffsetPx = 0f,
            consumedY = -60f,
            availableY = 0f,
            maxOffsetPx = barHeightPx
        )
        assertEquals(60f, step1, 0.01f)

        val step2 = computeNextBottomBarOffsetPx(
            currentOffsetPx = step1,
            consumedY = -90f,
            availableY = 0f,
            maxOffsetPx = barHeightPx
        )
        assertEquals(150f, step2, 0.01f)

        val clamped = computeNextBottomBarOffsetPx(
            currentOffsetPx = step2,
            consumedY = -100f,
            availableY = 0f,
            maxOffsetPx = barHeightPx
        )
        assertEquals(barHeightPx, clamped, 0.01f)
    }

    @Test
    fun scrollingUpwardProgressivelyRevealsBottomBarAndClampsAtZero() {
        val step1 = computeNextBottomBarOffsetPx(
            currentOffsetPx = barHeightPx,
            consumedY = 75f,
            availableY = 0f,
            maxOffsetPx = barHeightPx
        )
        assertEquals(105f, step1, 0.01f)

        val revealed = computeNextBottomBarOffsetPx(
            currentOffsetPx = step1,
            consumedY = 140f,
            availableY = 0f,
            maxOffsetPx = barHeightPx
        )
        assertEquals(0f, revealed, 0.01f)
    }

    @Test
    fun nonScrollableAttemptDoesNotHideBottomBarWhileTopOverscrollReveals() {
        // Dragging finger upward on a short non-scrollable screen (consumedY == 0, availableY < 0)
        val nonScrollable = computeNextBottomBarOffsetPx(
            currentOffsetPx = 0f,
            consumedY = 0f,
            availableY = -120f,
            maxOffsetPx = barHeightPx
        )
        assertEquals(0f, nonScrollable, 0.01f)

        // Pulling down at top boundary when partially hidden still reveals bar smoothly
        val topOverscrollReveal = computeNextBottomBarOffsetPx(
            currentOffsetPx = 50f,
            consumedY = 0f,
            availableY = 30f,
            maxOffsetPx = barHeightPx
        )
        assertEquals(20f, topOverscrollReveal, 0.01f)
    }

    @Test
    fun snapTargetSettlesMidTransitionAndRevealsOnUpwardTopFling() {
        // Less than 50% hidden snaps back to visible (0f)
        assertEquals(0f, computeSnapTargetOffsetPx(60f, 0f, barHeightPx)!!, 0.01f)

        // 50% or more hidden snaps smoothly to hidden (barHeightPx)
        assertEquals(barHeightPx, computeSnapTargetOffsetPx(90f, 0f, barHeightPx)!!, 0.01f)
        assertEquals(barHeightPx, computeSnapTargetOffsetPx(130f, -200f, barHeightPx)!!, 0.01f)

        // Upward fling hitting top boundary always snaps back to visible (0f)
        assertEquals(0f, computeSnapTargetOffsetPx(140f, 350f, barHeightPx)!!, 0.01f)

        // Already settled edges require no snap animation
        assertNull(computeSnapTargetOffsetPx(0f, 0f, barHeightPx))
        assertNull(computeSnapTargetOffsetPx(barHeightPx, 0f, barHeightPx))
    }
}
