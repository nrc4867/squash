package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val TWO_PI = 2.0 * PI

/** The most a brush's edge reaches around v from its curve, as a share of a turn, however close a pole brings the rings together. */
internal const val BRUSH_V_REACH = 0.24

/** The widest step around an arc of a brush's edge, in radians, for a brush no wider than its tolerance. */
private const val WIDEST_STEP = PI / 4.0
private const val MIN_LENGTH = 1e-12

private const val POLE_BISECTIONS = 10
private const val BELOW_THE_RINGS = -1.0

/** A point of a brush's curve in grid coordinates, with how far the surface moves there per unit of each. */
private class Station(val u: Double, val v: Double, val scaleU: Double, val scaleV: Double)

/** The radians an arc of a [radius] edge may step and stay within [tolerance] of the true arc. */
internal fun arcStep(radius: Float, tolerance: Float): Double {
    val share = (tolerance / radius).toDouble()
    return if (share >= 1.0) WIDEST_STEP else minOf(WIDEST_STEP, 2.0 * acos(1.0 - share))
}

/**
 * The closed outlines of a brush of [radius] along [points], wound counter-clockwise, in grid coordinates. Each side of the curve
 * is its offset with round joins on the outside of a bend and a return through the curve on the inside, which covers a tight bend
 * exactly under the non-zero rule. The ends are round. A pole inside the brush adds a cap, since a ring near a pole has no room
 * for the brush's width around v.
 */
internal fun sweptBrush(points: List<UnwrappedUv>, radius: Float, grid: PatchGrid, tolerance: Float): List<OutlinePath> {
    val curve = flattenedThrough(points, grid, tolerance)
    val stations = curve.map { point ->
        val u = grid.gridU(point.u).coerceIn(0.0, grid.rings - 1.0)
        val v = grid.gridV(point.v)
        val rates = grid.gridRatesAt(u, v)
        Station(u, v, rates.alongU.toDouble(), rates.aroundV.toDouble())
    }
    val path = OutlineBuilder(grid)
    val step = arcStep(radius, tolerance)
    val reachV = BRUSH_V_REACH * grid.columns
    walkSide(stations, 0.0, radius, step, reachV, grid, path)
    walkSide(stations.asReversed(), PI, radius, step, reachV, grid, path)
    path.closeOnFirst()
    return listOf(path.built()) + poleCaps(curve, radius, grid, step)
}

/** One side of the brush along [stations], on the right of the way they run, then the round end after the last of them. A lone station runs along [alone]. */
private fun walkSide(stations: List<Station>, alone: Double, radius: Float, step: Double, reachV: Double, grid: PatchGrid, path: OutlineBuilder) {
    fun offset(station: Station, angle: Double) {
        val around = (radius * sin(angle) / station.scaleV).coerceIn(-reachV, reachV)
        path.addGrid(grid.marchedU(station.u, station.v, radius * cos(angle).toFloat()), station.v + around)
    }
    fun arc(station: Station, from: Double, sweep: Double) {
        val steps = ceil(sweep / step).toInt().coerceAtLeast(1)
        for (i in 1..steps) offset(station, from + sweep * i / steps)
    }
    var heading = if (stations.size < 2) alone else headingFrom(stations, 0)
    offset(stations.first(), heading - PI / 2.0)
    for (i in 1 until stations.size) {
        val station = stations[i]
        offset(station, heading - PI / 2.0)
        if (i == stations.lastIndex) break
        val next = headingFrom(stations, i)
        val turn = wrappedTurn(next - heading)
        if (turn > 0.0) arc(station, heading - PI / 2.0, turn) else if (turn < 0.0) path.addGrid(station.u, station.v)
        heading = next
        if (turn < 0.0) offset(station, heading - PI / 2.0)
    }
    arc(stations.last(), heading - PI / 2.0, PI)
}

private fun headingFrom(stations: List<Station>, i: Int): Double {
    val a = stations[i]
    val b = stations[(i + 1).coerceAtMost(stations.lastIndex)]
    val along = (b.u - a.u) * (a.scaleU + b.scaleU) / 2.0
    val around = (b.v - a.v) * (a.scaleV + b.scaleV) / 2.0
    return if (abs(along) + abs(around) < MIN_LENGTH) 0.0 else atan2(around, along)
}

private fun wrappedTurn(turn: Double): Double = when {
    turn > PI -> turn - TWO_PI
    turn <= -PI -> turn + TWO_PI
    else -> turn
}

/** For each pole within [radius] of the [curve], the outlines of everything on its side of where the brush's reach ends around it. */
private fun poleCaps(curve: List<UnwrappedUv>, radius: Float, grid: PatchGrid, step: Double): List<OutlinePath> {
    val centers = curve.map { grid.positionOn(grid.gridU(it.u), grid.gridV(it.v)) }
    fun reaches(gridU: Double, gridV: Double): Boolean {
        val at = grid.positionOn(gridU, gridV)
        return distanceToPolyline(at, centers) <= radius
    }
    val caps = ArrayList<OutlinePath>()
    val samples = ceil(TWO_PI / step).toInt().coerceAtLeast(grid.columns)
    if (grid.closed && grid.isPole(0) && reaches(0.0, 0.0)) {
        val line = OutlineBuilder(grid)
        line.addGrid(BELOW_THE_RINGS, grid.columns.toDouble())
        line.addGrid(BELOW_THE_RINGS, 0.0)
        val edge = OutlineBuilder(grid)
        for (i in 0..samples) {
            val gridV = grid.columns.toDouble() * i / samples
            edge.addGrid(reachFrom(0.0, 1.0, grid, gridV, ::reaches), gridV)
        }
        caps += line.built()
        caps += edge.built()
    }
    val lastRing = grid.rings - 1.0
    if (grid.closed && grid.isPole(grid.rings - 1) && reaches(lastRing, 0.0)) {
        val edge = OutlineBuilder(grid)
        for (i in samples downTo 0) {
            val gridV = grid.columns.toDouble() * i / samples
            edge.addGrid(reachFrom(lastRing, -1.0, grid, gridV, ::reaches), gridV)
        }
        caps += edge.built()
    }
    return caps
}

/** The furthest ring position from the pole at [start], moving by [direction], that the brush still reaches at [gridV]. */
private fun reachFrom(start: Double, direction: Double, grid: PatchGrid, gridV: Double, reaches: (Double, Double) -> Boolean): Double {
    var inside = start
    var outside = start + direction
    val end = grid.rings - 1.0
    while (outside in 0.0..end && reaches(outside, gridV)) {
        inside = outside
        outside += direction
    }
    if (outside !in 0.0..end) return inside
    repeat(POLE_BISECTIONS) {
        val middle = (inside + outside) / 2.0
        if (reaches(middle, gridV)) inside = middle else outside = middle
    }
    return inside
}

private fun distanceToPolyline(point: Vec3, line: List<Vec3>): Float {
    if (line.size == 1) return (point - line[0]).length
    var best = Float.MAX_VALUE
    for (i in 0 until line.lastIndex) {
        val a = line[i]
        val along = line[i + 1] - a
        val square = along dot along
        val t = if (square > 0f) (((point - a) dot along) / square).coerceIn(0f, 1f) else 0f
        val offset = a + along * t - point
        val distance = sqrt(offset dot offset)
        if (distance < best) best = distance
    }
    return best
}
