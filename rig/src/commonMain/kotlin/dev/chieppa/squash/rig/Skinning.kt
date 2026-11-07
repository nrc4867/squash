package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Pose
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI

private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

/** The shortest a stretch makes a span, as a factor of its rest length. */
const val MIN_STRETCH = 0.1f

/**
 * Every rigged part posed, by part: its vertices in body space, three floats a vertex in the order of [Rig.parts]' meshes, in
 * [parts]; and in [meshes] the mesh the renderer draws, in the part's own space at its origin with its normals turned to match.
 * A part the pose leaves where it rests keeps its rest mesh itself.
 */
class PosedCharacter(val parts: Map<PartId, FloatArray>, val meshes: Map<PartId, RenderMesh>)

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

/**
 * [rig]'s parts posed by [pose], linear blend skinning over [boneTransforms]. A vertex that follows no bone stays at rest. A
 * normal turns by the same blend of the bones' maps for normals, so a stretch tilts it as the surface tilts, and is made unit again.
 */
fun Rig.posed(pose: Pose): PosedCharacter {
    val transforms = boneTransforms(this, pose)
    val positions = HashMap<PartId, FloatArray>(parts.size)
    val meshes = HashMap<PartId, RenderMesh>(parts.size)
    for (part in parts) {
        if (part.restsUnder(transforms)) {
            positions[part.id] = part.rest.copyOf()
            meshes[part.id] = part.mesh
        } else {
            val skinned = part.skinnedBy(transforms)
            positions[part.id] = skinned.positions
            meshes[part.id] = skinned.mesh
        }
    }
    return PosedCharacter(positions, meshes)
}

private fun RigPart.restsUnder(transforms: List<Affine>): Boolean =
    weights.bones.all { it < 0 || transforms[it].isIdentity }

private class Skinned(val positions: FloatArray, val mesh: RenderMesh)

private fun RigPart.skinnedBy(transforms: List<Affine>): Skinned {
    val positions = rest.copyOf()
    val vertices = mesh.vertices.copyOf()
    for (v in 0 until vertexCount) {
        val point = Vec3(rest[v * 3], rest[v * 3 + 1], rest[v * 3 + 2])
        val at = v * RenderMesh.FLOATS_PER_VERTEX
        val normal = vertices.vec(at + RenderMesh.NORMAL_OFFSET_FLOATS)
        val proxy = vertices.vec(at + RenderMesh.PROXY_OFFSET_FLOATS)
        var moved = Vec3.ZERO
        var turned = Vec3.ZERO
        var turnedProxy = Vec3.ZERO
        var total = 0f
        for (slot in 0 until MAX_INFLUENCES) {
            val bone = weights.bones[v * MAX_INFLUENCES + slot]
            if (bone < 0) continue
            val w = weights.weights[v * MAX_INFLUENCES + slot]
            val transform = transforms[bone]
            moved += transform(point) * w
            turned += transform.normals * normal * w
            turnedProxy += transform.normals * proxy * w
            total += w
        }
        if (total <= 0f) continue
        val posed = moved * (1f / total)
        positions[v * 3] = posed.x
        positions[v * 3 + 1] = posed.y
        positions[v * 3 + 2] = posed.z
        vertices.put(at, posed - origin)
        vertices.put(at + RenderMesh.NORMAL_OFFSET_FLOATS, turned.unitOr(normal))
        vertices.put(at + RenderMesh.PROXY_OFFSET_FLOATS, turnedProxy.unitOr(proxy))
    }
    return Skinned(positions, RenderMesh(vertices, mesh.indices))
}

private const val MIN_NORMAL_LENGTH = 1e-9f

private fun Vec3.unitOr(fallback: Vec3): Vec3 = if (length > MIN_NORMAL_LENGTH) normalized() else fallback

private fun FloatArray.vec(at: Int) = Vec3(this[at], this[at + 1], this[at + 2])

private fun FloatArray.put(at: Int, value: Vec3) {
    this[at] = value.x
    this[at + 1] = value.y
    this[at + 2] = value.z
}
