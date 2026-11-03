package dev.chieppa.squash.render

import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.PatchMesh
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val DISC_SEGMENTS = 48
private const val MIN_NORMAL_LENGTH = 1e-9f

/**
 * A mesh as the GPU reads it: [vertices] holds [FLOATS_PER_VERTEX] floats each, position then normal, and [indices] its
 * triangles. Never changes, so a frame can hand it to the render thread.
 */
class RenderMesh(val vertices: FloatArray, val indices: IntArray) {
    val vertexCount: Int get() = vertices.size / FLOATS_PER_VERTEX

    fun position(vertex: Int): Vec3 {
        val at = vertex * FLOATS_PER_VERTEX
        return Vec3(vertices[at], vertices[at + 1], vertices[at + 2])
    }

    companion object {
        const val FLOATS_PER_VERTEX = 6
        const val NORMAL_OFFSET_FLOATS = 3

        /** Every patch of [mesh] in one buffer, with smooth normals from the triangles around each vertex. */
        fun of(mesh: PartMesh): RenderMesh {
            val vertices = FloatArray(mesh.patches.sumOf { it.vertexCount } * FLOATS_PER_VERTEX)
            val indices = IntArray(mesh.patches.sumOf { it.indices.size })
            var firstVertex = 0
            var firstIndex = 0
            for (patch in mesh.patches) {
                writeVertices(patch, vertices, firstVertex)
                for (i in patch.indices.indices) indices[firstIndex + i] = patch.indices[i] + firstVertex
                firstVertex += patch.vertexCount
                firstIndex += patch.indices.size
            }
            return RenderMesh(vertices, indices)
        }

        /** A disc of radius 1 in the x, y plane facing +z, for a model matrix to place. */
        val UnitDisc: RenderMesh by lazy {
            val rim = FloatArray((DISC_SEGMENTS + 1) * FLOATS_PER_VERTEX)
            for (i in 0 until DISC_SEGMENTS) {
                val angle = 2.0 * PI * i / DISC_SEGMENTS
                val at = (i + 1) * FLOATS_PER_VERTEX
                rim[at] = cos(angle).toFloat()
                rim[at + 1] = sin(angle).toFloat()
                rim[at + 5] = 1f
            }
            rim[5] = 1f
            val triangles = IntArray(DISC_SEGMENTS * 3) { corner ->
                val segment = corner / 3
                when (corner % 3) {
                    0 -> 0
                    1 -> segment + 1
                    else -> (segment + 1) % DISC_SEGMENTS + 1
                }
            }
            RenderMesh(rim, triangles)
        }

        private fun writeVertices(patch: PatchMesh, out: FloatArray, first: Int) {
            val normals = FloatArray(patch.vertexCount * 3)
            for (t in 0 until patch.triangleCount) {
                val a = patch.indices[t * 3]
                val b = patch.indices[t * 3 + 1]
                val c = patch.indices[t * 3 + 2]
                val face = (patch.patch.point(b) - patch.patch.point(a)) cross (patch.patch.point(c) - patch.patch.point(a))
                for (corner in intArrayOf(a, b, c)) {
                    normals[corner * 3] += face.x
                    normals[corner * 3 + 1] += face.y
                    normals[corner * 3 + 2] += face.z
                }
            }
            for (v in 0 until patch.vertexCount) {
                val at = (first + v) * FLOATS_PER_VERTEX
                val point = patch.patch.point(v)
                val normal = Vec3(normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2])
                val unit = if (normal.length < MIN_NORMAL_LENGTH) Vec3.ZERO else normal.normalized()
                out[at] = point.x
                out[at + 1] = point.y
                out[at + 2] = point.z
                out[at + 3] = unit.x
                out[at + 4] = unit.y
                out[at + 5] = unit.z
            }
        }
    }
}
