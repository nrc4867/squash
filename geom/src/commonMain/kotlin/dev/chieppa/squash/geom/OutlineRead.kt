package dev.chieppa.squash.geom

/** What the build reads of an outline in the plane a = [at]: its outermost crossings there. */
data class ReadSpan(val at: Float, val low: Float, val high: Float)

/** A stretch inside the crossings shorter than this many points is the line's own wobble. */
private const val MIN_UNREAD_POINTS = 3

/** [outline] as the build takes it, closed by the chord between its ends. */
private class OutlineCrossings(outline: List<Vec2>) {
    private val edges = outline.zipWithNext() + (outline.last() to outline.first())

    /** The outermost crossings of x = [x], or null if the outline does not reach it. */
    fun at(x: Float): ReadSpan? {
        var low = Float.POSITIVE_INFINITY
        var high = Float.NEGATIVE_INFINITY
        for ((a, b) in edges) {
            if (x < minOf(a.x, b.x) || x > maxOf(a.x, b.x)) continue
            val y0 = if (a.x == b.x) minOf(a.y, b.y) else crossingAt(x, a, b).y
            val y1 = if (a.x == b.x) maxOf(a.y, b.y) else y0
            if (y0 < low) low = y0
            if (y1 > high) high = y1
        }
        return if (low > high) null else ReadSpan(x, low, high)
    }
}

/**
 * Which points of [outline] the build would not read as drawn. A slice lies in its plane a = constant and takes the outermost
 * two crossings of that line, so what doubles back across those planes and lies more than [tolerance] inside them is lost.
 */
private fun unreadPoints(outline: List<Vec2>, tolerance: Float): BooleanArray {
    val crossings = OutlineCrossings(outline)
    val unread = BooleanArray(outline.size) { i ->
        val span = crossings.at(outline[i].x)
        span != null && outline[i].y > span.low + tolerance && outline[i].y < span.high - tolerance
    }
    var from = 0
    while (from < unread.size) {
        var to = from
        while (to + 1 < unread.size && unread[to + 1] == unread[from]) to++
        if (unread[from] && to - from + 1 < MIN_UNREAD_POINTS) for (i in from..to) unread[i] = false
        from = to + 1
    }
    return unread
}

/** The runs of [outline] whose points are as [wanted] says, each running on to the next point so that neighbouring runs join. */
private fun runsOf(outline: List<Vec2>, flags: BooleanArray, wanted: Boolean): List<List<Vec2>> {
    val runs = mutableListOf<List<Vec2>>()
    var from = 0
    while (from < flags.size) {
        var to = from
        while (to + 1 < flags.size && flags[to + 1] == flags[from]) to++
        if (flags[from] == wanted) runs += outline.subList(from, (to + 2).coerceAtMost(outline.size))
        from = to + 1
    }
    return runs
}

/** The stretches of [outline] the build would not read as drawn, within [tolerance]. */
fun unreadStretches(outline: List<Vec2>, tolerance: Float): List<List<Vec2>> =
    if (outline.size < 2) emptyList() else runsOf(outline, unreadPoints(outline, tolerance), wanted = true)

/** The stretches of [outline] the build reads as drawn, within [tolerance]. */
fun readStretches(outline: List<Vec2>, tolerance: Float): List<List<Vec2>> =
    if (outline.size < 2) listOf(outline) else runsOf(outline, unreadPoints(outline, tolerance), wanted = false)

/** What the build reads of [outline], in planes about [spacing] apart across its reach. */
fun readSpans(outline: List<Vec2>, spacing: Float): List<ReadSpan> {
    if (outline.size < 2) return emptyList()
    val crossings = OutlineCrossings(outline)
    val start = outline.minOf { it.x }
    val reach = outline.maxOf { it.x } - start
    val count = (reach / spacing).toInt().coerceAtLeast(1)
    return List(count) { crossings.at(start + reach * (it + 0.5f) / count) }.filterNotNull()
}
