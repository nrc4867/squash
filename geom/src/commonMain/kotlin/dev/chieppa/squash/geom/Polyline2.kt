package dev.chieppa.squash.geom

import kotlin.math.hypot

/** A run of points in the plane, held flat for the loops that read outlines. */
class Polyline2(val xs: FloatArray, val ys: FloatArray) {
    init {
        require(xs.size == ys.size && xs.size >= 2) { "A polyline needs at least two points" }
    }

    /** Twice the area, as a closed loop; positive when it runs counter-clockwise with y up. */
    fun signedArea(): Float {
        var sum = 0f
        for (i in xs.indices) {
            val next = if (i == xs.lastIndex) 0 else i + 1
            sum += xs[i] * ys[next] - xs[next] * ys[i]
        }
        return sum
    }

    fun reversed() = Polyline2(xs.reversedArray(), ys.reversedArray())

    /** [count] points evenly spaced along this polyline as a closed loop, starting at point [from]. */
    fun resampledClosed(from: Int, count: Int): List<Vec2> {
        val n = xs.size
        val lengths = FloatArray(n) { i ->
            val a = (from + i) % n
            val b = (from + i + 1) % n
            hypot(xs[b] - xs[a], ys[b] - ys[a])
        }
        val total = lengths.sum()
        var edge = 0
        var walked = 0f
        return List(count) { k ->
            val target = total * k / count
            while (edge < n - 1 && walked + lengths[edge] < target) walked += lengths[edge++]
            val a = (from + edge) % n
            val b = (from + edge + 1) % n
            val t = if (lengths[edge] > 0f) (target - walked) / lengths[edge] else 0f
            Vec2(xs[a] + (xs[b] - xs[a]) * t, ys[a] + (ys[b] - ys[a]) * t)
        }
    }
}
