package validation

import com.farrah.sproutvision.analyze.JsonValue
import com.farrah.sproutvision.analyze.SamException
import com.farrah.sproutvision.analyze.SamRecord
import com.farrah.sproutvision.analyze.encodeOneBitRaster
import com.farrah.sproutvision.analyze.parseJsonObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline tests for the golden-run tool: the SAM calls are injected
 * fakes, so no key or network is needed. Mask payloads come from
 * Meta's own encoder port, so composites decode for real.
 */
class GoldenValidationTest {

    private val shot = GoldenShot("shot1-full", "shot1-full.jpg", 70, null)

    private fun record(id: String, raster: ByteArray, w: Int, h: Int): SamRecord =
        SamRecord(id, 0, 0, w - 1, h - 1, w, h, h, w, '!', encodeOneBitRaster(raster, w, h))

    private val full8 = record("0", ByteArray(64) { 1 }, 8, 8)
    private val half8 = record("0", ByteArray(64) { if (it < 32) 1 else 0 }, 8, 8)

    @Test
    fun `runShot measures union over largest tray`() = runBlocking {
        val outcome = runShot(
            ByteArray(4), shot,
            upload = { "file-x" },
            records = { _, concept ->
                if (concept == "black tray") listOf(full8) else listOf(half8)
            }
        )
        assertEquals(50, outcome.sam)
        assertEquals(1, outcome.plants)
        assertEquals(1, outcome.trays)
        assertNull(outcome.failed)
    }

    @Test
    fun `runShot keeps largest tray of several`() = runBlocking {
        val sliver = record("1", ByteArray(64) { 1 }, 8, 8)
            .copy(x1 = 6, x2 = 7)
        val outcome = runShot(
            ByteArray(4), shot,
            upload = { "file-x" },
            records = { _, concept ->
                if (concept == "black tray") listOf(sliver, full8) else listOf(half8)
            }
        )
        // Tray = full 8x8 (64 px), not the sliver: 32/64 = 50%.
        assertEquals(50, outcome.sam)
        assertEquals(2, outcome.trays)
        assertNull(outcome.failed)
    }

    @Test
    fun `zero tray matches means null not crash`() = runBlocking {
        val outcome = runShot(
            ByteArray(4), shot,
            upload = { "file-x" },
            records = { _, concept ->
                if (concept == "black tray") throw SamException("SAM 3.1 returned no usable masks.")
                else listOf(half8)
            }
        )
        assertNull(outcome.sam)
        assertNull(outcome.trays)
        assertTrue(outcome.failed!!.startsWith("tray"))
    }

    @Test
    fun `zero plant matches keeps the tray count`() = runBlocking {
        val outcome = runShot(
            ByteArray(4), shot,
            upload = { "file-x" },
            records = { _, concept ->
                if (concept == "black tray") listOf(full8)
                else throw SamException("SAM 3.1 returned no usable masks.")
            }
        )
        assertNull(outcome.sam)
        assertEquals(1, outcome.trays)
        assertTrue(outcome.failed!!.startsWith("plants"))
    }

    @Test
    fun `upload failure nulls the shot`() = runBlocking {
        val outcome = runShot(
            ByteArray(4), shot,
            upload = { throw SamException("nope") },
            records = { _, _ -> listOf(full8) }
        )
        assertNull(outcome.sam)
        assertTrue(outcome.failed!!.startsWith("upload"))
    }

    @Test
    fun `json renders numbers nulls and notes`() {
        val json = renderJson(
            listOf(
                ShotOutcome(shot, 72, 14, 2, null),
                ShotOutcome(shot.copy(id = "shot3-gap"), null, null, null, "tray (x)")
            )
        )
        assertTrue(json.contains("\"id\":\"shot1-full\""))
        assertTrue(json.contains("\"samCoverage\":72"))
        assertTrue(json.contains("\"difference\":2"))
        assertTrue(json.contains("\"plantInstances\":14"))
        assertTrue(json.contains("\"samCoverage\":null"))
    }

    @Test
    fun `table shows dashes for unavailable shots`() {
        val table = renderTable(
            listOf(
                ShotOutcome(shot, 72, 14, 2, null),
                ShotOutcome(shot.copy(id = "s", byEye = 5), null, null, null, "tray (x)")
            )
        )
        assertTrue(table.contains("| shot1-full | 72 | 70 | +2 |"))
        assertTrue(table.contains("| s | — | 5 | — |"))
    }

    @Test
    fun `json output parses back to the same shots`() {
        val outcomes = listOf(ShotOutcome(shot, 72, 14, 2, null))
        val obj = parseJsonObject(renderJson(outcomes))!!
        val shots = (obj["shots"] as JsonValue.Arr).items
        assertEquals(1, shots.size)
        val first = (shots[0] as JsonValue.Obj).entries
        assertEquals("shot1-full", (first["id"] as JsonValue.Str).value)
        assertEquals("72", (first["samCoverage"] as JsonValue.Other).raw)
        assertEquals("2", (first["difference"] as JsonValue.Other).raw)
    }
}
