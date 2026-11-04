package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

private const val FLATNESS_DIVISOR = 8.0
private const val MAX_STEPS_PER_SPAN = 512
private const val MIN_CHORD = 1e-9

/** A place in u and in v unwrapped, so that v runs on past 1 across a seam. */
internal data class UnwrappedUv(val u: Double, val v: Double)

/** Each of [points] with v moved by whole turns to lie the short way round from the one before, on a closed patch. */
internal fun unwrapped(points: List<SurfaceUv>, closed: Boolean): List<UnwrappedUv> {
    val out = ArrayList<UnwrappedUv>(points.size)
    for (point in points) {
        val v = point.v.toDouble()
        val previous = out.lastOrNull()
        out += if (closed && previous != null) UnwrappedUv(point.u.toDouble(), v - floor(v - previous.v + 0.5)) else UnwrappedUv(point.u.toDouble(), v)
    }
    return out
}

private fun List<UnwrappedUv>.withoutRepeats(): List<UnwrappedUv> {
    val out = ArrayList<UnwrappedUv>(size)
    for (point in this) {
        val previous = out.lastOrNull()
        if (previous == null || abs(previous.u - point.u) > MIN_CHORD || abs(previous.v - point.v) > MIN_CHORD) out += point
    }
    return out
}

/**
 * A curve through [points], smooth at each of them, as straight edges no further than [tolerance] in the surface's units from
 * it. Without [turn] it runs from the first point to the last. With it the curve closes, running on from the last point to the
 * first moved by [turn] whole values of v, and that point ends the result. Spans are centripetal, so uneven spacing does not
 * overshoot.
 */
internal fun flattenedThrough(points: List<UnwrappedUv>, grid: PatchGrid, tolerance: Float, turn: Double? = null): List<UnwrappedUv> {
    val through = points.withoutRepeats()
    if (through.size < 2) return through
    val n = through.size
    fun at(i: Int): UnwrappedUv {
        if (turn == null) return through[i.coerceIn(0, n - 1)]
        val wraps = floor(i.toDouble() / n).toInt()
        val base = through[i - wraps * n]
        return UnwrappedUv(base.u, base.v + wraps * turn)
    }
    val out = ArrayList<UnwrappedUv>()
    out += through.first()
    val spans = if (turn == null) n - 1 else n
    for (span in 0 until spans) flattenSpan(at(span - 1), at(span), at(span + 1), at(span + 2), grid, tolerance, out)
    return out
}

/** Adds a span of the curve from [p1] to [p2], with [p0] and [p3] either side of it, leaving out its first point. */
private fun flattenSpan(p0: UnwrappedUv, p1: UnwrappedUv, p2: UnwrappedUv, p3: UnwrappedUv, grid: PatchGrid, tolerance: Float, out: MutableList<UnwrappedUv>) {
    val rates = grid.ratesAt((p1.u + p2.u) / 2.0, (p1.v + p2.v) / 2.0)
    val scaleU = rates.alongU.toDouble()
    val scaleV = rates.aroundV.toDouble()
    fun gap(a: UnwrappedUv, b: UnwrappedUv): Double {
        val du = (b.u - a.u) * scaleU
        val dv = (b.v - a.v) * scaleV
        return sqrt(sqrt(du * du + dv * dv)).coerceAtLeast(MIN_CHORD)
    }
    val d0 = gap(p0, p1)
    val d1 = gap(p1, p2)
    val d2 = gap(p2, p3)
    fun tangent(previous: Double, here: Double, next: Double, before: Double, after: Double) =
        ((here - previous) / before - (next - previous) / (before + after) + (next - here) / after) * d1
    val startU = if (p0 == p1) p2.u - p1.u else tangent(p0.u, p1.u, p2.u, d0, d1)
    val startV = if (p0 == p1) p2.v - p1.v else tangent(p0.v, p1.v, p2.v, d0, d1)
    val endU = if (p3 == p2) p2.u - p1.u else tangent(p1.u, p2.u, p3.u, d1, d2)
    val endV = if (p3 == p2) p2.v - p1.v else tangent(p1.v, p2.v, p3.v, d1, d2)
    val steps = stepsFor(p2.u - p1.u, p2.v - p1.v, startU, startV, endU, endV, scaleU, scaleV, tolerance)
    for (step in 1 until steps) {
        val t = step.toDouble() / steps
        out += UnwrappedUv(hermite(p1.u, p2.u, startU, endU, t), hermite(p1.v, p2.v, startV, endV, t))
    }
    out += p2
}

/** How many straight edges keep a span within [tolerance], from the largest bend its cubic has, which is at one of its ends. */
private fun stepsFor(
    moveU: Double,
    moveV: Double,
    startU: Double,
    startV: Double,
    endU: Double,
    endV: Double,
    scaleU: Double,
    scaleV: Double,
    tolerance: Float,
): Int {
    val fromU = (6.0 * moveU - 4.0 * startU - 2.0 * endU) * scaleU
    val fromV = (6.0 * moveV - 4.0 * startV - 2.0 * endV) * scaleV
    val toU = (-6.0 * moveU + 2.0 * startU + 4.0 * endU) * scaleU
    val toV = (-6.0 * moveV + 2.0 * startV + 4.0 * endV) * scaleV
    val most = maxOf(sqrt(fromU * fromU + fromV * fromV), sqrt(toU * toU + toV * toV))
    return ceil(sqrt(most / (FLATNESS_DIVISOR * tolerance))).toInt().coerceIn(1, MAX_STEPS_PER_SPAN)
}

private fun hermite(p: Double, q: Double, start: Double, end: Double, t: Double): Double {
    val tt = t * t
    val ttt = tt * t
    return p * (2.0 * ttt - 3.0 * tt + 1.0) + q * (-2.0 * ttt + 3.0 * tt) + start * (ttt - 2.0 * tt + t) + end * (ttt - tt)
}
