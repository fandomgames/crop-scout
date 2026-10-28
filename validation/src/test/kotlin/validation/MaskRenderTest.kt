package validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import javax.imageio.ImageIO

/**
 * Offline tests for the mask-render diagnosis PNGs (no key or network;
 * the grids are hand-built, not SAM output).
 */
class MaskRenderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `mask render marks plants white tray box red and rest black`() {
        // 4x4 grid, plants at (1,1) and (2,2), full-frame tray box.
        val grid = IntArray(16)
        grid[1 * 4 + 1] = 1
        grid[2 * 4 + 2] = 1
        val out = tmp.newFile("mask.png")
        renderMaskPng(grid, 4, 4, 0, 0, 3, 3, out, outline = 1)
        assertTrue(out.isFile)
        val img = ImageIO.read(out)
        assertEquals(4, img.width)
        assertEquals(4, img.height)
        fun rgb(x: Int, y: Int) = img.getRGB(x, y) and 0xFFFFFF
        assertEquals(0xFFFFFF, rgb(1, 1))
        assertEquals(0xFFFFFF, rgb(2, 2))
        assertEquals(0xFF0000, rgb(0, 0)) // box outline through the corner
        assertEquals(0x000000, rgb(2, 1)) // bare soil stays black
    }
}
