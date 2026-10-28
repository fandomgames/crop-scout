package com.farrah.sproutvision.analyze

import com.farrah.sproutvision.data.GrowthStage

/** Result of analyzing one tray photo. */
data class AnalysisResult(
    val variety: String,
    val stage: GrowthStage,
    val healthScore: Int, // 0–100
    val issues: List<String>,
    val coveragePercent: Int, // 0–100 canopy coverage
    val recommendation: String,
    val isDemo: Boolean,
    /** False when no grow tray is visible in the photo. */
    val trayVisible: Boolean,
    /** Tray bounding box in 0-1000 normalized coords, null when not visible. */
    val trayBox: TrayBox?,
    /** Analyzer self-reported confidence 0–100. 0 means unknown / not reported. */
    val confidence: Int = 0,
    /** Measured coverage 0–100 from the segmenter, null when unavailable. */
    val samCoverage: Int? = null,
    /** Which segmenter measured [samCoverage], null when unavailable. */
    val samSource: SegmentationSource? = null,
    /** Trace: models label derived at scan time, null for pre-trace results. */
    val modelsUsed: String? = null,
    /** Trace: SHA-256 hex of the captured JPEG bytes, null for pre-trace results. */
    val imageHash: String? = null,
    /** Trace: capture timestamp ms, null for pre-trace results. */
    val scannedAtEpochMs: Long? = null
)

/** Normalized [x0, y0, x1, y1] bounding box, origin top-left, 0-1000 range. */
data class TrayBox(val x0: Int, val y0: Int, val x1: Int, val y1: Int)
