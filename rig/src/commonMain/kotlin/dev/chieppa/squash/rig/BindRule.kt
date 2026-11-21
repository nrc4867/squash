package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Vec3
import kotlin.math.pow

/** A vertex takes this much of one bone, or more, to count as held by it when the rule asks whether a part spans a joint. */
private const val HELD = 0.6f

/** Each of two bones holds this share of a part's vertices, or more, for the part to span the joint between them. */
private const val SPAN_SHARE = 0.03f

/** A part goes round the limb when its vertices' directions from the bones under them, averaged, are shorter than this. */
private const val WRAP_LENGTH = 0.5f

/** A stiffness at or above this keeps each vertex on its heaviest bone alone. */
private const val RIGID_STIFF = 0.95f

/**
 * Whether [part]'s vertices go round the bones of [body] under them: each vertex's direction from the nearest point on the nearest of
 * those bones, across the bone, averaged over the part, is short, as it is for a band or a shell round a limb and is not for a piece
 * lying on one side of it.
 */
internal fun wraps(part: RigPart, body: List<RigPart>, bones: List<Bone>): Boolean {
    val ids = body.map { it.id }.toSet()
    val spans = bones.filter { it.name.part in ids && it.length > 0f }
    if (spans.isEmpty() || part.vertexCount == 0) return false
    var sum = Vec3.ZERO
    for (v in 0 until part.vertexCount) {
        val p = part.rest.vec(v * 3)
        val bone = spans.minBy { (p - nearestOn(it, p)).length }
        val off = p - nearestOn(bone, p)
        val across = off - bone.direction * (off dot bone.direction)
        if (across.length > 0f) sum += across.normalized()
    }
    return sum.length / part.vertexCount < WRAP_LENGTH
}

private fun nearestOn(bone: Bone, p: Vec3): Vec3 = bone.pivot + bone.direction * ((p - bone.pivot) dot bone.direction).coerceIn(0f, bone.length)

/** Whether two bones each hold at least [SPAN_SHARE] of the vertices of [weights], so the part crosses the joint between them. */
internal fun spans(weights: PartWeights): Boolean {
    val held = HashMap<Int, Int>()
    for (v in 0 until weights.vertexCount) {
        val slot = v * MAX_INFLUENCES
        if (weights.bones[slot] >= 0 && weights.weights[slot] >= HELD) held[weights.bones[slot]] = (held[weights.bones[slot]] ?: 0) + 1
    }
    return held.values.count { it >= SPAN_SHARE * weights.vertexCount } >= 2
}

/** [weights] with each vertex's sharpened by [stiff]: every weight raised to 1 / (1 - stiff) and shared out again, the heaviest alone from [RIGID_STIFF]. */
internal fun stiffened(weights: PartWeights, stiff: Float): PartWeights {
    if (stiff <= 0f) return weights
    val bones = weights.bones.copyOf()
    val out = weights.weights.copyOf()
    val power = 1f / (1f - stiff.coerceAtMost(RIGID_STIFF))
    for (v in 0 until weights.vertexCount) {
        val at = v * MAX_INFLUENCES
        if (stiff >= RIGID_STIFF) {
            for (slot in 1 until MAX_INFLUENCES) {
                bones[at + slot] = -1
                out[at + slot] = 0f
            }
            if (bones[at] >= 0) out[at] = 1f
            continue
        }
        var total = 0f
        for (slot in 0 until MAX_INFLUENCES) if (bones[at + slot] >= 0) {
            out[at + slot] = out[at + slot].pow(power)
            total += out[at + slot]
        }
        if (total > 0f) for (slot in 0 until MAX_INFLUENCES) out[at + slot] /= total
    }
    return PartWeights(weights.part, bones, out)
}
