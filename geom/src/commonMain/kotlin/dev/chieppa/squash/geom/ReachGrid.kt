package dev.chieppa.squash.geom

import kotlin.math.floor

/** Places in space, binned in cubes [reach] wide, that answer whether any of them lies within [reach] of a place. */
class ReachGrid(private val reach: Float) {
    private val cells = HashMap<Long, MutableList<Vec3>>()

    private fun cellOf(v: Float): Int = floor(v / reach).toInt()

    private fun key(x: Int, y: Int, z: Int): Long = ((x.toLong() and MASK) shl 42) or ((y.toLong() and MASK) shl 21) or (z.toLong() and MASK)

    /** Adds [place]. */
    fun add(place: Vec3) {
        cells.getOrPut(key(cellOf(place.x), cellOf(place.y), cellOf(place.z))) { ArrayList() } += place
    }

    /** Whether a place added lies nearer [place] than the reach. */
    fun near(place: Vec3): Boolean {
        val cx = cellOf(place.x)
        val cy = cellOf(place.y)
        val cz = cellOf(place.z)
        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
            val points = cells[key(cx + dx, cy + dy, cz + dz)] ?: continue
            for (q in points) if ((q - place).length < reach) return true
        }
        return false
    }

    private companion object {
        const val MASK = 0x1FFFFFL
    }
}
