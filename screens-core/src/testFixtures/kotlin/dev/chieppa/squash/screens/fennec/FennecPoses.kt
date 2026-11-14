package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import kotlin.math.PI
import kotlin.math.cos

/** How far each leg swings at the hip in the walk, and how far a leg bends at the knee reaching forward and trailing. */
private const val STRIDE = 24f
private const val TRAILING_KNEE = 35f
private const val LEADING_KNEE = 10f
private const val TAIL_LIFT = -15f
private const val HEAD_DIP = 8f

/** How many evenly spaced moments of one stride the walk is looked at in. */
private const val WALK_PHASES = 4

/** Each leg and which way it swings: the left front and right back legs together, the other two against them. */
private val SWINGS = listOf(FRONT_LEG_L to -1f, FRONT_LEG_R to 1f, BACK_LEG_R to -1f, BACK_LEG_L to 1f)

/**
 * The walk at [phase] of a stride, from 0 to 1: each leg swung at the hip by the stride times the cosine of the phase, bending more at
 * the knee as it trails, the tail up and the head a little down. Phase 0 has the left front and right back legs reaching forward.
 */
fun fennecWalk(phase: Float): Pose {
    val swing = cos(2.0 * PI * phase).toFloat()
    val legs = SWINGS.flatMap { (leg: PartId, side) ->
        val hip = STRIDE * side * swing
        listOf(BonePose(leg, "hip", hip), BonePose(leg, "knee", LEADING_KNEE + (TRAILING_KNEE - LEADING_KNEE) * (0.5f + 0.5f * hip / STRIDE)))
    }
    return Pose(legs + listOf(BonePose(TAIL, "root", TAIL_LIFT), BonePose(HEAD, "neck", HEAD_DIP)))
}

/** The fennec's named poses: the walk at evenly spaced moments of one stride. */
val FENNEC_POSES: Map<String, Pose> = (0 until WALK_PHASES).associate { i -> "walk-$i" to fennecWalk(i.toFloat() / WALK_PHASES) }
