package dev.chieppa.squash.geom.blend

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

private const val DEGREES = PI / 180.0

/** Gradients closer than this run alongside each other, and the blend is shut. */
private const val SHUT_ALONGSIDE = 15.0 * DEGREES

/** From here to [OPEN_FACING] the blend is wide open. */
private const val OPEN_ALONGSIDE = 60.0 * DEGREES
private const val OPEN_FACING = 100.0 * DEGREES

/** Gradients further apart than this face each other, and the blend is down to its share apart. */
private const val SHUT_FACING = 140.0 * DEGREES

/**
 * How two signed distance fields join: their union, less a fillet where they cross. Of two fields a and b with radius k the
 * joined field is min(a, b) - k h² / 4, h = max(0, 1 - |a - b| / k): the union wherever the fields differ by k or more, so far
 * from where the surfaces cross nothing moves, and the surface grows by at most k / 4 at the crossing itself.
 *
 * The radius opens and closes with the angle between the two fields' gradients: whole where they cross square, none where they
 * run alongside each other, so two surfaces lying together do not bulge, and [apart] of it where they face each other across a
 * gap, so two shapes that do not touch blend only as much as asked. See docs/work/spike-implicit-blend.md.
 */
class GradientBlend(val radius: Float, val apart: Float = 0f) {
    /** The share of the radius open where the gradients' directions have dot product [cos]. */
    fun opening(cos: Double): Double {
        val angle = acos(cos.coerceIn(-1.0, 1.0))
        return when {
            angle <= SHUT_ALONGSIDE -> 0.0
            angle < OPEN_ALONGSIDE -> eased((angle - SHUT_ALONGSIDE) / (OPEN_ALONGSIDE - SHUT_ALONGSIDE))
            angle <= OPEN_FACING -> 1.0
            angle < SHUT_FACING -> apart + (1.0 - apart) * (1.0 - eased((angle - OPEN_FACING) / (SHUT_FACING - OPEN_FACING)))
            else -> apart.toDouble()
        }
    }

    private fun eased(t: Double): Double = t * t * (3.0 - 2.0 * t)

    /**
     * The joined value of fields [a] and [b], whose gradients' directions have dot product [cos], with how much each field's
     * gradient makes the joined one's written into [shares]: two values that sum to one.
     */
    fun join(a: Double, b: Double, cos: Double, shares: DoubleArray): Double {
        val k = radius * opening(cos)
        val aLeads = a <= b
        val h = if (k > 0.0) (1.0 - abs(a - b) / k).coerceAtLeast(0.0) else 0.0
        val lead = 1.0 - h / 2.0
        shares[0] = if (aLeads) lead else 1.0 - lead
        shares[1] = 1.0 - shares[0]
        return minOf(a, b) - k * h * h / 4.0
    }

    /** The most the surface can grow anywhere: a quarter of the radius. */
    val reach: Float get() = radius / 4f

    companion object {
        /** The dot product of two gradients' directions, or 1 when either has none. */
        fun cosine(g: DoubleArray, h: DoubleArray): Double {
            val gl = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
            val hl = sqrt(h[0] * h[0] + h[1] * h[1] + h[2] * h[2])
            if (gl == 0.0 || hl == 0.0) return 1.0
            return (g[0] * h[0] + g[1] * h[1] + g[2] * h[2]) / (gl * hl)
        }
    }
}
