package com.farrah.sproutvision.ui

import com.farrah.sproutvision.data.GrowthStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the staged-act gate both new confirms run through: nothing
 * applies without a staged act, a staged act applies exactly once, and
 * cancel leaves nothing behind. The Apply-stage HARVEST rule matches
 * manual advance (harvest entries ask, everything else flows through).
 */
class ConfirmGateTest {

    @Test
    fun `confirm applies the staged act exactly once`() {
        val (act, next) = takeAct(stageAct("harvest"))
        assertEquals("harvest", act)
        assertEquals(ConfirmGate.Idle, next)
        // Repeat pinch after confirm: nothing left to apply.
        val (again, _) = takeAct(next)
        assertNull(again)
    }

    @Test
    fun `confirm with nothing staged applies nothing`() {
        val (act, next) = takeAct(ConfirmGate.Idle)
        assertNull(act)
        assertEquals(ConfirmGate.Idle, next)
    }

    @Test
    fun `cancel leaves nothing to apply`() {
        var gate: ConfirmGate<String> = stageAct("delete")
        gate = cancelAct()
        val (act, next) = takeAct(gate)
        assertNull(act)
        assertEquals(ConfirmGate.Idle, next)
    }

    @Test
    fun `restaging replaces the pending act without queueing`() {
        var gate: ConfirmGate<String> = stageAct("first")
        gate = stageAct("second")
        val (act, next) = takeAct(gate)
        assertEquals("second", act)
        assertEquals(ConfirmGate.Idle, next)
    }

    @Test
    fun `gates start idle with no prompt and nothing to apply`() {
        val gate: ConfirmGate<String> = ConfirmGate.Idle
        val (act, _) = takeAct(gate)
        assertNull(act)
    }

    @Test
    fun `harvest apply needs confirm only when entering harvest`() {
        assertTrue(applyNeedsConfirm(GrowthStage.HARVEST, GrowthStage.LIGHT))
        assertTrue(applyNeedsConfirm(GrowthStage.HARVEST, GrowthStage.SEEDED))
        // Already harvested: nothing changes, no prompt.
        assertFalse(applyNeedsConfirm(GrowthStage.HARVEST, GrowthStage.HARVEST))
        // Non-harvest applications flow through (reversible via delete).
        assertFalse(applyNeedsConfirm(GrowthStage.LIGHT, GrowthStage.GERMINATION))
        assertFalse(applyNeedsConfirm(GrowthStage.LIGHT, GrowthStage.LIGHT))
        assertFalse(applyNeedsConfirm(GrowthStage.GERMINATION, GrowthStage.LIGHT))
    }
}
