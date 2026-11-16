package dev.chieppa.squash.geom

/** A grid of points in body space, [nu] rings of [nv] columns, ring after ring, closed round when [closedV]. */
class PointGrid(val points: FloatArray, val nu: Int, val nv: Int, val closedV: Boolean) {
    init {
        require(points.size == nu * nv * Patch.COMPONENTS) { "A grid of $nu by $nv holds ${points.size} values" }
    }

    fun point(ring: Int, column: Int): Vec3 {
        val at = (ring * nv + column) * Patch.COMPONENTS
        return Vec3(points[at], points[at + 1], points[at + 2])
    }
}

