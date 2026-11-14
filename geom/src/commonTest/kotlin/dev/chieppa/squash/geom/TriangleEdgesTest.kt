package dev.chieppa.squash.geom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A closed mesh's edges are found once each, between the two triangles that share them, in batches run in any order. */
class TriangleEdgesTest {
    private val positions = floatArrayOf(1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, -1f)
    private val triangles = intArrayOf(0, 2, 4, 2, 1, 4, 1, 3, 4, 3, 0, 4, 2, 0, 5, 1, 2, 5, 3, 1, 5, 0, 3, 5)

    @Test
    fun everyEdgeOfAClosedMeshLiesBetweenTheTwoTrianglesThatShareIt() {
        val edges = TriangleEdges(positions, positions.size / 3, triangles)
        while (true) (edges.next() ?: break).reversed().forEach { it() }
        assertEquals(12, edges.edgeA.size)
        for (e in edges.edgeA.indices) {
            assertTrue(edges.edgeA[e] < edges.edgeB[e])
            assertTrue(edges.right[e] >= 0)
            assertNotEquals(edges.left[e], edges.right[e])
            assertTrue(edges.left[e] < edges.right[e], "the first triangle to reach an edge lies on its left")
        }
        for (t in 0 until triangles.size / 3) for (c in 0 until 3) {
            val e = edges.triangleEdges[t * 3 + c]
            val ends = setOf(triangles[t * 3 + c], triangles[t * 3 + (c + 1) % 3])
            assertEquals(ends, setOf(edges.edgeA[e], edges.edgeB[e]))
            assertTrue(t == edges.left[e] || t == edges.right[e])
        }
    }

    @Test
    fun aFaceNormalPointsOutOfTheMesh() {
        val edges = TriangleEdges(positions, positions.size / 3, triangles).all()
        val n = Vec3(edges.faceNormals[0], edges.faceNormals[1], edges.faceNormals[2])
        assertEquals(1f, n.length, 1e-5f)
        assertTrue(n dot Vec3(1f, 1f, 1f) > 0f)
    }
}
