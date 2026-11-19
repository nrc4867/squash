package dev.chieppa.squash.screens.dress

import dev.chieppa.squash.character.dress.PaintedGarment
import dev.chieppa.squash.character.dress.paintedOf
import dev.chieppa.squash.character.dress.shapeCommands
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.character.shape.repainted
import dev.chieppa.squash.character.shape.rowAt
import dev.chieppa.squash.character.shape.withoutEdge
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.DocumentCommand
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.SurfaceCurve

/** This garment with area [area] given [mask], its cells taken from every other area. */
private fun PaintedGarment.withArea(area: Int, mask: SurfaceMask): PaintedGarment =
    with(areas = areas.mapIndexed { i, each -> if (i == area) each.with(mask) else each.with(each.mask - mask) })

/** The curves of area [area]'s edges, with the garment's edge [index] as [curve], or left out where [curve] is null. */
private fun PaintedGarment.areaCurves(area: Int, index: Int, curve: SurfaceCurve?): List<SurfaceCurve> =
    edges.withIndex().filter { it.value.area == area }.mapNotNull { (i, on) -> if (i == index) curve else on.edge.curve }

/**
 * The garment [held] with its edge [index], drawn as [from], drawn as [curve] instead: the cells either side of where the curve moved
 * repainted, as the area's edges with the curve moved cut them (see [repainted]), taking the cells they reach from every other area.
 * Unchanged for an edge it lacks.
 */
fun redrawnEdge(held: PaintedGarment, index: Int, from: SurfaceCurve, curve: SurfaceCurve): PaintedGarment {
    val on = held.edges.getOrNull(index) ?: return held
    val mask = repainted(held.areas[on.area].mask, held.areaCurves(on.area, index, from), held.areaCurves(on.area, index, curve))
    return held.withArea(on.area, mask)
}

/** The command that draws [garment]'s edge [index], drawn as [from], as [curve] (see [redrawnEdge]). */
fun Character.edgeMovedCommand(body: AssembledCharacter, garment: Garment, held: PaintedGarment, index: Int, from: SurfaceCurve, curve: SurfaceCurve): DocumentCommand =
    shapeCommands(this, body, garment, redrawnEdge(held, index, from, curve))

/** The command that takes [garment]'s edge [index] away: the cells on its far side join its area (see [withoutEdge]); null for an edge it lacks. */
fun Character.eraseEdgeCommand(body: AssembledCharacter, garment: Garment, index: Int): DocumentCommand? {
    val held = paintedOf(this, body, garment)
    val on = held.edges.getOrNull(index) ?: return null
    val mask = withoutEdge(held.areas[on.area].mask, on.edge.curve, held.areaCurves(on.area, -1, null))
    return shapeCommands(this, body, garment, held.withArea(on.area, mask))
}

/**
 * The command that makes [garment]'s edge [index] a level ring round its part, at the middle of where it runs along it: across the rows
 * it spans, each column's cells on the ring's near side take what most of that side was, and the far side's likewise. Null for an edge
 * it lacks or one already level.
 */
fun Character.straightenCommand(body: AssembledCharacter, garment: Garment, index: Int): DocumentCommand? {
    val held = paintedOf(this, body, garment)
    val on = held.edges.getOrNull(index) ?: return null
    val part = on.edge.part
    val rows = on.edge.curve.points.map { rowAt(it.u) }
    val low = rows.min()
    val high = rows.max()
    if (low == high) return null
    val level = (on.edge.curve.points.sumOf { it.u.toDouble() } / rows.size * CHART_ROWS).toInt().coerceIn(0, CHART_ROWS)
    val mask = held.areas[on.area].mask
    fun side(from: Int, to: Int): Boolean {
        var count = 0
        var total = 0
        for (row in from until to) for (column in 0 until CHART_COLUMNS) {
            total++
            if (mask.held(part, row * CHART_COLUMNS + column)) count++
        }
        return count * 2 > total
    }
    val near = side(low, level)
    val far = side(level, high + 1)
    if (near == far) return null
    val next = SurfaceMask.build(held.chart) { p, cell ->
        val row = cell / CHART_COLUMNS
        if (p != part || row < low || row > high) mask.held(p, cell) else if (row < level) near else far
    }
    if (next == mask) return null
    return shapeCommands(this, body, garment, held.withArea(on.area, next))
}
