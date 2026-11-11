package dev.chieppa.squash.character.build

import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3

/** This mesh with each vertex's position moved by [by], its normals as they were. */
internal fun RenderMesh.shifted(by: (Int) -> Vec3): RenderMesh {
    val moved = vertices.copyOf()
    for (v in 0 until vertexCount) {
        val step = by(v)
        if (step == Vec3.ZERO) continue
        val at = v * RenderMesh.FLOATS_PER_VERTEX
        moved[at] += step.x
        moved[at + 1] += step.y
        moved[at + 2] += step.z
    }
    return RenderMesh(moved, indices)
}

