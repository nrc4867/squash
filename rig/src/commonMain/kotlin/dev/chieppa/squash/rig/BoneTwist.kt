package dev.chieppa.squash.rig

import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** Below this, in radians, a bone's twist is left whole at its joint. */
private const val MIN_TWIST = 1e-4f

/**
 * The share of a bone's turn that twists it about its own length, spread along its span: a point of its span at [pivot] takes none
 * of it, a point at its far end, [length] along [along], all of it, as a forearm twists from the elbow to the wrist. [axis] is its
 * length as it rests and [radians] the whole twist.
 */
class BoneTwist(val pivot: Vec3, val along: Vec3, val length: Float, val axis: Vec3, val radians: Float) {
    /** How much of the twist [point], at rest, does without: what turns it back from the whole twist to its own share. */
    fun backOf(point: Vec3): Mat3? {
        val share = ((point - pivot) dot along) / length
        if (share >= 1f) return null
        return rotation(axis, (share.coerceAtLeast(0f) - 1f) * radians)
    }
}

/**
 * The twist of [bone] spread along its span when it turns by [own] in its parent's frame, or null when it turns whole: a bone that
 * carries on its part's chain from the one before it spreads the twist [own] makes about its length (its swing and twist split, the
 * twist taken first); a part's first bone, which may carry a rigid part, and a bone with no length turn whole.
 */
internal fun twistOf(bone: Bone, own: Mat3, parent: Bone?): BoneTwist? {
    if (parent == null || parent.name.part != bone.name.part || bone.length <= 0f) return null
    val axis = bone.axes().z
    val radians = twistAbout(own, axis)
    if (abs(radians) < MIN_TWIST) return null
    return BoneTwist(bone.pivot, bone.direction.normalized(), bone.length, axis, radians)
}

/** The angle the rotation [turn] twists about the unit direction [axis], from its quaternion's part along that axis, in -π to π. */
internal fun twistAbout(turn: Mat3, axis: Vec3): Float {
    val (w, v) = quaternionOf(turn)
    var angle = 2f * atan2(v dot axis, w)
    if (angle > PI) angle -= (2 * PI).toFloat()
    if (angle < -PI) angle += (2 * PI).toFloat()
    return angle
}

/** The unit quaternion of the rotation [m], its scalar and its vector part. */
private fun quaternionOf(m: Mat3): Pair<Float, Vec3> {
    val m00 = m.x.x
    val m11 = m.y.y
    val m22 = m.z.z
    val trace = m00 + m11 + m22
    return when {
        trace > 0f -> {
            val s = sqrt(trace + 1f) * 2f
            0.25f * s to Vec3((m.y.z - m.z.y) / s, (m.z.x - m.x.z) / s, (m.x.y - m.y.x) / s)
        }
        m00 > m11 && m00 > m22 -> {
            val s = sqrt(1f + m00 - m11 - m22) * 2f
            (m.y.z - m.z.y) / s to Vec3(0.25f * s, (m.y.x + m.x.y) / s, (m.z.x + m.x.z) / s)
        }
        m11 > m22 -> {
            val s = sqrt(1f + m11 - m00 - m22) * 2f
            (m.z.x - m.x.z) / s to Vec3((m.y.x + m.x.y) / s, 0.25f * s, (m.z.y + m.y.z) / s)
        }
        else -> {
            val s = sqrt(1f + m22 - m00 - m11) * 2f
            (m.x.y - m.y.x) / s to Vec3((m.z.x + m.x.z) / s, (m.z.y + m.y.z) / s, 0.25f * s)
        }
    }
}
