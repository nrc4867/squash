package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose

/** How far a digit joint with no limits curls at a whole curl, in degrees. */
private const val UNLIMITED_CURL = 90f

/** The bones of [part]'s digits, as places in [Rig.bones], in the order the rig lists them. */
fun Rig.digitBones(part: PartId): List<Int> = bones.indices.filter { bones[it].name.part == part && bones[it].digit != null }

/** How far a whole curl tips [bone]: the farther of its limits, the way a digit closes. */
private fun Bone.fullCurl(): Float = limits?.let { if (-it.min > it.max) it.min else it.max } ?: UNLIMITED_CURL

/**
 * [start] with every digit of [parts] curled by [amount], from 0, every digit straight, to 1, every digit joint tipped to the farther
 * of its limits: one value closing a hand or a foot at once. Each joint keeps its turn, lean and stretch; a locked joint keeps its tip.
 * See HLD: data-model, The rig.
 */
fun Rig.curled(start: Pose, parts: Collection<PartId>, amount: Float): Pose = parts.flatMap(::digitBones).fold(start) { pose, i ->
    val bone = bones[i]
    val set = pose.of(bone.name.part, bone.name.joint) ?: BonePose(bone.name.part, bone.name.joint)
    if (set.mode == JointMode.LOCKED) pose else pose.with(set.copy(degrees = bone.fullCurl() * amount.coerceIn(0f, 1f)))
}

/** How far [part]'s digits stand curled under [pose]: the mean share of each digit joint's whole curl, or null with no digits. */
fun Rig.curlOf(pose: Pose, part: PartId): Float? {
    val digits = digitBones(part).takeIf { it.isNotEmpty() } ?: return null
    return digits.map { i ->
        val bone = bones[i]
        val tip = pose.of(bone.name.part, bone.name.joint)?.degrees ?: 0f
        (tip / bone.fullCurl()).coerceIn(0f, 1f)
    }.average().toFloat()
}

/**
 * [start] with each digit of [parts] spread [degrees] at its root away from the middle of its part's digit roots, leaning free of its
 * hinge's limits, every other joint left as it was: fingers or toes fanned apart.
 */
fun Rig.spread(start: Pose, parts: Collection<PartId>, degrees: Float): Pose = parts.fold(start) { pose, part ->
    val roots = digitBones(part).filter { bones[it].digit?.joint == 1 }
    if (roots.isEmpty()) return@fold pose
    val middle = roots.map { bones[it].pivot }.reduce { a, b -> a + b } * (1f / roots.size)
    roots.fold(pose) { posed, i ->
        val bone = bones[i]
        val away = (bone.axes().y cross bone.direction) dot (bone.pivot - middle)
        val set = posed.of(bone.name.part, bone.name.joint) ?: BonePose(bone.name.part, bone.name.joint)
        if (set.mode == JointMode.LOCKED || kotlin.math.abs(away) < SPREAD_MIDDLE) posed
        else posed.with(set.copy(lean = if (away > 0f) degrees else -degrees, mode = JointMode.FREE))
    }
}

/** How near the middle of its row, in body units across it, a digit's root stands still when the row spreads. */
private const val SPREAD_MIDDLE = 1e-4f
