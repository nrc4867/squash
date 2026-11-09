package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.Anchor

/**
 * A garment's branch as a part of the rig, every vertex weighted as the vertex under it of [bodyPart], the part it was built over,
 * rigged as [body]: on the ring of the body's patch nearest in u, at the same place round it. It carries no bones of its own. See
 * HLD: data-model, Garments.
 */
internal fun branchRigPart(branch: PlacedPart, bodyPart: PlacedPart, body: RigPart): RigPart {
    val covering = requireNotNull(branch.covering) { "Part ${branch.id?.value} is no garment's branch" }
    val id = requireNotNull(branch.id)
    val patches = bodyPart.surface.patches
    val start = patches.take(covering.bodyPatch).sumOf { it.nu * it.nv }
    val around = patches[covering.bodyPatch].nv
    val mesh = branch.surface.mesh
    val bones = IntArray(mesh.vertexCount * MAX_INFLUENCES)
    val weights = FloatArray(bones.size)
    val vertices = ArrayList<SeamVertex>(mesh.vertexCount)
    val anchors = ArrayList<Anchor>(mesh.vertexCount)
    var vertex = 0
    for (patch in branch.surface.patches) for (local in 0 until patch.nu * patch.nv) {
        val ring = local / patch.nv
        val column = local % patch.nv
        val under = start + covering.bodyRings[ring] * around + column.coerceAtMost(around - 1)
        for (slot in 0 until MAX_INFLUENCES) {
            bones[vertex * MAX_INFLUENCES + slot] = body.weights.bones[under * MAX_INFLUENCES + slot]
            weights[vertex * MAX_INFLUENCES + slot] = body.weights.weights[under * MAX_INFLUENCES + slot]
        }
        val v = column.toFloat() / patch.segments
        vertices += SeamVertex(id, patch.name, patch.ringUs[ring], v)
        anchors += Anchor(id, patch.name, patch.ringUs[ring], v)
        vertex++
    }
    return RigPart(id, restOf(branch), mesh.indices, PartWeights(id, bones, weights), mesh, branch.origin, vertices, anchors, null, body.axis)
}

private fun restOf(part: PlacedPart): FloatArray {
    val mesh = part.surface.mesh
    val origin = part.origin
    return FloatArray(mesh.vertexCount * 3) { i ->
        val p = mesh.position(i / 3)
        when (i % 3) {
            0 -> p.x + origin.x
            1 -> p.y + origin.y
            else -> p.z + origin.z
        }
    }
}
