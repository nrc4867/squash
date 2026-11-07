package dev.chieppa.squash.geom

/**
 * This cut laid on [grid] again: each vertex written where its place on the patch lies on [grid]'s mesh, with every attribute as
 * that mesh's triangle has it. On a grid over a posed copy of the mesh the cut was made on, it lies exactly on the posed surface.
 */
fun PatchCut.laidOn(grid: PatchGrid): RenderMesh {
    val out = FloatArray(us.size * RenderMesh.FLOATS_PER_VERTEX)
    for (i in us.indices) grid.writeVertex(grid.gridU(us[i].toDouble()), grid.gridV(vs[i].toDouble()), out, i * RenderMesh.FLOATS_PER_VERTEX)
    return RenderMesh(out, mesh.indices)
}

/** This surface showing [mesh], a copy of its own mesh with the same vertices in the same order moved, as a pose moves them. */
fun PartSurface.showing(mesh: RenderMesh): PartSurface = PartSurface(patches, mirrored, mesh, silhouettes, box, extents)
