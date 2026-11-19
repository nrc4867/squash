package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.runsOfCells
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
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

/** The square root of two, for the probit from the inverse error function. */
private const val SQRT_TWO = 1.4142135f

/**
 * One part's painted cloth as a smooth field over its first run's (u, v), read from its chart of [CHART_ROWS] by [CHART_COLUMNS]
 * cells: each cell any area paints held as 1 and the rest 0, blurred by [CLOTH_BLUR_CELLS] cells along and round, rows held at the
 * part's ends and columns wrapping round, so its [CLOTH_HEM_LEVEL] is a smooth curve along the painted cells' edge with no steps. Each
 * area of a garment of several is blurred on its own too, for the seams where areas meet ([areaAt]); all areas are blurred together
 * for the cloth, so two areas that touch leave no gap between them. Equal for the same cells painted by the same areas.
 */
class ClothField private constructor(
    private val whole: FloatArray,
    private val areas: List<FloatArray?>,
    private val key: Any,
    /** Where its painted cells lie on the part's chart. */
    val reach: ClothReach,
    private val held: BooleanArray,
) {
    /** For each cell, the nearest middle of a side between a painted and an unpainted cell, as row and column in cells; empty with none. */
    private val nearest: FloatArray by lazy { nearestEdges(held) }

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
    fun at(u: Float, v: Float): Float = sample(whole, rowOf(u), columnOf(v))

    /** Whether area [area] paints any cell of the part. */
    fun paints(area: Int): Boolean = areas[area] != null

    /** Area [area]'s own field at (u, v); 0 where that area paints nothing of the part. */
    fun areaAt(area: Int, u: Float, v: Float): Float = areas[area]?.let { sample(it, rowOf(u), columnOf(v)) } ?: 0f

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
         * area fields of its own.
         */
        fun of(areas: List<BooleanArray>): ClothField? {
            val held = BooleanArray(CHART_CELLS) { cell -> areas.any { it[cell] } }
            if (held.none { it }) return null
            val own = if (areas.size < 2) emptyList() else areas.map { cells -> if (cells.any { it }) blurred(cells) else null }
            return ClothField(blurred(held), own, areas.map(::runsOfCells), checkNotNull(ClothReach.of(held)), held)
        }

        /** The largest of [fields] at each cell, as cloth painted by any of them, with no area fields; null with none. */
        fun union(fields: List<ClothField>): ClothField? {
            if (fields.size < 2) return fields.firstOrNull()
            val out = FloatArray(CHART_CELLS) { i -> fields.maxOf { it.whole[i] } }
            val held = BooleanArray(CHART_CELLS) { i -> fields.any { it.held[i] } }
            return ClothField(out, emptyList(), fields.map { it.key }, checkNotNull(ClothReach.of(held)), held)
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

        /** The probit of [p]: where under a unit normal curve a share [p] of it lies below, [p] held off 0 and 1. */
        private fun probit(p: Float): Float {
            val x = (2f * p - 1f).coerceIn(-0.9999f, 0.9999f)
            var w = -ln((1f - x) * (1f + x))
            var q: Float
            if (w < 5f) {
                w -= 2.5f
                q = 2.81022636e-08f
                q = 3.43273939e-07f + q * w
                q = -3.5233877e-06f + q * w
                q = -4.39150654e-06f + q * w
                q = 0.00021858087f + q * w
                q = -0.00125372503f + q * w
                q = -0.00417768164f + q * w
                q = 0.246640727f + q * w
                q = 1.50140941f + q * w
            } else {
                w = sqrt(w) - 3f
                q = -0.000200214257f
                q = 0.000100950558f + q * w
                q = 0.00134934322f + q * w
                q = -0.00367342844f + q * w
                q = 0.00573950773f + q * w
                q = -0.0076224613f + q * w
                q = 0.00943887047f + q * w
                q = 1.00167406f + q * w
                q = 2.83297682f + q * w
            }
            return SQRT_TWO * q * x
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
