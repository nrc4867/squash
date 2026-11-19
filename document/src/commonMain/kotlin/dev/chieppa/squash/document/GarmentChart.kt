package dev.chieppa.squash.document

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** How many rows of cells a part's chart holds along it, in u. */
const val CHART_ROWS = 64

/** How many columns of cells a part's chart holds round it, in v. */
const val CHART_COLUMNS = 32

/** How many cells each part's chart holds. */
const val CHART_CELLS = CHART_ROWS * CHART_COLUMNS

/** The fewest rows or columns a box's cells take: one row or column alone blurs below the cloth's hem level and drops out. */
const val LEAST_BOX_CELLS = 2

/** How near a cell's middle, in cells, a box's edge may lie and still leave the cell out, so a box drawn round cells takes only those. */
private const val EDGE_SLACK = 1e-3f

/**
 * The cells of one part's chart that [runs] hold: lengths of runs of cells row by row, the first run not held, so a part held whole is
 * `[0, cells]`; runs past the chart's end are cut off.
 */
fun cellsOfRuns(runs: List<Int>): BooleanArray {
    val held = BooleanArray(CHART_CELLS)
    var at = 0
    runs.forEachIndexed { i, length ->
        val end = (at + length).coerceAtMost(CHART_CELLS)
        if (i % 2 == 1) for (cell in at until end) held[cell] = true
        at = end
    }
    return held
}

/** [held], one a cell row by row, as the lengths of its runs, the first one not held; a run of cells not held reaching the end is left off. */
fun runsOfCells(held: BooleanArray): List<Int> {
    val out = ArrayList<Int>()
    var state = false
    var length = 0
    for (cell in held) {
        if (cell == state) {
            length++
            continue
        }
        out += length
        state = cell
        length = 1
    }
    if (state) out += length
    return out
}

/**
 * The cells of a part's chart [branch]'s box holds: each whose middle lies inside it along the part, cut to the part's ends, and round
 * it, reading v round. A box narrower than [LEAST_BOX_CELLS] cells along or round takes that many, those nearest its middle.
 */
fun boxCells(branch: GarmentBranch): BooleanArray {
    val rows = spanOf(branch.from.coerceIn(0f, 1f) * CHART_ROWS, branch.to.coerceIn(0f, 1f) * CHART_ROWS, CHART_ROWS, wraps = false)
    val columns = if (branch.allRound) BooleanArray(CHART_COLUMNS) { true } else spanOf(branch.fromV * CHART_COLUMNS, branch.toV * CHART_COLUMNS, CHART_COLUMNS, wraps = true)
    return BooleanArray(CHART_CELLS) { cell -> rows[cell / CHART_COLUMNS] && columns[cell % CHART_COLUMNS] }
}

/** Which of [count] cells lie from [low] to [high], in cells, by their middles; wrapping round when [wraps]; at least [LEAST_BOX_CELLS]. */
private fun spanOf(low: Float, high: Float, count: Int, wraps: Boolean): BooleanArray {
    val held = BooleanArray(count)
    val first = ceil(low - 0.5f + EDGE_SLACK).toInt()
    val last = floor(high - 0.5f - EDGE_SLACK).toInt()
    val (from, to) = if (last - first + 1 >= LEAST_BOX_CELLS) first to last else {
        val middle = (low + high) / 2f
        val start = round(middle - LEAST_BOX_CELLS / 2f).toInt()
        val held0 = if (wraps) start else start.coerceIn(0, count - LEAST_BOX_CELLS)
        held0 to held0 + LEAST_BOX_CELLS - 1
    }
    for (i in from..to) {
        if (wraps) held[((i % count) + count) % count] = true else if (i in 0 until count) held[i] = true
    }
    return held
}

/**
 * The cells a set of regions or painted areas holds of one part as a box reaching half a cell onto the walls beside it: along it [from]
 * to [to] in u, from 0 or to 1 where it reaches a row of the part's ends or the one beside it, and round it [fromV] to [toV], 0 to 1 the
 * whole way round, else starting below 0 where it crosses v 0; [cells] is how many it holds.
 */
data class ChartBox(val from: Float, val to: Float, val fromV: Float, val toV: Float, val cells: Int) {
    val allRound: Boolean get() = toV - fromV >= 1f

    /** Whether it holds the whole part. */
    val whole: Boolean get() = from <= 0f && to >= 1f && allRound

    companion object {
        /** The box round the cells of one part [held] takes, or null for none. */
        fun over(held: (Int) -> Boolean): ChartBox? {
            var low = CHART_ROWS
            var high = -1
            val columns = BooleanArray(CHART_COLUMNS)
            var count = 0
            for (cell in 0 until CHART_CELLS) if (held(cell)) {
                val row = cell / CHART_COLUMNS
                low = minOf(low, row)
                high = maxOf(high, row)
                columns[cell % CHART_COLUMNS] = true
                count++
            }
            return if (high < 0) null else of(low, high, columns, count)
        }

        private fun of(low: Int, high: Int, columns: BooleanArray, cells: Int): ChartBox {
            val from = if (low <= 1) 0f else (low - 0.5f) / CHART_ROWS
            val to = if (high >= CHART_ROWS - 2) 1f else (high + 1.5f) / CHART_ROWS
            if (columns.all { it }) return ChartBox(from, to, 0f, 1f, cells)
            var gapStart = 0
            var gapLength = 0
            for (start in 0 until CHART_COLUMNS) {
                if (columns[start] || !columns[(start + CHART_COLUMNS - 1) % CHART_COLUMNS]) continue
                var length = 0
                while (length < CHART_COLUMNS && !columns[(start + length) % CHART_COLUMNS]) length++
                if (length > gapLength) {
                    gapLength = length
                    gapStart = start
                }
            }
            val first = (gapStart + gapLength) % CHART_COLUMNS
            var fromV = (first - 0.5f) / CHART_COLUMNS
            var toV = (first + CHART_COLUMNS - gapLength + 0.5f) / CHART_COLUMNS
            if (toV - fromV >= 1f) return ChartBox(from, to, 0f, 1f, cells)
            if (toV > 1f) {
                fromV -= 1f
                toV -= 1f
            }
            return ChartBox(from, to, fromV, toV, cells)
        }
    }
}
