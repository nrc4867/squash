package dev.chieppa.squash.geom

/** Sample spacing and thresholds, in screen pixels of the view a stroke was drawn in. */
private const val SPACING_PIXELS = 2f
private const val FIT_TOLERANCE_PIXELS = 1f
private const val SIMPLIFY_TOLERANCE_PIXELS = 0.25f
private const val FIT_SPACING_PIXELS = 8f
private const val MIN_LENGTH_PIXELS = 6f
private const val SNAP_LINE_PIXELS = 2f
private const val CLOSE_GAP_PIXELS = 16f
private const val NEAR_PIXELS = 24f

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
 * Pointer samples to joined cubic Béziers: the line [strokePreview] gives, simplified and fitted between corners. [pixel]
 * is the size of a screen pixel in the samples' units, so every threshold is what the eye saw. Null for a stroke too short
 * to be one or with nothing on the kept side of the plane.
 */
fun cleanStroke(samples: List<Vec2>, ends: StrokeEnds, pixel: Float, options: StrokeOptions = StrokeOptions()): CleanStroke? {
    if (samples.isEmpty()) return null
    val preview = strokePreview(samples, ends, pixel, options.copy(snapLine = false))
    val shape = preview.shape
    if (shape.isEmpty()) return null
    val segments = fitBetweenCorners(shape, preview.closed, pixel, options.snapLine)
    return CleanStroke(BezierPath(segments), preview.closed, maxError(shape, segments))
}

internal fun smoothed(points: List<Vec2>, closed: Boolean, passes: Int): List<Vec2> {
    val corners = cornerIndices(points, CORNER_WINDOW, CORNER_COSINE, closed).toSet()
    val pinned = if (closed && 0 in corners) corners + points.lastIndex else corners
    return smooth(points, passes, closed, pinned)
}

/** The sections of [shape] between its corners; null for a closed shape with none, which has no ends to fit from. */
private fun cornerSections(shape: List<Vec2>, closed: Boolean): List<List<Vec2>>? {
    val corners = cornerIndices(shape, CORNER_WINDOW, CORNER_COSINE, closed)
    if (closed && corners.isEmpty()) return null
    val ring = if (closed) rotatedToStartAt(shape, corners.first()) else shape
    val cuts = if (closed) {
        val first = corners.first()
        corners.map { it - first } + ring.lastIndex
    } else {
        listOf(0) + corners + ring.lastIndex
    }
    return cuts.zipWithNext().map { (from, to) -> ring.subList(from, to + 1) }
}

/** Whether every point of [section] lies so close to its chord that it counts as a straight line. */
private fun isLine(section: List<Vec2>, pixel: Float): Boolean {
    val tolerance = SNAP_LINE_PIXELS * pixel
    return section.first() != section.last() && section.all { distanceToSegment(it, section.first(), section.last()) <= tolerance }
}

/** [shape] with each section between corners that is a line down to its two ends. */
internal fun straightened(shape: List<Vec2>, closed: Boolean, pixel: Float): List<Vec2> {
    val sections = cornerSections(shape, closed) ?: return shape
    return buildList {
        add(sections.first().first())
        for (section in sections) if (isLine(section, pixel)) add(section.last()) else addAll(section.drop(1))
    }
}

private fun fitBetweenCorners(shape: List<Vec2>, closed: Boolean, pixel: Float, snapLine: Boolean): List<CubicBezier> {
    val sections = cornerSections(shape, closed) ?: return fitSeamless(shape, pixel)
    return sections.flatMap { section ->
        if (snapLine && isLine(section, pixel)) listOf(straightCubic(section.first(), section.last())) else fitSection(section, pixel)
    }
}

private fun fitSeamless(ring: List<Vec2>, pixel: Float): List<CubicBezier> {
    val reach = TANGENT_REACH.coerceAtMost((ring.size - 1) / 2)
    val tangent = (ring[reach] - ring[ring.lastIndex - reach]).unit()
    return fitCubics(fitPoints(simplified(ring, pixel), pixel), FIT_TOLERANCE_PIXELS * pixel, tangent, tangent * -1f)
}

/** A section's end directions are the first and last edges of its simplified line, long on a straight run. */
private fun fitSection(section: List<Vec2>, pixel: Float, startTangent: Vec2? = null, endTangent: Vec2? = null): List<CubicBezier> {
    val line = simplified(section, pixel)
    val start = startTangent ?: (line[1] - line[0]).unit()
    val end = endTangent ?: (line[line.lastIndex - 1] - line.last()).unit()
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

/** The spacing strokes are resampled to, for a screen pixel of size [pixel]. */
internal fun strokeSpacing(pixel: Float): Float = SPACING_PIXELS * pixel

/** The length under which a stroke is no stroke. */
internal fun strokeMinLength(pixel: Float): Float = MIN_LENGTH_PIXELS * pixel

/** The smoothing filter's passes for a [smoothing] from 0 to 1. */
internal fun smoothPasses(smoothing: Float): Int = (smoothing * MAX_SMOOTH_PASSES).toInt()

/** How close a stroke's ends must come to close it. */
internal fun closeGap(pixel: Float): Float = CLOSE_GAP_PIXELS * pixel

/** How far from a curve a stroke's end still joins it. */
internal fun nearReach(pixel: Float): Float = NEAR_PIXELS * pixel

/** [samples] resampled and smoothed with corners held, both ends where they were. Null for a stroke too short to be one. */
internal fun smoothedOpenLine(samples: List<Vec2>, pixel: Float, smoothing: Float = DEFAULT_STROKE_SMOOTH): List<Vec2>? {
    if (samples.size < 2 || arcLength(samples) < strokeMinLength(pixel)) return null
    return smoothed(resample(samples, strokeSpacing(pixel)), closed = false, smoothPasses(smoothing))
}

/** An open run fitted between its corners. A given tangent leaves that end as [fitCubics] takes it; otherwise the run's own direction does. */
internal fun fitOpenRun(shape: List<Vec2>, pixel: Float, startTangent: Vec2?, endTangent: Vec2?): CleanStroke {
    val cuts = listOf(0) + cornerIndices(shape, CORNER_WINDOW, CORNER_COSINE) + shape.lastIndex
    val pieces = cuts.zipWithNext()
    val segments = pieces.flatMapIndexed { i, (from, to) ->
        fitSection(shape.subList(from, to + 1), pixel, startTangent.takeIf { i == 0 }, endTangent.takeIf { i == pieces.lastIndex })
    }
    return CleanStroke(BezierPath(segments), false, maxError(shape, segments))
}
