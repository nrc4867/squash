package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3
import kotlin.math.floor

/** The nearest of a mesh's [positions], three values each, to any point, found through a grid of [cell]-sized buckets. */
internal class NearestVertex(private val positions: FloatArray, private val cell: Float) {
    private val buckets = HashMap<Long, MutableList<Int>>()

    init {
        for (v in 0 until positions.size / 3) buckets.getOrPut(key(positions.vec(v * 3))) { ArrayList() } += v
    }

    /** The nearest vertex to vertex [at] of [points], three values each, no further than [reach]; null where none is. */
    fun within(points: FloatArray, at: Int, reach: Float): Int? {
        val p = Vec3(points[at * 3], points[at * 3 + 1], points[at * 3 + 2])
        val v = of(p) ?: return null
        val dx = positions[v * 3] - p.x
        val dy = positions[v * 3 + 1] - p.y
        val dz = positions[v * 3 + 2] - p.z
        return v.takeIf { dx * dx + dy * dy + dz * dz <= reach * reach }
    }

    fun of(p: Vec3): Int? {
        for (ring in 1..MOST_RINGS) {
            var best = -1
            var least = Float.MAX_VALUE
            val (x, y, z) = Triple(index(p.x), index(p.y), index(p.z))
            for (dx in -ring..ring) for (dy in -ring..ring) for (dz in -ring..ring) {
                buckets[pack(x + dx, y + dy, z + dz)]?.forEach { v ->
                    val d = (positions.vec(v * 3) - p).length
                    if (d < least) {
                        least = d
                        best = v
                    }
                }
            }
            if (best >= 0) return best
        }
        return null
    }

    private fun index(x: Float): Int = floor(x / cell).toInt()

    private fun key(p: Vec3): Long = pack(index(p.x), index(p.y), index(p.z))

    private fun pack(x: Int, y: Int, z: Int): Long = ((x + OFFSET).toLong() shl 42) or ((y + OFFSET).toLong() shl 21) or (z + OFFSET).toLong()

    private companion object {
        const val MOST_RINGS = 4
        const val OFFSET = 1 shl 20
    }
}
