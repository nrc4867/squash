package dev.chieppa.squash.geom

/**
 * A C1 cubic spline through [points], with Catmull-Rom tangents: the curve between two points depends only on those
 * points and one neighbour each side, so moving a point changes the curve within two spans of it and nowhere else.
 * [knots] gives each point's u, strictly rising from 0 to 1; left out, u follows chord length.
 */
class AxisSpline(val points: List<Vec3>, knots: FloatArray? = null) {
    private val knots: FloatArray
    private val slopes: List<Vec3>

    init {
        require(points.size >= 2) { "An axis needs at least two points" }
        this.knots = knots?.copyOf() ?: chordLengthKnots(points)
        require(this.knots.size == points.size) { "${this.knots.size} knots for ${points.size} axis points" }
        slopes = points.indices.map { i ->
            val before = (i - 1).coerceAtLeast(0)
            val after = (i + 1).coerceAtMost(points.lastIndex)
            (points[after] - points[before]) * (1f / (this.knots[after] - this.knots[before]))
        }
    }

    private fun span(u: Float): Int {
        var low = 0
        var high = knots.size - 2
        while (low < high) {
            val mid = (low + high + 1) / 2
            if (u >= knots[mid]) low = mid else high = mid - 1
        }
        return low
    }

    fun pointAt(u: Float): Vec3 {
        val i = span(u)
        val h = knots[i + 1] - knots[i]
        val t = ((u - knots[i]) / h).coerceIn(0f, 1f)
        val t2 = t * t
        val t3 = t2 * t
        return points[i] * (2f * t3 - 3f * t2 + 1f) + slopes[i] * ((t3 - 2f * t2 + t) * h) +
            points[i + 1] * (3f * t2 - 2f * t3) + slopes[i + 1] * ((t3 - t2) * h)
    }

    /** The unit direction of the axis at [u]. */
    fun tangentAt(u: Float): Vec3 {
        val i = span(u)
        val h = knots[i + 1] - knots[i]
        val t = ((u - knots[i]) / h).coerceIn(0f, 1f)
        val t2 = t * t
        val chordRate = (6f * t - 6f * t2) / h
        return (
            (points[i + 1] - points[i]) * chordRate + slopes[i] * (3f * t2 - 4f * t + 1f) +
                slopes[i + 1] * (3f * t2 - 2f * t)
            ).normalized()
    }

    override fun equals(other: Any?): Boolean =
        other is AxisSpline && points == other.points && knots.contentEquals(other.knots)

    override fun hashCode(): Int = 31 * points.hashCode() + knots.contentHashCode()

    private companion object {
        fun chordLengthKnots(points: List<Vec3>): FloatArray {
            val knots = FloatArray(points.size)
            for (i in 1 until points.size) knots[i] = knots[i - 1] + (points[i] - points[i - 1]).length
            val total = knots.last()
            require(total > 0f) { "An axis needs a length" }
            for (i in knots.indices) knots[i] /= total
            return knots
        }
    }
}
