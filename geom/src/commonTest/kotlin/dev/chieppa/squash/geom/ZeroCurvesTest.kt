package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val RINGS = 11
private const val AROUND = 16

/** A straight tube of radius 1 along x from 0 to 1, closed around v, rings evenly spaced. */
private fun tube(): Patch {
    val points = FloatArray(RINGS * AROUND * Patch.COMPONENTS)
    for (ring in 0 until RINGS) {
        for (segment in 0 until AROUND) {
            val angle = 2.0 * PI * segment / AROUND
            val at = (ring * AROUND + segment) * Patch.COMPONENTS
            points[at] = ring.toFloat() / (RINGS - 1)
            points[at + 1] = cos(angle).toFloat()
            points[at + 2] = sin(angle).toFloat()
        }
    }
    return Patch("tube", RINGS, AROUND, closedV = true, slices = null, points = points)
}

class ZeroCurvesTest {
    @Test
    fun aPlaneAcrossATubeIsOneClosedCurveAtItsPlace() {
        val curves = tube().zeroCurves { it.x - 0.37f }
        val curve = curves.single()
        assertEquals(curve.first(), curve.last(), "the curve closes")
        assertEquals(AROUND + 1, curve.size)
        assertTrue(curve.all { abs(it.u - 0.37f) < 1e-4f }, "$curve")
    }

    @Test
    fun aPlaneAlongATubeCrossesItTwice() {
        val curves = tube().zeroCurves { it.y - 0.1f }
        assertEquals(2, curves.size)
        curves.forEach { curve -> assertEquals(RINGS, curve.size) }
    }

    @Test
    fun aFieldThatNeverCrossesHasNoCurve() {
        assertTrue(tube().zeroCurves { 1f }.isEmpty())
    }

    @Test
    fun aSmallBlobIsAClosedCurveAroundIt() {
        val center = Vec3(0.5f, 1f, 0f)
        val curve = tube().zeroCurves { (it - center).length - 0.35f }.single()
        assertEquals(curve.first(), curve.last())
        assertTrue(curve.size > 4)
    }
}
