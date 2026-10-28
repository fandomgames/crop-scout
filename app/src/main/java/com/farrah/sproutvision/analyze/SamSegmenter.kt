package com.farrah.sproutvision.analyze

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure measurement math for a SAM 3.1 mask — no network, no model call.
 *
 * A mask is a grid of 0/1 values: 1 = canopy, 0 = not canopy.
 * This is deliberately separate from the SAM API call itself, because the
 * SAM response shape (mask encoding, image vs. video mode) must be verified
 * against Meta's current docs at kickoff. Once the real mask is decoded
 * into a 0/1 grid, everything below is testable offline against trays
 * Farrah has already judged by eye.
 */
data class SegmentationResult(
    /** Canopy pixels as a percent of the whole tray mask area (0-100). */
    val coveragePercent: Int,
    /** Largest empty patch, as a percent of tray area (0-100). */
    val largestGapPercent: Int,
    /** 0 = perfectly even canopy, 100 = all canopy in one quadrant. */
    val unevennessScore: Int,
    /** Human-readable summary to feed to Muse Spark. */
    val summary: String
)

object SamSegmenter {

    /**
     * Compute coverage, largest gap, and unevenness from a binary mask.
     * @param mask Row-major 0/1 values, length must equal width * height.
     */
    fun fromMask(mask: IntArray, width: Int, height: Int): SegmentationResult {
        require(mask.size == width * height) { "Mask size ${mask.size} != $width x $height" }
        require(width > 0 && height > 0) { "Mask dimensions must be positive" }

        val canopy = mask.count { it == 1 }
        val coverage = (canopy * 100 / mask.size).coerceIn(0, 100)

        // Unevenness: canopy share per quadrant, compared to the mean share.
        val halfW = width / 2
        val halfH = height / 2
        val quadrants = IntArray(4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y * width + x] == 1) {
                    val q = (if (y >= halfH) 2 else 0) + (if (x >= halfW) 1 else 0)
                    quadrants[q]++
                }
            }
        }
        val mean = if (canopy > 0) canopy / 4.0 else 0.0
        val deviation = if (mean > 0) {
            quadrants.map { abs(it - mean) / mean }.average()
        } else 0.0
        val unevenness = (deviation * 100).toInt().coerceIn(0, 100)

        // Largest gap: coarse 4x4 grid, emptiest cell as a percent of tray area.
        var emptiest = 0
        val grid = 4
        for (gy in 0 until grid) {
            for (gx in 0 until grid) {
                var empty = 0
                var total = 0
                for (y in gy * height / grid until (gy + 1) * height / grid) {
                    for (x in gx * width / grid until (gx + 1) * width / grid) {
                        total++
                        if (mask[y * width + x] == 0) empty++
                    }
                }
                if (total > 0) emptiest = maxOf(emptiest, empty * 100 / total)
            }
        }
        // Scale the emptiest cell back to tray-area terms (each cell is 1/16).
        val largestGap = (emptiest / grid / grid).coerceIn(0, 100)

        val summary = "SAM coverage $coverage%, largest gap ~$largestGap% of tray, " +
            "unevenness $unevenness/100"
        return SegmentationResult(coverage, largestGap, unevenness, summary)
    }

    /**
     * Coverage from two masks: unioned plant pixels ÷ tray-mask pixels.
     * Gap/unevenness reuse [fromMask] on the plant grid unchanged.
     * Plants are a subset of the tray area, so more plant pixels than
     * tray pixels proves the tray mask is wrong (rim-only or sliver)
     * and throws — the caller reports the measurement unavailable
     * (null, shown as —), never a clamped 100%.
     * @param label measurement source for the summary ("SAM" or "Demo").
     */
    fun fromMasks(
        trayGrid: IntArray,
        plantGrid: IntArray,
        width: Int,
        height: Int,
        label: String = "SAM"
    ): SegmentationResult {
        require(trayGrid.size == width * height) { "Tray grid size ${trayGrid.size} != $width x $height" }
        val tray = trayGrid.count { it == 1 }
        require(tray > 0) { "Tray mask is empty." }
        val canopy = plantGrid.count { it == 1 }
        require(canopy <= tray) {
            "Plant mask ($canopy px) exceeds tray mask ($tray px): tray segmentation is invalid."
        }
        val base = fromMask(plantGrid, width, height)
        // No clamp: canopy <= tray makes over-100 impossible, and any
        // impossible ratio must throw above rather than read 100%.
        val coverage = canopy * 100 / tray
        val summary = "$label coverage $coverage%, largest gap " +
            "~${base.largestGapPercent}% of tray, unevenness ${base.unevennessScore}/100"
        return base.copy(coveragePercent = coverage, summary = summary)
    }

    /**
     * Coverage from a plant grid plus the tray's bounding box: plant
     * pixels clipped to the box ÷ box pixels. SAM's "black tray" mask
     * covers only visible plastic (rim), so mask pixels understate the
     * tray — the box spans the full tray extent including soil and
     * canopy. Gap/unevenness are tray-relative (computed on the
     * box-cropped grid). Clipping makes over-100 impossible, so there
     * is no clamp and no throw for oversized plants.
     * @param x1 y1 x2 y2 inclusive tray box in grid pixels, inside the frame.
     * @param label measurement source for the summary ("SAM" or "Demo").
     */
    fun fromBox(
        plantGrid: IntArray,
        width: Int,
        height: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        label: String = "SAM"
    ): SegmentationResult {
        require(plantGrid.size == width * height) { "Plant grid size ${plantGrid.size} != $width x $height" }
        require(width > 0 && height > 0) { "Mask dimensions must be positive" }
        require(x1 in 0 until width && x2 in 0 until width && x2 >= x1) {
            "Tray box x-range [$x1, $x2] is outside the 0..${width - 1} frame."
        }
        require(y1 in 0 until height && y2 in 0 until height && y2 >= y1) {
            "Tray box y-range [$y1, $y2] is outside the 0..${height - 1} frame."
        }
        val boxW = x2 - x1 + 1
        val boxH = y2 - y1 + 1
        val cropped = IntArray(boxW * boxH) { i ->
            plantGrid[(y1 + i / boxW) * width + (x1 + i % boxW)]
        }
        val base = fromMask(cropped, boxW, boxH)
        val summary = "$label coverage ${base.coveragePercent}%, largest gap " +
            "~${base.largestGapPercent}% of tray, unevenness ${base.unevennessScore}/100"
        return base.copy(summary = summary)
    }

    /** Quick self-check: a half-covered mask must report ~50% coverage. */
    fun selfCheck(): Boolean {
        val mask = IntArray(100) { if (it < 50) 1 else 0 }
        return fromMask(mask, 10, 10).coveragePercent == 50
    }
}
