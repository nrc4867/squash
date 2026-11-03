package dev.chieppa.squash.geom

private const val POINTS_FOR_A_FIT = 3
private const val MAX_REPARAMETERIZE = 4
private const val REPARAMETERIZE_SQUARED_FACTOR = 16f
private const val THIRD = 1f / 3f
private const val MIN_HANDLE_OF_CHORD = 1e-3f
private const val MAX_HANDLE_OF_CHORD = 3f
private const val SINGULAR = 1e-12f

/**
 * Joined cubic Béziers through [points] within [tolerance], after Schneider, "An Algorithm for Automatically Fitting
 * Digitized Curves", Graphics Gems (1990): parameterize by chord length, solve the two handle lengths by least squares
 * with the end tangents fixed, reparameterize by Newton-Raphson while the error is near tolerance, and otherwise split at
 * the worst point with a shared tangent there. [startTangent] leaves the first point and [endTangent] leaves the last
 * point back along the curve; each defaults to the direction of the neighbouring point.
 */
fun fitCubics(
    points: List<Vec2>,
    tolerance: Float,
    startTangent: Vec2 = (points[1] - points[0]).unit(),
    endTangent: Vec2 = (points[points.lastIndex - 1] - points.last()).unit(),
): List<CubicBezier> {
    require(points.size >= 2) { "A fit needs at least two points" }
    val segments = mutableListOf<CubicBezier>()
    BezierFitter(points, tolerance * tolerance, segments).fit(0, points.lastIndex, startTangent, endTangent)
    return segments
}

private class BezierFitter(
    private val points: List<Vec2>,
    private val squaredTolerance: Float,
    private val out: MutableList<CubicBezier>,
) {
    fun fit(first: Int, last: Int, startTangent: Vec2, endTangent: Vec2) {
        if (last - first + 1 < POINTS_FOR_A_FIT) {
            out += byChordThirds(points[first], points[last], startTangent, endTangent)
            return
        }
        var parameters = chordLengths(first, last)
        var curve = solveHandles(first, last, parameters, startTangent, endTangent)
        var worst = worstPoint(curve, first, last, parameters)
        if (worst.squaredError <= squaredTolerance * REPARAMETERIZE_SQUARED_FACTOR) {
            var rounds = 0
            while (worst.squaredError > squaredTolerance && rounds < MAX_REPARAMETERIZE) {
                parameters = reparameterized(curve, first, parameters)
                curve = solveHandles(first, last, parameters, startTangent, endTangent)
                worst = worstPoint(curve, first, last, parameters)
                rounds++
            }
        }
        if (worst.squaredError <= squaredTolerance) {
            out += curve
            return
        }
        val tangent = tangentAt(worst.index)
        fit(first, worst.index, startTangent, tangent * -1f)
        fit(worst.index, last, tangent, endTangent)
    }

    private fun tangentAt(index: Int): Vec2 {
        val tangent = ((points[index] - points[index - 1]).unit() + (points[index + 1] - points[index]).unit()).unit()
        return if (tangent == Vec2(0f, 0f)) (points[index + 1] - points[index]).unit() else tangent
    }

    private fun chordLengths(first: Int, last: Int): FloatArray {
        val parameters = FloatArray(last - first + 1)
        for (i in 1 until parameters.size) {
            parameters[i] = parameters[i - 1] + (points[first + i] - points[first + i - 1]).magnitude
        }
        val total = parameters.last()
        if (total > 0f) for (i in parameters.indices) parameters[i] /= total
        return parameters
    }

    /** The normal equations for the handle lengths, falling back to a third of the chord when they say nothing useful. */
    private fun solveHandles(first: Int, last: Int, parameters: FloatArray, start: Vec2, end: Vec2): CubicBezier {
        val p0 = points[first]
        val p3 = points[last]
        var c00 = 0f
        var c01 = 0f
        var c11 = 0f
        var x0 = 0f
        var x1 = 0f
        for (i in parameters.indices) {
            val t = parameters[i]
            val s = 1f - t
            val b0 = s * s * s
            val b1 = 3f * s * s * t
            val b2 = 3f * s * t * t
            val b3 = t * t * t
            val a0 = start * b1
            val a1 = end * b2
            c00 += a0 dotWith a0
            c01 += a0 dotWith a1
            c11 += a1 dotWith a1
            val rest = points[first + i] - (p0 * (b0 + b1) + p3 * (b2 + b3))
            x0 += a0 dotWith rest
            x1 += a1 dotWith rest
        }
        val determinant = c00 * c11 - c01 * c01
        if (determinant > -SINGULAR && determinant < SINGULAR) return byChordThirds(p0, p3, start, end)
        val alpha0 = (x0 * c11 - x1 * c01) / determinant
        val alpha1 = (c00 * x1 - c01 * x0) / determinant
        val reach = maxOf((p3 - p0).magnitude, pathLength(first, last))
        val usable = MIN_HANDLE_OF_CHORD * reach..MAX_HANDLE_OF_CHORD * reach
        if (alpha0 !in usable || alpha1 !in usable) return byChordThirds(p0, p3, start, end)
        return CubicBezier(p0, p0 + start * alpha0, p3 + end * alpha1, p3)
    }

    private fun pathLength(first: Int, last: Int): Float {
        var total = 0f
        for (i in first + 1..last) total += (points[i] - points[i - 1]).magnitude
        return total
    }

    private fun byChordThirds(p0: Vec2, p3: Vec2, start: Vec2, end: Vec2): CubicBezier {
        val handle = (p3 - p0).magnitude * THIRD
        return CubicBezier(p0, p0 + start * handle, p3 + end * handle, p3)
    }

    private fun reparameterized(curve: CubicBezier, first: Int, parameters: FloatArray): FloatArray =
        FloatArray(parameters.size) { i -> newtonStep(curve, points[first + i], parameters[i]) }

    private fun newtonStep(curve: CubicBezier, point: Vec2, t: Float): Float {
        val s = 1f - t
        val d0 = (curve.p1 - curve.p0) * 3f
        val d1 = (curve.p2 - curve.p1) * 3f
        val d2 = (curve.p3 - curve.p2) * 3f
        val first = d0 * (s * s) + d1 * (2f * s * t) + d2 * (t * t)
        val second = (d1 - d0) * (2f * s) + (d2 - d1) * (2f * t)
        val offset = curve.pointAt(t) - point
        val denominator = (first dotWith first) + (offset dotWith second)
        return if (denominator == 0f) t else (t - (offset dotWith first) / denominator).coerceIn(0f, 1f)
    }

    private class Worst(val index: Int, val squaredError: Float)

    /** The point strictly inside the range furthest from where the curve puts it. */
    private fun worstPoint(curve: CubicBezier, first: Int, last: Int, parameters: FloatArray): Worst {
        var index = (first + last) / 2
        var error = 0f
        for (i in first + 1 until last) {
            val offset = curve.pointAt(parameters[i - first]) - points[i]
            val squared = offset dotWith offset
            if (squared >= error) {
                error = squared
                index = i
            }
        }
        return Worst(index, error)
    }
}
