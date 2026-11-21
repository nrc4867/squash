package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** How many straight pieces a tapered stroke's curve is read as. */
const val STROKE_PIECES = 6

/**
 * A stroke that tapers from [rootHalf] at its root ([rootX], [rootY]) to a point at its tip, ([reachX], [reachY]) on from the root, its
 * middle bent: [bendX] of the way across is reached as the square of the way along instead of in step with it, and [bendY] up, so a
 * stroke with [bendX] 1 rises straight off its root and sweeps across at its tip, and one with [bendY] 1 runs out straight and lifts at
 * its tip. Places across are counted [aspect] times, as a lid measures them. Its half width falls off as the way left to its tip raised
 * to [taper]: in step with it at 1, to a finer point above.
 */
class TaperedStroke(
    private val rootX: Float,
    private val rootY: Float,
    private val reachX: Float,
    private val reachY: Float,
    private val rootHalf: Float,
    private val bendX: Float,
    private val bendY: Float,
    private val aspect: Float,
    private val taper: Float = 1f,
) {
    /** How far outside the stroke ([x], [y]) lies, places across counted [aspect] times: at most zero inside. */
    fun level(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        var ax = 0f
        var ay = 0f
        for (i in 1..STROKE_PIECES) {
            val t = i.toFloat() / STROKE_PIECES
            val bx = along(t, bendX) * reachX * aspect
            val by = along(t, bendY) * reachY
            val px = (x - rootX) * aspect - ax
            val py = y - rootY - ay
            val ex = bx - ax
            val ey = by - ay
            val h = ((px * ex + py * ey) / max(ex * ex + ey * ey, TINY)).coerceIn(0f, 1f)
            val dx = px - ex * h
            val dy = py - ey * h
            val at = (i - 1 + h) / STROKE_PIECES
            val left = 1f - at
            best = min(best, sqrt(dx * dx + dy * dy) - rootHalf * (if (taper == 1f) left else left.pow(taper)))
            ax = bx
            ay = by
        }
        return best
    }

    private fun along(t: Float, bend: Float): Float = t + (t * t - t) * bend

    private companion object {
        const val TINY = 1e-12f
    }
}

/** The smaller of [a] and [b], rounded over [k] where they meet, so two shapes join with a fillet and no notch. */
fun smoothMin(a: Float, b: Float, k: Float): Float {
    val h = max(k - abs(a - b), 0f) / k
    return min(a, b) - h * h * k * FILLET_DEPTH
}

/** How deep a smooth minimum's fillet is, a share of its reach. */
const val FILLET_DEPTH = 0.25f
