package com.farrah.sproutvision.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the 9:16 viewfinder guide: visible only while vertical mode is
 * ON with panels up and Manage closed — never on the scan screen, but
 * kept for results so the verdict stays framed for the recording.
 */
class VerticalViewfinderTest {

    @Test
    fun `viewfinder shows only with mode on and panels up`() {
        assertEquals(true, verticalViewfinderVisible(true, false, false, HudScreen.Home))
        assertEquals(false, verticalViewfinderVisible(false, false, false, HudScreen.Home))
        assertEquals(false, verticalViewfinderVisible(true, true, false, HudScreen.Home))
        assertEquals(false, verticalViewfinderVisible(true, false, true, HudScreen.Home))
    }

    @Test
    fun `viewfinder hides for the scan screen but stays for results`() {
        assertEquals(false, verticalViewfinderVisible(true, false, false, HudScreen.Scan(7L)))
        assertEquals(true, verticalViewfinderVisible(true, false, false, HudScreen.Result(7L)))
    }

    @Test
    fun `hide toggle removes the guide but nothing else`() {
        assertEquals(
            false,
            verticalViewfinderVisible(true, false, false, HudScreen.Home, hideViewfinder = true)
        )
        assertEquals(
            true,
            verticalViewfinderVisible(true, false, false, HudScreen.Home, hideViewfinder = false)
        )
    }
}
