package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.OutlineProfile
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.Polyline2
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.ViewSizing
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A solid fitted to a drawing takes the drawing's reach, height and width, and comes back to itself under the drawing of its own shape. */
class FieldFitTest {
    private val axes = PartAxes.of(Vec3.X, Vec3.Y)

    /** An ellipse from [start] to [end] along the axis, [height] tall about [middle]: a drawn loop. */
    private fun ellipse(start: Float, end: Float, height: Float, middle: Float = 0f): OutlineProfile {
        val centre = (start + end) / 2f
        val half = (end - start) / 2f
        val angles = List(97) { 2.0 * PI * it / 96 }
        val xs = FloatArray(97) { centre - half * cos(angles[it]).toFloat() }
        val ys = FloatArray(97) { middle + height / 2f * sin(angles[it]).toFloat() }
        return assertNotNull(OutlineProfile.of(Polyline2(xs, ys)))
    }

    private val ball = BlendedField(listOf(Blended(Ellipsoid(Vec3(0.5f, 0f, 0f), Vec3(0.3f, 0.1f, 0.15f)))))

    /** The ball's own silhouettes in Side and Top. */
    private fun fitOf(drawn: ViewSizing) = FieldFit.of(drawn, ellipse(0.2f, 0.8f, 0.2f), ellipse(0.2f, 0.8f, 0.3f))

    private fun sizing(height: OutlineProfile?, width: OutlineProfile?) =
        assertNotNull(ViewSizing.of(axes, height, width, openStart = false, openEnd = false, scale = 1f))

    @Test
    fun theFittedSolidReachesAndSpansAsTheDrawingDoes() {
        val drawn = sizing(ellipse(0.1f, 1.3f, 0.4f, middle = 0.05f), ellipse(0.1f, 1.3f, 0.5f))
        val fit = assertNotNull(fitOf(drawn))
        val fitted = ball.fitted(fit)
        val (start, end) = FieldLoft.reach(fitted)
        assertEquals(0.1f, start, 0.01f)
        assertEquals(1.3f, end, 0.01f)
        fun edge(direction: Vec3): Float {
            var inside = 0f
            var outside = 1f
            repeat(30) {
                val mid = (inside + outside) / 2f
                if (fitted.value(Vec3(0.7f, 0.05f, 0f) + direction * mid) < 0f) inside = mid else outside = mid
            }
            return inside
        }
        assertEquals(0.4f, edge(Vec3.Y) + edge(Vec3.Y * -1f), 0.02f, "as tall as drawn")
        assertEquals(0.5f, edge(Vec3.Z) + edge(Vec3.Z * -1f), 0.02f, "as wide as drawn")
        assertEquals(edge(Vec3.Y), edge(Vec3.Y * -1f), 0.01f, "on the drawn midline")
    }

    @Test
    fun theSolidsOwnShapeDrawnLeavesItWhereItIs() {
        val drawn = sizing(ellipse(0.2f, 0.8f, 0.2f), ellipse(0.2f, 0.8f, 0.3f))
        val fit = assertNotNull(fitOf(drawn))
        for (p in listOf(Vec3(0.5f, 0.05f, 0.1f), Vec3(0.3f, -0.03f, 0.02f), Vec3(0.7f, 0f, -0.1f))) {
            assertEquals(0f, (fit.toDrawn(p) - p).length, 0.01f, "$p")
            assertEquals(0f, (fit.toNatural(fit.toDrawn(p)) - p).length, 1e-4f, "$p comes back")
        }
    }

    @Test
    fun aDigitsAxisIsCarriedWithTheSolid() {
        val drawn = sizing(ellipse(0.2f, 1.4f, 0.2f), null)
        val fit = assertNotNull(fitOf(drawn))
        val axis = fit.axis(DigitAxis(Vec3(0.5f, 0f, 0f), Vec3.X, 0.2f, 0.03f))
        assertEquals(0.4f, axis.length, 0.01f)
        assertTrue(axis.root.x > 0.7f, "the root moves out with the stretch")
    }
}
