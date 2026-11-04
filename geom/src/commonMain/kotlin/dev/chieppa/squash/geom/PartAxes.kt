package dev.chieppa.squash.geom

import kotlin.math.abs

/** The cosine of 45 degrees: a rest direction this close to vertical counts as vertical. */
private const val VERTICAL_AXIS_DOT = 0.7071f

/**
 * A part's own axes, in body space: [along] the rest axis from the socket end, [up] toward v = 0, [side] toward
 * v = 0.25. The socket plane is a = 0.
 */
data class PartAxes(val along: Vec3, val up: Vec3) {
    val side: Vec3 get() = up cross along

    fun point(a: Float, b: Float, c: Float): Vec3 = along * a + up * b + side * c

    companion object {
        /** Axes around [rest]; up is [towards], else body up, or body forward when the rest axis lies within 45 degrees of vertical. */
        fun of(rest: Vec3, towards: Vec3? = null): PartAxes {
            val along = rest.normalized()
            val reference = towards ?: if (abs(along dot Vec3.Y) > VERTICAL_AXIS_DOT) Vec3.X else Vec3.Y
            return PartAxes(along, (reference - along * (reference dot along)).normalized())
        }
    }
}
