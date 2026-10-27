package com.farrah.sproutvision.analyze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the wild-plant ID contract: every field of the identification
 * card parses from Gemini's JSON, and low confidence always reads as a
 * best guess paired with a better-photo tip — never as certain.
 */
class PlantIdentificationParserTest {

    private val fullJson = """
        {"commonName":"Purple Coneflower","scientificName":"Echinacea purpurea",
        "family":"Asteraceae","confidence":"high",
        "betterPhotoTip":"Get closer to the flower head.",
        "careSheet":{"sun":"full sun","water":"Medium; drought-tolerant once established.",
        "soil":"Average, well-drained loam.","hardinessZones":"3–9",
        "bloomTime":"Midsummer to fall","matureSize":"2–4 ft tall",
        "funFact":"Goldfinches love the seed heads."}}
    """.trimIndent()

    @Test
    fun `full identification parses every card field`() {
        val id = parsePlantIdentification(fullJson)

        assertEquals("Purple Coneflower", id.commonName)
        assertEquals("Echinacea purpurea", id.scientificName)
        assertEquals("Asteraceae", id.family)
        assertEquals(IdentifyConfidence.HIGH, id.confidence)
        assertEquals("Get closer to the flower head.", id.betterPhotoTip)
        assertEquals("full sun", id.care.sun)
        assertEquals("Medium; drought-tolerant once established.", id.care.water)
        assertEquals("Average, well-drained loam.", id.care.soil)
        assertEquals("3–9", id.care.hardinessZones)
        assertEquals("Midsummer to fall", id.care.bloomTime)
        assertEquals("2–4 ft tall", id.care.matureSize)
        assertEquals("Goldfinches love the seed heads.", id.care.funFact)
    }

    @Test
    fun `low confidence reads as best guess with photo tip`() {
        val id = parsePlantIdentification(
            """{"commonName":"Some Daisy","confidence":"low",
            "betterPhotoTip":"Capture the leaf arrangement."}"""
        )

        assertEquals(IdentifyConfidence.LOW, id.confidence)
        assertEquals("Best guess — low confidence", identifyBadgeText(id.confidence))
        assertEquals("Capture the leaf arrangement.", id.betterPhotoTip)
    }

    @Test
    fun `badge copy never presents medium or low as certain`() {
        assertEquals("High confidence", identifyBadgeText(IdentifyConfidence.HIGH))
        assertEquals("Likely — medium confidence", identifyBadgeText(IdentifyConfidence.MEDIUM))
        assertEquals("Best guess — low confidence", identifyBadgeText(IdentifyConfidence.LOW))
    }

    @Test
    fun `unknown confidence reads as low, never certain`() {
        assertEquals(IdentifyConfidence.LOW, IdentifyConfidence.fromName("bogus"))
        assertEquals(IdentifyConfidence.LOW, IdentifyConfidence.fromName(null))
        assertEquals(IdentifyConfidence.LOW, IdentifyConfidence.fromName(""))
        assertEquals(IdentifyConfidence.HIGH, IdentifyConfidence.fromName("High"))
        assertEquals(IdentifyConfidence.MEDIUM, IdentifyConfidence.fromName("MEDIUM"))
    }

    @Test
    fun `missing fields fall back to honest defaults`() {
        val id = parsePlantIdentification("""{"confidence":"low"}""")

        assertEquals("Unknown plant", id.commonName)
        assertEquals("", id.scientificName)
        assertEquals("", id.family)
        assertEquals(IdentifyConfidence.LOW, id.confidence)
        // A missing tip still says what photo would help.
        assertTrue(id.betterPhotoTip.isNotBlank())
        assertEquals(PlantCareSheet(), id.care)
    }

    @Test
    fun `markdown-fenced JSON still parses`() {
        val id = parsePlantIdentification("```json\n$fullJson\n```")
        assertEquals("Purple Coneflower", id.commonName)
        assertEquals(IdentifyConfidence.HIGH, id.confidence)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-JSON fails loudly`() {
        parsePlantIdentification("sorry, no plant here")
    }
}
