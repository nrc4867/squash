package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3

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

/** The middle of ring [ring] of [patch], this part's first, in body space. */
private fun PlacedPart.ringMiddle(patch: Patch, ring: Int): Vec3 {
    var sum = Vec3.ZERO
    for (c in 0 until patch.nv) sum += surface.shown(patch.point(patch.index(ring, c)))
    return origin + sum * (1f / patch.nv)
}
