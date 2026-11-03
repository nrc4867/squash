package dev.chieppa.squash.geom

import kotlin.math.abs

private const val CROSSING_INTERVALS = 32
private const val BISECTION_STEPS = 24
private const val JOIN_TOLERANCE = 1e-4f
private const val FLATTEN_STEPS = 16

/** An infinite line in the plane through [point] along [direction]. */
data class Line2(val point: Vec2, val direction: Vec2) {
    /** Positive on one side of the line, negative on the other, proportional to the distance. */
    fun side(p: Vec2): Float {
        val d = p - point
        return d.x * direction.y - d.y * direction.x
    }
}

data class CubicBezier(val p0: Vec2, val p1: Vec2, val p2: Vec2, val p3: Vec2) {
    fun pointAt(t: Float): Vec2 {
        val s = 1f - t
        return p0 * (s * s * s) + p1 * (3f * s * s * t) + p2 * (3f * s * t * t) + p3 * (t * t * t)
    }

    /** [count] points evenly spaced in t, both ends included. */
    fun sample(count: Int): List<Vec2> {
        require(count >= 2) { "A sample needs at least both ends" }
        return List(count) { pointAt(it.toFloat() / (count - 1)) }
    }

    /** The t values where the curve crosses [line], ascending. A touch without a sign change is not a crossing. */
    fun crossings(line: Line2): List<Float> {
        val result = mutableListOf<Float>()
        var t0 = 0f
        var side0 = line.side(pointAt(t0))
        if (side0 == 0f) result += t0
        for (i in 1..CROSSING_INTERVALS) {
            val t1 = i.toFloat() / CROSSING_INTERVALS
            val side1 = line.side(pointAt(t1))
            when {
                side1 == 0f -> result += t1
                side0 != 0f && (side0 < 0f) != (side1 < 0f) -> result += bisect(line, t0, t1, side0)
            }
            t0 = t1
            side0 = side1
        }
        return result
    }

    private fun bisect(line: Line2, start: Float, end: Float, startSide: Float): Float {
        var low = start
        var high = end
        repeat(BISECTION_STEPS) {
            val mid = (low + high) / 2f
            if ((line.side(pointAt(mid)) < 0f) == (startSide < 0f)) low = mid else high = mid
        }
        return (low + high) / 2f
    }
}

/** Joined cubic Béziers, as an outline is stored. */
data class BezierPath(val segments: List<CubicBezier>) {
    /** The points where the path crosses [line], in path order; a crossing on a join counts once. */
    fun crossings(line: Line2): List<Vec2> {
        val points = mutableListOf<Vec2>()
        for (segment in segments) {
            for (t in segment.crossings(line)) {
                val p = segment.pointAt(t)
                val last = points.lastOrNull()
                if (last == null || abs(last.x - p.x) + abs(last.y - p.y) > JOIN_TOLERANCE) points += p
            }
        }
        return points
    }

    /** This path as straight edges, [stepsPerSegment] to a segment. */
    fun flatten(stepsPerSegment: Int = FLATTEN_STEPS): Polyline2 {
        val count = segments.size * stepsPerSegment + 1
        val xs = FloatArray(count)
        val ys = FloatArray(count)
        var o = 0
        for ((index, segment) in segments.withIndex()) {
            val first = if (index == 0) 0 else 1
            for (step in first..stepsPerSegment) {
                val t = step.toFloat() / stepsPerSegment
                val s = 1f - t
                val b0 = s * s * s
                val b1 = 3f * s * s * t
                val b2 = 3f * s * t * t
                val b3 = t * t * t
                xs[o] = b0 * segment.p0.x + b1 * segment.p1.x + b2 * segment.p2.x + b3 * segment.p3.x
                ys[o] = b0 * segment.p0.y + b1 * segment.p1.y + b2 * segment.p2.y + b3 * segment.p3.y
                o++
            }
        }
        return Polyline2(xs, ys)
    }
}
