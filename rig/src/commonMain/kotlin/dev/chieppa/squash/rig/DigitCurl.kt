package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose

/** How far a digit joint with no limits curls at a whole curl, in degrees. */
private const val UNLIMITED_CURL = 90f

/** The bones of [part]'s digits, as places in [Rig.bones], in the order the rig lists them. */
fun Rig.digitBones(part: PartId): List<Int> = bones.indices.filter { bones[it].name.part == part && bones[it].digit != null }

/** How far a whole curl tips [bone]: its upper limit, the way a digit closes. */
private fun Bone.fullCurl(): Float = limits?.max ?: UNLIMITED_CURL

/**
 * [start] with every digit of [parts] curled by [amount], from 0, every digit straight, to 1, every digit joint tipped to its upper
 * limit: one value closing a hand or a foot at once. Each joint keeps its turn, lean and stretch; a locked joint keeps its tip.
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
