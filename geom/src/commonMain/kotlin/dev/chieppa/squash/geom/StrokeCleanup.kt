package dev.chieppa.squash.geom

import kotlin.math.pow
import kotlin.math.sqrt

/** Sample spacing and thresholds, in screen pixels of the view a stroke was drawn in. */
private const val SPACING_PIXELS = 2f
/** How far a faithful fit may stray from its stroke, in screen pixels. */
private const val FAITHFUL_FIT_PIXELS = 1f

/** How far a fit may stray at a fit of 0, smooth, and just short of 1, in [FIT_SPAN_PARTS]ths of the curve's longest span. */
private const val SMOOTH_FIT_PARTS = 16f
private const val FINE_FIT_PARTS = 1f

/** Fit measures how far a curve may stray in this many parts of its longest span, so a small part is as smooth as a large one. */
const val FIT_SPAN_PARTS = 300f

/** In a loose fit, corners closer than this along the stroke, in screen pixels, count as one. */
private const val MIN_CORNER_GAP_PIXELS = 32f

/** A loose fit takes a section's end directions this many screen pixels along it, past the hand's jitter at a corner. */
private const val END_DIRECTION_PIXELS = 16f
private const val SIMPLIFY_TOLERANCE_PIXELS = 0.25f
private const val FIT_SPACING_PIXELS = 8f
private const val MIN_LENGTH_PIXELS = 6f
private const val SNAP_LINE_PIXELS = 2f
private const val CLOSE_GAP_PIXELS = 16f
private const val NEAR_PIXELS = 24f

/** The filter's passes per unit of Smooth, and all it reaches at the slider's top: a blur of 32 pixels, standard deviation. */
private const val SMOOTH_PASSES_PER_UNIT = 8f
private const val MAX_SMOOTH_PASSES = 512f

/** How many samples each side of a point decide whether it is a corner, and the turn it takes: 60 degrees. */
internal const val CORNER_WINDOW = 4
internal const val CORNER_COSINE = 0.5f

/** How many samples either side of a closed shape's seam give its one direction, past the jitter of the nearest few. */
private const val TANGENT_REACH = 3

private const val ERROR_SAMPLES_PER_SEGMENT = 24

/** The board's default for a stroke's smoothing, from 0 to 1. */
const val DEFAULT_STROKE_SMOOTH = 0.45f

/** A stroke's fit at its most faithful: every point of the curve within a screen pixel of the smoothed stroke. */
const val FAITHFUL_FIT = 1f

/** The default fit, from 0, smooth, to 1, faithful: a limb's outline to about four to eight points. */
const val DEFAULT_STROKE_FIT = 0.6f

/**
 * How far a curve fitted at [fit] may stray from its stroke, as a share of the curve's longest span: 16 [FIT_SPAN_PARTS]ths
 * at 0, falling evenly in scale towards 1 at 1.
 */
fun fitShareOfSpan(fit: Float): Float =
    SMOOTH_FIT_PARTS * (FINE_FIT_PARTS / SMOOTH_FIT_PARTS).pow(fit.coerceIn(0f, 1f)) / FIT_SPAN_PARTS

/**
 * How far, in plane units, a curve fitted at [fit] may stray from its stroke, for a screen pixel of size [pixel] and a curve
 * whose longest span is [span]: a screen pixel at Faithful, otherwise [fitShareOfSpan] of the span and never under a pixel.
 */
fun fitTolerance(fit: Float, pixel: Float, span: Float): Float =
    if (fit >= FAITHFUL_FIT) FAITHFUL_FIT_PIXELS * pixel else maxOf(FAITHFUL_FIT_PIXELS * pixel, fitShareOfSpan(fit) * span)

/** The longer of the spans of [points] in x and y. */
fun longestSpan(points: List<Vec2>): Float =
    if (points.isEmpty()) 0f else maxOf(points.maxOf { it.x } - points.minOf { it.x }, points.maxOf { it.y } - points.minOf { it.y })

/** The longer of the spans of [path] in x and y. */
fun longestSpan(path: BezierPath): Float = path.flatten().let { flat -> longestSpan(flat.xs.indices.map { Vec2(flat.xs[it], flat.ys[it]) }) }

/** A stroke as joined cubic Béziers, with how far they stray from the smoothed stroke at worst, in plane units. */
data class CleanStroke(val path: BezierPath, val closed: Boolean, val maxError: Float)

/**
 * Pointer samples to joined cubic Béziers: the line [strokePreview] gives, simplified and fitted between corners. [pixel]
 * is the size of a screen pixel in the samples' units, so every threshold is what the eye saw. Null for a stroke too short
 * to be one, a loop that closes on itself before it encloses anything, or one with nothing on the kept side of the plane.
 */
fun cleanStroke(samples: List<Vec2>, ends: StrokeEnds, pixel: Float, options: StrokeOptions = StrokeOptions()): CleanStroke? {
    if (samples.isEmpty()) return null
    val preview = strokePreview(samples, ends, pixel, options.copy(snapLine = false))
    val shape = preview.shape
    if (shape.isEmpty() || preview.closed && simplified(shape, pixel).distinct().size < MIN_RING_POINTS) return null
    val tolerance = options.tolerance(pixel, longestSpan(shape))
    val segments = options.half?.let { fitMirrored(shape, preview.closed, it, pixel, options, tolerance) }
        ?: fitBetweenCorners(shape, preview.closed, pixel, options, tolerance)
    return CleanStroke(BezierPath(segments), preview.closed, maxError(shape, segments))
}

/** Evenly spaced [shape] fitted between its corners, with how far the fit strays. */
internal fun fitCleaned(shape: List<Vec2>, closed: Boolean, pixel: Float, snapLine: Boolean = true): CleanStroke {
    val segments = fitBetweenCorners(shape, closed, pixel, StrokeOptions(snapLine = snapLine), faithfulTolerance(pixel))
    return CleanStroke(BezierPath(segments), closed, maxError(shape, segments))
}

internal fun smoothed(points: List<Vec2>, closed: Boolean, passes: Int, marks: List<StrokeMark> = emptyList(), pixel: Float = 0f): List<Vec2> {
    val corners = cornersUnderBlur(points, closed, marks, pixel, cornerWindow(passes)).toSet()
    val pinned = if (closed && 0 in corners) corners + points.lastIndex else corners
    return smooth(points, passes, closed, pinned)
}

/** How many samples either side a corner must still turn across to hold through [passes] of the filter: as wide as the blur. */
private fun cornerWindow(passes: Int): Int = maxOf(CORNER_WINDOW, sqrt(passes / 2f).toInt())

/** The sections of [shape] between its corners; null for a closed shape with none, which has no ends to fit from. */
private fun cornerSections(shape: List<Vec2>, closed: Boolean, marks: List<StrokeMark>, pixel: Float, loose: Boolean = false): List<List<Vec2>>? {
    val corners = strokeCorners(shape, closed, marks, pixel).let { if (loose) spreadOut(it, shape, closed, pixel) else it }
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

/**
 * [corners] of [shape] with each that lies within [MIN_CORNER_GAP_PIXELS] along it of the corner before, or of an open
 * end, left out, so a kink where the hand closes a loop or turns back makes one corner rather than a bunch of short sections.
 */
private fun spreadOut(corners: List<Int>, shape: List<Vec2>, closed: Boolean, pixel: Float): List<Int> {
    if (corners.isEmpty()) return corners
    val gap = MIN_CORNER_GAP_PIXELS * pixel
    val along = FloatArray(shape.size)
    for (i in 1 until shape.size) along[i] = along[i - 1] + (shape[i] - shape[i - 1]).magnitude
    val total = along.last()
    val kept = mutableListOf<Int>()
    for (corner in corners) {
        if (!closed && (along[corner] < gap || total - along[corner] < gap)) continue
        if (kept.isNotEmpty() && along[corner] - along[kept.last()] < gap) continue
        kept += corner
    }
    if (closed && kept.size > 1 && total - along[kept.last()] + along[kept.first()] < gap) kept.removeAt(kept.lastIndex)
    return kept
}

/** Whether every point of [section] lies so close to its chord that it counts as a straight line. */
internal fun isLine(section: List<Vec2>, pixel: Float): Boolean {
    val tolerance = SNAP_LINE_PIXELS * pixel
    return section.first() != section.last() && section.all { distanceToSegment(it, section.first(), section.last()) <= tolerance }
}

/** [shape] with each section between corners that is a line down to its two ends. */
internal fun straightened(shape: List<Vec2>, closed: Boolean, pixel: Float, marks: List<StrokeMark> = emptyList()): List<Vec2> {
    val sections = cornerSections(shape, closed, marks, pixel) ?: return shape
    return buildList {
        add(sections.first().first())
        for (section in sections) if (isLine(section, pixel)) add(section.last()) else addAll(section.drop(1))
    }
}

private fun fitBetweenCorners(shape: List<Vec2>, closed: Boolean, pixel: Float, options: StrokeOptions, tolerance: Float): List<CubicBezier> {
    val sections = cornerSections(shape, closed, options.marks, pixel, tolerance > faithfulTolerance(pixel)) ?: return fitSeamless(shape, pixel, tolerance)
    return sections.flatMap { section ->
        if (options.snapLine && isLine(section, pixel)) listOf(straightCubic(section.first(), section.last())) else fitSection(section, pixel, tolerance)
    }
}

private fun fitSeamless(ring: List<Vec2>, pixel: Float, tolerance: Float): List<CubicBezier> {
    val reach = TANGENT_REACH.coerceAtMost((ring.size - 1) / 2)
    val tangent = (ring[reach] - ring[ring.lastIndex - reach]).unit()
    return fitterFor(tolerance, pixel)(fitPoints(simplified(ring, pixel), pixel), tolerance, tangent, tangent * -1f)
}

/** The fewest distinct points a simplified closed stroke has around it to enclose anything. */
private const val MIN_RING_POINTS = 3

/** A screen pixel, as a faithful fit's tolerance in plane units. */
internal fun faithfulTolerance(pixel: Float): Float = FAITHFUL_FIT_PIXELS * pixel

/**
 * [section] fitted within [tolerance] in plane units. A faithful fit's end directions are the first and last edges of its
 * simplified line, long on a straight run; a looser fit's run from each end to the point [END_DIRECTION_PIXELS] along it,
 * so a little jitter at an end does not set a direction no long cubic can leave in.
 */
internal fun fitSection(section: List<Vec2>, pixel: Float, tolerance: Float, startTangent: Vec2? = null, endTangent: Vec2? = null): List<CubicBezier> {
    val line = simplified(section, pixel)
    val loose = tolerance > faithfulTolerance(pixel)
    val reach = END_DIRECTION_PIXELS * pixel
    val start = startTangent ?: if (loose) directionAlong(line, reach) else (line[1] - line[0]).unit()
    val end = endTangent ?: if (loose) directionAlong(line.asReversed(), reach) else (line[line.lastIndex - 1] - line.last()).unit()
    return fitterFor(tolerance, pixel)(fitPoints(line, pixel), tolerance, start, end)
}

/** The direction from the first of [line] to its point [reach] along it, or a quarter of the way along a shorter line. */
private fun directionAlong(line: List<Vec2>, reach: Float): Vec2 {
    val within = minOf(reach, arcLength(line) / 4f)
    var travelled = 0f
    for (i in 1 until line.size) {
        travelled += (line[i] - line[i - 1]).magnitude
        if (travelled >= within) return (line[i] - line[0]).unit()
    }
    return (line[1] - line[0]).unit()
}

/**
 * At a faithful tolerance, the fit splits each cubic at its worst point, which keeps a curve close to everything along it;
 * looser, it reaches each cubic as far as it can, for the fewest points.
 */
private fun fitterFor(tolerance: Float, pixel: Float): (List<Vec2>, Float, Vec2, Vec2) -> List<CubicBezier> =
    if (tolerance <= faithfulTolerance(pixel)) ::fitCubics else ::fitFewCubics

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

/** The smoothing filter's passes for a [smoothing] from 0 to 1: steady up to the default, then climbing as a cube to the top. */
internal fun smoothPasses(smoothing: Float): Int {
    val steady = smoothing * SMOOTH_PASSES_PER_UNIT
    val climb = ((smoothing - DEFAULT_STROKE_SMOOTH) / (1f - DEFAULT_STROKE_SMOOTH)).coerceAtLeast(0f)
    return (steady + (MAX_SMOOTH_PASSES - SMOOTH_PASSES_PER_UNIT) * climb * climb * climb).toInt()
}

/** How close a stroke's ends must come to close it. */
internal fun closeGap(pixel: Float): Float = CLOSE_GAP_PIXELS * pixel

/** How far from a curve a stroke's end still joins it. */
internal fun nearReach(pixel: Float): Float = NEAR_PIXELS * pixel

/** [samples] resampled and smoothed with corners held, both ends where they were. Null for a stroke too short to be one. */
internal fun smoothedOpenLine(samples: List<Vec2>, pixel: Float, smoothing: Float = DEFAULT_STROKE_SMOOTH, marks: List<StrokeMark> = emptyList()): List<Vec2>? {
    if (samples.size < 2 || arcLength(samples) < strokeMinLength(pixel)) return null
    return smoothed(resample(samples, strokeSpacing(pixel)), closed = false, smoothPasses(smoothing), marks, pixel)
}

/** An open run fitted between its corners. A given tangent leaves that end as [fitCubics] takes it; otherwise the run's own direction does. */
internal fun fitOpenRun(shape: List<Vec2>, pixel: Float, tolerance: Float, startTangent: Vec2?, endTangent: Vec2?, marks: List<StrokeMark> = emptyList()): CleanStroke {
    val segments = fitOpenSegments(shape, pixel, tolerance, startTangent, endTangent, marks)
    return CleanStroke(BezierPath(segments), false, maxError(shape, segments))
}

/** The sections of [fitOpenRun], for a caller with no use for how far they stray. */
internal fun fitOpenSegments(shape: List<Vec2>, pixel: Float, tolerance: Float, startTangent: Vec2?, endTangent: Vec2?, marks: List<StrokeMark> = emptyList()): List<CubicBezier> {
    val corners = strokeCorners(shape, closed = false, marks, pixel)
    val cuts = listOf(0) + (if (tolerance > faithfulTolerance(pixel)) spreadOut(corners, shape, closed = false, pixel) else corners) + shape.lastIndex
    val pieces = cuts.zipWithNext()
    return pieces.flatMapIndexed { i, (from, to) ->
        fitSection(shape.subList(from, to + 1), pixel, tolerance, startTangent.takeIf { i == 0 }, endTangent.takeIf { i == pieces.lastIndex })
    }
}
