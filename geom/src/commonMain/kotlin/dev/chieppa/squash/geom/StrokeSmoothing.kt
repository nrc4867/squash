package dev.chieppa.squash.geom

private const val CENTER_WEIGHT = 0.5f
private const val NEIGHBOUR_WEIGHT = 0.25f

/**
 * [passes] runs of the binomial (1 2 1) low-pass filter over evenly spaced [points]. Points in [pinned] hold still, as do
 * an open polyline's ends. A [closed] polyline repeats its first point last and is filtered across that seam.
 */
fun smooth(points: List<Vec2>, passes: Int, closed: Boolean = false, pinned: Set<Int> = emptySet()): List<Vec2> {
    if (points.size < 3) return points
    var current = points
    repeat(passes) {
        val last = current.lastIndex
        val next = current.toMutableList()
        for (i in 1 until last) {
            if (i !in pinned) next[i] = filtered(current[i - 1], current[i], current[i + 1])
        }
        if (closed && 0 !in pinned && last !in pinned) {
            val seam = filtered(current[last - 1], current[0], current[1])
            next[0] = seam
            next[last] = seam
        }
        current = next
    }
    return current
}

private fun filtered(before: Vec2, at: Vec2, after: Vec2): Vec2 =
    at * CENTER_WEIGHT + (before + after) * NEIGHBOUR_WEIGHT
