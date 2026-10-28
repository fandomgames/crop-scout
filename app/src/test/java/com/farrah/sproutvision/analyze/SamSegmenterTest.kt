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
    fun `fromMasks rejects a plant union larger than the tray`() {
        // Plants are a subset of the tray area: more plant pixels than
        // tray pixels proves the tray mask is wrong (rim-only or sliver).
        // Must throw — never clamp to a confident-looking 100%.
        val tray = IntArray(100) { if (it < 10) 1 else 0 }
        val plants = IntArray(100) { if (it < 50) 1 else 0 }
        try {
            SamSegmenter.fromMasks(tray, plants, 10, 10)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("exceeds tray mask"))
        }
    }

    @Test
    fun `mostly-bare plants over a broken tray sliver must not return 100`() {
        // Gap-shot analog: 3 canopy pixels on bare soil, tray mask a
        // 2-pixel sliver. A 150% ratio is impossible → throw, not 100.
        val tray = IntArray(100) { if (it < 2) 1 else 0 }
        val plants = IntArray(100) { if (it < 3) 1 else 0 }
        try {
            SamSegmenter.fromMasks(tray, plants, 10, 10)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("exceeds tray mask"))
        }
    }

    @Test
    fun `full canopy equal to the tray still measures 100`() {
        val tray = IntArray(100) { 1 }
        val plants = IntArray(100) { 1 }
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
    fun `largest record wins by mask pixels, not box area`() {
        // A spurious near-frame box with a sparse mask must lose to the
        // smaller true-tray box with a dense mask: box area misranks them.
        fun record(
            id: String, x1: Int, y1: Int, x2: Int, y2: Int,
            raster: ByteArray, w: Int, h: Int
        ) = SamRecord(id, x1, y1, x2, y2, 8, 8, h, w, '!', encodeOneBitRaster(raster, w, h))
        val sparse = record("0", 0, 0, 7, 7, ByteArray(64) { if (it < 4) 1 else 0 }, 8, 8)
        val dense = record("1", 0, 0, 3, 3, ByteArray(16) { 1 }, 4, 4)
        assertEquals(dense, largestRecord(listOf(sparse, dense)))
        assertEquals(dense, largestRecord(listOf(dense, sparse)))
        assertEquals(sparse, largestRecord(listOf(sparse)))
    }

    @Test
    fun `largestRecord rejects an empty list`() {
        try {
            largestRecord(emptyList())
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("No records"))
        }
    }

    @Test
    fun `fromBox divides in-box plant pixels by box area`() {
        // 10x10 frame, tray box = top-left 4x4 (16 px): 8 canopy px
        // inside the box plus a full out-of-box row that must not count.
        val plants = IntArray(100)
        for (y in 0..1) for (x in 0..3) plants[y * 10 + x] = 1
        for (x in 0..9) plants[9 * 10 + x] = 1
        val result = SamSegmenter.fromBox(plants, 10, 10, 0, 0, 3, 3)
        assertEquals(50, result.coveragePercent)
        assertTrue(result.summary.startsWith("SAM coverage 50%"))
        // Gap/unevenness are tray-relative: same as fromMask on the crop.
        val crop = IntArray(16) { i -> plants[(i / 4) * 10 + (i % 4)] }
        val trayRelative = SamSegmenter.fromMask(crop, 4, 4)
        assertEquals(trayRelative.largestGapPercent, result.largestGapPercent)
        assertEquals(trayRelative.unevennessScore, result.unevennessScore)
    }

    @Test
    fun `fromBox with all plants outside the tray reads zero`() {
        val plants = IntArray(100) { if (it >= 90) 1 else 0 }
        assertEquals(0, SamSegmenter.fromBox(plants, 10, 10, 0, 0, 3, 3).coveragePercent)
    }

    @Test
    fun `fromBox rejects a box outside the frame`() {
        try {
            SamSegmenter.fromBox(IntArray(100), 10, 10, 8, 8, 12, 12)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("outside"))
        }
        try {
            SamSegmenter.fromBox(IntArray(100), 10, 10, 5, 5, 2, 2)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("outside"))
        }
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
