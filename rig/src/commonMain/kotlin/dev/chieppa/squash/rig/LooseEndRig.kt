package dev.chieppa.squash.rig

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.Hanging
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId

/** A garment's loose end as the rig holds it: its chain of [bones] and its [part], weighted along them. */
internal class LooseEndRig(val bones: List<Bone>, val part: RigPart)

/** The joint name of the [k]th bone of a loose end, from 1 at its root. */
fun looseJoint(k: Int): String = "end-$k"

/**
 * The rig of every loose end [character] wears, its bones numbered on after [body]'s: a chain of its bones down the strip from
 * its root, the first hanging from the bone that moves the branch most at the root, turning across the strip so it swings toward and
 * away from the body; each vertex follows the bone whose span holds it, shared with the one before it over the first half of the span.
 * See HLD: data-model, Garments.
 */
internal fun looseEndRigs(character: AssembledCharacter, rigged: List<RigPart>, body: List<Bone>): List<LooseEndRig> {
    var next = body.size
    return character.parts.mapNotNull { placed ->
        val hanging = placed.hanging ?: return@mapNotNull null
        val branch = rigged.firstOrNull { it.id == hanging.branch } ?: return@mapNotNull null
        val carrier = branch.weights.of(hanging.vertex).maxByOrNull { it.second }?.first ?: return@mapNotNull null
        val id = requireNotNull(placed.id)
        val bones = List(hanging.bones) { k ->
            Bone(
                name = BoneName(id, looseJoint(k + 1)),
                parent = if (k == 0) body[carrier].name else BoneName(id, looseJoint(k)),
                pivot = hanging.root + hanging.direction * (hanging.boneLength * k),
                direction = hanging.direction,
                length = hanging.boneLength,
                hinge = hanging.across,
                limits = null,
                stretch = false,
            )
        }
        LooseEndRig(bones, strip(placed, hanging, id, carrier, next)).also { next += hanging.bones }
    }
}

private fun strip(placed: PlacedPart, hanging: Hanging, id: PartId, carrier: Int, first: Int): RigPart {
    val mesh = placed.surface.mesh
    val patch = placed.surface.patches.single()
    val bones = IntArray(mesh.vertexCount * MAX_INFLUENCES) { -1 }
    val weights = FloatArray(bones.size)
    val vertices = ArrayList<SeamVertex>(mesh.vertexCount)
    val anchors = ArrayList<Anchor>(mesh.vertexCount)
    for (vertex in 0 until patch.nu * patch.nv) {
        val point = placed.origin + mesh.position(vertex)
        val along = ((point - hanging.root) dot hanging.direction).coerceIn(0f, hanging.length) / hanging.boneLength
        val k = along.toInt().coerceAtMost(hanging.bones - 1)
        val into = along - k
        val own = first + k
        val before = if (k == 0) carrier else own - 1
        val share = (0.5f + into).coerceAtMost(1f)
        bones[vertex * MAX_INFLUENCES] = own
        weights[vertex * MAX_INFLUENCES] = share
        if (share < 1f) {
            bones[vertex * MAX_INFLUENCES + 1] = before
            weights[vertex * MAX_INFLUENCES + 1] = 1f - share
        }
        val u = patch.ringUs[vertex / patch.nv]
        val v = (vertex % patch.nv).toFloat() / patch.segments
        vertices += SeamVertex(id, patch.name, u, v)
        anchors += Anchor(id, patch.name, u, v)
    }
    val rest = FloatArray(mesh.vertexCount * 3) { i -> (placed.origin + mesh.position(i / 3)).let { p -> if (i % 3 == 0) p.x else if (i % 3 == 1) p.y else p.z } }
    return RigPart(id, rest, mesh.indices, PartWeights(id, bones, weights), mesh, placed.origin, vertices, anchors, null, hanging.direction)
}
