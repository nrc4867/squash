package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PIXEL = 0.01f
private const val COARSE_PIXEL = 0.03f
private const val TANGENT_TOLERANCE = 1e-3f
private val PLANE = StrokeEnds.CutAtPlane()

class OversketchTest {
    /** Five straight-ish sections from the plane out along x and back, on the plane at both ends. */
    private val open = BezierPath(
        listOf(
            straight(Vec2(0f, 1f), Vec2(1f, 1f)),
            straight(Vec2(1f, 1f), Vec2(2f, 1f)),
            straight(Vec2(2f, 1f), Vec2(3f, 1f)),
            straight(Vec2(3f, 1f), Vec2(3f, -1f)),
            straight(Vec2(3f, -1f), Vec2(0f, -1f)),
        ),
    )

    /** A closed diamond of four sections. */
    private val closed = BezierPath(
        listOf(
            straight(Vec2(2f, 0f), Vec2(0f, 2f)),
            straight(Vec2(0f, 2f), Vec2(-2f, 0f)),
            straight(Vec2(-2f, 0f), Vec2(0f, -2f)),
            straight(Vec2(0f, -2f), Vec2(2f, 0f)),
        ),
    )

    private fun straight(a: Vec2, b: Vec2) = CubicBezier(a, a + (b - a) * (1f / 3f), a + (b - a) * (2f / 3f), b)

    /** A bump above y = 1 from [fromX] to [toX], [height] tall in the middle. */
    private fun bump(fromX: Float, toX: Float, height: Float, count: Int = 60): List<Vec2> = List(count) { i ->
        val t = i.toFloat() / (count - 1)
        Vec2(fromX + (toX - fromX) * t, 1f + height * 4f * t * (1f - t))
    }

    private fun direction(a: Vec2, b: Vec2) = (b - a).unit()

    private fun assertSameDirection(a: Vec2, b: Vec2) {
        assertEquals(a.x, b.x, TANGENT_TOLERANCE)
        assertEquals(a.y, b.y, TANGENT_TOLERANCE)
    }

    @Test
    fun sectionsOutsideTheSpanKeepTheirExactValues() {
        val result = assertNotNull(oversketch(open, bump(1f, 2f, 0.3f), PLANE, PIXEL))
        assertEquals(1, result.firstSection)
        assertEquals(1, result.replaced)
        val segments = result.path.segments
        assertEquals(open.segments[0], segments.first())
        assertEquals(open.segments.subList(2, 5), segments.takeLast(3))
    }

    @Test
    fun theJoinsAreOnTheKeptAnchorsWithContinuousTangents() {
        val result = assertNotNull(oversketch(open, bump(1f, 2f, 0.3f), PLANE, PIXEL))
        val added = result.path.segments.subList(1, 1 + result.added)
        assertEquals(Vec2(1f, 1f), added.first().p0)
        assertEquals(Vec2(2f, 1f), added.last().p3)
        val before = open.segments[0]
        assertSameDirection(direction(before.p2, before.p3), direction(added.first().p0, added.first().p1))
        val after = open.segments[2]
        assertSameDirection(direction(added.last().p2, added.last().p3), direction(after.p0, after.p1))
    }

    @Test
    fun theNewSectionsFollowTheStrokeAwayFromTheJoins() {
        val result = assertNotNull(oversketch(open, bump(1f, 2f, 0.3f), PLANE, PIXEL))
        assertTrue(result.maxError / PIXEL < 2f, "the fit stays within a pixel or two")
        val highest = result.path.flatten().ys.max()
        assertEquals(1.3f, highest, 0.02f, "the middle of the stroke is the middle of the new span")
    }

    @Test
    fun aStrokeInsideOneLongSectionKeepsWhatItDidNotReach() {
        val result = assertNotNull(oversketch(open, bump(1.3f, 1.6f, 0.2f), PLANE, PIXEL, OversketchOptions(snapEnds = false)))
        assertEquals(1, result.firstSection)
        assertEquals(1, result.replaced)
        val flat = result.path.flatten()
        for (i in flat.xs.indices) {
            if (flat.ys[i] > 0f && flat.xs[i] in 1.05f..1.25f) assertEquals(1f, flat.ys[i], 0.02f, "the old curve holds before the stroke")
        }
        assertEquals(1.2f, flat.ys.max(), 0.02f)
    }

    @Test
    fun aStrokeDrawnBackwardsRunsWithTheCurve() {
        val forward = assertNotNull(oversketch(open, bump(1f, 2f, 0.3f), PLANE, PIXEL))
        val backward = assertNotNull(oversketch(open, bump(1f, 2f, 0.3f).reversed(), PLANE, PIXEL))
        assertEquals(forward.firstSection, backward.firstSection)
        assertEquals(forward.path.flatten().ys.max(), backward.path.flatten().ys.max(), 0.01f)
    }

    @Test
    fun aStrokeWithAFarEndIsNoOversketch() {
        assertNull(oversketch(open, bump(1f, 2f, 0.3f).map { Vec2(it.x, it.y + 0.5f) }, PLANE, PIXEL))
        val leaving = List(40) { Vec2(1f, 1f + it * 0.05f) }
        assertNull(oversketch(open, leaving, PLANE, PIXEL))
    }

    @Test
    fun aStrokeTooShortIsNoOversketch() {
        assertNull(oversketch(open, listOf(Vec2(1f, 1f), Vec2(1.01f, 1f)), PLANE, PIXEL))
    }

    @Test
    fun aStrokePastTheSocketPlaneSetsTheOpenEndOnThePlane() {
        val stroke = List(60) { i -> Vec2(-0.4f + 1.4f * i / 59f, 1.3f - 0.3f * i / 59f) }
        val result = assertNotNull(oversketch(open, stroke, PLANE, PIXEL))
        assertEquals(0, result.firstSection)
        val first = result.path.segments.first().p0
        assertEquals(0f, first.x)
        assertTrue(first.y > 1.1f, "the open end moved along the plane to where the stroke crossed it")
        assertEquals(open.segments.subList(1, 5), result.path.segments.takeLast(4))
        assertTrue(result.path.flatten().xs.min() >= 0f, "nothing behind the plane")
    }

    @Test
    fun aStrokeStoppingShortOfTheOpenEndLeavesItOnThePlane() {
        val stroke = List(60) { i -> Vec2(0.1f + 0.9f * i / 59f, 1.1f - 0.1f * i / 59f) }
        val result = assertNotNull(oversketch(open, stroke, PLANE, PIXEL))
        assertEquals(Vec2(0f, 1f), result.path.segments.first().p0)
    }

    @Test
    fun theFarEndOfACurveMovesWhenTheStrokeRedrawsIt() {
        val stroke = List(60) { i -> Vec2(2f + 1f * i / 59f + 0.8f * 4f * (i / 59f) * (1f - i / 59f), 1f - 2f * i / 59f) }
        val result = assertNotNull(oversketch(open, stroke, PLANE, PIXEL))
        assertTrue(result.path.flatten().xs.max() != open.flatten().xs.max())
    }

    @Test
    fun aClosedCurveStaysClosedAndKeepsItsOtherSections() {
        val stroke = List(60) { i ->
            val t = i.toFloat() / 59f
            Vec2(2f - 2f * t + 0.3f * 4f * t * (1f - t), 2f * t + 0.3f * 4f * t * (1f - t))
        }
        val result = assertNotNull(oversketch(closed, stroke, StrokeEnds.Closed, PIXEL))
        val segments = result.path.segments
        assertEquals(segments.first().p0, segments.last().p3)
        assertEquals(closed.segments.subList(1, 4), segments.takeLast(3))
    }

    @Test
    fun aStrokeOverAClosedCurvesSeamKeepsTheSectionsItMisses() {
        val stroke = List(80) { i ->
            val t = i.toFloat() / 79f
            val x = if (t < 0.5f) 2f * t * 2f else 2f - 2f * (t - 0.5f) * 2f
            val y = if (t < 0.5f) -2f + 2f * t * 2f else 2f * (t - 0.5f) * 2f
            Vec2(x + 0.4f * 4f * t * (1f - t), y)
        }
        val result = assertNotNull(oversketch(closed, stroke, StrokeEnds.Closed, PIXEL))
        val segments = result.path.segments
        assertEquals(segments.first().p0, segments.last().p3)
        assertEquals(result.replaced, 2)
        assertTrue(closed.segments[1] in segments && closed.segments[2] in segments, "the far two sections survive whole")
    }

    @Test
    fun aClosedStrokeOverTheWholeCurveRedrawsIt() {
        val loop = List(200) { i ->
            val angle = 2.0 * kotlin.math.PI * i / 199
            Vec2(2f * kotlin.math.cos(angle).toFloat(), 2f * kotlin.math.sin(angle).toFloat())
        }
        val result = assertNotNull(oversketch(closed, loop, StrokeEnds.Closed, COARSE_PIXEL))
        assertEquals(closed.segments.size, result.replaced)
        assertEquals(result.path.segments.first().p0, result.path.segments.last().p3)
    }

    @Test
    fun aLongerFalloffHoldsCloserToTheOldCurveNearTheJoins() {
        val stroke = bump(1f, 2f, 0.3f)
        fun heightNearTheJoin(falloff: Float): Float {
            val flat = assertNotNull(oversketch(open, stroke, PLANE, PIXEL, OversketchOptions(falloff))).path.flatten()
            return flat.ys[flat.xs.indices.minBy { abs(flat.xs[it] - 1.1f) }]
        }
        assertTrue(heightNearTheJoin(0.5f) < heightNearTheJoin(0f))
    }

    @Test
    fun snapEndsOffStillJoinsTheEnclosingAnchors() {
        val result = assertNotNull(oversketch(open, bump(1.02f, 1.98f, 0.3f), PLANE, PIXEL, OversketchOptions(snapEnds = false)))
        assertEquals(open.segments[0], result.path.segments.first())
        assertEquals(open.segments[2], result.path.segments[1 + result.added])
    }

    @Test
    fun thePreviewShowsTheWholeCurveWithTheStrokeInPlace() {
        val stroke = bump(1f, 2f, 0.3f)
        val preview = assertNotNull(oversketchPreview(open, stroke, PLANE, PIXEL))
        assertEquals(Vec2(0f, 1f), preview.line.first())
        assertEquals(Vec2(0f, -1f), preview.line.last())
        assertEquals(1.3f, preview.line.maxOf { it.y }, 0.02f)
        assertTrue(preview.touched.all { it.y == 1f && it.x in 1f..2f }, "the stretch of the old curve under the stroke")
    }

    @Test
    fun thePreviewWaitsForAStartNearTheCurve() {
        assertNull(oversketchPreview(open, List(10) { Vec2(1.5f, 2f + it * 0.01f) }, PLANE, PIXEL))
        assertNotNull(oversketchPreview(open, List(10) { Vec2(1.5f + it * 0.1f, 1f + it * 0.2f) }, PLANE, PIXEL), "a far tail is still in progress")
    }
}
