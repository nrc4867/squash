package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A ring's points lie at even steps of its length within each quarter, its quarter points kept, however thin it is. */
class EvenAroundTest {
    /** A flat ellipse's outline from rays at even angles from its middle, as a field loft first finds it: x side, y up. */
    private fun flatRays(count: Int, halfSide: Float, halfUp: Float): List<Vec2> = List(count) { j ->
        val angle = 2.0 * PI * j / count
        val up = cos(angle).toFloat()
        val side = sin(angle).toFloat()
        val r = 1f / kotlin.math.sqrt((side / halfSide) * (side / halfSide) + (up / halfUp) * (up / halfUp))
        Vec2(side * r, up * r)
    }

    private fun gaps(points: List<Vec2>) = points.indices.map { i -> points[(i + 1) % points.size].let { hypot(it.x - points[i].x, it.y - points[i].y) } }

    @Test
    fun aThinRingsPointsStepEvenlyRoundEachQuarter() {
        val even = evenAround(flatRays(4096, halfSide = 1f, halfUp = 0.15f), 24)
        assertEquals(24, even.size)
        val steps = gaps(even)
        val (least, most) = steps.min() to steps.max()
        assertTrue(most / least < 1.05f, "steps from $least to $most")
    }

    @Test
    fun itsQuarterPointsStayFacingUpAndToTheSide() {
        val even = evenAround(flatRays(96, halfSide = 1f, halfUp = 0.3f), 16)
        assertEquals(0f, even[0].x, 1e-5f)
        assertEquals(0.3f, even[0].y, 1e-5f)
        assertEquals(1f, even[4].x, 1e-5f)
        assertEquals(0f, even[4].y, 1e-5f)
        assertEquals(-0.3f, even[8].y, 1e-5f)
        assertEquals(-1f, even[12].x, 1e-5f)
    }

    @Test
    fun aFlatFieldLoftsWithEvenStepsRoundItsRings() {
        val field = BlendedField(listOf(Blended(Ellipsoid(Vec3(0.5f, 0f, 0f), Vec3(0.3f, 0.04f, 0.2f)))))
        val patch = FieldLoft.loft("body", field, PartAxes.of(Vec3.X, Vec3.Y), rings = 9, segments = 24, uOf = { it })
        val ring = 4
        val points = List(patch.nv) { patch.point(patch.index(ring, it)) }
        val steps = points.indices.map { (points[(it + 1) % points.size] - points[it]).length }
        assertTrue(steps.max() / steps.min() < 1.25f, "steps from ${steps.min()} to ${steps.max()}")
    }
}
