package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS

/** How many chart rows the range a painted part's garment grows over is rounded out to, so a small stroke keeps the same range. */
const val GROWN_STEP_ROWS = 32

/** How many chart rows past its painted cells a painted part's garment grows at least, beyond the hem the blur draws. */
const val GROWN_MARGIN_ROWS = 2

/**
 * Where one part's painted cells lie on its chart: rows [firstRow] to [lastRow] along it, and [columns], the fewest columns round it one
 * span holding them all takes, [CHART_COLUMNS] where they reach the whole way round, that span starting at column [firstColumn]. The
 * garment grows over [from] to [to] of the part, the painted rows [GROWN_MARGIN_ROWS] further each way, rounded out to steps of
 * [GROWN_STEP_ROWS].
 */
data class ClothReach(val firstRow: Int, val lastRow: Int, val columns: Int, val firstColumn: Int = 0) {
    /** Where along the part, in u, the garment grows from. */
    val from: Float get() = stepDown(firstRow - GROWN_MARGIN_ROWS).toFloat() / CHART_ROWS

    /** Where along the part, in u, the garment grows to. */
    val to: Float get() = stepUp(lastRow + 1 + GROWN_MARGIN_ROWS).toFloat() / CHART_ROWS

    /** Whether the painted cells stop short of either end of the part, as a box's range does past the row beside an end. */
    val partial: Boolean get() = firstRow > 1 || lastRow < CHART_ROWS - 2

    /** How far along the part, in u, the painted cells reach, half a cell onto the walls each side. */
    val along: Float get() = (lastRow - firstRow + 2f) / CHART_ROWS

    /** Whether the painted cells reach the whole way round the part. */
    val allRound: Boolean get() = columns >= CHART_COLUMNS

    /**
     * Where round the part, in v, the painted cells' span starts: at the edge of its first column, below 0 where it crosses v 0, 0 the
     * whole way round. The cloth's hem lies on the cells' edges, where its field is half.
     */
    val fromV: Float get() = if (allRound) 0f else (if (firstColumn + columns > CHART_COLUMNS) firstColumn - CHART_COLUMNS else firstColumn).toFloat() / CHART_COLUMNS

    /** Where round the part, in v, the painted cells' span ends, at the edge of its last column; 1 the whole way round. */
    val toV: Float get() = if (allRound) 1f else fromV + columns.toFloat() / CHART_COLUMNS

    /** How far round the part, as a share of the way round, the painted cells reach, half a cell onto the walls each side. */
    val around: Float get() = minOf(columns + 1f, CHART_COLUMNS.toFloat()) / CHART_COLUMNS

    companion object {
        /** Where the cells [held] holds lie, a cell row by row, or null where it holds none. */
        fun of(held: BooleanArray): ClothReach? {
            var low = CHART_ROWS
            var high = -1
            val painted = BooleanArray(CHART_COLUMNS)
            for (cell in 0 until CHART_CELLS) if (held[cell]) {
                val row = cell / CHART_COLUMNS
                low = minOf(low, row)
                high = maxOf(high, row)
                painted[cell % CHART_COLUMNS] = true
            }
            if (high < 0) return null
            val (gapStart, gap) = longestGap(painted)
            return ClothReach(low, high, CHART_COLUMNS - gap, if (gap == 0) 0 else (gapStart + gap) % CHART_COLUMNS)
        }

        private fun stepDown(row: Int): Int = if (row <= 0) 0 else row / GROWN_STEP_ROWS * GROWN_STEP_ROWS

        private fun stepUp(row: Int): Int = if (row >= CHART_ROWS) CHART_ROWS else minOf((row + GROWN_STEP_ROWS - 1) / GROWN_STEP_ROWS * GROWN_STEP_ROWS, CHART_ROWS)

        /** Where the most columns in a row, wrapping round, that [painted] leaves unpainted start, and how many they are; 0 for none. */
        private fun longestGap(painted: BooleanArray): Pair<Int, Int> {
            if (painted.none { !it }) return 0 to 0
            var longest = 0
            var at = 0
            for (start in painted.indices) {
                if (painted[start] || !painted[(start + CHART_COLUMNS - 1) % CHART_COLUMNS]) continue
                var length = 0
                while (length < CHART_COLUMNS && !painted[(start + length) % CHART_COLUMNS]) length++
                if (length > longest) {
                    longest = length
                    at = start
                }
            }
            return at to longest
        }
    }
}
