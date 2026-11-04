package dev.chieppa.squash.geom

/** The share of a stroke's length, at each end, over which it eases in from the old curve by default. */
const val DEFAULT_OVERSKETCH_FALLOFF = 0.3f

/** However small the falloff, a joined end eases in over this many screen pixels. */
private const val MIN_BLEND_PIXELS = 8f

/** A closed stroke over this share of a curve redraws all of it, and an eraser's stroke over this share erases it. */
internal const val WHOLE_CURVE_COVERAGE = 0.75f

/**
 * What a redrawn stretch does at its ends: [falloff] is the share of the stroke easing in, [snapEnds] pulls an end onto a
 * nearby anchor. [stroke] cleans up the stroke, all of it for a whole redraw and only its smoothing for a span.
 */
data class OversketchOptions(
    val falloff: Float = DEFAULT_OVERSKETCH_FALLOFF,
    val snapEnds: Boolean = true,
    val stroke: StrokeOptions = StrokeOptions(),
)

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
    options.stroke.half?.let { return halfOversketch(path, samples, ends, pixel, options, it) }
    val ruler = CurveRuler(path.segments)
    val plane = socketPlane(ruler, ends)
    val dense = smoothedOpenLine(samples, pixel, options.stroke.smoothing, options.stroke.marks) ?: return null
    wholeRedraw(ruler, dense, samples, plane ?: StrokeEnds.Closed, pixel, options.stroke)?.let { return it }
    val line = keptOfDrawnStroke(dense, plane, pixel) ?: return null
    val span = locateSpan(ruler, line, plane, pixel, options.snapEnds) ?: return null
    val shape = resample(spanLine(span, options.falloff, pixel), strokeSpacing(pixel))
    if (shape.size < 2) return null
    val fit = fitOpenRun(shape, pixel, startTangent(span), endTangent(span), options.stroke.marks)
    val exact = fit.path.segments.toMutableList().apply {
        this[0] = first().copy(p0 = shape.first())
        this[lastIndex] = last().copy(p3 = shape.last())
    }
    val added = if (plane == null) exact else keptOnPlaneSide(exact, plane, span.freeStart, span.freeEnd)
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
    /** The stretch of the stroke that stays. */
    val drawn: List<Vec2>,
    /** The short runs that would join the stroke's ends to the curve. */
    val connectors: List<List<Vec2>>,
    /** The stretches of the stroke release discards, past the socket plane. */
    val dropped: List<List<Vec2>>,
    /** Whether releasing now would redraw anything; false while the stroke's end is too far from the curve to join it. */
    val joins: Boolean,
    /** What the stroke would make instead of a span, when it would redraw the whole curve. */
    val whole: StrokePreview?,
    /** The same preview for the stroke's mirror image, with Half outline on and that image close enough to join. */
    val mirrored: OversketchPreview? = null,
) {
    /** The span [line] stands the stroke in for; null where it stands in for the whole curve. */
    internal var span: OversketchSpan? = null
}

/** What [oversketch] would touch if the stroke ended here, or null while its start is too far from [path] to join it. */
fun oversketchPreview(
    path: BezierPath,
    samples: List<Vec2>,
    ends: StrokeEnds,
    pixel: Float,
    options: OversketchOptions = OversketchOptions(),
): OversketchPreview? {
    val ruler = CurveRuler(path.segments)
    options.stroke.half?.let { return halfOversketchPreview(ruler, samples, ends, pixel, options, it) }
    return oversketchPreviewOn(ruler, samples, ends, pixel, options)
}

/** [oversketchPreview] over a [ruler] the caller holds. */
internal fun oversketchPreviewOn(
    ruler: CurveRuler,
    samples: List<Vec2>,
    ends: StrokeEnds,
    pixel: Float,
    options: OversketchOptions,
): OversketchPreview? {
    val plane = socketPlane(ruler, ends)
    val dense = smoothedOpenLine(samples, pixel, options.stroke.smoothing, options.stroke.marks)
    if (dense != null && redrawsAll(ruler, dense, pixel)) {
        val whole = strokePreview(samples, plane ?: StrokeEnds.Closed, pixel, options.stroke)
        if (whole.shape.isNotEmpty()) {
            return OversketchPreview(whole.shape, ruler.between(0f, ruler.size.toFloat()), emptyList(), emptyList(), emptyList(), true, whole)
        }
    }
    val run = plane?.let { keptRun(samples, it) ?: return null }
    val line = run?.points ?: samples
    val span = locateSpan(ruler, line, plane, pixel, options.snapEnds, requireEnd = false) ?: return null
    val released = dense?.let { keptOfDrawnStroke(it, plane, pixel) }
    val joins = released != null && locateSpan(ruler, released, plane, pixel, options.snapEnds) != null
    val whole = span.ruler
    val stitched = buildList {
        addAll(whole.between(0f, span.from))
        addAll(span.line)
        addAll(whole.between(span.to, whole.size.toFloat()))
    }
    return OversketchPreview(
        stitched,
        whole.between(span.from, span.to),
        line,
        if (joins) spanConnectors(span) else emptyList(),
        run?.rest?.map { it.line }.orEmpty(),
        joins,
        null,
    ).also { it.span = span }
}

/** The straight runs from the stroke's joined ends to where they meet the curve. */
private fun spanConnectors(span: OversketchSpan): List<List<Vec2>> = buildList {
    if (!span.freeStart) add(listOf(span.line.first(), span.ruler.pointAt(span.from)))
    if (!span.freeEnd) add(listOf(span.line.last(), span.ruler.pointAt(span.to)))
}

/** The kept stretch of a finished stroke; one that comes back on itself counts as the loop it closes, cut once around. */
private fun keptOfDrawnStroke(dense: List<Vec2>, plane: StrokeEnds.CutAtPlane?, pixel: Float): List<Vec2>? {
    if (plane == null) return dense
    val loop = findLoop(dense, closeGap(pixel), strokeSpacing(pixel)) ?: return keptOfStroke(dense, plane)
    return longestRun(loop.line, closed = true, plane.plane, if (plane.above) 1f else -1f)?.points
}

/** Whether [dense] closes on itself over most of the curve, which redraws all of it. */
private fun redrawsAll(ruler: CurveRuler, dense: List<Vec2>, pixel: Float): Boolean =
    findLoop(dense, closeGap(pixel), strokeSpacing(pixel)) != null && ruler.coverage(dense, nearReach(pixel)) >= WHOLE_CURVE_COVERAGE

private fun wholeRedraw(
    ruler: CurveRuler,
    dense: List<Vec2>,
    samples: List<Vec2>,
    ends: StrokeEnds,
    pixel: Float,
    options: StrokeOptions,
): Oversketch? {
    if (!redrawsAll(ruler, dense, pixel)) return null
    val clean = cleanStroke(samples, ends, pixel, options) ?: return null
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
internal fun keptOnPlaneSide(added: List<CubicBezier>, plane: StrokeEnds.CutAtPlane, freeStart: Boolean, freeEnd: Boolean): List<CubicBezier> {
    val sign = if (plane.above) 1f else -1f
    fun held(p: Vec2) = if ((p.x - plane.plane) * sign < 0f) Vec2(plane.plane, p.y) else p
    return added.mapIndexed { i, segment ->
        val first = i == 0
        val last = i == added.lastIndex
        CubicBezier(
            if (first) segment.p0 else held(segment.p0),
            if (first && !freeStart) segment.p1 else held(segment.p1),
            if (last && !freeEnd) segment.p2 else held(segment.p2),
            if (last) segment.p3 else held(segment.p3),
        )
    }
}
