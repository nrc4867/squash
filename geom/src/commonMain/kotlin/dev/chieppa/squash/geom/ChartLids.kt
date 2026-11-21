package dev.chieppa.squash.geom

import kotlin.math.max
import kotlin.math.min

/**
 * How narrow a gap between the lids, in opening heights, is pushed outside the opening by what it lacks, so lids that meet leave no
 * smoothed hairline of the eye along their meeting.
 */
const val SHUT_GAP = 0.04f

/** How open an opening is at a place along it, a share of its middle's height, below which its closed line is drawn in toward the corner. */
const val CLOSED_DRAW_IN = 0.5f

/**
 * Where an eye's lids stand, read in its opening's chart in shares of its width along and heights up: the upper lid [close] of the way
 * down from its open curve to the closed line and the lower lid [rise] of the way up to it, both 0 open and 1 meeting there. The closed
 * line crosses the opening's middle [closedAt] heights up, its middle standing [closedBend] heights above a straight line through its
 * ends (an arch above 0, a sag below), rising [closedSlope] heights for each width toward the front; toward the corners, where the
 * opening is less than [CLOSED_DRAW_IN] as open as at its middle, it is drawn in, so it meets them as smoothly as the lids do.
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

    /**
     * The closed line's height at [a] along the opening, in heights, between an open lid [top] and [bottom] there, the opening standing
     * [middleOpen] heights from bottom to top at its middle.
     */
    fun closed(top: Float, bottom: Float, a: Float, middleOpen: Float): Float {
        val raw = closedAt - closedBend * a * a + closedSlope * a
        val open = if (middleOpen > 0f) smoothstep(0f, CLOSED_DRAW_IN, (top - bottom) / middleOpen) else 0f
        return min(max(raw * open, bottom), top)
    }

    companion object {
        /** Where a closed line crosses the middle unless told: a little below it, since the upper lid travels further. */
        const val DEFAULT_CLOSED_AT = -0.25f

        /** Both lids open on a straight closed line. */
        val OPEN = ChartLids()
    }
}

/** [a] and [b] mixed [t] of the way, exactly [a] at 0. */
internal fun mixed(a: Float, b: Float, t: Float): Float = if (t <= 0f) a else a + (b - a) * min(max(t, 0f), 1f)
