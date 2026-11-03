package dev.chieppa.squash.geom

import kotlin.math.ceil
import kotlin.math.floor

/** An end of a stroke this close to an anchor starts or stops at that anchor, in screen pixels. */
private const val SNAP_PIXELS = 12f

/** Every so many samples of a stroke vote on which way around a closed curve it runs. */
private const val VOTE_STRIDE = 4

/**
 * Which part of a curve a stroke redraws: sections [first] until [last] of [ruler], oriented with [line], which meets the
 * curve at parameters [from] and [to]. A free end has no kept section to join: the stroke's own end is the curve's end.
 */
internal class OversketchSpan(
    val ruler: CurveRuler,
    val line: List<Vec2>,
    val first: Int,
    val last: Int,
    val from: Float,
    val to: Float,
    val freeStart: Boolean,
    val freeEnd: Boolean,
)

/** The stretch of [points] on the kept side of a socket plane, or [points] when there is none. Null if none is kept. */
internal fun keptOfStroke(points: List<Vec2>, plane: StrokeEnds.CutAtPlane?): List<Vec2>? =
    if (plane == null) points else longestRun(points, closed = false, plane.plane, if (plane.above) 1f else -1f)

/** The plane an open curve's ends stay on, or null for a closed curve or one with no plane. */
internal fun socketPlane(ruler: CurveRuler, ends: StrokeEnds): StrokeEnds.CutAtPlane? =
    (ends as? StrokeEnds.CutAtPlane).takeIf { !ruler.closed }

/**
 * The span [line] redraws, or null if it is too far from the curve to be an oversketch. An end on the socket plane reaches
 * the curve's nearest open end however far that is. With [requireEnd] false, the stroke's last point need not be near, as
 * while it is still being drawn.
 */
internal fun locateSpan(
    source: CurveRuler,
    line: List<Vec2>,
    plane: StrokeEnds.CutAtPlane?,
    pixel: Float,
    snapEnds: Boolean,
    requireEnd: Boolean = true,
): OversketchSpan? {
    if (line.size < 2) return null
    val near = nearReach(pixel)
    val startFree = plane != null && line.first().x == plane.plane
    val endFree = plane != null && line.last().x == plane.plane
    val startAt = endSpot(source, line.first(), startFree, near) ?: return null
    val endAt = endSpot(source, line.last(), endFree, if (requireEnd) near else Float.POSITIVE_INFINITY) ?: return null
    if (startAt == endAt) return null
    val forward = if (source.closed) runsForward(source, line, startAt, endAt) else startAt < endAt
    val oriented = if (forward) line else line.reversed()
    val from = if (forward) startAt else endAt
    val to = if (forward) endAt else startAt
    val freeFrom = if (forward) startFree else endFree
    val freeTo = if (forward) endFree else startFree
    if (from <= to) return anchored(source, oriented, from, to, freeFrom, freeTo, snapEnds, pixel)
    // The arc runs over a closed curve's seam, so the curve starts somewhere the stroke does not touch.
    val seam = if (ceil(to) <= floor(from)) ceil(to).toInt() % source.size else floor(from).toInt()
    val size = source.size.toFloat()
    val newFrom = (from - seam).mod(size)
    val newTo = (to - seam).mod(size).let { if (it == 0f) size else it }
    return anchored(source.rotated(seam), oriented, newFrom, newTo, freeFrom, freeTo, snapEnds, pixel)
}

private fun endSpot(ruler: CurveRuler, point: Vec2, onPlane: Boolean, near: Float): Float? {
    if (onPlane) {
        val toFirst = (point - ruler.anchor(0)).magnitude
        val toLast = (point - ruler.anchor(ruler.size)).magnitude
        return if (toFirst <= toLast) 0f else ruler.size.toFloat()
    }
    return ruler.nearest(point).takeIf { it.distance <= near }?.at
}

/** Whether most of [line] lies over the arc going forward from [from] to [to] rather than the one going back. */
private fun runsForward(ruler: CurveRuler, line: List<Vec2>, from: Float, to: Float): Boolean {
    val size = ruler.size.toFloat()
    val arc = (to - from).mod(size)
    var votes = 0
    var cast = 0
    for (i in VOTE_STRIDE until line.lastIndex step VOTE_STRIDE) {
        cast++
        if ((ruler.nearest(line[i]).at - from).mod(size) < arc) votes++
    }
    return if (cast == 0) arc <= size / 2f else votes * 2 >= cast
}

/** Widens the stretch [from] to [to] out to whole sections, which are all a span ever replaces. */
private fun anchored(
    ruler: CurveRuler,
    line: List<Vec2>,
    from: Float,
    to: Float,
    freeFrom: Boolean,
    freeTo: Boolean,
    snapEnds: Boolean,
    pixel: Float,
): OversketchSpan? {
    val reach = snapReach(pixel)
    val start = (if (snapEnds && !freeFrom) snapped(ruler, line.first(), from, reach) else null) ?: from
    val end = (if (snapEnds && !freeTo) snapped(ruler, line.last(), to, reach) else null) ?: to
    val (spanFrom, spanTo) = if (start < end) start to end else from to to
    val first = floor(spanFrom).toInt()
    val last = ceil(spanTo).toInt()
    if (first >= last) return null
    return OversketchSpan(ruler, line, first, last, spanFrom, spanTo, freeFrom, freeTo)
}

private fun snapReach(pixel: Float) = SNAP_PIXELS * pixel

/** The anchor at either end of [at]'s section, if the nearer one lies within [reach] of [point]. */
private fun snapped(ruler: CurveRuler, point: Vec2, at: Float, reach: Float): Float? {
    val before = floor(at)
    val after = ceil(at)
    val toBefore = (point - ruler.anchor(before.toInt())).magnitude
    val toAfter = (point - ruler.anchor(after.toInt())).magnitude
    val (anchor, distance) = if (toBefore <= toAfter) before to toBefore else after to toAfter
    return anchor.takeIf { distance <= reach }
}
