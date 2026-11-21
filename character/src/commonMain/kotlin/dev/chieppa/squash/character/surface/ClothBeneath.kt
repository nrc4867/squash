package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS

/** How many cells round an area's cells they run on under the shapes laid after them, so its edge there is theirs. */
private const val BENEATH_REACH = 2

/** [cells], a part's chart, grown by [reach] cells along and round, Chebyshev, rows held at the part's ends and columns wrapping. */
internal fun grownCells(cells: BooleanArray, reach: Int): BooleanArray = BooleanArray(CHART_CELLS) { cell ->
    val r = cell / CHART_COLUMNS
    val c = cell % CHART_COLUMNS
    (-reach..reach).any { dr ->
        val rr = r + dr
        rr in 0 until CHART_ROWS && (-reach..reach).any { dc -> cells[rr * CHART_COLUMNS + ((c + dc) % CHART_COLUMNS + CHART_COLUMNS) % CHART_COLUMNS] }
    }
}

/**
 * A part's areas read from their cells under the shapes laid after them: each area's cells run on under the cells the shapes of later
 * areas touch within [BENEATH_REACH] cells of it, then what those shapes lay takes from it, so where a collar's shape lies over a body
 * painted as cells the seam between them is the shape's outline, and the body's own edges elsewhere, an opening's, are its cells'. An area
 * no shape paints is read so everywhere; one a shape paints, where its cells are painted otherwise than its shapes lay.
 */
class ClothBeneath private constructor(private val cells: List<BooleanArray>, private val drawn: DrawnCloth) {
    private val shaped = cells.indices.map { drawn.shapes(it) }

    private val areas: List<ClothField?> by lazy {
        cells.mapIndexed { k, area ->
            if (area.none { it }) return@mapIndexed null
            val near = grownCells(area, BENEATH_REACH)
            val later = drawn.touchedAfter(k)
            ClothField.of(listOf(BooleanArray(CHART_CELLS) { cell -> area[cell] || (near[cell] && later[cell]) }))
        }
    }

    private val whole: ClothField? by lazy {
        val union = BooleanArray(CHART_CELLS) { cell -> cells.indices.any { k -> !shaped[k] && cells[k][cell] } }
        ClothField.of(listOf(union))
    }

    /** Whether some area no shape paints holds cells. */
    val holdsCells: Boolean by lazy { cells.indices.any { k -> !shaped[k] && cells[k].any { it } } }

    /** Area [area]'s field at (u, v) from its cells, less what the shapes of later areas lay over it. */
    fun cellAreaAt(area: Int, u: Float, v: Float): Float {
        val field = areas[area] ?: return 0f
        return minOf(field.at(u, v), 1f - drawn.takenAfter(area, u, v))
    }

    /** Area [area]'s field at (u, v) where no shape paints it, as [cellAreaAt] reads it; null where a shape paints it. */
    fun areaAt(area: Int, u: Float, v: Float): Float? = if (shaped.getOrElse(area) { true }) null else cellAreaAt(area, u, v)

    /** The field at (u, v) of the cloth the areas no shape paints lay together, less what the unpainting shapes take. */
    fun at(u: Float, v: Float): Float = whole?.let { minOf(it.at(u, v), 1f - drawn.takenAt(u, v, unpaintingOnly = true)) } ?: 0f

    /** How far inside that cloth (u, v) lies in body units, a unit of u [along] long and of v [around]. */
    fun inside(u: Float, v: Float, along: Float, around: Float): Float =
        whole?.let { minOf(it.inside(u, v, along, around), -drawn.unpaintedInside(u, v, along, around)) } ?: -Float.MAX_VALUE

    companion object {
        /** [cells], each area's, under [drawn]'s shapes. */
        fun of(cells: List<BooleanArray>, drawn: DrawnCloth): ClothBeneath = ClothBeneath(cells, drawn)
    }
}
