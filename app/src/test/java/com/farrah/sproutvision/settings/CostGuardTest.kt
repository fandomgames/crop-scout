package com.farrah.sproutvision.settings

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the money rules: one detection per admitted scan, midnight
 * rollover, a hard cap that stops SAM (not a warning), and the
 * Settings display. The store and ViewModel are thin delegates over
 * these transitions.
 */
class CostGuardTest {

    @Test
    fun `one scan records exactly one detection`() {
        assertEquals(
            "2026-10-28" to 13,
            recordTransition("2026-10-28", 12, "2026-10-28")
        )
        assertTrue(shouldRecordDetection(null, 100L))
        assertFalse(shouldRecordDetection(100L, 100L))
        assertTrue(shouldRecordDetection(100L, 101L))
    }

    @Test
    fun `counter resets on a new calendar day`() {
        assertEquals(
            "2026-10-28" to 1,
            recordTransition("2026-10-27", 12, "2026-10-28")
        )
        assertEquals(0, countForToday("2026-10-27", 12, "2026-10-28"))
        assertEquals(12, countForToday("2026-10-28", 12, "2026-10-28"))
        assertEquals(0, countForToday("2026-10-28", -3, "2026-10-28"))
    }

    @Test
    fun `at cap SAM stops with the limit note`() {
        assertFalse(samAllowed(25, 25))
        assertFalse(samAllowed(27, 25))
        assertEquals(
            "daily detection limit reached, 25/day — raise it in Settings",
            limitReachedNote(25)
        )
        assertEquals(
            "25 / 25 detections today — daily limit reached",
            detectionDisplay(25, 25)
        )
    }

    @Test
    fun `below cap SAM runs normally`() {
        assertTrue(samAllowed(0, 25))
        assertTrue(samAllowed(24, 25))
        assertEquals("12 / 25 detections today", detectionDisplay(12, 25))
    }

    @Test
    fun `raising the cap re-enables SAM the same day`() {
        assertFalse(samAllowed(25, 25))
        assertTrue(samAllowed(25, 50))
    }

    @Test
    fun `cap clamps to the sane range`() {
        assertEquals(1, clampCap(0))
        assertEquals(1, clampCap(-5))
        assertEquals(999, clampCap(1000))
        assertEquals(25, clampCap(25))
        assertEquals(25, DEFAULT_DAILY_DETECTION_CAP)
    }

    @Test
    fun `today string is the local calendar date`() {
        // 2026-10-28T12:00:00Z, read in UTC.
        assertEquals("2026-10-28", todayString(1793188800000L, ZoneId.of("UTC")))
    }
}
