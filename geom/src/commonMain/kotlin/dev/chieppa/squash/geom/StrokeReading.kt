package dev.chieppa.squash.geom

import kotlin.math.ceil
import kotlin.math.floor

/** How near an outermost crossing a point still counts as on it, in screen pixels: a snapped straight run lies up to 2 from the hand's line, and the table adds its own. */
private const val READ_REACH_PIXELS = 4f

/** How much of a station's spacing a point may lie from the table's straight run between stations and still be on it. */
private const val READ_REACH_OF_STEP = 0.75f

/** One stretch the build fills, across the line at [x], from [low] to [high], standing for the [width] along x around it. */
data class ReadStrip(val x: Float, val low: Float, val high: Float, val width: Float)

/** A line of a stroke parted into what the build reads and what it does not. */
class ReadSplit(val read: List<List<Vec2>>, val unread: List<List<Vec2>>)

/** What the build takes from an outline: where it fills, and which of the outline's points it uses. */
interface OutlineReading {
    /** What the build fills, across the view's x at intervals of [step]. */
    fun spans(step: Float): List<ReadStrip>

    fun isRead(point: Vec2): Boolean

    /** [line] parted at each change between read and not, the pieces sharing the point where they meet. */
    fun split(line: List<Vec2>): ReadSplit {
        val read = mutableListOf<List<Vec2>>()
        val unread = mutableListOf<List<Vec2>>()
        var from = 0
        var state = line.firstOrNull()?.let(::isRead) ?: return ReadSplit(read, unread)
        fun close(to: Int) {
            if (to > from) (if (state) read else unread) += line.subList(from, to + 1)
        }
        for (i in 1 until line.size) {
            val now = isRead(line[i])
            if (now != state) {
                close(i)
                from = i
                state = now
            }
        }
        close(line.lastIndex)
        return ReadSplit(read, unread)
    }
}

/** A height or width view's reading of [shape]: [OutlineProfile]'s two outermost crossings at each x, cut as [ends] says. Null if it sets nothing. */
fun crossingsReading(shape: List<Vec2>, ends: StrokeEnds, pixel: Float): OutlineReading? {
    if (shape.size < 2) return null
    val profile = OutlineProfile.of(Polyline2(FloatArray(shape.size) { shape[it].x }, FloatArray(shape.size) { shape[it].y })) ?: return null
    val plane = ends as? StrokeEnds.CutAtPlane
    val from = if (plane?.above == true) plane.plane else profile.start
    val to = if (plane?.above == false) plane.plane else profile.end
    return if (to > from) CrossingsReading(profile, from, to, READ_REACH_PIXELS * pixel) else null
}

/** A section view's reading of [shape]: all of it, as the closed loop the build walks around, filling what the loop encloses. */
fun sectionReading(shape: List<Vec2>): OutlineReading? = if (shape.size < 3) null else SectionReading(shape)

private class CrossingsReading(private val profile: OutlineProfile, private val from: Float, private val to: Float, reach: Float) : OutlineReading {
    private val step = profile.stationStep
    private val slack = maxOf(reach, step * READ_REACH_OF_STEP)

    override fun spans(step: Float): List<ReadStrip> {
        val count = ceil((to - from) / step).toInt().coerceAtLeast(1)
        return List(count) { i ->
            val x = from + (to - from) * (i + 0.5f) / count
            ReadStrip(x, profile.lowAt(x), profile.highAt(x), (to - from) / count)
        }
    }

    override fun isRead(point: Vec2): Boolean {
        val x = point.x
        if (x < from - slack || x > to + slack) return false
        if (x <= profile.start + slack && onEndWall(point, profile.start, profile.start + slack)) return true
        if (x >= profile.end - slack && onEndWall(point, profile.end, profile.end - slack)) return true
        val station = floor((x - profile.start) / step)
        for (s in -1..1) {
            val a = profile.start + (station + s) * step
            val b = a + step
            if (distanceToSegment(point, Vec2(a, profile.highAt(a)), Vec2(b, profile.highAt(b))) <= slack) return true
            if (distanceToSegment(point, Vec2(a, profile.lowAt(a)), Vec2(b, profile.lowAt(b))) <= slack) return true
        }
        return false
    }

    /** Whether [point] lies between the crossings at the end of the range or just inside it, where a wall leaning off square shows its full height. */
    private fun onEndWall(point: Vec2, end: Float, inside: Float): Boolean =
        point.y >= minOf(profile.lowAt(end), profile.lowAt(inside)) - slack && point.y <= maxOf(profile.highAt(end), profile.highAt(inside)) + slack
}

private class SectionReading(private val shape: List<Vec2>) : OutlineReading {
    override fun isRead(point: Vec2): Boolean = true

    override fun spans(step: Float): List<ReadStrip> {
        val low = shape.minOf { it.x }
        val high = shape.maxOf { it.x }
        val count = ceil((high - low) / step).toInt().coerceAtLeast(1)
        val width = (high - low) / count
        val crossings = List(count) { mutableListOf<Float>() }
        fun scan(a: Vec2, b: Vec2) {
            if (a.x == b.x) return
            val first = ceil((minOf(a.x, b.x) - low) / width - 0.5f).toInt().coerceAtLeast(0)
            val last = floor((maxOf(a.x, b.x) - low) / width - 0.5f).toInt().coerceAtMost(count - 1)
            for (s in first..last) {
                val x = low + (s + 0.5f) * width
                crossings[s] += a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x)
            }
        }
        for (i in 1 until shape.size) scan(shape[i - 1], shape[i])
        if (shape.first() != shape.last()) scan(shape.last(), shape.first())
        return buildList {
            crossings.forEachIndexed { s, ys ->
                ys.sort()
                for (i in 0 until ys.size - 1 step 2) add(ReadStrip(low + (s + 0.5f) * width, ys[i], ys[i + 1], width))
            }
        }
    }
}
