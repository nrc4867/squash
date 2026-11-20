package dev.chieppa.squash.character.surface

import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JoinedMember
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How near its end, as a share of its rings, a socket must sit for the part on it to end the run. */
private const val END_SHARE = 0.3f

/** How far a run may go on past the socket, as a multiple of its radius there, for the stretch past it to be held inside the part on it. */
private const val PAST_REACH = 1f

/** Halvings the scale that keeps a ring past the socket inside the part on it is found to. */
private const val INSIDE_STEPS = 10

/** Every how many points round a ring past the socket is checked to lie inside the part on it. */
private const val INSIDE_POINT_STEP = 2

/** How far back along the run from the socket an end taper reaches, as a multiple of the run's radius there. */
const val TAPER_REACH = 2.5f

/** A run narrows to the part ending it only where that part's section is at most this share of the run's radius there. */
private const val TAPER_TOLERANCE = 0.95f

/** A part whose section where it meets the run is under this share of the run's radius there sits on the run's end rather than continuing it. */
private const val CONTINUES_SHARE = 0.35f

/** How many planes, evenly back from the socket's ring to the run's radius behind it, a child's section is looked for in. */
private const val SECTION_PLANES = 4

/** How many directions round the socket plane a section's radius is read in. */
private const val SECTION_DIRECTIONS = 8

/** Halvings a section's edge is found to along each direction. */
private const val SECTION_STEPS = 14

/** How far out, as a multiple of the run's radius, a section's edge is looked for. */
private const val SECTION_REACH = 2f

/**
 * How a run ends in the part on a socket near its end: [radius] of the run at its ring [ring], nearest the socket, and the [section]
 * radius of the part there, which the run's last stretch narrows to (the run's own radius where the part is as wide); each ring past the
 * socket's, by its place past it in [beyond], held to the scale that keeps it inside the part, so a neck running on past the head's seat
 * never shows behind the skull.
 */
class EndTaper(val ring: Int, val radius: Float, val section: Float, val beyond: FloatArray = FloatArray(0)) {
    /** Whether the run narrows into the part at all, rather than only ending inside it. */
    val narrows: Boolean get() = section < radius

    /** How much ring [k], [back] along the run from the socket ring toward the root, is scaled about the axis. */
    fun scale(k: Int, back: Float): Float {
        if (k > ring) return section / radius * (beyond.getOrNull(k - ring - 1) ?: 1f)
        if (k == ring) return section / radius
        val reach = radius * TAPER_REACH
        if (back >= reach) return 1f
        val t = 1f - back / reach
        val w = t * t * (3f - 2f * t)
        return 1f + (section / radius - 1f) * w
    }
}

/**
 * How [patch], a parent's run placed as [parent], ends in [child], a part closed at its root on a socket at [childOrigin] within the last
 * [END_SHARE] of the run (see [EndTaper]). The child's section is read along [SECTION_DIRECTIONS] directions round the run's axis, in the
 * plane square to it furthest back toward the root, of [SECTION_PLANES] up to the run's radius back from its ring nearest the socket, where
 * the axis still lies inside the child: the ankle rising from a foot into the leg, not the foot below it. The run narrows to it where it
 * is narrower than the run but not so much narrower that the child sits on the run's end rather than continuing it, as a head on a neck
 * does. Rings past the socket are held inside the child where the run goes on past it no more than [PAST_REACH] times its radius. Null
 * where the run neither narrows nor has a ring past the socket standing out of the child.
 */
internal fun endTaper(patch: Patch, parent: JoinedMember, child: JoinedMember, childOrigin: Vec3): EndTaper? {
    val slices = patch.slices ?: return null
    val local = parent.local(childOrigin)
    val ring = slices.indices.minByOrNull { (slices[it].frame.center - local).length } ?: return null
    if (ring < (patch.nu - 1) * (1f - END_SHARE)) return null
    val frame = slices[ring].frame
    val radius = (0 until patch.nv).map { (patch.point(patch.index(ring, it)) - frame.center).length }.average().toFloat()
    if (radius <= 0f) return null
    val section = childSection(patch, parent, child, ring, radius)?.takeIf { it <= radius * TAPER_TOLERANCE && it >= radius * CONTINUES_SHARE } ?: radius
    val past = (ring until patch.nu - 1).sumOf { (slices[it + 1].frame.center - slices[it].frame.center).length.toDouble() }.toFloat()
    val beyond = if (past <= radius * PAST_REACH) insideScales(patch, parent, child, ring, section / radius) else FloatArray(0)
    if (section >= radius && beyond.all { it >= 1f }) return null
    return EndTaper(ring, radius, section, beyond)
}

/** The child's section radius about the run's axis near ring [ring], as [endTaper] reads it; null where the axis lies outside the child. */
private fun childSection(patch: Patch, parent: JoinedMember, child: JoinedMember, ring: Int, radius: Float): Float? {
    val frame = requireNotNull(patch.slices)[ring].frame
    val back = frame.tangent.normalized() * -1f
    val plane = (SECTION_PLANES downTo 0).map { frame.center + back * (radius * it / SECTION_PLANES) }
        .map { parent.origin + parent.shownDirection(it) }
        .firstOrNull { child.own(it) < 0f } ?: return null
    val side = frame.side.normalized()
    val up = frame.up.normalized()
    val reach = radius * SECTION_REACH
    return (0 until SECTION_DIRECTIONS).map { d ->
        val angle = (2.0 * PI * d / SECTION_DIRECTIONS).toFloat()
        edgeAlong(child, plane, parent.shownDirection(side * cos(angle) + up * sin(angle)), reach)
    }.average().toFloat()
}

/**
 * For each ring past [ring], the largest scale about the axis, times [base], no larger than the ring before's, at which its points lie
 * inside the child; 1 for a ring already inside it, so a run ending inside its child is left as it is.
 */
private fun insideScales(patch: Patch, parent: JoinedMember, child: JoinedMember, ring: Int, base: Float): FloatArray {
    val slices = requireNotNull(patch.slices)
    val scales = FloatArray(patch.nu - 1 - ring) { 1f }
    var most = 1f
    for (k in ring + 1 until patch.nu) {
        val centre = slices[k].frame.center
        fun inside(scale: Float) = (0 until patch.nv step INSIDE_POINT_STEP).all { v ->
            val point = centre + (patch.point(patch.index(k, v)) - centre) * (scale * base)
            child.own(parent.origin + parent.shownDirection(point)) < 0f
        }
        val scale = when {
            inside(most) -> most
            !inside(0f) -> most
            else -> {
                var low = 0f
                var high = most
                repeat(INSIDE_STEPS) {
                    val mid = (low + high) / 2f
                    if (inside(mid)) low = mid else high = mid
                }
                low
            }
        }
        most = scale
        scales[k - ring - 1] = scale
    }
    return scales
}

/** How far from [from] along [toward] [member]'s field crosses zero, within [reach]; [reach] when it does not. */
private fun edgeAlong(member: JoinedMember, from: Vec3, toward: Vec3, reach: Float): Float {
    if (member.own(from + toward * reach) < 0f) return reach
    var inside = 0f
    var outside = reach
    repeat(SECTION_STEPS) {
        val mid = (inside + outside) / 2f
        if (member.own(from + toward * mid) < 0f) inside = mid else outside = mid
    }
    return (inside + outside) / 2f
}

/** A direction in this member's own space as it lies in body space. */
private fun JoinedMember.shownDirection(local: Vec3): Vec3 = if (mirrored) local.mirrored else local
