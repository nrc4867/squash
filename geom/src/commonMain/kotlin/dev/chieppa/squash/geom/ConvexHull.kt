package dev.chieppa.squash.geom

import kotlin.math.hypot

/** The convex hull of [points], counter-clockwise from the lowest-leftmost, by Andrew's monotone chain; fewer than three points as they are. */
fun convexHull(points: List<Vec2>): List<Vec2> {
    val sorted = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
    if (sorted.size < 3) return sorted
    fun turn(o: Vec2, a: Vec2, b: Vec2) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val hull = ArrayList<Vec2>(sorted.size + 1)
    for (p in sorted) {
        while (hull.size >= 2 && turn(hull[hull.size - 2], hull[hull.size - 1], p) <= 0f) hull.removeAt(hull.size - 1)
        hull += p
    }
    val lower = hull.size + 1
    for (i in sorted.size - 2 downTo 0) {
        val p = sorted[i]
        while (hull.size >= lower && turn(hull[hull.size - 2], hull[hull.size - 1], p) <= 0f) hull.removeAt(hull.size - 1)
        hull += p
    }
    hull.removeAt(hull.size - 1)
    return hull
}

/** How far [p] lies outside the counter-clockwise convex [hull], 0 on or inside it. */
fun outsideHull(hull: List<Vec2>, p: Vec2): Float {
    if (hull.size < 3) return hull.minOfOrNull { hypot(p.x - it.x, p.y - it.y) } ?: 0f
    var inside = true
    var nearest = Float.MAX_VALUE
    for (i in hull.indices) {
        val a = hull[i]
        val b = hull[(i + 1) % hull.size]
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (dx * (p.y - a.y) - dy * (p.x - a.x) < 0f) inside = false
        val l = dx * dx + dy * dy
        val t = if (l == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / l).coerceIn(0f, 1f)
        nearest = minOf(nearest, hypot(p.x - a.x - t * dx, p.y - a.y - t * dy))
    }
    return if (inside) 0f else nearest
}
