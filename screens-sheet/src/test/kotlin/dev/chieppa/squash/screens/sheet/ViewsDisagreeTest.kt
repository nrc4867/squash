package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ViewsDisagreeTest {
    /** A straight outline from [from] to [to]. */
    private fun bar(from: CurvePoint, to: CurvePoint) = OutlineCurve(
        listOf(from, CurvePoint(from.x + (to.x - from.x) / 3f, from.y + (to.y - from.y) / 3f), CurvePoint(from.x + (to.x - from.x) * 2f / 3f, from.y + (to.y - from.y) * 2f / 3f), to),
    )

    private val side = bar(CurvePoint(0f, -0.5f), CurvePoint(2f, 0.5f))

    @Test
    fun oneViewAloneDisagreesWithNothing() {
        assertTrue(viewsDisagree(mapOf(View.SIDE to side)).isEmpty())
    }

    @Test
    fun viewsWithTheSameEndsAgree() {
        val top = bar(CurvePoint(0f, 1f), CurvePoint(2f, -1f))
        assertTrue(viewsDisagree(mapOf(View.SIDE to side, View.TOP to top)).isEmpty())
    }

    @Test
    fun topPastSidesEndIsMarkedFromThereOn() {
        val top = bar(CurvePoint(0f, 0f), CurvePoint(3f, 0f))
        val found = viewsDisagree(mapOf(View.SIDE to side, View.TOP to top)).single()
        assertEquals(View.TOP, found.view)
        assertEquals(View.SIDE, found.against)
        assertEquals(PartAxis.ALONG, found.axis)
        assertEquals(2f, found.limit, 1e-3f)
        val span = found.spans.single()
        assertEquals(2f, span.first().x, 1e-3f, "starts on the limit")
        assertEquals(3f, span.last().x, 1e-3f)
    }

    @Test
    fun sidePastTopsEndIsMarkedToo() {
        val top = bar(CurvePoint(0f, 0f), CurvePoint(1f, 0f))
        val found = viewsDisagree(mapOf(View.SIDE to side, View.TOP to top)).single()
        assertEquals(View.SIDE, found.view)
        assertEquals(1f, found.limit, 1e-3f)
    }

    @Test
    fun frontTallerThanSideIsMarkedAboveSidesHeight() {
        val front = bar(CurvePoint(0f, -0.5f), CurvePoint(0f, 0.8f))
        val found = viewsDisagree(mapOf(View.SIDE to side, View.FRONT to front)).single()
        assertEquals(View.FRONT, found.view)
        assertEquals(PartAxis.UP, found.axis)
        assertEquals(0.5f, found.limit, 1e-3f)
        assertEquals(0.5f, found.spans.single().first().y, 1e-3f)
    }

    @Test
    fun sideTallerThanFrontIsNoDisagreement() {
        val front = bar(CurvePoint(0f, -0.1f), CurvePoint(0f, 0.1f))
        assertTrue(viewsDisagree(mapOf(View.SIDE to side, View.FRONT to front)).isEmpty())
    }

    @Test
    fun frontWiderThanTopIsReadThroughTopsFlippedY() {
        val top = bar(CurvePoint(0f, 0.4f), CurvePoint(2f, -0.2f))
        val front = bar(CurvePoint(-0.4f, 0f), CurvePoint(0.6f, 0f))
        val found = viewsDisagree(mapOf(View.TOP to top, View.FRONT to front))
        assertEquals(1, found.size, "top reaches side -0.4 to 0.2, front -0.4 to 0.6: $found")
        assertEquals(PartAxis.SIDE, found.single().axis)
        assertEquals(0.2f, found.single().limit, 1e-3f)
    }

    @Test
    fun theToleranceIgnoresASmallMiss() {
        val top = bar(CurvePoint(0f, 0f), CurvePoint(2.01f, 0f))
        val outlines = mapOf(View.SIDE to side, View.TOP to top)
        assertEquals(1, viewsDisagree(outlines).size)
        assertTrue(viewsDisagree(outlines, tolerance = 0.02f).isEmpty())
    }

    @Test
    fun aLiveStrokeIsMeasuredAgainstTheOthersButHoldsThemToNothing() {
        val top = bar(CurvePoint(0f, 0f), CurvePoint(2f, 0f))
        val shortLive = LiveStroke(View.SIDE, listOf(CurvePoint(0f, 0f), CurvePoint(1f, 0f)))
        assertTrue(viewsDisagree(mapOf(View.TOP to top), shortLive).isEmpty(), "top is not past a stroke still being drawn")
        val longLive = LiveStroke(View.SIDE, listOf(CurvePoint(1f, 0f), CurvePoint(3f, 0f), CurvePoint(1f, 1f), CurvePoint(4f, 1f)))
        val found = viewsDisagree(mapOf(View.TOP to top), longLive).single()
        assertEquals(View.SIDE, found.view)
        assertEquals(2, found.spans.size, "one stretch for each time it goes past")
    }

    @Test
    fun aLiveStrokeReplacesItsViewsStoredOutline() {
        val top = bar(CurvePoint(0f, 0f), CurvePoint(1f, 0f))
        val live = LiveStroke(View.SIDE, listOf(CurvePoint(0f, 0f), CurvePoint(0.5f, 0f)))
        assertTrue(viewsDisagree(mapOf(View.SIDE to side, View.TOP to top), live).isEmpty())
    }

    @Test
    fun aDisagreementDrawsItsLineInBothPanesAndItsSpanInItsOwn() {
        val look = OutlineLook(Color.Gray, 1f, Color.Red, 2f, 4f)
        val axes = PartAxes.of(Vec3.X)
        fun camera(view: View) = LockedCamera(view, axes, SheetFit(Vec3.ZERO, 100f), 600, 400)
        val top = bar(CurvePoint(0f, 0f), CurvePoint(1f, 0f))
        val front = bar(CurvePoint(0f, -0.5f), CurvePoint(0f, 0.8f))
        val found = viewsDisagree(mapOf(View.SIDE to side, View.FRONT to front, View.TOP to top))
        val inFront = disagreeOverlay(View.FRONT, camera(View.FRONT), found.filter { it.view == View.FRONT }, look)
        val inSide = disagreeOverlay(View.SIDE, camera(View.SIDE), found.filter { it.view == View.FRONT }, look)
        val limitRow = camera(View.FRONT).toPixel(CurvePoint(0f, 0.5f)).y
        assertTrue(inFront.lines.any { it.width == 2f && it.from.y == limitRow && it.to.y == limitRow && it.to.x == 600f })
        assertTrue(inFront.lines.any { it.width == 4f }, "the stretch past it")
        assertTrue(inSide.lines.any { it.width == 2f && it.from.y == limitRow }, "the same height in Side")
        assertTrue(inSide.lines.none { it.width == 4f }, "Side's own outline is not past anything here")
    }
}
