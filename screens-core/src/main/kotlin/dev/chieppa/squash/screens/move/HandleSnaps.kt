package dev.chieppa.squash.screens.move

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs

/** How many steps a part's midline and rings take on screen. */
private const val LINE_STEPS = 48

/** The places along a part a dragged handle snaps to: its ends and its middle. */
val SNAP_PLACES = listOf(0f, 0.5f, 1f)

/** How far either way, in body units, the character's centre line runs on screen. */
private const val CENTRE_REACH = 50f

/** A centre plane closer than this to edge on shows as a line. */
private const val EDGE_ON = 1e-3f

/** What a snap line stands for. */
enum class SnapKind { MIDLINE, MIDDLE, END, CENTRE }

/** A line on screen a dragged handle snaps to, as [points] in pane pixels; one point snaps to that point. [u] is where along its part a ring lies. */
class SnapLine(val kind: SnapKind, val points: List<Offset>, val u: Float? = null)

/** Where a snapped handle goes, at [pixel], and the [lines] it lies on. */
class Snap(val pixel: Offset, val lines: List<SnapLine>) {
    /** Where along the part the ring it lies on is, or null when it lies on none. */
    val u: Float? get() = lines.firstNotNullOfOrNull { it.u }
}

/**
 * The lines a socket on [parent] snaps to in [view]: the parent's midline, its middle and end rings on the side facing the viewer,
 * and the character's centre line where the view sees the body's middle plane edge on.
 */
fun socketSnapLines(parent: PlacedPart, view: HandleView): List<SnapLine> {
    val midline = (0..LINE_STEPS).mapNotNull { i -> axisMiddle(parent, i.toFloat() / LINE_STEPS)?.let(view::toPixel) }
    val rings = SNAP_PLACES.flatMap { u -> ringRuns(parent, u, view).map { SnapLine(if (u == 0.5f) SnapKind.MIDDLE else SnapKind.END, it, u) } }
    return listOfNotNull(SnapLine(SnapKind.MIDLINE, midline).takeIf { midline.size > 1 }, centreLine(view)) + rings
}

/** The point a joint of [part] snaps to in [view]: the middle of its axis. */
fun jointSnapLines(part: PlacedPart, view: HandleView): List<SnapLine> =
    listOfNotNull(axisMiddle(part, 0.5f)?.let(view::toPixel)?.let { SnapLine(SnapKind.MIDDLE, listOf(it), 0.5f) })

/** The runs of [part]'s ring at [u] that face the viewer, on screen. */
private fun ringRuns(part: PlacedPart, u: Float, view: HandleView): List<List<Offset>> {
    val runs = mutableListOf<MutableList<Offset>>()
    var open: MutableList<Offset>? = null
    for (i in 0..LINE_STEPS) {
        val v = (i % LINE_STEPS).toFloat() / LINE_STEPS
        val point = part.pointOn(u, v)
        val normal = part.normalAt(u, v)
        val pixel = if (point != null && normal != null && (normal dot view.toViewer(point)) >= 0f) view.toPixel(point) else null
        if (pixel == null) {
            open = null
            continue
        }
        val run = open ?: mutableListOf<Offset>().also { runs += it }
        run += pixel
        open = run
    }
    return runs.filter { it.size > 1 }
}

/** The character's centre line in [view], where its middle plane shows edge on; null elsewhere. */
fun centreLine(view: HandleView): SnapLine? {
    val toViewer = view.toViewer(Vec3.ZERO).normalized()
    if (abs(toViewer dot Vec3.Z) > EDGE_ON) return null
    val along = (toViewer cross Vec3.Z).normalized()
    val from = view.toPixel(along * -CENTRE_REACH) ?: return null
    val to = view.toPixel(along * CENTRE_REACH) ?: return null
    return SnapLine(SnapKind.CENTRE, listOf(from, to))
}

/**
 * Where [target] snaps among [lines] within [reach] pixels, or null where it is near none. A drag held to [direction] through
 * [start] snaps only where a line crosses its own. A free one snaps where two lines cross, when that is in reach, before the
 * nearest point of one line.
 */
fun snapTo(target: Offset, lines: List<SnapLine>, reach: Float, start: Offset? = null, direction: Offset? = null): Snap? {
    if (start != null && direction != null) {
        val crossings = lines.flatMap { line -> crossings(line, start, direction, reach).map { it to line } }
        val (pixel, line) = crossings.minByOrNull { (it.first - target).getDistance() } ?: return null
        return if ((pixel - target).getDistance() <= reach) Snap(pixel, listOf(line)) else null
    }
    val near = lines.mapNotNull { line -> nearest(line.points, target)?.takeIf { (it - target).getDistance() <= reach }?.let { line to it } }
    if (near.isEmpty()) return null
    val crossed = near.indices.flatMap { i -> (i + 1 until near.size).flatMap { j -> meet(near[i].first, near[j].first).map { Snap(it, listOf(near[i].first, near[j].first)) } } }
        .filter { (it.pixel - target).getDistance() <= reach }
        .minByOrNull { (it.pixel - target).getDistance() }
    if (crossed != null) return crossed
    val (line, pixel) = near.minBy { (it.second - target).getDistance() }
    return Snap(pixel, listOf(line))
}

/** The nearest point of [points], read as a polyline, to [target]. */
private fun nearest(points: List<Offset>, target: Offset): Offset? {
    if (points.size == 1) return points.first()
    return points.zipWithNext().map { (a, b) -> onSegment(a, b, target) }.minByOrNull { (it - target).getDistance() }
}

private fun onSegment(a: Offset, b: Offset, target: Offset): Offset {
    val run = b - a
    val length = run.x * run.x + run.y * run.y
    if (length == 0f) return a
    val t = (((target - a).x * run.x + (target - a).y * run.y) / length).coerceIn(0f, 1f)
    return a + run * t
}

/** Where [line] crosses the line through [start] along [direction]; a single point counts where it lies within [reach] of it. */
private fun crossings(line: SnapLine, start: Offset, direction: Offset, reach: Float): List<Offset> {
    if (line.points.size == 1) {
        val point = line.points.first()
        val along = (point - start).let { it.x * direction.x + it.y * direction.y }
        val on = Offset(start.x + direction.x * along, start.y + direction.y * along)
        return if ((on - point).getDistance() <= reach) listOf(on) else emptyList()
    }
    return line.points.zipWithNext().mapNotNull { (a, b) -> crossing(a, b, start, direction) }
}

/** Where the segment [a] to [b] crosses the line through [start] along [direction], kept on that line exactly. */
private fun crossing(a: Offset, b: Offset, start: Offset, direction: Offset): Offset? {
    val normal = Offset(-direction.y, direction.x)
    val sideA = (a - start).let { it.x * normal.x + it.y * normal.y }
    val sideB = (b - start).let { it.x * normal.x + it.y * normal.y }
    if (sideA == sideB || sideA * sideB > 0f) return null
    val at = a + (b - a) * (sideA / (sideA - sideB))
    val along = (at - start).let { it.x * direction.x + it.y * direction.y }
    return Offset(start.x + direction.x * along, start.y + direction.y * along)
}

/** Everywhere two snap lines of more than one point cross. */
private fun meet(first: SnapLine, second: SnapLine): List<Offset> {
    if (first.points.size == 1 || second.points.size == 1) return emptyList()
    return first.points.zipWithNext().flatMap { (a, b) -> second.points.zipWithNext().mapNotNull { (c, d) -> segmentsCross(a, b, c, d) } }
}

private fun segmentsCross(a: Offset, b: Offset, c: Offset, d: Offset): Offset? {
    val r = b - a
    val s = d - c
    val denominator = r.x * s.y - r.y * s.x
    if (denominator == 0f) return null
    val t = ((c - a).x * s.y - (c - a).y * s.x) / denominator
    val w = ((c - a).x * r.y - (c - a).y * r.x) / denominator
    return if (t in 0f..1f && w in 0f..1f) a + r * t else null
}
