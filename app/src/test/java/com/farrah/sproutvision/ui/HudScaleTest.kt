package com.farrah.sproutvision.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the HUD resize scale: identity at design size, proportional
 * shrink/growth, smaller-axis-wins, and safe fallbacks. The design
 * constants are pinned to the panel display sizes in registerPanels.
 */
class HudScaleTest {

    @Test
    fun `full size is identity`() {
        assertEquals(1f, hudScaleFactor(780f, 500f), 0.0001f)
    }

    @Test
    fun `half size halves`() {
        assertEquals(0.5f, hudScaleFactor(390f, 250f), 0.0001f)
    }

    @Test
    fun `smaller axis wins`() {
        assertEquals(0.5f, hudScaleFactor(390f, 500f), 0.0001f)
        assertEquals(0.5f, hudScaleFactor(780f, 250f), 0.0001f)
    }

    @Test
    fun `larger panels scale up`() {
        assertEquals(2f, hudScaleFactor(1560f, 1000f), 0.0001f)
    }

    @Test
    fun `compact design is identity at compact size`() {
        assertEquals(1f, hudScaleFactor(450f, 320f, 450f, 320f), 0.0001f)
    }

    @Test
    fun `degenerate sizes fall back to one`() {
        assertEquals(1f, hudScaleFactor(0f, 500f), 0f)
        assertEquals(1f, hudScaleFactor(780f, -1f), 0f)
        assertEquals(1f, hudScaleFactor(780f, 500f, 0f, 500f), 0f)
    }

    @Test
    fun `design constants match panel display options`() {
        assertEquals(780f, HUD_DESIGN_WIDTH_DP, 0f)
        assertEquals(500f, HUD_DESIGN_HEIGHT_DP, 0f)
        assertEquals(450f, HUD_COMPACT_DESIGN_WIDTH_DP, 0f)
        assertEquals(320f, HUD_COMPACT_DESIGN_HEIGHT_DP, 0f)
    }
}
