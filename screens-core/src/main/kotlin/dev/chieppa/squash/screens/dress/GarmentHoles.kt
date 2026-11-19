package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentHole
import dev.chieppa.squash.document.PartId

/**
 * The command a Hole press on [at]'s part sends where the part meets [garment]'s cloth, the cloth holding the cell of its parent it sits
 * on or any of its own: a hole round its root, which lets it out with no cuff; with a hole already there, a cuff band round it while
 * [cuffOnSecondPress]. Null where it does not meet the cloth, or there is nothing more to add.
 */
fun Character.holeCommand(body: AssembledCharacter, garment: Garment, at: Anchor, cuffOnSecondPress: Boolean): DocumentCommand? {
    val held = paintedOf(this, body, garment)
    val part = held.chart.part(at.part) ?: return null
    val parent = part.parent ?: return null
    val all = held.areas.fold(SurfaceMask.empty(held.chart)) { mask, area -> mask + area.mask }
    val meets = all.held(parent, held.chart.socketCell(part)) || all.count(part.id) > 0
    val hole = held.holes.firstOrNull { it.part == part.id }
    val holes = when {
        hole == null -> if (meets) held.holes + GarmentHole(part.id) else return null
        !hole.cuff && cuffOnSecondPress -> held.holes.map { if (it.part == part.id) it.copy(cuff = true) else it }
        else -> return null
    }
    return shapeCommands(this, body, garment, held.with(holes = holes))
}

/** The command that turns the cuff of [garment]'s hole round [part] on or off; null where it has no hole there. */
fun Character.holeCuffCommand(body: AssembledCharacter, garment: Garment, part: PartId, cuff: Boolean): DocumentCommand? {
    val held = paintedOf(this, body, garment)
    if (held.holes.none { it.part == part }) return null
    return shapeCommands(this, body, garment, held.with(holes = held.holes.map { if (it.part == part) it.copy(cuff = cuff) else it }))
}

/**
 * The command that closes [garment]'s hole round [part] over: the hole goes, and the part is painted into the area holding the cell of
 * its parent it sits on, or the first area, where no area holds any of it; null where it has no hole there.
 */
fun Character.closeHoleCommand(body: AssembledCharacter, garment: Garment, part: PartId): DocumentCommand? {
    val held = paintedOf(this, body, garment)
    if (held.holes.none { it.part == part }) return null
    val holes = held.holes.filter { it.part != part }
    val chartPart = held.chart.part(part)
    val covered = held.areas.any { it.mask.count(part) > 0 }
    if (chartPart == null || covered || held.areas.isEmpty()) return shapeCommands(this, body, garment, held.with(holes = holes))
    val socket = chartPart.parent?.let { parent -> held.areas.indexOfFirst { it.mask.held(parent, held.chart.socketCell(chartPart)) } } ?: -1
    val into = if (socket >= 0) socket else 0
    val whole = SurfaceMask.build(held.chart) { p, cell -> p == chartPart.id && cell < CHART_CELLS }
    return shapeCommands(this, body, garment, held.with(areas = held.areas.mapIndexed { i, area -> if (i == into) area.with(area.mask + whole) else area }, holes = holes))
}
