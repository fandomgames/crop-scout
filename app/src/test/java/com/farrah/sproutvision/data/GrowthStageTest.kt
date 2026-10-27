package com.farrah.sproutvision.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the harvest-confirm rule: only the step INTO harvest asks for
 * confirmation (harvested trays leave the working HUD).
 */
class GrowthStageTest {

    @Test
    fun `only the step into harvest needs confirmation`() {
        assertEquals(false, GrowthStage.SEEDED.needsHarvestConfirm())
        assertEquals(false, GrowthStage.GERMINATION.needsHarvestConfirm())
        assertEquals(false, GrowthStage.BLACKOUT.needsHarvestConfirm())
        assertEquals(true, GrowthStage.LIGHT.needsHarvestConfirm())
        assertEquals(false, GrowthStage.HARVEST.needsHarvestConfirm())
    }
}
