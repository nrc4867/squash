package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.HemPush
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.PointGrid
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.posed

/** How far a hanging branch stands off what pushes it, in body units. */
private const val HANG_CLEARANCE = 0.012f

/** How far along a hanging branch from its held end a part must leave it to leave by its free end, as a share of its length. */
private const val FREE_END_SHARE = 0.85f

/**
 * Whether [part], on a socket inside the region [branch] covers, leaves [branch] by its free end rather than through its wall: the
 * branch [Covering.hangsFree], and the first ring of [part] whose middle lies outside it lies past [FREE_END_SHARE] of the way from
 * the held end to the free end, or none does.
 */
internal fun leavesByFreeEnd(part: PlacedPart, branch: PlacedPart): Boolean {
    val covering = branch.covering ?: return false
    if (!covering.hangsFree) return false
    val slices = part.bodyPatch()?.slices ?: return false
    val inside = insideOf(branch) ?: return false
    val shell = branch.surface.patches.firstOrNull() ?: return false
    val held = branch.ringMiddle(shell, covering.hingeRing(shell.nu))
    val free = branch.ringMiddle(shell, shell.nu - 1 - covering.hingeRing(shell.nu))
    val length = (free - held).length
    if (length == 0f) return false
    val axis = (free - held) * (1f / length)
    val exit = slices.firstOrNull { slice -> part.origin + part.surface.shown(slice.frame.center) !in inside } ?: return true
    return ((part.origin + part.surface.shown(exit.frame.center) - held) dot axis) >= length * FREE_END_SHARE
}

/**
 * The parts on sockets of the part [branch] covers, inside the region it covers, that hang inside it out through its free end, which
 * [excluded] does not leave out; see [leavesByFreeEnd].
 */
internal fun hangingInside(branch: PlacedPart, body: AssembledCharacter, excluded: (PlacedPart) -> Boolean): Set<PartId> {
    val covering = branch.covering ?: return emptySet()
    if (!covering.hangsFree) return emptySet()
    return body.parts.filter { part ->
        val spec = part.socketSpec
        part.covering == null && part.hanging == null && part.socket?.parent == covering.part && spec != null &&
            spec.u in covering.from..covering.to && !excluded(part) && leavesByFreeEnd(part, branch)
    }.mapNotNullTo(HashSet()) { it.id }
}

/**
 * This character with every branch that hangs free pushed out of what hangs inside it as it stands, see [pushedClear]. Itself when
 * nothing moves.
 */
fun AssembledCharacter.withHangingPushed(): AssembledCharacter {
    val replaced = HashMap<PlacedPart, PlacedPart>()
    for (branch in parts) {
        val covering = branch.covering ?: continue
        if (!covering.hangsFree || covering.hangsOver.isEmpty()) continue
        val pushed = branch.pushedClear(this)
        if (pushed !== branch) replaced[branch] = pushed
    }
    if (replaced.isEmpty()) return this
    return AssembledCharacter(parts.map { replaced[it] ?: it }, sockets)
}

/**
 * This branch, which hangs free, pushed out by [HemPush] wherever what hangs inside it in [body] would cut it: the parts it
 * [Covering.hangsOver], every part on them, and the branches of other garments over any of those. Itself when nothing moves.
 */
internal fun PlacedPart.pushedClear(body: AssembledCharacter): PlacedPart {
    val covering = covering ?: return this
    val shell = surface.patches.firstOrNull() ?: return this
    val under = hungOver(body, covering)
    if (under.isEmpty()) return this
    val grids = under.flatMap { it.grids() }
    if (grids.isEmpty()) return this
    val own = PointGrid(gridPoints(this, shell, 0), shell.nu, shell.nv, closedV = true)
    val shift = HemPush.push(own, covering.hingeRing(shell.nu), grids, HANG_CLEARANCE) ?: return this
    val count = shell.nu * shell.nv
    return showing(surface.posed(surface.mesh.shifted { v -> if (v < count) shift[v] else Vec3.ZERO }), turn)
}

/** What hangs inside a branch with [covering] in [body]: the parts it hangs over, the parts on them, and other garments' branches over those. */
private fun hungOver(body: AssembledCharacter, covering: Covering): List<PlacedPart> {
    val ids = HashSet(covering.hangsOver)
    var grew = true
    while (grew) {
        grew = false
        for (part in body.parts) {
            val id = part.id ?: continue
            if (part.covering == null && part.socket?.parent in ids && ids.add(id)) grew = true
        }
    }
    return body.parts.filter { part ->
        val held = part.covering
        if (held == null) part.id in ids && part.hanging == null else held.garment != covering.garment && held.part in ids
    }
}

/** Each patch of this part with a grid of more than one ring, as its points stand in body space. */
private fun PlacedPart.grids(): List<PointGrid> {
    val out = ArrayList<PointGrid>()
    var first = 0
    for (patch in surface.patches) {
        if (patch.nu > 1 && patch.nv > 1) out += PointGrid(gridPoints(this, patch, first), patch.nu, patch.nv, patch.closedV)
        first += patch.nu * patch.nv
    }
    return out
}

/** The points of [patch], whose vertices start at [first] in [part]'s mesh, in body space. */
private fun gridPoints(part: PlacedPart, patch: Patch, first: Int): FloatArray {
    val mesh = part.surface.mesh
    val points = FloatArray(patch.nu * patch.nv * Patch.COMPONENTS)
    for (i in 0 until patch.nu * patch.nv) {
        val p = part.origin + mesh.position(first + i)
        points[i * 3] = p.x
        points[i * 3 + 1] = p.y
        points[i * 3 + 2] = p.z
    }
    return points
}

/** The middle of ring [ring] of [patch], this part's first, in body space. */
private fun PlacedPart.ringMiddle(patch: Patch, ring: Int): Vec3 {
    var sum = Vec3.ZERO
    for (c in 0 until patch.nv) sum += surface.shown(patch.point(patch.index(ring, c)))
    return origin + sum * (1f / patch.nv)
}
