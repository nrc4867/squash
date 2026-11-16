package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.SurfaceGarment
import dev.chieppa.squash.geom.hemFlare
import dev.chieppa.squash.geom.surface.HELD

/**
 * [garment]'s vertices, posed at [positions], belled out of the vertices of the posed [body] in [inside] as cloth hanging under its own
 * weight is (see [hemFlare]): the vertices that hang are those held most by a branch hanging free past an end of its part that points
 * down (see [dev.chieppa.squash.character.surface.WornCover.hangsFree]), never a branch running on along a body lying level. Null when
 * nothing moves.
 */
fun flaredHem(garment: SurfaceGarment, positions: FloatArray, body: CharacterSurface, inside: IntArray, clear: Float): FloatArray? {
    val mesh = garment.surface.mesh
    val runs = garment.surface.field.runs
    val hangs = BooleanArray(mesh.vertexCount)
    for (v in 0 until mesh.vertexCount) for (k in 0 until HELD) {
        val member = mesh.held[v * HELD + k]
        if (member < 0 || mesh.heldShares[v * HELD + k] < 0.5f || !runs[member].first) continue
        if (runs[member].partId in garment.cover.hangsFree) hangs[v] = true
    }
    val points = FloatArray(inside.size * 3) { body.positions[inside[it / 3] * 3 + it % 3] }
    return hemFlare(positions, hangs, mesh.cell, points, clear)
}
