package com.farrah.sproutvision.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the grow-guide targets shown on the scan result screen: per-variety
 * lookup, the unknown-variety fallback, and the expected-height estimate.
 */
class VarietyGuideTest {

    @Test
    fun `known varieties keep their targets`() {
        val basil = VarietyGuides.forVariety("Basil")
        assertEquals(4, basil.blackoutMinDays)
        assertEquals(6, basil.blackoutMaxDays)
        assertEquals(2.0, basil.harvestMinIn, 0.0)
        assertEquals(3.0, basil.harvestMaxIn, 0.0)
        assertEquals("4-6 days", basil.blackoutLabel())
        assertEquals("2-3 in", basil.harvestLabel())

        val peas = VarietyGuides.forVariety("Pea Shoots")
        assertEquals("3-5 in", peas.harvestLabel())
    }

    @Test
    fun `lookup is case-insensitive with a generic fallback`() {
        assertEquals("Kale", VarietyGuides.forVariety("kale").variety)
        val unknown = VarietyGuides.forVariety("Mystery Mix")
        assertEquals("Mystery Mix", unknown.variety)
        assertEquals("2-3 days", unknown.blackoutLabel())
        assertEquals("1-3 in", unknown.harvestLabel())
    }

    @Test
    fun `expected height scales with age and caps at harvest max`() {
        val kale = VarietyGuides.forVariety("Kale") // harvest 2-3 in, mid 2.5
        assertEquals(0.0, VarietyGuides.expectedHeightInches(kale, 0, 10), 0.0)
        assertEquals(1.25, VarietyGuides.expectedHeightInches(kale, 5, 10), 0.001)
        // Past harvest day the estimate holds at the maximum, never above.
        assertEquals(3.0, VarietyGuides.expectedHeightInches(kale, 20, 10), 0.0)
        assertEquals(0.0, VarietyGuides.expectedHeightInches(kale, 5, 0), 0.0)
    }
}
