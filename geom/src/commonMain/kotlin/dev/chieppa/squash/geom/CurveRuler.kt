package dev.chieppa.squash.geom

import kotlin.math.ceil
import kotlin.math.floor

private const val STEPS_PER_SEGMENT = 16

/** Where a point lies nearest a curve: [at] is the segment index plus t along it, [distance] how far off it is. */
internal class CurveSpot(val at: Float, val distance: Float)

/** Joined cubics measured by a parameter that runs from 0 at the first point to one per segment at the last. */
internal class CurveRuler(val segments: List<CubicBezier>) {
    val size: Int = segments.size
    val closed: Boolean = segments.first().p0 == segments.last().p3

    private val flat: List<Vec2> = buildList {
        add(segments.first().p0)
        for (segment in segments) for (step in 1..STEPS_PER_SEGMENT) add(segment.pointAt(step.toFloat() / STEPS_PER_SEGMENT))
    }

    /** The anchor before section [index], or after the last section at [size]. */
    fun anchor(index: Int): Vec2 = if (index >= size) segments.last().p3 else segments[index].p0

    fun pointAt(at: Float): Vec2 {
        if (at >= size) return segments.last().p3
        val index = at.toInt()
        val t = at - index
        return if (t == 0f) segments[index].p0 else segments[index].pointAt(t)
    }

    /** The spot nearest [point] with its parameter within [from] to [to]. */
    fun nearest(point: Vec2, from: Float = 0f, to: Float = size.toFloat()): CurveSpot {
        val first = floor(from * STEPS_PER_SEGMENT).toInt().coerceIn(0, flat.lastIndex - 1)
        val last = ceil(to * STEPS_PER_SEGMENT).toInt().coerceIn(first + 1, flat.lastIndex)
        var best = Float.POSITIVE_INFINITY
        var bestAt = from
        for (i in first until last) {
            val a = flat[i]
            val along = flat[i + 1] - a
            val square = along dotWith along
            val t = if (square == 0f) 0f else (((point - a) dotWith along) / square).coerceIn(0f, 1f)
            val distance = (point - (a + along * t)).magnitude
            if (distance < best) {
                best = distance
                bestAt = (i + t) / STEPS_PER_SEGMENT
            }
        }
        return CurveSpot(bestAt.coerceIn(from, to), best)
    }

    /** The curve from parameter [from] to [to] as a polyline, both ends on it. */
    fun between(from: Float, to: Float): List<Vec2> = buildList {
        add(pointAt(from))
        val first = floor(from * STEPS_PER_SEGMENT).toInt() + 1
        val last = ceil(to * STEPS_PER_SEGMENT).toInt() - 1
        for (i in first..last) add(flat[i])
        if (to > from) add(pointAt(to))
    }

    /** The share of this curve within [reach] of some point of [line]. */
    fun coverage(line: List<Vec2>, reach: Float): Float =
        flat.count { point -> line.any { (it - point).magnitude <= reach } }.toFloat() / flat.size

    /** The closed curve starting at anchor [index] instead. */
    fun rotated(index: Int): CurveRuler = CurveRuler(segments.subList(index, size) + segments.subList(0, index))
}
