package dev.chieppa.squash.geom

/** The share of a stroke's length, at each end, over which it eases in from the old curve by default. */
const val DEFAULT_OVERSKETCH_FALLOFF = 0.3f

/** However small the falloff, a joined end eases in over this many screen pixels. */
private const val MIN_BLEND_PIXELS = 8f

/** A closed stroke over this share of a curve redraws all of it. */
private const val WHOLE_CURVE_COVERAGE = 0.75f

/** What a redrawn stretch does at its ends: [falloff] is the share of the stroke easing in, [snapEnds] pulls an end onto a nearby anchor. */
data class OversketchOptions(val falloff: Float = DEFAULT_OVERSKETCH_FALLOFF, val snapEnds: Boolean = true)

/**
 * A curve after an oversketch. [replaced] sections gave way to [added] new ones starting at section [firstSection] of
 * [path]; every other section is the old curve's, unchanged. [maxError] is how far the new sections stray from the stroke
 * at worst, in plane units.
 */
data class Oversketch(val path: BezierPath, val firstSection: Int, val replaced: Int, val added: Int, val maxError: Float)

/**
 * [path] with the span a stroke covers redrawn, or null if the stroke is too far from it to be an oversketch. The span
 * widens to whole sections, which keeps the rest exact: anything of those sections the stroke does not reach is re-sampled
 * from the old curve. The new sections leave and reach the kept ones along the kept handles' directions, so the joins have
 * continuous tangents. On an open curve with a socket plane, a stroke past the plane is cut there and sets the open end,
 * so one that crosses it twice redraws the whole outline wherever it runs.
 * A closed stroke over most of the curve redraws it all, as [cleanStroke] would.
 */
fun oversketch(
    path: BezierPath,
    samples: List<Vec2>,
    ends: StrokeEnds,
    pixel: Float,
    options: OversketchOptions = OversketchOptions(),
): Oversketch? {
    val ruler = CurveRuler(path.segments)
    val plane = socketPlane(ruler, ends)
    val dense = smoothedOpenLine(samples, pixel) ?: return null
    wholeRedraw(ruler, dense, samples, plane ?: StrokeEnds.Closed, pixel)?.let { return it }
    val line = keptOfDrawnStroke(dense, plane, pixel) ?: return null
    val span = locateSpan(ruler, line, plane, pixel, options.snapEnds) ?: return null
    val shape = resample(spanLine(span, options.falloff, pixel), strokeSpacing(pixel))
    if (shape.size < 2) return null
    val fit = fitOpenRun(shape, pixel, startTangent(span), endTangent(span))
    val exact = fit.path.segments.toMutableList().apply {
        this[0] = first().copy(p0 = shape.first())
        this[lastIndex] = last().copy(p3 = shape.last())
    }
    val added = if (plane == null) exact else keptOnPlaneSide(exact, plane, span)
    val kept = span.ruler.segments
    return Oversketch(
        BezierPath(kept.subList(0, span.first) + added + kept.subList(span.last, kept.size)),
        span.first,
        span.last - span.first,
        added.size,
        fit.maxError,
    )
}

/** A live view of a stroke over a curve, cheap enough for stroke rate. */
class OversketchPreview(
    /** The whole curve with the stroke standing in for the span it covers. */
    val line: List<Vec2>,
    /** The stretch of the old curve the stroke would replace. */
    val touched: List<Vec2>,
)

/** What [oversketch] would touch if the stroke ended here, or null while its start is too far from [path] to join it. */
fun oversketchPreview(
    path: BezierPath,
    samples: List<Vec2>,
    ends: StrokeEnds,
    pixel: Float,
    options: OversketchOptions = OversketchOptions(),
): OversketchPreview? {
    val ruler = CurveRuler(path.segments)
    val plane = socketPlane(ruler, ends)
    val line = keptOfStroke(samples, plane) ?: return null
    val span = locateSpan(ruler, line, plane, pixel, options.snapEnds, requireEnd = false) ?: return null
    val whole = span.ruler
    val stitched = buildList {
        addAll(whole.between(0f, span.from))
        addAll(span.line)
        addAll(whole.between(span.to, whole.size.toFloat()))
    }
    return OversketchPreview(stitched, whole.between(span.from, span.to))
}

/** The kept stretch of a finished stroke; one that comes back on itself counts as the loop it closes, cut once around. */
private fun keptOfDrawnStroke(dense: List<Vec2>, plane: StrokeEnds.CutAtPlane?, pixel: Float): List<Vec2>? {
    if (plane == null) return dense
    val loop = closeLoop(dense, closeGap(pixel)) ?: return keptOfStroke(dense, plane)
    return longestRun(loop, closed = true, plane.plane, if (plane.above) 1f else -1f)
}

private fun wholeRedraw(ruler: CurveRuler, dense: List<Vec2>, samples: List<Vec2>, ends: StrokeEnds, pixel: Float): Oversketch? {
    if (closeLoop(dense, closeGap(pixel)) == null) return null
    if (ruler.coverage(dense, nearReach(pixel)) < WHOLE_CURVE_COVERAGE) return null
    val clean = cleanStroke(samples, ends, pixel) ?: return null
    return Oversketch(clean.path, 0, ruler.size, clean.path.segments.size, clean.maxError)
}

/** The polyline the new sections are fitted to: what is kept of the old curve in the span's sections, then the eased stroke. */
private fun spanLine(span: OversketchSpan, falloff: Float, pixel: Float): List<Vec2> = buildList {
    val ruler = span.ruler
    addAll(ruler.between(span.first.toFloat(), span.from).dropLast(1))
    addAll(eased(span, falloff, pixel))
    addAll(ruler.between(span.to, span.last.toFloat()).drop(1))
}

/** The stroke eased in from the old curve at each joined end, starting and stopping exactly on it. */
private fun eased(span: OversketchSpan, falloff: Float, pixel: Float): List<Vec2> {
    val line = span.line
    val ruler = span.ruler
    val length = arcLength(line)
    val zone = maxOf(falloff * length, MIN_BLEND_PIXELS * pixel).coerceAtMost(length / 2f)
    var walked = 0f
    return List(line.size) { i ->
        if (i > 0) walked += (line[i] - line[i - 1]).magnitude
        val weight = minOf(
            if (span.freeStart) 1f else smoothstep(walked / zone),
            if (span.freeEnd) 1f else smoothstep((length - walked) / zone),
        )
        when {
            i == 0 && !span.freeStart -> ruler.pointAt(span.from)
            i == line.lastIndex && !span.freeEnd -> ruler.pointAt(span.to)
            weight >= 1f -> line[i]
            else -> {
                val old = ruler.pointAt(ruler.nearest(line[i], span.from, span.to).at)
                old + (line[i] - old) * weight
            }
        }
    }
}

private fun smoothstep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** The direction out of the span's first anchor, carrying on from the kept section before it; null with nothing kept there. */
private fun startTangent(span: OversketchSpan): Vec2? {
    if (span.freeStart) return null
    val ruler = span.ruler
    val kept = when {
        span.first > 0 -> ruler.segments[span.first - 1]
        ruler.closed && span.last < ruler.size -> ruler.segments.last()
        else -> return null
    }
    return directionTo(kept.p3, kept.p2, kept.p1, kept.p0)
}

/** The direction out of the span's last anchor, back along the curve, so opposite the kept section after it; null with nothing kept there. */
private fun endTangent(span: OversketchSpan): Vec2? {
    if (span.freeEnd) return null
    val ruler = span.ruler
    val kept = when {
        span.last < ruler.size -> ruler.segments[span.last]
        ruler.closed && span.first > 0 -> ruler.segments.first()
        else -> return null
    }
    return directionTo(kept.p0, kept.p1, kept.p2, kept.p3)
}

/** The direction to [anchor] from the first of [from] that is not on it. */
private fun directionTo(anchor: Vec2, vararg from: Vec2): Vec2? {
    val other = from.firstOrNull { it != anchor } ?: return null
    return (anchor - other).unit()
}

/** [added] with its inner points on the kept side of [plane]; a joined end's handle keeps the join's direction. */
private fun keptOnPlaneSide(added: List<CubicBezier>, plane: StrokeEnds.CutAtPlane, span: OversketchSpan): List<CubicBezier> {
    val sign = if (plane.above) 1f else -1f
    fun held(p: Vec2) = if ((p.x - plane.plane) * sign < 0f) Vec2(plane.plane, p.y) else p
    return added.mapIndexed { i, segment ->
        val first = i == 0
        val last = i == added.lastIndex
        CubicBezier(
            if (first) segment.p0 else held(segment.p0),
            if (first && !span.freeStart) segment.p1 else held(segment.p1),
            if (last && !span.freeEnd) segment.p2 else held(segment.p2),
            if (last) segment.p3 else held(segment.p3),
        )
    }
}
