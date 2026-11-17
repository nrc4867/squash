package dev.chieppa.squash.geom

/** How many points either side of a join give the direction the curve passes through it. */
private const val JOIN_TANGENT_REACH = 2

/** A join is moved to even out its two handles only when the longer is more than this many times the shorter. */
private const val UNEVEN_HANDLES = 2f

/** How many places along each side of a join balancing tries, at most. */
private const val BALANCE_TRIES = 12

/**
 * Joined cubic Béziers through [points] within [tolerance], with as few joins as a greedy pass finds. Each cubic is fitted
 * as [fitCubics] fits one. The last cubic reaches back from the end as far as stays within tolerance, then cubics from the
 * start each reach as far along the points as stays within tolerance up to it, found by bisection on where they end. Any
 * join whose two cubics can be one is dropped, and a join whose handles are uneven slides to where they are most even while
 * both cubics still fit. Each join is smooth, along the direction the points pass through it. [startTangent] leaves the
 * first point and [endTangent] leaves the last point back along the curve.
 */
fun fitFewCubics(
    points: List<Vec2>,
    tolerance: Float,
    startTangent: Vec2 = (points[1] - points[0]).unit(),
    endTangent: Vec2 = (points[points.lastIndex - 1] - points.last()).unit(),
): List<CubicBezier> {
    require(points.size >= 2) { "A fit needs at least two points" }
    val fitter = FewPointFitter(points, tolerance, startTangent, endTangent)
    return fitter.fit()
}

private class FewPointFitter(private val points: List<Vec2>, tolerance: Float, private val start: Vec2, private val end: Vec2) {
    private val squared = tolerance * tolerance
    private val single = SingleCubicFit(points, squared)
    private val last = points.lastIndex

    fun fit(): List<CubicBezier> {
        if (fits(0, last)) return listOf(cubic(0, last))
        val tail = lastStart()
        val joins = forward(tail).toMutableList()
        if (tail != last) joins += tail
        merge(joins)
        balance(joins)
        val breaks = listOf(0) + joins + last
        return breaks.zipWithNext { from, to -> cubic(from, to) }
    }

    private fun leaving(at: Int): Vec2 = if (at == 0) start else forwardThrough(at)

    private fun arriving(at: Int): Vec2 = if (at == last) end else forwardThrough(at) * -1f

    private fun fitted(from: Int, to: Int) = single.fit(from, to, leaving(from), arriving(to))

    private fun fits(from: Int, to: Int) = fitted(from, to).squaredError <= squared

    private fun cubic(from: Int, to: Int) = fitted(from, to).curve

    /** The earliest point the last cubic can start at and still fit, found by bisection. */
    private fun lastStart(): Int {
        var fitting = last - 1
        var failing = 0
        while (failing < fitting - 1) {
            val middle = (failing + fitting) / 2
            if (fits(middle, last)) fitting = middle else failing = middle
        }
        return fitting
    }

    /** The joins from the start up to [until], each cubic reaching as far as fits. */
    private fun forward(until: Int): List<Int> {
        val joins = mutableListOf<Int>()
        var from = 0
        while (from < until) {
            if (fits(from, until)) break
            var reach = from + 1
            var beyond = until - 1
            while (reach < beyond) {
                val middle = (reach + beyond + 1) / 2
                if (fits(from, middle)) reach = middle else beyond = middle - 1
            }
            joins += reach
            from = reach
        }
        return joins
    }

    /** Drops each join whose two cubics fit as one, until none does. */
    private fun merge(joins: MutableList<Int>) {
        var merged = true
        while (merged) {
            merged = false
            for (k in joins.indices) {
                val before = if (k == 0) 0 else joins[k - 1]
                val after = if (k == joins.lastIndex) last else joins[k + 1]
                if (fits(before, after)) {
                    joins.removeAt(k)
                    merged = true
                    break
                }
            }
        }
    }

    /** Slides each join with uneven handles to the place between its neighbours where they are most even and both cubics fit. */
    private fun balance(joins: MutableList<Int>) {
        for (k in joins.indices) {
            val before = if (k == 0) 0 else joins[k - 1]
            val after = if (k == joins.lastIndex) last else joins[k + 1]
            var best = joins[k]
            var bestUneven = uneven(before, best, after) ?: continue
            if (bestUneven <= UNEVEN_HANDLES) continue
            val low = before + 2
            val high = after - 2
            if (high < low) continue
            val step = maxOf(1, (high - low) / BALANCE_TRIES)
            for (at in low..high step step) {
                if (!fits(before, at) || !fits(at, after)) continue
                val tried = uneven(before, at, after) ?: continue
                if (tried < bestUneven) {
                    best = at
                    bestUneven = tried
                }
            }
            joins[k] = best
        }
    }

    /** How many times longer the longer handle at join [at] is than the shorter, with cubics from [before] and to [after]. */
    private fun uneven(before: Int, at: Int, after: Int): Float? {
        val into = cubic(before, at).let { (it.p3 - it.p2).magnitude }
        val out = cubic(at, after).let { (it.p1 - it.p0).magnitude }
        val shorter = minOf(into, out)
        return if (shorter <= 0f) null else maxOf(into, out) / shorter
    }

    /** The direction forward along the points through point [index], from the points [JOIN_TANGENT_REACH] either side. */
    private fun forwardThrough(index: Int): Vec2 {
        val after = points[(index + JOIN_TANGENT_REACH).coerceAtMost(last)]
        val before = points[(index - JOIN_TANGENT_REACH).coerceAtLeast(0)]
        val forward = (after - before).unit()
        return if (forward == Vec2(0f, 0f)) (points[(index + 1).coerceAtMost(last)] - points[index]).unit() else forward
    }
}
