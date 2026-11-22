package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3

/**
 * Writes [refit]'s corners into a posed drawn mesh: each moved corner's place from [positions] into [vertices], its normal and line normal,
 * in [lineNormals], as the posed field's gradient, and its shading normal turned as far; then the middle of each quad of [indices] with a
 * moved corner, past the first [corners] vertices, as the mean of its corners.
 */
internal fun writeRefit(refit: Refit, corners: Int, indices: IntArray, vertices: FloatArray, positions: FloatArray, lineNormals: FloatArray) {
    val width = RenderMesh.FLOATS_PER_VERTEX
    val touched = BooleanArray(corners)
    for ((i, v) in refit.moved.withIndex()) {
        touched[v] = true
        val at = v * width
        vertices.put(at, positions.vec(v * 3))
        val normal = refit.normals[i] ?: continue
        val before = vertices.vec(at + RenderMesh.NORMAL_OFFSET_FLOATS)
        val proxy = vertices.vec(at + RenderMesh.PROXY_OFFSET_FLOATS)
        vertices.put(at + RenderMesh.NORMAL_OFFSET_FLOATS, normal)
        vertices.put(at + RenderMesh.PROXY_OFFSET_FLOATS, (proxy + normal - before).unitOr(normal))
        lineNormals.put(v * 3, normal)
    }
    val count = vertices.size / width
    for (quad in 0 until count - corners) {
        if ((0 until 4).none { touched[indices[(quad * 4 + it) * 3]] }) continue
        var sum = Vec3.ZERO
        var turned = Vec3.ZERO
        for (k in 0 until 4) {
            val corner = indices[(quad * 4 + k) * 3] * width
            sum += vertices.vec(corner)
            turned += vertices.vec(corner + RenderMesh.NORMAL_OFFSET_FLOATS)
        }
        val at = (corners + quad) * width
        vertices.put(at, sum * 0.25f)
        vertices.put(at + RenderMesh.NORMAL_OFFSET_FLOATS, turned.unitOr(vertices.vec(at + RenderMesh.NORMAL_OFFSET_FLOATS)))
    }
}
