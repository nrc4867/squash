package dev.chieppa.squash.geom

import dev.chieppa.squash.geom.collections.FloatList
import dev.chieppa.squash.geom.collections.IntList
import dev.chieppa.squash.geom.collections.LongIntMap

/**
 * Some of a mesh's triangles, [split], each cut into [splits] by [splits] smaller ones on its own plane. The pieces' corners are the mesh's
 * [base] vertices and the extra vertices after them, extra vertex k a mix of the mesh's vertices [a], [b] and [c]: `a (1 - wb - wc) +
 * b wb + c wc`. A point on a side two split triangles share is one vertex of both, so a value cut on the pieces meets itself along it.
 * [pieces] are wound as the mesh's triangles.
 */
class TriangleSplits(
    val base: Int,
    val splits: Int,
    val split: IntArray,
    val a: IntArray,
    val b: IntArray,
    val c: IntArray,
    val wb: FloatArray,
    val wc: FloatArray,
    val pieces: IntArray,
) {
    val extraCount: Int get() = a.size

    /** Whether triangle [tri] of the mesh is one of [split], as a flag a triangle of a mesh of [triangleCount] triangles. */
    fun splitFlags(triangleCount: Int): BooleanArray = BooleanArray(triangleCount).also { flags -> for (tri in split) flags[tri] = true }

    /** [values], one a vertex of the mesh, with each extra vertex's value mixed from its three after them. */
    fun extended(values: FloatArray): FloatArray {
        val out = values.copyOf(base + extraCount)
        for (k in 0 until extraCount) out[base + k] = values[a[k]] * (1f - wb[k] - wc[k]) + values[b[k]] * wb[k] + values[c[k]] * wc[k]
        return out
    }

    companion object {
        /** The triangles [split] of the mesh of [indices], over [base] vertices, each cut into [splits] by [splits] pieces. */
        fun of(indices: IntArray, base: Int, split: IntArray, splits: Int): TriangleSplits {
            require(splits >= 2) { "a split triangle is cut at least in two along each side" }
            val a = IntList()
            val b = IntList()
            val c = IntList()
            val wb = FloatList()
            val wc = FloatList()
            val pieces = IntList()
            val onSide = LongIntMap()
            val step = 1f / splits
            val grid = IntArray((splits + 1) * (splits + 1))
            fun extra(pa: Int, pb: Int, pc: Int, sb: Float, sc: Float): Int {
                a.add(pa)
                b.add(pb)
                c.add(pc)
                wb.add(sb)
                wc.add(sc)
                return base + a.size - 1
            }
            fun sidePoint(p: Int, q: Int, k: Int): Int {
                val low = minOf(p, q)
                val high = maxOf(p, q)
                val along = if (low == p) k else splits - k
                val key = ((low.toLong() shl 32) or high.toLong()) * (splits + 1) + along
                val held = onSide.get(key)
                if (held >= 0) return held
                return extra(low, high, low, along * step, 0f).also { onSide.put(key, it) }
            }
            for (tri in split) {
                val ca = indices[tri * 3]
                val cb = indices[tri * 3 + 1]
                val cc = indices[tri * 3 + 2]
                for (j in 0..splits) for (i in 0..splits - j) {
                    val rest = splits - i - j
                    grid[j * (splits + 1) + i] = when {
                        i == 0 && j == 0 -> ca
                        i == splits -> cb
                        j == splits -> cc
                        j == 0 -> sidePoint(ca, cb, i)
                        i == 0 -> sidePoint(ca, cc, j)
                        rest == 0 -> sidePoint(cb, cc, j)
                        else -> extra(ca, cb, cc, i * step, j * step)
                    }
                }
                for (j in 0 until splits) for (i in 0 until splits - j) {
                    val p00 = grid[j * (splits + 1) + i]
                    val p10 = grid[j * (splits + 1) + i + 1]
                    val p01 = grid[(j + 1) * (splits + 1) + i]
                    pieces.add(p00)
                    pieces.add(p10)
                    pieces.add(p01)
                    if (i + j + 2 <= splits) {
                        pieces.add(p10)
                        pieces.add(grid[(j + 1) * (splits + 1) + i + 1])
                        pieces.add(p01)
                    }
                }
            }
            return TriangleSplits(base, splits, split, a.toArray(), b.toArray(), c.toArray(), wb.toArray(), wc.toArray(), pieces.toArray())
        }
    }
}
