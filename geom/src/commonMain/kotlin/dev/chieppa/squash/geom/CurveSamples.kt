package dev.chieppa.squash.geom

import kotlin.math.ceil

private const val LENGTH_STEPS = 32

/**
 * Joined cubics as [points] about [spacing] apart along each section, every anchor among them, with where each lies on the
 * curve, so a stretch between two of them can be cut back out of the sections exactly.
 */
internal class CurveSamples(private val path: BezierPath, val spacing: Float) {
    private val sections: IntArray
    private val ts: FloatArray
    val points: List<Vec2>

    init {
        val atSection = mutableListOf<Int>()
        val atT = mutableListOf<Float>()
        val found = mutableListOf<Vec2>()
        path.segments.forEachIndexed { index, segment ->
            for (t in evenParameters(segment, spacing)) {
                atSection += index
                atT += t
                found += segment.pointAt(t)
            }
        }
        atSection += path.segments.lastIndex
        atT += 1f
        found += path.segments.last().p3
        sections = atSection.toIntArray()
        ts = atT.toFloatArray()
        points = found
    }

    /** The sections and parts of sections from sample [from] to sample [to]. */
    fun piecesBetween(from: Int, to: Int): List<CubicBezier> = buildList {
        for (section in sections[from]..sections[to]) {
            val a = if (section == sections[from]) ts[from] else 0f
            val b = if (section == sections[to]) ts[to] else 1f
            if (a < b) add(path.segments[section].between(a, b))
        }
    }

    /** The curve's direction going forward out of sample [i], which is not the last. */
    fun leaving(i: Int): Vec2 = path.segments[sections[i]].between(ts[i], 1f).leaving()

    /** The direction back along the curve from sample [i], which is not the first. */
    fun arriving(i: Int): Vec2 =
        if (ts[i] == 0f) path.segments[sections[i] - 1].arriving() else path.segments[sections[i]].between(0f, ts[i]).arriving()
}

/** The t values that part [segment] about [spacing] apart along its length, from 0 and short of 1. */
private fun evenParameters(segment: CubicBezier, spacing: Float): List<Float> {
    val lengths = FloatArray(LENGTH_STEPS + 1)
    var previous = segment.p0
    for (step in 1..LENGTH_STEPS) {
        val point = segment.pointAt(step.toFloat() / LENGTH_STEPS)
        lengths[step] = lengths[step - 1] + (point - previous).magnitude
        previous = point
    }
    val parts = ceil(lengths.last() / spacing).toInt().coerceAtLeast(1)
    var step = 0
    return List(parts) { part ->
        val wanted = lengths.last() * part / parts
        while (step < LENGTH_STEPS - 1 && lengths[step + 1] < wanted) step++
        val edge = lengths[step + 1] - lengths[step]
        val share = if (edge > 0f) (wanted - lengths[step]) / edge else 0f
        (step + share) / LENGTH_STEPS
    }
}

private fun CubicBezier.leaving(): Vec2 = ((listOf(p1, p2, p3).firstOrNull { it != p0 } ?: p0) - p0).unit()

private fun CubicBezier.arriving(): Vec2 = ((listOf(p2, p1, p0).firstOrNull { it != p3 } ?: p3) - p3).unit()
