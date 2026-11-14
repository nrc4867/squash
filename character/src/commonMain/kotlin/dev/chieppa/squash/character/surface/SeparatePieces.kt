package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.RenderMesh

/**
 * [part]'s own mesh cut to its patches that are not runs, such as a pad laid on it, which stay their own small mesh on a part in the one
 * surface; null when it has none. Its vertices are in the part's surface space, as the part's own mesh's are; taken from [mesh], the
 * part's mesh or the same in a pose.
 */
fun separatePieces(part: PlacedPart, mesh: RenderMesh = part.surface.mesh): RenderMesh? {
    val patches = part.surface.patches
    val keep = BooleanArray(patches.sumOf { it.nu * it.nv })
    var first = 0
    var any = false
    for (patch in patches) {
        val count = patch.nu * patch.nv
        if (!isRun(patch)) {
            for (v in first until first + count) keep[v] = true
            any = true
        }
        first += count
    }
    if (!any) return null
    val renumbered = IntArray(keep.size) { -1 }
    var kept = 0
    for (v in keep.indices) if (keep[v]) renumbered[v] = kept++
    val vertices = FloatArray(kept * RenderMesh.FLOATS_PER_VERTEX)
    for (v in keep.indices) if (keep[v]) {
        mesh.vertices.copyInto(vertices, renumbered[v] * RenderMesh.FLOATS_PER_VERTEX, v * RenderMesh.FLOATS_PER_VERTEX, (v + 1) * RenderMesh.FLOATS_PER_VERTEX)
    }
    val triangles = ArrayList<Int>()
    for (t in 0 until mesh.indices.size / 3) {
        val a = mesh.indices[t * 3]
        val b = mesh.indices[t * 3 + 1]
        val c = mesh.indices[t * 3 + 2]
        if (a < keep.size && b < keep.size && c < keep.size && keep[a] && keep[b] && keep[c]) {
            triangles += renumbered[a]
            triangles += renumbered[b]
            triangles += renumbered[c]
        }
    }
    return RenderMesh(vertices, triangles.toIntArray())
}
