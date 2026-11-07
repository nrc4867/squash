package dev.chieppa.squash.geom

/**
 * This surface as [mesh] shows it, a copy of its own mesh with the same vertices in the same order moved, as a pose moves them: each
 * patch's points and shading normals read from the mesh, so a grid, a crease or an anchor resolved on it lies on the moved surface.
 * Each patch keeps its slices, so u and v land on the same rings, and the surface keeps its silhouettes and extents, which are the
 * part's own at rest. [mesh] being the surface's own mesh gives this surface itself.
 */
fun PartSurface.posed(mesh: RenderMesh): PartSurface {
    if (mesh === this.mesh) return this
    var first = 0
    val moved = patches.map { patch ->
        val count = patch.nu * patch.nv
        val points = FloatArray(count * Patch.COMPONENTS)
        val proxies = patch.proxyNormals?.let { FloatArray(it.size) }
        for (i in 0 until count) {
            val at = (first + i) * RenderMesh.FLOATS_PER_VERTEX
            val point = shown(Vec3(mesh.vertices[at], mesh.vertices[at + 1], mesh.vertices[at + 2]))
            points[i * 3] = point.x
            points[i * 3 + 1] = point.y
            points[i * 3 + 2] = point.z
            if (proxies != null) {
                val proxy = at + RenderMesh.PROXY_OFFSET_FLOATS
                val normal = shown(Vec3(mesh.vertices[proxy], mesh.vertices[proxy + 1], mesh.vertices[proxy + 2]))
                proxies[i * 3] = normal.x
                proxies[i * 3 + 1] = normal.y
                proxies[i * 3 + 2] = normal.z
            }
        }
        first += count
        Patch(patch.name, patch.nu, patch.nv, patch.closedV, patch.slices, points, patch.boneU, proxies)
    }
    return PartSurface(moved, mirrored, mesh, silhouettes, PartSurface.boxOf(mesh), extents)
}
