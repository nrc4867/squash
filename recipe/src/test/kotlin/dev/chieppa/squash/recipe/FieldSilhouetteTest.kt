package dev.chieppa.squash.recipe

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.field.JoinedMember
import dev.chieppa.squash.geom.field.Solid
import dev.chieppa.squash.recipe.figure.FieldSilhouette
import dev.chieppa.squash.recipe.figure.FigureView
import dev.chieppa.squash.recipe.figure.P2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A body's silhouette read off its field: the edge between two limbs drawn apart is found even where the body closes the gap. */
class FieldSilhouetteTest {
    /** A box upright from y 0 to 0.4, 0.04 deep, reaching across z from -[out] to -[gap]: the middle lies at z 0, the left at -z. */
    private class Limb(gap: Float, out: Float) : Solid {
        override val low = Vec3(-0.02f, 0f, -out)
        override val high = Vec3(0.02f, 0.4f, -gap)
        override fun distance(p: Vec3): Float {
            val centre = (low + high) * 0.5f
            val half = (high - low) * 0.5f
            val d = Vec3(abs(p.x - centre.x) - half.x, abs(p.y - centre.y) - half.y, abs(p.z - centre.z) - half.z)
            return maxOf(d.x, d.y, d.z)
        }
    }

    /** A pair of limbs, the left and its mirror, [gap] apart from the middle each. */
    private fun pair(gap: Float, middle: Float? = null): FieldSilhouette {
        val limb = Limb(gap, 0.06f)
        val members = listOf(JoinedMember(limb, Vec3.ZERO, false, 0f), JoinedMember(limb, Vec3.ZERO, true, 0f, part = 1))
        val post = middle?.let { listOf(JoinedMember(Limb(-it, it), Vec3.ZERO, false, 0f, part = 2)) }.orEmpty()
        return FieldSilhouette(JoinedField(members + post), 0.0)
    }

    /** A sample of the left limb's inner side drawn 0.01 from the middle, its normal pointing out of the limb toward the middle. */
    private val at = P2(0.01, 0.2)
    private val normal = P2(-1.0, 0.0)

    @Test
    fun aGapTheBodyLeavesIsMeasuredToItsEdge() {
        val miss = assertNotNull(pair(0.004f).edge(FigureView.FRONT, at, normal, 0.03))
        assertEquals(0.006, miss, 1e-3, "the body stands out past the drawn edge by as far as the gap is too narrow")
    }

    @Test
    fun aGapTheBodyClosesStandsOutAsFarAsTheMiddle() {
        val miss = assertNotNull(pair(0f).edge(FigureView.FRONT, at, normal, 0.03), "the other limb, past the middle, is not this edge")
        assertEquals(0.01, miss, 1e-3)
    }

    @Test
    fun theEdgeIsTheFirstOneOutFromTheSampleNotAPartBeyondIt() {
        val miss = assertNotNull(pair(0.004f, middle = 0.002f).edge(FigureView.FRONT, at, normal, 0.03), "a trunk between the limbs, past a gap")
        assertEquals(0.006, miss, 1e-3)
    }
}
