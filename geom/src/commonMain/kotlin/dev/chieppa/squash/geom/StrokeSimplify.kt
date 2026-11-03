package dev.chieppa.squash.geom

/**
 * Ramer-Douglas-Peucker: the fewest of [points], first and last among them, such that every dropped point lies within
 * [tolerance] of the polyline that remains.
 */
fun simplify(points: List<Vec2>, tolerance: Float): List<Vec2> {
    if (points.size < 3) return points
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true
    val ranges = ArrayDeque<Pair<Int, Int>>()
    ranges.addLast(0 to points.lastIndex)
    while (ranges.isNotEmpty()) {
        val (first, last) = ranges.removeLast()
        var farthest = -1
        var distance = tolerance
        for (i in first + 1 until last) {
            val d = distanceToSegment(points[i], points[first], points[last])
            if (d > distance) {
                distance = d
                farthest = i
            }
        }
        if (farthest >= 0) {
            keep[farthest] = true
            ranges.addLast(first to farthest)
            ranges.addLast(farthest to last)
        }
    }
    return points.filterIndexed { i, _ -> keep[i] }
}
