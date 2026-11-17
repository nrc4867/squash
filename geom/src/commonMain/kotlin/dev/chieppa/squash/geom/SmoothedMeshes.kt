package dev.chieppa.squash.geom

import kotlin.concurrent.Volatile

/** How many smoothed meshes [SmoothedMeshes] keeps, the most recently used first. */
private const val SMOOTHED_KEPT = 48

/** How many floats of meshes, before and after, [SmoothedMeshes] keeps at most. */
private const val SMOOTHED_FLOATS_KEPT = 6_000_000

/**
 * The meshes [withSmoothedShading] made lately, by what they were made from, for the whole process: a part built the same way again,
 * in another session, on the other side or after an undo, takes its smoothed shading from here. A mesh is never changed once made, so
 * a kept one is handed out as it is.
 */
internal object SmoothedMeshes {
    private class Kept(val from: RenderMesh, val hash: Int, val smoothed: RenderMesh) {
        val floats: Int get() = from.vertices.size + smoothed.vertices.size + from.indices.size
    }

    @Volatile
    private var kept: List<Kept> = emptyList()

    /** [mesh] smoothed: a kept result for a mesh with the same vertices and indices, or else [smooth]'s, then kept. */
    fun of(mesh: RenderMesh, smooth: () -> RenderMesh): RenderMesh {
        val hash = 31 * mesh.vertices.contentHashCode() + mesh.indices.contentHashCode()
        val found = kept.firstOrNull { it.hash == hash && it.from.vertices.contentEquals(mesh.vertices) && it.from.indices.contentEquals(mesh.indices) }
        if (found != null) {
            kept = listOf(found) + kept.filter { it !== found }
            return RenderMesh(found.smoothed.vertices, mesh.indices)
        }
        val smoothed = smooth()
        var floats = 0
        kept = (listOf(Kept(mesh, hash, smoothed)) + kept).take(SMOOTHED_KEPT).takeWhile { floats += it.floats; floats <= SMOOTHED_FLOATS_KEPT }
        return smoothed
    }
}
