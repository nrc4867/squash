package dev.chieppa.squash.character.shape

import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SurfaceCurve

/**
 * [mask] as [curves] cut it, each curve with the held cells on its left as [edgesOf] runs them: the chart cut into regions by the curves,
 * each region held where most of its cells are held now, and each cell a curve runs through held where it lies on the curve's left,
 * or where that is even, where most of the cells beside it end up held. Drawing a mask's own edges somewhere else moves its edge there.
 */
fun redrawn(mask: SurfaceMask, curves: List<SurfaceCurve>): SurfaceMask {
    val chart = mask.chart
    val regions = SurfaceRegions(chart, curves)
    val held = IntArray(regions.count)
    val all = IntArray(regions.count)
    for (part in chart.parts) {
        val labels = regions.labels.getValue(part.id)
        for (cell in labels.indices) {
            val region = labels[cell]
            if (region < 0) continue
            all[region]++
            if (mask.held(part.id, cell)) held[region]++
        }
    }
    val regionHeld = BooleanArray(regions.count) { held[it] * 2 > all[it] }
    fun inRegion(part: PartId, cell: Int): Boolean? = regions.labels[part]?.get(cell)?.takeIf { it >= 0 }?.let { regionHeld[it] }
    val sides = sidesOf(chart, curves)
    return SurfaceMask.build(chart) { part, cell ->
        inRegion(part, cell) ?: sides[part]?.get(cell)?.takeIf { it != 0 }?.let { it > 0 } ?: run {
            val beside = chart.neighbours(part, cell).mapNotNull { (p, c) -> inRegion(p, c) }
            if (beside.isEmpty()) mask.held(part, cell) else beside.count { it } * 2 > beside.size || (beside.count { it } * 2 == beside.size && mask.held(part, cell))
        }
    }
}

/**
 * [mask] with the cells where drawing its edges as [after] cuts it differently from drawing them as [before] (see [redrawn]) taking what
 * [after] gives them, every other cell as it is: a curve moved repaints only the cells either side of where it moved.
 */
fun repainted(mask: SurfaceMask, before: List<SurfaceCurve>, after: List<SurfaceCurve>): SurfaceMask {
    val was = redrawn(mask, before)
    val now = redrawn(mask, after)
    return SurfaceMask.build(mask.chart) { part, cell ->
        val next = now.held(part, cell)
        if (next != was.held(part, cell)) next else mask.held(part, cell)
    }
}

/** How far step a curve is walked at, in cells, to find which side of it the cells it runs through lie on. */
private const val SIDE_STEP = 0.25f

/** For each cell a curve runs through, by part, how many of the curve's steps through it have its middle on their left, less those on their right. */
private fun sidesOf(chart: BodyChart, curves: List<SurfaceCurve>): Map<PartId, IntArray> {
    val out = HashMap<PartId, IntArray>()
    for (curve in curves) {
        val points = curve.points
        val spans = points.zipWithNext() + if (curve.closed && points.size > 2) listOf(points.last() to points.first()) else emptyList()
        for ((a, b) in spans) {
            val part = chart.partOf(a) ?: continue
            if (chart.partOf(b)?.id != part.id) continue
            val ax = a.v * CHART_COLUMNS
            val ay = a.u * CHART_ROWS
            var dx = (b.v - a.v) * CHART_COLUMNS
            dx -= CHART_COLUMNS * kotlin.math.floor(dx / CHART_COLUMNS + 0.5f)
            val dy = (b.u - a.u) * CHART_ROWS
            val length = kotlin.math.sqrt(dx * dx + dy * dy)
            if (length == 0f) continue
            val votes = out.getOrPut(part.id) { IntArray(CHART_CELLS) }
            val steps = (length / SIDE_STEP).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val x = ax + dx * t
                val y = ay + dy * t
                val column = kotlin.math.floor(x).toInt()
                val row = kotlin.math.floor(y).toInt().coerceIn(0, CHART_ROWS - 1)
                val cross = dx * (row + 0.5f - y) - dy * (column + 0.5f - x)
                val cell = row * CHART_COLUMNS + ((column % CHART_COLUMNS) + CHART_COLUMNS) % CHART_COLUMNS
                if (cross > 0f) votes[cell]++ else if (cross < 0f) votes[cell]--
            }
        }
    }
    return out
}

/** [mask] with the region [curves] leave [edge] in all held: the cells on the far side of an edge taken away become what the near side is. */
fun withoutEdge(mask: SurfaceMask, edge: SurfaceCurve, curves: List<SurfaceCurve>): SurfaceMask {
    val regions = SurfaceRegions(mask.chart, curves - edge)
    val touched = SurfaceRegions(mask.chart, listOf(edge))
    val onEdge = HashSet<Int>()
    for (part in mask.chart.parts) {
        val walls = touched.labels.getValue(part.id)
        val labels = regions.labels.getValue(part.id)
        for (cell in walls.indices) if (walls[cell] == ON_CURVE && labels[cell] >= 0) onEdge += labels[cell]
    }
    return mask + SurfaceMask.build(mask.chart) { part, cell -> regions.labels[part]?.get(cell)?.let { it in onEdge } == true }
}
