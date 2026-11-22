package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.geom.field.JoinedField

/**
 * What implicit skinning reads of a one surface at rest: the [field] it was meshed from at [cell], its corners' [positions] and
 * [normals], three values a corner, its [triangles], each corner's [winners] and the members holding it most with their shares
 * ([held], [heldShares], see [dev.chieppa.squash.geom.surface.SurfaceMesh]), and for each member the bones of its own run,
 * [ownBones], which its pieces follow.
 */
class SkinSurface(
    val field: JoinedField,
    val cell: Float,
    val positions: FloatArray,
    val normals: FloatArray,
    val triangles: IntArray,
    val winners: IntArray,
    val held: IntArray,
    val heldShares: FloatArray,
    val ownBones: List<IntArray>,
) {
    val vertexCount: Int get() = positions.size / 3

    companion object {
        /** [surface] as [rig] carries it: each run's own bones its digit's for a digit's run, its part's otherwise. */
        fun of(surface: CharacterSurface, rig: Rig): SkinSurface {
            val runs = surface.field.runs
            val digits = rig.bones.mapNotNullTo(HashSet()) { it.digit?.patch }
            val own = runs.map { run ->
                val digit = run.patch.name.takeIf { it in digits }
                rig.bones.indices.filter { b -> rig.bones[b].name.part == run.partId && rig.bones[b].digit?.patch == digit }.toIntArray()
            }
            val mesh = surface.mesh
            return SkinSurface(surface.field.field, surface.field.cell, surface.positions, surface.lineNormals, mesh.triangles, mesh.winners, mesh.held, mesh.heldShares, own)
        }
    }
}
