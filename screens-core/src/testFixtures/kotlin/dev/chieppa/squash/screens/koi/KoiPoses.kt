package dev.chieppa.squash.screens.koi

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.Pose
import kotlin.math.PI
import kotlin.math.sin

/** How far each body joint sways side to side at the swim's widest, from the head back, and how far the swim's wave lags along it. */
private val SWAY = listOf("joint.3" to 6f, "joint.2" to 12f, "joint.1" to 20f)
private const val LAG = 0.15f
private const val TAIL_SWAY = 25f

/** How many evenly spaced moments of one beat the swim is looked at in. */
private const val SWIM_MOMENTS = 4

/** The swim at [phase] of a beat, from 0 to 1: a wave along the body from the head back to the tail fin. */
fun koiSwim(phase: Float): Pose {
    val body = SWAY.mapIndexed { i, (joint, degrees) ->
        BonePose(KOI_BODY, joint, lean = degrees * sin(2.0 * PI * (phase - LAG * i)).toFloat(), mode = JointMode.FREE)
    }
    val tail = BonePose(TAIL_FIN, "base", lean = TAIL_SWAY * sin(2.0 * PI * (phase - LAG * SWAY.size)).toFloat(), mode = JointMode.FREE)
    return Pose(body + tail)
}

/** The koi rising: its body arched with the head up and the tail down. */
private val RISE = Pose(listOf(BonePose(KOI_BODY, "joint.1", 15f), BonePose(KOI_BODY, "joint.2", 10f), BonePose(KOI_BODY, "joint.3", -15f)))

/** The koi's named poses: the swim at evenly spaced moments of a beat, and rising. */
val KOI_POSES: Map<String, Pose> = (0 until SWIM_MOMENTS).associate { i -> "swim-$i" to koiSwim(i.toFloat() / SWIM_MOMENTS) } + ("rise" to RISE)
