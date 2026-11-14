package dev.chieppa.squash.geom

import dev.chieppa.squash.geom.collections.FloatList
import dev.chieppa.squash.geom.collections.IntList
import dev.chieppa.squash.geom.collections.LongIntMap
import kotlin.math.sqrt

/**
 * The part of a mesh where a value each vertex holds is at least zero, cut along each triangle where it crosses zero. Each of its
 * vertices is a mix of two of the mesh's, [from] and [to], by [t] of the way, so the cut lies exactly on the mesh's triangles and can be
 * laid again on the same mesh moved, posed or shaded anew. Its [triangles] are wound as the mesh's.
 */
class LevelCut(val from: IntArray, val to: IntArray, val t: FloatArray, val triangles: IntArray) {
    val isEmpty: Boolean get() = triangles.isEmpty()
    val vertexCount: Int get() = from.size

    /** The cut laid on [source], a mesh with the same vertices as the one it was cut from. */
    fun mesh(source: RenderMesh): RenderMesh {
        val width = RenderMesh.FLOATS_PER_VERTEX
        val values = source.vertices
        val out = FloatArray(vertexCount * width)
        for (i in 0 until vertexCount) {
            val a = from[i] * width
            val b = to[i] * width
            val s = t[i]
            for (c in 0 until width) out[i * width + c] = values[a + c] * (1f - s) + values[b + c] * s
            if (s > 0f) {
                normalize(out, i * width + RenderMesh.NORMAL_OFFSET_FLOATS)
                normalize(out, i * width + RenderMesh.PROXY_OFFSET_FLOATS)
            }
        }
        return RenderMesh(out, triangles)
    }

    /** A value each of the mesh's vertices holds, [values], at each of the cut's. */
    fun values(values: FloatArray): FloatArray = FloatArray(vertexCount) { i -> values[from[i]] * (1f - t[i]) + values[to[i]] * t[i] }

    private fun normalize(values: FloatArray, at: Int) {
        val x = values[at]
        val y = values[at + 1]
        val z = values[at + 2]
        val length = sqrt(x * x + y * y + z * z)
        if (length > TINY) for (c in 0 until 3) values[at + c] /= length
    }

    companion object {
        private const val TINY = 1e-12f

        /** Where [values] is at least zero on the mesh of [indices], over the triangles [candidates] lists, or every one when null. */
        fun of(values: FloatArray, indices: IntArray, candidates: IntArray? = null): LevelCut {
            val builder = Builder(values)
            if (candidates == null) {
                for (tri in 0 until indices.size / 3) builder.triangle(indices, tri)
            } else {
                for (tri in candidates) builder.triangle(indices, tri)
            }
            return builder.built()
        }
    }

    private class Builder(private val values: FloatArray) {
        private val from = IntList()
        private val to = IntList()
        private val ts = FloatList()
        private val triangles = IntList()

        /** The cut's vertex at each of the mesh's vertices, -1 for none yet, and at each crossed edge by its ends. */
        private val atVertex = IntArray(values.size) { -1 }
        private val atEdge = LongIntMap()
        private val polygon = IntArray(4)
        private val corners = IntArray(3)

        fun triangle(indices: IntArray, tri: Int) {
            val a = indices[tri * 3]
            val b = indices[tri * 3 + 1]
            val c = indices[tri * 3 + 2]
            val inA = values[a] >= 0f
            val inB = values[b] >= 0f
            val inC = values[c] >= 0f
            if (!inA && !inB && !inC) return
            if (inA && inB && inC) {
                triangles.add(vertex(a))
                triangles.add(vertex(b))
                triangles.add(vertex(c))
                return
            }
            var count = 0
            corners[0] = a
            corners[1] = b
            corners[2] = c
            for (k in 0 until 3) {
                val p = corners[k]
                val q = corners[(k + 1) % 3]
                val inP = values[p] >= 0f
                if (inP) polygon[count++] = vertex(p)
                if (inP != (values[q] >= 0f)) polygon[count++] = crossing(p, q)
            }
            for (k in 1 until count - 1) {
                triangles.add(polygon[0])
                triangles.add(polygon[k])
                triangles.add(polygon[k + 1])
            }
        }

        /** The cut's vertex at the mesh's vertex [p], held once. */
        private fun vertex(p: Int): Int {
            val held = atVertex[p]
            if (held >= 0) return held
            return add(p, p, 0f).also { atVertex[p] = it }
        }

        /** The cut's vertex where the values cross zero between [p] and [q], held once. */
        private fun crossing(p: Int, q: Int): Int {
            val low = minOf(p, q)
            val high = maxOf(p, q)
            val key = (low.toLong() shl 32) or high.toLong()
            val held = atEdge.get(key)
            if (held >= 0) return held
            val s = (values[low] / (values[low] - values[high])).coerceIn(0f, 1f)
            return add(low, high, s).also { atEdge.put(key, it) }
        }

        private fun add(low: Int, high: Int, s: Float): Int {
            from.add(low)
            to.add(high)
            ts.add(s)
            return ts.size - 1
        }

        fun built(): LevelCut = LevelCut(from.toArray(), to.toArray(), ts.toArray(), triangles.toArray())
    }
}
