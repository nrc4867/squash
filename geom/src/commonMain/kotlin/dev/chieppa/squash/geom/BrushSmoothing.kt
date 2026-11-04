package dev.chieppa.squash.geom

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.min

/** How far toward the blurred curve a dab at full strength moves a point at the brush's centre. */
private const val DAB_SHARE = 0.5f

/** The blur's standard deviation as a share of the brush's radius, and how many of them its window reaches. */
private const val BLUR_OF_RADIUS = 0.5f
private const val BLUR_REACH = 3f

/** How far apart a drag lays its dabs, as a share of the radius, so the result does not depend on how fast it goes. */
private const val DAB_SPACING_OF_RADIUS = 0.25f

/** A point moving less than this off the curve's own line, in screen pixels, stays exactly where it was. */
private const val MIN_MOVE_PIXELS = 0.02f

/** How many samples either side of a point give the curve's direction there. */
private const val DIRECTION_REACH = 3

/** A brush in the plane's units; [strength] runs from 0 to 1. */
data class SmoothBrush(val radius: Float, val strength: Float)

/**
 * A curve a smoothing brush is working on. It holds the curve as dense samples for the whole drag, so a drag's dabs add up
 * on the samples rather than on refits of refits. Only samples inside the brush move. An open curve's end samples hold still,
 * and a closed curve's seam moves as one point. [path] is the old curve wherever samples did not move, cut where they start
 * to, and a fit to the samples where they did. A pixel is a screen pixel's size in the plane's units.
 */
class BrushedPath private constructor(
    private val original: BezierPath,
    private val samples: CurveSamples,
    private val moved: List<Vec2>,
    private val plane: StrokeEnds.CutAtPlane?,
    private val pixel: Float,
) {
    private val closed = moved.first() == moved.last()
    private val count = if (closed) moved.lastIndex else moved.size

    constructor(path: BezierPath, ends: StrokeEnds, pixel: Float) :
        this(path, CurveSamples(path, strokeSpacing(pixel)), ends, pixel)

    private constructor(path: BezierPath, samples: CurveSamples, ends: StrokeEnds, pixel: Float) : this(
        path,
        samples,
        samples.points,
        (ends as? StrokeEnds.CutAtPlane).takeIf { samples.points.first() != samples.points.last() },
        pixel,
    )

    /** The curve as the brush has left it. */
    val path: BezierPath by lazy { rebuilt() }

    /** One touch of [brush] at [at]. */
    fun dab(at: Vec2, brush: SmoothBrush): BrushedPath {
        val radius = brush.radius
        if (radius <= 0f || brush.strength <= 0f) return this
        val kernel = blurKernel(radius * BLUR_OF_RADIUS / samples.spacing)
        val minMove = MIN_MOVE_PIXELS * pixel
        val next = moved.toMutableList()
        var changed = false
        for (i in movable()) {
            val gap = moved[i] - at
            val inside = 1f - (gap dotWith gap) / (radius * radius)
            if (inside <= 0f) continue
            val share = brush.strength * DAB_SHARE * inside * inside
            val pull = (blurredAt(i, kernel) - moved[i]) * share
            val along = direction(i)
            val off = pull - along * (pull dotWith along)
            if (off.magnitude < minMove) continue
            next[i] = moved[i] + pull
            changed = true
        }
        if (!changed) return this
        if (closed) next[next.lastIndex] = next[0]
        return BrushedPath(original, samples, next, plane, pixel)
    }

    /** Dabs of [brush] along the line from [from] to [to], [from] itself left out as the drag's last touch. */
    fun drag(from: Vec2, to: Vec2, brush: SmoothBrush): BrushedPath {
        val distance = (to - from).magnitude
        if (distance == 0f || brush.radius <= 0f) return this
        val dabs = ceil(distance / (brush.radius * DAB_SPACING_OF_RADIUS)).toInt()
        var current = this
        for (i in 1..dabs) current = current.dab(from + (to - from) * (i.toFloat() / dabs), brush)
        return current
    }

    private fun movable(): IntRange = if (closed) 0 until count else 1 until moved.lastIndex

    private fun around(i: Int): Int = if (closed) i.mod(count) else i

    /** How many samples either side of [i] there are to use, so a window stays even up to an open end. */
    private fun room(i: Int): Int = if (closed) (count - 1) / 2 else min(i, moved.lastIndex - i)

    private fun blurredAt(i: Int, kernel: FloatArray): Vec2 {
        val reach = min(kernel.lastIndex, room(i))
        var total = kernel[0]
        var x = moved[i].x * total
        var y = moved[i].y * total
        for (k in 1..reach) {
            val weight = kernel[k]
            val before = moved[around(i - k)]
            val after = moved[around(i + k)]
            x += (before.x + after.x) * weight
            y += (before.y + after.y) * weight
            total += 2f * weight
        }
        return Vec2(x / total, y / total)
    }

    private fun direction(i: Int): Vec2 {
        val reach = min(DIRECTION_REACH, room(i))
        return (moved[around(i + reach)] - moved[around(i - reach)]).unit()
    }

    private fun rebuilt(): BezierPath {
        val runs = movedRuns()
        if (runs.isEmpty()) return original
        val out = mutableListOf<CubicBezier>()
        var from = 0
        for (run in runs) {
            out += kept(from, run.first)
            out += fitted(run)
            from = run.last
        }
        out += kept(from, moved.lastIndex)
        return BezierPath(out)
    }

    /** The stretches of samples that moved, each with the still sample either side of it, or a curve's end. */
    private fun movedRuns(): List<IntRange> {
        val runs = mutableListOf<IntRange>()
        var i = 0
        while (i < moved.size) {
            if (moved[i] == samples.points[i]) {
                i++
                continue
            }
            val first = (i - 1).coerceAtLeast(0)
            while (i < moved.size && moved[i] != samples.points[i]) i++
            val last = i.coerceAtMost(moved.lastIndex)
            val joined = runs.lastOrNull()?.takeIf { it.last > first }
            if (joined != null) runs[runs.lastIndex] = joined.first..last else runs += first..last
        }
        return runs
    }

    /** The old curve from sample [from] to sample [to]. */
    private fun kept(from: Int, to: Int): List<CubicBezier> {
        if (from >= to) return emptyList()
        val pieces = samples.piecesBetween(from, to).toMutableList()
        pieces[0] = pieces[0].copy(p0 = samples.points[from])
        pieces[pieces.lastIndex] = pieces.last().copy(p3 = samples.points[to])
        return pieces
    }

    private fun fitted(run: IntRange): List<CubicBezier> {
        val shape = resample(moved.subList(run.first, run.last + 1), samples.spacing)
        val start = startDirection(run.first)
        val end = endDirection(run.last)
        val fit = if (shape.first() == shape.last()) fittedRing(shape, start, end) else fittedLine(shape, start, end)
        return if (plane == null) fit else keptOnPlaneSide(fit, plane, start == null, end == null)
    }

    /** A run all the way round starts and stops at one point, which has no chord to fit from: two halves do. */
    private fun fittedRing(shape: List<Vec2>, start: Vec2?, end: Vec2?): List<CubicBezier> {
        val middle = shape.size / 2
        val reach = min(DIRECTION_REACH, min(middle, shape.lastIndex - middle))
        val through = (shape[middle + reach] - shape[middle - reach]).unit()
        return fittedLine(shape.subList(0, middle + 1), start, through * -1f) + fittedLine(shape.subList(middle, shape.size), through, end)
    }

    private fun fittedLine(shape: List<Vec2>, start: Vec2?, end: Vec2?): List<CubicBezier> =
        fitOpenSegments(shape, pixel, start, end).toMutableList().apply {
            this[0] = first().copy(p0 = shape.first())
            this[lastIndex] = last().copy(p3 = shape.last())
        }

    /** The direction a run leaves sample [i] in: the seam's, or the old curve's so it carries on from what stays. Free at an open end. */
    private fun startDirection(i: Int): Vec2? = when {
        i > 0 -> samples.leaving(i)
        !closed -> null
        moved[0] != samples.points[0] -> seamDirection()
        else -> samples.leaving(0)
    }

    /** The direction back along a run from its last sample [i]; as [startDirection]. */
    private fun endDirection(i: Int): Vec2? = when {
        i < moved.lastIndex -> samples.arriving(i)
        !closed -> null
        moved[0] != samples.points[0] -> seamDirection()?.let { it * -1f }
        else -> samples.arriving(i)
    }

    /** The one direction through a moved seam; a corner there has none and each side finds its own. */
    private fun seamDirection(): Vec2? {
        if (0 in strokeCorners(moved, closed = true, emptyList(), pixel)) return null
        return direction(0)
    }
}

/** The stretches of [path] inside a brush of [radius] at [at], as a brush would touch them, where [pixel] is a screen pixel's size. */
fun stretchesUnderBrush(path: BezierPath, at: Vec2, radius: Float, pixel: Float): List<List<Vec2>> {
    val points = CurveSamples(path, strokeSpacing(pixel)).points
    val square = radius * radius
    fun inside(p: Vec2) = (p - at).let { it dotWith it } < square
    val stretches = mutableListOf<List<Vec2>>()
    var i = 0
    while (i < points.size) {
        if (!inside(points[i])) {
            i++
            continue
        }
        val first = i
        while (i < points.size && inside(points[i])) i++
        if (i - first > 1) stretches += points.subList(first, i)
    }
    return stretches
}

private fun blurKernel(sigma: Float): FloatArray {
    val reach = ceil(sigma * BLUR_REACH).toInt().coerceAtLeast(1)
    return FloatArray(reach + 1) { exp(-(it * it) / (2f * sigma * sigma)) }
}
