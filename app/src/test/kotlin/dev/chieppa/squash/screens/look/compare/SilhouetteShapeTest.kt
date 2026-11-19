package dev.chieppa.squash.screens.look.compare

import kotlin.test.Test
import kotlin.test.assertEquals

class SilhouetteShapeTest {
    private fun dot(width: Int, height: Int, x: Int, y: Int) = Silhouette(width, height, BooleanArray(width * height) { it == y * width + x })

    @Test
    fun `growing a pixel by two covers the disc round it`() {
        val grown = dot(9, 9, 4, 4).grown(2)
        assertEquals(PixelBox(2, 2, 7, 7), grown.box())
        assertEquals(false, grown[2, 2])
        assertEquals(true, grown[3, 3])
    }

    @Test
    fun `shrinking keeps a pixel where half of what it stands for is covered`() {
        val full = Silhouette(4, 4, BooleanArray(16) { it % 4 < 2 })
        val small = full.shrunk(2)
        assertEquals(listOf(true, false, true, false), listOf(small[0, 0], small[1, 0], small[0, 1], small[1, 1]))
    }
}
