package dev.chieppa.squash.geom

import kotlin.math.sqrt

internal infix fun Vec2.dotWith(o: Vec2): Float = x * o.x + y * o.y

internal val Vec2.magnitude: Float get() = sqrt(this dotWith this)

/** This vector scaled to length 1; a zero vector stays zero. */
internal fun Vec2.unit(): Vec2 {
    val length = magnitude
    return if (length > 0f) this * (1f / length) else this
}

internal fun distanceToSegment(p: Vec2, a: Vec2, b: Vec2): Float {
    val along = b - a
    val square = along dotWith along
    if (square == 0f) return (p - a).magnitude
    val t = (((p - a) dotWith along) / square).coerceIn(0f, 1f)
    return (p - (a + along * t)).magnitude
}

/** The length of the polyline through [points]. */
fun arcLength(points: List<Vec2>): Float {
    var total = 0f
    for (i in 1 until points.size) total += (points[i] - points[i - 1]).magnitude
    return total
}

/** [points] walked at even [spacing], keeping the exact first and last point. */
fun resample(points: List<Vec2>, spacing: Float): List<Vec2> {
    require(spacing > 0f) { "Samples need a spacing" }
    if (points.size < 2) return points
    val result = mutableListOf(points.first())
    var toNext = spacing
    for (i in 1 until points.size) {
        val from = points[i - 1]
        val edge = points[i] - from
        val length = edge.magnitude
        var at = toNext
        while (at < length) {
            result += from + edge * (at / length)
            at += spacing
        }
        toNext = at - length
    }
    if (result.last() != points.last()) {
        val tooClose = result.size > 1 && (points.last() - result.last()).magnitude < spacing / 2f
        if (tooClose) result[result.lastIndex] = points.last() else result += points.last()
    }
    return result
}
