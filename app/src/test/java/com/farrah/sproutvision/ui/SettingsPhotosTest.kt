package com.farrah.sproutvision.ui

import com.farrah.sproutvision.util.formatDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings "Scan photos" shortcut: each tray row navigates to that
 * tray's existing photo history (Manage History screen, reused — no
 * second photo viewer), with a last-scan subtitle or the no-scans
 * placeholder. The History screen itself is read-only; deletes stay
 * in TrayDetail and Manage with their existing confirms.
 */
class SettingsPhotosTest {

    @Test
    fun `shortcut opens the tray's own photo history`() {
        assertEquals(ManageScreen.History(7L), scanPhotosTarget(7L))
    }

    @Test
    fun `every tray maps to its own history, none cross-wired`() {
        val targets = listOf(1L, 2L, 3L).map(::scanPhotosTarget)
        assertEquals(
            listOf(
                ManageScreen.History(1L),
                ManageScreen.History(2L),
                ManageScreen.History(3L)
            ),
            targets
        )
    }

    @Test
    fun `empty tray list maps to no targets, no crash`() {
        assertTrue(emptyList<Long>().map(::scanPhotosTarget).isEmpty())
    }

    @Test
    fun `subtitle shows last scan date`() {
        val timestamp = 1_729_000_000_000L
        assertEquals("Last scan ${formatDate(timestamp)}", scanPhotosSubtitle(timestamp))
    }

    @Test
    fun `tray without scans shows the placeholder`() {
        assertEquals("No scans yet", scanPhotosSubtitle(null))
    }
}
