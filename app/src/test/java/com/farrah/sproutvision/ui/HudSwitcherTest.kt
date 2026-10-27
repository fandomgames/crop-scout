package com.farrah.sproutvision.ui

import com.farrah.sproutvision.data.GrowthStage
import com.farrah.sproutvision.data.TrayEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the HUD switcher filter: harvested trays leave the working HUD
 * (Manage/history/scorecard keep them), and a harvested current tray
 * advances the switcher to the next active tray.
 */
class HudSwitcherTest {

    private fun tray(id: Long, stage: GrowthStage) = TrayEntity(
        id = id,
        name = "Tray $id",
        variety = "Kale",
        seededAtEpochMs = 0L,
        expectedDaysToHarvest = 10,
        currentStage = stage
    )

    @Test
    fun `only harvested trays are filtered`() {
        val trays = listOf(
            tray(1, GrowthStage.LIGHT),
            tray(2, GrowthStage.HARVEST),
            tray(3, GrowthStage.GERMINATION)
        )

        assertEquals(listOf(1L, 3L), visibleHudTrays(trays).map { it.id })
    }

    @Test
    fun `active current tray stays selected`() {
        val trays = listOf(
            tray(1, GrowthStage.LIGHT),
            tray(2, GrowthStage.HARVEST),
            tray(3, GrowthStage.LIGHT)
        )

        assertEquals(3L, nextHudTray(trays, 3L)?.id)
    }

    @Test
    fun `harvested current tray advances to the next active tray`() {
        val trays = listOf(
            tray(1, GrowthStage.LIGHT),
            tray(2, GrowthStage.HARVEST),
            tray(3, GrowthStage.LIGHT)
        )

        assertEquals(3L, nextHudTray(trays, 2L)?.id)
    }

    @Test
    fun `advance wraps past the end of the list`() {
        val trays = listOf(
            tray(1, GrowthStage.LIGHT),
            tray(2, GrowthStage.LIGHT),
            tray(3, GrowthStage.HARVEST)
        )

        assertEquals(1L, nextHudTray(trays, 3L)?.id)
    }

    @Test
    fun `unknown current tray starts at the first active tray`() {
        val trays = listOf(
            tray(1, GrowthStage.HARVEST),
            tray(2, GrowthStage.LIGHT)
        )

        assertEquals(2L, nextHudTray(trays, null)?.id)
        assertEquals(2L, nextHudTray(trays, 99L)?.id)
    }

    @Test
    fun `all harvested means empty HUD`() {
        val trays = listOf(
            tray(1, GrowthStage.HARVEST),
            tray(2, GrowthStage.HARVEST)
        )

        assertEquals(true, visibleHudTrays(trays).isEmpty())
        assertNull(nextHudTray(trays, 1L))
    }
}
