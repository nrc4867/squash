package dev.chieppa.squash.geom

import kotlin.math.sqrt

/** The share of a weight's radius it holds all of its amount across, before easing to none at the edge. */
private const val CORE = 0.6f

/** A stretch of surface adding [amount] to the lines within [radius] of [points], in the same space as what asks. */
class WeightStroke(val points: List<Vec3>, val radius: Float, val amount: Float) {
    internal fun distanceTo(point: Vec3): Float {
        var best = Float.MAX_VALUE
        if (points.size == 1) best = (points[0] - point).let { it dot it }
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val along = points[i] - a
            val span = along dot along
            val t = if (span > 0f) (((point - a) dot along) / span).coerceIn(0f, 1f) else 0f
            val off = a + along * t - point
            best = minOf(best, off dot off)
        }
        return sqrt(best)
    }
}

/**
 * How much a line weighs at [point], from [strokes]: each adds its amount within its radius, easing to none at the edge. Distances
 * are in space, so a seam or a pole needs no case.
 */
fun lineWeightAt(point: Vec3, strokes: List<WeightStroke>): Float {
    var weight = 1f
    for (stroke in strokes) {
        val distance = stroke.distanceTo(point)
        if (distance >= stroke.radius) continue
        val share = ((distance / stroke.radius - CORE) / (1f - CORE)).coerceIn(0f, 1f)
        weight += stroke.amount * (1f - share * share * (3f - 2f * share))
    }
    return weight.coerceAtLeast(0f)
}
