package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A pixel, in plane units, at 200 pixels to the unit. */
private const val PIXEL = 0.005f

class StrokeCleanupTest {
    private val noise = Random(7)

    private fun jitter(p: Vec2, pixels: Float = 0.6f) = Vec2(
        p.x + (noise.nextFloat() - 0.5f) * 2f * pixels * PIXEL,
        p.y + (noise.nextFloat() - 0.5f) * 2f * pixels * PIXEL,
    )

    private fun circle(center: Vec2, radius: Float, samples: Int, sweep: Double = 2.0 * PI, from: Double = 0.0) = List(samples) {
        val angle = from + sweep * it / (samples - 1)
        jitter(Vec2(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat()))
    }

    private fun assertJoined(path: BezierPath) {
        for ((a, b) in path.segments.zipWithNext()) assertEquals(a.p3, b.p0)
    }

    @Test
    fun aClickIsNoStroke() {
        assertNull(cleanStroke(listOf(Vec2(1f, 1f)), StrokeEnds.Closed, PIXEL))
        assertNull(cleanStroke(listOf(Vec2(1f, 1f), Vec2(1f + PIXEL, 1f)), StrokeEnds.Closed, PIXEL))
    }

    @Test
    fun aHandDrawnCircleBecomesAFewJoinedCubicsThatCloseExactly() {
        val stroke = assertNotNull(cleanStroke(circle(Vec2(1f, 1f), 0.5f, 400), StrokeEnds.Closed, PIXEL))
        assertTrue(stroke.closed)
        assertJoined(stroke.path)
        assertEquals(stroke.path.segments.first().p0, stroke.path.segments.last().p3)
        assertTrue(stroke.path.segments.size in 1..10, "${stroke.path.segments.size} segments")
        assertTrue(stroke.maxError <= 1.5f * PIXEL, "error ${stroke.maxError / PIXEL} px")
        for (segment in stroke.path.segments) {
            for (p in segment.sample(8)) assertEquals(0.5f, (p - Vec2(1f, 1f)).magnitude, 8f * PIXEL)
        }
    }

    @Test
    fun theSeamOfASmoothClosedShapeHasOneTangent() {
        val stroke = assertNotNull(cleanStroke(circle(Vec2(1f, 1f), 0.5f, 400), StrokeEnds.Closed, PIXEL))
        val first = stroke.path.segments.first()
        val last = stroke.path.segments.last()
        val out = (first.p1 - first.p0).unit()
        val into = (last.p3 - last.p2).unit()
        assertTrue((out dotWith into) > 0.999f, "seam turns, dot ${out dotWith into}")
    }

    @Test
    fun anOpenStrokeInAClosedViewIsClosedByItsChord() {
        val stroke = assertNotNull(cleanStroke(circle(Vec2(0f, 0f), 0.5f, 200, sweep = PI), StrokeEnds.Closed, PIXEL))
        assertEquals(stroke.path.segments.first().p0, stroke.path.segments.last().p3)
    }

    @Test
    fun aCornerStaysACorner() {
        val square = listOf(Vec2(0f, 0f), Vec2(1f, 0f), Vec2(1f, 1f), Vec2(0f, 1f), Vec2(0f, 0f))
        val dense = resample(square, PIXEL).map { jitter(it, 0.3f) }
        val stroke = assertNotNull(cleanStroke(dense, StrokeEnds.Closed, PIXEL))
        val joins = stroke.path.segments.map { it.p0 }
        for (corner in square.drop(1)) {
            assertTrue(joins.any { (it - corner).magnitude < 3f * PIXEL }, "no join at $corner in $joins")
        }
        assertTrue(stroke.maxError <= 2f * PIXEL, "error ${stroke.maxError / PIXEL} px")
    }

    @Test
    fun aClosedStrokeOnAnOpenPartIsCutAtTheSocketPlane() {
        val samples = circle(Vec2(0.6f, 0f), 1f, 500)
        val stroke = assertNotNull(cleanStroke(samples, StrokeEnds.CutAtPlane(), PIXEL))
        assertFalse(stroke.closed)
        assertJoined(stroke.path)
        assertEquals(0f, stroke.path.segments.first().p0.x)
        assertEquals(0f, stroke.path.segments.last().p3.x)
        val all = stroke.path.segments.flatMap { it.sample(16) }
        assertTrue(all.all { it.x >= -2f * PIXEL }, "nothing past the plane: ${all.minOf { it.x }}")
        assertEquals(0.8f, abs(stroke.path.segments.first().p0.y), 4f * PIXEL)
        assertEquals(0.8f, abs(stroke.path.segments.last().p3.y), 4f * PIXEL)
        assertTrue(stroke.path.segments.first().p0.y * stroke.path.segments.last().p3.y < 0f, "one end on each side")
    }

    @Test
    fun aStrokeThatStopsShortReachesThePlaneStraightAlongX() {
        val arc = circle(Vec2(1f, 0f), 0.5f, 200, sweep = PI, from = PI / 2.0)
        val stroke = assertNotNull(cleanStroke(arc.reversed(), StrokeEnds.CutAtPlane(), PIXEL))
        val start = stroke.path.segments.first().p0
        val end = stroke.path.segments.last().p3
        assertEquals(0f, start.x)
        assertEquals(0f, end.x)
        assertEquals(0.5f, abs(start.y), 3f * PIXEL)
        assertEquals(0.5f, abs(end.y), 3f * PIXEL)
    }

    @Test
    fun aClosedShapeShortOfThePlaneOpensTowardsItWithItsWidth() {
        val stroke = assertNotNull(cleanStroke(circle(Vec2(1f, 0f), 0.5f, 400), StrokeEnds.CutAtPlane(), PIXEL))
        val start = stroke.path.segments.first().p0
        val end = stroke.path.segments.last().p3
        assertEquals(0f, start.x)
        assertEquals(0f, end.x)
        assertTrue(abs(start.y - end.y) > 0.4f, "the neck has width: $start to $end")
    }

    @Test
    fun anOpenEndKeepsTheOtherSideOfThePlane() {
        val samples = circle(Vec2(-0.6f, 0f), 1f, 500)
        val stroke = assertNotNull(cleanStroke(samples, StrokeEnds.CutAtPlane(above = false), PIXEL))
        assertTrue(stroke.path.segments.flatMap { it.sample(16) }.all { it.x <= 2f * PIXEL })
    }

    @Test
    fun aStrokeWhollyBehindThePlaneIsNoOutline() {
        assertNull(cleanStroke(circle(Vec2(-2f, 0f), 0.5f, 200), StrokeEnds.CutAtPlane(), PIXEL))
    }

    @Test
    fun overshootPastTheStartIsTrimmed() {
        val overshoot = circle(Vec2(1f, 1f), 0.5f, 500, sweep = 2.3 * PI)
        val stroke = assertNotNull(cleanStroke(overshoot, StrokeEnds.Closed, PIXEL))
        val all = stroke.path.segments.flatMap { it.sample(16) }
        assertTrue(arcLength(all) < 2.05f * PI.toFloat() * 0.5f, "length ${arcLength(all)}")
    }

    @Test
    fun aStraightStrokeFitsWithinTolerance() {
        val line = resample(listOf(Vec2(0f, 0f), Vec2(2f, 0f)), PIXEL).map { jitter(it, 0.2f) }
        val stroke = assertNotNull(cleanStroke(line, StrokeEnds.CutAtPlane(plane = -1f), PIXEL))
        assertTrue(stroke.maxError <= 1.5f * PIXEL)
    }

    @Test
    fun smoothingReducesJitterAndHoldsPinnedAndEndPoints() {
        val line = List(200) { Vec2(it * 0.01f, if (it % 2 == 0) 0.01f else -0.01f) }
        val smoothed = smooth(line, 4, pinned = setOf(100))
        assertEquals(line.first(), smoothed.first())
        assertEquals(line.last(), smoothed.last())
        assertEquals(line[100], smoothed[100])
        assertTrue(abs(smoothed[50].y) < 0.002f, "${smoothed[50].y}")
    }

    @Test
    fun simplifyingKeepsPointsWithinTolerance() {
        val points = circle(Vec2(0f, 0f), 1f, 300)
        val kept = simplify(points, 0.01f)
        assertTrue(kept.size < points.size / 2, "${kept.size}")
        assertEquals(points.first(), kept.first())
        assertEquals(points.last(), kept.last())
        for (p in points) {
            assertTrue((1 until kept.size).minOf { distanceToSegment(p, kept[it - 1], kept[it]) } <= 0.0101f)
        }
    }

    @Test
    fun aFitPassesThroughItsEndsAndStaysWithinTolerance() {
        val points = List(100) { Vec2(it * 0.02f, sin(it * 0.1f)) }
        val segments = fitCubics(points, 0.01f)
        assertEquals(points.first(), segments.first().p0)
        assertEquals(points.last(), segments.last().p3)
        val flat = segments.flatMap { it.sample(32) }
        for (p in points) {
            assertTrue((1 until flat.size).minOf { distanceToSegment(p, flat[it - 1], flat[it]) } <= 0.012f)
        }
    }

    @Test
    fun aFitOfTwoPointsIsAStraightCubic() {
        val segment = fitCubics(listOf(Vec2(0f, 0f), Vec2(3f, 0f)), 0.01f).single()
        assertEquals(Vec2(1f, 0f), segment.p1)
        assertEquals(Vec2(2f, 0f), segment.p2)
    }

    @Test
    fun resamplingKeepsBothEndsAtEvenSpacing() {
        val samples = resample(listOf(Vec2(0f, 0f), Vec2(1f, 0f), Vec2(1f, 1f)), 0.1f)
        assertEquals(Vec2(0f, 0f), samples.first())
        assertEquals(Vec2(1f, 1f), samples.last())
        for ((a, b) in samples.zipWithNext()) assertTrue((b - a).magnitude <= 0.15f)
    }
}
