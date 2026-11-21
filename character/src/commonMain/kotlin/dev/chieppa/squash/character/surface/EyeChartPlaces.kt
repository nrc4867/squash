package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3

/** Each drawn vertex of [candidates]' chart place, ([u], [v]) per unit of its offset from [middle] along [toU] and [toV]; zero elsewhere. */
internal fun eyeChartAt(surface: CharacterSurface, candidates: IntArray, middle: Vec3, toU: Vec3, toV: Vec3): Pair<FloatArray, FloatArray> {
    val render = surface.render
    val u = FloatArray(render.vertexCount)
    val v = FloatArray(render.vertexCount)
    val indices = render.indices
    val width = RenderMesh.FLOATS_PER_VERTEX
    for (tri in candidates) for (c in 0 until 3) {
        val at = indices[tri * 3 + c]
        val x = render.vertices[at * width] - middle.x
        val y = render.vertices[at * width + 1] - middle.y
        val z = render.vertices[at * width + 2] - middle.z
        u[at] = x * toU.x + y * toU.y + z * toU.z
        v[at] = x * toV.x + y * toV.y + z * toV.z
    }
    return u to v
}
