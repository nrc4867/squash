package dev.chieppa.squash.screens.sheet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FocusPureTest {
    @Test
    fun theFocusedPaneFillsTheLeftAndTheOthersStackOnTheRight() {
        val rects = paneRects(SheetLayout.FOCUS, 1000, 800, 3, SheetPane.SIDE)
        val side = rects[SheetPane.SIDE.ordinal]
        assertEquals(0, side.x)
        assertEquals(800, side.height)
        val stack = listOf(SheetPane.TOP, SheetPane.FRONT, SheetPane.FULL).map { rects[it.ordinal] }
        assertTrue(stack.all { it.x == side.right + 3 && it.right == 1000 }, "the small panes share one column at the right edge")
        assertEquals(stack.sortedBy { it.y }, stack, "Top, Front and Full read top to bottom")
        assertEquals(800, stack.last().bottom)
        stack.zipWithNext { above, below -> assertEquals(above.bottom + 3, below.y) }
    }

    @Test
    fun swappingInFullPutsSideInTheColumn() {
        val rects = paneRects(SheetLayout.FOCUS, 1000, 800, 3, SheetPane.FULL)
        assertEquals(0, rects[SheetPane.FULL.ordinal].x)
        assertTrue(rects[SheetPane.SIDE.ordinal].x > 0)
    }

    @Test
    fun theDividersRunBesideTheLargePaneAndBetweenTheSmallOnes() {
        val rects = paneRects(SheetLayout.FOCUS, 1000, 800, 3, SheetPane.SIDE)
        val bars = paneDividers(SheetLayout.FOCUS, rects, 1000, 800)
        assertEquals(3, bars.size)
        assertEquals(rects[SheetPane.SIDE.ordinal].right, bars.first().x)
        assertTrue(bars.all { it.width > 0 && it.height > 0 })
    }
}
