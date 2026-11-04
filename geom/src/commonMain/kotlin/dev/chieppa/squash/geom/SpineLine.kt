package dev.chieppa.squash.geom

import kotlin.math.abs

/** How many points either side of a point give a spine's direction there, past the jitter of the nearest few. */
private const val TANGENT_REACH = 3

/** A pointer sample of a centre stroke; [pressure] is a pen's, 0 to 1, and null from anything else. */
data class SpineSample(val point: Vec2, val pressure: Float? = null)

/** The place on a spine nearest a point: [at] among the spine's drawn places, [distance] from it, and whether it lies to the spine's left. */
data class SpineStation(val at: Float, val distance: Float, val left: Boolean)

/**
 * A centre line, evenly spaced, running from where the part starts. Each of [points] holds its place along the stroke as
 * drawn in [drawnAt], 0 to 1 from the spine's start, so widths stay put when the line is cut at a plane or carried to it.
 */
class SpineLine internal constructor(val points: List<Vec2>, val drawnAt: FloatArray, private val reversed: Boolean) {
    val length: Float get() = arcLength(points)

    /** The index of the point at or just past the drawn place [at]. */
    fun indexAt(at: Float): Int = drawnAt.indexOfFirst { it >= at }.takeIf { it >= 0 } ?: points.lastIndex

    /** The direction across the line at point [index], to its left as it runs. */
    fun normalAt(index: Int): Vec2 {
        val before = points[(index - TANGENT_REACH).coerceAtLeast(0)]
        val after = points[(index + TANGENT_REACH).coerceAtMost(points.lastIndex)]
        val along = (after - before).unit()
        return Vec2(-along.y, along.x)
    }

    /** The place on this line nearest [to]. */
    fun nearest(to: Vec2): SpineStation {
        val index = points.indices.minBy { (points[it] - to).let { d -> d dotWith d } }
        val offset = to - points[index]
        return SpineStation(drawnAt[index], offset.magnitude, (offset dotWith normalAt(index)) >= 0f)
    }

    /** What the pressures in [samples], the samples this line came from, ask of the half-width, [full] at full pressure. */
    fun pressureWidths(samples: List<SpineSample>, full: Float): List<WidthSample> {
        var walked = 0f
        val total = arcLength(samples.map { it.point })
        return samples.mapIndexedNotNull { i, sample ->
            if (i > 0) walked += (sample.point - samples[i - 1].point).magnitude
            val along = if (total > 0f) walked / total else 0f
            sample.pressure?.let { WidthSample(if (reversed) 1f - along else along, it * full) }
        }
    }
}

/**
 * [samples] as a centre line, smoothed as a stroke is, or null for a stroke too short to be one or with nothing on the kept
 * side of the plane. With [ends] open at a plane the line starts on it: a stroke drawn towards the plane is turned round, what
 * passes the plane is cut, and a line that stops short is carried to the plane along x.
 */
fun spineLine(samples: List<SpineSample>, ends: StrokeEnds, pixel: Float, smoothing: Float = DEFAULT_STROKE_SMOOTH): SpineLine? {
    if (samples.size < 2) return null
    val plane = ends as? StrokeEnds.CutAtPlane
    val reversed = plane != null && abs(samples.first().point.x - plane.plane) > abs(samples.last().point.x - plane.plane)
    val raw = samples.map { it.point }.let { if (reversed) it.reversed() else it }
    val line = smoothedOpenLine(raw, pixel, smoothing) ?: return null
    val total = arcLength(raw)
    val spacing = strokeSpacing(pixel)
    val drawn = FloatArray(line.size) { i -> if (i == line.lastIndex) 1f else (i * spacing / total).coerceAtMost(1f) }
    if (plane == null) return SpineLine(line, drawn, reversed)
    return startingOnPlane(line, drawn, plane, spacing, reversed)?.takeIf { it.length >= strokeMinLength(pixel) }
}

private fun startingOnPlane(line: List<Vec2>, drawn: FloatArray, plane: StrokeEnds.CutAtPlane, spacing: Float, reversed: Boolean): SpineLine? {
    val run = longestKeptRun(line, plane) ?: return null
    val first = line[run.first]
    val head = when {
        run.first > 0 -> listOf(crossingAt(plane.plane, line[run.first - 1], first))
        first.x != plane.plane -> resample(listOf(Vec2(plane.plane, first.y), first), spacing).dropLast(1)
        else -> emptyList()
    }
    val points = head + line.subList(run.first, run.last + 1)
    return SpineLine(points, FloatArray(points.size) { i -> drawn[(i - head.size + run.first).coerceAtLeast(run.first)] }, reversed)
}

private fun longestKeptRun(line: List<Vec2>, plane: StrokeEnds.CutAtPlane): IntRange? {
    val sign = if (plane.above) 1f else -1f
    var from = -1
    var best: IntRange? = null
    for (i in line.indices) {
        if ((line[i].x - plane.plane) * sign < 0f) {
            from = -1
            continue
        }
        if (from < 0) from = i
        val longest = best
        if (longest == null || i - from > longest.last - longest.first) best = from..i
    }
    return best
}

/** Where the edge from [a] to [b], which reaches x, does so. */
internal fun crossingAt(x: Float, a: Vec2, b: Vec2) = Vec2(x, a.y + (b.y - a.y) * ((x - a.x) / (b.x - a.x)))
