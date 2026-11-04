package dev.chieppa.squash.geom

import kotlin.math.ceil
import kotlin.math.sqrt

private const val MIN_TOP_STATIONS = 16
private const val MAX_TOP_STATIONS = 512

/** How far along an offset a loop may run and still be the fold of a tight inner bend, in half-widths at their widest. */
private const val LOOP_REACH_OF_HALF_WIDTH = 6f

/** Joined cubic Béziers for the outlines of a spine, with how far each strays from its shape at worst, in the plane's units. */
class SpineFit(val side: BezierPath, val top: BezierPath, val sideError: Float, val topError: Float)

/**
 * The outlines a spine and its width make, in the planes of Side and Top. [side] runs out along one offset and back along the
 * other; [top] has the width along a, either side of 0. Open ones start and end on the socket plane; [closed] ones repeat
 * their first point last.
 */
class SpineShape internal constructor(
    val line: SpineLine,
    val halfWidths: FloatArray,
    val closed: Boolean,
    private val sidePieces: List<List<Vec2>>,
    private val topPieces: List<List<Vec2>>,
) {
    val side: List<Vec2> by lazy { joined(sidePieces) }
    val top: List<Vec2> by lazy { joined(topPieces) }

    /** The two ends of the width across the spine at the drawn place [at]: left of the spine as it runs, then right. */
    fun across(at: Float): Pair<Vec2, Vec2> {
        val index = line.indexAt(at)
        val out = line.normalAt(index) * halfWidths[index]
        return (line.points[index] + out) to (line.points[index] - out)
    }

    /** The outlines as curves, fitted to the shapes as a stroke is for a screen pixel of size [pixel]. */
    fun fitted(pixel: Float): SpineFit {
        val spacing = strokeSpacing(pixel)
        val sides = sidePieces.map { fitOpenRun(resample(it, spacing), pixel, null, null) }
        val tops = topPieces.map { fitOpenRun(resample(it, spacing), pixel, null, null) }
        return SpineFit(
            BezierPath(sides.flatMap { it.path.segments }),
            BezierPath(tops.flatMap { it.path.segments }),
            sides.maxOf { it.maxError },
            tops.maxOf { it.maxError },
        )
    }

    private fun joined(pieces: List<List<Vec2>>): List<Vec2> = pieces.flatMapIndexed { i, piece -> if (i == 0) piece else piece.drop(1) }
}

/**
 * The outlines [line] makes with [width]: its offsets by the half-width, closing at the far end, and with [ends] closed at the
 * start too. Near a closed end the half-width rounds off to nothing within its own reach, so the end stays where the spine's
 * does and Side and Top close together. Null if the width comes to nothing.
 */
fun spineShape(line: SpineLine, width: WidthProfile, ends: StrokeEnds): SpineShape? {
    val plane = ends as? StrokeEnds.CutAtPlane
    val halves = cappedHalfWidths(line, width, closedStart = plane == null)
    val peak = halves.max()
    if (peak <= 0f) return null
    fun side(sign: Float): List<Vec2> {
        val offset = withoutLoops(line.points.indices.map { line.points[it] + line.normalAt(it) * (halves[it] * sign) }, LOOP_REACH_OF_HALF_WIDTH * peak)
        return if (plane != null) carriedToPlane(offset, plane) else offset
    }
    val pieces = listOf(side(1f), side(-1f).reversed())
    return SpineShape(line, halves, plane == null, pieces, topPieces(line, halves, pieces.flatten().map { it.x }, plane))
}

private fun cappedHalfWidths(line: SpineLine, width: WidthProfile, closedStart: Boolean): FloatArray {
    val points = line.points
    val walked = FloatArray(points.size)
    for (i in 1 until points.size) walked[i] = walked[i - 1] + (points[i] - points[i - 1]).magnitude
    val total = walked.last()
    val asked = FloatArray(points.size) { width.at(line.drawnAt[it]) }
    val farReach = asked.last().coerceAtMost(total / 2f)
    val startReach = asked.first().coerceAtMost(total / 2f)
    return FloatArray(points.size) { i ->
        val atStart = if (closedStart) rounding(walked[i], startReach) else 1f
        asked[i] * rounding(total - walked[i], farReach) * atStart
    }
}

/** How much of a half-width stands [distance] from a closed end whose rounding has [reach]: a quarter ellipse, nothing at the end. */
private fun rounding(distance: Float, reach: Float): Float {
    if (distance <= 0f) return 0f
    if (distance >= reach) return 1f
    val t = 1f - distance / reach
    return sqrt(1f - t * t)
}

/** [points] with each loop it makes crossing itself within [reach] along it cut off at the crossing, as an offset folds inside a tight bend. */
internal fun withoutLoops(points: List<Vec2>, reach: Float): List<Vec2> {
    val kept = mutableListOf(points.first())
    var i = 0
    while (i < points.lastIndex) {
        val fold = foldFrom(points, i, reach)
        if (fold != null) kept += fold.second
        i = if (fold != null) fold.first + 1 else i + 1
        kept += points[i]
    }
    return kept
}

/** The last edge within [reach] past edge [i] that crosses it, and where. */
private fun foldFrom(points: List<Vec2>, i: Int, reach: Float): Pair<Int, Vec2>? {
    var end = i + 2
    var walked = 0f
    while (end < points.lastIndex && walked <= reach) {
        walked += (points[end] - points[end - 1]).magnitude
        end++
    }
    for (edge in end - 1 downTo i + 2) {
        segmentCrossing(points[i], points[i + 1], points[edge], points[edge + 1])?.let { return edge to it }
    }
    return null
}

/** [offset] from the plane: what lies behind the plane goes, and an end short of it reaches it along x. */
private fun carriedToPlane(offset: List<Vec2>, plane: StrokeEnds.CutAtPlane): List<Vec2> {
    val sign = if (plane.above) 1f else -1f
    val behind = offset.indexOfLast { (it.x - plane.plane) * sign < 0f }
    if (behind == offset.lastIndex) return listOf(Vec2(plane.plane, offset.last().y), offset.last())
    val first = offset[behind + 1]
    val head = if (behind >= 0) crossingAt(plane.plane, offset[behind], first) else Vec2(plane.plane, first.y)
    val rest = offset.subList(behind + 1, offset.size)
    return if (head == first) rest else listOf(head) + rest
}

/**
 * The width along a. Top's reach is [sideXs]'s, so the two agree where the part starts and ends: a place in that reach reads
 * the spine at the matching place in the spine's own, and where the spine passes a place more than once the widest counts.
 */
private fun topPieces(line: SpineLine, halves: FloatArray, sideXs: List<Float>, plane: StrokeEnds.CutAtPlane?): List<List<Vec2>> {
    val start = sideXs.min()
    val end = sideXs.max()
    val widths = widthAlongAxis(line, halves, line.points.size.coerceIn(MIN_TOP_STATIONS, MAX_TOP_STATIONS) + 1)
    val stations = widths.indices.map { start + (end - start) * it / widths.lastIndex to widths[it] }
    val ordered = if (plane == null || plane.above) stations else stations.reversed()
    val out = ordered.map { (a, half) -> Vec2(a, half) }
    val back = ordered.reversed().map { (a, half) -> Vec2(a, 0f - half) }
    val across = listOf(out.last()) + if (out.last() == back.first()) back.drop(1) else back
    val closing = plane == null && across.last() != out.first()
    return listOf(out, if (closing) across + out.first() else across)
}

/** The half-width at [count] places evenly across the reach of [line] along x. */
private fun widthAlongAxis(line: SpineLine, halves: FloatArray, count: Int): FloatArray {
    val spineStart = line.points.minOf { it.x }
    val spineEnd = line.points.maxOf { it.x }
    val perStation = (count - 1) / (spineEnd - spineStart)
    val widths = FloatArray(count) { -1f }
    for (i in 1 until line.points.size) {
        val a0 = (line.points[i - 1].x - spineStart) * perStation
        val a1 = (line.points[i].x - spineStart) * perStation
        var station = ceil(minOf(a0, a1)).toInt().coerceAtLeast(0)
        while (station <= maxOf(a0, a1) && station < count) {
            val t = if (a1 == a0) 0f else (station - a0) / (a1 - a0)
            val half = halves[i - 1] + (halves[i] - halves[i - 1]) * t
            if (half > widths[station]) widths[station] = half
            station++
        }
    }
    return filledFromNearest(widths)
}

/** [widths] with each place no edge reached taking the nearest one that was, in order along them. */
private fun filledFromNearest(widths: FloatArray): FloatArray {
    var seen = -1f
    for (i in widths.indices) if (widths[i] >= 0f) seen = widths[i] else widths[i] = seen
    seen = 0f
    for (i in widths.indices.reversed()) if (widths[i] >= 0f) seen = widths[i] else widths[i] = seen
    return widths
}
