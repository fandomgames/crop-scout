package com.farrah.sproutvision.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scan-photo rules: the expanded card reads the latest scan's photo,
 * history rows match their own scan's photo by timestamp, missing
 * photos read null (the UI shows placeholder text, never a fake
 * photo), and deletes remove the JPEGs so no orphans remain.
 */
class ScanPhotoTest {

    private fun record(trayId: Long, timestamp: Long, photoPath: String? = "/scans/p.jpg") =
        ScanRecordEntity(
            trayId = trayId,
            timestamp = timestamp,
            healthPct = 80,
            coveragePct = 15,
            photoPath = photoPath
        )

    private fun scan(trayId: Long, timestamp: Long) = ScanEntity(
        trayId = trayId,
        timestampEpochMs = timestamp,
        variety = "Basil",
        stage = GrowthStage.LIGHT,
        healthScore = 80,
        coveragePercent = 15
    )

    @Test
    fun `expanded card reads the latest scan photo`() {
        val records = listOf(
            record(1L, 1000L, "/scans/old.jpg"),
            record(1L, 2000L, "/scans/new.jpg")
        )
        assertEquals("/scans/new.jpg", latestScanPhoto(records))
        // Order-independent: newest by timestamp, not list position.
        assertEquals("/scans/new.jpg", latestScanPhoto(records.reversed()))
    }

    @Test
    fun `latest scan without a photo reads null for the placeholder`() {
        assertEquals("No photo saved for this scan", SCAN_NO_PHOTO_TEXT)
        assertNull(latestScanPhoto(emptyList()))
        assertNull(latestScanPhoto(listOf(record(1L, 1000L, null))))
        assertNull(latestScanPhoto(listOf(record(1L, 1000L, "  "))))
        // Newest scan has no photo even though an older one does.
        assertNull(
            latestScanPhoto(
                listOf(record(1L, 1000L, "/scans/old.jpg"), record(1L, 2000L, null))
            )
        )
    }

    @Test
    fun `history row matches its own scan photo by timestamp`() {
        val records = listOf(
            record(1L, 1000L, "/scans/first.jpg"),
            record(1L, 2000L, "/scans/second.jpg")
        )
        assertEquals("/scans/first.jpg", scanPhotoPath(records, scan(1L, 1000L)))
        assertEquals("/scans/second.jpg", scanPhotoPath(records, scan(1L, 2000L)))
        assertNull(scanPhotoPath(records, scan(1L, 3000L)))
        assertNull(scanPhotoPath(emptyList(), scan(1L, 1000L)))
    }

    @Test
    fun `deleting one photo removes exactly that file`() {
        val dir = Files.createTempDirectory("scanphoto").toFile()
        try {
            val keep = File(dir, "1_1000.jpg").apply { writeText("keep") }
            val target = File(dir, "1_2000.jpg").apply { writeText("drop") }

            assertTrue(ScanPhotoStore.delete(target.absolutePath))
            assertFalse(target.exists())
            assertTrue(keep.exists())

            assertFalse(ScanPhotoStore.delete(null))
            assertFalse(ScanPhotoStore.delete("  "))
            assertFalse(ScanPhotoStore.delete(File(dir, "missing.jpg").absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `deleting a tray removes its photos and keeps others`() {
        val dir = Files.createTempDirectory("scanphoto").toFile()
        try {
            File(dir, "1_1000.jpg").apply { writeText("a") }
            File(dir, "1_2000.jpg").apply { writeText("b") }
            File(dir, "2_1000.jpg").apply { writeText("c") }
            File(dir, "notes.txt").apply { writeText("d") }

            assertEquals(2, ScanPhotoStore.deleteForTray(dir, 1L))
            assertFalse(File(dir, "1_1000.jpg").exists())
            assertFalse(File(dir, "1_2000.jpg").exists())
            assertTrue(File(dir, "2_1000.jpg").exists())
            assertTrue(File(dir, "notes.txt").exists())

            assertEquals(0, ScanPhotoStore.deleteForTray(dir, 9L))
            assertEquals(0, ScanPhotoStore.deleteForTray(File(dir, "absent"), 1L))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `clear-all removes every scan photo`() {
        val dir = Files.createTempDirectory("scanphoto").toFile()
        try {
            File(dir, "1_1000.jpg").apply { writeText("a") }
            File(dir, "2_1000.jpg").apply { writeText("b") }

            assertEquals(2, ScanPhotoStore.deleteAll(dir))
            assertTrue((dir.listFiles() ?: emptyArray()).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
