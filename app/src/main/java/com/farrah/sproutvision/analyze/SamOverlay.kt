package com.farrah.sproutvision.analyze

import android.graphics.Bitmap

/**
 * Translucent green tint over the counted plant pixels, drawn on top of
 * the tray photo in the verdict card so the grower sees what was
 * counted. Same dims as the segmentation grid; the card scales it onto
 * the photo with the same content scale, so it stays aligned.
 */
fun overlayBitmap(plantGrid: IntArray, width: Int, height: Int): Bitmap {
    require(plantGrid.size == width * height) { "Grid size ${plantGrid.size} != $width x $height" }
    val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val tint = 0x60_00FF00.toInt()
    val pixels = IntArray(width * height) { i -> if (plantGrid[i] == 1) tint else 0 }
    out.setPixels(pixels, 0, width, 0, 0, width, height)
    return out
}
