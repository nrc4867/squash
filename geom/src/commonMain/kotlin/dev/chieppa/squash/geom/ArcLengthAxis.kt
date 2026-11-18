package dev.chieppa.squash.geom

/** How many places along a curve its arc length is measured at. */
private const val ARC_SAMPLES = 129

/**
 * The smooth curve through [places] scaled about the first so it is [length] long, as an axis whose u is the share of
 * that length: equal steps of u are equal steps along the curve, so rings and joints spaced in u space evenly along it.
 */
fun arcLengthAxis(places: List<Vec3>, length: Float): AxisSpline {
    val shape = AxisSpline(places)
    val samples = List(ARC_SAMPLES) { shape.pointAt(it.toFloat() / (ARC_SAMPLES - 1)) }
    val reached = FloatArray(ARC_SAMPLES)
    for (i in 1 until ARC_SAMPLES) reached[i] = reached[i - 1] + (samples[i] - samples[i - 1]).length
    val total = reached.last()
    require(total > 0f) { "A curve needs a length" }
    val origin = places.first()
    val scale = length / total
    return AxisSpline(samples.map { origin + (it - origin) * scale }, FloatArray(ARC_SAMPLES) { reached[it] / total })
}
