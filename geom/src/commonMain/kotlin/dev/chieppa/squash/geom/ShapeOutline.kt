package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

/** How far past the rings a shape's closing edges run, in rings, so they never lie on a ring. */
private const val PAST_THE_RINGS = 1.0

/** A chain of straight edges in grid coordinates. It ends where it began, or the same place a whole number of turns round v. */
internal class OutlinePath(val us: DoubleArray, val vs: DoubleArray)

/** Collects a path's points, from u and v or directly in grid coordinates. */
internal class OutlineBuilder(private val grid: PatchGrid) {
    private val us = ArrayList<Double>()
    private val vs = ArrayList<Double>()

    fun add(u: Double, v: Double) = addGrid(grid.gridU(u), grid.gridV(v))

    fun addGrid(gridU: Double, gridV: Double) {
        us += gridU
        vs += gridV
    }

    fun closeOnFirst() = addGrid(us.first(), vs.first())

    fun built() = OutlinePath(us.toDoubleArray(), vs.toDoubleArray())
}

/** The outlines of this shape in [grid]'s grid coordinates, flattened to [tolerance] in the surface's units. */
internal fun ColorShape.outlines(grid: PatchGrid, tolerance: Float): List<OutlinePath> = when (this) {
    is ColorShape.Brush -> sweptBrush(unwrapped(points, grid.closed), radius, grid, tolerance)
    is ColorShape.Border -> listOf(borderOutline(this, grid, tolerance))
    is ColorShape.Rectangle -> listOf(rectangleOutline(this, grid))
    ColorShape.WholePatch -> wholePatchOutlines(grid)
}

private fun borderOutline(border: ColorShape.Border, grid: PatchGrid, tolerance: Float): OutlinePath {
    val points = unwrapped(border.points, grid.closed)
    val path = OutlineBuilder(grid)
    if (border.closing == BorderClosing.ON_ITSELF) {
        flattenedThrough(points, grid, tolerance, turnsToClose(points, grid)).forEach { path.add(it.u, it.v) }
        return path.built()
    }
    flattenedThrough(points, grid, tolerance).forEach { path.add(it.u, it.v) }
    val past = if (border.closing == BorderClosing.PAST_FIRST_RING) -PAST_THE_RINGS else grid.rings - 1.0 + PAST_THE_RINGS
    path.addGrid(past, grid.gridV(points.last().v))
    path.addGrid(past, grid.gridV(points.first().v))
    path.add(points.first().u, points.first().v)
    return path.built()
}

/** The whole turns of v from a closed curve's last point the short way to its first, which is 0 unless it goes round the patch. */
private fun turnsToClose(points: List<UnwrappedUv>, grid: PatchGrid): Double {
    if (!grid.closed) return 0.0
    val last = points.last().v
    val first = points.first().v
    return first.let { it - floor(it - last + 0.5) } - first
}

private fun rectangleOutline(area: ColorShape.Rectangle, grid: PatchGrid): OutlinePath {
    val path = OutlineBuilder(grid)
    val low = area.uStart.toDouble()
    val high = area.uEnd.toDouble()
    val from = area.vStart.toDouble()
    val to = max(area.vEnd.toDouble(), from).let { if (it - from > 1.0) from + 1.0 else it }
    path.add(low, from)
    path.add(high, from)
    path.add(high, to)
    path.add(low, to)
    path.closeOnFirst()
    return path.built()
}

/** A closed patch's band, between lines all the way round past both end rings; an open patch's rectangle past its edges. */
private fun wholePatchOutlines(grid: PatchGrid): List<OutlinePath> {
    val below = -PAST_THE_RINGS
    val above = grid.rings - 1.0 + PAST_THE_RINGS
    val columns = grid.columns.toDouble()
    if (grid.closed) {
        val low = OutlineBuilder(grid).apply { addGrid(below, columns); addGrid(below, 0.0) }
        val high = OutlineBuilder(grid).apply { addGrid(above, 0.0); addGrid(above, columns) }
        return listOf(low.built(), high.built())
    }
    val around = OutlineBuilder(grid).apply {
        addGrid(below, -PAST_THE_RINGS)
        addGrid(above, -PAST_THE_RINGS)
        addGrid(above, columns + PAST_THE_RINGS)
        addGrid(below, columns + PAST_THE_RINGS)
        closeOnFirst()
    }
    return listOf(around.built())
}

/** [value] on the whole number within [distance] of it, or as it is. */
internal fun snappedToWhole(value: Double, distance: Double): Double {
    val whole = round(value)
    return if (abs(value - whole) < distance) whole else value
}
