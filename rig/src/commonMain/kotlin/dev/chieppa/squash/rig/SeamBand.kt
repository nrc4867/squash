package dev.chieppa.squash.rig

import dev.chieppa.squash.document.PartId

/**
 * How far either side of a joint inside a part its two bones blend, in the part's radius at the joint. A turn of up to 90
 * degrees keeps the inner side from folding when the blend reaches one radius each way.
 */
const val JOINT_REACH = 1f

/** How far a seam's band reaches past the seam, in the child's radius at its socket end: along the child, and round the rim on the parent. */
const val SEAM_REACH = 1f

/** The share of the other side's bone right at a seam, where both sides meet halfway. */
const val SEAM_SHARE = 0.5f

/**
 * How far along u from a seam the rings that size it are read: up to this u on the child, so a closed part such as a paw is
 * measured where it is full width and not at its tip, and this far either side of the socket on the parent.
 */
const val SOCKET_END_U = 0.25f

/** The seam where [child] meets the part its socket is on. */
data class Seam(val child: PartId)

/** A vertex as a painted seam stroke addresses it: by part, patch, u and v, as an anchor does. */
data class SeamVertex(val part: PartId, val patch: String, val u: Float, val v: Float)

/**
 * Every vertex of one part as a seam's band reaches it: each one's address in [vertices] and its place in body space in
 * [positions], three floats a vertex. [onChild] is true on the part sitting on the socket, whose share is its parent's bone, and
 * false on the parent, whose share is the child's first bone.
 */
class SeamVertices(val part: PartId, val onChild: Boolean, val vertices: List<SeamVertex>, val positions: FloatArray)

/**
 * What painted seam strokes make of a seam's guessed band. The rig asks it, for every vertex of both parts at each seam, one
 * part at a time, with the share of the other side's bone the band guessed at each, which is 0 outside it, and uses the shares
 * it answers, one a vertex in the same order.
 */
fun interface SeamPaint {
    fun shares(seam: Seam, at: SeamVertices, guessed: FloatArray): FloatArray

    companion object {
        /** No strokes: every seam keeps its guessed band. */
        val NONE = SeamPaint { _, _, guessed -> guessed }
    }
}

/** How far each joint's blend reaches, as a factor of what the rig guesses, by part and joint name. */
fun interface JointReach {
    fun of(part: PartId, joint: String): Float

    companion object {
        /** Every joint at the guessed reach. */
        val GUESSED = JointReach { _, _ -> 1f }
    }
}

/** 0 at 0, 1 at 1 and beyond, easing in and out. */
internal fun smoothStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

/**
 * How wide the seam where [child] meets [parent] is: the child's widest ring near its socket end, held to the parent's widest
 * near the socket, since a closed child such as a head can be wider than what it sits on.
 */
internal fun seamRadius(child: RiggedPart, parent: RiggedPart?): Float {
    val own = child.rings.widestOver(0f, SOCKET_END_U)
    val socketU = child.placed.socketSpec?.u ?: return own
    if (parent == null || !parent.rings.hasRings) return own
    val around = parent.rings.widestOver((socketU - SOCKET_END_U / 2f).coerceAtLeast(0f), (socketU + SOCKET_END_U / 2f).coerceAtMost(1f))
    return minOf(own, around)
}

/** The share of the parent's bone on a child at [u], for a band [band] long along u from the socket end. */
internal fun childShare(u: Float, band: Float): Float =
    if (band <= 0f || u >= band) 0f else SEAM_SHARE * (1f - smoothStep(u / band))

/** The share of a child's bone on its parent at [distance] from the socket, for a child [radius] wide there. */
internal fun rimShare(distance: Float, radius: Float): Float = when {
    radius <= 0f -> 0f
    distance <= radius -> SEAM_SHARE
    else -> SEAM_SHARE * (1f - smoothStep((distance - radius) / (SEAM_REACH * radius)))
}

/** 0 at 0, 1 at 1 and beyond, easing in and out, for views that shade by weight as the bands blend. */
fun smoothStepOf(t: Float): Float = smoothStep(t)
