package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3
import kotlin.math.atan2
import kotlin.math.tan

/** Below this length an edge or a tangent counts as none. */
private const val TINY = 1e-9f

/**
 * Each of [count] vertices' neighbours across the edges of [triangles] at rest, vertex v's from [starts][v] until [starts][v + 1] in
 * [around], each with its mean value weight (Floater, "Mean value coordinates", 2003) in [weights], read in the plane square to the
 * vertex's rest normal: the weights that rebuild the vertex as the mean of its neighbours where it lies at rest, so a vertex relaxed toward
 * them in a pose keeps the spacing the rest surface gave it rather than bunching (Vaillant et al., "Implicit Skinning", 2013). A vertex on
 * the mesh's border, one of whose edges only one triangle holds, is [open] and is not relaxed.
 */
internal class RestRings(count: Int, triangles: IntArray, positions: FloatArray, normals: FloatArray) {
    val starts = IntArray(count + 1)
    val around: IntArray
    val weights: FloatArray
    val open = BooleanArray(count)

    init {
        val sets = Array(count) { LinkedHashMap<Int, Int>(8) }
        for (t in 0 until triangles.size / 3) {
            val a = triangles[t * 3]
            val b = triangles[t * 3 + 1]
            val c = triangles[t * 3 + 2]
            edge(sets, a, b); edge(sets, b, c); edge(sets, c, a)
        }
        for (v in 0 until count) {
            starts[v + 1] = starts[v] + sets[v].size
            if (sets[v].values.any { it != 2 }) open[v] = true
        }
        around = IntArray(starts[count])
        weights = FloatArray(starts[count])
        for (v in 0 until count) sets[v].keys.forEachIndexed { i, n -> around[starts[v] + i] = n }
        for (t in 0 until triangles.size / 3) {
            val a = triangles[t * 3]
            val b = triangles[t * 3 + 1]
            val c = triangles[t * 3 + 2]
            corner(a, b, c, positions, normals)
            corner(b, c, a, positions, normals)
            corner(c, a, b, positions, normals)
        }
        for (v in 0 until count) {
            var sum = 0f
            for (k in starts[v] until starts[v + 1]) sum += weights[k]
            if (sum <= TINY || sum.isNaN()) open[v] = true
            else for (k in starts[v] until starts[v + 1]) weights[k] /= sum
        }
    }

    /** Counts edge a-b once more for each end; an edge two triangles hold is counted twice. */
    private fun edge(sets: Array<LinkedHashMap<Int, Int>>, a: Int, b: Int) {
        if (a == b) return
        sets[a][b] = (sets[a][b] ?: 0) + 1
        sets[b][a] = (sets[b][a] ?: 0) + 1
    }

    /** Adds triangle (v, b, c)'s share to v's weights of b and c: tan of half its angle at v, over each edge's length, in v's tangent plane. */
    private fun corner(v: Int, b: Int, c: Int, positions: FloatArray, normals: FloatArray) {
        val n = normals.vec(v * 3)
        val p = positions.vec(v * 3)
        val eb = flat(positions.vec(b * 3) - p, n)
        val ec = flat(positions.vec(c * 3) - p, n)
        val lb = eb.length
        val lc = ec.length
        if (lb <= TINY || lc <= TINY) return
        val angle = atan2((eb cross ec).length, eb dot ec)
        val half = tan(angle / 2f)
        add(v, b, half / lb)
        add(v, c, half / lc)
    }

    private fun flat(e: Vec3, n: Vec3): Vec3 = e - n * (e dot n)

    private fun add(v: Int, n: Int, w: Float) {
        for (k in starts[v] until starts[v + 1]) if (around[k] == n) {
            weights[k] += w
            return
        }
    }
}
