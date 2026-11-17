package dev.chieppa.squash.geom

/** How many places along each section the nearest point is first looked for, before it is narrowed down. */
private const val NEAREST_SAMPLES = 32
private const val NEAREST_REFINE_STEPS = 20

/** How densely a section is sampled when two are refitted as one. */
private const val MERGE_SAMPLES_PER_SECTION = 24

/** A place on a path: section [section] at parameter [t], [distance] from what was looked for. */
data class PathSpot(val section: Int, val t: Float, val distance: Float)

/** The place on this path nearest [to]. */
fun BezierPath.nearest(to: Vec2): PathSpot {
    var best = PathSpot(0, 0f, Float.POSITIVE_INFINITY)
    for ((section, segment) in segments.withIndex()) {
        for (i in 0..NEAREST_SAMPLES) {
            val t = i.toFloat() / NEAREST_SAMPLES
            val d = (segment.pointAt(t) - to).magnitude
            if (d < best.distance) best = PathSpot(section, t, d)
        }
    }
    val segment = segments[best.section]
    var step = 1f / NEAREST_SAMPLES
    var t = best.t
    repeat(NEAREST_REFINE_STEPS) {
        step /= 2f
        val lower = (t - step).coerceAtLeast(0f)
        val upper = (t + step).coerceAtMost(1f)
        t = listOf(lower, t, upper).minBy { (segment.pointAt(it) - to).magnitude }
    }
    return PathSpot(best.section, t, (segment.pointAt(t) - to).magnitude)
}

/** This path with section [section] split at [t] into two that trace exactly the same curve, so a new anchor lies there. */
fun BezierPath.splitAt(section: Int, t: Float): BezierPath {
    val segment = segments[section]
    val pieces = listOf(segment.between(0f, t), segment.between(t, 1f))
    return BezierPath(segments.subList(0, section) + pieces + segments.subList(section + 1, segments.size))
}

/**
 * This path without the anchor that ends section [join] - 1 and starts section [join], its two sections refitted as one that
 * leaves and arrives along the old directions at their outer anchors; on a closed path, [join] 0 is the seam. Null for an
 * open path's end, or when too few sections would remain to make a shape.
 */
fun BezierPath.withoutAnchor(join: Int): BezierPath? {
    val closed = segments.first().p0 == segments.last().p3
    if (closed && segments.size < 3) return null
    if (!closed && (join <= 0 || join >= segments.size)) return null
    val ring = if (closed && join == 0) segments.drop(1) + segments.first() else segments
    val at = if (closed && join == 0) segments.lastIndex else join
    val before = ring[at - 1]
    val after = ring[at]
    val samples = before.sample(MERGE_SAMPLES_PER_SECTION) + after.sample(MERGE_SAMPLES_PER_SECTION).drop(1)
    val merged = SingleCubicFit(samples, 0f, closest = true).fit(0, samples.lastIndex, before.leaving(), after.arriving()).curve
        .copy(p0 = before.p0, p3 = after.p3)
    return BezierPath(ring.subList(0, at - 1) + merged + ring.subList(at + 1, ring.size))
}
