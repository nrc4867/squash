package dev.chieppa.squash.geom.surface

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** An edge four triangles share, where a sheet thinner than a cell pinches, splits into two edges of two triangles each. */
class PinchSplitTest {
    /** Two closed tetrahedra sharing their edge 0 to 1 and nothing else, vertices 0 to 5. */
    private val pinched = intArrayOf(
        0, 1, 2, 0, 2, 3, 0, 3, 1, 1, 3, 2,
        1, 0, 4, 0, 5, 4, 0, 1, 5, 1, 4, 5,
    )

    private fun meshOf(triangles: IntArray, count: Int) = SurfaceMesh(
        1f, FloatArray(count * 3) { it.toFloat() }, FloatArray(count * 3), triangles, IntArray(count) { it }, IntArray(count) { -1 },
        FloatArray(count), FloatArray(count), FloatArray(count), IntArray(count * HELD) { -1 }, FloatArray(count * HELD), FloatArray(count * HELD),
        FloatArray(count * HELD), FloatArray(count * HELD), 1, quadCount = 0,
    )

    private fun edgeCounts(mesh: SurfaceMesh): Map<Pair<Int, Int>, Int> {
        val counts = HashMap<Pair<Int, Int>, Int>()
        for (t in 0 until mesh.triangleCount) for (c in 0 until 3) {
            val a = mesh.triangles[t * 3 + c]
            val b = mesh.triangles[t * 3 + (c + 1) % 3]
            counts.merge(minOf(a, b) to maxOf(a, b), 1, Int::plus)
        }
        return counts
    }

    @Test
    fun anEdgeFourTrianglesShareSplitsIntoTwoEdgesOfTwo() {
        val split = pinchSplit(meshOf(pinched, 6))
        assertEquals(8, split.vertexCount, "each end of the pinched edge gets a copy")
        assertEquals(setOf(2), edgeCounts(split).values.toSet(), "every edge lies on two triangles")
        for (copy in 6 until 8) {
            val source = if (split.winners[copy] == 0) 0 else 1
            assertEquals(source, split.winners[copy], "a copy holds what its vertex holds")
            assertContentEquals(split.positions.copyOfRange(source * 3, source * 3 + 3), split.positions.copyOfRange(copy * 3, copy * 3 + 3))
        }
    }

    @Test
    fun aManifoldMeshIsLeftAsItIs() {
        val one = meshOf(pinched.copyOf(12), 4)
        assertSame(one, pinchSplit(one))
    }
}
