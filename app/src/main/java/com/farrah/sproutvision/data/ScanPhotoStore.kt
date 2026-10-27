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
}
