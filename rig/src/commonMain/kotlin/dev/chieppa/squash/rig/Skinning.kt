package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI

private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

/** The shortest a stretch makes a span, as a factor of its rest length. */
const val MIN_STRETCH = 0.1f

/** Every rigged part's vertices posed, in body space, three floats a vertex in the order of [Rig.parts]' meshes. */
class PosedCharacter(val parts: Map<PartId, FloatArray>)

/**
 * Where each bone of [rig] takes a rest point under [pose], parents first. A bone turns by its pose's degrees, held to its
 * limits, about its hinge as its parent has turned it, through its pivot as its parent has carried it. A bone whose span
 * ends at a stretching joint also lengthens along its span by that joint's stretch and narrows across it to keep volume,
 * which carries the stretching joint and everything past it.
 */
fun boneTransforms(rig: Rig, pose: Pose): List<Affine> {
    val turns = ArrayList<Mat3>(rig.bones.size)
    val result = ArrayList<Affine>(rig.bones.size)
    for ((i, bone) in rig.bones.withIndex()) {
        val parent = rig.parents[i]
        val degrees = pose.of(bone.name.part, bone.name.joint)?.degrees ?: 0f
        val held = bone.limits?.let { degrees.coerceIn(it.min, it.max) } ?: degrees
        val own = rotation(bone.hinge, held * DEGREES_TO_RADIANS)
        val turned = if (parent >= 0) turns[parent] * own else own
        val pivot = if (parent >= 0) result[parent](bone.pivot) else bone.pivot
        val stretcher = rig.stretchedBy[i]
        val factor = if (stretcher >= 0) stretchOf(rig, stretcher, pose) else 1f
        val linear = if (factor != 1f) turned * stretchAlong(bone.direction, factor) else turned
        turns += turned
        result += Affine(linear, pivot - linear * bone.pivot)
    }
    return result
}

private fun stretchOf(rig: Rig, bone: Int, pose: Pose): Float {
    val name = rig.bones[bone].name
    return (pose.of(name.part, name.joint)?.stretch ?: 1f).coerceAtLeast(MIN_STRETCH)
}

/** [rig]'s parts posed by [pose], linear blend skinning over [boneTransforms]. A vertex that follows no bone stays at rest. */
fun Rig.posed(pose: Pose): PosedCharacter {
    val transforms = boneTransforms(this, pose)
    return PosedCharacter(parts.associate { it.id to it.posedBy(transforms) })
}

private fun RigPart.posedBy(transforms: List<Affine>): FloatArray {
    val out = rest.copyOf()
    for (v in 0 until vertexCount) {
        val point = Vec3(rest[v * 3], rest[v * 3 + 1], rest[v * 3 + 2])
        var x = 0f
        var y = 0f
        var z = 0f
        var total = 0f
        for (slot in 0 until MAX_INFLUENCES) {
            val bone = weights.bones[v * MAX_INFLUENCES + slot]
            if (bone < 0) continue
            val w = weights.weights[v * MAX_INFLUENCES + slot]
            val moved = transforms[bone](point)
            x += moved.x * w
            y += moved.y * w
            z += moved.z * w
            total += w
        }
        if (total > 0f) {
            out[v * 3] = x / total
            out[v * 3 + 1] = y / total
            out[v * 3 + 2] = z / total
        }
    }
    return out
}
