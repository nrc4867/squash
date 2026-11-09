package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.DEFAULT_EASE
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side

/**
 * [branches], one garment's, with each joined to the branch over the part its own part sits on: the child branch moves onto a socket
 * fillet [garment]'s fillet wide, as a share of its length along its part, the parent branch stays still, and the child branch's
 * faces inside the parent branch are left out.
 */
internal fun joined(garment: Garment, branches: List<PlacedPart>, body: AssembledCharacter, checkCancelled: () -> Unit): List<PlacedPart> {
    val shown = branches.toMutableList()
    for (i in shown.indices) {
        val covered = body.part(shown[i].covering?.part ?: continue) ?: continue
        val parentPart = covered.socket?.parent ?: continue
        val p = shown.indexOfFirst { it.covering?.part == parentPart }
        if (p < 0) continue
        checkCancelled()
        val radius = garment.fillet * shown[i].surface.axisLength
        filleted(shown[i], shown[p], radius, checkCancelled)?.let { (child, parent) ->
            shown[i] = child
            shown[p] = parent
        }
        if (shown[p].covering?.allRound == true) {
            val inside = shown[p].shellInside()
            shown[i] = shown[i].hiding { _, _, point -> point in inside }
        }
    }
    return shown
}

/**
 * [branch], a branch of [garment], with a hole where each body part comes out through it, and the cuff round each such part that has
 * one. A part comes out through it when it sits on a socket inside the region [branch] covers and [garment] does not hide it; the hole
 * is the branch's faces inside that part, and the cuff runs from the part's root past where it comes out by its cuff size, as a share.
 */
internal fun throughHoles(garment: Garment, branch: PlacedPart, body: AssembledCharacter, twins: Map<PartId, PartId>, checkCancelled: () -> Unit): Pair<PlacedPart, List<PlacedPart>> {
    val covering = requireNotNull(branch.covering)
    val parent = body.part(covering.part) ?: return branch to emptyList()
    val through = body.parts.filter { part ->
        val id = part.id ?: return@filter false
        if (part.covering != null || part.socket?.parent != covering.part) return@filter false
        val spec = part.socketSpec ?: return@filter false
        val v = if (part.side == Side.RIGHT && !parent.mirrored) 1f - spec.v else spec.v
        val change = garment.changes[id] ?: twins[id]?.let { garment.changes[it] }
        change != BodyPartChange.Hide && spec.u in covering.from..covering.to && coverOf(branch).covers(covering.part, spec.u, v, part.origin)
    }
    val ease = (garment.branch(covering.part) ?: twins[covering.part]?.let(garment::branch))?.ease ?: DEFAULT_EASE
    var holed = branch
    val cuffs = through.mapNotNull { part ->
        checkCancelled()
        bodyInside(part)?.let { inside -> holed = holed.hiding { _, _, point -> point in inside } }
        val id = requireNotNull(part.id)
        val change = (garment.changes[id] ?: twins[id]?.let { garment.changes[it] }) as? BodyPartChange.Through ?: BodyPartChange.Through()
        if (!change.cuff) null else cuffOver(garment.id, part, exitOf(part, branch) * (1f + change.cuffSize), ease)
    }
    return holed to cuffs
}

/** How far along [part], in u, its surface comes out of [branch]: the u of its first ring whose middle lies outside it, or 0. */
private fun exitOf(part: PlacedPart, branch: PlacedPart): Float {
    val slices = part.bodyPatch()?.slices ?: return 0f
    val inside = branch.shellInside()
    val first = slices.indexOfFirst { slice -> part.origin + part.surface.shown(slice.frame.center) !in inside }
    return if (first <= 0) 0f else slices[first].u
}
