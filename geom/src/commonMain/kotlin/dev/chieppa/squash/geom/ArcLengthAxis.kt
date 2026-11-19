package dev.chieppa.squash.geom

/** How many places along a curve its arc length is measured at. */
private const val ARC_SAMPLES = 129

/**
 * The smooth curve through [places] scaled about the first so it is [length] long, as an axis whose u is the share of
 * that length: equal steps of u are equal steps along the curve, so rings and joints spaced in u space evenly along it.
 * The curve is the natural cubic spline through the places by chord length ([NaturalSpline]), whose bend changes smoothly
 * through every place, so a tight hook curls evenly and the rings along it turn evenly too.
 */
fun arcLengthAxis(places: List<Vec3>, length: Float): AxisSpline {
    val shape = NaturalSpline(places)
    val samples = List(ARC_SAMPLES) { shape.pointAt(it.toFloat() / (ARC_SAMPLES - 1)) }
    val reached = FloatArray(ARC_SAMPLES)
    for (i in 1 until ARC_SAMPLES) reached[i] = reached[i - 1] + (samples[i] - samples[i - 1]).length
    val total = reached.last()
    require(total > 0f) { "A curve needs a length" }
    val origin = places.first()
    val scale = length / total
    return AxisSpline(samples.map { origin + (it - origin) * scale }, FloatArray(ARC_SAMPLES) { reached[it] / total })
}

/**
 * The C2 cubic spline through [points] with chord-length knots and no bend at either end: each span's second derivatives are
 * solved for together, so the bend is continuous through every point. Two points give the straight line between them.
 */
class NaturalSpline(private val points: List<Vec3>) {
    private val knots = FloatArray(points.size)
    private val bends: List<Vec3>

    init {
        require(points.size >= 2) { "A curve needs at least two points" }
        for (i in 1 until points.size) knots[i] = knots[i - 1] + (points[i] - points[i - 1]).length
        val total = knots.last()
        require(total > 0f) { "A curve needs a length" }
        for (i in knots.indices) knots[i] /= total
        bends = solveBends()
    }

    /** The second derivative at each point: zero at the ends, the tridiagonal system of a natural spline between. */
    private fun solveBends(): List<Vec3> {
        val n = points.size
        if (n < 3) return List(n) { Vec3.ZERO }
        val diagonal = FloatArray(n)
        val upper = FloatArray(n)
        val right = Array(n) { Vec3.ZERO }
        diagonal[0] = 1f
        diagonal[n - 1] = 1f
        for (i in 1 until n - 1) {
            val h0 = knots[i] - knots[i - 1]
            val h1 = knots[i + 1] - knots[i]
            diagonal[i] = 2f * (h0 + h1)
            upper[i] = h1
            right[i] = ((points[i + 1] - points[i]) * (1f / h1) - (points[i] - points[i - 1]) * (1f / h0)) * 6f
        }
        val lower = FloatArray(n) { i -> if (i in 1 until n - 1) knots[i] - knots[i - 1] else 0f }
        for (i in 1 until n) {
            val m = lower[i] / diagonal[i - 1]
            diagonal[i] -= m * upper[i - 1]
            right[i] = right[i] - right[i - 1] * m
        }
        val out = Array(n) { Vec3.ZERO }
        out[n - 1] = right[n - 1] * (1f / diagonal[n - 1])
        for (i in n - 2 downTo 0) out[i] = (right[i] - out[i + 1] * upper[i]) * (1f / diagonal[i])
        return out.toList()
    }

    fun pointAt(u: Float): Vec3 {
        var i = 0
        while (i < knots.size - 2 && u > knots[i + 1]) i++
        val h = knots[i + 1] - knots[i]
        val t = ((u - knots[i]) / h).coerceIn(0f, 1f)
        val a = 1f - t
        val p0 = points[i]
        val p1 = points[i + 1]
        val m0 = bends[i]
        val m1 = bends[i + 1]
        return p0 * a + p1 * t + (m0 * (a * a * a - a) + m1 * (t * t * t - t)) * (h * h / 6f)
    }
}
