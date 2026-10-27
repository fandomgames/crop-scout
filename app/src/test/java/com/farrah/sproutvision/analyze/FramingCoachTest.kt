package com.farrah.sproutvision.analyze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the live viewfinder coach: darkness, missing tray, and too-close
 * frames each produce their warning; a well-framed tray stays quiet.
 */
class FramingCoachTest {

    private fun solidFrame(argb: Int, size: Int = 64): IntArray =
        IntArray(size * size) { argb }

    @Test
    fun `dark frame warns about light`() {
        val stats = computeFramingStats(solidFrame(0xFF050505.toInt()), 64, 64)

        assertEquals("Frame is very dark", framingAdviceNow(stats))
    }

    @Test
    fun `blank bright frame means tray out of view`() {
        // Bright gray wall: no vegetation, no soil.
        val stats = computeFramingStats(solidFrame(0xFFC8C8C8.toInt()), 64, 64)

        assertEquals("Tray out of view — aim at the tray", framingAdviceNow(stats))
    }

    @Test
    fun `all-green frame means too close`() {
        val stats = computeFramingStats(solidFrame(0xFF2E7D32.toInt()), 64, 64)

        assertEquals("Too close — pull back a little", framingAdviceNow(stats))
    }

    @Test
    fun `brown soil counts as tray visible`() {
        val stats = computeFramingStats(solidFrame(0xFF6B4A2F.toInt()), 64, 64)

        // All soil, no brights: reads as too close, not missing.
        assertEquals("Too close — pull back a little", framingAdviceNow(stats))
    }

    @Test
    fun `framed tray with edges stays quiet`() {
        // Green canopy ringed by white tray edges.
        val pixels = IntArray(64 * 64) { i ->
            val x = i % 64
            val y = i / 64
            if (x < 8 || x >= 56 || y < 8 || y >= 56) 0xFFF2F2F2.toInt()
            else 0xFF2E7D32.toInt()
        }
        val stats = computeFramingStats(pixels, 64, 64, stride = 4)

        assertNull(framingAdviceNow(stats))
    }
}
