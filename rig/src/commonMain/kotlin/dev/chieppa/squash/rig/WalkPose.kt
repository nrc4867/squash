package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.document.Side
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sign

/** How far each thigh swings forward and back of hanging, in degrees. */
private const val HIP_SWING = 22f

/** How far each knee stays bent at the least, and how much more it bends as its leg swings through. */
private const val KNEE_LEAST = 6f
private const val KNEE_SWING = 40f

/** How far each upper arm swings forward and back of where it rests, in degrees. */
private const val ARM_SWING = 20f

/** How far each elbow stays bent at the least, and how much more it bends as its arm swings forward. */
private const val ELBOW_LEAST = 10f
private const val ELBOW_SWING = 15f

/** A turn this small of a bone's hinge, in degrees, reads which way it swings the bone forward. */
private const val PROBE_DEGREES = 1f

/**
 * One frame of a walk cycle at [phase] (0 to 1, a whole stride) for the [legs] and [arms] of [rig]: each thigh swings forward and back
 * about its hinge, its knee bending most as it passes through, and each arm swings against the leg on its side with its elbow a little
 * bent. Forward is body +x; each limb's first bone swings through its own hinge the way that carries its tip forward, whatever way the
 * template's hinge points, and its second bends the one way its limits allow. The left limbs lead and the right ones run half a
 * stride behind. Joints are free of their limits, so each frame shows what the cycle asks.
 */
fun walkPoseOf(rig: Rig, legs: List<Pair<PartId, Side?>>, arms: List<Pair<PartId, Side?>>, phase: Float): Pose {
    fun angle(side: Side?): Float = (2.0 * PI * (phase + if (side == Side.RIGHT) 0.5f else 0f)).toFloat()
    val bones = legs.flatMap { (leg, side) ->
        val a = angle(side)
        val swing = cos(a).coerceAtLeast(0f)
        limb(rig, leg, HIP_SWING * sin(a), KNEE_LEAST + KNEE_SWING * swing * swing, bendsForward = false)
    } + arms.flatMap { (arm, side) ->
        val a = angle(side)
        limb(rig, arm, -ARM_SWING * sin(a), ELBOW_LEAST + ELBOW_SWING * (-sin(a)).coerceAtLeast(0f), bendsForward = true)
    }
    return Pose(bones)
}

/**
 * The first two bones of [part]'s own chain: the first swung [swing] degrees forward, as [forwardSign] reads forward, and the second
 * bent [bend] degrees the way its limits let it bend, or where they let it bend either way, forward when it [bendsForward] and back when not.
 */
private fun limb(rig: Rig, part: PartId, swing: Float, bend: Float, bendsForward: Boolean): List<BonePose> {
    val chain = rig.bones.filter { it.name.part == part && it.digit == null && it.length > 0f }
    val first = chain.getOrNull(0) ?: return emptyList()
    val swung = BonePose(part, first.name.joint, swing * forwardSign(first), mode = JointMode.FREE)
    val second = chain.getOrNull(1) ?: return listOf(swung)
    val way = bendSign(second) ?: (forwardSign(second) * if (bendsForward) 1f else -1f)
    return listOf(swung, BonePose(part, second.name.joint, bend * way, mode = JointMode.FREE))
}

/** The one way [bone]'s limits let it bend from straight, as a sign; null where they let it bend both ways or it has none. */
internal fun bendSign(bone: Bone): Float? {
    val limits = bone.limits ?: return null
    return when {
        limits.max <= 0f && limits.min < 0f -> -1f
        limits.min >= 0f && limits.max > 0f -> 1f
        else -> null
    }
}

/** Which way a turn about [bone]'s hinge carries its tip toward body +x: 1, -1, or 1 where it carries it neither way. */
internal fun forwardSign(bone: Bone): Float {
    val turned = localTurn(bone, BoneAngles(PROBE_DEGREES, 0f, 0f)) * bone.direction
    val moved = turned.x - bone.direction.x
    return if (moved == 0f) 1f else sign(moved)
}
