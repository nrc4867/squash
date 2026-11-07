package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3

/**
 * A part of the rig: its vertices in body space at rest, with the bones each vertex follows, each one's address as the bands
 * weigh it in [vertices] and the anchor landing on it in [anchors], and the [mesh] they come from as the renderer draws it, in
 * the part's own space at [origin]. Its [role] is what the pose's handles read: a leg or an arm is a limb a pose reaches with.
 */
class RigPart(
    val id: PartId,
    val rest: FloatArray,
    val indices: IntArray,
    val weights: PartWeights,
    val mesh: RenderMesh,
    val origin: Vec3,
    val vertices: List<SeamVertex> = emptyList(),
    val anchors: List<Anchor> = emptyList(),
    val role: Role? = null,
) {
    val vertexCount: Int get() = rest.size / 3
}

/**
 * Where [child] meets [parent]: the parent's bone [carrier] carrying the socket and the child's [first] bone, as places in
 * [Rig.bones], with the socket's [anchor] in body space and how far its band reaches round it on the parent, [radius].
 */
class RigSeam(val child: PartId, val parent: PartId, val carrier: Int, val first: Int, val anchor: Vec3, val radius: Float) {
    val seam: Seam get() = Seam(child)
}

/**
 * The character's rig, derived from its assembly: every bone, parents before children, and every part of the document with
 * its weights. A planned part, which the document does not hold, is not rigged. See HLD: data-model, Rig.
 */
class Rig(val bones: List<Bone>, val parts: List<RigPart>, val seams: List<RigSeam> = emptyList()) {
    private val byName = bones.withIndex().associate { (i, bone) -> bone.name to i }

    /** Where [name] sits in [bones], or null when the rig has no such bone. */
    fun indexOf(name: BoneName): Int? = byName[name]

    fun bone(name: BoneName): Bone? = indexOf(name)?.let(bones::get)

    fun part(id: PartId): RigPart? = parts.firstOrNull { it.id == id }

    /** The seam where [child] meets its parent, or null when it sits on nothing rigged. */
    fun seam(child: PartId): RigSeam? = seams.firstOrNull { it.child == child }

    /** Each bone's parent's place in [bones], or -1 for a root. */
    val parents: IntArray = IntArray(bones.size) { i -> bones[i].parent?.let { byName[it] } ?: -1 }

    /**
     * For each bone, the bone whose distance from it a stretch scales, or -1: the next joint of its part, since a stretched
     * joint moves away from the one before it.
     */
    val stretchedBy: IntArray = IntArray(bones.size) { i ->
        bones.indices.firstOrNull { j -> parents[j] == i && bones[j].name.part == bones[i].name.part } ?: -1
    }

    companion object {
        val EMPTY = Rig(emptyList(), emptyList())
    }
}

/**
 * The rig of [character], with [paint]'s strokes on its seams and each joint's blend as wide as [reach] sets. [checkCancelled]
 * runs between parts and throws to stop a stale run.
 */
fun rigOf(character: AssembledCharacter, paint: SeamPaint = SeamPaint.NONE, checkCancelled: () -> Unit = {}, reach: JointReach = JointReach.GUESSED): Rig {
    val parts = character.parts.filter { it.id != null }.map(::RiggedPart)
    val bones = boneTree(parts)
    val index = bones.withIndex().associate { (i, bone) -> bone.name to i }
    val byId = parts.associateBy { it.id }
    val children = parts.groupBy { it.placed.socket?.parent }
    val rigged = parts.map { part ->
        checkCancelled()
        val vertices = PartVertices(part)
        val parent = part.placed.socket?.parent?.let(byId::get)
        val weights = weightsOf(part, carrierOf(part, byId), parent, children[part.id].orEmpty(), index, paint, checkCancelled, reach, vertices)
        RigPart(
            part.id, restPositions(part), part.placed.surface.mesh.indices, weights, part.placed.surface.mesh, part.placed.origin,
            vertices.addresses, vertices.anchors, part.placed.role,
        )
    }
    return Rig(bones, rigged, seamsOf(parts, byId, index, reach))
}

private fun seamsOf(parts: List<RiggedPart>, byId: Map<PartId, RiggedPart>, index: Map<BoneName, Int>, reach: JointReach): List<RigSeam> = parts.mapNotNull { part ->
    val parent = part.placed.socket?.parent?.let(byId::get) ?: return@mapNotNull null
    if (part.joints.isEmpty) return@mapNotNull null
    val carrier = carrierOf(part, byId)?.let(index::get) ?: return@mapNotNull null
    val anchor = part.placed.anchor ?: return@mapNotNull null
    val radius = seamRadius(part, parent) * reach.of(part.id, part.joints.specs[0].name)
    RigSeam(part.id, parent.id, carrier, index.getValue(part.bone(0)), anchor, radius)
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
