package dev.chieppa.squash.geom

import kotlin.math.ceil
import kotlin.math.floor

private const val STATIONS = 129
private const val MIN_RANGE = 1e-4f
private const val MIN_CROSSING_GAP = 1e-5f

/**
 * A drawn outline read along its x: at each station, the lowest and highest y where the outline crosses that x. An open
 * outline is closed by the straight chord between its ends. A station depends only on the edges that cross it, so
 * changing a span of the outline changes only the stations under that span, as long as the x range stays the same.
 */
class OutlineProfile private constructor(
    val start: Float,
    val end: Float,
    private val low: FloatArray,
    private val high: FloatArray,
) {
    /** The distance between the crossings at [x], held at the nearest end outside the range. */
    fun extentAt(x: Float): Float = lookup(high, x) - lookup(low, x)

    /** The midpoint of the crossings at [x], held at the nearest end outside the range. */
    fun midAt(x: Float): Float = (lookup(high, x) + lookup(low, x)) / 2f

    private fun lookup(table: FloatArray, x: Float): Float {
        val position = ((x - start) / (end - start)).coerceIn(0f, 1f) * (STATIONS - 1)
        val i = position.toInt().coerceAtMost(STATIONS - 2)
        val t = position - i
        return table[i] + (table[i + 1] - table[i]) * t
    }

    companion object {
        /** The profile of [outline], or null if it has no usable extent in x or in y. */
        fun of(outline: Polyline2): OutlineProfile? {
            val xs = outline.xs
            val ys = outline.ys
            val start = xs.min()
            val end = xs.max()
            if (end - start < MIN_RANGE) return null
            val low = FloatArray(STATIONS) { Float.POSITIVE_INFINITY }
            val high = FloatArray(STATIONS) { Float.NEGATIVE_INFINITY }
            val step = (end - start) / (STATIONS - 1)
            for (i in xs.indices) {
                val next = if (i == xs.lastIndex) 0 else i + 1
                scanEdge(xs[i], ys[i], xs[next], ys[next], start, step, low, high)
            }
            return if (fillGaps(low, high)) OutlineProfile(start, end, low, high) else null
        }

        private fun scanEdge(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            start: Float,
            step: Float,
            low: FloatArray,
            high: FloatArray,
        ) {
            val first = ceil((minOf(x0, x1) - start) / step).toInt().coerceAtLeast(0)
            val last = floor((maxOf(x0, x1) - start) / step).toInt().coerceAtMost(STATIONS - 1)
            for (s in first..last) {
                val across = x1 - x0
                val t = if (across == 0f) 0f else ((start + s * step - x0) / across).coerceIn(0f, 1f)
                val y = y0 + (y1 - y0) * t
                if (y < low[s]) low[s] = y
                if (y > high[s]) high[s] = y
                if (across == 0f) {
                    if (y1 < low[s]) low[s] = y1
                    if (y1 > high[s]) high[s] = y1
                }
            }
        }

        /**
         * A station no edge reached, as rounding can leave at the ends of the range, takes its nearest neighbour's crossings.
         * False if no station has any gap between its crossings. A tip keeps its single crossing, so its extent is zero.
         */
        private fun fillGaps(low: FloatArray, high: FloatArray): Boolean {
            if ((0 until STATIONS).none { high[it] - low[it] > MIN_CROSSING_GAP }) return false
            val usable = BooleanArray(STATIONS) { high[it] >= low[it] }
            val nearest = IntArray(STATIONS)
            var seen = -1
            for (s in 0 until STATIONS) {
                if (usable[s]) seen = s
                nearest[s] = seen
            }
            seen = -1
            for (s in STATIONS - 1 downTo 0) {
                if (usable[s]) seen = s
                if (seen >= 0 && (nearest[s] < 0 || seen - s < s - nearest[s])) nearest[s] = seen
            }
            for (s in 0 until STATIONS) {
                if (!usable[s]) {
                    low[s] = low[nearest[s]]
                    high[s] = high[nearest[s]]
                }
            }
            return true
        }
    }
}
