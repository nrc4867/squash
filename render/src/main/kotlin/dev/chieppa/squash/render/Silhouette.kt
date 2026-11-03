package dev.chieppa.squash.render

import dev.chieppa.squash.geom.PartMesh
import dev.chieppa.squash.geom.PatchMesh
import dev.chieppa.squash.geom.Vec3

/**
 * The outline of [mesh] seen along an orthographic view: the edges between a triangle facing [toViewer] and one facing
 * away, and the open edges of triangles facing it, joined into paths of points in the mesh's space. Hidden silhouette
 * edges are included.
 */
fun silhouette(mesh: PartMesh, toViewer: Vec3): List<List<Vec3>> = mesh.patches.flatMap { patchSilhouette(it, toViewer) }

private fun patchSilhouette(mesh: PatchMesh, toViewer: Vec3): List<List<Vec3>> {
    val facing = HashMap<Long, IntArray>()
    for (t in 0 until mesh.triangleCount) {
        val a = mesh.indices[t * 3]
        val b = mesh.indices[t * 3 + 1]
        val c = mesh.indices[t * 3 + 2]
        val normal = (mesh.patch.point(b) - mesh.patch.point(a)) cross (mesh.patch.point(c) - mesh.patch.point(a))
        val slot = if ((normal dot toViewer) > 0f) 0 else 1
        for ((from, to) in listOf(a to b, b to c, c to a)) {
            facing.getOrPut(edgeKey(from, to)) { IntArray(2) }[slot]++
        }
    }
    val edges = facing.filterValues { (front, back) -> front > 0 && (back > 0 || front == 1) }.keys
    return joinEdges(edges).map { path -> path.map(mesh.patch::point) }
}

/** Walks [edges] into paths of vertices; a path ends where it runs out of unused edges. */
internal fun joinEdges(edges: Collection<Long>): List<List<Int>> {
    val around = HashMap<Int, MutableList<Long>>()
    for (edge in edges) {
        around.getOrPut(edgeLow(edge)) { mutableListOf() } += edge
        around.getOrPut(edgeHigh(edge)) { mutableListOf() } += edge
    }
    val unused = edges.toMutableSet()
    val paths = mutableListOf<List<Int>>()
    // Start from the ends of open paths first, so a path is not cut in two.
    val starts = around.entries.sortedBy { (vertex, list) -> if (list.size % 2 == 1) -1 else vertex }.map { it.key }
    for (start in starts) {
        while (around.getValue(start).any { it in unused }) {
            val path = mutableListOf(start)
            var at = start
            while (true) {
                val next = around.getValue(at).firstOrNull { it in unused } ?: break
                unused -= next
                at = if (edgeLow(next) == at) edgeHigh(next) else edgeLow(next)
                path += at
            }
            paths += path
        }
    }
    return paths
}

internal fun edgeKey(a: Int, b: Int): Long = (minOf(a, b).toLong() shl Int.SIZE_BITS) or maxOf(a, b).toLong()

private fun edgeLow(key: Long): Int = (key ushr Int.SIZE_BITS).toInt()

private fun edgeHigh(key: Long): Int = key.toInt()
