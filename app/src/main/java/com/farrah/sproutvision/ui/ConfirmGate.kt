package com.farrah.sproutvision.ui

import com.farrah.sproutvision.data.GrowthStage

/**
 * Staged-act gate for irreversible acts: the act waits in [Staged] until
 * the grower pinches confirm, and applies exactly once via [takeAct]
 * (taking clears the gate, so a repeat pinch finds [Idle]). Cancel
 * returns to [Idle] with nothing applied. Pure so the once-only
 * contract stays unit-tested; screens hold the value in remember and
 * mirror the existing inline-confirm UX (tap only, no new gestures,
 * no dialogs).
 */
sealed interface ConfirmGate<out T> {
    data object Idle : ConfirmGate<Nothing>
    data class Staged<T>(val act: T) : ConfirmGate<T>
}

/** Stage an act for confirmation (replaces any previously staged act). */
fun <T> stageAct(act: T): ConfirmGate<T> = ConfirmGate.Staged(act)

/** Dismiss a staged act without applying it. */
fun cancelAct(): ConfirmGate<Nothing> = ConfirmGate.Idle

/**
 * Takes the staged act exactly once: (act, Idle) when staged,
 * (null, Idle) when idle. The caller applies the act only when non-null.
 */
fun <T> takeAct(gate: ConfirmGate<T>): Pair<T?, ConfirmGate<T>> =
    if (gate is ConfirmGate.Staged) gate.act to ConfirmGate.Idle
    else null to ConfirmGate.Idle

/**
 * "Apply stage" needs its own confirm only when it would move the tray
 * into HARVEST — the same rule as manual advance
 * ([GrowthStage.needsHarvestConfirm]): harvested trays leave the HUD,
 * and the grower may cut based on it. Any other application is
 * reversible through the existing stage-entry delete + rollback, and
 * the result screen itself already stages the verdict until tapped.
 */
fun applyNeedsConfirm(detected: GrowthStage, current: GrowthStage): Boolean =
    detected == GrowthStage.HARVEST && current != GrowthStage.HARVEST
