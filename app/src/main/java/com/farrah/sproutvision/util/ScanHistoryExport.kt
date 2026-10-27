package com.farrah.sproutvision.util

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Outcome of a scan-history CSV export. */
sealed interface ScanExportResult {
    /** No scans exist yet — nothing was written. */
    data object Empty : ScanExportResult
    /** CSV written to Downloads; [fileName] is the display name. */
    data class Saved(val fileName: String) : ScanExportResult
    /** The write failed (storage unavailable, etc.). */
    data object Failed : ScanExportResult
}

/** One row of the scan-history CSV: date, tray, variety, day, health, top_issue. */
data class ScanCsvRow(
    val date: String,
    val tray: String,
    val variety: String,
    val day: Int,
    val health: Int,
    val topIssue: String
)

/**
 * Scan-history CSV export for compositing over timelapse footage in DaVinci.
 * [buildCsv] is pure (and unit-tested); [writeToDownloads] drops the file in
 * the shared Downloads collection via MediaStore, which needs no storage
 * permission for app-created files on Horizon OS / Android 10+.
 */
object ScanHistoryExport {
    const val HEADER = "date,tray,variety,day,health,top_issue"

    fun buildCsv(rows: List<ScanCsvRow>): String = buildString {
        appendLine(HEADER)
        for (r in rows) {
            appendLine(
                listOf(
                    escape(r.date),
                    escape(r.tray),
                    escape(r.variety),
                    r.day.toString(),
                    r.health.toString(),
                    escape(r.topIssue)
                ).joinToString(",")
            )
        }
    }

    private fun escape(field: String): String {
        if (field.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return field
        return "\"" + field.replace("\"", "\"\"") + "\""
    }

    /**
     * Writes [csv] to Downloads as `sproutvision_scans_<stamp>.csv`.
     * @return the display name on success, null on failure.
     */
    suspend fun writeToDownloads(context: Context, csv: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val name = "sproutvision_scans_$stamp.csv"
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val resolver = context.applicationContext.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null
                resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
                    ?: return@withContext null
                name
            } catch (_: Exception) {
                null
            }
        }
}
