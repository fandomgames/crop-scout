package com.farrah.sproutvision.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards camera ownership between the two HUD panels: exactly one panel
 * may open the passthrough camera (a second openCamera disconnects the
 * first session). Main owns it normally, compact in 9:16 mode.
 */
class ScanCameraTest {

    @Test
    fun `main panel owns the camera in normal mode`() {
        assertEquals(true, scanCameraEnabled(panelIsCompact = false, verticalHudActive = false))
        assertEquals(false, scanCameraEnabled(panelIsCompact = true, verticalHudActive = false))
    }

    @Test
    fun `compact panel owns the camera in vertical mode`() {
        assertEquals(false, scanCameraEnabled(panelIsCompact = false, verticalHudActive = true))
        assertEquals(true, scanCameraEnabled(panelIsCompact = true, verticalHudActive = true))
    }
}
