package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Triangles split finer, and a level cut over their pieces, which finds a value's zero inside a triangle whose corners all miss it. */
class TriangleSplitsTest {
    /** Two triangles sharing the side from vertex 1 to vertex 2: (0, 0), (1, 0), (0, 1) and (1, 0), (1, 1), (0, 1). */
    private val square = intArrayOf(0, 1, 2, 1, 3, 2)
    private val corners = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f)

    @Test
    fun twoSplitTrianglesShareTheVerticesOnTheirCommonSide() {
        val splits = TriangleSplits.of(square, base = 4, split = intArrayOf(0, 1), splits = 4)
        assertEquals(32, splits.pieces.size / 3)
        // Each triangle has 3 points inside, and each of the 5 sides 3 points along it, the shared side's held once.
        assertEquals(2 * 3 + 5 * 3, splits.extraCount)
        val at = { p: Int -> if (p < splits.base) corners[p] else mixed(splits, p - splits.base) }
        val whole = (0 until 2).sumOf { area(square, it) { p -> corners[p] }.toDouble() }
        val pieces = (0 until splits.pieces.size / 3).map { area(splits.pieces, it, at) }
        assertTrue(pieces.all { it > 0f }, "every piece is wound as the triangles")
        assertEquals(whole.toFloat(), pieces.sum(), 1e-5f)
    }

    @Test
    fun aCutOverSplitTrianglesFindsAStripeNoCornerReaches() {
        // A stripe a tenth wide along x = 0.45 to 0.55: every corner of the square lies outside it.
        val stripe = { x: Float, _: Float -> 0.05f - abs(x - 0.5f) }
        val values = FloatArray(4) { stripe(corners[it].first, corners[it].second) }
        assertTrue(LevelCut.of(values, square).isEmpty)
        val splits = TriangleSplits.of(square, base = 4, split = intArrayOf(0, 1), splits = 4)
        val extended = splits.extended(values)
        for (k in 0 until splits.extraCount) {
            val (x, y) = mixed(splits, k)
            extended[splits.base + k] = stripe(x, y)
        }
        val cut = LevelCut.of(extended, square, splits = splits)
        assertTrue(!cut.isEmpty)
        val positions = FloatArray(4 * RenderMesh.FLOATS_PER_VERTEX).also { p ->
            corners.forEachIndexed { i, (x, y) ->
                p[i * RenderMesh.FLOATS_PER_VERTEX] = x
                p[i * RenderMesh.FLOATS_PER_VERTEX + 1] = y
            }
        }
        val laid = cut.mesh(RenderMesh(positions, square))
        for (i in 0 until cut.vertexCount) {
            val x = laid.vertices[i * RenderMesh.FLOATS_PER_VERTEX]
            assertTrue(abs(x - 0.5f) <= 0.05f + 1e-4f, "each vertex of the cut lies on the stripe, not at $x")
        }
    }

    private fun mixed(splits: TriangleSplits, k: Int): Pair<Float, Float> {
        val wa = 1f - splits.wb[k] - splits.wc[k]
        val a = corners[splits.a[k]]
        val b = corners[splits.b[k]]
        val c = corners[splits.c[k]]
        return (a.first * wa + b.first * splits.wb[k] + c.first * splits.wc[k]) to (a.second * wa + b.second * splits.wb[k] + c.second * splits.wc[k])
    }

    private fun area(indices: IntArray, tri: Int, at: (Int) -> Pair<Float, Float>): Float {
        val (ax, ay) = at(indices[tri * 3])
        val (bx, by) = at(indices[tri * 3 + 1])
        val (cx, cy) = at(indices[tri * 3 + 2])
        return ((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)) / 2f
    }
}
