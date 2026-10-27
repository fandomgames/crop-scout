package com.farrah.sproutvision.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the persisted timing-history encoding: round-trip, garbage
 * tolerance, and the sample cap.
 */
class TimingHistoryTest {

    @Test
    fun `round trip preserves values`() {
        val values = listOf(1200L, 8400L, 9100L)
        assertEquals(values, parseMsList(joinMsList(values)))
    }

    @Test
    fun `blank input parses empty`() {
        assertEquals(emptyList<Long>(), parseMsList(null))
        assertEquals(emptyList<Long>(), parseMsList(""))
        assertEquals(emptyList<Long>(), parseMsList("   "))
    }

    @Test
    fun `garbage and non-positive entries are dropped`() {
        assertEquals(
            listOf(800L, 4200L),
            parseMsList("800,abc,, -5,0,4200, 9x")
        )
    }

    @Test
    fun `oldest samples beyond the cap are trimmed`() {
        val values = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L)
        assertEquals(listOf(3L, 4L, 5L, 6L, 7L), parseMsList(joinMsList(values)))
        assertEquals(5, MAX_TIMING_SAMPLES)
    }
}
