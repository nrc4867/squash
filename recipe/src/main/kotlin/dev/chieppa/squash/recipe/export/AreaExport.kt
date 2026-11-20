package dev.chieppa.squash.recipe.export

import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentArea
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.cellsOfRuns
import dev.chieppa.squash.recipe.model.RecipeArea
import dev.chieppa.squash.recipe.model.RecipeHole
import dev.chieppa.squash.recipe.model.RecipeNet
import dev.chieppa.squash.recipe.model.RecipeStroke
import dev.chieppa.squash.recipe.model.RecipeStrokeShape
import dev.chieppa.squash.recipe.model.RecipeTuck

/** Whether [garment]'s cloth is what its covers' boxes paint, in one area with no name, colour or see-through of its own, holed and tucked nowhere. */
internal fun paintsItsBoxes(garment: Garment): Boolean {
    if (garment.holes.isNotEmpty() || garment.tucks.isNotEmpty()) return false
    if (garment.areas.size > 1 || garment.areas.any { it.name.isNotEmpty() || it.color != null || it.opacity != null || it.net != null }) return false
    val boxes = garment.copy(areas = emptyList()).withBoxesPainted()
    return boxes.areas.map { it.cells } == garment.areas.map { it.cells }
}

/** [area] as a recipe area painting exactly its cells: a box a run of columns, rows with the same runs taken together. */
internal fun areaOf(area: GarmentArea): RecipeArea =
    RecipeArea(
        area.id.value, area.name, area.color?.value, area.gather, area.fold, area.opacity, area.net?.let { RecipeNet(it.pattern, it.hole, it.thread, it.angle) },
        area.cells.flatMap { (part, runs) -> boxesOf(part, cellsOfRuns(runs)) },
    )

internal fun holesOf(garment: Garment): List<RecipeHole> = garment.holes.map { RecipeHole(it.part.value, it.cuff) }

internal fun tucksOf(garment: Garment): List<RecipeTuck> = garment.tucks.map { RecipeTuck(it.part.value, it.kind, it.tucked) }

/** The held cells of [part]'s chart as boxes whose cell middles are exactly those cells. */
private fun boxesOf(part: PartId, held: BooleanArray): List<RecipeStroke> {
    val out = ArrayList<RecipeStroke>()
    var row = 0
    while (row < CHART_ROWS) {
        val runs = columnRuns(held, row)
        var last = row
        while (last + 1 < CHART_ROWS && columnRuns(held, last + 1) == runs) last++
        for ((first, count) in runs) {
            val v = if (count == CHART_COLUMNS) null else listOf(first.toFloat() / CHART_COLUMNS, (first + count).toFloat() / CHART_COLUMNS)
            out += RecipeStroke(part.value, RecipeStrokeShape.BOX, u = listOf(row.toFloat() / CHART_ROWS, (last + 1f) / CHART_ROWS), v = v)
        }
        row = last + 1
    }
    return out
}

/** The runs of held columns round [row], each its first column and length; a run across v 0 starts below 0. */
private fun columnRuns(held: BooleanArray, row: Int): List<Pair<Int, Int>> {
    val at = { column: Int -> held[row * CHART_COLUMNS + ((column % CHART_COLUMNS) + CHART_COLUMNS) % CHART_COLUMNS] }
    if ((0 until CHART_COLUMNS).all(at)) return listOf(0 to CHART_COLUMNS)
    val start = (0 until CHART_COLUMNS).first { !at(it) }
    val out = ArrayList<Pair<Int, Int>>()
    var column = start
    while (column < start + CHART_COLUMNS) {
        if (!at(column)) {
            column++
            continue
        }
        val first = column
        while (column < start + CHART_COLUMNS && at(column)) column++
        val wrapped = if (first >= CHART_COLUMNS) first - CHART_COLUMNS else first
        out += (if (wrapped + (column - first) > CHART_COLUMNS) wrapped - CHART_COLUMNS else wrapped) to (column - first)
    }
    return out.sortedBy { it.first }
}
