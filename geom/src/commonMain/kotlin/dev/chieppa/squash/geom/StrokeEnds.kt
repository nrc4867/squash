package dev.chieppa.squash.geom

import kotlin.math.max
import kotlin.math.min

/** The most of a shape's reach along x given up where a closed shape that stops short of the plane is opened towards it. */
private const val OPENING_FRACTION = 0.1f

/** The head and the tail of a stroke searched for where it crosses itself are each this fraction of it, inverted. */
private const val END_FRACTION_DIVISOR = 3

/** How a cleaned stroke ends. */
sealed interface StrokeEnds {
    /** A closed shape: the last point is the first. */
    data object Closed : StrokeEnds

    /** Open on the plane x = [plane], both ends on it; the shape keeps to the side of it that [above] says. */
    data class CutAtPlane(val plane: Float = 0f, val above: Boolean = true) : StrokeEnds
}

/**
 * A stroke made into a loop, first point repeated last. Its edges from [closingFrom] on are what closes it rather than
 * what was drawn; [overshoot] is what was drawn past the place it closes. [closes] says the stroke came back on its own.
 */
internal class StrokeLoop(val line: List<Vec2>, val closingFrom: Int, val overshoot: List<List<Vec2>>, val closes: Boolean)

/**
 * [points] as a loop if the stroke comes back to where it started: past its head, in which case the overshoot on both
 * ends goes, or within [gap] of it, closed by a run at [spacing] so the line stays evenly spaced. Null if it stays open.
 */
internal fun findLoop(points: List<Vec2>, gap: Float, spacing: Float): StrokeLoop? {
    if (points.size < 3 || arcLength(points) <= 2f * gap) return null
    crossedLoop(points)?.let { return it }
    return if ((points.last() - points.first()).magnitude <= gap) chordLoop(points, spacing, closes = true) else null
}

/** [points] closed by the straight run from its last point back to its first, at [spacing]. */
internal fun chordLoop(points: List<Vec2>, spacing: Float, closes: Boolean = false): StrokeLoop {
    val chord = resample(listOf(points.last(), points.first()), spacing)
    return StrokeLoop(points + chord.drop(1), points.lastIndex, emptyList(), closes)
}

/**
 * Where the tail of [points] crosses its head, latest in the tail first. No point of a run of edges lies further from its
 * first point than their lengths add up to, so the search jumps the head's edges too far from a tail edge to meet it.
 */
private fun crossedLoop(points: List<Vec2>): StrokeLoop? {
    val headEnd = points.size / END_FRACTION_DIVISOR
    val tailStart = points.size - headEnd - 1
    val longest = (1 until points.size).maxOf { (points[it] - points[it - 1]).magnitude }
    if (longest == 0f) return null
    for (i in points.lastIndex - 1 downTo tailStart) {
        val edge = (points[i + 1] - points[i]).magnitude
        var j = 0
        while (j < headEnd) {
            val jump = (((points[j] - points[i]).magnitude - edge) / longest).toInt() - 1
            if (jump >= 1) {
                j += jump
                continue
            }
            val crossing = segmentCrossing(points[i], points[i + 1], points[j], points[j + 1])
            if (crossing != null) {
                val line = listOf(crossing) + points.subList(j + 1, i + 1) + crossing
                val head = points.subList(0, j + 1) + crossing
                val tail = listOf(crossing) + points.subList(i + 1, points.size)
                return StrokeLoop(line, line.lastIndex, listOf(head, tail), closes = true)
            }
            j++
        }
    }
    return null
}

internal fun segmentCrossing(a: Vec2, b: Vec2, c: Vec2, d: Vec2): Vec2? {
    val r = b - a
    val s = d - c
    val across = r.x * s.y - r.y * s.x
    if (across == 0f) return null
    val t = ((c.x - a.x) * s.y - (c.y - a.y) * s.x) / across
    val u = ((c.x - a.x) * r.y - (c.y - a.y) * r.x) / across
    return if (t in 0f..1f && u in 0f..1f) a + r * t else null
}

/** A stroke cut at a plane: the [run] it keeps, and the stubs along x that carry the run's ends to the plane, if they stop short. */
internal class PlaneCut(val run: SideRun, val startStub: List<Vec2>?, val endStub: List<Vec2>?) {
    val line: List<Vec2>
        get() = buildList {
            startStub?.let { add(it.first()) }
            addAll(run.points)
            endStub?.let { add(it.last()) }
        }
}

/**
 * The part of [points] on the kept side of [ends]'s plane, both ends placed on the plane: where it crosses, or straight
 * along x where it stops short. The longest such part if the stroke crosses the plane more than once. A [closed] loop that
 * never passes the plane is opened around its nearest point, no deeper than the least of how far that point lies from the
 * plane and a tenth of the shape's reach, and at least [minOpening]. Null if nothing lies on the kept side.
 */
internal fun cutAtPlane(points: List<Vec2>, closed: Boolean, ends: StrokeEnds.CutAtPlane, minOpening: Float): PlaneCut? {
    val sign = if (ends.above) 1f else -1f
    val nearest = points.minOf { (it.x - ends.plane) * sign }
    val farthest = points.maxOf { (it.x - ends.plane) * sign }
    if (farthest <= 0f) return null
    val run = if (closed && nearest >= 0f) {
        val reach = farthest - nearest
        val depth = max(min(nearest, reach * OPENING_FRACTION), minOpening).coerceAtMost(reach / 2f)
        openedAtNearest(points, ends.plane + sign * (nearest + depth), sign)
    } else {
        longestRun(points, closed, ends.plane, sign)
    } ?: return null
    return withStubs(run, ends.plane)
}

/** [run] with its ends carried along x to the plane x = [plane]. */
private fun withStubs(run: SideRun, plane: Float): PlaneCut {
    val first = run.points.first()
    val last = run.points.last()
    return PlaneCut(
        run,
        if (first.x != plane) listOf(Vec2(plane, first.y), first) else null,
        if (last.x != plane) listOf(last, Vec2(plane, last.y)) else null,
    )
}

/** Whether a stub of [cut] runs across its own stroke further than [reach] from where it leaves it; nearer is a hand's wobble. */
internal fun stubCrossesStroke(cut: PlaneCut, reach: Float): Boolean {
    val line = cut.run.points
    fun crosses(from: Vec2, to: Vec2) = (1 until line.size).any { i ->
        val crossing = segmentCrossing(from, to, line[i - 1], line[i])
        crossing != null && (crossing - from).magnitude > reach
    }
    return cut.startStub?.let { crosses(it.last(), it.first()) } == true || cut.endStub?.let { crosses(it.first(), it.last()) } == true
}

/** A polyline lying along a source's edges: [edges] names the source edge each of its own edges is on. */
internal class RunPiece(val line: List<Vec2>, val edges: List<Int>)

/**
 * A stretch of a polyline on one side of a plane: from [head], where it crosses, through the source's points [first] to
 * [last], which wrap on a closed source, to [tail]. Source edge k runs from point k to the next.
 */
internal class SideRun(
    private val source: List<Vec2>,
    private val closed: Boolean,
    private val head: Vec2?,
    private val first: Int,
    private val last: Int,
    private val tail: Vec2?,
) {
    private val count = if (closed) source.lastIndex else source.size

    private fun cyclic(from: Int, to: Int): List<Int> =
        if (from <= to) (from..to).toList() else (from until count).toList() + (0..to).toList()

    val points: List<Vec2> by lazy {
        buildList {
            head?.let(::add)
            cyclic(first, last).forEach { add(source[it]) }
            tail?.let(::add)
        }
    }

    /** The run itself as a piece of its source. */
    val kept: RunPiece
        get() = RunPiece(
            points,
            buildList {
                if (head != null) add((first - 1).mod(count))
                addAll(cyclic(first, last).dropLast(1))
                if (tail != null) add(last)
            },
        )

    /** What the run leaves of its source. */
    val rest: List<RunPiece>
        get() {
            val start = points.first()
            val end = points.last()
            if (!closed) {
                return buildList {
                    if (first > 0) add(RunPiece(source.subList(0, first) + start, (0 until first).toList()))
                    if (last < source.lastIndex) add(RunPiece(listOf(end) + source.subList(last + 1, source.size), (last until source.lastIndex).toList()))
                }
            }
            if (head == null) return emptyList()
            val edges = cyclic(last, (first - 1).mod(count))
            return listOf(RunPiece(listOf(end) + edges.drop(1).map { source[it] } + start, edges))
        }
}

/** The longest stretch of [points] with x on the [sign] side of [plane], with its crossings placed exactly on the plane. */
internal fun longestRun(points: List<Vec2>, closed: Boolean, plane: Float, sign: Float): SideRun? =
    runsOnSide(points, closed, plane, sign).maxByOrNull { arcLength(it.points) }

private fun runsOnSide(points: List<Vec2>, closed: Boolean, plane: Float, sign: Float): List<SideRun> {
    fun kept(p: Vec2) = (p.x - plane) * sign >= 0f
    fun crossing(a: Vec2, b: Vec2) = Vec2(plane, a.y + (b.y - a.y) * ((plane - a.x) / (b.x - a.x)))
    val count = if (closed) points.lastIndex else points.size
    val begin = if (closed) {
        (0 until count).firstOrNull { !kept(points[it]) } ?: return listOf(SideRun(points, true, null, 0, count - 1, points.last()))
    } else {
        -1
    }
    val runs = mutableListOf<SideRun>()
    var from = -1
    var head: Vec2? = null
    for (offset in 1..count) {
        val i = (begin + offset).mod(count)
        if (!kept(points[i])) continue
        val previous = if (closed || i > 0) points[(i - 1).mod(count)] else null
        val next = if (closed || i < count - 1) points[(i + 1).mod(count)] else null
        if (previous == null || !kept(previous)) {
            from = i
            head = previous?.let { crossing(it, points[i]) }
        }
        if (next == null || !kept(next)) runs += SideRun(points, closed, head, from, i, next?.let { crossing(points[i], it) })
    }
    return runs
}

/** A closed polyline without the arc around its nearest point that lies short of the plane x = [threshold]. */
private fun openedAtNearest(points: List<Vec2>, threshold: Float, sign: Float): SideRun? {
    val count = points.lastIndex
    fun at(i: Int) = points[i.mod(count)]
    fun kept(i: Int) = (at(i).x - threshold) * sign >= 0f
    fun crossing(a: Vec2, b: Vec2) = Vec2(threshold, a.y + (b.y - a.y) * ((threshold - a.x) / (b.x - a.x)))
    val nearest = (0 until count).minBy { (points[it].x - threshold) * sign }
    var after = nearest
    while (!kept(after)) {
        after++
        if (after - nearest > count) return null
    }
    var before = nearest
    while (!kept(before)) before--
    return SideRun(points, true, crossing(at(after - 1), at(after)), after.mod(count), before.mod(count), crossing(at(before), at(before + 1)))
}
