package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.ClothOutline
import dev.chieppa.squash.document.ClothShape
import dev.chieppa.squash.document.Garment
import dev.chieppa.squash.document.GarmentAreaId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.cellsOfRuns
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** How far outside a painting shape, in cells, a cell's middle may lie and the cell still be painted, so a shape narrower than a cell holds cells. */
const val DRAWN_PAINT_REACH = 0.5f

/** What a shape's signed distance reads where nothing paints. */
private const val NOTHING = -1e6f

/** A distance this large or more is no shape's: what lies outside every shape's reach. */
private const val NOTHING_NEAR = 1e5f

/** How many samples a chart row and column [DrawnCloth.narrowest] reads along and round. */
private const val NARROW_ROWS_PER_ROW = 4

/**
 * One part's cloth drawn as vector shapes laid in order, each painting an area, as index into its garment's areas, or taking what it
 * holds from every area: the cloth is every painting shape's inside less what the later unpainting shapes take, and an area's its own
 * shapes' less what every later shape of another area or any unpainting shape takes. Read as signed distances, so their zero curves are
 * the shapes' outlines exactly, and as fields from 0 to 1 that are those of a blurred straight edge at the same distance, so they read as
 * a part's painted cells do (see [ClothField]). Equal for the same shapes.
 */
class DrawnCloth(private val shapes: List<ClothShape>, private val areaOf: List<Int>, private val areaCount: Int) {
    private val distances = shapes.map { ClothOutlineDistance(it.outline) }

    /** How far inside the whole cloth (u, v) lies, a unit of u [uScale] long and of v [vScale]. */
    fun whole(u: Float, v: Float, uScale: Float, vScale: Float): Float = level(-1, u, v, uScale, vScale)

    /** How far inside area [area]'s cloth (u, v) lies, measured as [whole] measures. */
    fun area(area: Int, u: Float, v: Float, uScale: Float, vScale: Float): Float = level(area, u, v, uScale, vScale)

    /** The whole cloth's field at (u, v), from 0 far outside to 1 deep inside, its half level on the outline. */
    fun at(u: Float, v: Float): Float = fieldOf(whole(u.coerceIn(0f, 1f), v, CHART_ROWS.toFloat(), CHART_COLUMNS.toFloat()))

    /** Area [area]'s field at (u, v), as [at] reads the whole cloth's. */
    fun areaAt(area: Int, u: Float, v: Float): Float = fieldOf(area(area, u.coerceIn(0f, 1f), v, CHART_ROWS.toFloat(), CHART_COLUMNS.toFloat()))

    /** How far inside the cloth (u, v) lies in body units, a unit of u [along] long and of v [around]. */
    fun inside(u: Float, v: Float, along: Float, around: Float): Float = whole(u.coerceIn(0f, 1f), v, along, around)

    /**
     * How wide, in body units, the narrowest piece of the cloth or gap between its pieces is, where a unit of u at u is [along] of it long
     * and of v [around] of it: twice the distance from the outline at each ridge of the distance, a place no neighbour on the same side
     * of the outline lies further from, read every [NARROW_ROWS_PER_ROW] of a row and column over the shapes' rows, so a strap narrower
     * than that spacing still has its ridge; null where it finds none.
     */
    fun narrowest(along: (Float) -> Float, around: (Float) -> Float): Float? {
        val rows = CHART_ROWS * NARROW_ROWS_PER_ROW
        val columns = CHART_COLUMNS * NARROW_ROWS_PER_ROW
        val from = (distances.minOf { it.lowU }.coerceIn(0f, 1f) * rows).toInt()
        val to = ((distances.maxOf { it.highU }.coerceIn(0f, 1f)) * rows).toInt().coerceAtMost(rows - 1)
        if (to - from < 2) return null
        val values = Array(to - from + 1) { i ->
            val u = (from + i + 0.5f) / rows
            val a = along(u)
            val r = around(u)
            FloatArray(columns) { j -> whole(u, (j + 0.5f) / columns, a, r) }
        }
        var least: Float? = null
        for (i in 1 until values.size - 1) for (j in 0 until columns) {
            val d = values[i][j]
            var ridge = true
            for (di in -1..1) for (dj in -1..1) {
                if (di == 0 && dj == 0) continue
                val n = values[i + di][(j + dj + columns) % columns]
                if ((n > 0f) == (d > 0f) && abs(n) > abs(d)) ridge = false
            }
            if (ridge && abs(d) < NOTHING_NEAR) least = minOf(least ?: Float.MAX_VALUE, 2f * abs(d))
        }
        return least
    }

    /** Each area's cells as the shapes lay them, cell by cell, as [drawnCells] gives them. */
    val cells: List<BooleanArray> by lazy { drawnCells(distances, shapes.map { it.unpaint }, areaOf, areaCount) }

    /** The level of area [area], or of the whole cloth where [area] is below 0, with each shape read only where it can change it. */
    private fun level(area: Int, u: Float, v: Float, uScale: Float, vScale: Float): Float {
        var d = NOTHING
        for (i in shapes.indices) {
            val adds = !shapes[i].unpaint && (area < 0 || areaOf[i] == area)
            val outside = distances[i].outsideBounds(u, v, uScale, vScale)
            if (adds) {
                if (d >= -outside) continue
                d = maxOf(d, distances[i].at(u, v, uScale, vScale))
            } else {
                if (d <= outside) continue
                d = minOf(d, -distances[i].at(u, v, uScale, vScale))
            }
        }
        return d
    }

    override fun equals(other: Any?): Boolean = other is DrawnCloth && other.shapes == shapes && other.areaOf == areaOf

    override fun hashCode(): Int = shapes.hashCode() * 31 + areaOf.hashCode()

    companion object {
        /**
         * [garment]'s shapes on [part], a pair's leader, as its cloth there, where the cells its areas hold of the part, [cells], are
         * exactly what those shapes lay; null where it has none there or the cells say otherwise.
         */
        fun of(garment: Garment, part: PartId, cells: List<BooleanArray>): DrawnCloth? = of(garment.shapes, garment.areas.map { it.id }, part, cells)

        /** [shapes] on [part] as its cloth, laid into the areas [ids], where [cells], each area's, are exactly what they lay; else null. */
        fun of(shapes: List<ClothShape>, ids: List<GarmentAreaId>, part: PartId, cells: List<BooleanArray>): DrawnCloth? {
            val shapes = shapes.filter { it.part == part }
            if (shapes.isEmpty() || cells.size != ids.size) return null
            val areaOf = shapes.map { ids.indexOf(it.area) }
            if (areaOf.any { it < 0 }) return null
            val drawn = DrawnCloth(shapes, areaOf, ids.size)
            return drawn.takeIf { drawn.cells.indices.all { k -> drawn.cells[k].contentEquals(cells[k]) } }
        }

        /** The cells each of [garment]'s areas holds of [part], from its runs. */
        fun cellsOf(garment: Garment, part: PartId): List<BooleanArray> =
            garment.areas.map { area -> area.cells[part]?.let(::cellsOfRuns) ?: BooleanArray(CHART_CELLS) }
    }
}

/**
 * The cells of a part's chart each of [areaCount] areas holds once [shapes] are laid in order, shape i painting area [areaOf] i or, where
 * [unpaint] i, taking what it holds from every area: a painting shape takes every cell whose middle lies within [DRAWN_PAINT_REACH] of
 * cells outside it, from every other area, and an unpainting shape every cell whose middle lies more than that inside it, so the cells
 * hold all the drawn cloth and a shape narrower than a cell still holds a cell across it.
 */
fun drawnCells(shapes: List<ClothOutlineDistance>, unpaint: List<Boolean>, areaOf: List<Int>, areaCount: Int): List<BooleanArray> {
    val owner = IntArray(CHART_CELLS) { -1 }
    for (i in shapes.indices) {
        val laid = strokeCells(shapes[i], unpaint[i])
        for (cell in 0 until CHART_CELLS) if (laid[cell]) owner[cell] = if (unpaint[i]) -1 else areaOf[i]
    }
    return List(areaCount) { k -> BooleanArray(CHART_CELLS) { owner[it] == k } }
}

/**
 * The cells of a part's chart one drawn stroke of [outline] lays, as [drawnCells] lays them: painting, those whose middles lie within
 * [DRAWN_PAINT_REACH] of a cell outside it; with [unpaint], those whose middles lie more than that inside it.
 */
fun drawnStrokeCells(outline: ClothOutline, unpaint: Boolean): BooleanArray = strokeCells(ClothOutlineDistance(outline), unpaint)

private fun strokeCells(shape: ClothOutlineDistance, unpaint: Boolean): BooleanArray {
    val rows = CHART_ROWS.toFloat()
    val columns = CHART_COLUMNS.toFloat()
    return BooleanArray(CHART_CELLS) { cell ->
        val u = (cell / CHART_COLUMNS + 0.5f) / CHART_ROWS
        val v = (cell % CHART_COLUMNS + 0.5f) / CHART_COLUMNS
        if (shape.outsideBounds(u, v, rows, columns) >= DRAWN_PAINT_REACH) {
            false
        } else {
            val d = shape.at(u, v, rows, columns)
            if (unpaint) d > DRAWN_PAINT_REACH else d > -DRAWN_PAINT_REACH
        }
    }
}

/** The field of a blurred straight edge [cells] cells inside it, blurred as painted cells are: the normal curve's share below [cells] over the blur. */
internal fun fieldOf(cells: Float): Float = normalShare(cells / CLOTH_BLUR_CELLS)

/** The share of a unit normal curve lying below [x]. */
private fun normalShare(x: Float): Float {
    val z = abs(x) / sqrt(2f)
    val t = 1f / (1f + ERF_P * z)
    val poly = t * (ERF_A1 + t * (ERF_A2 + t * (ERF_A3 + t * (ERF_A4 + t * ERF_A5))))
    val erf = 1f - poly * exp(-z * z)
    return if (x >= 0f) 0.5f + 0.5f * erf else 0.5f - 0.5f * erf
}

private const val ERF_P = 0.3275911f
private const val ERF_A1 = 0.254829592f
private const val ERF_A2 = -0.284496736f
private const val ERF_A3 = 1.421413741f
private const val ERF_A4 = -1.453152027f
private const val ERF_A5 = 1.061405429f
