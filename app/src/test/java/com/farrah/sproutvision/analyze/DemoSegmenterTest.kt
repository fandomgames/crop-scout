package com.farrah.sproutvision.analyze

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the demo segmenter's green-pixel heuristic (pure and
 * unit-testable; the Bitmap decode around it needs Android).
 */
class DemoSegmenterTest {

    @Test
    fun `canopy green passes, soil tray and shadow fail`() {
        assertEquals(true, isCanopyPixel(60, 180, 70))
        assertEquals(true, isCanopyPixel(40, 120, 50))
        assertEquals(false, isCanopyPixel(120, 110, 90)) // brown soil
        assertEquals(false, isCanopyPixel(200, 200, 200)) // white tray
        assertEquals(false, isCanopyPixel(30, 40, 35)) // dark shadow
        assertEquals(false, isCanopyPixel(100, 150, 140)) // gray-green, weak dominance
    }

    @Test
    fun `threshold grid marks only canopy pixels`() {
        val green = (0xFF shl 24) or (60 shl 16) or (180 shl 8) or 70
        val red = (0xFF shl 24) or (200 shl 16) or (60 shl 8) or 60
        val blue = (0xFF shl 24) or (60 shl 16) or (80 shl 8) or 200
        val grid = thresholdGrid(intArrayOf(green, red, blue, green), 2, 2)
        assertArrayEquals(intArrayOf(1, 0, 0, 1), grid)
    }
}
