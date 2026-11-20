package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.RailPlace
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.covers

/**
 * Every membrane [character] holds as a part of the rig, riding the parts it is spanned between: each vertex takes, from each of its two
 * rails, the bones the part there gives its place along that rail, blended by how far across from the first rail to the second it lies,
 * so the sheet bends, stretches and turns with the limbs it spans and adds no bone of its own. See HLD: surface, Membranes.
 */
internal fun membraneRigs(character: AssembledCharacter, parts: List<RiggedPart>, index: Map<BoneName, Int>, reach: JointReach): List<RigPart> {
    val byId = parts.associateBy { it.id }
    val own = HashMap<PartId, OwnWeights?>()
    fun ownOf(part: RiggedPart): OwnWeights? = own.getOrPut(part.id) { if (part.joints.isEmpty) null else OwnWeights(part, index, reach) }
    fun bonesAt(place: RailPlace): Map<Int, Float> {
        val part = byId[place.part] ?: return emptyMap()
        val weights = ownOf(part) ?: return carrierOf(part, byId)?.let(index::get)?.let { mapOf(it to 1f) }.orEmpty()
        val patch = place.patch ?: part.placed.surface.patches.firstOrNull { it.covers(place.u) }?.name ?: BODY_PATCH
        return weights.at(patch, place.u)
    }
    return character.parts.mapNotNull { placed -> placed.spanning?.let { membraneRig(placed, ::bonesAt) } }
}

private fun membraneRig(placed: PlacedPart, bonesAt: (RailPlace) -> Map<Int, Float>): RigPart? {
    val spanning = requireNotNull(placed.spanning)
    val id = requireNotNull(placed.id)
    val patch = placed.surface.patches.singleOrNull() ?: return null
    val spanned = patch.spanned ?: return null
    val mesh = placed.surface.mesh
    val count = patch.nu * patch.nv
    val bones = IntArray(mesh.vertexCount * MAX_INFLUENCES) { -1 }
    val weights = FloatArray(bones.size)
    val vertices = ArrayList<SeamVertex>(count)
    val anchors = ArrayList<Anchor>(count)
    for (vertex in 0 until count) {
        val share = spanned.shares[vertex]
        val blend = HashMap<Int, Float>()
        bonesAt(spanning.first.at(spanned.firstUs[vertex])).forEach { (bone, w) -> blend[bone] = (blend[bone] ?: 0f) + w * (1f - share) }
        bonesAt(spanning.second.at(spanned.secondUs[vertex])).forEach { (bone, w) -> blend[bone] = (blend[bone] ?: 0f) + w * share }
        val kept = blend.entries.filter { it.value > 0f }.sortedByDescending { it.value }.take(MAX_INFLUENCES)
        val total = kept.sumOf { it.value.toDouble() }.toFloat()
        kept.forEachIndexed { slot, (bone, w) ->
            bones[vertex * MAX_INFLUENCES + slot] = bone
            weights[vertex * MAX_INFLUENCES + slot] = if (total > 0f) w / total else 0f
        }
        val u = spanned.u(vertex)
        val v = (vertex % patch.nv).toFloat() / patch.segments
        vertices += SeamVertex(id, patch.name, u, v)
        anchors += Anchor(id, patch.name, u, v)
    }
    val rest = FloatArray(mesh.vertexCount * 3) { i -> (placed.origin + mesh.position(i / 3)).let { p -> if (i % 3 == 0) p.x else if (i % 3 == 1) p.y else p.z } }
    return RigPart(id, rest, mesh.indices, PartWeights(id, bones, weights), mesh, placed.origin, vertices, anchors)
}
