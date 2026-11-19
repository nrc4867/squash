package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.SurfaceCurve
import kotlin.math.floor
import kotlin.math.sqrt

/** How many steps [onLevel] takes each point toward the level. */
private const val LEVEL_STEPS = 4

/** The most one step moves a point, in cells. */
private const val MOST_STEP_CELLS = 1f

/** How far either side of a point, in cells, the level's slope is read. */
private const val SLOPE_REACH = 0.5f

/** The least slope, per cell, a point is stepped along; flatter than this it stays. */
private const val LEAST_SLOPE = 1e-4f

/**
 * The level of area [area] of a garment's cloth over one part, [cloth], which reads 0 on the area's hem and seams and above 0 inside it:
 * the cloth's field less its hem level, and where the cloth holds several areas, no more than the area's own field less the most of the
 * others', which is 0 where two areas meet. The cloth is cut along the same curves.
 */
fun ClothField.areaLevel(area: Int, u: Float, v: Float): Float {
    val hem = at(u, v) - CLOTH_HEM_LEVEL
    if (areaCount < 2) return hem
    var others = 0f
    for (other in 0 until areaCount) if (other != area) others = maxOf(others, areaAt(other, u, v))
    return minOf(hem, areaAt(area, u, v) - others)
}

/**
 * [curve], on one part's chart, moved onto the curve where [level] reads 0: each point stepped along the level's slope, read in cells, to
 * where it reads 0, those [pinned] kept where they are. So a curve along painted cells' sides becomes the smooth curve the cloth is cut on.
 */
fun onLevel(curve: SurfaceCurve, pinned: (Anchor) -> Boolean, level: (u: Float, v: Float) -> Float): SurfaceCurve = curve.copy(
    points = curve.points.map { point ->
        if (pinned(point)) return@map point
        var row = point.u * CHART_ROWS
        var column = point.v * CHART_COLUMNS
        repeat(LEVEL_STEPS) {
            val at = { r: Float, c: Float -> level(r / CHART_ROWS, c / CHART_COLUMNS) }
            val value = at(row, column)
            val gr = (at(row + SLOPE_REACH, column) - at(row - SLOPE_REACH, column)) / (2f * SLOPE_REACH)
            val gc = (at(row, column + SLOPE_REACH) - at(row, column - SLOPE_REACH)) / (2f * SLOPE_REACH)
            val slope = gr * gr + gc * gc
            if (slope < LEAST_SLOPE * LEAST_SLOPE) return@repeat
            var dr = -value * gr / slope
            var dc = -value * gc / slope
            val length = sqrt(dr * dr + dc * dc)
            if (length > MOST_STEP_CELLS) {
                dr *= MOST_STEP_CELLS / length
                dc *= MOST_STEP_CELLS / length
            }
            row = (row + dr).coerceIn(0f, CHART_ROWS.toFloat())
            column += dc
        }
        val v = column / CHART_COLUMNS
        point.copy(u = row / CHART_ROWS, v = v - floor(v))
    },
)
