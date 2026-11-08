package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

/** How far along u a joint's turn comes in, as a share of the part's length. */
private const val BEND_SPAN = 0.06f

/** How far around from a one-sided joint's v its turn holds whole. */
private const val SIDE_HELD = 0.06f

/** How far around from a one-sided joint's v its turn has gone. */
private const val SIDE_GONE = 0.11f

private const val VECTOR = 3

/**
 * A joint turned by [degrees] about [hinge], a unit direction in the part's axes, through the middle of its ring at [u]. What lies past [u] turns with it. A joint
 * with a [v] turns only the side of the part around that v, as a jaw does, and only on swept patches.
 */
data class JointBend(val u: Float, val v: Float?, val hinge: Vec3, val degrees: Float)

/** This mesh with [bends] turned, the furthest along first so an inner joint carries what an outer one turned. Grids, indices and everything addressed by u and v stay as they are. */
fun PartMesh.bent(bends: List<JointBend>): PartMesh {
    val turning = bends.filter { it.degrees != 0f }.sortedByDescending { it.u }
    if (turning.isEmpty()) return this
    return PartMesh(part, turning.fold(patches) { held, bend -> held.turnedBy(bend) }, axes)
}

private fun List<PatchMesh>.turnedBy(bend: JointBend): List<PatchMesh> {
    val swept = firstOrNull { it.patch.covers(bend.u) } ?: firstOrNull { it.patch.slices != null } ?: return this
    val pivot = swept.patch.ringMiddle(bend.u)
    return map { mesh ->
        val patch = mesh.patch
        if (bend.v != null && patch.slices == null) return@map mesh
        val points = patch.points.copyOf()
        val normals = patch.proxyNormals?.copyOf()
        for (ring in 0 until patch.nu) {
            for (segment in 0 until patch.nv) {
                val i = patch.index(ring, segment)
                val share = bendShare(patch.boneUOf(i), bend.u, patch.boneU != null) * sideShare(segment.toFloat() / patch.segments, bend.v)
                if (share == 0f) continue
                val radians = bend.degrees * DEGREES_TO_RADIANS * share
                write(points, i, pivot + (patch.point(i) - pivot).turned(bend.hinge, radians))
                normals?.let { write(it, i, read(it, i).turned(bend.hinge, radians)) }
            }
        }
        PatchMesh(patch.withPoints(points, normals), mesh.indices, mesh.bones, mesh.us, mesh.vs)
    }
}

/** How much of a turn at [joint] reaches [u]: none before it, all by [BEND_SPAN] past it, all at once for a [rigid] patch riding at one u. A joint at the part's start takes it all. */
private fun bendShare(u: Float, joint: Float, rigid: Boolean): Float = when {
    joint <= 0f -> 1f
    u <= joint -> 0f
    rigid || u >= joint + BEND_SPAN -> 1f
    else -> smooth((u - joint) / BEND_SPAN)
}

private fun sideShare(v: Float, joint: Float?): Float {
    if (joint == null) return 1f
    val off = abs(v - joint).let { minOf(it, 1f - it) }
    return when {
        off <= SIDE_HELD -> 1f
        off >= SIDE_GONE -> 0f
        else -> 1f - smooth((off - SIDE_HELD) / (SIDE_GONE - SIDE_HELD))
    }
}

private fun smooth(t: Float) = t * t * (3f - 2f * t)

/** The middle of this patch's ring nearest [u]. */
fun Patch.ringMiddle(u: Float): Vec3 {
    val held = u.coerceIn(ringUs.first(), ringUs.last())
    val around = (0 until nv).map { positionAt(held, it.toFloat() / segments) }
    return around.reduce { sum, point -> sum + point } * (1f / around.size)
}

/** This vector turned by [radians] about the unit direction [axis]. */
private fun Vec3.turned(axis: Vec3, radians: Float): Vec3 {
    val c = cos(radians)
    return this * c + (axis cross this) * sin(radians) + axis * ((axis dot this) * (1f - c))
}

private fun read(values: FloatArray, i: Int) = Vec3(values[i * VECTOR], values[i * VECTOR + 1], values[i * VECTOR + 2])

private fun write(values: FloatArray, i: Int, to: Vec3) {
    values[i * VECTOR] = to.x
    values[i * VECTOR + 1] = to.y
    values[i * VECTOR + 2] = to.z
}
