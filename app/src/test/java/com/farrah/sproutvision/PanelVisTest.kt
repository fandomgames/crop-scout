package com.farrah.sproutvision

import com.farrah.sproutvision.settings.AppSettings
import com.farrah.sproutvision.ui.HudScreen
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the panel visibility matrix: the same [PanelVis] drives the live
 * visibility loop AND the sleep/wake restore, so a wrong row here would
 * resurrect the wrong panels after wake.
 */
class PanelVisTest {

    private val home = AppSettings()

    @Test
    fun `default home shows main HUD only`() {
        assertEquals(
            PanelVis(
                hud = true,
                hudCompact = false,
                manage = false,
                tripwire = false,
                viewfinder = false
            ),
            currentPanelVis(home, false, false, HudScreen.Home, false)
        )
    }

    @Test
    fun `clean mode shows only the tripwire`() {
        assertEquals(
            PanelVis(
                hud = false,
                hudCompact = false,
                manage = false,
                tripwire = true,
                viewfinder = false
            ),
            currentPanelVis(home, true, false, HudScreen.Home, false)
        )
    }

    @Test
    fun `clean mode hides manage and vertical HUD too`() {
        val vertical = home.copy(verticalVideo = true)
        assertEquals(
            PanelVis(
                hud = false,
                hudCompact = false,
                manage = false,
                tripwire = true,
                viewfinder = false
            ),
            currentPanelVis(vertical, true, true, HudScreen.Home, false)
        )
    }

    @Test
    fun `vertical video swaps in the compact HUD and viewfinder`() {
        val vertical = home.copy(verticalVideo = true)
        assertEquals(
            PanelVis(
                hud = false,
                hudCompact = true,
                manage = false,
                tripwire = false,
                viewfinder = true
            ),
            currentPanelVis(vertical, false, false, HudScreen.Home, false)
        )
    }

    @Test
    fun `manage open adds manage and drops the viewfinder`() {
        val vertical = home.copy(verticalVideo = true)
        assertEquals(
            PanelVis(
                hud = false,
                hudCompact = true,
                manage = true,
                tripwire = false,
                viewfinder = false
            ),
            currentPanelVis(vertical, false, true, HudScreen.Home, false)
        )
    }

    @Test
    fun `viewfinder hides on scan screen and when hidden`() {
        val vertical = home.copy(verticalVideo = true)
        val scan = currentPanelVis(vertical, false, false, HudScreen.Scan(1L), false)
        assertEquals(true, scan.hudCompact)
        assertEquals(false, scan.viewfinder)
        val hidden = currentPanelVis(vertical, false, false, HudScreen.Home, true)
        assertEquals(true, hidden.hudCompact)
        assertEquals(false, hidden.viewfinder)
    }
}
