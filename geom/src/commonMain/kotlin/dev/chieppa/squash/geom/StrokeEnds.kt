package dev.chieppa.squash.geom

/** Share of a shape's reach along x given up where a closed shape that stops short of the plane is opened towards it. */
private const val OPENING_FRACTION = 0.1f

/** The head and the tail of a stroke searched for where it crosses itself are each this fraction of it, inverted. */
private const val END_FRACTION_DIVISOR = 3

/** How a cleaned stroke ends. */
sealed interface StrokeEnds {
    /** A closed shape: the last point is the first. */
    data object Closed : StrokeEnds

    /** Open on the plane x = [plane], both ends on it; the shape keeps to the side of it that [above] says. */
    data class CutAtPlane(val plane: Float = 0f, val above: Boolean = true) : StrokeEnds
}

/**
 * [points] as a closed loop, first point repeated last, if the stroke comes back to where it started: past its head, in
 * which case the overshoot on both ends goes, or within [gap] of it. Null if it stays open.
 */
fun closeLoop(points: List<Vec2>, gap: Float): List<Vec2>? {
    if (points.size < 3 || arcLength(points) <= 2f * gap) return null
    val headEnd = points.size / END_FRACTION_DIVISOR
    val tailStart = points.size - headEnd - 1
    for (i in points.lastIndex - 1 downTo tailStart) {
        for (j in 0 until headEnd) {
            val crossing = segmentCrossing(points[i], points[i + 1], points[j], points[j + 1]) ?: continue
            return listOf(crossing) + points.subList(j + 1, i + 1) + crossing
        }
    }
    return if ((points.last() - points.first()).magnitude <= gap) points + points.first() else null
}

private fun segmentCrossing(a: Vec2, b: Vec2, c: Vec2, d: Vec2): Vec2? {
    val r = b - a
    val s = d - c
    val across = r.x * s.y - r.y * s.x
    if (across == 0f) return null
    val t = ((c.x - a.x) * s.y - (c.y - a.y) * s.x) / across
    val u = ((c.x - a.x) * r.y - (c.y - a.y) * r.x) / across
    return if (t in 0f..1f && u in 0f..1f) a + r * t else null
}

/**
 * The longest part of [points] on the kept side of [ends]'s plane, both ends placed on the plane: where it crosses, or
 * straight along x where it stops short. A [closed] loop that never reaches the plane is opened on the side facing it.
 * Null if nothing lies on the kept side.
 */
fun cutAtPlane(points: List<Vec2>, closed: Boolean, ends: StrokeEnds.CutAtPlane): List<Vec2>? {
    val sign = if (ends.above) 1f else -1f
    val nearest = points.minOf { (it.x - ends.plane) * sign }
    val farthest = points.maxOf { (it.x - ends.plane) * sign }
    if (farthest <= 0f) return null
    val threshold = if (closed && nearest > 0f) {
        ends.plane + sign * (nearest + (farthest - nearest) * OPENING_FRACTION)
    } else {
        ends.plane
    }
    val run = longestRun(points, closed, threshold, sign) ?: return null
    return buildList {
        if (run.first().x != ends.plane) add(Vec2(ends.plane, run.first().y))
        addAll(run)
        if (run.last().x != ends.plane) add(Vec2(ends.plane, run.last().y))
    }
}

/** The longest stretch of [points] with x on the [sign] side of [plane], with its crossings placed exactly on the plane. */
private fun longestRun(points: List<Vec2>, closed: Boolean, plane: Float, sign: Float): List<Vec2>? {
    fun kept(p: Vec2) = (p.x - plane) * sign >= 0f
    fun crossing(a: Vec2, b: Vec2) = Vec2(plane, a.y + (b.y - a.y) * ((plane - a.x) / (b.x - a.x)))
    val runs = mutableListOf<MutableList<Vec2>>()
    var open: MutableList<Vec2>? = null
    for (i in points.indices) {
        val p = points[i]
        val previous = points.getOrNull(i - 1)
        val current = open
        when {
            kept(p) && current != null -> current += p
            kept(p) -> {
                val run = mutableListOf<Vec2>()
                if (previous != null) run += crossing(previous, p)
                run += p
                runs += run
                open = run
            }
            current != null -> {
                current += crossing(checkNotNull(previous), p)
                open = null
            }
        }
    }
    if (closed && runs.size > 1 && kept(points.first()) && kept(points.last())) {
        val seamed = runs.removeLast() + runs.removeFirst().drop(1)
        runs += seamed.toMutableList()
    }
    return runs.maxByOrNull(::arcLength)
}
