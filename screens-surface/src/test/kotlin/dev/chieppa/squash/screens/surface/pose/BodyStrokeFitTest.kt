package dev.chieppa.squash.screens.surface.pose

import dev.chieppa.squash.document.BodyCurve
import dev.chieppa.squash.document.BodyStroke
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.BodyBox
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.pose.CurveSpot
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PartAxes
import dev.chieppa.squash.screens.sheet.fitSheet
import dev.chieppa.squash.screens.sheet.sheetPaneRects
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How near the fitted stroke keeps to the line drawn, in screen pixels. */
private const val FIT_PIXELS = 1.5f

private const val SAMPLES = 120

/** A stroke drawn across a locked pane that looks along the body's side or down on it, read in that pane's plane and fitted. */
class BodyStrokeFitTest {
    private val axes = PartAxes.of(Vec3(1f, 0f, 0f))
    private val box = BodyBox(Vec3(-0.6f, -0.4f, -0.3f), Vec3(0.6f, 0.4f, 0.3f))
    private val fit = fitSheet(box, axes, sheetPaneRects(1200, 900, 2))
    private val middle = Vec3(0f, 0.05f, 0f)

    private fun camera(view: View) = LockedCamera(view, axes, fit, 599, 449)

    /** A bowed line from behind the tail to ahead of the head, across the plane of [view], in body space. */
    private fun bow(view: View): List<Vec3> = List(SAMPLES) { i ->
        val t = i / (SAMPLES - 1f)
        val x = -0.7f + 1.4f * t
        val dip = -0.25f * sin(PI * t).toFloat()
        if (view == View.TOP) Vec3(x, 0.3f, dip) else Vec3(x, dip, 0.2f)
    }

    private fun drawn(view: View, line: List<Vec3>): BodyStroke {
        val camera = camera(view)
        assertEquals(view, bodyCurveView(camera, middle))
        val points = line.map { assertNotNull(strokePoint(camera, camera.toPixel(it), view, middle)) }
        return assertNotNull(fitBodyStroke(points, view, 1f / camera.fit.pixelsPerUnit))
    }

    private fun distance(a: Vec2, b: Vec2) = hypot(a.x - b.x, a.y - b.y)

    private fun flat(p: Vec3, view: View) = if (view == View.TOP) Vec2(p.x, p.z) else Vec2(p.x, p.y)

    @Test
    fun aStrokeInSideOrTopIsFittedInThatViewsPlane() {
        for (view in listOf(View.SIDE, View.TOP)) {
            val line = bow(view)
            val stroke = drawn(view, line)
            assertEquals(view, stroke.view)
            assertEquals(0, (stroke.points.size - 1) % 3)
            val pixel = 1f / fit.pixelsPerUnit
            val path = stroke.arcPath()
            val worst = line.maxOf { path.distanceTo(flat(it, view)) } / pixel
            println("$view: ${(stroke.points.size - 1) / 3} cubics, the drawn line ${"%.2f".format(worst)} pixels from them at worst")
            assertTrue(worst <= FIT_PIXELS, "$view strays $worst pixels")
            assertTrue(distance(path.points.first(), flat(line.first(), view)) <= FIT_PIXELS * pixel, "$view starts where it was drawn")
            assertTrue(distance(path.points.last(), flat(line.last(), view)) <= FIT_PIXELS * pixel, "$view ends where it was drawn")
        }
    }

    @Test
    fun aStrokeDrawnHeadFirstKeepsTheWayItWasDrawn() {
        val line = bow(View.SIDE).reversed()
        val stroke = drawn(View.SIDE, line)
        val pixel = 1f / fit.pixelsPerUnit
        assertTrue(distance(stroke.arcPath().points.first(), flat(line.first(), View.SIDE)) <= FIT_PIXELS * pixel, "it starts at the head end")
        assertTrue(stroke.points.first().x > stroke.points.last().x)
    }

    @Test
    fun theFrontPaneDrawsNoBodyCurve() {
        assertNull(bodyCurveView(camera(View.FRONT), middle))
    }

    @Test
    fun aStrokeReplacesItsViewsAndKeepsTheOther() {
        val side = BodyStroke(View.SIDE, listOf(0f to 0f, 0.3f to 0.1f, 0.6f to 0.1f, 0.9f to 0f).map { (x, y) -> CurvePoint(x, y) })
        val top = BodyStroke(View.TOP, side.points)
        val again = side.copy(points = side.points.map { CurvePoint(it.x, it.y - 0.1f) })
        val curve = BodyCurve(listOf(side, top), 0.9f).withStroke(again)
        assertEquals(setOf(again, top), curve.strokes.toSet())
        assertEquals(0.9f, curve.length)
        assertEquals(BodyCurve(listOf(top)), (null as BodyCurve?).withStroke(top))
    }

    @Test
    fun anEndMovesItsPointAndTheMiddleBowsTheCurve() {
        val stroke = BodyStroke(View.SIDE, listOf(0f to 0f, 0.3f to 0.1f, 0.6f to 0.1f, 0.9f to 0f, 1.2f to -0.1f, 1.5f to -0.1f, 1.8f to 0f).map { (x, y) -> CurvePoint(x, y) })
        val by = Vec2(0f, 0.2f)
        val start = stroke.moved(CurveSpot.START, by)
        assertEquals(stroke.points.drop(2), start.points.drop(2))
        assertEquals(CurvePoint(0f, 0.2f), start.points.first())
        val bowed = stroke.moved(CurveSpot.MIDDLE, by)
        assertEquals(stroke.points.first(), bowed.points.first())
        assertEquals(stroke.points.last(), bowed.points.last())
        val before = stroke.spots().getValue(CurveSpot.MIDDLE)
        val after = bowed.spots().getValue(CurveSpot.MIDDLE)
        assertEquals(0.2f, after.y - before.y, 0.02f)
    }
}
