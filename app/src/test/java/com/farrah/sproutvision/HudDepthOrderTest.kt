package com.farrah.sproutvision

import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards HUD-in-front depth ordering: viewer-frame projection with
 * the real SDK math types, the overlap test (including its exact
 * boundary), and the depth glide. NOTE: the SDK Quaternion
 * constructor is (w, x, y, z).
 */
class HudDepthOrderTest {

    private val identityViewer = Pose(Vector3(0f, 0f, 0f), Quaternion(1f, 0f, 0f, 0f))

    @Test
    fun `worldToViewer is exact for translated viewers`() {
        val viewer = Pose(Vector3(1f, 2f, 3f), Quaternion(1f, 0f, 0f, 0f))

        val v = worldToViewer(viewer, Vector3(4f, 6f, 8f))

        assertEquals(3f, v.x, 1e-6f)
        assertEquals(4f, v.y, 1e-6f)
        assertEquals(5f, v.z, 1e-6f)
    }

    @Test
    fun `depth nudge round-trips through a rotated viewer`() {
        // Non-trivial viewer: translated + yawed. Rotation must preserve
        // the nudge length, and view->world->view must be identity.
        val viewer = Pose(Vector3(0.3f, 1.6f, 0.5f), Quaternion(0.8f, 0f, 0.6f, 0f))

        val delta = viewerDepthNudgeToWorldDelta(viewer, 0.02f)
        val len = kotlin.math.sqrt(delta.x * delta.x + delta.y * delta.y + delta.z * delta.z)
        assertEquals(0.02f, len, 1e-6f)

        val vt = viewer.t
        val back = worldToViewer(viewer, Vector3(vt.x + delta.x, vt.y + delta.y, vt.z + delta.z))
        assertEquals(0f, back.x, 1e-4f)
        assertEquals(0f, back.y, 1e-4f)
        assertEquals(-0.02f, back.z, 1e-4f)
    }

    @Test
    fun `identity viewer passes the nudge through untouched`() {
        val delta = viewerDepthNudgeToWorldDelta(identityViewer, 0.02f)

        assertEquals(0f, delta.x, 1e-6f)
        assertEquals(0f, delta.y, 1e-6f)
        assertEquals(-0.02f, delta.z, 1e-6f)
    }

    @Test
    fun `projection scales cards onto the HUD plane`() {
        val same = projectToHudPlane(Vector3(0.2f, 0.1f, 1f), 1f, 0.25f, 0.18f)!!
        assertEquals(0.2f, same.cx, 1e-6f)
        assertEquals(0.1f, same.cy, 1e-6f)
        assertEquals(0.25f, same.hx, 1e-6f)
        assertEquals(0.18f, same.hy, 1e-6f)

        val half = projectToHudPlane(Vector3(0.2f, 0.1f, 2f), 1f, 0.25f, 0.18f)!!
        assertEquals(0.1f, half.cx, 1e-6f)
        assertEquals(0.05f, half.cy, 1e-6f)
        assertEquals(0.125f, half.hx, 1e-6f)
        assertEquals(0.09f, half.hy, 1e-6f)
    }

    @Test
    fun `projection rejects unusable depths`() {
        assertNull(projectToHudPlane(Vector3(0f, 0f, 0f), 1f))
        assertNull(projectToHudPlane(Vector3(0f, 0f, -1f), 1f))
        assertNull(projectToHudPlane(Vector3(0f, 0f, 1f), 0f))
    }

    @Test
    fun `overlap detects touching rects with margin`() {
        val hud = ViewRect(0f, 0f, 0.35f, 0.225f)

        assertTrue(hudOverlapsCards(hud, listOf(ViewRect(0.5f, 0f, 0.25f, 0.18f))))
        assertTrue(hudOverlapsCards(hud, listOf(ViewRect(0f, 0.3f, 0.25f, 0.18f))))
        assertFalse(hudOverlapsCards(hud, listOf(ViewRect(2f, 0f, 0.25f, 0.18f))))
        assertFalse(hudOverlapsCards(hud, emptyList()))
    }

    @Test
    fun `overlap boundary is exact at edge contact`() {
        // Binary-exact halves: edge contact (0.5 == 0.25 + 0.25 + 0.0)
        // is NOT overlap; any penetration is.
        val hud = ViewRect(0f, 0f, 0.25f, 0.25f)

        assertFalse(hudOverlapsCards(hud, listOf(ViewRect(0.5f, 0f, 0.25f, 0.25f)), 0f))
        assertTrue(hudOverlapsCards(hud, listOf(ViewRect(0.25f, 0f, 0.25f, 0.25f)), 0f))
    }

    @Test
    fun `depth glides and snaps`() {
        val half = lerpHudDepthM(0f, 0.02f, alpha = 0.5f)
        assertEquals(0.01f, half, 1e-6f)

        // Within snap distance: returns the target verbatim.
        assertEquals(0.02f, lerpHudDepthM(0.0199f, 0.02f), 0f)

        // Outside snap distance with default alpha: one small step.
        assertEquals(0.0024f, lerpHudDepthM(0f, 0.02f), 1e-6f)
    }

    @Test
    fun `depth alpha clamps to unit range`() {
        assertEquals(0.02f, lerpHudDepthM(0f, 0.02f, alpha = 5f), 0f)
        assertEquals(0f, lerpHudDepthM(0f, 0.02f, alpha = -1f), 0f)
    }

    @Test
    fun `panel half extents match registered shapes`() {
        assertEquals(0.35f, HUD_HALF_W_M, 0f)
        assertEquals(0.225f, HUD_HALF_H_M, 0f)
        assertEquals(0.225f, HUD_COMPACT_HALF_W_M, 0f)
        assertEquals(0.16f, HUD_COMPACT_HALF_H_M, 0f)
        assertEquals(0.25f, CARD_HALF_W_M, 0f)
        assertEquals(0.18f, CARD_HALF_H_M, 0f)
        assertEquals(0.525f, MANAGE_HALF_W_M, 0f)
        assertEquals(0.35f, MANAGE_HALF_H_M, 0f)
    }
}
