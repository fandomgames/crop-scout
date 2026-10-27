package com.farrah.sproutvision.data

import com.farrah.sproutvision.analyze.IdentifyConfidence
import com.farrah.sproutvision.analyze.PlantCareSheet
import com.farrah.sproutvision.analyze.PlantIdentification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the herbarium Save mapping: an explicit Save files the photo,
 * date, names, and location note, and reopening restores the full card —
 * including an unknown confidence reopening as a best guess, never certain.
 */
class HerbariumMappingTest {

    private fun sample() = PlantIdentification(
        commonName = "Purple Coneflower",
        scientificName = "Echinacea purpurea",
        family = "Asteraceae",
        confidence = IdentifyConfidence.MEDIUM,
        care = PlantCareSheet(
            sun = "full sun",
            water = "Medium",
            soil = "Loam",
            hardinessZones = "3–9",
            bloomTime = "Midsummer to fall",
            matureSize = "2–4 ft tall",
            funFact = "Goldfinches love the seed heads."
        ),
        betterPhotoTip = "Get closer to the flower head."
    )

    @Test
    fun `save files photo date names and location note`() {
        val entry = herbariumEntryOf(
            result = sample(),
            photoPath = "/files/wildplants/wild_123.jpg",
            timestampMs = 1791651600000L,
            locationNote = "Petrifying Springs, Oct 19"
        )

        assertEquals("/files/wildplants/wild_123.jpg", entry.photoPath)
        assertEquals(1791651600000L, entry.timestampEpochMs)
        assertEquals("Purple Coneflower", entry.commonName)
        assertEquals("Echinacea purpurea", entry.scientificName)
        assertEquals("Asteraceae", entry.family)
        assertEquals("MEDIUM", entry.confidence)
        assertEquals("Petrifying Springs, Oct 19", entry.locationNote)
        assertEquals("full sun", entry.sun)
        assertEquals("Get closer to the flower head.", entry.betterPhotoTip)
    }

    @Test
    fun `reopen restores the full card`() {
        val entry = herbariumEntryOf(sample(), null, 1791651600000L, "")

        assertNull(entry.photoPath)
        val reopened = entry.toIdentification()

        assertEquals(sample().copy(isDemo = false), reopened)
    }

    @Test
    fun `unknown saved confidence reopens as best guess`() {
        val entry = herbariumEntryOf(sample(), null, 0L, "").copy(confidence = "bogus")

        assertEquals(IdentifyConfidence.LOW, entry.toIdentification().confidence)
    }
}
