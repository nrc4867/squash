package dev.chieppa.squash.rig

import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.min

internal const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()
internal const val RADIANS_TO_DEGREES = (180.0 / PI).toFloat()

/** Below this a direction has no length to make an axis from. */
private const val MIN_AXIS = 1e-6f

/** A bone's three angles in degrees: [tip] about its hinge, [turn] about its own length, [lean] across the hinge. */
data class BoneAngles(val tip: Float, val turn: Float, val lean: Float) {
    companion object {
        val REST = BoneAngles(0f, 0f, 0f)
    }
}

/** The angles [pose] sets, or rest. */
fun BonePose?.angles(): BoneAngles = if (this == null) BoneAngles.REST else BoneAngles(degrees, turn, lean)

/**
 * The bone's own axes as columns, right-handed: its hinge, the axis it leans about, and its length. A mirrored bone takes the
 * lean and length axes the other way, so the same angles turn it as the mirror image of its twin.
 */
fun Bone.axes(): Mat3 {
    val along = direction - hinge * (direction dot hinge)
    val length = if (along.length > MIN_AXIS) along.normalized() else hinge.anyPerpendicular()
    val signed = if (mirrored) length * -1f else length
    return Mat3(hinge, signed cross hinge, signed)
}

private fun Vec3.anyPerpendicular(): Vec3 {
    val other = if (abs(x) < 0.9f) Vec3.X else Vec3.Y
    return (this cross other).normalized()
}

/**
 * How far a joint turns and leans either way: as far as its tip's nearer limit, or not at all for a joint whose limits bend it
 * one way only, as elbows and knees do. A joint with no limits is held by none.
 */
fun Limits.turnReach(): Float = if (min >= 0f || max <= 0f) 0f else min(-min, max)

/** [angles] held to [limits] as [mode] asks: within them unless the bone is free. */
fun held(angles: BoneAngles, limits: Limits?, mode: JointMode): BoneAngles {
    if (limits == null || mode == JointMode.FREE) return angles
    val reach = limits.turnReach()
    return BoneAngles(angles.tip.coerceIn(limits.min, limits.max), angles.turn.coerceIn(-reach, reach), angles.lean.coerceIn(-reach, reach))
}

/** The turn [angles] make of [bone] in its parent's frame: tip about the hinge first, then lean, then turn about its length. */
fun localTurn(bone: Bone, angles: BoneAngles): Mat3 {
    if (angles.turn == 0f && angles.lean == 0f) return rotation(bone.hinge, angles.tip * DEGREES_TO_RADIANS)
    val axes = bone.axes()
    return rotation(axes.z, angles.turn * DEGREES_TO_RADIANS) * rotation(axes.y, angles.lean * DEGREES_TO_RADIANS) * rotation(axes.x, angles.tip * DEGREES_TO_RADIANS)
}

/** The angles whose [localTurn] of [bone] is [turn], a rotation in its parent's frame. */
fun anglesOf(bone: Bone, turn: Mat3): BoneAngles {
    val axes = bone.axes()
    fun m(row: Vec3, column: Vec3) = row dot (turn * column)
    val m20 = m(axes.z, axes.x)
    val lean = asin((-m20).coerceIn(-1f, 1f))
    val tip = atan2(m(axes.z, axes.y), m(axes.z, axes.z))
    val spin = atan2(m(axes.y, axes.x), m(axes.x, axes.x))
    return BoneAngles(tip * RADIANS_TO_DEGREES, spin * RADIANS_TO_DEGREES, lean * RADIANS_TO_DEGREES)
}

/** The rotation's transpose, which undoes it. */
internal val Mat3.transposed: Mat3 get() = Mat3(Vec3(x.x, y.x, z.x), Vec3(x.y, y.y, z.y), Vec3(x.z, y.z, z.z))
