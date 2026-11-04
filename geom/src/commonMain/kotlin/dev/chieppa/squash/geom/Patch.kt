package dev.chieppa.squash.geom

import kotlin.math.floor

/** One cross-section of a swept patch. Its section is scaled by half of [width] and [height]. */
data class Slice(
    val u: Float,
    val frame: Frame,
    val height: Float,
    val width: Float,
    val section: ClosedSpline,
    val proxyNormalFrame: Mat3,
) {
    /** The point of this ring at [v] around it, as a patch lofted from it holds it. */
    fun pointAt(v: Float): Vec3 {
        val around = section.pointAt(v - floor(v))
        return frame.center + frame.side * (around.x * width / 2f) + frame.up * (around.y * height / 2f)
    }
}

/**
 * A named surface: a fixed grid of [nu] rings by [nv] points, closed in v for a tube and open for a sheet. A ring whose
 * points all coincide is a pole. [slices] is present for swept patches. [boneU] is the u along the part that every
 * vertex takes its bone from, for a patch whose own u does not run along the part.
 */
class Patch(
    val name: String,
    val nu: Int,
    val nv: Int,
    val closedV: Boolean,
    val slices: List<Slice>?,
    val points: FloatArray,
    val boneU: Float? = null,
) {
    init {
        require(points.size == nu * nv * COMPONENTS) { "Patch $name holds ${points.size} values for a $nu by $nv grid" }
    }

    fun index(ring: Int, segment: Int): Int = ring * nv + segment

    fun point(index: Int): Vec3 =
        Vec3(points[index * COMPONENTS], points[index * COMPONENTS + 1], points[index * COMPONENTS + 2])

    override fun equals(other: Any?): Boolean =
        other is Patch && name == other.name && nu == other.nu && nv == other.nv && closedV == other.closedV &&
            slices == other.slices && points.contentEquals(other.points) && boneU == other.boneU

    override fun hashCode(): Int = 31 * (31 * name.hashCode() + nu) + points.contentHashCode()

    companion object {
        const val COMPONENTS = 3
    }
}
