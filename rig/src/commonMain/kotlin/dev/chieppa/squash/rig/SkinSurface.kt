package dev.chieppa.squash.rig

import dev.chieppa.squash.character.surface.CharacterField
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.JAW_JOINT
import dev.chieppa.squash.character.surface.JawSide
import dev.chieppa.squash.character.surface.OpenJaw
import dev.chieppa.squash.geom.field.JoinedField
import dev.chieppa.squash.geom.surface.HELD

/**
 * What implicit skinning reads of a one surface at rest: the [field] it was meshed from at [cell], its corners' [positions] and
 * [normals], three values a corner, its [triangles], each corner's [winners] and the members holding it most with their shares
 * ([held], [heldShares], see [dev.chieppa.squash.geom.surface.SurfaceMesh]), and for each member the bones of its own run,
 * [ownBones], which its pieces follow; for a mouth's lining, the head built with its jaw open it lines, in [linings]; and for the corners
 * of a head built with its jaw open, the jaw's bone, in [jaws], -1 elsewhere.
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
    val linings: List<OpenJaw?> = emptyList(),
    val jaws: IntArray = IntArray(0),
) {
    val vertexCount: Int get() = positions.size / 3

    companion object {
        /**
         * [surface] as [rig] carries it: each run's own bones its digit's for a digit's run, its part's otherwise; on a head built with its
         * jaw open, the jaw's half follows the jaw alone, the snout's every bone of the head but the jaw, and the lining the jaw and the
         * bone it hangs from; read in [field], the surface's own unless another of the same character is given.
         */
        fun of(surface: CharacterSurface, rig: Rig, field: CharacterField = surface.field): SkinSurface {
            val runs = field.runs
            val digits = rig.bones.mapNotNullTo(HashSet()) { it.digit?.patch }
            val own = runs.map { run ->
                val digit = run.patch.name.takeIf { it in digits }
                val bones = rig.bones.indices.filter { b -> rig.bones[b].name.part == run.partId && rig.bones[b].digit?.patch == digit }
                val jaw = if (run.jawSide == null) null else rig.indexOf(BoneName(run.partId, JAW_JOINT))
                when {
                    jaw == null -> bones
                    run.jawSide == JawSide.JAW -> listOf(jaw)
                    run.jawSide == JawSide.LINING -> listOf(rig.parents[jaw], jaw).filter { it >= 0 }
                    else -> bones - jaw
                }.toIntArray()
            }
            val linings = runs.map { run -> if (run.jawSide == JawSide.LINING) field.openJawOf(run.partId) else null }
            val mesh = surface.mesh
            val jaws = IntArray(mesh.vertexCount) { -1 }
            for (jaw in field.openJaws) {
                val bone = rig.indexOf(BoneName(jaw.part, JAW_JOINT)) ?: continue
                val split = runs.indices.filter { runs[it].partId == jaw.part && runs[it].jawSide != null }.toSet()
                for (v in 0 until mesh.vertexCount) if ((0 until HELD).any { k -> mesh.held[v * HELD + k] in split }) jaws[v] = bone
            }
            return SkinSurface(field.field, field.cell, surface.positions, surface.lineNormals, mesh.triangles, mesh.winners, mesh.held, mesh.heldShares, own, linings, jaws)
        }
    }
}
