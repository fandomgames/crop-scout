package com.farrah.sproutvision.analyze

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Green-dominance test for one pixel. Plants under white grow light
 * read green-dominant; soil, tray plastic, and shadows do not. A
 * heuristic, not a measurement — the card labels it "Demo-measured".
 */
internal fun isCanopyPixel(r: Int, g: Int, b: Int): Boolean =
    g > 60 && g > r + 12 && g > b + 12

/** Pure green-threshold over ARGB pixels → row-major 0/1 grid. */
internal fun thresholdGrid(pixels: IntArray, width: Int, height: Int): IntArray {
    require(pixels.size == width * height) { "Pixel count ${pixels.size} != $width x $height" }
    return IntArray(pixels.size) { i ->
        val p = pixels[i]
        if (isCanopyPixel((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)) 1 else 0
    }
}

/**
 * Offline fallback segmenter: green-pixel threshold, no network. Used
 * when SAM is enabled but no Meta key is configured, and in demo mode
 * (which must stay offline-safe). The tray is approximated as the full
 * frame, so [Segmentation.trayBox] is always null.
 */
class DemoSegmenter : Segmenter {

    override suspend fun segment(
        imageBytes: ByteArray,
        onEvent: (String) -> Unit
    ): Segmentation = withContext(Dispatchers.Default) {
        onEvent("Segmenting (demo)…")
        val decoded = try {
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (_: Exception) {
            null
        } ?: throw SegmentationException("Demo segmenter could not decode the photo.")
        // 320 on the long edge is plenty for a green-pixel count.
        val scale = 320f / maxOf(decoded.width, decoded.height).coerceAtLeast(1)
        val w = (decoded.width * scale).toInt().coerceAtLeast(1)
        val h = (decoded.height * scale).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(decoded, w, h, true)
        if (small !== decoded) decoded.recycle()
        try {
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            val plantGrid = thresholdGrid(pixels, w, h)
            val trayGrid = IntArray(w * h) { 1 }
            val result = SamSegmenter.fromMasks(trayGrid, plantGrid, w, h, label = "Demo")
            onEvent(result.summary)
            Segmentation(
                trayGrid = trayGrid,
                plantGrid = plantGrid,
                width = w,
                height = h,
                result = result,
                source = SegmentationSource.DEMO,
                trayBox = null
            )
        } finally {
            small.recycle()
        }
    }
}
