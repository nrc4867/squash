package dev.chieppa.squash.render

import dev.chieppa.squash.geom.MeshBuilder
import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderMeshTest {
    /** An open tube of radius 1 along +z, from z = 0 to z = 4, with v running the way a swept patch's does. */
    private fun tube(rings: Int = 5, segments: Int = 16): Patch {
        val points = FloatArray(rings * segments * Patch.COMPONENTS)
        var at = 0
        for (ring in 0 until rings) {
            for (segment in 0 until segments) {
                val angle = 2.0 * PI * segment / segments
                points[at++] = cos(angle).toFloat()
                points[at++] = -sin(angle).toFloat()
                points[at++] = 4f * ring / (rings - 1)
            }
        }
        return Patch("tube", rings, segments, closedV = true, slices = null, points = points)
    }

    private fun mesh() = PartMesh("p", listOf(MeshBuilder.build(tube()) { 0 }))

    @Test
    fun normalsPointAwayFromTheAxis() {
        val mesh = RenderMesh.of(mesh())
        assertEquals(5 * 16, mesh.vertexCount)
        for (v in 0 until mesh.vertexCount) {
            val at = v * RenderMesh.FLOATS_PER_VERTEX + RenderMesh.NORMAL_OFFSET_FLOATS
            val normal = Vec3(mesh.vertices[at], mesh.vertices[at + 1], mesh.vertices[at + 2])
            val point = mesh.position(v)
            assertTrue(abs(normal.length - 1f) < 1e-3f, "unit length")
            assertTrue((normal dot Vec3(point.x, point.y, 0f)) > 0f, "normal $normal at $point")
        }
    }

    @Test
    fun patchesShareOneBufferWithTheirIndicesOffset() {
        val one = MeshBuilder.build(tube()) { 0 }
        val merged = RenderMesh.of(PartMesh("p", listOf(one, one)))
        assertEquals(2 * one.vertexCount, merged.vertexCount)
        assertEquals(2 * one.indices.size, merged.indices.size)
        assertEquals(one.indices[0] + one.vertexCount, merged.indices[one.indices.size])
    }

    @Test
    fun theUnitDiscFacesPlusZ() {
        val disc = RenderMesh.UnitDisc
        val a = disc.position(disc.indices[0])
        val b = disc.position(disc.indices[1])
        val c = disc.position(disc.indices[2])
        assertTrue(((b - a) cross (c - a)).z > 0f)
        assertTrue(disc.indices.max() < disc.vertexCount)
    }

    @Test
    fun aTubeSeenFromTheSideHasTwoLongEdgesAndItsOpenEnds() {
        val paths = silhouette(mesh(), toViewer = Vec3(0f, 1f, 0f))
        val points = paths.flatten()
        assertEquals(setOf(0f, 1f, 2f, 3f, 4f), points.filter { abs(it.y) < 1e-3f }.map { it.z }.toSet(), "the edges at y = 0 run the tube's length")
        assertTrue(points.all { it.y >= -1e-3f }, "only the side facing the viewer: $points")
    }

    @Test
    fun edgesJoinIntoOnePathAcrossAnOpenChain() {
        val paths = joinEdges(listOf(edgeKey(2, 3), edgeKey(1, 2), edgeKey(3, 7)))
        assertEquals(1, paths.size)
        assertEquals(setOf(1, 2, 3, 7), paths.single().toSet())
        assertEquals(setOf(1, 7), setOf(paths.single().first(), paths.single().last()), "it runs end to end")
    }

    @Test
    fun aRectangleKeepsOnlyWhatIsOnTheSurface() {
        assertEquals(PixelRect(0, 10, 50, 20), PixelRect(-5, 10, 55, 20).clampedTo(100, 100))
        assertEquals(PixelRect(90, 90, 10, 10), PixelRect(90, 90, 40, 40).clampedTo(100, 100))
        assertTrue(PixelRect(200, 0, 10, 10).clampedTo(100, 100).isEmpty)
    }
}
