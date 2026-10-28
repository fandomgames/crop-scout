package com.farrah.sproutvision.analyze

import com.farrah.sproutvision.data.ScanEntity
import com.farrah.sproutvision.data.ScanRecordEntity
import com.farrah.sproutvision.util.formatDateTime
import java.security.MessageDigest

/**
 * Pinned verdict trace: the four receipts persisted with every scan —
 * the models that actually ran, the SAM-measured coverage, the capture
 * timestamp, and the SHA-256 of the captured JPEG bytes. All fields are
 * nullable: scans saved before the trace feature read back nulls and
 * render as —, never invented.
 */
data class VerdictTrace(
    val models: String?,
    val samCoverage: Int?,
    val timestampMs: Long?,
    val imageHash: String?
)

/** Display strings for one trace; every null field renders as "—". */
data class VerdictTraceDisplay(
    val models: String,
    val coverage: String,
    val timestamp: String,
    val imageHash: String
)

/**
 * Maps a trace to its display strings. Null coverage renders as "—",
 * never as 0%: a missing measurement is not a bare tray.
 */
fun formatVerdictTrace(trace: VerdictTrace): VerdictTraceDisplay =
    VerdictTraceDisplay(
        models = trace.models ?: "—",
        coverage = trace.samCoverage?.let { "$it%" } ?: "—",
        timestamp = trace.timestampMs?.let { formatDateTime(it) } ?: "—",
        imageHash = trace.imageHash ?: "—"
    )

/**
 * Models label derived from the scan state — the analyzer branch that
 * ran, whether SAM measured, and the SAM failure detail when it didn't.
 * Never hardcoded per screen: the ViewModel passes its actual branch
 * decisions ([demo]/[useMeta] mirror the analyzer selection,
 * [samMeasured] is true only for a real SAM measurement, and
 * [samErrorDetail] is the SAM failure message, sanitized here).
 */
fun traceModelsLabel(
    demo: Boolean,
    useMeta: Boolean,
    samMeasured: Boolean,
    samErrorDetail: String?
): String {
    if (demo) return "demo"
    if (!useMeta) return "gemini"
    if (samMeasured) return "sam-3.1 + muse-spark"
    val reason = samErrorDetail
        ?.replace('\n', ' ')?.replace('\r', ' ')?.trim()?.take(160)
    return if (reason.isNullOrBlank()) "muse-spark only"
    else "muse-spark only ($reason)"
}

/**
 * SHA-256 hex of the captured bytes, computed in-memory at scan time.
 * Hash-only: the trace stores this string, never the photo.
 */
fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return buildString(digest.size * 2) {
        digest.forEach { append("%02x".format(it)) }
    }
}

/** Trace for the verdict card, from the in-memory result (pre-save). */
fun AnalysisResult.verdictTrace(): VerdictTrace =
    VerdictTrace(modelsUsed, samCoverage, scannedAtEpochMs, imageHash)

/** Trace for a history entry, from its persisted record (old rows: nulls → —). */
fun ScanRecordEntity.verdictTrace(): VerdictTrace =
    VerdictTrace(modelsUsed, samCoverage, timestamp, imageHash)

/** Trace for a per-tray history entry, from its persisted scan (old rows: nulls → —). */
fun ScanEntity.verdictTrace(): VerdictTrace =
    VerdictTrace(modelsUsed, samCoverage, timestampEpochMs, imageHash)
