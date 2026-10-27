package com.farrah.sproutvision.analyze

/**
 * Downsampled frame statistics for live viewfinder coaching. All values
 * are fractions in 0..1.
 */
data class FramingStats(
    /** Mean frame luma: near 0 is darkness, near 1 is blown out. */
    val brightness: Float,
    /** Green-vegetation or brown-soil pixels: the grow tray itself. */
    val plantSoilFrac: Float,
    /** Bright white/gray pixels: tray edges, walls, background. */
    val brightNeutralFrac: Float
)

// Live-coach thresholds — tune in-headset. v1 heuristics: young trays are
// mostly brown soil (not green), so "tray visible" counts soil too, and a
// frame with no bright neutrals at all is nose-against-the-canopy close.
private const val DARK_LUMA = 0.10f
private const val MIN_PLANT_SOIL = 0.08f
private const val TOO_CLOSE_PLANT_SOIL = 0.90f
private const val TOO_CLOSE_BRIGHT_MAX = 0.03f

/**
 * Samples every [stride]-th pixel of ARGB [pixels] ([width] × [height])
 * into [FramingStats]. Pure JVM — the camera feeds it downsampled pixels.
 */
fun computeFramingStats(
    pixels: IntArray,
    width: Int,
    height: Int,
    stride: Int = 16
): FramingStats {
    var lumaSum = 0.0
    var plantSoil = 0
    var brightNeutral = 0
    var n = 0
    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val p = pixels[y * width + x]
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            val luma = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            lumaSum += luma
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            // Vegetation (strictly green dominant, not near-black) or
            // grow-medium brown (red over blue, mid tones). Strictness
            // keeps gray walls and white trays out of the plant count.
            if ((g > r && g > b && g > 30) ||
                (r > b && r >= 40 && luma < 0.6)
            ) {
                plantSoil++
            }
            if (min > 180 && max - min < 40) brightNeutral++
            n++
            x += stride
        }
        y += stride
    }
    if (n == 0) return FramingStats(0f, 0f, 0f)
    return FramingStats(
        brightness = (lumaSum / n).toFloat(),
        plantSoilFrac = plantSoil.toFloat() / n,
        brightNeutralFrac = brightNeutral.toFloat() / n
    )
}

/**
 * Live viewfinder coaching for the aiming preview, or null when framing
 * looks fine. Checked in order: darkness, tray missing, too close.
 */
fun framingAdviceNow(stats: FramingStats): String? = when {
    stats.brightness < DARK_LUMA -> "Frame is very dark"
    stats.plantSoilFrac < MIN_PLANT_SOIL -> "Tray out of view — aim at the tray"
    stats.plantSoilFrac > TOO_CLOSE_PLANT_SOIL &&
        stats.brightNeutralFrac < TOO_CLOSE_BRIGHT_MAX ->
        "Too close — pull back a little"
    else -> null
}
