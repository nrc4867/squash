package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.runsOfCells
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sqrt

/** How wide the blur that turns painted cells into a smooth field is, in cells: wide enough to take the steps out of a slanted edge. */
const val CLOTH_BLUR_CELLS = 0.8f

/** The level of a cloth field its hem lies on. */
const val CLOTH_HEM_LEVEL = 0.5f

/** How far either side of a cell the blur reads, in its widths. */
private const val BLUR_WIDTHS = 3f

/** The least slope the field is read at, per cell, below which its distance is read from the cells' edges alone. */
private const val LEAST_SLOPE = 1e-4f

/** How far from its hem level, as a share of the field's range, the field's own distance is read wholly; past it the cells' edges blend in. */
private const val NEAR_LEVEL = 0.35f

/** How far past [NEAR_LEVEL] the cells' edges take over wholly. */
private const val BLEND_LEVEL = 0.1f

/** How far a cloth with no edge reads inside or outside, in body units: past any reach a coverage reads. */
private const val NO_EDGE = 1e6f

/**
 * One part's painted cloth as a smooth field over its first run's (u, v), read from its chart of [CHART_ROWS] by [CHART_COLUMNS]
 * cells: each cell any area paints held as 1 and the rest 0, blurred by [CLOTH_BLUR_CELLS] cells along and round, rows held at the
 * part's ends and columns wrapping round, so its [CLOTH_HEM_LEVEL] is a smooth curve along the painted cells' edge with no steps. Each
 * area of a garment of several is blurred on its own too, for the seams where areas meet ([areaAt]); all areas are blurred together
 * for the cloth, so two areas that touch leave no gap between them. Equal for the same cells painted by the same areas. An area the
 * garment's shapes paint, [drawn], is read from the shapes instead, so its edge is their outline however fine, less what cell strokes
 * took and with what they added ([CellEdits]); areas no shape paints lie beneath the shapes ([ClothBeneath]); a union with drawn cloth in
 * it reads each of its cloths and takes the most.
 */
class ClothField private constructor(
    private val whole: FloatArray,
    private val areas: List<FloatArray?>,
    private val key: Any,
    /** Where its painted cells lie on the part's chart. */
    val reach: ClothReach,
    private val held: BooleanArray,
    private val drawn: DrawnCloth? = null,
    private val joined: List<ClothField>? = null,
    private val edits: CellEdits? = null,
    private val beneath: ClothBeneath? = null,
    private val areaEdits: List<CellEdits?>? = null,
) {
    /** For each cell, the nearest middle of a side between a painted and an unpainted cell, as row and column in cells; empty with none. */
    private val nearest: FloatArray by lazy { nearestEdges(held) }

    /** Whether its edge is read from drawn shapes, its own or a cloth's it joins. */
    val isDrawn: Boolean get() = drawn != null || joined?.any { it.isDrawn } == true

    /** Whether every cell of the part is painted. */
    val paintsAll: Boolean get() = held.all { it }

    /** How many areas' fields it holds: one for each area of its garment where the garment has several, else none. */
    val areaCount: Int get() = areas.size

    /**
     * How far inside the cloth (u, v) lies, in body units, where a unit of u is [along] long and a unit of v [around]: near its hem the
     * distance a blurred straight edge gives for the field's value ([CLOTH_BLUR_CELLS] cells times its probit, along its slope), so the
     * hem is the smooth half level; further off the distance to the painted cells' edge, the two blended from [NEAR_LEVEL] of the field's
     * range off its hem level to [BLEND_LEVEL] past that.
     */
    fun inside(u: Float, v: Float, along: Float, around: Float): Float {
        joined?.let { fields -> return fields.maxOf { it.inside(u, v, along, around) } }
        val drawn = drawn ?: return cellInside(u, v, along, around)
        val shaped = drawn.inside(u, v, along, around)
        val laid = beneath?.takeIf { it.holdsCells }?.let { maxOf(shaped, it.inside(u, v, along, around)) } ?: shaped
        return edits?.inside(laid, u, v, along, around) ?: laid
    }

    /** [inside] read from the painted cells alone. */
    private fun cellInside(u: Float, v: Float, along: Float, around: Float): Float {
        val x = rowOf(u)
        val y = columnOf(v)
        val value = sample(whole, x, y)
        val level = value - CLOTH_HEM_LEVEL
        val rowLength = along / CHART_ROWS
        val columnLength = around / CHART_COLUMNS
        val edge = edgeDistance(x, y, rowLength, columnLength).let { if (level < 0f) -it else it }
        val off = abs(level)
        if (off >= NEAR_LEVEL + BLEND_LEVEL) return edge
        val gx = sample(whole, x + 0.5f, y) - sample(whole, x - 0.5f, y)
        val gy = sample(whole, x, y + 0.5f) - sample(whole, x, y - 0.5f)
        val cells = sqrt(gx * gx + gy * gy)
        if (cells < LEAST_SLOPE) return edge
        val bx = gx / maxOf(rowLength, LEAST_SLOPE)
        val by = gy / maxOf(columnLength, LEAST_SLOPE)
        val near = CLOTH_BLUR_CELLS * probit(value) * cells / sqrt(bx * bx + by * by)
        if (off <= NEAR_LEVEL) return near
        return near + (edge - near) * (off - NEAR_LEVEL) / BLEND_LEVEL
    }

    /** How far, in body units, the place at row [x] and column [y] lies from the nearest edge the cells about it hold. */
    private fun edgeDistance(x: Float, y: Float, rowLength: Float, columnLength: Float): Float {
        if (nearest.isEmpty()) return NO_EDGE
        val row = x.coerceIn(0f, (CHART_ROWS - 1).toFloat())
        val r0 = (row + 0.5f).toInt().coerceIn(0, CHART_ROWS - 1)
        val c0 = floor(y + 0.5f).toInt()
        var best = Float.MAX_VALUE
        for (dr in -1..1) for (dc in -1..1) {
            val r = (r0 + dr).coerceIn(0, CHART_ROWS - 1)
            val c = ((c0 + dc) % CHART_COLUMNS + CHART_COLUMNS) % CHART_COLUMNS
            val cell = r * CHART_COLUMNS + c
            var across = nearest[cell * 2 + 1] - y
            across -= CHART_COLUMNS * round(across / CHART_COLUMNS)
            val dx = (nearest[cell * 2] - row) * rowLength
            val dy = across * columnLength
            best = minOf(best, dx * dx + dy * dy)
        }
        return sqrt(best)
    }

    /** The field at (u, v), from 0 far outside the cloth to 1 deep inside it. */
    fun at(u: Float, v: Float): Float {
        joined?.let { fields -> return fields.maxOf { it.at(u, v) } }
        val drawn = drawn ?: return sample(whole, rowOf(u), columnOf(v))
        val shaped = drawn.at(u, v)
        val laid = beneath?.takeIf { it.holdsCells }?.let { maxOf(shaped, it.at(u, v)) } ?: shaped
        return edits?.at(laid, u, v) ?: laid
    }

    /**
     * Where its cloth is drawn, how wide its narrowest piece or gap is in body units, a unit of u at u [along] of it long and of v [around]
     * (see [DrawnCloth.narrowest]); null where it is read from cells alone.
     */
    fun drawnNarrowest(along: (Float) -> Float, around: (Float) -> Float): Float? {
        drawn?.let { return it.narrowest(along, around) }
        return joined?.mapNotNull { it.drawnNarrowest(along, around) }?.minOrNull()
    }

    /** Whether area [area] paints any cell of the part. */
    fun paints(area: Int): Boolean = areas[area] != null

    /** Area [area]'s own field at (u, v); 0 where that area paints nothing of the part. */
    fun areaAt(area: Int, u: Float, v: Float): Float {
        val cells = areas[area] ?: return 0f
        val drawn = drawn ?: return sample(cells, rowOf(u), columnOf(v))
        beneath?.areaAt(area, u, v)?.let { return it }
        val shaped = drawn.areaAt(area, u, v)
        return areaEdits?.getOrNull(area)?.at(shaped, u, v) ?: shaped
    }

    override fun equals(other: Any?): Boolean = other is ClothField && other.key == key

    override fun hashCode(): Int = key.hashCode()

    companion object {
        private val KERNEL: FloatArray = run {
            val half = (CLOTH_BLUR_CELLS * BLUR_WIDTHS).toInt() + 1
            val raw = FloatArray(half * 2 + 1) { i ->
                val d = (i - half).toFloat()
                exp(-d * d / (2f * CLOTH_BLUR_CELLS * CLOTH_BLUR_CELLS))
            }
            val sum = raw.sum()
            FloatArray(raw.size) { raw[it] / sum }
        }

        /**
         * The field of the cells each of [areas] paints, one a cell row by row, or null where they paint none. With one area it holds no
         * area fields of its own. With [drawn], the garment's shapes on the part, its edge and fields are those of the areas
         * a shape paints, less the cells cell strokes took from them and with those they added ([CellEdits]); areas no shape paints are
         * read beneath the shapes ([ClothBeneath]).
         */
        fun of(areas: List<BooleanArray>, drawn: DrawnCloth? = null): ClothField? {
            val held = BooleanArray(CHART_CELLS) { cell -> areas.any { it[cell] } }
            if (held.none { it }) return null
            val own = if (areas.size < 2) emptyList() else areas.map { cells -> if (cells.any { it }) blurred(cells) else null }
            val key: Any = if (drawn == null) areas.map(::runsOfCells) else areas.map(::runsOfCells) to drawn
            val edits = drawn?.editsOf(areas)
            val areaEdits = drawn?.let { d -> areas.indices.map { k -> d.editsOf(k, areas[k]) } }
            val beneath = drawn?.let { ClothBeneath.of(areas, it) }
            return ClothField(blurred(held), own, key, checkNotNull(ClothReach.of(held)), held, drawn, edits = edits, beneath = beneath, areaEdits = areaEdits)
        }

        /**
         * The largest of [fields] at each cell, as cloth painted by any of them, with no area fields; null with none. Where any of them is
         * drawn, it reads each of them and takes the most.
         */
        fun union(fields: List<ClothField>): ClothField? {
            if (fields.size < 2) return fields.firstOrNull()
            val out = FloatArray(CHART_CELLS) { i -> fields.maxOf { it.whole[i] } }
            val held = BooleanArray(CHART_CELLS) { i -> fields.any { it.held[i] } }
            val joined = fields.takeIf { list -> list.any { it.drawn != null || it.joined != null } }
            return ClothField(out, emptyList(), fields.map { it.key }, checkNotNull(ClothReach.of(held)), held, joined = joined)
        }

        /**
         * For each cell of [held], the nearest middle of a side between a painted and an unpainted cell, rows not crossing the part's ends
         * and columns wrapping, as row and column in cells; empty where every cell is the same.
         */
        private fun nearestEdges(held: BooleanArray): FloatArray {
            val points = ArrayList<Float>()
            for (r in 0 until CHART_ROWS) for (c in 0 until CHART_COLUMNS) {
                val here = held[r * CHART_COLUMNS + c]
                if (r + 1 < CHART_ROWS && held[(r + 1) * CHART_COLUMNS + c] != here) {
                    points += r + 0.5f
                    points += c.toFloat()
                }
                if (held[r * CHART_COLUMNS + (c + 1) % CHART_COLUMNS] != here) {
                    points += r.toFloat()
                    points += c + 0.5f
                }
            }
            if (points.isEmpty()) return FloatArray(0)
            val out = FloatArray(CHART_CELLS * 2)
            for (cell in 0 until CHART_CELLS) {
                val r = (cell / CHART_COLUMNS).toFloat()
                val c = (cell % CHART_COLUMNS).toFloat()
                var best = Float.MAX_VALUE
                var i = 0
                while (i < points.size) {
                    val dr = points[i] - r
                    var dc = points[i + 1] - c
                    dc -= CHART_COLUMNS * round(dc / CHART_COLUMNS)
                    val d = dr * dr + dc * dc
                    if (d < best) {
                        best = d
                        out[cell * 2] = points[i]
                        out[cell * 2 + 1] = points[i + 1]
                    }
                    i += 2
                }
            }
            return out
        }

        private fun rowOf(u: Float): Float = u * CHART_ROWS - 0.5f

        private fun columnOf(v: Float): Float = (v - floor(v)) * CHART_COLUMNS - 0.5f

        /** [field] at row [x] and column [y], in cells from the first cell's middle, bilinear, rows held at the ends, columns wrapping. */
        private fun sample(field: FloatArray, x: Float, y: Float): Float {
            val xc = x.coerceIn(0f, (CHART_ROWS - 1).toFloat())
            val r0 = minOf(xc.toInt(), CHART_ROWS - 2)
            val tx = xc - r0
            val yf = floor(y)
            val ty = y - yf
            val c0 = ((yf.toInt() % CHART_COLUMNS) + CHART_COLUMNS) % CHART_COLUMNS
            val c1 = (c0 + 1) % CHART_COLUMNS
            val a = field[r0 * CHART_COLUMNS + c0] * (1f - ty) + field[r0 * CHART_COLUMNS + c1] * ty
            val b = field[(r0 + 1) * CHART_COLUMNS + c0] * (1f - ty) + field[(r0 + 1) * CHART_COLUMNS + c1] * ty
            return a * (1f - tx) + b * tx
        }

        /** [held] as 1 and the rest 0, blurred by [KERNEL] round each row, wrapping, then along each column, held at the ends. */
        private fun blurred(held: BooleanArray): FloatArray {
            val half = KERNEL.size / 2
            val across = FloatArray(CHART_CELLS)
            for (r in 0 until CHART_ROWS) for (c in 0 until CHART_COLUMNS) {
                var sum = 0f
                for (k in KERNEL.indices) {
                    val cc = ((c + k - half) % CHART_COLUMNS + CHART_COLUMNS) % CHART_COLUMNS
                    if (held[r * CHART_COLUMNS + cc]) sum += KERNEL[k]
                }
                across[r * CHART_COLUMNS + c] = sum
            }
            val out = FloatArray(CHART_CELLS)
            for (r in 0 until CHART_ROWS) for (c in 0 until CHART_COLUMNS) {
                var sum = 0f
                for (k in KERNEL.indices) {
                    val rr = (r + k - half).coerceIn(0, CHART_ROWS - 1)
                    sum += KERNEL[k] * across[rr * CHART_COLUMNS + c]
                }
                out[r * CHART_COLUMNS + c] = sum
            }
            return out
        }
    }
}
