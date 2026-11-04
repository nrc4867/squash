package dev.chieppa.squash.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PixelRectTest {
    @Test
    fun aRectangleKeepsOnlyWhatIsOnTheSurface() {
        assertEquals(PixelRect(0, 10, 50, 20), PixelRect(-5, 10, 55, 20).clampedTo(100, 100))
        assertEquals(PixelRect(90, 90, 10, 10), PixelRect(90, 90, 40, 40).clampedTo(100, 100))
        assertTrue(PixelRect(200, 0, 10, 10).clampedTo(100, 100).isEmpty)
    }
}
