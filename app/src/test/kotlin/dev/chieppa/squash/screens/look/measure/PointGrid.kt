package dev.chieppa.squash.screens.look.measure

import kotlin.math.floor

/** The points of [positions], three floats each, bucketed in cubes of side [cell], for finding the nearest within a reach. */
class PointGrid(private val positions: FloatArray, private val cell: Float) {
    private val buckets = HashMap<Long, MutableList<Int>>()

    init {
        for (i in 0 until positions.size / 3) buckets.getOrPut(key(cell(positions[i * 3]), cell(positions[i * 3 + 1]), cell(positions[i * 3 + 2]))) { mutableListOf() } += i
    }

    private fun cell(value: Float) = floor(value / cell).toInt()

    private fun key(x: Int, y: Int, z: Int): Long = (x.toLong() and 0x1FFFFF shl 42) or (y.toLong() and 0x1FFFFF shl 21) or (z.toLong() and 0x1FFFFF)

    /** The index of the point nearest ([x], [y], [z]) no further than [reach], or null; [accept] limits which points count. */
    fun nearest(x: Float, y: Float, z: Float, reach: Float, accept: (Int) -> Boolean = { true }): Int? {
        val span = kotlin.math.ceil(reach / cell).toInt()
        val cx = cell(x)
        val cy = cell(y)
        val cz = cell(z)
        var best: Int? = null
        var bestSq = reach * reach
        for (i in -span..span) for (j in -span..span) for (k in -span..span) {
            val members = buckets[key(cx + i, cy + j, cz + k)] ?: continue
            for (p in members) {
                val dx = positions[p * 3] - x
                val dy = positions[p * 3 + 1] - y
                val dz = positions[p * 3 + 2] - z
                val sq = dx * dx + dy * dy + dz * dz
                if (sq <= bestSq && accept(p)) {
                    bestSq = sq
                    best = p
                }
            }
        }
        return best
    }
}
