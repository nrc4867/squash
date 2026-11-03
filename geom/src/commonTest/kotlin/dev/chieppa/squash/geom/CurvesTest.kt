package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CurvesTest {
    private val arch = CubicBezier(Vec2(0f, 0f), Vec2(0f, 2f), Vec2(2f, 2f), Vec2(2f, 0f))

    @Test
    fun bezierHitsItsEndsAndSamples() {
        assertEquals(arch.p0, arch.pointAt(0f))
        assertEquals(arch.p3, arch.pointAt(1f))
        val samples = arch.sample(5)
        assertEquals(5, samples.size)
        assertEquals(Vec2(1f, 1.5f), samples[2])
    }

    @Test
    fun bezierCrossesALineTwice() {
        val crossings = arch.crossings(Line2(Vec2(0f, 1f), Vec2(1f, 0f)))
        assertEquals(2, crossings.size)
        crossings.forEach { assertEquals(1f, arch.pointAt(it).y, 1e-3f) }
        assertTrue(crossings[0] < crossings[1])
    }

    @Test
    fun bezierMissingALineHasNoCrossings() {
        assertTrue(arch.crossings(Line2(Vec2(0f, 5f), Vec2(1f, 0f))).isEmpty())
    }

    @Test
    fun aPathCountsACrossingOnAJoinOnce() {
        val left = CubicBezier(Vec2(-2f, 0f), Vec2(-1f, 0f), Vec2(-1f, 0f), Vec2(0f, 0f))
        val right = CubicBezier(Vec2(0f, 0f), Vec2(1f, 0f), Vec2(1f, 0f), Vec2(2f, 0f))
        val crossings = BezierPath(listOf(left, right)).crossings(Line2(Vec2(0f, -1f), Vec2(0f, 1f)))
        assertEquals(listOf(Vec2(0f, 0f)), crossings)
    }

    @Test
    fun axisSplinePassesThroughItsPoints() {
        val points = listOf(Vec3.ZERO, Vec3(1f, 1f, 0f), Vec3(2f, 0f, 1f), Vec3(3f, 2f, 0f))
        val spline = AxisSpline(points)
        val total = points.zipWithNext().sumOf { (a, b) -> (b - a).length.toDouble() }.toFloat()
        var travelled = 0f
        points.zipWithNext().forEach { (a, b) ->
            assertClose(a, spline.pointAt(travelled / total))
            travelled += (b - a).length
        }
        assertClose(points.last(), spline.pointAt(1f))
    }

    @Test
    fun aStraightAxisStaysStraightWithAConstantTangent() {
        val spline = AxisSpline(listOf(Vec3.ZERO, Vec3(0f, -2f, 0f)))
        assertClose(Vec3(0f, -1f, 0f), spline.pointAt(0.5f))
        assertClose(Vec3(0f, -1f, 0f), spline.tangentAt(0.25f))
    }

    @Test
    fun everyProfileHasTheRequestedControlPointsAndUnitSpan() {
        for (profile in SectionProfile.entries) {
            val spline = profile.spline(16)
            assertEquals(16, spline.controlPoints.size)
            val samples = spline.sample(16)
            val span = maxOf(samples.maxOf { it.x } - samples.minOf { it.x }, samples.maxOf { it.y } - samples.minOf { it.y })
            assertEquals(2f, span, 1e-3f, profile.name)
        }
    }

    @Test
    fun profilesStartOnTopAndReachTheBottomHalfwayRound() {
        val samples = SectionProfile.CIRCLE.spline(16).sample(16)
        assertTrue(samples[0].y > 0.9f && abs(samples[0].x) < 1e-3f)
        assertTrue(samples[8].y < -0.9f)
        assertTrue(samples[4].x > 0.9f)
    }

    @Test
    fun aKeelIsNarrowUnderneathAndALensIsFlat() {
        val keel = SectionProfile.KEEL.spline(16).sample(16)
        assertTrue(abs(keel[6].x) < abs(keel[2].x))
        val lens = SectionProfile.LENS.spline(16).sample(16)
        assertTrue(lens.maxOf { it.y } - lens.minOf { it.y } < 1f)
    }

    private fun assertClose(expected: Vec3, actual: Vec3) {
        assertTrue((expected - actual).length < 1e-3f, "Expected $expected, was $actual")
    }
}
