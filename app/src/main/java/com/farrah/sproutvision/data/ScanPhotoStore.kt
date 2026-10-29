package com.farrah.sproutvision.data

import android.content.Context
import java.io.File

/** Persists captured scan JPEGs under filesDir/scans/<trayId>_<timestamp>.jpg. */
object ScanPhotoStore {
    const val DIR_NAME = "scans"

    fun filename(trayId: Long, timestamp: Long): String = "${trayId}_${timestamp}.jpg"

    /** Writes [bytes] and returns the absolute path for the scan record. */
    fun save(context: Context, trayId: Long, timestamp: Long, bytes: ByteArray): String {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val file = File(dir, filename(trayId, timestamp))
        file.writeBytes(bytes)
        return file.absolutePath
    }

    fun scansDir(context: Context): File = File(context.filesDir, DIR_NAME)

    /** Deletes one photo file. Null/blank/missing paths are a no-op false. */
    fun delete(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        return try {
            File(path).delete()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deletes every scan photo for one tray (filename prefix match), so
     * deleting a tray leaves no orphaned JPEGs. Returns files removed.
     */
    fun deleteForTray(scansDir: File, trayId: Long): Int =
        deleteMatching(scansDir) { it.startsWith("${trayId}_") && it.endsWith(".jpg") }

    /** Deletes every scan photo. Returns files removed. */
    fun deleteAll(scansDir: File): Int =
        deleteMatching(scansDir) { it.endsWith(".jpg") }

    private fun deleteMatching(scansDir: File, match: (String) -> Boolean): Int {
        val files = try {
            scansDir.listFiles()
        } catch (_: Exception) {
            null
        } ?: return 0
        var removed = 0
        for (file in files) {
            if (!match(file.name)) continue
            try {
                if (file.delete()) removed++
            } catch (_: Exception) {
                // Best-effort cleanup: a locked file stays, the delete stands.
            }
        }
        return removed
    }
}
