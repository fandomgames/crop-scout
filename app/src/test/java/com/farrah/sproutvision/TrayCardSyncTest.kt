package com.farrah.sproutvision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the save-must-not-move-cards rules: ghost slots survive
 * insert/delete/reorder, and the tick matrix keeps anchored
 * billboards fresh while placement-only work stays gated.
 */
class TrayCardSyncTest {

    @Test
    fun `fresh ghosts take slots in order`() {
        assertEquals(
            mapOf(7L to 0, 9L to 1, 4L to 2),
            assignGhostSlots(emptyMap(), listOf(7L, 9L, 4L))
        )
    }

    @Test
    fun `inserting a tray keeps every surviving ghost put`() {
        val before = mapOf(7L to 0, 9L to 1)

        // Newcomer first, last, and middle: survivors never move.
        assertEquals(mapOf(7L to 0, 9L to 1, 12L to 2), assignGhostSlots(before, listOf(12L, 7L, 9L)))
        assertEquals(mapOf(7L to 0, 9L to 1, 12L to 2), assignGhostSlots(before, listOf(7L, 9L, 12L)))
        assertEquals(mapOf(7L to 0, 9L to 1, 12L to 2), assignGhostSlots(before, listOf(7L, 12L, 9L)))
    }

    @Test
    fun `reordering never re-slots ghosts`() {
        val slots = mapOf(7L to 0, 9L to 1, 4L to 2)

        assertEquals(slots, assignGhostSlots(slots, listOf(4L, 9L, 7L)))
    }

    @Test
    fun `deleting frees the slot and newcomers reuse lowest free`() {
        val afterDelete = assignGhostSlots(mapOf(7L to 0, 9L to 1, 4L to 2), listOf(7L, 4L))
        assertEquals(mapOf(7L to 0, 4L to 2), afterDelete)

        assertEquals(
            mapOf(7L to 0, 4L to 2, 12L to 1),
            assignGhostSlots(afterDelete, listOf(7L, 4L, 12L))
        )
    }

    @Test
    fun `empty in means empty out`() {
        assertEquals(emptyMap<Long, Int>(), assignGhostSlots(mapOf(7L to 0), emptyList()))
        assertEquals(emptyMap<Long, Int>(), assignGhostSlots(emptyMap(), emptyList()))
    }

    @Test
    fun `anchored yaw refreshes on every tick`() {
        assertTrue(cardTickScope(placing = true).refreshAnchoredYaw)
        assertTrue(cardTickScope(placing = false).refreshAnchoredYaw)
    }

    @Test
    fun `ghosts and grab persistence stay placement-only`() {
        val placing = cardTickScope(placing = true)
        assertTrue(placing.trackGhosts)
        assertTrue(placing.persistGrabs)

        val idle = cardTickScope(placing = false)
        assertFalse(idle.trackGhosts)
        assertFalse(idle.persistGrabs)
    }
}
