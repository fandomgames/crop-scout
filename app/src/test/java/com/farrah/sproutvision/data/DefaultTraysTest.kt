package com.farrah.sproutvision.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Guards the bulletproof seed: dates must be noon America/Chicago (not
 * hardcoded epochs) and the two real trays must keep their spec'd values.
 */
class DefaultTraysTest {

    @Test
    fun `seeded dates are noon Chicago`() {
        // Cross-checked against the documented seed timestamps.
        assertEquals(1790614800000L, DefaultTrays.noonChicagoMillis(LocalDate.parse("2026-09-28")))
        assertEquals(1791651600000L, DefaultTrays.noonChicagoMillis(LocalDate.parse("2026-10-10")))
    }

    @Test
    fun `seed holds the two sample trays`() {
        val seed = DefaultTrays.seed()
        assertEquals(2, seed.size)

        val basil = seed.first { it.name == "Basil" }
        assertEquals("Basil", basil.variety)
        assertEquals(1790614800000L, basil.seededAtEpochMs)
        assertEquals(18, basil.expectedDaysToHarvest)
        assertEquals(GrowthStage.LIGHT, basil.currentStage)
        assertEquals(
            "Patchy germination; the seedlings that came up are healthy.",
            basil.notes
        )

        val kale = seed.first { it.name == "Kale" }
        assertEquals("Kale", kale.variety)
        assertEquals(1791651600000L, kale.seededAtEpochMs)
        assertEquals(10, kale.expectedDaysToHarvest)
        assertEquals(GrowthStage.BLACKOUT, kale.currentStage)
        assertEquals("Under blackout as of Oct 12.", kale.notes)
    }

    @Test
    fun `scan photo filename is trayId_timestamp`() {
        assertEquals("7_123456789.jpg", ScanPhotoStore.filename(7, 123456789L))
    }
}
