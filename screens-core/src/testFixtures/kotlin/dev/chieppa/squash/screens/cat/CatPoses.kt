package dev.chieppa.squash.screens.cat

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import kotlin.math.PI
import kotlin.math.cos

/** The tail's joints from root to tip, which the display pose curls up. */
private val TAIL_JOINTS = listOf("root", "tail1", "tail2", "tail3", "tail4", "tail5")
private val TAIL_CURL = listOf(-25f, -15f, -5f, 15f, 30f, 40f)

/** The cat standing with its tail raised and its tip curled over, as a content cat carries it. */
val CAT_DISPLAY = Pose(TAIL_JOINTS.zip(TAIL_CURL).map { (joint, degrees) -> BonePose(CAT_TAIL, joint, degrees) })

/** How far each leg swings at the hip in the walk, and how far the knee bends as the leg trails and as it reaches. */
private const val STRIDE = 22f
private const val TRAILING_KNEE = 30f
private const val REACHING_KNEE = 8f
private const val HEAD_NOD = 5f

/** How many evenly spaced moments of one stride the walk is looked at in. */
private const val WALK_MOMENTS = 4

/** The legs that move together: the left front with the right back, the other two against them. */
private val LEGS = listOf(CAT_FRONT_LEG_L to -1f, CAT_BACK_LEG_R to -1f, CAT_FRONT_LEG_R to 1f, CAT_BACK_LEG_L to 1f)

/** The walk at [phase] of a stride, from 0 to 1, over the display pose: the legs swinging in diagonal pairs and the head nodding. */
fun catWalk(phase: Float): Pose {
    val swing = cos(2.0 * PI * phase).toFloat()
    val legs = LEGS.flatMap { (leg: PartId, side) ->
        val hip = STRIDE * side * swing
        val trailing = 0.5f + 0.5f * hip / STRIDE
        listOf(BonePose(leg, "hip", hip), BonePose(leg, "knee", REACHING_KNEE + (TRAILING_KNEE - REACHING_KNEE) * trailing))
    }
    return Pose(CAT_DISPLAY.bones + legs + BonePose(CAT_HEAD, "neck", HEAD_NOD * swing))
}

/** The cat's named poses: the walk at evenly spaced moments of a stride. */
val CAT_POSES: Map<String, Pose> = (0 until WALK_MOMENTS).associate { i -> "walk-$i" to catWalk(i.toFloat() / WALK_MOMENTS) }
