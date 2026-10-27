package com.farrah.sproutvision.analyze

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Guards the SAM 3.1 wire decode against Meta's own encoder: every
 * payload vector below was produced by meta-sam-parser 0.1.0's
 * _encode_raster/_pack (see /tmp/samparser/gen_vectors.py), so a
 * passing suite means byte-identical interop with Meta's reference.
 */
class SamMaskCodecTest {

    @Test
    fun `base85 unpack matches reference vectors`() {
        assertArrayEquals(byteArrayOf(), unpack85("!!!!!"))
        assertArrayEquals(byteArrayOf(65), unpack85("!!!!#7r"))
        assertArrayEquals("Hello".toByteArray(), unpack85("!!!!':9iZWI`"))
        assertArrayEquals(
            ByteArray(16) { it.toByte() },
            unpack85("!!!!3!!+/(#=k{9\$WQnL%q5a]")
        )
    }

    @Test
    fun `base85 rejects short payloads`() {
        try {
            unpack85("!!!!")
            fail("expected SamMaskException")
        } catch (e: SamMaskException) {
            // Expected.
        }
    }

    @Test
    fun `base85 rejects out-of-alphabet characters`() {
        // '~' is not in the effective 85-char alphabet (values 85+ invalid).
        try {
            unpack85("~~~~~")
            fail("expected SamMaskException")
        } catch (e: SamMaskException) {
            // Expected.
        }
    }

    @Test
    fun `one-bit decode matches reference rasters`() {
        assertArrayEquals(byteArrayOf(1), decodeMaskPayload("!!!!!&O.lVP", 1, 1))
        assertArrayEquals(byteArrayOf(0), decodeMaskPayload("!!!!!&!!!!!", 1, 1))
        assertArrayEquals(
            byteArrayOf(0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 0),
            decodeMaskPayload("!!!!!(M[8Ypjl=", 4, 4)
        )
        val half8 = ByteArray(64) { if (it < 32) 1 else 0 }
        assertArrayEquals(half8, decodeMaskPayload("!!!!!*y:5B-6uhVe", 8, 8))
        val rand10 = byteArrayOf(
            1, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0,
            0, 1, 0, 0, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 1,
            1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 1, 0, 1,
            0, 1, 1, 0, 0, 1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 1, 1,
            1, 0, 1, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1, 0, 0, 1, 1, 1
        )
        assertArrayEquals(rand10, decodeMaskPayload("!!!!!3YKdYmj+%`vl4?{_.QUl9", 10, 10))
    }

    @Test
    fun `tampered payload fails the canonical check`() {
        try {
            decodeMaskPayload("!!!!!(M[8Ypjl!", 4, 4)
            fail("expected SamMaskException")
        } catch (e: SamMaskException) {
            // Expected.
        }
    }

    @Test
    fun `lossless payloads match metas own decoder`() {
        // Real ~ payloads from meta-sam-parser's README example; the
        // expected rows/popcounts below came from Meta's own
        // decode_mask_to_raster, not from this port.
        assertLosslessVector(
            "~!!!!M!0c[0o91w?q1!pIH4pPRVp2B3'7`e.ioeAf6-k/#Xd8%dX9x(",
            60, 27, 1539,
            "000001111111111111111111111111111111111111111111111111000000",
            "111111111111111111111111111111111111111111111111111111111110",
            "000000011111111111111111111111111111111111111111111100000000"
        )
        assertLosslessVector(
            "~!!!!J!0c[q=Pj_zs=*4C4(/./x#:/`S_GnD`=o3?X{emCgO\$y@",
            48, 27, 1236,
            "000011111111111111111111111111111111111111100000",
            "111111111111111111111111111111111111111111111111",
            "000000111111111111111111111111111111111111000000"
        )
    }

    private fun assertLosslessVector(
        payload: String, w: Int, h: Int, popcount: Int,
        row0: String, rowMid: String, rowLast: String
    ) {
        val raster = decodeMaskPayload(payload, w, h)
        assertEquals(w * h, raster.size)
        assertEquals(popcount, raster.count { it.toInt() == 1 })
        fun row(y: Int) = (0 until w).joinToString("") { raster[y * w + it].toString() }
        assertEquals(row0, row(0))
        assertEquals(rowMid, row(h / 2))
        assertEquals(rowLast, row(h - 1))
    }

    @Test
    fun `record line parses boxes masks and dims`() {
        val line = "<0f>0<|box;x1=10;y1=20;x2=17;y2=27;w=640;h=480|>" +
            "<|mask;x=0;y=0;data=8,8,!!!!!*y:5B-6uhVe|>"
        val records = parseSegmentationLine(line)
        assertEquals(1, records.size)
        val r = records[0]
        assertEquals("0", r.objectId)
        assertEquals(10, r.x1)
        assertEquals(20, r.y1)
        assertEquals(17, r.x2)
        assertEquals(27, r.y2)
        assertEquals(640, r.frameW)
        assertEquals(480, r.frameH)
        assertEquals(8, r.maskH)
        assertEquals(8, r.maskW)
        assertEquals('!', r.marker)
        val half8 = ByteArray(64) { if (it < 32) 1 else 0 }
        assertArrayEquals(half8, decodeMaskPayload(r.payload, r.maskW, r.maskH))
    }

    @Test
    fun `two-record line composites onto the frame grid`() {
        val line = "<0f>0<|box;x1=10;y1=20;x2=17;y2=27;w=640;h=480|>" +
            "<|mask;x=0;y=0;data=8,8,!!!!!*y:5B-6uhVe|>" +
            ",1<|box;x1=100;y1=100;x2=103;y2=103;w=640;h=480|>" +
            "<|mask;x=0;y=0;data=4,4,!!!!!(M[8Ypjl=|>"
        val records = parseSegmentationLine(line)
        assertEquals(2, records.size)
        val (grid, w, h) = compositeFrameGrid(records)
        assertEquals(640, w)
        assertEquals(480, h)
        // Mask 1: top half set at (10..17, 20..23), bottom half clear.
        assertEquals(1, grid[20 * w + 10])
        assertEquals(1, grid[23 * w + 17])
        assertEquals(0, grid[24 * w + 10])
        assertEquals(0, grid[27 * w + 17])
        // Mask 2: 4x4 checker at (100..103, 100..103).
        assertEquals(0, grid[100 * w + 100])
        assertEquals(1, grid[100 * w + 101])
        assertEquals(1, grid[101 * w + 100])
        // Untouched frame stays clear.
        assertEquals(0, grid[0])
        assertEquals(0, grid[479 * w + 639])
        // End to end through the segmenter: 40 canopy px of 307200.
        val result = SamSegmenter.fromMask(grid, w, h)
        assertEquals(0, result.coveragePercent)
        assertEquals(6, result.largestGapPercent)
        assertEquals(100, result.unevennessScore)
        assertEquals(
            "SAM coverage 0%, largest gap ~6% of tray, unevenness 100/100",
            result.summary
        )
    }

    @Test
    fun `blank lines yield no records`() {
        assertTrue(parseSegmentationLine("").isEmpty())
        assertTrue(parseSegmentationLine("   ").isEmpty())
    }

    @Test
    fun `malformed lines throw`() {
        val bad = listOf(
            // No frame header.
            "0<|box;x1=0;y1=0;x2=1;y2=1;w=4;h=4|><|mask;x=0;y=0;data=2,2,!!!!!&!!!!!|>",
            // Wrong frame (image mode is frame 0 only).
            "<1f>0<|box;x1=0;y1=0;x2=1;y2=1;w=4;h=4|><|mask;x=0;y=0;data=2,2,!!!!!&!!!!!|>",
            // Box outside frame.
            "<0f>0<|box;x1=0;y1=0;x2=9;y2=9;w=4;h=4|><|mask;x=0;y=0;data=2,2,!!!!!&!!!!!|>",
            // Mask without a box.
            "<0f>0<|mask;x=0;y=0;data=2,2,!!!!!&!!!!!|>",
            // Non-zero mask origin.
            "<0f>0<|box;x1=0;y1=0;x2=1;y2=1;w=4;h=4|><|mask;x=1;y=0;data=2,2,!!!!!&!!!!!|>",
            // Mask bigger than its frame.
            "<0f>0<|box;x1=0;y1=0;x2=1;y2=1;w=4;h=4|><|mask;x=0;y=0;data=9,9,!!!!!&!!!!!|>"
        )
        for (line in bad) {
            try {
                parseSegmentationLine(line)
                fail("expected SamMaskException for: ${line.take(60)}")
            } catch (e: SamMaskException) {
                // Expected.
            }
        }
    }
}
