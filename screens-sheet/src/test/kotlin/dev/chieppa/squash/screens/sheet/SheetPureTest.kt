package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.PartExtents
import dev.chieppa.squash.geom.Span
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
    private val fit = SheetFit(Vec3(0.5f, 0f, 0f), 100f)

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
        val columns = own.lines.take(2)
        val rows = own.lines.drop(2)
        assertEquals(columns.map { it.start.x }, top.lines.map { it.start.x })
        assertEquals(rows.map { it.start.y }, front.lines.map { it.start.y })
        assertEquals(listOf(250f, 150f), columns.map { it.start.x }, "the socket on the right, 100 pixels a unit")
        assertEquals(columns.map { it.start.x }, columns.map { it.end.x })
        assertEquals(listOf(0f, 300f), columns.first().run { listOf(start.y, end.y) }.sorted(), "the pane's whole height")
        assertEquals(listOf(175f, 125f), rows.map { it.start.y })
        assertEquals(rows.map { it.start.y }, rows.map { it.end.y })
        assertEquals(listOf(0f, 400f), rows.first().run { listOf(start.x, end.x) }.sorted(), "the pane's whole width")
    }

    @Test
    fun aTiltedPartsGuidesAreTrueLinesOfItsExtentsInEveryView() {
        val side = SideExtents(Span(0f, 0.6f), Span(-0.1f, 0.2f))
        for (rest in listOf(Vec3(-0.8f, 0.6f, 0f), Vec3(0f, 0.96f, -0.28f))) {
            for (mirrored in listOf(false, true)) {
                val tilted = PartAxes.of(rest, mirrored)
                val center = LockedCamera(View.SIDE, tilted, fit, 400, 300, Vec3(0.5f, 0f, 0f)).origin
                for (view in View.entries) {
                    val camera = LockedCamera(view, tilted, fit, 400, 300, center)
                    val guides = paneGuides(camera, side).lines
                    val expected = when (view) {
                        View.SIDE -> listOf(0 to 0f, 0 to 0.6f, 1 to -0.1f, 1 to 0.2f)
                        View.TOP -> listOf(0 to 0f, 0 to 0.6f)
                        View.FRONT -> listOf(1 to -0.1f, 1 to 0.2f)
                    }
                    assertEquals(expected.size, guides.size, "$view")
                    for ((line, wanted) in guides.zip(expected)) {
                        val (coordinate, value) = wanted
                        val ends = listOf(camera.toPlane(line.start), camera.toPlane(line.end))
                        for (end in ends) {
                            val along = if (coordinate == 0) end.x else end.y
                            assertTrue(abs(along - value) < 1e-3f, "$rest $view: $end should have $value")
                        }
                        assertTrue(listOf(line.start, line.end).all { it.x in -0.01f..400.01f && it.y in -0.01f..300.01f }, "$rest $view: $line stays in the pane")
                    }
                }
            }
        }
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
    fun chipsShowOnlyWhatIsHappeningInAPane() {
        assertEquals(null, paneChip(null))
        assertEquals(null, paneChip(View.SIDE), "a pane at rest has no chip")
        assertEquals(PaneChip.SURFACE_REFUSED, paneChip(null, refused = true))
        assertEquals(PaneChip.REFIT, paneChip(null, refitting = true))
        assertEquals(PaneChip.FIT_ERROR, paneChip(View.SIDE, fitError = 1.5f), "a fit's error still reads")
        assertEquals(PaneChip.DRAWING, paneChip(View.SIDE, View.SIDE))
        assertEquals(PaneChip.HIDES_PAIR, paneChip(View.SIDE, overridden = setOf(View.SIDE)), "an edit lands under the override")
        assertEquals(PaneChip.THIS_SIDE, paneChip(View.SIDE, overridden = setOf(View.SIDE), mirror = false))
        assertEquals(null, paneChip(View.FRONT, conflicted = setOf(View.FRONT), fitError = 1.5f), "the highlight has the corner")
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
