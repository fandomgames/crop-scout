package com.farrah.sproutvision.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the pH/EC log rules: optional-either saves, blank renders as
 * a dash (never 0), newest-first history, range flags only against set
 * ranges, and exact stepper math (no float drift, clamped, seeded).
 */
class PhEcTest {

    private fun reading(id: Long, ms: Long, ph: Double?, ec: Double?) =
        PhEcReading(id = id, trayId = 1, timestampEpochMs = ms, ph = ph, ec = ec)

    @Test
    fun `pH-only entry saves and blank EC shows a dash`() {
        assertTrue(canSavePhEc(6.0, null))
        assertEquals("6.0", formatPhEc(6.0))
        assertEquals("—", formatPhEc(null))
    }

    @Test
    fun `entry with both pH and EC saves`() {
        assertTrue(canSavePhEc(6.0, 1.2))
        assertTrue(canSavePhEc(null, 1.2))
    }

    @Test
    fun `empty entry cannot save`() {
        assertFalse(canSavePhEc(null, null))
    }

    @Test
    fun `history orders newest first with latest on top`() {
        val rows = listOf(
            reading(id = 1, ms = 100L, ph = 6.0, ec = null),
            reading(id = 3, ms = 300L, ph = 6.5, ec = 1.2),
            reading(id = 2, ms = 200L, ph = null, ec = 1.0)
        )
        assertEquals(listOf(3L, 2L, 1L), latestFirst(rows).map { it.id })
        assertEquals(3L, latestReading(rows)!!.id)
        assertNull(latestReading(emptyList()))
    }

    @Test
    fun `same-millisecond ties break by newest id`() {
        val rows = listOf(
            reading(id = 5, ms = 100L, ph = 6.0, ec = null),
            reading(id = 6, ms = 100L, ph = 6.5, ec = null)
        )
        assertEquals(listOf(6L, 5L), latestFirst(rows).map { it.id })
        assertEquals(6L, latestReading(rows)!!.id)
    }

    @Test
    fun `out-of-range flags only against set ranges`() {
        assertTrue(isOutOfRange(7.5, 5.8, 6.2))
        assertTrue(isOutOfRange(5.5, 5.8, 6.2))
        assertFalse(isOutOfRange(6.0, 5.8, 6.2))
        // Blank value or blank range never flags (EC especially).
        assertFalse(isOutOfRange(null, 5.8, 6.2))
        assertFalse(isOutOfRange(7.5, null, 6.2))
        assertFalse(isOutOfRange(7.5, 5.8, null))
        assertFalse(isOutOfRange(7.5, null, null))
        assertEquals(" (low)", rangeSuffix(5.5, 5.8, 6.2))
        assertEquals(" (high)", rangeSuffix(7.5, 5.8, 6.2))
        assertEquals("", rangeSuffix(6.0, 5.8, 6.2))
        assertEquals("", rangeSuffix(7.5, null, null))
    }

    @Test
    fun `pH stepper moves in exact halves`() {
        assertEquals(7.0, stepReadingPh(null, true), 0.0)
        assertEquals(7.0, stepReadingPh(null, false), 0.0)
        assertEquals(6.5, stepReadingPh(7.0, false), 0.0)
        assertEquals(6.0, stepReadingPh(6.5, false), 0.0)
        assertEquals(9.0, stepReadingPh(9.0, true), 0.0)
        assertEquals(4.0, stepReadingPh(4.0, false), 0.0)
    }

    @Test
    fun `EC stepper moves in exact tenths without drift`() {
        assertEquals(1.0, stepReadingEc(null, true), 0.0)
        // 0.2 + 0.1 must be exactly 0.3, not 0.30000000000000004.
        assertEquals(0.3, stepReadingEc(0.2, true), 0.0)
        assertEquals(1.1, stepReadingEc(1.0, true), 0.0)
        assertEquals(4.0, stepReadingEc(4.0, true), 0.0)
        assertEquals(0.0, stepReadingEc(0.0, false), 0.0)
    }

    @Test
    fun `target steppers seed sensibly and move in tenths`() {
        assertEquals(5.8, stepTargetPhLow(null, true), 0.0)
        assertEquals(6.2, stepTargetPhHigh(null, false), 0.0)
        assertEquals(5.9, stepTargetPhLow(5.8, true), 0.0)
        assertEquals(0.0, stepTargetEcLow(null, true), 0.0)
        assertEquals(4.0, stepTargetEcHigh(null, false), 0.0)
        assertEquals(0.1, stepTargetEcLow(0.0, true), 0.0)
    }

    @Test
    fun `new trays carry the basil pH default and blank EC`() {
        val tray = TrayEntity(
            name = "T1",
            variety = "Basil",
            seededAtEpochMs = 0L,
            expectedDaysToHarvest = null
        )
        assertEquals(5.8, tray.phTargetLow!!, 0.0)
        assertEquals(6.2, tray.phTargetHigh!!, 0.0)
        assertNull(tray.ecTargetLow)
        assertNull(tray.ecTargetHigh)
    }
}
