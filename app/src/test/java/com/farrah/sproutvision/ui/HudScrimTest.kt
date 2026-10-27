package com.farrah.sproutvision.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the HUD translucency contract: the panel scrim is genuinely
 * translucent, and every text backing is at least as dark as the old
 * full-panel scrim — so readability under grow lights cannot regress
 * by retuning a constant.
 */
class HudScrimTest {

    @Test
    fun `text backing is at least as dark as the old scrim`() {
        assertTrue(HUD_TEXT_BACKING_ALPHA >= 0.7f)
    }

    @Test
    fun `panel scrim is translucent but present`() {
        assertTrue(HUD_PANEL_SCRIM_ALPHA < 0.7f)
        assertTrue(HUD_PANEL_SCRIM_ALPHA > 0f)
    }

    @Test
    fun `scrim helpers carry the pinned alphas`() {
        // Color packs alpha to 8 bits (0.3 -> 77/255), so compare loosely.
        assertEquals(HUD_PANEL_SCRIM_ALPHA, hudPanelScrim().alpha, 0.01f)
        assertEquals(HUD_TEXT_BACKING_ALPHA, hudTextBackingColor().alpha, 0.01f)
    }
}
