package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.PartId

/**
 * This garment as worn over [body] in pieces that meet nowhere, each a cover of its own: parts it covers are in one piece when one sits
 * on the other, however far up, through parts it covers, so a pair of boots is two pieces and a sweater over a torso and its legs one.
 * Each piece is meshed apart, so two pieces touching at rest never join into one surface that a pose would tear open. Each piece keeps
 * the garment's holes; [WornCover.piece] names it by the part at its root.
 */
fun WornCover.pieces(body: AssembledCharacter): List<WornCover> {
    val covered = branches.keys
    fun rootOf(part: PartId): PartId {
        var at = part
        while (true) {
            val parent = body.part(at)?.socket?.parent ?: return at
            if (parent !in covered) return at
            at = parent
        }
    }
    val roots = covered.groupBy(::rootOf)
    if (roots.size < 2) return listOf(this)
    return roots.map { (root, parts) ->
        val kept = parts.toSet()
        WornCover(cover, branches.filterKeys { it in kept }, cuffs.filterTo(HashSet()) { it in kept }, holes, shells.filterKeys { it in kept }, root, openEnds.filterKeys { it in kept })
    }
}
