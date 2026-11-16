package com.arwora

import com.arwora.service.FloatingWindowService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingWindowBoundsTest {

    @Test
    fun computeSizeBounds_enforcesMinimumWidthAndHeightForCompactAndExpanded() {
        val compactBounds = FloatingWindowService.computeSizeBounds(
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3.0f,
            posX = 60,
            posY = 120,
            expanded = false
        )
        assertEquals((260f * 3.0f).toInt(), compactBounds.minWidth)
        assertEquals((200f * 3.0f).toInt(), compactBounds.minHeight)
        assertTrue(compactBounds.maxWidth >= compactBounds.minWidth)
        assertTrue(compactBounds.maxHeight >= compactBounds.minHeight)

        val expandedBounds = FloatingWindowService.computeSizeBounds(
            screenWidthPx = 1080,
            screenHeightPx = 2400,
            density = 3.0f,
            posX = 60,
            posY = 120,
            expanded = true
        )
        assertEquals((260f * 3.0f).toInt(), expandedBounds.minWidth)
        assertEquals((280f * 3.0f).toInt(), expandedBounds.minHeight)
        assertTrue(expandedBounds.maxWidth >= expandedBounds.minWidth)
        assertTrue(expandedBounds.maxHeight >= expandedBounds.minHeight)
    }

    @Test
    fun clampWindowPosition_keepsExpandedWindowInsideScreenEdges() {
        val (clampedX, clampedY) = FloatingWindowService.clampWindowPosition(
            x = 980,
            y = 2250,
            widthPx = 900,
            heightPx = 1200,
            screenWidthPx = 1080,
            screenHeightPx = 2400
        )
        assertEquals(180, clampedX)
        assertEquals(1200, clampedY)
    }

    @Test
    fun computeSizeBounds_handlesSmallScreensWithoutInvertingBounds() {
        val tinyScreenBounds = FloatingWindowService.computeSizeBounds(
            screenWidthPx = 480,
            screenHeightPx = 800,
            density = 2.0f,
            posX = 400,
            posY = 700,
            expanded = true
        )
        assertTrue(tinyScreenBounds.minWidth <= tinyScreenBounds.maxWidth)
        assertTrue(tinyScreenBounds.minHeight <= tinyScreenBounds.maxHeight)
        assertTrue(tinyScreenBounds.maxWidth <= (480 * 0.96f).toInt())
        assertTrue(tinyScreenBounds.maxHeight <= (800 * 0.88f).toInt())
    }
}
