package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.PixelRect
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LockedCameraTest {
    private val paw = PartAxes.of(Vec3(1f, 0f, 0f))
    private val leg = PartAxes.of(Vec3(0f, -1f, 0f))
    private val extents = PartExtents(along = Span(0f, 1f), up = Span(-0.25f, 0.25f), side = Span(-0.5f, 0.5f))
    private val rects = sheetPaneRects(1000, 800, 2)
    private val fit = fitSheet(paw, extents, rects)

    private fun camera(view: View, axes: PartAxes = paw) = LockedCamera(view, axes, fit, 499, 399)

    private fun near(a: Float, b: Float, slack: Float = 1e-3f) = abs(a - b) <= slack

    private fun assertNear(expected: Vec3, actual: Vec3, slack: Float = 1e-3f) =
        assertTrue((expected - actual).length <= slack, "expected $expected, got $actual")

    @Test
    fun axesAreOrthonormalAndAVerticalAxisTakesUpFromX() {
        assertEquals(Vec3(1f, 0f, 0f), leg.up)
        assertEquals(Vec3(0f, 1f, 0f), paw.up)
        assertNear(Vec3(0f, 0f, -1f), paw.side)
        assertNear(Vec3(0f, 0f, -1f), leg.side)
    }

    @Test
    fun eachLockedViewLooksDownTheAxisItDoesNotShow() {
        assertNear(paw.side, paw.basis(View.SIDE).back)
        assertNear(paw.up, paw.basis(View.TOP).back)
        assertNear(paw.along, paw.basis(View.FRONT).back)
    }

    @Test
    fun aPixelMapsToPartSpaceAndBack() {
        val pixel = Offset(120f, 310f)
        for (axes in listOf(paw, leg)) {
            for (view in View.entries) {
                val camera = camera(view, axes)
                val part = camera.toPart(pixel)
                val back = camera.toPixel(part)
                assertTrue(near(pixel.x, back.x, 0.01f) && near(pixel.y, back.y, 0.01f), "$view: $pixel came back as $back")
                val plane = camera.toPlane(pixel)
                assertEquals(pixel, camera.toPixel(plane).let { Offset(Math.round(it.x * 100) / 100f, Math.round(it.y * 100) / 100f) })
                assertNear(camera.basis.xAxis * plane.x + camera.basis.yAxis * plane.y, part)
            }
        }
    }

    @Test
    fun aPointInSidePlaneIsAlongTheAxisAndUp() {
        val camera = camera(View.SIDE)
        val tip = camera.toPixel(CurvePoint(1f, 0f))
        val socket = camera.toPixel(CurvePoint(0f, 0f))
        assertTrue(tip.x < socket.x, "the part's socket is on the right")
        assertTrue(camera.toPixel(CurvePoint(0f, 0.25f)).y < socket.y, "up is up")
        assertNear(Vec3(1f, 0f, 0f), camera.toPart(tip))
    }

    @Test
    fun thePartsMiddleIsAtEveryPanesMiddle() {
        for (view in View.entries) {
            val middle = camera(view).toPixel(paw.middle(extents))
            assertTrue(near(middle.x, 499 / 2f) && near(middle.y, 399 / 2f), "$view: $middle")
        }
    }

    @Test
    fun onePartUnitIsTheSameInEveryPane() {
        val lengths = View.entries.map { view ->
            val camera = camera(view)
            (camera.toPixel(CurvePoint(1f, 0f)) - camera.toPixel(CurvePoint(0f, 0f))).getDistance()
        }
        assertTrue(lengths.all { near(it, fit.pixelsPerUnit) }, "$lengths at ${fit.pixelsPerUnit}")
    }

    @Test
    fun theShapeFitsTheTightestPaneDirection() {
        val tight = fitSheet(paw, extents, sheetPaneRects(2000, 200, 0))
        assertTrue(near(tight.pixelsPerUnit, 100 * 0.66f / 1f), "height limits it: ${tight.pixelsPerUnit}")
    }

    @Test
    fun sideLinesUpWithTopInItsColumnAndFrontInItsRow() {
        val point = Vec3(0.8f, 0.2f, 0.1f)
        val rect = PixelRect(0, 0, 499, 399)
        assertEquals(rect.width, camera(View.TOP).width)
        assertTrue(near(camera(View.SIDE).toPixel(point).x, camera(View.TOP).toPixel(point).x), "same column")
        assertTrue(near(camera(View.SIDE).toPixel(point).y, camera(View.FRONT).toPixel(point).y), "same row")
    }

    @Test
    fun theMatrixAgreesWithThePixelMapping() {
        for (view in View.entries) {
            val camera = camera(view)
            val point = Vec3(0.7f, 0.1f, -0.2f)
            val clip = camera.viewProjection().project(point.toGpu())!!
            val x = (clip.x + 1f) / 2f * camera.width
            val y = (1f - clip.y) / 2f * camera.height
            val pixel = camera.toPixel(point)
            assertTrue(near(x, pixel.x, 0.05f) && near(y, pixel.y, 0.05f), "$view: $x, $y and $pixel")
            assertTrue(clip.z in 0f..1f, "$view: inside the depth range: ${clip.z}")
        }
    }

    @Test
    fun theFitHoldsWhileThePartStaysInView() {
        val kept = keepFit(fit, paw, extents.copy(along = Span(0.1f, 0.9f)), rects)
        assertSame(fit, kept)
        val grown = extents.copy(along = Span(-3f, 5f))
        val moved = keepFit(fit, paw, grown, rects)
        assertNotSame(fit, moved)
        assertTrue(moved.pixelsPerUnit < fit.pixelsPerUnit)
    }
}
