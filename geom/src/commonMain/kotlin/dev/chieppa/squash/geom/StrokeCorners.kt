package dev.chieppa.squash.geom

/**
 * The indices of evenly spaced [points] where the direction over [window] samples before and after turns by more than
 * the angle whose cosine is [maxCosine], one index per corner: the sharpest of each run. A [closed] polyline repeats its
 * first point last, and its windows wrap across that seam; a corner on the seam reports index 0.
 */
fun cornerIndices(points: List<Vec2>, window: Int, maxCosine: Float, closed: Boolean = false): List<Int> {
    val count = if (closed) points.size - 1 else points.size
    if (count <= 2 * window) return emptyList()
    fun wrapped(i: Int) = if (closed) i.mod(count) else i
    val range = if (closed) 0 until count else window until count - window
    val turns = FloatArray(count) { Float.POSITIVE_INFINITY }
    for (i in range) {
        val before = points[wrapped(i - window)]
        val after = points[wrapped(i + window)]
        turns[i] = (points[i] - before).unit() dotWith (after - points[i]).unit()
    }
    return range.filter { i ->
        turns[i] < maxCosine && (1..window).all { offset ->
            turns[i] <= turns.getOrElse(wrapped(i - offset)) { Float.POSITIVE_INFINITY } &&
                turns[i] < turns.getOrElse(wrapped(i + offset)) { Float.POSITIVE_INFINITY }
        }
    }
}
