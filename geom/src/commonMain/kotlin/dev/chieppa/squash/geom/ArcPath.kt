package dev.chieppa.squash.geom

import kotlin.math.sqrt

/** How many straight steps a cubic of an [ArcPath] is measured in. */
private const val STEPS_PER_CUBIC = 64

/** The share of a path's length its tangent is read across. */
private const val TANGENT_SHARE = 0.005f

/**
 * A curve in the plane measured by arc length from its first point, held as a dense polyline. Past either end it runs on
 * along the end's tangent, so a walk along it never runs out.
 */
class ArcPath(val points: List<Vec2>) {
    init {
        require(points.size >= 2) { "A path needs two points" }
    }

    /** The arc length at each point. */
    private val at = FloatArray(points.size).also { for (i in 1 until points.size) it[i] = it[i - 1] + (points[i] - points[i - 1]).magnitude }

    val length: Float get() = at.last()

    /** The point at arc length [s]. */
    fun pointAt(s: Float): Vec2 = when {
        s <= 0f -> points.first() + startTangent * s
        s >= length -> points.last() + endTangent * (s - length)
        else -> {
            val i = segmentAt(s)
            val span = at[i + 1] - at[i]
            val t = if (span > 0f) (s - at[i]) / span else 0f
            points[i] + (points[i + 1] - points[i]) * t
        }
    }

    /** The unit direction of travel at arc length [s]. */
    fun tangentAt(s: Float): Vec2 {
        val reach = length * TANGENT_SHARE
        val from = (s - reach).coerceIn(0f, length)
        val to = (s + reach).coerceIn(0f, length)
        return (pointAt(to) - pointAt(from)).unit()
    }

    val startTangent: Vec2 get() = (points.first { it != points.first() } - points.first()).unit()

    val endTangent: Vec2 get() = (points.last() - points.last { it != points.last() }).unit()

    /**
     * The arc length of the first point past arc length [after] that lies [distance] from [from], going on along the end's
     * tangent when the path ends first. When [from] lies farther than [distance] from the path, the first point where the path
     * comes within [distance] of it.
     */
    fun reach(from: Vec2, after: Float, distance: Float): Float {
        var s0 = after.coerceIn(0f, length)
        var p0 = pointAt(s0)
        for (i in segmentAt(s0) until points.size - 1) {
            val s1 = at[i + 1]
            val p1 = points[i + 1]
            crossing(from, p0, p1, distance, s1 >= length)?.let { return s0 + (s1 - s0) * it }
            s0 = s1
            p0 = p1
        }
        val ahead = p0 + endTangent
        return s0 + (crossing(from, p0, ahead, distance, true) ?: 0f)
    }

    /** This path the other way round. */
    fun reversed(): ArcPath = ArcPath(points.reversed())

    /** This path scaled by [factor] about the origin and moved by [offset]. */
    fun placed(factor: Float, offset: Vec2 = Vec2(0f, 0f)): ArcPath = ArcPath(points.map { it * factor + offset })

    /** The distance from [point] to the nearest point of this path between its ends. */
    fun distanceTo(point: Vec2): Float = (0 until points.size - 1).minOf { distanceToSegment(point, points[it], points[it + 1]) }

    private fun segmentAt(s: Float): Int {
        var lo = 0
        var hi = points.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (at[mid] <= s) lo = mid else hi = mid - 1
        }
        return lo
    }

    companion object {
        /** The path of joined cubics [path]. */
        fun of(path: BezierPath): ArcPath {
            val flat = path.flatten(STEPS_PER_CUBIC)
            return ArcPath(flat.xs.indices.map { Vec2(flat.xs[it], flat.ys[it]) }.filterIndexed { i, p -> i == 0 || p != Vec2(flat.xs[i - 1], flat.ys[i - 1]) })
        }
    }
}

/**
 * Where along a to b, as a share of it, the distance from [centre] first crosses [radius], or null when it does not between
 * them; with [open] the line runs on past b.
 */
private fun crossing(centre: Vec2, a: Vec2, b: Vec2, radius: Float, open: Boolean): Float? {
    val d = b - a
    val f = a - centre
    val qa = d dotWith d
    if (qa == 0f) return null
    val qb = 2f * (f dotWith d)
    val qc = (f dotWith f) - radius * radius
    val disc = qb * qb - 4f * qa * qc
    if (disc < 0f) return null
    val root = sqrt(disc)
    val limit = if (open) Float.POSITIVE_INFINITY else 1f
    val near = (-qb - root) / (2f * qa)
    val far = (-qb + root) / (2f * qa)
    return when {
        qc > 0f && near in 0f..limit -> near
        far in 0f..limit -> far
        else -> null
    }
}
