package dev.chieppa.squash.character.surface

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.GarmentBranch
import dev.chieppa.squash.document.PartId

/** Where round a part a dropped seam reads the stand-off of the cloth above it: halfway from the front to the back. */
private const val SIDE = 0.5f

/**
 * How far [cover]'s garment stands off each part it covers, worn over [body] on the garments [under] it: its own ease there, standing off
 * each inner garment whose cloth goes the whole way round the part over any of the same rows by that garment's own ease.
 */
internal fun clothEases(cover: GarmentCover, body: AssembledCharacter, under: List<GarmentCover>): Map<PartId, ClothEase> =
    cover.branches.mapValues { (part, branch) ->
        val rows = cover.cloth[part]?.reach
        val inner = if (rows == null) emptyList() else under.mapNotNull { below ->
            val reach = below.cloth[part]?.reach
            val overlaps = reach != null && reach.allRound && reach.firstRow <= rows.lastRow && reach.lastRow >= rows.firstRow
            if (overlaps) ownEase(below, part, checkNotNull(below.branches[part]), body) else null
        }
        ownEase(cover, part, branch, body).over(inner)
    }

/**
 * [cover]'s own ease over [part], its [branch] naming a pair's leader: the garment's profile there, the seam it drops there eased from the
 * stand-off of the cloth over the part [part] grows from, at its socket, the bands of its areas that gather over the part and those that fold back over it.
 */
private fun ownEase(cover: GarmentCover, part: PartId, branch: GarmentBranch, body: AssembledCharacter): ClothEase {
    val garment = cover.garment
    val cloth = cover.cloth[part]
    val gathers = if (cloth == null) emptyList() else garment.areas.mapIndexedNotNull { i, area ->
        val ease = area.gather ?: return@mapIndexedNotNull null
        if (branch.part !in area.cells) return@mapIndexedNotNull null
        Gather(cloth, if (cloth.areaCount == 0) -1 else i, ease)
    }
    val folds = if (cloth == null) emptyList() else garment.areas.mapIndexedNotNull { i, area ->
        val thickness = area.fold?.takeIf { it > 0f } ?: return@mapIndexedNotNull null
        if (branch.part !in area.cells) return@mapIndexedNotNull null
        Fold(cloth, if (cloth.areaCount == 0) -1 else i, thickness)
    }
    val drop = garment.drops[branch.part]?.takeIf { it > 0f }?.let { u ->
        val placed = body.part(part)
        val above = placed?.socket?.parent?.let { cover.branches[it] }
            ?.let { garment.profileOf(it).at(placed.socketSpec?.u ?: 1f, SIDE) }
            ?: garment.profileOf(branch).at(0f, SIDE)
        Drop(u, above)
    }
    return ClothEase(garment.profileOf(branch), gathers, drop, folds = folds)
}
