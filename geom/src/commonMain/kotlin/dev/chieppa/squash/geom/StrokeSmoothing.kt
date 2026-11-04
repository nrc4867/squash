package dev.chieppa.squash.geom

private const val CENTER_WEIGHT = 0.5f
private const val NEIGHBOUR_WEIGHT = 0.25f

/**
 * [passes] runs of the binomial (1 2 1) low-pass filter over evenly spaced [points]. Each pass adds a variance of half a sample
 * squared. Points in [pinned] hold still, as do an open polyline's ends. A [closed] polyline repeats its first point last and
 * is filtered across that seam.
 */
fun smooth(points: List<Vec2>, passes: Int, closed: Boolean = false, pinned: Set<Int> = emptySet()): List<Vec2> {
    if (points.size < 3 || passes <= 0) return points
    val last = points.lastIndex
    val held = BooleanArray(points.size) { it in pinned }
    val seamMoves = closed && !held[0] && !held[last]
    var xs = FloatArray(points.size) { points[it].x }
    var ys = FloatArray(points.size) { points[it].y }
    var nextXs = xs.copyOf()
    var nextYs = ys.copyOf()
    repeat(passes) {
        for (i in 1 until last) {
            if (held[i]) continue
            nextXs[i] = filtered(xs[i - 1], xs[i], xs[i + 1])
            nextYs[i] = filtered(ys[i - 1], ys[i], ys[i + 1])
        }
        if (seamMoves) {
            nextXs[0] = filtered(xs[last - 1], xs[0], xs[1])
            nextYs[0] = filtered(ys[last - 1], ys[0], ys[1])
            nextXs[last] = nextXs[0]
            nextYs[last] = nextYs[0]
        }
        xs = nextXs.also { nextXs = xs }
        ys = nextYs.also { nextYs = ys }
    }
    return List(points.size) { Vec2(xs[it], ys[it]) }
}

private fun filtered(before: Float, at: Float, after: Float): Float = at * CENTER_WEIGHT + (before + after) * NEIGHBOUR_WEIGHT
