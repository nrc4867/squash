package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Mat3
import dev.chieppa.squash.geom.RenderMesh
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.turnedNormals

/** A turn whose columns stray from the identity's by less than this is taken as none. */
private const val STILL = 1e-6f

/** How many times a pose pushes a bound part off the body, each reading the body again where the last push left it. */
private const val PUSH_ROUNDS = 2

/**
 * [part], bound to the body by [hold] and skinned into [positions] and [meshes] with the body under [frames]: first carried by its own
 * bone's turn on the bone it sits on, about its own bone's posed pivot, so posing the part's own bone still moves it over the skin;
 * then, [PUSH_ROUNDS] times, pushed out along the posed body's normal, read at the body's nearest point as at rest ([BodySurface]),
 * wherever it lies closer to the body than [KEPT_STANDOFF] of its rest standoff, whichever body it has come near, the push eased over
 * its triangles so it moves as one piece. Null when none of these moves it.
 */
internal fun Rig.heldOnBody(part: RigPart, hold: BodyHold, frames: BoneFrames, positions: Map<PartId, FloatArray>, meshes: Map<PartId, RenderMesh>): Pair<FloatArray, RenderMesh>? {
    val skinned = positions[part.id] ?: return null
    val mesh = meshes[part.id] ?: return null
    val own = ownTurn(part.id, frames)
    val bodyAt = hold.parts.map { positions[it] ?: return null }
    val bodyMeshes = hold.parts.map { meshes[it] ?: return null }
    if (own == null && mesh === part.mesh && hold.parts.all { meshes[it] === this.part(it)?.mesh }) return null
    val moved = skinned.copyOf()
    val vertices = mesh.vertices.copyOf()
    if (own != null) {
        val (turn, pivot) = own
        for (v in 0 until part.vertexCount) {
            moved.put(v * 3, pivot + turn * (moved.vec(v * 3) - pivot))
            val at = v * RenderMesh.FLOATS_PER_VERTEX
            vertices.put(at + RenderMesh.NORMAL_OFFSET_FLOATS, turn * vertices.vec(at + RenderMesh.NORMAL_OFFSET_FLOATS))
            vertices.put(at + RenderMesh.PROXY_OFFSET_FLOATS, turn * vertices.vec(at + RenderMesh.PROXY_OFFSET_FLOATS))
        }
    }
    val before = moved.copyOf()
    val body = BodySurface.of(bodyAt, bodyMeshes, hold.reach)
    var pushed = false
    repeat(PUSH_ROUNDS) { if (pushedOff(part, hold, body, moved)) pushed = true }
    if (pushed) turnNormals(vertices, part.vertexCount, before, moved, part.indices)
    if (own == null && !pushed) return null
    for (v in 0 until part.vertexCount) vertices.put(v * RenderMesh.FLOATS_PER_VERTEX, moved.vec(v * 3) - part.origin)
    return moved to RenderMesh(vertices, mesh.indices)
}

/**
 * [moved], the posed vertices of [part], pushed out along [body]'s normal wherever one lies closer to it than [KEPT_STANDOFF] of its
 * rest standoff, the push eased over the part's triangles and then made up again along each vertex's own push where easing left it
 * short, so neighbours pushed apart, as either side of a crease, never carry each other back in; whether any was.
 */
private fun pushedOff(part: RigPart, hold: BodyHold, body: BodySurface, moved: FloatArray): Boolean {
    val push = FloatArray(moved.size)
    var pushed = false
    for (v in 0 until part.vertexCount) {
        val standoff = hold.standoff[v]
        if (standoff <= 0f) continue
        val p = moved.vec(v * 3)
        val plane = body.planeAt(p) ?: continue
        val wanted = standoff * KEPT_STANDOFF
        val gap = plane.standoff(p)
        if (gap >= wanted) continue
        push.put(v * 3, plane.normal * (wanted - gap))
        pushed = true
    }
    if (!pushed) return false
    val own = push.copyOf()
    eased(push, part.indices, part.vertexCount)
    for (v in 0 until part.vertexCount) {
        val needed = own.vec(v * 3)
        val length = needed.length
        if (length <= 0f) continue
        val along = needed * (1f / length)
        val short = length - (push.vec(v * 3) dot along)
        if (short > 0f) push.put(v * 3, push.vec(v * 3) + along * short)
    }
    for (i in moved.indices) moved[i] += push[i]
    return true
}

/** The normals and shading normals in [vertices] turned as the faces of [triangles] turn from [before] to [after], so a push shades as it bends the part. */
private fun turnNormals(vertices: FloatArray, count: Int, before: FloatArray, after: FloatArray, triangles: IntArray) {
    for (offset in intArrayOf(RenderMesh.NORMAL_OFFSET_FLOATS, RenderMesh.PROXY_OFFSET_FLOATS)) {
        val normals = FloatArray(count * 3) { vertices[(it / 3) * RenderMesh.FLOATS_PER_VERTEX + offset + it % 3] }
        val turned = turnedNormals(normals, before, after, triangles)
        for (i in turned.indices) vertices[(i / 3) * RenderMesh.FLOATS_PER_VERTEX + offset + i % 3] = turned[i]
    }
}

/** How [part]'s own first bone turns on the bone it hangs from, with its posed pivot; null where it does not turn on it. */
private fun Rig.ownTurn(part: PartId, frames: BoneFrames): Pair<Mat3, Vec3>? {
    val bone = bones.indexOfFirst { it.name.part == part }
    if (bone < 0) return null
    val parent = parents[bone]
    val turn = if (parent >= 0) frames.turns[bone] * frames.turns[parent].transposed() else frames.turns[bone]
    if (turn.isStill()) return null
    return turn to frames.transforms[bone](bones[bone].pivot)
}

private fun Mat3.transposed(): Mat3 = Mat3(Vec3(x.x, y.x, z.x), Vec3(x.y, y.y, z.y), Vec3(x.z, y.z, z.z))

private fun Mat3.isStill(): Boolean =
    (x - Vec3.X).length < STILL && (y - Vec3.Y).length < STILL && (z - Vec3.Z).length < STILL
