package com.farrah.sproutvision

/**
 * Tray-card sync rules shared by the tray-table collector and the
 * scene tick. Both exist because saving a tray must never visibly
 * move an unrelated floating card.
 */

/**
 * Sticky ghost-slot assignment. Ghost fan slots derive from this map,
 * never from list position: inserting, deleting, or reordering trays
 * leaves every surviving ghost exactly where it was. Newcomers take
 * the lowest free slot; anchoring or deleting a tray frees its slot
 * for later newcomers.
 *
 * @param current trayId -> slot assignments that are currently live.
 * @param ghostIds ids of currently unanchored trays, in any order.
 */
internal fun assignGhostSlots(
    current: Map<Long, Int>,
    ghostIds: List<Long>
): Map<Long, Int> {
    val kept = current.filterKeys { it in ghostIds }
    val used = kept.values.toMutableSet()
    val out = kept.toMutableMap()
    for (id in ghostIds) {
        if (id in out) continue
        var slot = 0
        while (slot in used) slot++
        out[id] = slot
        used += slot
    }
    return out
}

/** What one card tick is allowed to do (see [cardTickScope]). */
internal data class CardTickScope(
    /** Re-face anchored cards at their anchors (billboard upkeep). */
    val refreshAnchoredYaw: Boolean,
    /** Glide unanchored ghosts around the view. */
    val trackGhosts: Boolean,
    /** Persist grab-release poses and ghost tap-to-drops. */
    val persistGrabs: Boolean
)

/**
 * Card-tick scope matrix. Anchored yaw refreshes on EVERY tick:
 * gating it on placement mode lets billboards go stale, and the next
 * tray-table emission (a save!) then visibly snaps every anchored
 * card at once. Ghost tracking and grab persistence stay
 * placement-only (existing behavior, preserved).
 */
internal fun cardTickScope(placing: Boolean): CardTickScope = CardTickScope(
    refreshAnchoredYaw = true,
    trackGhosts = placing,
    persistGrabs = placing
)
