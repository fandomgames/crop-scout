package com.farrah.sproutvision

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Vector3
import kotlin.math.abs

/**
 * HUD-in-front depth ordering vs anchored tray cards. When the HUD
 * overlaps a card from the viewer's viewpoint, the HUD nudges a small
 * step toward the viewer so depth ordering puts it on top — fully
 * readable, and the HUD never moves laterally. Cards never move; a
 * covered card returns to full view when the HUD closes or moves on.
 *
 * Frame: viewer space in meters, card rects perspectively scaled onto
 * the HUD's depth plane. The tick collects one HUD rect + one rect per
 * visible card, tests overlap ([hudOverlapsCards]), eases the live
 * depth toward its target ([lerpHudDepthM]), and applies the result.
 * Panel half extents below MUST match registerPanels / createTrayCard.
 * User resizes are NOT tracked (the SDK exposes no readable size), so
 * rects use design sizes — an approximation once resized.
 */
internal const val HUD_HALF_W_M = 0.35f // 0.70 m HUD shape
internal const val HUD_HALF_H_M = 0.225f // 0.45 m HUD shape
internal const val HUD_COMPACT_HALF_W_M = 0.225f // 0.45 m compact shape
internal const val HUD_COMPACT_HALF_H_M = 0.16f // 0.32 m compact shape
internal const val CARD_HALF_W_M = 0.25f // 0.50 m card shape
internal const val CARD_HALF_H_M = 0.18f // 0.36 m card shape
internal const val MANAGE_HALF_W_M = 0.525f // 1.05 m Manage shape
internal const val MANAGE_HALF_H_M = 0.35f // 0.70 m Manage shape

/** Viewer-space depths at or below this are behind/too close: ignored. */
internal const val MIN_FRONT_DEPTH_M = 0.05f

/** Overlap test margin: engage slightly early so edges never touch. */
internal const val HUD_FRONT_MARGIN_M = 0.02f

/** Depth step toward the viewer while overlapping (smoothed). */
internal const val HUD_FRONT_NUDGE_M = 0.02f

/** Per-tick glide rate toward the target depth (~0.2 s to settle). */
internal const val HUD_DEPTH_LERP_ALPHA = 0.12f

/** Below this distance the depth snaps exactly for a clean settle. */
internal const val HUD_DEPTH_SNAP_M = 0.0005f

/** Center + half extents in the HUD depth plane, meters. */
internal data class ViewRect(val cx: Float, val cy: Float, val hx: Float, val hy: Float)

/**
 * Viewer-space position of a world point. Rotation semantics of the
 * single-arg Pose constructor do not matter here: only the composed
 * translation is read, which is rotation-independent.
 */
internal fun worldToViewer(viewer: Pose, world: Vector3): Vector3 =
    viewer.inverse().times(Pose(world)).t

/**
 * World-space delta pushing a panel [towardViewerM] meters toward
 * the viewer (-Z in viewer space). Same rotation-independence note
 * as [worldToViewer].
 */
internal fun viewerDepthNudgeToWorldDelta(viewer: Pose, towardViewerM: Float): Vector3 {
    val moved = viewer.times(Pose(Vector3(0f, 0f, -towardViewerM))).t
    val origin = viewer.t
    return Vector3(moved.x - origin.x, moved.y - origin.y, moved.z - origin.z)
}

/**
 * Card rect scaled onto the HUD depth plane (perspective: a distant
 * card subtends a smaller rect). Null when either depth is
 * unusable — those panels cannot meaningfully overlap the HUD.
 */
internal fun projectToHudPlane(
    cardView: Vector3,
    hudDepthM: Float,
    halfWidthM: Float = CARD_HALF_W_M,
    halfHeightM: Float = CARD_HALF_H_M
): ViewRect? {
    if (cardView.z <= MIN_FRONT_DEPTH_M || hudDepthM <= MIN_FRONT_DEPTH_M) return null
    val s = hudDepthM / cardView.z
    return ViewRect(cardView.x * s, cardView.y * s, halfWidthM * s, halfHeightM * s)
}

/** True when the HUD rect touches any card rect (margin included). */
internal fun hudOverlapsCards(
    hud: ViewRect,
    cards: List<ViewRect>,
    marginM: Float = HUD_FRONT_MARGIN_M
): Boolean = cards.any { c ->
    abs(hud.cx - c.cx) < hud.hx + c.hx + marginM &&
        abs(hud.cy - c.cy) < hud.hy + c.hy + marginM
}

/**
 * One glide step for the depth nudge; snaps exactly once close.
 * Clearing overlap is the same path with a ZERO target — the HUD
 * eases home with no special case.
 */
internal fun lerpHudDepthM(
    currentM: Float,
    targetM: Float,
    alpha: Float = HUD_DEPTH_LERP_ALPHA,
    snapM: Float = HUD_DEPTH_SNAP_M
): Float {
    val a = alpha.coerceIn(0f, 1f)
    val next = currentM + (targetM - currentM) * a
    return if (abs(targetM - next) <= snapM) targetM else next
}
