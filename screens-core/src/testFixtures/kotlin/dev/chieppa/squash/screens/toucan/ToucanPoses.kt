package dev.chieppa.squash.screens.toucan

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import kotlin.math.PI
import kotlin.math.cos

/** How far each leg swings at the hip in the walk, how far the heel folds as the leg trails and as it reaches, and how far the head bobs and the tail tips. */
private const val STRIDE = 20f
private const val TRAILING_HEEL = -30f
private const val REACHING_HEEL = -8f
private const val HEAD_BOB = 8f
private const val TAIL_TIP = 8f

/** How many evenly spaced moments of one stride the walk is looked at in. */
private const val WALK_MOMENTS = 4

/** The legs and which way each swings: one against the other. */
private val LEGS = listOf(LEG_L to -1f, LEG_R to 1f)

/** The walk at [phase] of a stride, from 0 to 1: the legs swinging one against the other, folding at the heel as they trail, the head bobbing and the tail tipping against it. */
fun toucanWalk(phase: Float): Pose {
    val swing = cos(2.0 * PI * phase).toFloat()
    val legs = LEGS.flatMap { (leg: PartId, side) ->
        val hip = STRIDE * side * swing
        val trailing = 0.5f + 0.5f * hip / STRIDE
        listOf(BonePose(leg, "hip", hip), BonePose(leg, "heel", REACHING_HEEL + (TRAILING_HEEL - REACHING_HEEL) * trailing))
    }
    val bob = HEAD_BOB * cos(4.0 * PI * phase).toFloat()
    return Pose(legs + BonePose(HEAD, "neck", bob) + BonePose(TAIL, "root", -TAIL_TIP * swing))
}

/** The toucan calling: head thrown back with the beak open, the tail cocked up and the wings eased from the flanks. */
private val CALL = Pose(
    listOf(
        BonePose(HEAD, "neck", -30f),
        BonePose(BEAK_LOWER, "jaw", 25f),
        BonePose(TAIL, "root", -35f),
        BonePose(WING_L, "shoulder", 15f),
        BonePose(WING_R, "shoulder", 15f),
    ),
)

/** The toucan's named poses: the walk at evenly spaced moments of a stride, and calling. */
val TOUCAN_POSES: Map<String, Pose> = (0 until WALK_MOMENTS).associate { i -> "walk-$i" to toucanWalk(i.toFloat() / WALK_MOMENTS) } + ("call" to CALL)
