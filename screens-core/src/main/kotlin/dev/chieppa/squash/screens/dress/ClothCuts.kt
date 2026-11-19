package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.character.surface.holds
import dev.chieppa.squash.document.BodyPartChange
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.Garment

/**
 * The painted cells of [painted], [garment]'s, an opening will cut away, worn on [body]: round each part that comes out of the cloth, a
 * part its cloth leaves out and the garment does not hide, on a socket inside the region its parent's cells paint (see [holds]), as a
 * hole through its wall or an open end, the cells of the parent within the part's root ring, which the part's way out takes. Paint
 * there is not cloth.
 */
fun cutCells(character: Character, body: AssembledCharacter, garment: Garment, painted: PaintedGarment): SurfaceMask {
    val chart = painted.chart
    val hidden = garment.changes.filterValues { it == BodyPartChange.Hide }.keys.mapTo(HashSet()) { chart.leaderOf(it) }
    val places = chartPlaces(character, body)
    var cut = SurfaceMask.empty(chart)
    for (part in chart.parts) {
        val parent = part.parent ?: continue
        if (part.id in hidden || (painted.reachOf(part.id) != null && part.id !in painted.holed)) continue
        val reach = painted.reachOf(parent) ?: continue
        if (!reach.holds(part.socketU, part.socketV)) continue
        val around = places.rings(part.id, 0).fold(SurfaceMask.empty(chart)) { held, (middle, radius) -> held + places.within(middle, radius) }
        cut += SurfaceMask.build(chart) { p, cell -> p == chart.leaderOf(parent) && around.held(p, cell) }
    }
    return cut.within(painted.cloth)
}

/** [cutCells] for the last garment and body asked for, kept while they are the same. */
fun cutCellsOf(character: Character, body: AssembledCharacter, garment: Garment, painted: PaintedGarment): SurfaceMask = CutMemo.of(character, body, garment, painted)

private object CutMemo {
    private var key: Triple<PaintedGarment, AssembledCharacter, Garment>? = null
    private var held: SurfaceMask? = null

    @Synchronized
    fun of(character: Character, body: AssembledCharacter, garment: Garment, painted: PaintedGarment): SurfaceMask {
        val last = held
        val now = key
        if (last != null && now != null && now.first === painted && now.second === body && now.third == garment) return last
        return cutCells(character, body, garment, painted).also {
            key = Triple(painted, body, garment)
            held = it
        }
    }
}
