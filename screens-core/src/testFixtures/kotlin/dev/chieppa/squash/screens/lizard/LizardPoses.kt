package dev.chieppa.squash.screens.lizard

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How far each leg swings at its root in the walk, how far the knee bends as the leg trails and as it reaches, and how far the spine and tail sway. */
private const val STRIDE = 25f
private const val TRAILING_KNEE = 35f
private const val REACHING_KNEE = 10f
private const val SPINE_SWAY = 10f
private const val TAIL_SWAY = 14f

/** How many evenly spaced moments of one stride the walk is looked at in. */
private const val WALK_MOMENTS = 4

/** The legs that move together: the left front with the right back, the other two against them. */
private val LEGS = listOf(FRONT_LEG_L to -1f, BACK_LEG_R to -1f, FRONT_LEG_R to 1f, BACK_LEG_L to 1f)

/** The spine's joints that sway, against each other so the body makes an S. */
private val SPINE = listOf("joint.2" to 1f, "joint.3" to -1f)
private val TAIL_JOINTS = listOf("joint.1", "joint.2")

/** The walk at [phase] of a stride, from 0 to 1: the legs swinging in diagonal pairs, the spine and the tail swaying side to side. */
fun lizardWalk(phase: Float): Pose {
    val swing = cos(2.0 * PI * phase).toFloat()
    val sway = sin(2.0 * PI * phase).toFloat()
    val legs = LEGS.flatMap { (leg: PartId, side) ->
        val root = STRIDE * side * swing
        val trailing = 0.5f + 0.5f * root / STRIDE
        listOf(BonePose(leg, "base", root), BonePose(leg, "joint.1", REACHING_KNEE + (TRAILING_KNEE - REACHING_KNEE) * trailing))
    }
    val spine = SPINE.map { (joint, sign) -> BonePose(LIZARD_BODY, joint, lean = SPINE_SWAY * sign * swing, mode = JointMode.FREE) }
    val tail = TAIL_JOINTS.map { BonePose(TAIL, it, lean = TAIL_SWAY * sway, mode = JointMode.FREE) }
    return Pose(legs + spine + tail)
}

/** The lizard basking: head raised at the neck, the tail's tip lifted and curled to one side. */
private val BASK = Pose(
    listOf(
        BonePose(LIZARD_BODY, "joint.4", -20f),
        BonePose(TAIL, "joint.1", -15f, lean = 20f, mode = JointMode.FREE),
        BonePose(TAIL, "joint.2", -25f, lean = 30f, mode = JointMode.FREE),
    ),
)

/** The lizard's named poses: the walk at evenly spaced moments of a stride, and basking. */
val LIZARD_POSES: Map<String, Pose> = (0 until WALK_MOMENTS).associate { i -> "walk-$i" to lizardWalk(i.toFloat() / WALK_MOMENTS) } + ("bask" to BASK)
