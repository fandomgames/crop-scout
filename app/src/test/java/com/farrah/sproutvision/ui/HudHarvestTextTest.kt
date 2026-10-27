package com.farrah.sproutvision.ui

import com.farrah.sproutvision.data.GrowIntent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the HUD card's harvest line: microgreen trays keep the exact
 * countdown wording; grow-out trays get no countdown and no overdue
 * warnings.
 */
class HudHarvestTextTest {

    @Test
    fun `microgreen countdown wording is unchanged`() {
        assertEquals(
            "Harvest in ~5 d",
            hudHarvestText(5, 10, GrowIntent.MICROGREEN)
        )
        assertEquals(
            "Harvest today",
            hudHarvestText(10, 10, GrowIntent.MICROGREEN)
        )
        assertEquals(
            "Harvest overdue",
            hudHarvestText(12, 10, GrowIntent.MICROGREEN)
        )
    }

    @Test
    fun `grow-out trays show no countdown`() {
        assertEquals("growing out", hudHarvestText(5, 10, GrowIntent.GROW_OUT))
        assertEquals("growing out", hudHarvestText(30, 10, GrowIntent.GROW_OUT))
    }

    @Test
    fun `unset expected days shows no-harvest-date instead of a countdown`() {
        assertEquals("no harvest date", hudHarvestText(5, null, GrowIntent.MICROGREEN))
    }

    @Test
    fun `grow-out wins over unset expected days`() {
        assertEquals("growing out", hudHarvestText(5, null, GrowIntent.GROW_OUT))
    }
}
