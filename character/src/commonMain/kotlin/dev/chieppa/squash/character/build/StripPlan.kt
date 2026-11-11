package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Role
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

/** How far from where the neck leaves a head its strip's foot lies under the head, in the neck's radii. */
private const val HEAD_REACH = 0.1f

/** How far out the foot lies on [host], in the tube's radii: less on a head, so the jaw keeps its edge. */
private fun reachOn(host: PlacedPart) = if (host.role == Role.HEAD) HEAD_REACH else HOST_REACH

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
 * radius, or [MOST_SHOWN] of the rings showing past the host if that comes first, and a foot on [host] [HOST_REACH] radii out from where
 * each column leaves it, calmed round the loop; a column never inside leaves at its first ring. Null when fewer than [ENTERING] of the
 * columns come out of [host], unless the tube is [seated] on its surface, or the tube runs inside it for more than [MOST_RINGS] of its
 * rings.
 */
internal fun planStrip(tube: PlacedPart, atStart: Boolean, host: PlacedPart, joined: PartId, seated: Boolean = false): StripJoin? {
    val hostId = host.id ?: return null
    val own = tube.surface.patches.firstOrNull { it.slices != null } ?: return null
    val inside = host.inside ?: return null
    val nu = own.nu
    val nv = own.nv
    val limit = (nu * MOST_RINGS).toInt()
    if (limit < 2) return null
    val run = TubeRun(tube, own, atStart)
    val last = run.lastInside(limit, inside)
    if ((!seated && last.count { it >= 0 } < nv * ENTERING) || last.max() >= limit - 1) return null
    val leaving = run.leaving(last, inside)
    val out = last.max() + 1
    val radius = ringRadius(List(nv) { run.at(out, it) })
    if (radius <= 0f) return null
    val most = out + ((nu - 1 - out) * MOST_SHOWN).toInt()
    val step = (out until most).firstOrNull { s -> (0 until nv).all { j -> (run.at(s, j) - leaving[j]).length >= TUBE_REACH * radius } } ?: most
    val surface = hostPatchUnder(host, leaving) ?: return null
    val feet = feetOf(host, surface, leaving, List(nv) { j -> middleOf(List(nv) { run.at(maxOf(last[j], 0), it) }) }, reachOn(host) * radius, own.closedV)
    return StripJoin(StripJoin.patchName(atStart), own.name, run.ring(step), atStart, hostId, surface.name, feet, joined, capped = atStart && tube.role == Role.ARM)
}

/**
 * The foot [join]'s strip takes on [host] with [tube] as they now stand: planned as [planStrip] plans it, up to the strip's own ring,
 * which stays; a column still inside [host] at that ring leaves it there. So in a pose the foot slides over the host with the tube, and
 * the strip meets the host where the posed tube leaves it. Null when the tube has lost its patch or the host its inside.
 */
internal fun slidFoot(tube: PlacedPart, join: StripJoin, host: PlacedPart): List<SurfaceUv>? {
    val own = tube.surface.patches.firstOrNull { it.name == join.tube } ?: return null
    val onHost = host.surface.patches.firstOrNull { it.name == join.hostPatch } ?: return null
    val inside = host.inside ?: return null
    val run = TubeRun(tube, own, join.atStart)
    val ringStep = if (join.atStart) join.ring else own.nu - 1 - join.ring
    val last = run.lastInside(ringStep, inside)
    val leaving = Array(own.nv) { j -> if (last[j] >= ringStep) run.at(ringStep, j) else null }
    val left = run.leaving(IntArray(own.nv) { j -> if (last[j] >= ringStep) -1 else last[j] }, inside)
    for (j in leaving.indices) leaving[j] = leaving[j] ?: left[j]
    val out = (last.max() + 1).coerceAtMost(ringStep)
    val radius = ringRadius(List(own.nv) { run.at(out, it) })
    if (radius <= 0f) return null
    val middles = List(own.nv) { j -> middleOf(List(own.nv) { run.at(last[j].coerceIn(0, ringStep), it) }) }
    return feetOf(host, onHost, Array(own.nv) { requireNotNull(leaving[it]) }, middles, reachOn(host) * radius, own.closedV)
}

/** A tube's swept patch [own] read in steps from the end [atStart] names, its points where [tube] now stands. */
private class TubeRun(private val tube: PlacedPart, private val own: Patch, private val atStart: Boolean) {
    fun ring(step: Int) = if (atStart) step else own.nu - 1 - step

    fun at(step: Int, column: Int): Vec3 = tube.origin + tube.surface.shown(own.point(own.index(ring(step), column)))

    /** For each column, the last step up to [limit] whose point lies inside [inside], or -1 when none does. */
    fun lastInside(limit: Int, inside: ShellInside): IntArray = IntArray(own.nv) { j -> (limit downTo 0).firstOrNull { at(it, j) in inside } ?: -1 }

    /** Where each column leaves [inside] past its [last] step inside, or its first point when it never lies inside. */
    fun leaving(last: IntArray, inside: ShellInside): Array<Vec3> =
        Array(own.nv) { j -> if (last[j] < 0) at(0, j) else leavingPoint(at(last[j], j), at(last[j] + 1, j), inside) }
}

/** The swept patch of [host] nearest the middle of [leaving]. */
private fun hostPatchUnder(host: PlacedPart, leaving: Array<Vec3>): Patch? {
    val middle = host.surface.shown(middleOf(leaving.toList()) - host.origin)
    return host.surface.patches.filter { it.slices != null }.minByOrNull { patch ->
        val near = patch.nearestUv(middle)
        (patch.positionAt(near.u, near.v) - middle).length
    }
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
