package dev.chieppa.squash.rig

import dev.chieppa.squash.character.block.Landmark
import dev.chieppa.squash.character.block.Socket
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.JAW_JOINT
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.pointAt
import dev.chieppa.squash.character.mouth.jawJointOf
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3

/**
 * This rig with each head's jaw held to open no wider than [limits] gives it, by part, in degrees, and turning so that a positive angle
 * drops the jaw: its hinge taken the way that carries the head's mouth on [character] down.
 */
fun Rig.withJawLimits(limits: Map<PartId, Float>, character: AssembledCharacter): Rig {
    if (limits.isEmpty()) return this
    val held = bones.map { bone ->
        val limit = limits[bone.name.part]?.takeIf { bone.name.joint == JAW_JOINT } ?: return@map bone
        val part = character.parts.firstOrNull { it.id == bone.name.part }
        val mouth = part?.let(::mouthPointOf)
        val pivot = mouth?.let { hingeOf(part, bone.hinge.normalized(), it) } ?: bone.pivot
        val drops = mouth == null || ((bone.hinge cross (mouth - pivot)) dot BODY_DOWN) >= 0f
        bone.copy(pivot = pivot, limits = Limits(0f, limit), hinge = if (drops) bone.hinge else bone.hinge * -1f)
    }
    return Rig(held, parts, seams, chains)
}

/** How far up from its mouth toward its ear a head's jaw hinges, as a share of the way. */
private const val TOWARD_EAR = 0.1f

/**
 * Where [part]'s jaw hinges at rest in body space, turning about [hinge], with its [mouth] there: as far back as its ear and part of the
 * way up from the mouth toward it, as a jaw hinges at its back below the ear, so its chin swings down and back; null where it has no ear.
 */
private fun hingeOf(part: PlacedPart, hinge: Vec3, mouth: Vec3): Vec3? {
    val id = part.id ?: return null
    val socket = part.template.blocks.filterIsInstance<Socket>().firstOrNull { it.name == EAR_SOCKET } ?: return null
    val ear = part.surface.pointAt(Anchor(id, BODY_PATCH, socket.u, socket.v))?.position?.let { it + part.origin } ?: return null
    val across = (ear - mouth).let { it - hinge * (it dot hinge) }
    val up = BODY_DOWN * (across dot BODY_DOWN)
    return mouth + (across - up) + up * TOWARD_EAR
}

private const val EAR_SOCKET = "ear"

private val BODY_DOWN = Vec3(0f, -1f, 0f)

/** How far back from a head's end, in u, the point its jaw drops is read where its template marks no mouth. */
private const val CHIN_FROM_END = 0.03f

/** Where [part]'s mouth sits at rest in body space: its template's mouth landmark, else its end on its jaw's side. */
private fun mouthPointOf(part: PlacedPart): Vec3? {
    val id = part.id ?: return null
    val jaw = jawJointOf(part.template) ?: return null
    val landmark = part.template.blocks.filterIsInstance<Landmark>().firstOrNull { it.name == MOUTH_LANDMARK }
    val anchor = landmark?.let { Anchor(id, BODY_PATCH, it.u, it.v) } ?: Anchor(id, BODY_PATCH, 1f - CHIN_FROM_END, jaw.v ?: return null)
    return part.surface.pointAt(anchor)?.position?.let { it + part.origin }
}

private const val MOUTH_LANDMARK = "mouth"

/**
 * These weights with each jaw's share of its head handed to the jaw's bone, on a [surface] whose field a posed jaw has slit at its
 * mouth line ([JawShare]): of a vertex's weight on its head's own bones, the share below the line goes to the jaw, so the chin, the
 * lower lip and the pocket's floor follow it and the rest stays with the head. Itself where no jaw slits the surface.
 */
fun SurfaceSkin.withJaws(rig: Rig, surface: CharacterSurface): SurfaceSkin {
    val slits = surface.field.recesses.filter { it.jaw }
    if (slits.isEmpty()) return this
    val bones = bones.copyOf()
    val weights = weights.copyOf()
    val positions = surface.mesh.positions
    var changed = false
    for (recess in slits) {
        val jaw = rig.indexOf(BoneName(recess.part, JAW_JOINT)) ?: continue
        val run = surface.field.runs.firstOrNull { it.partId == recess.part } ?: continue
        val share = JawShare.of(recess, rig.bones[jaw], run.part.origin, run.part.mirrored) ?: continue
        val own = BooleanArray(rig.bones.size) { it != jaw && rig.bones[it].name.part == recess.part }
        for (v in 0 until surface.mesh.vertexCount) {
            val at = v * MAX_INFLUENCES
            var held = 0f
            for (slot in 0 until MAX_INFLUENCES) {
                val bone = bones[at + slot]
                if (bone >= 0 && own[bone]) held += weights[at + slot]
            }
            if (held <= 0f) continue
            val p = Vec3(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2])
            val s = share.at(p)
            if (s <= 0f) continue
            handOver(bones, weights, at, own, jaw, held * s, 1f - s)
            changed = true
        }
    }
    if (!changed) return this
    val corners = surface.mesh.vertexCount
    SurfaceSkin.middleWeights(surface.render.indices, corners, 0, vertexCount - corners, bones, weights)
    return SurfaceSkin(bones, weights, contacts)
}

/** At vertex slot [at], each of [own]'s weights scaled by [keep] and [moved] given to [jaw], the heaviest [MAX_INFLUENCES] kept and summing to one. */
private fun handOver(bones: IntArray, weights: FloatArray, at: Int, own: BooleanArray, jaw: Int, moved: Float, keep: Float) {
    val ids = IntArray(MAX_INFLUENCES + 1) { -1 }
    val amounts = FloatArray(MAX_INFLUENCES + 1)
    var count = 0
    var jawSlot = -1
    for (slot in 0 until MAX_INFLUENCES) {
        val bone = bones[at + slot]
        if (bone < 0) continue
        ids[count] = bone
        amounts[count] = if (own[bone]) weights[at + slot] * keep else weights[at + slot]
        if (bone == jaw) jawSlot = count
        count++
    }
    if (jawSlot >= 0) {
        amounts[jawSlot] += moved
    } else {
        ids[count] = jaw
        amounts[count] = moved
        count++
    }
    val order = (0 until count).sortedByDescending { amounts[it] }.take(MAX_INFLUENCES)
    val total = order.sumOf { amounts[it].toDouble() }.toFloat()
    for (slot in 0 until MAX_INFLUENCES) {
        val i = order.getOrNull(slot)
        bones[at + slot] = if (i == null || total <= 0f) -1 else ids[i]
        weights[at + slot] = if (i == null || total <= 0f) 0f else amounts[i] / total
    }
}
