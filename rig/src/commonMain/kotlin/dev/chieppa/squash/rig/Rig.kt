package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3

/**
 * A part of the rig: its vertices in body space at rest, with the bones each vertex follows, and the [mesh] they come from as
 * the renderer draws it, in the part's own space at [origin].
 */
class RigPart(val id: PartId, val rest: FloatArray, val indices: IntArray, val weights: PartWeights, val mesh: RenderMesh, val origin: Vec3) {
    val vertexCount: Int get() = rest.size / 3
}

/**
 * The character's rig, derived from its assembly: every bone, parents before children, and every part of the document with
 * its weights. A planned part, which the document does not hold, is not rigged. See HLD: data-model, Rig.
 */
class Rig(val bones: List<Bone>, val parts: List<RigPart>) {
    private val byName = bones.withIndex().associate { (i, bone) -> bone.name to i }

    /** Where [name] sits in [bones], or null when the rig has no such bone. */
    fun indexOf(name: BoneName): Int? = byName[name]

    fun bone(name: BoneName): Bone? = indexOf(name)?.let(bones::get)

    fun part(id: PartId): RigPart? = parts.firstOrNull { it.id == id }

    /** Each bone's parent's place in [bones], or -1 for a root. */
    val parents: IntArray = IntArray(bones.size) { i -> bones[i].parent?.let { byName[it] } ?: -1 }

    /**
     * For each bone, the bone whose distance from it a stretch scales, or -1: the next joint of its part when that joint
     * stretches, since a stretching joint moves away from the one before it.
     */
    val stretchedBy: IntArray = IntArray(bones.size) { i ->
        bones.indices.firstOrNull { j -> bones[j].stretch && parents[j] == i && bones[j].name.part == bones[i].name.part } ?: -1
    }

    companion object {
        val EMPTY = Rig(emptyList(), emptyList())
    }
}

/** The rig of [character], with [paint]'s strokes on its seams. [checkCancelled] runs between parts and throws to stop a stale run. */
fun rigOf(character: AssembledCharacter, paint: SeamPaint = SeamPaint.NONE, checkCancelled: () -> Unit = {}): Rig {
    val parts = character.parts.filter { it.id != null }.map(::RiggedPart)
    val bones = boneTree(parts)
    val index = bones.withIndex().associate { (i, bone) -> bone.name to i }
    val byId = parts.associateBy { it.id }
    val children = parts.groupBy { it.placed.socket?.parent }
    val rigged = parts.map { part ->
        checkCancelled()
        val weights = weightsOf(part, carrierOf(part, byId), part.placed.socket?.parent?.let(byId::get), children[part.id].orEmpty(), index, paint, checkCancelled)
        RigPart(part.id, restPositions(part), part.placed.surface.mesh.indices, weights, part.placed.surface.mesh, part.placed.origin)
    }
    return Rig(bones, rigged)
}

private fun restPositions(part: RiggedPart): FloatArray {
    val mesh = part.placed.surface.mesh
    val origin = part.placed.origin
    return FloatArray(mesh.vertexCount * 3) { i ->
        val at = (i / 3) * RenderMesh.FLOATS_PER_VERTEX + i % 3
        mesh.vertices[at] + when (i % 3) {
            0 -> origin.x
            1 -> origin.y
            else -> origin.z
        }
    }
}
