package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.collections.LongIntMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * The nearest of a mesh's [positions], three values each, to any point, found through a grid of [cell]-sized buckets: each bucket's
 * vertices held in a run of one array, in the order of their indices, so a search allocates nothing. Safe to search from many threads.
 */
internal class NearestVertex(private val positions: FloatArray, private val cell: Float) {
    private val buckets = LongIntMap()
    private val starts: IntArray
    private val members: IntArray

    /** The box round every position, low corner then high, so a point further than a reach from it is answered without a search. */
    private val box = FloatArray(6).also { box ->
        for (k in 0 until 3) {
            box[k] = Float.MAX_VALUE
            box[k + 3] = -Float.MAX_VALUE
        }
        for (i in positions.indices) {
            val k = i % 3
            box[k] = minOf(box[k], positions[i])
            box[k + 3] = maxOf(box[k + 3], positions[i])
        }
    }

    init {
        val count = positions.size / 3
        val bucketOf = IntArray(count)
        val sizes = ArrayList<Int>()
        for (v in 0 until count) {
            val key = pack(index(positions[v * 3]), index(positions[v * 3 + 1]), index(positions[v * 3 + 2]))
            var b = buckets.get(key)
            if (b < 0) {
                b = sizes.size
                buckets.put(key, b)
                sizes += 0
            }
            bucketOf[v] = b
            sizes[b] = sizes[b] + 1
        }
        starts = IntArray(sizes.size + 1)
        for (b in sizes.indices) starts[b + 1] = starts[b] + sizes[b]
        val filled = starts.copyOf(sizes.size)
        members = IntArray(count)
        for (v in 0 until count) members[filled[bucketOf[v]]++] = v
    }

    /** The nearest vertex [accept] takes to vertex [at] of [points], three values each, no further than [reach]; null where none is. */
    fun within(points: FloatArray, at: Int, reach: Float, accept: ((Int) -> Boolean)? = null): Int? {
        val x = points[at * 3]
        val y = points[at * 3 + 1]
        val z = points[at * 3 + 2]
        if (outsideBox(x, y, z, reach)) return null
        val v = nearest(x, y, z, accept)
        if (v < 0) return null
        val dx = positions[v * 3] - x
        val dy = positions[v * 3 + 1] - y
        val dz = positions[v * 3 + 2] - z
        return v.takeIf { dx * dx + dy * dy + dz * dz <= reach * reach }
    }

    /** Whether the point lies further than [reach] from the box, so no position lies within [reach] of it. */
    private fun outsideBox(x: Float, y: Float, z: Float, reach: Float): Boolean {
        val dx = maxOf(box[0] - x, 0f, x - box[3])
        val dy = maxOf(box[1] - y, 0f, y - box[4])
        val dz = maxOf(box[2] - z, 0f, z - box[5])
        return dx * dx + dy * dy + dz * dz > reach * reach
    }

    fun of(p: Vec3): Int? = nearest(p.x, p.y, p.z).takeIf { it >= 0 }

    /** Hands [action] every vertex within [reach] of [p], with its distance, in no set order. */
    fun forEachWithin(p: Vec3, reach: Float, action: (vertex: Int, distance: Float) -> Unit) {
        if (outsideBox(p.x, p.y, p.z, reach)) return
        val rings = ceil(reach / cell).toInt()
        val x = index(p.x)
        val y = index(p.y)
        val z = index(p.z)
        for (dx in -rings..rings) for (dy in -rings..rings) for (dz in -rings..rings) {
            val b = buckets.get(pack(x + dx, y + dy, z + dz))
            if (b < 0) continue
            for (k in starts[b] until starts[b + 1]) {
                val v = members[k]
                val ex = positions[v * 3] - p.x
                val ey = positions[v * 3 + 1] - p.y
                val ez = positions[v * 3 + 2] - p.z
                val d = sqrt(ex * ex + ey * ey + ez * ez)
                if (d <= reach) action(v, d)
            }
        }
    }

    /** The nearest vertex [accept] takes, all where null, in the first ring of buckets round the point's that holds any, or -1 within [MOST_RINGS] rings. */
    private fun nearest(px: Float, py: Float, pz: Float, accept: ((Int) -> Boolean)? = null): Int {
        val x = index(px)
        val y = index(py)
        val z = index(pz)
        for (ring in 1..MOST_RINGS) {
            var best = -1
            var least = Float.MAX_VALUE
            for (dx in -ring..ring) for (dy in -ring..ring) for (dz in -ring..ring) {
                val b = buckets.get(pack(x + dx, y + dy, z + dz))
                if (b < 0) continue
                for (k in starts[b] until starts[b + 1]) {
                    val v = members[k]
                    if (accept != null && !accept(v)) continue
                    val ex = positions[v * 3] - px
                    val ey = positions[v * 3 + 1] - py
                    val ez = positions[v * 3 + 2] - pz
                    val d = sqrt(ex * ex + ey * ey + ez * ez)
                    if (d < least) {
                        least = d
                        best = v
                    }
                }
            }
            if (best >= 0) return best
        }
        return -1
    }

    private fun index(x: Float): Int = floor(x / cell).toInt()

    private fun pack(x: Int, y: Int, z: Int): Long = ((x + OFFSET).toLong() shl 42) or ((y + OFFSET).toLong() shl 21) or (z + OFFSET).toLong()

    private companion object {
        const val MOST_RINGS = 4
        const val OFFSET = 1 shl 20
    }
}
