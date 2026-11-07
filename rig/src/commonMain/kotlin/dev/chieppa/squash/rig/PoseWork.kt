package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3
import kotlin.math.atan2

/** Below this two directions are taken as one, and a turn between them as none. */
private const val SAME_DIRECTION = 1e-7f

/**
 * A pose being solved on [rig]: the pose so far, and each bone's place and turn under it, worked out again whenever the pose
 * changes. A locked bone is never changed, and every angle set is held to its limits unless its bone is free.
 */
internal class PoseWork(val rig: Rig, start: Pose) {
    var pose: Pose = start
        set(value) {
            field = value
            known = null
        }
    private var known: BoneFrames? = null
    val frames: BoneFrames get() = known ?: boneFrames(rig, pose).also { known = it }

    /** Where the joint of bone [i] stands now. */
    fun joint(i: Int): Vec3 = frames.transforms[i](rig.bones[i].pivot)

    /** The turn bone [i] has in body space now. */
    fun turn(i: Int): Mat3 = frames.turns[i]

    private fun parentTurn(i: Int): Mat3 = rig.parents[i].let { if (it >= 0) frames.turns[it] else IDENTITY }

    fun set(i: Int): BonePose? = rig.bones[i].name.let { pose.of(it.part, it.joint) }

    fun locked(i: Int): Boolean = set(i)?.mode == JointMode.LOCKED

    /** Sets bone [i]'s angles, held to its limits as its mode asks; a locked bone keeps its own. */
    fun setAngles(i: Int, angles: BoneAngles) {
        if (locked(i)) return
        val bone = rig.bones[i]
        val current = set(i) ?: BonePose(bone.name.part, bone.name.joint)
        val kept = held(angles, bone.limits, current.mode)
        pose = pose.with(current.copy(degrees = kept.tip, turn = kept.turn, lean = kept.lean))
    }

    /** Turns bone [i] so its turn in body space is [world], as near as its limits let it. */
    fun setWorldTurn(i: Int, world: Mat3) {
        if (locked(i)) return
        setAngles(i, anglesOf(rig.bones[i], parentTurn(i).transposed * world))
    }

    /** Sets the stretch of the joint [i]; a locked joint keeps its own. */
    fun setStretch(i: Int, stretch: Float) {
        if (locked(i)) return
        val bone = rig.bones[i]
        val current = set(i) ?: BonePose(bone.name.part, bone.name.joint)
        pose = pose.with(current.copy(stretch = stretch))
    }

    fun angles(i: Int): BoneAngles = set(i).angles()

    fun stretch(i: Int): Float = set(i)?.stretch ?: 1f

    companion object {
        val IDENTITY = Mat3(Vec3.X, Vec3.Y, Vec3.Z)
    }
}

/** The turn taking direction [from] onto direction [to] the shortest way, as an axis and an angle in radians, or null when they agree. */
internal fun turnBetween(from: Vec3, to: Vec3): Pair<Vec3, Float>? {
    val axis = from cross to
    val sine = axis.length
    if (sine < SAME_DIRECTION * from.length * to.length) return null
    return axis * (1f / sine) to atan2(sine, from dot to)
}

/** The shortest turn taking [from] onto [to], scaled by [share] of its angle. */
internal fun shareOfTurn(from: Vec3, to: Vec3, share: Float = 1f): Mat3 =
    turnBetween(from, to)?.let { (axis, angle) -> rotation(axis, angle * share) } ?: PoseWork.IDENTITY

/** The angle of the turn [m] makes, in degrees. */
internal fun angleOf(m: Mat3): Float {
    val trace = m.x.x + m.y.y + m.z.z
    return kotlin.math.acos(((trace - 1f) / 2f).coerceIn(-1f, 1f)) * RADIANS_TO_DEGREES
}
