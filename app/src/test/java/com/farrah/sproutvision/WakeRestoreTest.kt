package com.farrah.sproutvision

import com.farrah.sproutvision.ui.HudScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the wake watchdog: auto-recreate fires only for a dead session
 * past the cooldown (no infinite recreate loops), and only resumable HUD
 * screens survive the recreation (mid-scan state cannot).
 */
class WakeRestoreTest {

    @Test
    fun `recreates for a dead session with no recent recreate`() {
        assertTrue(shouldAutoRecreate(nowMs = 100_000L, lastRecreateMs = 0L, sessionAliveSinceResume = false))
    }

    @Test
    fun `skips recreate while the session is alive`() {
        assertFalse(shouldAutoRecreate(nowMs = 100_000L, lastRecreateMs = 0L, sessionAliveSinceResume = true))
    }

    @Test
    fun `skips recreate inside the cooldown window`() {
        assertFalse(
            shouldAutoRecreate(
                nowMs = 100_000L,
                lastRecreateMs = 99_000L,
                sessionAliveSinceResume = false,
                minIntervalMs = 30_000L
            )
        )
    }

    @Test
    fun `recreates again once the cooldown has passed`() {
        assertTrue(
            shouldAutoRecreate(
                nowMs = 200_000L,
                lastRecreateMs = 100_000L,
                sessionAliveSinceResume = false,
                minIntervalMs = 30_000L
            )
        )
    }

    @Test
    fun `home and identify screens survive recreation`() {
        assertEquals(HudScreen.Home, restoreHudScreen(HudScreen.Home))
        assertEquals(HudScreen.Identify, restoreHudScreen(HudScreen.Identify))
    }

    @Test
    fun `mid-scan screens fall back to home`() {
        assertEquals(HudScreen.Home, restoreHudScreen(HudScreen.Scan(1L)))
        assertEquals(HudScreen.Home, restoreHudScreen(HudScreen.Result(1L)))
        assertEquals(HudScreen.Home, restoreHudScreen(HudScreen.IdentifyResult))
    }
}
