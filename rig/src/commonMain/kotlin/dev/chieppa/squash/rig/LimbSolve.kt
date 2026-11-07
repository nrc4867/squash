package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/** How many rounds of knee then hip a limb takes to land, each holding to the limits the last one met. */
private const val LIMB_ROUNDS = 8

/** How near its target a limb's end counts as there, in body units. */
const val LIMB_TOLERANCE = 1e-3f


/**
 * Two-bone reach: bends [limb]'s knee about its hinge until its end stands as far from the hip as [target] does, the bend the
 * knee's limits allow nearest its own, then turns the hip to carry the end onto [target], over a few rounds so each holds to the
 * limits the other met. With [stretch] up to [maxStretch], a target past the straight limb's reach lengthens the lower bone by its
 * stretching joint. The paw keeps its turn in body space, [foot]. Returns how far the end stays from the target.
 */
internal fun PoseWork.reachWith(limb: Limb, target: Vec3, stretch: Boolean, maxStretch: Float, foot: Mat3?): Float {
    if (stretch) stretchToward(limb, target, maxStretch)
    for (round in 0 until LIMB_ROUNDS) {
        bendKnee(limb, target)
        aimHip(limb, target)
        if ((joint(limb.end) - target).length <= LIMB_TOLERANCE) break
    }
    if (foot != null && limb.foot != null) setWorldTurn(limb.foot, foot)
    return (joint(limb.end) - target).length
}

/**
 * Lengthens [limb]'s lower bone, when its template offers a stretch at its end joint, so the straight limb reaches [target], up to
 * [maxStretch].
 */
private fun PoseWork.stretchToward(limb: Limb, target: Vec3, maxStretch: Float) {
    if (!rig.bones[limb.end].stretch || rig.stretchedBy[limb.lower] != limb.end) return
    val upper = (joint(limb.lower) - joint(limb.upper)).length
    val lower = (joint(limb.end) - joint(limb.lower)).length
    val wanted = (target - joint(limb.upper)).length
    val now = stretch(limb.end)
    if (lower <= 0f || wanted <= upper + lower) return
    setStretch(limb.end, (now * (wanted - upper) / lower).coerceIn(now, maxOf(now, maxStretch)))
}

/** Tips the knee so the end stands from the hip as far as [target] does, or as near as the knee's limits let it. */
private fun PoseWork.bendKnee(limb: Limb, target: Vec3) {
    val hip = joint(limb.upper)
    val knee = joint(limb.lower)
    val a = knee - hip
    val b = joint(limb.end) - knee
    val axis = turn(limb.lower) * rig.bones[limb.lower].hinge
    val along = axis * (axis dot b)
    val across = b - along
    val sideways = axis cross across
    val wanted = (target - hip).length
    val p = a dot across
    val q = a dot sideways
    val k = (wanted * wanted - (a dot a) - (b dot b)) / 2f - (a dot along)
    val r = sqrt(p * p + q * q)
    if (r < 1e-9f) return
    val phase = atan2(q, p)
    val spread = acos((k / r).coerceIn(-1f, 1f))
    val tip = angles(limb.lower)
    val limits = rig.bones[limb.lower].limits
    val choices = listOf(phase + spread, phase - spread).map { tip.tip + wrapped(it) * RADIANS_TO_DEGREES }
    val best = choices.minBy { degrees ->
        val outside = limits?.let { maxOf(it.min - degrees, degrees - it.max, 0f) } ?: 0f
        outside * LIMIT_COST + abs(degrees - tip.tip)
    }
    setAngles(limb.lower, tip.copy(tip = best))
}

/** How much worse a knee angle past its limits is than one as far from the knee's own, per degree. */
private const val LIMIT_COST = 10f

/** Turns the hip the shortest way that carries the end toward [target]. */
private fun PoseWork.aimHip(limb: Limb, target: Vec3) {
    val hip = joint(limb.upper)
    val turn = shareOfTurn(joint(limb.end) - hip, target - hip)
    setWorldTurn(limb.upper, turn * turn(limb.upper))
}

/** Swings [limb] about the line from its hip to its end so its knee goes toward [pole], leaving the end where it is. */
internal fun PoseWork.swingToward(limb: Limb, pole: Vec3, foot: Mat3?) {
    val hip = joint(limb.upper)
    val line = joint(limb.end) - hip
    if (line.length <= 0f) return
    val axis = line.normalized()
    fun across(v: Vec3) = v - axis * (axis dot v)
    val from = across(joint(limb.lower) - hip)
    val to = across(pole - hip)
    if (from.length <= 0f || to.length <= 0f) return
    val angle = atan2((from cross to) dot axis, from dot to)
    setWorldTurn(limb.upper, rotation(axis, angle) * turn(limb.upper))
    if (foot != null && limb.foot != null) setWorldTurn(limb.foot, foot)
}
