package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A simple solid as a distance-like field in a part's own axes (x along the part, y toward its up, z toward its side): negative
 * inside, positive outside, near the true distance close to the surface.
 */
sealed interface Primitive {
    /** The field at [p]. */
    fun distance(p: Vec3): Float

    /** The least corner of a box holding the solid. */
    val low: Vec3

    /** The greatest corner of a box holding the solid. */
    val high: Vec3
}

/**
 * An ellipsoid about [centre] with semi-axes [radii] along x, y and z. Its z semi-axis is [narrows] shorter toward smaller x,
 * eased in over [over] either side of the centre, as a palm narrows into the wrist.
 *
 * The field is the first-order distance of the scaled sphere |q / r| - 1: that function over the length of its gradient, which is
 * k0 (k0 - 1) / k1 with k0 = |q / r| and k1 = |q / r²|.
 */
data class Ellipsoid(val centre: Vec3, val radii: Vec3, val narrows: Float = 0f, val over: Float = 0f) : Primitive {
    override fun distance(p: Vec3): Float {
        val q = p - centre
        val rz = if (narrows == 0f || over <= 0f) radii.z else radii.z * (1f - narrows * smoothstep(-over, over, -q.x))
        val sx = q.x / radii.x
        val sy = q.y / radii.y
        val sz = q.z / rz
        val k0 = sqrt(sx * sx + sy * sy + sz * sz)
        val k1 = sqrt(sx * sx / (radii.x * radii.x) + sy * sy / (radii.y * radii.y) + sz * sz / (rz * rz))
        if (k1 < TINY) return -min(radii.x, min(radii.y, rz))
        return k0 * (k0 - 1f) / k1
    }

    override val low: Vec3 get() = centre - radii
    override val high: Vec3 get() = centre + radii
}

/** A capsule from [from] to [to] whose radius runs from [fromRadius] to [toRadius]: the distance to the segment less the radius there. */
data class Capsule(val from: Vec3, val to: Vec3, val fromRadius: Float, val toRadius: Float = fromRadius) : Primitive {
    override fun distance(p: Vec3): Float {
        val along = to - from
        val lengthSquared = along dot along
        val t = if (lengthSquared < TINY) 0f else (((p - from) dot along) / lengthSquared).coerceIn(0f, 1f)
        return (p - (from + along * t)).length - (fromRadius + (toRadius - fromRadius) * t)
    }

    private val reach: Float get() = max(fromRadius, toRadius)
    override val low: Vec3 get() = Vec3(min(from.x, to.x) - reach, min(from.y, to.y) - reach, min(from.z, to.z) - reach)
    override val high: Vec3 get() = Vec3(max(from.x, to.x) + reach, max(from.y, to.y) + reach, max(from.z, to.z) + reach)
}

private const val TINY = 1e-12f

/** 0 below [edge0], 1 above [edge1], a smooth cubic between; reversed edges run the other way. */
fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge0 == edge1) return if (x < edge0) 0f else 1f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The smooth union of two distance fields with blend radius [k]: min(a, b) − k h² / 4, h = max(0, 1 − |a − b| / k). Wherever the
 * fields differ by [k] or more it is their plain union, so far from where two solids meet nothing changes, and where they meet the
 * surface fills in by at most k / 4.
 */
fun smoothMin(a: Float, b: Float, k: Float): Float {
    if (k <= 0f) return min(a, b)
    val h = max(0f, 1f - abs(a - b) / k)
    return min(a, b) - k * h * h / 4f
}

/** The smooth intersection of two distance fields with blend radius [k], the mirror of [smoothMin]. */
fun smoothMax(a: Float, b: Float, k: Float): Float = -smoothMin(-a, -b, k)
