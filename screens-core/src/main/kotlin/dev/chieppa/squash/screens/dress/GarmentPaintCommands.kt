package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentAreaId
import dev.chieppa.squash.document.PartId

/** How many rounds of smoothing Clean edges at its fullest gives a stroke's edge. */
const val MOST_CLEAN_ROUNDS = 4

/** How far round a stroke, in cells, Clean edges smooths the edge. */
private const val CLEAN_REACH = 2

/** How Paint lays a stroke: [erase] to take cloth away, Clean edges at [clean] from 0 to 1, and on both sides of the body's middle on [mirrored] parts. */
class PaintStyle(val erase: Boolean = false, val clean: Float = 0f, val mirrored: Set<PartId> = emptySet())

/** The area [area] of [painted], or a new one first in line when it has none of that id. */
private fun PaintedGarment.areaOrNew(area: GarmentAreaId?): Pair<List<PaintedArea>, Int> {
    val at = areas.indexOfFirst { it.id == area }
    if (at >= 0) return areas to at
    if (area == null && areas.isNotEmpty()) return areas to 0
    val id = area ?: freshArea(this)
    return areas + PaintedArea(id, "", SurfaceMask.empty(chart)) to areas.size
}

/** An area id [painted] does not use. */
fun freshArea(painted: PaintedGarment): GarmentAreaId =
    GarmentAreaId(freshNumbered(NEW_AREA, painted.areas.map { it.id.value }.toSet()).first)

/**
 * [painted] with [stroke], cells of its chart, laid in [area] in [style]: painted cells join the area and leave every other, unpainted
 * ones leave every area; the edge is cleaned round the stroke. Painting into an area it lacks makes it.
 */
fun painted(painted: PaintedGarment, area: GarmentAreaId?, stroke: SurfaceMask, style: PaintStyle): PaintedGarment {
    val laid = if (style.mirrored.isEmpty()) stroke else stroke.mirrored(style.mirrored)
    val rounds = (style.clean * MOST_CLEAN_ROUNDS + 0.5f).toInt()
    val near = laid.grown(CLEAN_REACH)
    val clean = { mask: SurfaceMask -> if (rounds == 0) mask else mask.cleaned(rounds, near) }
    if (style.erase) return painted.with(areas = painted.areas.map { it.with(clean(it.mask - laid)) })
    val (areas, at) = painted.areaOrNew(area)
    val grown = clean(areas[at].mask + laid)
    return painted.with(areas = areas.mapIndexed { i, each -> if (i == at) each.with(grown) else each.with(each.mask - grown) })
}

/** The command that lays [stroke] on [garment] worn over [body] (see [painted]), its shape written again, one step. */
fun Character.paintCommand(body: AssembledCharacter, garment: Garment, area: GarmentAreaId?, stroke: SurfaceMask, style: PaintStyle): DocumentCommand =
    shapeCommands(this, body, garment, painted(paintedOf(this, body, garment), area, stroke, style))

/** The command that grows [garment]'s area [area] by a cell all round, into no other area, or shrinks it with [shrink]; null with no such area. */
fun Character.growCommand(body: AssembledCharacter, garment: Garment, area: GarmentAreaId?, shrink: Boolean): DocumentCommand? {
    val held = paintedOf(this, body, garment)
    val at = held.areas.indexOfFirst { it.id == area }.takeIf { it >= 0 } ?: if (area == null && held.areas.isNotEmpty()) 0 else return null
    val others = held.areas.filterIndexed { i, _ -> i != at }.fold(SurfaceMask.empty(held.chart)) { mask, each -> mask + each.mask }
    val mask = held.areas[at].mask
    val next = if (shrink) mask.shrunk() else mask.grown() - others
    return shapeCommands(this, body, garment, held.with(areas = held.areas.mapIndexed { i, each -> if (i == at) each.with(next) else each }))
}

/** The command that adds a new empty area to [garment], with its id; the cloth read for it saved as its first area. */
fun Character.newAreaCommand(body: AssembledCharacter, garment: Garment): Pair<DocumentCommand, GarmentAreaId> {
    val held = paintedOf(this, body, garment)
    val id = freshArea(held)
    return shapeCommands(this, body, garment, held.with(areas = held.areas + PaintedArea(id, "", SurfaceMask.empty(held.chart)))) to id
}

/** The command that takes [garment]'s area [area] away, with its cloth. */
fun Character.removeAreaCommand(body: AssembledCharacter, garment: Garment, area: GarmentAreaId): DocumentCommand {
    val held = paintedOf(this, body, garment)
    return shapeCommands(this, body, garment, held.with(areas = held.areas.filter { it.id != area }))
}
