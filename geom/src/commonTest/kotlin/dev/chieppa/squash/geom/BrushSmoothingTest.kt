package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A pixel, in plane units, at 200 pixels to the unit. */
private const val PIXEL = 0.005f
private val BRUSH = SmoothBrush(radius = 30f * PIXEL, strength = 1f)

class BrushSmoothingTest {
    private fun fitted(points: List<Vec2>): BezierPath = BezierPath(fitCubics(resample(points, 2f * PIXEL), 0.2f * PIXEL))

    /** A line along x from 0 to 2 with a ripple of [amplitude] over [from] to [to] and straight runs either side. */
    private fun rippled(amplitude: Float = 4f * PIXEL, from: Float = 0.8f, to: Float = 1.2f): BezierPath = fitted(
        List(401) { i ->
            val x = i * 0.005f
            val inRipple = x in from..to
            Vec2(x, if (inRipple) amplitude * sin((x - from) / (to - from) * 8f * PI.toFloat()) else 0f)
        },
    )

    private fun ring(radius: Float = 1f, count: Int = 8): BezierPath {
        val handle = 4f / 3f * kotlin.math.tan(PI.toFloat() / (2f * count)) * radius
        fun point(angle: Float) = Vec2(radius * cos(angle), radius * sin(angle))
        fun tangent(angle: Float) = Vec2(-sin(angle), cos(angle)) * handle
        val step = 2f * PI.toFloat() / count
        val segments = List(count) { i ->
            val a = i * step
            val b = (i + 1) * step
            val end = if (i == count - 1) point(0f) else point(b)
            CubicBezier(point(a), point(a) + tangent(a), end - tangent(b), end)
        }
        return BezierPath(segments)
    }

    private fun swept(start: BrushedPath, from: Vec2, to: Vec2, brush: SmoothBrush = BRUSH): BrushedPath = start.dab(from, brush).drag(from, to, brush)

    /** How far [path] strays from y = 0 between [from] and [to] in x. */
    private fun rippleOf(path: BezierPath, from: Float = 0.9f, to: Float = 1.1f): Float =
        path.segments.flatMap { it.sample(16) }.filter { it.x in from..to }.maxOf { abs(it.y) }

    @Test
    fun aDragOverARippleCalmsIt() {
        val path = rippled()
        val done = swept(BrushedPath(path, StrokeEnds.Closed, PIXEL), Vec2(0.8f, 0f), Vec2(1.2f, 0f)).path
        val before = rippleOf(path)
        val after = rippleOf(done)
        assertTrue(after < before * 0.25f, "ripple $before to $after")
    }

    @Test
    fun whatIsOutsideTheBrushStaysExactly() {
        val path = rippled()
        val done = swept(BrushedPath(path, StrokeEnds.Closed, PIXEL), Vec2(1f, 0f), Vec2(1.05f, 0f)).path
        val reachEnd = 1.05f + BRUSH.radius
        val reachStart = 1f - BRUSH.radius
        val outsideBefore = path.segments.filter { it.p3.x < reachStart - 0.01f }
        assertTrue(outsideBefore.isNotEmpty())
        assertEquals(outsideBefore, done.segments.take(outsideBefore.size), "whole sections before the brush keep their values")
        val outsideAfter = path.segments.filter { it.p0.x > reachEnd + 0.01f }
        assertTrue(outsideAfter.isNotEmpty())
        assertEquals(outsideAfter, done.segments.takeLast(outsideAfter.size), "and so do those after it")
        val flat = done.segments.flatMap { it.sample(16) }
        val old = path.segments.flatMap { it.sample(16) }
        for (point in flat.filter { it.x < reachStart - 0.002f || it.x > reachEnd + 0.002f }) {
            val nearest = old.minOf { (it - point).magnitude }
            assertTrue(nearest < 2f * PIXEL, "$point moved by ${nearest / PIXEL} px")
        }
    }

    @Test
    fun theBrushFarFromTheCurveChangesNothing() {
        val path = rippled()
        val brushed = BrushedPath(path, StrokeEnds.Closed, PIXEL)
        assertSame(path, swept(brushed, Vec2(1f, 2f), Vec2(1.2f, 2f)).path)
    }

    @Test
    fun aStraightRunHasNothingToSmooth() {
        val line = BezierPath(listOf(straightCubic(Vec2(0f, 0f), Vec2(1f, 0f)), straightCubic(Vec2(1f, 0f), Vec2(1.73f, 0f))))
        assertSame(line, swept(BrushedPath(line, StrokeEnds.Closed, PIXEL), Vec2(0.9f, 0f), Vec2(1.1f, 0f)).path)
    }

    @Test
    fun noStrengthDoesNothingAndMoreDoesMore() {
        val path = rippled()
        val start = BrushedPath(path, StrokeEnds.Closed, PIXEL)
        fun at(strength: Float) = swept(start, Vec2(0.8f, 0f), Vec2(1.2f, 0f), BRUSH.copy(strength = strength)).path
        assertSame(path, at(0f))
        assertTrue(rippleOf(at(0.9f)) < rippleOf(at(0.2f)))
        assertTrue(rippleOf(at(0.2f)) < rippleOf(path))
    }

    @Test
    fun anOpenCurvesEndsHoldTheirPlaceAndTheCurveItsSide() {
        val arc = BezierPath(
            listOf(
                CubicBezier(Vec2(0f, 0.2f), Vec2(0.05f, 0.5f), Vec2(0.1f, 0.1f), Vec2(0.3f, 0.3f)),
                CubicBezier(Vec2(0.3f, 0.3f), Vec2(0.5f, 0.5f), Vec2(0.5f, -0.5f), Vec2(0.3f, -0.3f)),
                CubicBezier(Vec2(0.3f, -0.3f), Vec2(0.1f, -0.1f), Vec2(0.05f, -0.5f), Vec2(0f, -0.2f)),
            ),
        )
        val brush = SmoothBrush(radius = 60f * PIXEL, strength = 1f)
        var brushed = BrushedPath(arc, StrokeEnds.CutAtPlane(), PIXEL)
        repeat(3) {
            brushed = brushed.dab(Vec2(0f, 0.2f), brush).drag(Vec2(0f, 0.2f), Vec2(0.1f, 0.3f), brush)
            brushed = brushed.dab(Vec2(0f, -0.2f), brush).drag(Vec2(0f, -0.2f), Vec2(0.1f, -0.3f), brush)
        }
        val done = brushed.path
        assertTrue(done != arc)
        assertEquals(arc.segments.first().p0, done.segments.first().p0)
        assertEquals(arc.segments.last().p3, done.segments.last().p3)
        val everyPoint = done.segments.flatMap { listOf(it.p0, it.p1, it.p2, it.p3) }
        assertTrue(everyPoint.all { it.x >= 0f }, "nothing past the plane: ${everyPoint.minOf { it.x }}")
    }

    @Test
    fun aClosedCurveStaysClosedWithTheBrushOnItsSeam() {
        val wobbly = fitted(
            List(721) { i ->
                val angle = i * PI.toFloat() / 360f
                val radius = 1f + 0.02f * sin(angle * 24f)
                if (i == 720) Vec2(1f, 0f) else Vec2(radius * cos(angle), radius * sin(angle))
            },
        )
        val brush = SmoothBrush(radius = 60f * PIXEL, strength = 1f)
        val done = swept(BrushedPath(wobbly, StrokeEnds.Closed, PIXEL), Vec2(1f, -0.2f), Vec2(1f, 0.2f), brush).path
        assertTrue(done != wobbly)
        assertEquals(done.segments.first().p0, done.segments.last().p3, "closed exactly")
        assertTrue(done.segments.first().p0 != wobbly.segments.first().p0, "the seam moved with the rest")
        for ((a, b) in done.segments.zipWithNext()) assertEquals(a.p3, b.p0)
        val first = done.segments.first()
        val last = done.segments.last()
        val out = (first.p1 - first.p0).unit()
        val into = (last.p3 - last.p2).unit()
        assertTrue((out dotWith into) > 0.99f, "the seam turns, dot ${out dotWith into}")
    }

    @Test
    fun brushingAllTheWayRoundAClosedCurveKeepsItWhole() {
        var brushed = BrushedPath(ring(), StrokeEnds.Closed, PIXEL)
        val brush = SmoothBrush(radius = 60f * PIXEL, strength = 0.5f)
        val steps = 72
        fun onRing(i: Int) = Vec2(cos(i * 2f * PI.toFloat() / steps), sin(i * 2f * PI.toFloat() / steps))
        for (i in 0 until steps) brushed = brushed.drag(onRing(i), onRing(i + 1), brush)
        val done = brushed.path
        assertEquals(done.segments.first().p0, done.segments.last().p3)
        for ((a, b) in done.segments.zipWithNext()) assertEquals(a.p3, b.p0)
        for (point in done.segments.flatMap { it.sample(8) }) {
            assertTrue(abs(point.magnitude - 1f) < 0.1f, "still a ring: ${point.magnitude}")
        }
    }

    @Test
    fun aCornerInsideTheBrushRounds() {
        val corner = BezierPath(listOf(straightCubic(Vec2(0f, 0f), Vec2(1f, 0f)), straightCubic(Vec2(1f, 0f), Vec2(1f, 1f))))
        val done = swept(BrushedPath(corner, StrokeEnds.Closed, PIXEL), Vec2(1f, 0f), Vec2(1f, 0.01f)).path
        val nearest = done.segments.flatMap { it.sample(32) }.minOf { (it - Vec2(1f, 0f)).magnitude }
        assertTrue(nearest > 2f * PIXEL, "the tip came in by ${nearest / PIXEL} px")
        assertEquals(Vec2(0f, 0f), done.segments.first().p0)
        assertEquals(Vec2(1f, 1f), done.segments.last().p3)
    }

    @Test
    fun theSameDragInPixelsDoesTheSameAtAnyZoom() {
        val zoom = 4f
        val path = rippled()
        val near = BezierPath(path.segments.map { CubicBezier(it.p0 * (1f / zoom), it.p1 * (1f / zoom), it.p2 * (1f / zoom), it.p3 * (1f / zoom)) })
        val done = swept(BrushedPath(path, StrokeEnds.Closed, PIXEL), Vec2(0.8f, 0f), Vec2(1.2f, 0f)).path
        val brush = BRUSH.copy(radius = BRUSH.radius / zoom)
        val closer = swept(BrushedPath(near, StrokeEnds.Closed, PIXEL / zoom), Vec2(0.8f / zoom, 0f), Vec2(1.2f / zoom, 0f), brush).path
        assertEquals(rippleOf(done), rippleOf(closer, 0.9f / zoom, 1.1f / zoom) * zoom, 1f * PIXEL)
    }

    @Test
    fun manySmallMovesDoAboutWhatOneLongOneDoes() {
        val start = BrushedPath(rippled(), StrokeEnds.Closed, PIXEL).dab(Vec2(0.8f, 0f), BRUSH)
        val once = start.drag(Vec2(0.8f, 0f), Vec2(1.2f, 0f), BRUSH).path
        var bit = start
        val steps = 200
        for (i in 0 until steps) bit = bit.drag(Vec2(0.8f + 0.4f * i / steps, 0f), Vec2(0.8f + 0.4f * (i + 1) / steps, 0f), BRUSH)
        assertTrue(rippleOf(bit.path) < rippleOf(once) * 1.5f + 1f * PIXEL, "${rippleOf(bit.path)} against ${rippleOf(once)}")
    }

    @Test
    fun aDragOfManyDabsAddsFewSections() {
        val path = rippled()
        var brushed = BrushedPath(path, StrokeEnds.Closed, PIXEL)
        repeat(20) { brushed = swept(brushed, Vec2(0.9f, 0f), Vec2(1.1f, 0f)) }
        assertTrue(brushed.path.segments.size <= path.segments.size + 2, "${path.segments.size} to ${brushed.path.segments.size}")
    }

    @Test
    fun theStretchUnderTheBrushIsWhatIsInsideIt() {
        val line = BezierPath(listOf(straightCubic(Vec2(0f, 0f), Vec2(2f, 0f))))
        val stretch = stretchesUnderBrush(line, Vec2(1f, 0.05f), 0.2f, PIXEL).single()
        assertTrue(stretch.all { (it - Vec2(1f, 0.05f)).magnitude < 0.2f })
        assertEquals(1f - 0.194f, stretch.first().x, 2f * PIXEL)
        assertEquals(1f + 0.194f, stretch.last().x, 2f * PIXEL)
        assertTrue(stretchesUnderBrush(line, Vec2(1f, 1f), 0.2f, PIXEL).isEmpty())
    }

    @Test
    fun aPartOfACurveIsThatStretchOfIt() {
        val curve = CubicBezier(Vec2(0f, 0f), Vec2(1f, 2f), Vec2(2f, -1f), Vec2(3f, 1f))
        val part = curve.between(0.25f, 0.75f)
        assertEquals(curve.pointAt(0.25f).x, part.p0.x, 1e-5f)
        assertEquals(curve.pointAt(0.5f).y, part.pointAt(0.5f).y, 1e-5f)
        assertEquals(curve.pointAt(0.75f).y, part.p3.y, 1e-5f)
        assertSame(curve, curve.between(0f, 1f))
    }
}
