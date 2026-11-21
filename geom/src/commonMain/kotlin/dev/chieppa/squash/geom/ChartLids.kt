package dev.chieppa.squash.geom

import kotlin.math.max
import kotlin.math.min

/** Over how many opening heights the closed line eases into the lids where it would cross them near the corners. */
const val CLOSED_SOFT = 0.08f

/**
 * Where an eye's lids stand, read in its opening's chart in shares of its width along and heights up: the upper lid [close] of the way
 * down from its open curve to the closed line and the lower lid [rise] of the way up to it, both 0 open and 1 meeting there. The closed
 * line crosses the opening's middle [closedAt] heights up, its middle standing [closedBend] heights above a straight line through its
 * ends (an arch above 0, a sag below), rising [closedSlope] heights for each width toward the front; it is eased into the open lids
 * where it would pass them, so it always ends in the corners.
 */
data class ChartLids(
    val close: Float = 0f,
    val rise: Float = 0f,
    val closedAt: Float = DEFAULT_CLOSED_AT,
    val closedBend: Float = 0f,
    val closedSlope: Float = 0f,
) {
    /** Whether both lids stand open, so the opening is the almond's own. */
    val isOpen: Boolean get() = close <= 0f && rise <= 0f

    /** The closed line's height at [a] along the opening, in heights, between an open lid [top] and [bottom] there. */
    fun closed(top: Float, bottom: Float, a: Float): Float {
        val raw = closedAt - closedBend * a * a + closedSlope * a
        return softMax(softMin(raw, top, CLOSED_SOFT), bottom, CLOSED_SOFT)
    }

    companion object {
        /** Where a closed line crosses the middle unless told: a little below it, since the upper lid travels further. */
        const val DEFAULT_CLOSED_AT = -0.25f

        /** Both lids open on a straight closed line. */
        val OPEN = ChartLids()
    }
}

/** The smaller of [a] and [b], eased over [k] where they are near, never above either. */
fun softMin(a: Float, b: Float, k: Float): Float {
    val h = (0.5f + 0.5f * (b - a) / k).coerceIn(0f, 1f)
    return b + (a - b) * h - k * h * (1f - h)
}

/** The larger of [a] and [b], eased over [k] where they are near, never below either. */
fun softMax(a: Float, b: Float, k: Float): Float = -softMin(-a, -b, k)

/** [a] and [b] mixed [t] of the way, exactly [a] at 0. */
internal fun mixed(a: Float, b: Float, t: Float): Float = if (t <= 0f) a else a + (b - a) * min(max(t, 0f), 1f)
