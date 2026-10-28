package validation

import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders one shot's measurement as a standalone PNG: black background,
 * white plant pixels, red tray-box outline. Deliberately NOT overlaid on
 * the photo — the golden JPEGs are stored 4000x3000 with EXIF rotation
 * while SAM's frame is the 3000x4000 display orientation, so a naive
 * overlay would misalign. View side by side with the photo instead.
 * Mac-side diagnosis only; never ships in the APK. Headless-safe.
 */
fun renderMaskPng(
    plantGrid: IntArray,
    width: Int,
    height: Int,
    boxX1: Int,
    boxY1: Int,
    boxX2: Int,
    boxY2: Int,
    outFile: File,
    outline: Int = (width / 500).coerceAtLeast(3)
) {
    require(plantGrid.size == width * height) { "Plant grid size ${plantGrid.size} != $width x $height" }
    require(width > 0 && height > 0) { "Mask dimensions must be positive" }
    val pixels = IntArray(width * height) { i -> if (plantGrid[i] == 1) 0xFFFFFF else 0x000000 }
    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    img.setRGB(0, 0, width, height, pixels, 0, width)
    val g = img.createGraphics()
    try {
        g.color = Color.RED
        g.stroke = BasicStroke(outline.toFloat())
        g.drawRect(boxX1, boxY1, boxX2 - boxX1, boxY2 - boxY1)
    } finally {
        g.dispose()
    }
    outFile.parentFile?.mkdirs()
    if (!ImageIO.write(img, "png", outFile)) {
        throw IllegalStateException("No PNG writer available.")
    }
}
