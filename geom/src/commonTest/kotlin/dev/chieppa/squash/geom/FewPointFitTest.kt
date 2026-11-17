package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FewPointFitTest {
    private fun wavy(count: Int = 200): List<Vec2> = List(count) { i ->
        val t = i.toFloat() / (count - 1)
        Vec2(t * 4f, sin(t * 3f * PI.toFloat()) * 0.6f + 0.01f * sin(i * 1.3f))
    }

    private fun worst(points: List<Vec2>, segments: List<CubicBezier>): Float {
        val flat = segments.flatMap { it.sample(64) }
        return points.maxOf { p -> (1 until flat.size).minOf { distanceToSegment(p, flat[it - 1], flat[it]) } }
    }

    @Test
    fun theFitStaysWithinItsToleranceWithFewerSectionsThanSplittingAtTheWorstPoint() {
        val points = wavy()
        for (tolerance in listOf(0.01f, 0.03f, 0.1f)) {
            val few = fitFewCubics(points, tolerance)
            val split = fitCubics(points, tolerance)
            assertTrue(worst(points, few) <= tolerance * 1.05f, "within $tolerance: ${worst(points, few)}")
            assertTrue(few.size <= split.size, "at $tolerance: ${few.size} sections against ${split.size}")
            assertEquals(points.first(), few.first().p0)
            assertEquals(points.last(), few.last().p3)
        }
    }

    @Test
    fun theJoinsAreSmooth() {
        val few = fitFewCubics(wavy(), 0.01f)
        assertTrue(few.size > 1)
        for ((a, b) in few.zipWithNext()) {
            assertEquals(a.p3, b.p0)
            val into = (a.p3 - a.p2).unit()
            val out = (b.p1 - b.p0).unit()
            assertTrue((into dotWith out) > 0.999f, "a join turns: $into to $out")
        }
    }

    @Test
    fun theFitSettingIsAShareOfTheCurvesSpanAndAPixelAtFaithful() {
        assertEquals(16f / FIT_SPAN_PARTS, fitShareOfSpan(0f), 1e-6f)
        assertEquals(1f / FIT_SPAN_PARTS, fitShareOfSpan(0.999999f), 1e-5f)
        val pixel = 0.01f
        assertEquals(pixel, fitTolerance(FAITHFUL_FIT, pixel, 3f), 1e-6f)
        val small = fitTolerance(DEFAULT_STROKE_FIT, pixel, 3f)
        val large = fitTolerance(DEFAULT_STROKE_FIT, pixel, 6f)
        assertEquals(2f * small, large, 1e-5f, "twice the span, twice the tolerance, at any zoom")
        assertEquals(pixel, fitTolerance(DEFAULT_STROKE_FIT, pixel, 0.1f), 1e-6f, "never under a pixel")
    }

    @Test
    fun aWrongEndDirectionCostsOneShortCubicNotACluster() {
        val arc = List(120) { i -> (PI * i / 119).toFloat() }.map { Vec2(cos(it), sin(it)) }
        val bent = Vec2(cos(2.2f), sin(2.2f))
        val fit = fitFewCubics(arc, 0.01f, endTangent = bent)
        val short = fit.count { (it.p3 - it.p0).magnitude < 0.1f }
        assertTrue(short <= 1, "${fit.size} cubics, $short of them short: ${fit.map { (it.p3 - it.p0).magnitude }}")
    }

    @Test
    fun aRoundTipsHandlesAreEven() {
        val tip = List(161) { i ->
            val t = i / 160f * 2f - 1f
            Vec2(1f - t * t, 0.4f * t)
        }
        val fit = fitFewCubics(tip, 0.01f)
        for ((a, b) in fit.zipWithNext()) {
            val into = (a.p3 - a.p2).magnitude
            val out = (b.p1 - b.p0).magnitude
            assertTrue(maxOf(into, out) / minOf(into, out) <= 2.5f, "handles $into and $out at ${a.p3}")
        }
    }

    @Test
    fun simplifyingTwiceDropsNothingMore() {
        val wobbly = List(300) { i ->
            val a = 2.0 * PI * i / 299
            Vec2(cos(a).toFloat() * (1f + 0.02f * sin(9 * a).toFloat()), sin(a).toFloat() * 0.6f)
        }
        val dense = BezierPath(fitCubics(wobbly, 1e-3f))
        val once = simplifiedPath(dense, 0.02f)
        assertTrue(once.segments.size < dense.segments.size)
        assertEquals(once, simplifiedPath(once, 0.02f))
    }

    @Test
    fun simplifyingDropsSmoothJoinsAndKeepsCorners() {
        val arc = List(200) { i -> PI.toFloat() * i / 199 }.map { Vec2(cos(it), sin(it)) }
        val smooth = fitCubics(resample(arc, 0.01f), 1e-4f)
        val dense = BezierPath(smooth + straightCubic(smooth.last().p3, smooth.first().p0))
        val simple = simplifiedPath(dense, 0.005f)
        assertTrue(simple.segments.size < dense.segments.size, "${dense.segments.size} to ${simple.segments.size}")
        val anchors = simple.segments.map { it.p0 }
        assertTrue(anchors.any { (it - Vec2(-1f, 0f)).magnitude < 1e-4f } && anchors.any { (it - Vec2(1f, 0f)).magnitude < 1e-4f }, "both corners stay: $anchors")
        val samples = dense.flatten(32)
        val points = samples.xs.indices.map { Vec2(samples.xs[it], samples.ys[it]) }
        assertTrue(worst(points, simple.segments) <= 0.0055f, "within tolerance: ${worst(points, simple.segments)}")
    }

    @Test
    fun aPathWithNothingToDropStaysAsItIs() {
        val square = BezierPath(listOf(straightCubic(Vec2(0f, 0f), Vec2(1f, 0f)), straightCubic(Vec2(1f, 0f), Vec2(1f, 1f)), straightCubic(Vec2(1f, 1f), Vec2(0f, 0f))))
        assertEquals(square, simplifiedPath(square, 0.01f))
    }

    @Test
    fun anEllipseFillsTheBoxBetweenTwoCorners() {
        val ellipse = assertNotNull(ellipseInBox(Vec2(0f, 0f), Vec2(4f, 2f)))
        val ring = ellipse.outline(0.05f)
        assertEquals(0f, ring.minOf { it.x }, 0.01f)
        assertEquals(4f, ring.maxOf { it.x }, 0.01f)
        assertEquals(0f, ring.minOf { it.y }, 0.01f)
        assertEquals(2f, ring.maxOf { it.y }, 0.01f)
        assertNull(ellipseInBox(Vec2(0f, 0f), Vec2(3f, 0f)))
    }

    /** A loop whose hand overshoots its start and then [closing]. */
    private fun closedLoop(closing: List<Vec2>): List<Vec2> {
        val ring = List(200) { i ->
            val a = 2.4 + 2.0 * PI * i / 199
            Vec2(0.3f * cos(a).toFloat() + 0.002f * sin(i * 0.9f), 0.15f * sin(a).toFloat())
        }
        return ring + closing.map { ring.last() + it }
    }

    @Test
    fun aLoopClosedWithAHookOrAZigzagHasAtMostOneShortSectionAtItsSeam() {
        val pixel = 0.002f
        val closings = mapOf(
            "overshoot" to List(15) { i -> Vec2(0.004f * i, -0.003f * i) },
            "hook" to List(10) { i -> Vec2(0.004f * i, 0f) } + List(10) { i -> Vec2(0.04f - 0.002f * i, 0.003f * i) },
            "zigzag" to List(16) { i -> Vec2(0.003f * i, if (i % 4 < 2) 0.004f else -0.004f) },
        )
        for ((name, closing) in closings) {
            val stroke = assertNotNull(cleanStroke(closedLoop(closing), StrokeEnds.Closed, pixel, StrokeOptions(fit = DEFAULT_STROKE_FIT)))
            val anchors = stroke.path.segments.map { it.p0 }
            val gaps = (anchors + anchors.first()).zipWithNext().map { (a, b) -> (a - b).magnitude / pixel }
            assertTrue(gaps.count { it < 24f } <= 1, "$name: ${anchors.size} points, gaps $gaps")
        }
    }
}
