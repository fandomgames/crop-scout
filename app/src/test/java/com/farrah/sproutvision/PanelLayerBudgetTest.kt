package com.farrah.sproutvision

import com.farrah.sproutvision.settings.AppSettings
import com.farrah.sproutvision.ui.HudScreen
import com.meta.spatial.toolkit.PanelRenderMode
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the pin-limit fix (greenhouse 2, 6:24): every runtime tray card and
 * ID label used to render on its own compositor layer (the SDK default),
 * so ~12 labels + cards + 5 static panels spent past the 16-layer Horizon
 * budget — black frame, session death, drop to home. Runtime panels must
 * stay meshes; the static layer set must keep headroom under the budget.
 */
class PanelLayerBudgetTest {

    @Test
    fun `runtime cards render as mesh, never layers`() {
        assertTrue(runtimeCardRenderOptions().renderMode is PanelRenderMode.Mesh)
    }

    @Test
    fun `static layer panels keep headroom under the compositor budget`() {
        val home = AppSettings()
        val vertical = home.copy(verticalVideo = true)
        var worst = 0
        for (settings in listOf(home, vertical)) {
            for (clean in listOf(false, true)) {
                for (manage in listOf(false, true)) {
                    for (screen in listOf(HudScreen.Home, HudScreen.Scan(1L))) {
                        for (hideFinder in listOf(false, true)) {
                            val count = layerPanelCount(
                                currentPanelVis(settings, clean, manage, screen, hideFinder)
                            )
                            worst = maxOf(worst, count)
                        }
                    }
                }
            }
        }
        // Today the worst realistic snapshot is 2 co-visible layers; the
        // tripwire at 3 forces this test to be revisited before the static
        // set can silently grow toward the budget.
        assertTrue("worst static layer count $worst exceeds 3", worst <= 3)
        assertTrue(3 < COMPOSITOR_LAYER_BUDGET)
    }
}
