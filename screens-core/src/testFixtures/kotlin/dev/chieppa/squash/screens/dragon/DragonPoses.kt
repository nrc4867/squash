package dev.chieppa.squash.screens.dragon

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How far each leg swings at the hip in the walk, how far the knee bends as the leg trails and as it reaches, and how far the tail sways and the head nods. */
private const val STRIDE = 22f
private const val TRAILING_KNEE = 35f
private const val REACHING_KNEE = 10f
private const val TAIL_SWAY = 8f
private const val HEAD_NOD = 6f

/** How many evenly spaced moments of one stride the walk is looked at in. */
private const val WALK_MOMENTS = 4

/** The legs that move together: the left front with the right back, the other two against them. */
private val LEGS = listOf(FRONT_LEG_L to -1f, BACK_LEG_R to -1f, FRONT_LEG_R to 1f, BACK_LEG_L to 1f)

/** The tail's joints that sway in the walk, from its root out. */
private val TAIL_SWAYING = listOf("tail1", "tail2", "tail3", "tail4")

/** The walk at [phase] of a stride, from 0 to 1: the legs swinging in diagonal pairs, the tail swaying side to side and the head nodding. */
fun dragonWalk(phase: Float): Pose {
    val swing = cos(2.0 * PI * phase).toFloat()
    val sway = sin(2.0 * PI * phase).toFloat()
    val legs = LEGS.flatMap { (leg: PartId, side) ->
        val hip = STRIDE * side * swing
        val trailing = 0.5f + 0.5f * hip / STRIDE
        listOf(BonePose(leg, "hip", hip), BonePose(leg, "knee", REACHING_KNEE + (TRAILING_KNEE - REACHING_KNEE) * trailing))
    }
    val tail = TAIL_SWAYING.map { BonePose(TAIL, it, lean = TAIL_SWAY * sway, mode = JointMode.FREE) }
    return Pose(legs + tail + BonePose(HEAD, "neck", HEAD_NOD * swing))
}

/** The dragon roaring: head thrown up with the jaw open, wings raised high, the tail lifted and curling to one side. */
private val ROAR = Pose(
    listOf(
        BonePose(HEAD, "neck", -30f),
        BonePose(HEAD, "jaw", 30f),
        BonePose(WING_L, "shoulder", -35f),
        BonePose(WING_R, "shoulder", -35f),
        BonePose(TAIL, "root", -20f),
        BonePose(TAIL, "tail2", 10f, lean = 20f, mode = JointMode.FREE),
        BonePose(TAIL, "tail4", 10f, lean = 25f, mode = JointMode.FREE),
    ),
)

/** The dragon's named poses: the walk at evenly spaced moments of a stride, and roaring. */
val DRAGON_POSES: Map<String, Pose> = (0 until WALK_MOMENTS).associate { i -> "walk-$i" to dragonWalk(i.toFloat() / WALK_MOMENTS) } + ("roar" to ROAR)
