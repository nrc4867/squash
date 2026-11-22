package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.surface.SurfaceMesh

/** The cell each vertex of [mesh], meshed from [field], meshes at: that of the member it lies on most (see [CharacterField.cellOf]). */
internal fun vertexCells(field: CharacterField, mesh: SurfaceMesh): FloatArray =
    FloatArray(mesh.vertexCount) { v -> mesh.cell * field.cellOf(mesh.winners[v]) / field.cell }
