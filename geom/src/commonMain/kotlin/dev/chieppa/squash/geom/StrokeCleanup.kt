package dev.chieppa.squash.geom

/** Sample spacing and thresholds, in screen pixels of the view a stroke was drawn in. */
private const val SPACING_PIXELS = 2f
private const val FIT_TOLERANCE_PIXELS = 1f
private const val SIMPLIFY_TOLERANCE_PIXELS = 0.25f
private const val FIT_SPACING_PIXELS = 8f
private const val MIN_LENGTH_PIXELS = 6f
private const val CLOSE_GAP_PIXELS = 16f

private const val MAX_SMOOTH_PASSES = 8

/** How many samples each side of a point decide whether it is a corner, and the turn it takes: 60 degrees. */
private const val CORNER_WINDOW = 4
private const val CORNER_COSINE = 0.5f

/** How many samples either side of a closed shape's seam give its one direction, past the jitter of the nearest few. */
private const val TANGENT_REACH = 3

private const val ERROR_SAMPLES_PER_SEGMENT = 24

/** The board's default for a stroke's smoothing, from 0 to 1. */
const val DEFAULT_STROKE_SMOOTH = 0.45f

/** A stroke as joined cubic Béziers, with how far they stray from the smoothed stroke at worst, in plane units. */
data class CleanStroke(val path: BezierPath, val closed: Boolean, val maxError: Float)

/**
 * Pointer samples to joined cubic Béziers: resample, smooth with corners held, close the loop or cut at the socket plane
 * as [ends] says, simplify, then fit between corners. [pixel] is the size of a screen pixel in the samples' units, so
 * every threshold is what the eye saw. Null for a stroke too short to be one or with nothing on the kept side of the plane.
 */
fun cleanStroke(
    samples: List<Vec2>,
    ends: StrokeEnds,
    pixel: Float,
    smoothing: Float = DEFAULT_STROKE_SMOOTH,
): CleanStroke? {
    val spacing = SPACING_PIXELS * pixel
    if (samples.size < 2 || arcLength(samples) < MIN_LENGTH_PIXELS * pixel) return null
    val dense = resample(samples, spacing)
    val loop = closeLoop(dense, CLOSE_GAP_PIXELS * pixel)
    val passes = (smoothing * MAX_SMOOTH_PASSES).toInt()
    val shape: List<Vec2>
    val closed: Boolean
    when (ends) {
        StrokeEnds.Closed -> {
            shape = resample(smoothed(loop ?: (dense + dense.first()), closed = true, passes), spacing)
            closed = true
        }
        is StrokeEnds.CutAtPlane -> {
            val source = smoothed(loop ?: dense, closed = loop != null, passes)
            val cut = cutAtPlane(source, loop != null, ends) ?: return null
            if (arcLength(cut) < MIN_LENGTH_PIXELS * pixel) return null
            shape = resample(cut, spacing)
            closed = false
        }
    }
    val segments = fitBetweenCorners(shape, closed, pixel)
    return CleanStroke(BezierPath(segments), closed, maxError(shape, segments))
}

private fun smoothed(points: List<Vec2>, closed: Boolean, passes: Int): List<Vec2> {
    val corners = cornerIndices(points, CORNER_WINDOW, CORNER_COSINE, closed).toSet()
    val pinned = if (closed && 0 in corners) corners + points.lastIndex else corners
    return smooth(points, passes, closed, pinned)
}

private fun fitBetweenCorners(shape: List<Vec2>, closed: Boolean, pixel: Float): List<CubicBezier> {
    val corners = cornerIndices(shape, CORNER_WINDOW, CORNER_COSINE, closed)
    if (closed && corners.isEmpty()) return fitSeamless(shape, pixel)
    val ring = if (closed) rotatedToStartAt(shape, corners.first()) else shape
    val cuts = if (closed) {
        val first = corners.first()
        corners.map { it - first } + ring.lastIndex
    } else {
        listOf(0) + corners + ring.lastIndex
    }
    return cuts.zipWithNext().flatMap { (from, to) -> fitSection(ring.subList(from, to + 1), pixel) }
}

private fun fitSeamless(ring: List<Vec2>, pixel: Float): List<CubicBezier> {
    val reach = TANGENT_REACH.coerceAtMost((ring.size - 1) / 2)
    val tangent = (ring[reach] - ring[ring.lastIndex - reach]).unit()
    return fitCubics(fitPoints(simplified(ring, pixel), pixel), FIT_TOLERANCE_PIXELS * pixel, tangent, tangent * -1f)
}

/** A section's end directions are the first and last edges of its simplified line, long on a straight run. */
private fun fitSection(section: List<Vec2>, pixel: Float): List<CubicBezier> {
    val line = simplified(section, pixel)
    val start = (line[1] - line[0]).unit()
    val end = (line[line.lastIndex - 1] - line.last()).unit()
    return fitCubics(fitPoints(line, pixel), FIT_TOLERANCE_PIXELS * pixel, start, end)
}

private fun simplified(points: List<Vec2>, pixel: Float) = simplify(points, SIMPLIFY_TOLERANCE_PIXELS * pixel)

/** The points a fit answers to: evenly along the simplified line, so a long straight run still holds the curve to it. */
private fun fitPoints(line: List<Vec2>, pixel: Float) = resample(line, FIT_SPACING_PIXELS * pixel)

/** A closed polyline, first point repeated last, starting at [index] instead. */
private fun rotatedToStartAt(ring: List<Vec2>, index: Int): List<Vec2> =
    ring.subList(index, ring.lastIndex) + ring.subList(0, index + 1)

/** The furthest any of [points] lies from the flattened [segments]. */
private fun maxError(points: List<Vec2>, segments: List<CubicBezier>): Float {
    val flat = segments.flatMap { it.sample(ERROR_SAMPLES_PER_SEGMENT) }
    return points.maxOf { point -> (1 until flat.size).minOf { distanceToSegment(point, flat[it - 1], flat[it]) } }
}
