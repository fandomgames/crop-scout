package com.farrah.sproutvision.analyze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Guards two-mask coverage (plant union ÷ tray pixels), the union's
 * no-double-count rule, and the tray union box. Rasters are encoded
 * with the codec's own [encodeOneBitRaster], so the payloads are
 * canonical by construction.
 */
class SamSegmenterTest {

    @Test
    fun `fromMasks divides plant pixels by tray pixels`() {
        val tray = IntArray(100) { 1 }
        val plants = IntArray(100) { if (it < 25) 1 else 0 }
        val result = SamSegmenter.fromMasks(tray, plants, 10, 10)
        assertEquals(25, result.coveragePercent)
        assertTrue(result.summary.startsWith("SAM coverage 25%"))
    }

    @Test
    fun `fromMasks coerces overfull coverage to 100`() {
        val tray = IntArray(100) { if (it < 10) 1 else 0 }
        val plants = IntArray(100) { if (it < 50) 1 else 0 }
        assertEquals(100, SamSegmenter.fromMasks(tray, plants, 10, 10).coveragePercent)
    }

    @Test
    fun `fromMasks rejects an empty tray`() {
        try {
            SamSegmenter.fromMasks(IntArray(100), IntArray(100) { 1 }, 10, 10)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Tray mask is empty"))
        }
    }

    @Test
    fun `fromMasks reuses gap and unevenness from the plant grid`() {
        val tray = IntArray(100) { 1 }
        val plants = IntArray(100) { if (it < 25) 1 else 0 }
        val single = SamSegmenter.fromMask(plants, 10, 10)
        val dual = SamSegmenter.fromMasks(tray, plants, 10, 10)
        assertEquals(single.largestGapPercent, dual.largestGapPercent)
        assertEquals(single.unevennessScore, dual.unevennessScore)
    }

    @Test
    fun `demo label appears in the summary`() {
        val tray = IntArray(100) { 1 }
        val plants = IntArray(100) { if (it < 40) 1 else 0 }
        val result = SamSegmenter.fromMasks(tray, plants, 10, 10, label = "Demo")
        assertEquals(40, result.coveragePercent)
        assertTrue(result.summary.startsWith("Demo coverage 40%"))
    }

    @Test
    fun `overlapping instances count once`() {
        val payload = encodeOneBitRaster(ByteArray(16) { 1 }, 4, 4)
        fun record(id: String) = "$id<|box;x1=0;y1=0;x2=3;y2=3;w=8;h=8|>" +
            "<|mask;x=0;y=0;data=4,4,$payload|>"
        val one = parseSegmentationLine("<0f>" + record("0"))
        val two = parseSegmentationLine("<0f>" + record("0") + "," + record("1"))
        assertEquals(1, one.size)
        assertEquals(2, two.size)
        val (gridTwo, w, h) = compositeFrameGrid(two)
        assertEquals(8, w)
        assertEquals(8, h)
        // Two identical 4x4 instances union to 16 pixels, not 32.
        assertEquals(16, gridTwo.count { it == 1 })
        val result = SamSegmenter.fromMasks(IntArray(64) { 1 }, gridTwo, w, h)
        assertEquals(25, result.coveragePercent)
    }

    @Test
    fun `largest record wins by box area`() {
        val sliver = SamRecord("0", 600, 10, 639, 100, 640, 480, 91, 40, '!', "!x")
        val main = SamRecord("1", 10, 20, 600, 460, 640, 480, 441, 591, '!', "!y")
        assertEquals(main, largestRecord(listOf(sliver, main)))
        assertEquals(main, largestRecord(listOf(main, sliver)))
        assertEquals(sliver, largestRecord(listOf(sliver)))
    }

    @Test
    fun `tray box unions record boxes`() {
        val records = listOf(
            SamRecord("0", 10, 20, 17, 27, 640, 480, 8, 8, '!', "!x"),
            SamRecord("1", 100, 100, 103, 103, 640, 480, 4, 4, '!', "!y")
        )
        assertEquals(TrayBox(15, 41, 162, 216), trayBoxFromRecords(records))
    }
}
