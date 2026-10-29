package com.farrah.sproutvision.data

/**
 * Scan-photo lookups over [ScanRecordEntity.photoPath] — the single
 * photo store (filesDir/scans, see [ScanPhotoStore]). Photos are local
 * only: never in the APK, never committed, never published, and never
 * part of the hash-only verdict trace.
 */

/** Placeholder when the latest scan saved no photo — never a fake photo. */
const val SCAN_NO_PHOTO_TEXT = "No photo saved for this scan"

/**
 * Latest scan's photo path for a tray (null when the latest scan has no
 * photo, even if an older scan does). Order-independent: picks by
 * timestamp, not list position.
 */
fun latestScanPhoto(records: List<ScanRecordEntity>): String? =
    records.maxByOrNull { it.timestamp }
        ?.photoPath
        ?.ifBlank { null }

/**
 * One scan's photo path, matching the history record by timestamp (the
 * scan and its record share the capture timestamp). Null when no
 * record matches or the record has no photo.
 */
fun scanPhotoPath(records: List<ScanRecordEntity>, scan: ScanEntity): String? =
    records.firstOrNull { it.timestamp == scan.timestampEpochMs }
        ?.photoPath
        ?.ifBlank { null }
