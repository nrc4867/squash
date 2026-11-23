package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose

/** How far a whole retract slides a claw toward its anchor, as a share of the way: as far as a stretch can shorten a span. */
private const val FULL_RETRACT = 1f - MIN_STRETCH

/**
 * The root bones of [part]'s claws, as places in [Rig.bones]: each claw's second joint, which slides toward its hidden anchor as its
 * stretch shortens the span before it.
 */
fun Rig.clawBones(part: PartId): List<Int> = bones.indices.filter { i ->
    val place = bones[i].digit
    bones[i].name.part == part && place?.on != null && place.joint == CLAW_ROOT_JOINT
}

/**
 * The stretch a claw's root joint takes at [amount] retracted, from 1, out, to [MIN_STRETCH], in, the slide growing with the amount's
 * square: a claw's anchor lies further back than the claw is long, so half way shows about half the claw and the last stretch buries it.
 */
fun retractStretch(amount: Float): Float = amount.coerceIn(0f, 1f).let { 1f - FULL_RETRACT * it * it }

/**
 * [start] with every claw of [parts] retracted by [amount], from 0, out, to 1, slid back along its hidden anchor's line into its toe:
 * the stretch of each claw's root joint, not keeping volume, so the claw moves whole. Each joint keeps its turn; a locked joint keeps
 * its stretch. See HLD: parts, Claws.
 */
fun Rig.retracted(start: Pose, parts: Collection<PartId>, amount: Float): Pose = parts.flatMap(::clawBones).fold(start) { pose, i ->
    val bone = bones[i]
    val set = pose.of(bone.name.part, bone.name.joint) ?: BonePose(bone.name.part, bone.name.joint)
    if (set.mode == JointMode.LOCKED) pose else pose.with(set.copy(stretch = retractStretch(amount), keepVolume = false))
}

/** How far [part]'s claws stand retracted under [pose]: the mean share of each claw's whole slide, or null with no claws. */
fun Rig.retractOf(pose: Pose, part: PartId): Float? {
    val claws = clawBones(part).takeIf { it.isNotEmpty() } ?: return null
    return claws.map { i ->
        val bone = bones[i]
        val stretch = pose.of(bone.name.part, bone.name.joint)?.stretch ?: 1f
        kotlin.math.sqrt(((1f - stretch) / FULL_RETRACT).coerceIn(0f, 1f))
    }.average().toFloat()
}

/** The number of a claw's root joint on its chain, after its anchor's. */
private const val CLAW_ROOT_JOINT = 2
