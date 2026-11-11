package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.ShellInside
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import dev.chieppa.squash.geom.strip.nearestUv
import dev.chieppa.squash.geom.surfaceAt

/** How far past where the tube leaves the host its strip meets it, along each column, in the tube's radii there. */
private const val TUBE_REACH = 0.7f

/** How far from where the tube leaves the host the strip's foot lies on the host, in the tube's radii. */
private const val HOST_REACH = 0.6f

/** The share of a tube's rings, from the end it joins by, that its strip may take. */
private const val MOST_RINGS = 0.6f

/** The most of what a tube shows past the host its strip may take, as a share of its rings there. */
private const val MOST_SHOWN = 0.34f

/** The share of a tube's columns that must come out of the host for a strip to join them. */
private const val ENTERING = 0.5f

/** How many halvings find where a column leaves the host. */
private const val HALVINGS = 8

/** How many passes calm the foot's loop, each point toward the middle of its neighbours. */
private const val CALMING = 3

private const val MIN_WAY = 1e-6f

/**
 * Where a strip would join [tube]'s first swept patch, from its start when [atStart] or else from its end, to [host]'s surface, for the
 * part [joined] on the socket between them: the first ring past where every column has left [host] by [TUBE_REACH] of the tube's
 * radius, or [MOST_SHOWN] of the rings showing past the host if that comes first, and a foot on [host] [HOST_REACH] radii out from where each column leaves it, calmed round the loop. Null when too few
 * columns come out of [host], or the tube runs inside it for more than [MOST_RINGS] of its rings.
 */
internal fun planStrip(tube: PlacedPart, atStart: Boolean, host: PlacedPart, joined: PartId): StripJoin? {
    val hostId = host.id ?: return null
    val own = tube.surface.patches.firstOrNull { it.slices != null } ?: return null
    val inside = host.inside ?: return null
    val nu = own.nu
    val nv = own.nv
    val limit = (nu * MOST_RINGS).toInt()
    if (limit < 2) return null
    fun ring(step: Int) = if (atStart) step else nu - 1 - step
    fun at(step: Int, j: Int) = tube.origin + tube.surface.shown(own.point(own.index(ring(step), j)))
    val last = IntArray(nv) { j -> (limit downTo 0).firstOrNull { at(it, j) in inside } ?: -1 }
    if (last.count { it >= 0 } < nv * ENTERING || last.max() >= limit - 1) return null
    val leaving = Array(nv) { j -> if (last[j] < 0) at(0, j) else leavingPoint(at(last[j], j), at(last[j] + 1, j), inside) }
    val out = last.max() + 1
    val radius = ringRadius(List(nv) { at(out, it) })
    if (radius <= 0f) return null
    val most = out + ((nu - 1 - out) * MOST_SHOWN).toInt()
    val step = (out until most).firstOrNull { s -> (0 until nv).all { j -> (at(s, j) - leaving[j]).length >= TUBE_REACH * radius } } ?: most
    val middle = host.surface.shown(middleOf(leaving.toList()) - host.origin)
    val surface = host.surface.patches.filter { it.slices != null }.minByOrNull { patch ->
        val near = patch.nearestUv(middle)
        (patch.positionAt(near.u, near.v) - middle).length
    } ?: return null
    val feet = feetOf(host, surface, leaving, List(nv) { j -> middleOf(List(nv) { at(maxOf(last[j], 0), it) }) }, HOST_REACH * radius, own.closedV)
    return StripJoin(StripJoin.patchName(atStart), own.name, ring(step), atStart, hostId, surface.name, feet, joined)
}

/** Where a column leaves the shell between [inside], a point within it, and [outside], a point beyond, by halving. */
private fun leavingPoint(inside: Vec3, outside: Vec3, shell: ShellInside): Vec3 {
    var a = inside
    var b = outside
    repeat(HALVINGS) {
        val middle = (a + b) * 0.5f
        if (middle in shell) a = middle else b = middle
    }
    return (a + b) * 0.5f
}

private fun middleOf(points: List<Vec3>): Vec3 = points.fold(Vec3.ZERO) { sum, p -> sum + p } * (1f / points.size)

private fun ringRadius(points: List<Vec3>): Float {
    val middle = middleOf(points)
    return points.sumOf { (it - middle).length.toDouble() }.toFloat() / points.size
}

/**
 * The strip's foot on [host]'s [patch]: each of [leaving] carried [reach] across the host's surface away from the tube's middle there,
 * [middles], then the loop calmed and laid back on the surface.
 */
private fun feetOf(host: PlacedPart, patch: Patch, leaving: Array<Vec3>, middles: List<Vec3>, reach: Float, closed: Boolean): List<SurfaceUv> {
    fun local(p: Vec3) = host.surface.shown(p - host.origin)
    fun body(at: SurfaceUv) = host.origin + host.surface.shown(patch.positionAt(at.u, at.v))
    var feet = leaving.indices.map { j ->
        val at = patch.nearestUv(local(leaving[j]))
        val normal = host.surface.shown(patch.surfaceAt(at.u, at.v).normal)
        val off = leaving[j] - middles[j]
        val across = off - normal * (off dot normal)
        if (across.length < MIN_WAY) body(at) else body(at) + across.normalized() * reach
    }
    val n = feet.size
    repeat(CALMING) {
        val held = feet
        feet = List(n) { j ->
            val before = if (closed || j > 0) held[(j + n - 1) % n] else held[j]
            val after = if (closed || j < n - 1) held[(j + 1) % n] else held[j]
            (before + after) * 0.25f + held[j] * 0.5f
        }
    }
    return feet.map { patch.nearestUv(local(it)) }
}
