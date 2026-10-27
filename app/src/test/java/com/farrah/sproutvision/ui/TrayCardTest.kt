package com.farrah.sproutvision.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards floating tray-card visibility: anchored cards always show (except
 * in clean mode); unanchored ghosts only during placement mode.
 */
class TrayCardTest {

    @Test
    fun `anchored cards show outside clean mode`() {
        assertEquals(true, trayCardVisible(true, false, false))
        assertEquals(true, trayCardVisible(true, true, false))
        assertEquals(false, trayCardVisible(true, false, true))
        assertEquals(false, trayCardVisible(true, true, true))
    }

    @Test
    fun `ghosts show only while placing and never when clean`() {
        assertEquals(false, trayCardVisible(false, false, false))
        assertEquals(true, trayCardVisible(false, true, false))
        assertEquals(false, trayCardVisible(false, true, true))
    }
}
