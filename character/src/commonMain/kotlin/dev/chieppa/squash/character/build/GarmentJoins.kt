package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.DEFAULT_EASE
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.Side

/** A branch with its holes cut, the [cuffs] round what comes out through them, and the body parts that come [through]. */
internal class Holed(val branch: PlacedPart, val cuffs: List<PlacedPart>, val through: List<PartId>)

/**
 * [branch], a branch of [garment], with a hole where each body part comes out through it, and the cuff round each such part that has
 * one. A part comes out through it when it sits on a socket inside the region [branch] covers and [garment] neither hides it nor
 * covers it with a branch of its own, which joins [branch] by its fillet alone, no garment owns it, and it does not hang inside [branch] out through its
 * free end, which [branch] then [Covering.hangsOver] (see [hangingInside]); the hole
 * is the branch's faces inside that part, and the cuff runs from the part's root to its cuff size, as a share of the part's length, past
 * where it comes out. A cuff inks its far edge and where it comes out of [branch] when the garment's cuffs and its own change are inked;
 * with no cuff, [branch] inks where the part comes through it when its hem is inked.
 */
internal fun throughHoles(garment: Garment, branch: PlacedPart, body: AssembledCharacter, twins: Map<PartId, PartId>, checkCancelled: () -> Unit): Holed {
    val covering = requireNotNull(branch.covering)
    val parent = body.part(covering.part) ?: return Holed(branch, emptyList(), emptyList())
    val keeps = { part: PlacedPart ->
        val id = requireNotNull(part.id)
        val change = garment.changes[id] ?: twins[id]?.let { garment.changes[it] }
        val ownBranch = garment.branch(id) != null || twins[id]?.let(garment::branch) != null
        ownBranch || change == BodyPartChange.Hide
    }
    val hanging = hangingInside(branch, body) { part -> part.id == null || keeps(part) }
    val through = body.parts.filter { part ->
        val id = part.id ?: return@filter false
        if (part.covering != null || part.owner != null || part.socket?.parent != covering.part || id in hanging) return@filter false
        val spec = part.socketSpec ?: return@filter false
        val v = if (part.side == Side.RIGHT && !parent.mirrored) 1f - spec.v else spec.v
        !keeps(part) && spec.u in covering.from..covering.to && coverOf(branch).covers(covering.part, spec.u, v, part.origin)
    }
    val ease = (garment.branch(covering.part) ?: twins[covering.part]?.let(garment::branch))?.let(garment::easeOf) ?: DEFAULT_EASE
    var holed = if (hanging.isEmpty()) branch else branch.covered(covering.hangingOver(hanging))
    val cuffs = through.mapNotNull { part ->
        checkCancelled()
        insideHider(part, whole = true)?.let { holed = holed.hiddenBy(listOf(it)) }
        val id = requireNotNull(part.id)
        val change = (garment.changes[id] ?: twins[id]?.let { garment.changes[it] }) as? BodyPartChange.Through ?: BodyPartChange.Through()
        if (!change.cuff) {
            if (garment.inkHem) holed = holed.inking(GarmentInk(crossings = setOf(id)))
            return@mapNotNull null
        }
        val inked = garment.inkHem && change.inkCuff
        cuffOver(garment.id, part, exitOf(part, branch) + change.cuffSize, ease)
            ?.let { cuff -> if (inked) cuff.inking(GarmentInk(hem = true, crossings = setOfNotNull(branch.id))) else cuff }
    }
    return Holed(holed, cuffs, through.mapNotNull { it.id })
}

/** How far along [part], in u, its surface comes out of [branch]: the u of its first ring whose middle lies outside it, or 0. */
private fun exitOf(part: PlacedPart, branch: PlacedPart): Float {
    val slices = part.bodyPatch()?.slices ?: return 0f
    val inside = insideOf(branch) ?: return 0f
    val first = slices.indexOfFirst { slice -> part.origin + part.surface.shown(slice.frame.center) !in inside }
    return if (first <= 0) 0f else slices[first].u
}
