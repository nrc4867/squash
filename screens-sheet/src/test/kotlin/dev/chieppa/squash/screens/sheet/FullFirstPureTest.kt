package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.render.PixelRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FullFirstPureTest {
    @Test
    fun theBoardsAreaGivesTheBoardsPanes() {
        val rects = fullFirstPaneRects(1091, 788, 1)
        assertEquals(PixelRect(0, 0, 1091, 525), rects[SheetPane.FULL.ordinal])
        assertEquals(PixelRect(0, 526, 363, 262), rects[SheetPane.TOP.ordinal])
        assertEquals(PixelRect(364, 526, 363, 262), rects[SheetPane.SIDE.ordinal])
        assertEquals(PixelRect(728, 526, 363, 262), rects[SheetPane.FRONT.ordinal])
    }

    @Test
    fun theOddPixelsGoToTheLastSheetAndToFull() {
        val rects = fullFirstPaneRects(1000, 800, 3)
        assertEquals(1000, rects[SheetPane.FRONT.ordinal].right)
        assertEquals(800, rects[SheetPane.FRONT.ordinal].bottom)
        assertEquals(rects[SheetPane.TOP.ordinal].width, rects[SheetPane.SIDE.ordinal].width)
    }

    @Test
    fun dividersFillTheGaps() {
        val rects = fullFirstPaneRects(1000, 802, 2)
        assertEquals(
            listOf(PixelRect(0, 534, 1000, 2), PixelRect(332, 536, 2, 266), PixelRect(666, 536, 2, 266)),
            fullFirstDividers(rects, 1000),
        )
    }

    @Test
    fun anEmptyAreaHasEmptyPanes() {
        assertTrue(fullFirstPaneRects(0, 0, 3).all { it.isEmpty })
    }

    @Test
    fun otherLayoutsKeepTheSheetsRects() {
        assertEquals(sheetPaneRects(1000, 800, 3), paneRects(SheetLayout.SHEET, 1000, 800, 3))
        assertEquals(fullFirstPaneRects(1000, 800, 3), paneRects(SheetLayout.FULL_FIRST, 1000, 800, 3))
    }

    @Test
    fun fullSaysItTakesSurfaceStrokesAndLightsUpWhenARefusedToolPresses() {
        assertEquals(PaneChip.SURFACE_ONLY, fullFirstChip(PaneChip.LIVE, refused = false, fitError = null))
        assertEquals(PaneChip.SURFACE_REFUSED, fullFirstChip(PaneChip.LIVE, refused = true, fitError = null))
        assertEquals(PaneChip.REFIT, fullFirstChip(PaneChip.REFIT, refused = false, fitError = null))
    }

    @Test
    fun aSheetShowsOnlyWhatIsHappeningInIt() {
        assertNull(fullFirstChip(PaneChip.DRAWN, refused = false, fitError = null))
        assertNull(fullFirstChip(PaneChip.FROM_TEMPLATE, refused = false, fitError = null))
        assertEquals(PaneChip.DRAWING, fullFirstChip(PaneChip.DRAWING, refused = false, fitError = null))
        assertEquals(PaneChip.DRAWN, fullFirstChip(PaneChip.DRAWN, refused = false, fitError = 1.5f), "a fit's error still reads")
        assertEquals(PaneChip.HIDES_PAIR, fullFirstChip(PaneChip.HIDES_PAIR, refused = false, fitError = null))
    }
}
