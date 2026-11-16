package dev.chieppa.squash.character.shape

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.SurfaceCurve
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** How far round its part, as a share of the way round, a stroke must run for it to close round the part's far side. */
private const val LEAST_SWEEP = 0.15f

/** How many points a curve takes the whole way round a part. */
const val POINTS_ROUND = 24

/** How far along a part, in u, a curve's points lie apart at most. */
private const val MOST_ALONG = 1f / 32f

/**
 * The curve a stroke through [points] makes on a surface: closed where it ends where it began ([meets]); closed round the far side of
 * its part where it starts and ends on one part having run [LEAST_SWEEP] or more round it, as a stroke across what a view shows of a
 * limb rings it; else open. It holds on both sides of the middle when [mirrored]. Its points follow the surface: see [densified].
 */
fun strokeCurve(points: List<Anchor>, meets: Boolean, mirrored: Boolean = false): SurfaceCurve = densified(rawStroke(points, meets, mirrored))

/**
 * [curve] with points added between each two on one part and run that lie far apart, so it follows its part's surface round and along
 * it rather than cutting across: no two lie more than a [POINTS_ROUND]th of the way round apart, or [MOST_ALONG] along.
 */
fun densified(curve: SurfaceCurve): SurfaceCurve {
    val points = curve.points
    if (points.size < 2) return curve
    val spans = points.zipWithNext() + if (curve.closed && points.size > 2) listOf(points.last() to points.first()) else emptyList()
    val out = ArrayList<Anchor>(points.size)
    for ((i, span) in spans.withIndex()) {
        val (a, b) = span
        out += a
        if (a.part != b.part || a.patch != b.patch) continue
        val dv = wrapped(b.v - a.v)
        val du = b.u - a.u
        val steps = ceil(maxOf(abs(dv) * POINTS_ROUND, abs(du) / MOST_ALONG)).toInt()
        for (k in 1 until steps) {
            val t = k.toFloat() / steps
            out += a.copy(u = a.u + du * t, v = unit(a.v + dv * t))
        }
        if (!curve.closed && i == spans.lastIndex) out += b
    }
    if (!curve.closed && spans.isNotEmpty() && out.last() != points.last()) out += points.last()
    return curve.copy(points = out)
}

private fun rawStroke(points: List<Anchor>, meets: Boolean, mirrored: Boolean): SurfaceCurve {
    if (points.size < 2) return SurfaceCurve(points, mirrored = mirrored)
    if (meets) return SurfaceCurve(points, closed = true, mirrored = mirrored)
    val first = points.first()
    val last = points.last()
    if (first.part != last.part || first.patch != last.patch) return SurfaceCurve(points, mirrored = mirrored)
    val sweep = points.zipWithNext().filter { (a, b) -> a.part == first.part && b.part == first.part }.sumOf { (a, b) -> wrapped(b.v - a.v).toDouble() }.toFloat()
    if (abs(sweep) < LEAST_SWEEP) return SurfaceCurve(points, mirrored = mirrored)
    val gap = first.v - last.v
    val rest = if (sweep > 0f) gap - floor(gap) else -((-gap) - floor(-gap))
    val steps = ceil(abs(rest) * POINTS_ROUND).toInt()
    val around = (1 until steps).map { i ->
        val t = i.toFloat() / steps
        Anchor(first.part, first.patch, last.u + (first.u - last.u) * t, unit(last.v + rest * t))
    }
    return SurfaceCurve(points + around, closed = true, mirrored = mirrored)
}

/** A closed curve the whole way round [anchor]'s part at its u, from its v. */
fun ringRound(anchor: Anchor): SurfaceCurve =
    SurfaceCurve(List(POINTS_ROUND) { i -> anchor.copy(v = unit(anchor.v + i.toFloat() / POINTS_ROUND)) }, closed = true)

/** [v] read round, from 0 up to 1. */
private fun unit(v: Float): Float = v - floor(v)

/** [dv] read round as the shorter way, from -0.5 to 0.5. */
private fun wrapped(dv: Float): Float = dv - floor(dv + 0.5f)
