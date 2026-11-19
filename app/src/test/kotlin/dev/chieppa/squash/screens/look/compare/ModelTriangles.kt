package dev.chieppa.squash.screens.look.compare

import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.surface.CharacterState

/** Triangles in body space, three floats a point and three indices a triangle: what a silhouette is rastered from. */
class ModelTriangles(val positions: FloatArray, val indices: IntArray) {
    val pointCount: Int get() = positions.size / 3

    fun point(i: Int): Vec3 = Vec3(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2])

    companion object {
        /** What [state] draws: its one surface, the garments it wears as cut, and every part drawn as its own mesh, as posed. */
        fun of(state: CharacterState): ModelTriangles {
            val meshes = mutableListOf<Pair<RenderMesh, Vec3>>()
            meshes += state.surface.render to Vec3(0f, 0f, 0f)
            state.dress?.garments?.forEach { meshes += it.drawn to Vec3(0f, 0f, 0f) }
            val inSurface = state.surface.parts
            state.character.parts.filter { it.id != null && it.id !in inSurface }.forEach { meshes += it.surface.mesh to it.origin }
            val positions = FloatArray(meshes.sumOf { it.first.vertexCount } * 3)
            val indices = IntArray(meshes.sumOf { it.first.indices.size })
            var p = 0
            var t = 0
            for ((mesh, origin) in meshes) {
                val base = p
                for (v in 0 until mesh.vertexCount) {
                    val at = mesh.position(v) + origin
                    positions[p * 3] = at.x
                    positions[p * 3 + 1] = at.y
                    positions[p * 3 + 2] = at.z
                    p++
                }
                for (i in mesh.indices) indices[t++] = base + i
            }
            return ModelTriangles(positions, indices)
        }
    }
}
