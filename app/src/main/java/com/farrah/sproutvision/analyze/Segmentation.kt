package com.farrah.sproutvision.analyze

/** Thrown when segmentation fails; the caller falls back to a verdict without measurement. */
class SegmentationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Which segmenter produced a measurement — the card labels each honestly. */
enum class SegmentationSource { META, DEMO }

/**
 * One segmentation run: the tray mask and the unioned plant mask plus the
 * metrics computed from them. Grids are row-major 0/1, [width] x [height],
 * in the uploaded photo's pixels (≤640 on the long edge).
 */
/**
 * One-line SAM failure flag shared by the analyzing feed and the
 * result card: names the real failure (HTTP status, auth, parse
 * error, turned-off-in-Settings) instead of a bare "unavailable".
 */
internal fun samFailureFlag(error: Throwable?): String {
    val detail = (error?.message ?: "")
        .replace('\n', ' ').replace('\r', ' ').trim().take(160)
    return if (detail.isEmpty()) "SAM measurement unavailable"
    else "SAM measurement unavailable: $detail"
}

data class Segmentation(
    val trayGrid: IntArray,
    val plantGrid: IntArray,
    val width: Int,
    val height: Int,
    val result: SegmentationResult,
    val source: SegmentationSource,
    /** Union box of the tray records in 0-1000 coords, null when unknown (demo). */
    val trayBox: TrayBox? = null
)
