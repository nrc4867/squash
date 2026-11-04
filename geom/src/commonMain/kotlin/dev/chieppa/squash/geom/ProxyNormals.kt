package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.max

/** How far along a run a proxy normal blends on each side: one ring in this many, and at least one. */
private const val BLEND_RINGS_DIVISOR = 12

/** How far a ring's slope along the axis may turn its normals toward the axis, as a ratio to the ring's own normal. */
private const val MAX_SLOPE = 4f
private const val MIN_LENGTH = 1e-6f

/** One ring's ellipse: its half extents, and the section's center and half spans, which its samples are read against. */
private class RingEllipse(val slice: Slice, val samples: List<Vec2>) {
    val middle = Vec2((samples.maxOf { it.x } + samples.minOf { it.x }) / 2f, (samples.maxOf { it.y } + samples.minOf { it.y }) / 2f)
    val halfSpan = Vec2(
        max((samples.maxOf { it.x } - samples.minOf { it.x }) / 2f, MIN_LENGTH),
        max((samples.maxOf { it.y } - samples.minOf { it.y }) / 2f, MIN_LENGTH),
    )
    val across = slice.width / 2f * halfSpan.x
    val rise = slice.height / 2f * halfSpan.y
    val radius get() = (across + rise) / 2f
    val isPoint get() = across < MIN_LENGTH || rise < MIN_LENGTH
}

/**
 * The shading normal at each point of a run lofted from [slices] around [segments] points, three values each. Each ring gives
 * the normal of the ellipse through its extents, in its [Slice.proxyNormalFrame], which ignores a section's small bumps; those
 * are blended over neighbouring rings along the axis, and the ring's slope along the axis turns them toward its ends, so a
 * rounded end shades round. A ring that has closed to a point reads its nearest open neighbour's ellipse.
 */
internal fun proxyNormals(slices: List<Slice>, segments: Int): FloatArray {
    val ellipses = ringEllipses(slices, segments)
    val own = Array(slices.size) { ring -> ellipseNormals(ellipses, ring, segments) }
    val reach = max(1, slices.size / BLEND_RINGS_DIVISOR)
    val slopes = blended(FloatArray(slices.size) { slopeAt(ellipses, it) }, reach)
    val out = FloatArray(slices.size * segments * Patch.COMPONENTS)
    var at = 0
    for (ring in slices.indices) {
        val tangent = slices[ring].proxyNormalFrame.z
        for (segment in 0 until segments) {
            val normal = (blendedAround(own, ring, segment, reach) + tangent * (-slopes[ring])).unitOr(tangent)
            out[at++] = normal.x
            out[at++] = normal.y
            out[at++] = normal.z
        }
    }
    return out
}

private fun ringEllipses(slices: List<Slice>, segments: Int): List<RingEllipse> {
    var sampled: ClosedSpline? = null
    var samples = emptyList<Vec2>()
    return slices.map { slice ->
        if (slice.section !== sampled) {
            sampled = slice.section
            samples = slice.section.sample(segments)
        }
        RingEllipse(slice, samples)
    }
}

/** The unit normals of [ring]'s ellipse, shaped by its nearest neighbour's extents when it has closed to a point. */
private fun ellipseNormals(ellipses: List<RingEllipse>, ring: Int, segments: Int): Array<Vec3> {
    val own = ellipses[ring]
    val shape = if (!own.isPoint) own else ellipses.indices.sortedBy { abs(it - ring) }.map { ellipses[it] }.firstOrNull { !it.isPoint } ?: own
    val frame = own.slice.proxyNormalFrame
    return Array(segments) { segment ->
        val sample = own.samples[segment]
        val x = (sample.x - own.middle.x) / own.halfSpan.x
        val y = (sample.y - own.middle.y) / own.halfSpan.y
        (frame.x * (x * shape.rise) + frame.y * (y * shape.across)).unitOr(frame.y)
    }
}

/** How fast the ring's radius changes along the axis around [ring], held to [MAX_SLOPE]. */
private fun slopeAt(ellipses: List<RingEllipse>, ring: Int): Float {
    val before = ellipses[(ring - 1).coerceAtLeast(0)]
    val after = ellipses[(ring + 1).coerceAtMost(ellipses.lastIndex)]
    val rise = after.radius - before.radius
    val run = (after.slice.frame.center - before.slice.frame.center) dot ellipses[ring].slice.proxyNormalFrame.z
    if (run < MIN_LENGTH) return if (rise > 0f) MAX_SLOPE else if (rise < 0f) -MAX_SLOPE else 0f
    return (rise / run).coerceIn(-MAX_SLOPE, MAX_SLOPE)
}

private fun blended(values: FloatArray, reach: Int) = FloatArray(values.size) { ring ->
    var sum = 0f
    var weights = 0f
    for (other in (ring - reach).coerceAtLeast(0)..(ring + reach).coerceAtMost(values.lastIndex)) {
        val weight = (reach + 1 - abs(other - ring)).toFloat()
        sum += values[other] * weight
        weights += weight
    }
    sum / weights
}

private fun blendedAround(normals: Array<Array<Vec3>>, ring: Int, segment: Int, reach: Int): Vec3 {
    var sum = Vec3.ZERO
    for (other in (ring - reach).coerceAtLeast(0)..(ring + reach).coerceAtMost(normals.lastIndex)) {
        sum += normals[other][segment] * (reach + 1 - abs(other - ring)).toFloat()
    }
    return sum.unitOr(normals[ring][segment])
}

private fun Vec3.unitOr(fallback: Vec3): Vec3 = if (length < MIN_LENGTH) fallback else normalized()

/**
 * [runs], in the order they lie along the axis, with their proxy normals blended where one overlaps the next: at each ring of the
 * overlap both take the same mix of the two, all the first's at the overlap's start and all the next's at its end, so a shadow's
 * edge crosses from one run to the other as one line. Runs with no overlap, or different point counts around, stay as they are.
 */
fun blendedOverlaps(runs: List<Patch>): List<Patch> {
    val out = runs.toMutableList()
    for (i in 0 until out.lastIndex) {
        val first = out[i]
        val next = out[i + 1]
        val start = next.ringUs.first()
        val end = first.ringUs.last()
        if (end <= start || first.nv != next.nv) continue
        val firstNormals = first.proxyNormals ?: continue
        val nextNormals = next.proxyNormals ?: continue
        out[i] = first.withProxyNormals(mixedOver(first, firstNormals, next, nextNormals, start, end, ownShareRises = false))
        out[i + 1] = next.withProxyNormals(mixedOver(next, nextNormals, first, firstNormals, start, end, ownShareRises = true))
    }
    return out
}

/** [own]'s normals, with the rings between [start] and [end] mixed with [other]'s at the same u and v. */
private fun mixedOver(own: Patch, ownNormals: FloatArray, other: Patch, otherNormals: FloatArray, start: Float, end: Float, ownShareRises: Boolean): FloatArray {
    val out = ownNormals.copyOf()
    for (ring in 0 until own.nu) {
        val u = own.ringUs[ring]
        if (u < start || u > end) continue
        val t = (u - start) / (end - start)
        val rising = t * t * (3f - 2f * t)
        val ownShare = if (ownShareRises) rising else 1f - rising
        for (segment in 0 until own.nv) {
            val mine = normalAt(ownNormals, own, ring, segment)
            val mixed = (mine * ownShare + normalBetweenRings(other, otherNormals, u, segment) * (1f - ownShare)).unitOr(mine)
            val at = own.index(ring, segment) * Patch.COMPONENTS
            out[at] = mixed.x
            out[at + 1] = mixed.y
            out[at + 2] = mixed.z
        }
    }
    return out
}

private fun normalAt(normals: FloatArray, patch: Patch, ring: Int, segment: Int): Vec3 {
    val at = patch.index(ring, segment) * Patch.COMPONENTS
    return Vec3(normals[at], normals[at + 1], normals[at + 2])
}

private fun normalBetweenRings(patch: Patch, normals: FloatArray, u: Float, segment: Int): Vec3 {
    val us = patch.ringUs
    val above = us.indexOfFirst { it >= u }.let { if (it < 0) patch.nu - 1 else it }
    val below = (above - 1).coerceAtLeast(0)
    val low = normalAt(normals, patch, below, segment)
    val high = normalAt(normals, patch, above, segment)
    val span = us[above] - us[below]
    if (span < MIN_LENGTH) return high
    val t = ((u - us[below]) / span).coerceIn(0f, 1f)
    return (low * (1f - t) + high * t).unitOr(high)
}
