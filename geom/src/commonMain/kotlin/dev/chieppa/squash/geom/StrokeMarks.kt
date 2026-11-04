package dev.chieppa.squash.geom

import kotlin.math.abs

/** How far from a stroke a mark still lands on it, in screen pixels: a sample's spacing and the smoothing around it. */
private const val MARK_REACH_PIXELS = 4f

/** What a modifier held during a stroke forces at the pointer: a [CORNER], or a [SMOOTH] point where one would be found. */
enum class StrokeMarkKind { CORNER, SMOOTH }

/** A forced point of a stroke, at [at] in the stroke's own units. */
data class StrokeMark(val at: Vec2, val kind: StrokeMarkKind)

/**
 * The indices of [points] a fit cuts at: the corners the hand made, as [cornerIndices] finds them, as [marks] overrule them.
 * A mark replaces any corner within the corner window of it. A mark further than its reach from [points], on a stretch that
 * was cut off, does nothing, and an open line's ends are ends whatever a mark says.
 */
internal fun strokeCorners(points: List<Vec2>, closed: Boolean, marks: List<StrokeMark>, pixel: Float): List<Int> {
    val found = cornerIndices(points, CORNER_WINDOW, CORNER_COSINE, closed)
    if (marks.isEmpty()) return found
    val count = if (closed) points.size - 1 else points.size
    val corners = found.toMutableSet()
    for (mark in marks) {
        val spot = points.indices.minByOrNull { (points[it] - mark.at).magnitude } ?: continue
        if ((points[spot] - mark.at).magnitude > MARK_REACH_PIXELS * pixel) continue
        val at = if (closed) spot % count else spot
        corners.removeAll { apart(it, at, count, closed) <= CORNER_WINDOW }
        if (mark.kind == StrokeMarkKind.CORNER && (closed || at in 1 until points.lastIndex)) corners += at
    }
    return corners.sorted()
}

private fun apart(a: Int, b: Int, count: Int, closed: Boolean): Int {
    val direct = abs(a - b)
    return if (closed) minOf(direct, count - direct) else direct
}

/** [marks] and their mirror images across [centre]. */
internal fun mirroredMarks(marks: List<StrokeMark>, centre: CentreLine): List<StrokeMark> =
    marks + marks.map { it.copy(at = centre.mirror(it.at)) }
