package dev.chieppa.squash.rig

import dev.chieppa.squash.document.BonePose
import dev.chieppa.squash.document.JointMode
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import kotlin.math.abs
import kotlin.math.sign

/** The lean is searched over this range either way, in degrees, in steps of [LEAN_STEP]. */
private const val LEAN_RANGE = 180
private const val LEAN_STEP = 1f

/** Bisection stops once the bracket is this narrow, in degrees. */
private const val LEAN_PRECISION = 0.001f

/** A direction this near level counts as level when the grid lands on it exactly. */
private const val LEVEL = 1e-6f

/**
 * The lean in degrees that turns [bone] across its hinge until it points level, away from the body's middle plane, or null when no
 * lean does. Of the leans that level it, the one that points it most directly out to the side is taken. Out to the side is along
 * body z, the side the bone's pivot lies on.
 */
fun levelLean(bone: Bone): Float? {
    val outward = sign(if (abs(bone.pivot.z) > LEVEL) bone.pivot.z else bone.direction.z)
    if (outward == 0f) return null
    fun height(lean: Float): Float = (localTurn(bone, BoneAngles(0f, 0f, lean)) * bone.direction).y
    fun reach(lean: Float): Float = (localTurn(bone, BoneAngles(0f, 0f, lean)) * bone.direction).z * outward
    val roots = mutableListOf<Float>()
    var low = -LEAN_RANGE.toFloat()
    while (low < LEAN_RANGE) {
        var a = low
        var b = low + LEAN_STEP
        val ha = height(a)
        val hb = height(b)
        low = b
        if (abs(ha) < LEVEL) roots += a
        if (ha * hb >= 0f) continue
        var below = ha
        while (b - a > LEAN_PRECISION) {
            val middle = (a + b) / 2f
            val h = height(middle)
            if (h * below > 0f) { a = middle; below = h } else b = middle
        }
        roots += (a + b) / 2f
    }
    return roots.filter { reach(it) > 0f }.maxByOrNull(::reach)
}

/**
 * The pose that holds every arm in [arms] straight out level: its first bone leaning to [levelLean], free of its limits, and each
 * later bone of its own chain straight; the bones of what hangs from the arm are left out, so they keep the pose they are laid over.
 * An arm with no level lean is left out, and a rig with none gives a pose with no bones.
 */
fun tPoseOf(rig: Rig, arms: List<PartId>): Pose {
    val bones = arms.flatMap { arm ->
        val chain = rig.bones.filter { it.name.part == arm && it.digit == null }
        val root = chain.firstOrNull() ?: return@flatMap emptyList()
        val lean = levelLean(root) ?: return@flatMap emptyList()
        listOf(BonePose(arm, root.name.joint, lean = lean, mode = JointMode.FREE)) + chain.drop(1).filter { it.length > 0f }.map { BonePose(arm, it.name.joint) }
    }
    return Pose(bones)
}
