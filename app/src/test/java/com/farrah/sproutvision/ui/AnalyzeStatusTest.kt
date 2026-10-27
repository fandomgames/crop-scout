package com.farrah.sproutvision.ui

import com.farrah.sproutvision.analyze.AnalysisResult
import com.farrah.sproutvision.analyze.TrayBox
import com.farrah.sproutvision.data.GrowthStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the analyzing-status formatters and the framing-coaching rules
 * (tray missing / cut off / too small / off-center).
 */
class AnalyzeStatusTest {

    @Test
    fun `elapsed formats tenths of a second`() {
        assertEquals("0.0s", formatElapsed(0))
        assertEquals("0.4s", formatElapsed(450))
        assertEquals("4.2s", formatElapsed(4200))
        assertEquals("61.5s", formatElapsed(61500))
    }

    @Test
    fun `typical rounds to whole seconds with a measuring fallback`() {
        assertEquals("measuring…", formatTypical(null))
        assertEquals("usually ~8s", formatTypical(8400))
        assertEquals("usually ~9s", formatTypical(8600))
    }

    private fun result(visible: Boolean, box: TrayBox?) = AnalysisResult(
        variety = "Kale",
        stage = GrowthStage.LIGHT,
        healthScore = 80,
        issues = emptyList(),
        coveragePercent = 85,
        recommendation = "",
        isDemo = false,
        trayVisible = visible,
        trayBox = box
    )

    @Test
    fun `missing tray asks the grower to aim`() {
        assertEquals(
            "No tray visible — aim the headset at the tray.",
            framingAdvice(result(false, null))
        )
    }

    @Test
    fun `clipped tray asks to pull back`() {
        assertEquals(
            "Tray is cut off at the frame edge — pull back or recenter.",
            framingAdvice(result(true, TrayBox(0, 200, 600, 800)))
        )
    }

    @Test
    fun `small tray reports its frame share and asks to move closer`() {
        assertEquals(
            "Tray fills only 1% of the frame — move closer.",
            framingAdvice(result(true, TrayBox(400, 400, 500, 500)))
        )
    }

    @Test
    fun `off-center tray names the direction`() {
        assertEquals(
            "Tray is left of center — aim a bit left.",
            framingAdvice(result(true, TrayBox(100, 300, 400, 700)))
        )
        assertEquals(
            "Tray is low in frame — aim a bit down.",
            framingAdvice(result(true, TrayBox(300, 550, 700, 900)))
        )
    }

    @Test
    fun `good framing and boxless demo results stay silent`() {
        assertNull(framingAdvice(result(true, TrayBox(300, 200, 700, 800))))
        assertNull(framingAdvice(result(true, null)))
    }
}
