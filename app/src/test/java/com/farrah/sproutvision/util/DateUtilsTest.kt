package com.farrah.sproutvision.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Guards calendar-day age math: the Day number derives from dates
 * only, flips at local midnight, and never moves mid-day. Includes
 * the reported basil/beets cases and both DST transitions.
 */
class DateUtilsTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** Epoch millis of a local wall-clock time (zone-robust test data). */
    private fun ms(date: LocalDate, hour: Int, z: ZoneId = zone): Long =
        date.atTime(hour, 0).atZone(z).toInstant().toEpochMilli()

    @Test
    fun `basil seeded Sep 28 reads Day 26 all Oct 24`() {
        val morning = daysSince(ms(LocalDate.of(2026, 9, 28), 19), ms(LocalDate.of(2026, 10, 24), 8))
        val evening = daysSince(ms(LocalDate.of(2026, 9, 28), 19), ms(LocalDate.of(2026, 10, 24), 20))

        assertEquals(26, morning)
        assertEquals(26, evening)
    }

    @Test
    fun `beets seeded Oct 22 read Day 2 all Oct 24`() {
        val morning = daysSince(ms(LocalDate.of(2026, 10, 22), 19), ms(LocalDate.of(2026, 10, 24), 8))
        val evening = daysSince(ms(LocalDate.of(2026, 10, 22), 19), ms(LocalDate.of(2026, 10, 24), 20))

        assertEquals(2, morning)
        assertEquals(2, evening)
    }

    @Test
    fun `seeded day is Day 0 at any hour`() {
        val today = LocalDate.of(2026, 10, 24)

        assertEquals(0, daysSince(ms(today, 0), ms(today, 23)))
        assertEquals(0, daysSince(ms(today, 15), ms(today, 15)))
        assertEquals(0, daysSince(ms(today, 23), ms(today, 0)))
    }

    @Test
    fun `number flips at midnight never mid-day`() {
        val seeded = ms(LocalDate.of(2026, 10, 20), 12)

        assertEquals(3, daysSince(seeded, ms(LocalDate.of(2026, 10, 23), 0)))
        assertEquals(3, daysSince(seeded, ms(LocalDate.of(2026, 10, 23), 23)))
        assertEquals(4, daysSince(seeded, ms(LocalDate.of(2026, 10, 24), 0)))
    }

    @Test
    fun `spring-forward day still counts one`() {
        // US DST starts Mar 8 2026: Mar 7 noon to Mar 8 noon is 23 h.
        val ny = ZoneId.of("America/New_York")

        assertEquals(
            1,
            daysSince(ms(LocalDate.of(2026, 3, 7), 12, ny), ms(LocalDate.of(2026, 3, 8), 12, ny), ny)
        )
    }

    @Test
    fun `fall-back day still counts one`() {
        // US DST ends Nov 1 2026: Nov 1 noon to Nov 2 noon is 25 h.
        val ny = ZoneId.of("America/New_York")

        assertEquals(
            1,
            daysSince(ms(LocalDate.of(2026, 11, 1), 12, ny), ms(LocalDate.of(2026, 11, 2), 12, ny), ny)
        )
    }

    @Test
    fun `future seeded dates clamp to zero`() {
        assertEquals(
            0,
            daysSince(ms(LocalDate.of(2026, 10, 25), 12), ms(LocalDate.of(2026, 10, 24), 12))
        )
    }

    @Test
    fun `dayOn pins the endpoint for history rows`() {
        assertEquals(
            3,
            dayOn(ms(LocalDate.of(2026, 10, 20), 12), ms(LocalDate.of(2026, 10, 23), 9))
        )
    }
}
