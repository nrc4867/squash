package dev.chieppa.squash.geom

/** A natural cubic spline through [points] in order, parameterised 0 to 1 by chord length. */
class AxisSpline(val points: List<Vec3>) {
    private val knots: FloatArray
    private val secondDerivatives: List<Vec3>

    init {
        require(points.size >= 2) { "An axis needs at least two points" }
        knots = FloatArray(points.size)
        for (i in 1 until points.size) knots[i] = knots[i - 1] + (points[i] - points[i - 1]).length
        val total = knots.last()
        require(total > 0f) { "An axis needs a length" }
        for (i in knots.indices) knots[i] /= total
        secondDerivatives = solveSecondDerivatives()
    }

    private fun solveSecondDerivatives(): List<Vec3> {
        val n = points.size
        val upper = FloatArray(n)
        val rhs = Array(n) { Vec3.ZERO }
        for (i in 1 until n - 1) {
            val h0 = knots[i] - knots[i - 1]
            val h1 = knots[i + 1] - knots[i]
            val slope = (points[i + 1] - points[i]) * (1f / h1) - (points[i] - points[i - 1]) * (1f / h0)
            val pivot = 2f * (h0 + h1) - h0 * upper[i - 1]
            upper[i] = h1 / pivot
            rhs[i] = (slope * 6f - rhs[i - 1] * h0) * (1f / pivot)
        }
        val result = Array(n) { Vec3.ZERO }
        for (i in n - 2 downTo 1) result[i] = rhs[i] - result[i + 1] * upper[i]
        return result.toList()
    }

    private fun span(u: Float): Int {
        var i = 0
        while (i < knots.size - 2 && u > knots[i + 1]) i++
        return i
    }

    fun pointAt(u: Float): Vec3 {
        val i = span(u)
        val h = knots[i + 1] - knots[i]
        val a = (knots[i + 1] - u) / h
        val b = (u - knots[i]) / h
        return points[i] * a + points[i + 1] * b +
            (secondDerivatives[i] * (a * a * a - a) + secondDerivatives[i + 1] * (b * b * b - b)) * (h * h / 6f)
    }

    /** The unit direction of the axis at [u]. */
    fun tangentAt(u: Float): Vec3 {
        val i = span(u)
        val h = knots[i + 1] - knots[i]
        val a = (knots[i + 1] - u) / h
        val b = (u - knots[i]) / h
        val derivative = (points[i + 1] - points[i]) * (1f / h) +
            (secondDerivatives[i + 1] * (3f * b * b - 1f) - secondDerivatives[i] * (3f * a * a - 1f)) * (h / 6f)
        return derivative.normalized()
    }

    override fun equals(other: Any?): Boolean = other is AxisSpline && points == other.points

    override fun hashCode(): Int = points.hashCode()
}
