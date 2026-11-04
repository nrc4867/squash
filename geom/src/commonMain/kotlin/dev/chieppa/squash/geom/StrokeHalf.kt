package dev.chieppa.squash.geom

import kotlin.math.abs

/** The part's centre line in a view's plane: Half outline mirrors a stroke across it. */
enum class CentreLine {
    /** The line y = 0: the part's axis, in a view that runs along it. */
    ALONG_X,

    /** The line x = 0: down the middle of a view that looks along the axis. */
    ALONG_Y;

    /** How far [p] lies off the line, signed by its side. */
    fun across(p: Vec2): Float = if (this == ALONG_X) p.y else p.x

    /** Where [p] drops square onto the line. */
    fun onLine(p: Vec2): Vec2 = if (this == ALONG_X) Vec2(p.x, 0f) else Vec2(0f, p.y)

    /** [p] on the line's other side; a point on the line stays exactly where it is. */
    fun mirror(p: Vec2): Vec2 = if (this == ALONG_X) Vec2(p.x, 0f - p.y) else Vec2(0f - p.x, p.y)

    /** The unit direction square off the line, on the side of [p]. */
    fun awayTo(p: Vec2): Vec2 {
        val positive = across(p) >= 0f
        return if (this == ALONG_X) Vec2(0f, if (positive) 1f else -1f) else Vec2(if (positive) 1f else -1f, 0f)
    }
}

/** A stroke's longest stretch on one side of a centre line, and the [rest] of it. */
internal class OneSide(val kept: List<Vec2>, val rest: List<List<Vec2>>)

/** [line] split where it crosses [centre], with the crossings exactly on the line. Null with nothing long enough to keep. */
internal fun oneSide(line: List<Vec2>, centre: CentreLine): OneSide? {
    val runs = mutableListOf<List<Vec2>>()
    var run = mutableListOf(line.first())
    for (i in 1 until line.size) {
        val from = centre.across(line[i - 1])
        val to = centre.across(line[i])
        if ((from >= 0f) != (to >= 0f)) {
            val crossing = centre.onLine(line[i - 1] + (line[i] - line[i - 1]) * (from / (from - to)))
            run += crossing
            runs += run
            run = mutableListOf(crossing)
        }
        run += line[i]
    }
    runs += run
    val kept = runs.filter { it.size >= 2 }.maxByOrNull(::arcLength) ?: return null
    return OneSide(kept, runs.filter { it !== kept && it.size >= 2 })
}

/**
 * Half outline's preview: the hand's edge, what keeps to one side of the centre line and of a socket plane, joined to its
 * mirror image at the end furthest from the plane, or with no plane the end nearest the line. Where that end stops short
 * of the line a square run carries it there. With Close end on, the other end closes too: along x to the plane, as a
 * stroke's own end does, or square to the line. Everything release adds to the hand's edge, its mirror image first, shows
 * as a connector.
 */
internal fun halfPreview(samples: List<Vec2>, ends: StrokeEnds, pixel: Float, options: StrokeOptions, centre: CentreLine): StrokePreview {
    val nothing = StrokePreview(emptyList(), false, emptyList(), emptyList(), listOf(samples), samples.first(), false)
    if (samples.size < 2 || arcLength(samples) < strokeMinLength(pixel)) return nothing
    val spacing = strokeSpacing(pixel)
    val plane = ends as? StrokeEnds.CutAtPlane
    val side = oneSide(resample(samples, spacing), centre) ?: return nothing
    val run = plane?.let { keptRun(side.kept, it) ?: return nothing }
    val hand = oriented(run?.points ?: side.kept, plane, centre)
    if (arcLength(hand) < strokeMinLength(pixel)) return nothing
    val closed = plane == null && options.closeEnd
    val startRun = if (closed) squareRun(centre, hand.first(), spacing).reversed() else listOf(hand.first())
    val endRun = squareRun(centre, hand.last(), spacing)
    val edge = startRun.dropLast(1) + hand + endRun.drop(1)
    val whole = edge + edge.reversed().drop(1).map(centre::mirror)
    val marks = mirroredMarks(options.marks, centre)
    val line = smoothed(whole, closed, smoothPasses(options.smoothing), marks, pixel)
    val handFrom = startRun.lastIndex
    val handTo = handFrom + hand.lastIndex
    val onPlane = plane?.takeIf { options.closeEnd }?.plane
    val startStub = onPlane?.takeIf { line.first().x != it }?.let { listOf(Vec2(it, line.first().y), line.first()) }
    val endStub = onPlane?.takeIf { line.last().x != it }?.let { listOf(line.last(), Vec2(it, line.last().y)) }
    val shape = resample(listOfNotNull(startStub?.first()) + line + listOfNotNull(endStub?.last()), spacing)
    val gap = closeGap(pixel)
    return StrokePreview(
        if (options.snapLine) straightened(shape, closed, pixel, marks) else shape,
        closed,
        listOf(line.subList(handFrom, handTo + 1)),
        buildList {
            if (handFrom > 0) add(line.subList(0, handFrom + 1))
            add(line.subList(handTo, line.size))
            startStub?.let(::add)
            endStub?.let(::add)
        },
        side.rest + run?.rest?.map { it.line }.orEmpty(),
        samples.first(),
        options.closeEnd && abs(centre.across(hand.last())) <= gap && (plane != null || abs(centre.across(hand.first())) <= gap),
    )
}

/** [hand] running toward the end that joins its mirror image. */
private fun oriented(hand: List<Vec2>, plane: StrokeEnds.CutAtPlane?, centre: CentreLine): List<Vec2> {
    val keepsDirection = if (plane != null) {
        abs(hand.last().x - plane.plane) >= abs(hand.first().x - plane.plane)
    } else {
        abs(centre.across(hand.last())) <= abs(centre.across(hand.first()))
    }
    return if (keepsDirection) hand else hand.reversed()
}

/** The evenly spaced run square from [from] onto [centre]. */
private fun squareRun(centre: CentreLine, from: Vec2, spacing: Float): List<Vec2> {
    val to = centre.onLine(from)
    return if (from == to) listOf(from) else resample(listOf(from, to), spacing)
}

/**
 * The fit of a mirrored [shape]: its first half between corners, and exactly that half's mirror image, so the outline is
 * symmetric to the bit. Where it meets the centre line with no corner the fit leaves square to the line, so the join is smooth.
 */
internal fun fitMirrored(shape: List<Vec2>, closed: Boolean, centre: CentreLine, pixel: Float, options: StrokeOptions): List<CubicBezier> {
    val half = firstHalf(shape, closed, centre)
    val join = half.lastIndex
    val corners = strokeCorners(shape, closed, mirroredMarks(options.marks, centre), pixel)
    val joinCorner = corners.any { abs(it - join) <= CORNER_WINDOW }
    val seamCorner = closed && corners.any { it <= CORNER_WINDOW || it >= shape.lastIndex - CORNER_WINDOW }
    val inner = corners.filter { it < join - CORNER_WINDOW && (!closed || it > CORNER_WINDOW) }
    val cuts = (listOf(0) + inner + join).zipWithNext()
    val startTangent = if (closed && !seamCorner) centre.awayTo(half[1.coerceAtMost(join)]) else null
    val endTangent = if (joinCorner) null else centre.awayTo(half[(join - 1).coerceAtLeast(0)])
    val first = cuts.flatMapIndexed { i, (from, to) ->
        val section = half.subList(from, to + 1)
        if (options.snapLine && isLine(section, pixel)) {
            listOf(straightCubic(section.first(), section.last()))
        } else {
            fitSection(section, pixel, startTangent.takeIf { i == 0 }, endTangent.takeIf { i == cuts.lastIndex })
        }
    }
    return first + first.asReversed().map { CubicBezier(centre.mirror(it.p3), centre.mirror(it.p2), centre.mirror(it.p1), centre.mirror(it.p0)) }
}

/** [shape] from its start to halfway round, where a mirrored shape meets its centre line; the ends on the line lie exactly on it. */
private fun firstHalf(shape: List<Vec2>, closed: Boolean, centre: CentreLine): List<Vec2> {
    var left = arcLength(shape) / 2f
    val half = mutableListOf(if (closed) centre.onLine(shape.first()) else shape.first())
    for (i in 1 until shape.size) {
        val edge = (shape[i] - shape[i - 1]).magnitude
        if (edge >= left) {
            half += centre.onLine(shape[i - 1] + (shape[i] - shape[i - 1]) * (if (edge > 0f) left / edge else 0f))
            return half
        }
        left -= edge
        half += shape[i]
    }
    return half
}

private fun OversketchOptions.mirroredAcross(centre: CentreLine) =
    copy(stroke = stroke.copy(marks = stroke.marks.map { it.copy(at = centre.mirror(it.at)) }))

/** [path] redrawn by the hand's edge and by that edge's mirror image, as one change. The image stays out if it is too far to join. */
internal fun halfOversketch(
    path: BezierPath,
    samples: List<Vec2>,
    ends: StrokeEnds,
    pixel: Float,
    options: OversketchOptions,
    centre: CentreLine,
): Oversketch? {
    if (samples.size < 2) return null
    val hand = oneSide(samples, centre)?.kept ?: return null
    val plain = options.copy(stroke = options.stroke.copy(half = null))
    val first = oversketch(path, hand, ends, pixel, plain) ?: return null
    val second = oversketch(first.path, hand.map(centre::mirror), ends, pixel, plain.mirroredAcross(centre)) ?: return first
    return Oversketch(
        second.path,
        minOf(first.firstSection, second.firstSection),
        first.replaced + second.replaced,
        first.added + second.added,
        maxOf(first.maxError, second.maxError),
    )
}

/** [oversketchPreview] with Half outline on: what the hand's edge and its mirror image would each redraw, as one curve. */
internal fun halfOversketchPreview(
    ruler: CurveRuler,
    samples: List<Vec2>,
    ends: StrokeEnds,
    pixel: Float,
    options: OversketchOptions,
    centre: CentreLine,
): OversketchPreview? {
    if (samples.size < 2) return null
    val side = oneSide(samples, centre) ?: return null
    val plain = options.copy(stroke = options.stroke.copy(half = null))
    val hand = oversketchPreviewOn(ruler, side.kept, ends, pixel, plain) ?: return null
    val image = side.kept.map(centre::mirror)
    val joined = oversketchPreviewOn(ruler, image, ends, pixel, plain.mirroredAcross(centre))?.takeIf { it.joins }
    val dropped = hand.dropped + side.rest + if (joined == null) listOf(image) else emptyList()
    return OversketchPreview(stitched(ruler, hand, joined), hand.touched, hand.drawn, hand.connectors, dropped, hand.joins, hand.whole, joined)
}

/** The curve with both strokes standing in for their spans, or with the first alone where the spans overlap or a seam moved. */
private fun stitched(ruler: CurveRuler, first: OversketchPreview, second: OversketchPreview?): List<Vec2> {
    val a = first.span
    val b = second?.span
    if (a == null || b == null || a.ruler !== ruler || b.ruler !== ruler) return first.line
    val (low, high) = if (a.from <= b.from) a to b else b to a
    if (low.to > high.from) return first.line
    return buildList {
        addAll(ruler.between(0f, low.from))
        addAll(low.line)
        addAll(ruler.between(low.to, high.from))
        addAll(high.line)
        addAll(ruler.between(high.to, ruler.size.toFloat()))
    }
}
