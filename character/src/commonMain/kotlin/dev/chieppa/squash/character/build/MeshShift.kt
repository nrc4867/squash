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


/**
 * This mesh with the first [nu] by [nv] vertices, a grid of rings closed round, each moved by [shift], and their normals and shading
 * normals turned with the surface: at each grid vertex, by the turn that carries the grid's normal there before the move onto the one
 * after it. The vertices past the grid stay as they are.
 */
internal fun RenderMesh.shiftedOnGrid(shift: Array<Vec3>, nu: Int, nv: Int): RenderMesh {
    val moved = shifted { v -> if (v < shift.size) shift[v] else Vec3.ZERO }
    val out = moved.vertices
    for (i in 0 until nu) for (j in 0 until nv) {
        val v = i * nv + j
        val before = gridNormal(i, j, nu, nv) { position(it) } ?: continue
        val after = moved.gridNormal(i, j, nu, nv) { moved.position(it) } ?: continue
        val axis = before cross after
        val sin = axis.length
        if (sin < MIN_TURN) continue
        val cos = before dot after
        val k = axis * (1f / sin)
        for (offset in intArrayOf(RenderMesh.NORMAL_OFFSET_FLOATS, RenderMesh.PROXY_OFFSET_FLOATS)) {
            val at = v * RenderMesh.FLOATS_PER_VERTEX + offset
            val n = Vec3(out[at], out[at + 1], out[at + 2])
            val turned = n * cos + (k cross n) * sin + k * ((k dot n) * (1f - cos))
            out[at] = turned.x
            out[at + 1] = turned.y
            out[at + 2] = turned.z
        }
    }
    return moved
}

/** How small a turn of a normal is left alone, as the sine of its angle. */
private const val MIN_TURN = 1e-4f

/** The unit normal of a grid of rings closed round at ring [i], column [j], from its neighbours along and round; null where it has none. */
private fun RenderMesh.gridNormal(i: Int, j: Int, nu: Int, nv: Int, at: (Int) -> Vec3): Vec3? {
    val along = at(minOf(i + 1, nu - 1) * nv + j) - at(maxOf(i - 1, 0) * nv + j)
    val round = at(i * nv + (j + 1) % nv) - at(i * nv + (j - 1 + nv) % nv)
    val normal = along cross round
    return if (normal.length < MIN_NORMAL) null else normal.normalized()
}

private const val MIN_NORMAL = 1e-12f
