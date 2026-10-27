package com.farrah.sproutvision.ui

import com.meta.spatial.core.Quaternion
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the billboard primitive tray cards depend on: stripping pitch and
 * roll from any tilted rotation yields an upright yaw-only quaternion.
 */
class CardBillboardTest {

    @Test
    fun `removePitchAndRoll yields upright yaw-only rotation`() {
        val tilted = Quaternion(0.2f, 0.5f, 0.3f, 0.8f).normalize()

        val upright = tilted.removePitchAndRoll()

        assertEquals(0f, upright.x, 1e-5f)
        assertEquals(0f, upright.z, 1e-5f)
        assertEquals(1f, upright.norm(), 1e-4f)
    }
}
