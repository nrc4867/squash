package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.stretch.frame.OverlayDot
import dev.chieppa.stretch.frame.OverlayLine
import dev.chieppa.stretch.frame.OverlayRect
import dev.chieppa.stretch.frame.ViewportOverlay
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SheetPureTest {
    private val axes = PartAxes.of(Vec3(1f, 0f, 0f))
    private val extents = PartExtents(along = Span(0f, 1f), up = Span(-0.25f, 0.25f), side = Span(-0.5f, 0.5f))
    private val fit = SheetFit(axes.middle(extents), 100f)

    private fun camera(view: View) = LockedCamera(view, axes, fit, 400, 300)

    @Test
    fun panesFillTheAreaTwoByTwoWithTheGapBetweenThem() {
        val rects = sheetPaneRects(1001, 801, 3)
        assertEquals(PixelRect(0, 0, 499, 399), rects[SheetPane.TOP.ordinal])
        assertEquals(PixelRect(502, 0, 499, 399), rects[SheetPane.FULL.ordinal])
        assertEquals(PixelRect(0, 402, 499, 399), rects[SheetPane.SIDE.ordinal])
        assertEquals(PixelRect(502, 402, 499, 399), rects[SheetPane.FRONT.ordinal])
    }

    @Test
    fun theOddPixelGoesToTheLastColumnAndRow() {
        val rects = sheetPaneRects(1000, 800, 3)
        assertEquals(498, rects[SheetPane.TOP.ordinal].width)
        assertEquals(1000, rects[SheetPane.FULL.ordinal].right)
        assertEquals(800, rects[SheetPane.FRONT.ordinal].bottom)
    }

    @Test
    fun dividersFillTheGaps() {
        val rects = sheetPaneRects(1001, 801, 3)
        assertEquals(
            listOf(PixelRect(499, 0, 3, 801), PixelRect(0, 399, 1001, 3)),
            sheetDividers(rects, 1001, 801),
        )
    }

    @Test
    fun anEmptyAreaHasEmptyPanes() {
        assertTrue(sheetPaneRects(0, 0, 3).all { it.isEmpty })
    }

    @Test
    fun sidesExtentsShowInTopAsColumnsAndFrontAsRows() {
        val side = SideExtents(Span(0f, 1f), Span(-0.25f, 0.25f))
        val top = paneGuides(camera(View.TOP), side)
        val front = paneGuides(camera(View.FRONT), side)
        val own = paneGuides(camera(View.SIDE), side)
        assertEquals(own.verticals, top.verticals)
        assertTrue(top.horizontals.isEmpty())
        assertEquals(own.horizontals, front.horizontals)
        assertTrue(front.verticals.isEmpty())
        assertEquals(listOf(250f, 150f), own.verticals, "the socket on the right, 100 pixels a unit")
        assertEquals(listOf(125f, 175f), own.horizontals)
    }

    @Test
    fun aDrawnSideOutlineSetsTheExtents() {
        val outline = OutlineCurve(listOf(CurvePoint(0.25f, 0f), CurvePoint(0.25f, 1f), CurvePoint(0.75f, 1f), CurvePoint(0.75f, 0f)))
        val side = sideExtents(outline, extents)
        assertEquals(Span(0.25f, 0.75f), side.along)
        assertEquals(0f, side.up.min)
        assertTrue(abs(side.up.max - 0.75f) < 0.01f, "the curve's top, not its handles: ${side.up.max}")
        assertEquals(SideExtents(extents.along, extents.up), sideExtents(null, extents), "else the mesh's")
    }

    @Test
    fun chipsReadTheOutlinesAndTheStroke() {
        assertEquals(PaneChip.LIVE, paneChip(SheetPane.FULL, emptySet(), null))
        assertEquals(PaneChip.FROM_TEMPLATE, paneChip(SheetPane.TOP, setOf(View.SIDE), null))
        assertEquals(PaneChip.DRAWN, paneChip(SheetPane.SIDE, setOf(View.SIDE), null))
        assertEquals(PaneChip.DRAWING, paneChip(SheetPane.SIDE, setOf(View.SIDE), View.SIDE))
    }

    @Test
    fun aPanesOverlayKeepsInsideItsPane() {
        val area = Rect(0f, 0f, 100f, 100f)
        val overlay = ViewportOverlay(
            rects = listOf(OverlayRect(Rect(90f, 90f, 120f, 120f), Color.Red), OverlayRect(Rect(200f, 0f, 210f, 10f), Color.Red)),
            lines = listOf(
                OverlayLine(Offset(50f, 50f), Offset(150f, 50f), Color.Red),
                OverlayLine(Offset(10f, 10f), Offset(20f, 20f), Color.Red),
                OverlayLine(Offset(150f, 0f), Offset(150f, 100f), Color.Red),
                OverlayLine(Offset(-50f, 50f), Offset(50f, 150f), Color.Red),
            ),
            dots = listOf(OverlayDot(Offset(5f, 5f), 2f, Color.Red), OverlayDot(Offset(500f, 5f), 2f, Color.Red)),
        )
        val clipped = overlay.clippedTo(area)
        assertEquals(listOf(Rect(90f, 90f, 100f, 100f)), clipped.rects.map { it.rect })
        assertEquals(
            listOf(
                OverlayLine(Offset(50f, 50f), Offset(100f, 50f), Color.Red),
                OverlayLine(Offset(10f, 10f), Offset(20f, 20f), Color.Red),
                OverlayLine(Offset(0f, 100f), Offset(0f, 100f), Color.Red),
            ),
            clipped.lines,
        )
        assertEquals(1, clipped.dots.size)
    }

    @Test
    fun aPanesOverlayMovesToItsPlaceOnTheSurface() {
        val moved = ViewportOverlay(lines = listOf(OverlayLine(Offset(1f, 2f), Offset(3f, 4f), Color.Red))).translated(Offset(10f, 20f))
        assertEquals(OverlayLine(Offset(11f, 22f), Offset(13f, 24f), Color.Red), moved.lines.single())
    }
}
