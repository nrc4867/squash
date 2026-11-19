package dev.chieppa.squash.screens.look.compare

import kotlin.test.Test
import kotlin.test.assertEquals

class SheetCropTest {
    /** A [width] by [height] silhouette covering [boxes]. */
    private fun sheet(width: Int, height: Int, vararg boxes: PixelBox): Silhouette {
        val covered = BooleanArray(width * height) { at -> boxes.any { at % width in it.left until it.right && at / width in it.top until it.bottom } }
        return Silhouette(width, height, covered)
    }

    @Test
    fun `views in a row split at the gaps between them, left to right`() {
        val front = PixelBox(10, 20, 60, 180)
        val side = PixelBox(90, 25, 150, 180)
        val back = PixelBox(180, 20, 230, 180)
        assertEquals(listOf(front, side, back), sheetViews(sheet(240, 200, side, back, front)))
    }

    @Test
    fun `a view made of pieces with a narrow gap stays whole and a small label is dropped`() {
        val head = PixelBox(20, 10, 60, 50)
        val body = PixelBox(15, 51, 65, 150)
        val label = PixelBox(25, 170, 45, 176)
        val other = PixelBox(120, 10, 170, 150)
        assertEquals(listOf(PixelBox(15, 10, 65, 150), other), sheetViews(sheet(200, 200, head, body, label, other)))
    }

    @Test
    fun `rows of views split by rows, then within each row`() {
        val a = PixelBox(10, 10, 50, 80)
        val b = PixelBox(80, 10, 120, 80)
        val c = PixelBox(10, 110, 50, 190)
        assertEquals(listOf(a, b, c), sheetViews(sheet(140, 200, c, b, a)))
    }

    @Test
    fun `a sheet showing nothing has no views`() {
        assertEquals(emptyList(), sheetViews(sheet(50, 50)))
    }
}
