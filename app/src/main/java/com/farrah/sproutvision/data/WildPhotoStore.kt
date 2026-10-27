package com.farrah.sproutvision.data

import android.content.Context
import java.io.File

/** Persists captured wild-plant JPEGs under filesDir/wildplants/wild_<timestamp>.jpg. */
object WildPhotoStore {
    const val DIR_NAME = "wildplants"

    fun filename(timestamp: Long): String = "wild_${timestamp}.jpg"

    /** Writes [bytes] and returns the absolute path for the herbarium entry. */
    fun save(context: Context, timestamp: Long, bytes: ByteArray): String {
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        val file = File(dir, filename(timestamp))
        file.writeBytes(bytes)
        return file.absolutePath
    }
}
