package dev.chieppa.squash.character.shape

import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_CELLS
import dev.chieppa.squash.document.ChartBox
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SurfaceCurve
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/** The label of a cell a curve runs through, which belongs to no region. */
const val ON_CURVE = -1

/** How finely a curve's span between two points is walked, in cells. */
private const val WALK_STEP = 0.5f

/**
 * A body's [chart] cut by [curves] into regions, each a run of cells reached from one another without crossing a curve: a curve's cells
 * are walls, a curve [SurfaceCurve.mirrored] cuts on both sides of the middle, and a curve stepping onto another part walls the cells
 * either side of the step. Regions are numbered from 0; [labels] holds one per cell of each part, [ON_CURVE] on a wall.
 */
class SurfaceRegions(val chart: BodyChart, val curves: List<SurfaceCurve>) {
    /** Each part's cells, row by row, by the region they lie in. */
    val labels: Map<PartId, IntArray>

    /** How many regions there are. */
    val count: Int

    /** Each region's area. */
    val areas: FloatArray

    /** Each curve's regions on either side, those its cells touch. */
    val sides: List<Set<Int>>

    init {
        val walls = chart.parts.associate { it.id to BooleanArray(CELLS) }
        val curveCells = curves.map { curve -> cellsOf(curve).onEach { (part, cell) -> walls[part]?.set(cell, true) } }
        val held = chart.parts.associate { it.id to IntArray(CELLS) { ON_CURVE } }
        var next = 0
        val area = ArrayList<Float>()
        for (part in chart.parts) for (start in 0 until CELLS) {
            if (walls.getValue(part.id)[start] || held.getValue(part.id)[start] != ON_CURVE) continue
            area += flood(part.id, start, next, walls, held)
            next++
        }
        labels = held
        count = next
        areas = area.toFloatArray()
        sides = curveCells.map { cells ->
            cells.flatMapTo(HashSet()) { (part, cell) -> chart.neighbours(part, cell).mapNotNull { (p, c) -> held[p]?.get(c)?.takeIf { it != ON_CURVE } } }
        }
    }

    /** The region at [anchor], or null on a wall or off the chart. */
    fun regionAt(anchor: Anchor): Int? {
        val part = chart.partOf(anchor) ?: return null
        return labels.getValue(part.id)[cellAt(anchor.u, anchor.v)].takeIf { it != ON_CURVE }
    }

    /** The regions across a curve from [region]: those sharing a curve with it. */
    fun across(region: Int): Set<Int> = sides.filter { region in it }.flatMapTo(HashSet()) { it } - region

    /** The smallest of [regions] by area, or null for none. */
    fun smallest(regions: Collection<Int>): Int? = regions.minByOrNull { areas[it] }

    /** A place in [region], on the first cell of it found, as an anchor of its part. */
    fun placeIn(region: Int): Anchor? {
        for (part in chart.parts) {
            val cells = labels.getValue(part.id)
            val cell = cells.indexOfFirst { it == region }.takeIf { it >= 0 } ?: continue
            return cellMiddle(part.id, part.patch, cell)
        }
        return null
    }

    /** What of each part [regions] hold, as boxes (see [ChartBox]); a part they hold none of is left out. */
    fun boxes(regions: Set<Int>): Map<PartId, ChartBox> = chart.parts.mapNotNull { part ->
        val cells = labels.getValue(part.id)
        ChartBox.over { cells[it] in regions }?.let { part.id to it }
    }.toMap()

    private fun flood(part: PartId, start: Int, label: Int, walls: Map<PartId, BooleanArray>, held: Map<PartId, IntArray>): Float {
        val queue = ArrayDeque(listOf(part to start))
        held.getValue(part)[start] = label
        var area = 0f
        while (queue.isNotEmpty()) {
            val (p, cell) = queue.removeFirst()
            area += chart.part(p)?.cellArea ?: 0f
            for ((q, other) in chart.neighbours(p, cell)) {
                val cells = held[q] ?: continue
                if (walls.getValue(q)[other] || cells[other] != ON_CURVE) continue
                cells[other] = label
                queue += q to other
            }
        }
        return area
    }

    /** The cells [curve] runs through, by part, its mirror's too. */
    private fun cellsOf(curve: SurfaceCurve): List<Pair<PartId, Int>> {
        val points = curve.points.mapNotNull { anchor -> chart.partOf(anchor)?.let { Placed(it.id, anchor.u, anchor.v) } }
        val walked = walk(points, curve.closed)
        if (!curve.mirrored) return walked
        return walked + walk(points.map { it.copy(v = -it.v) }, curve.closed)
    }

    private data class Placed(val part: PartId, val u: Float, val v: Float)

    private fun walk(points: List<Placed>, closed: Boolean): List<Pair<PartId, Int>> {
        val out = ArrayList<Pair<PartId, Int>>()
        val spans = points.zipWithNext() + if (closed && points.size > 2) listOf(points.last() to points.first()) else emptyList()
        if (points.size == 1) out += points[0].part to cellOf(points[0].u, points[0].v)
        for ((a, b) in spans) {
            if (a.part != b.part) {
                out += across(a, b)
                continue
            }
            out += along(a, b)
        }
        return out
    }

    /**
     * The cells a span from [a] to [b] on parts that meet runs through: up its own part to where the child sits on the parent, then from
     * the child's root ring, so the wall it lays has no gap at the join; just the two cells where the parts do not meet.
     */
    private fun across(a: Placed, b: Placed): List<Pair<PartId, Int>> {
        val childOfA = chart.part(b.part)?.takeIf { it.parent == a.part }
        val childOfB = chart.part(a.part)?.takeIf { it.parent == b.part }
        return when {
            childOfA != null -> along(a, Placed(a.part, childOfA.socketU, childOfA.socketV)) + along(Placed(b.part, 0f, b.v), b)
            childOfB != null -> along(a, Placed(a.part, 0f, a.v)) + along(Placed(b.part, childOfB.socketU, childOfB.socketV), b)
            else -> listOf(a.part to cellOf(a.u, a.v), b.part to cellOf(b.u, b.v))
        }
    }

    private fun along(a: Placed, b: Placed): List<Pair<PartId, Int>> {
        val out = ArrayList<Pair<PartId, Int>>()
        run {
            var dv = b.v - a.v
            dv -= floor(dv + 0.5f)
            val du = b.u - a.u
            val steps = ceil(max(abs(du) * CHART_ROWS, abs(dv) * CHART_COLUMNS) / WALK_STEP).toInt().coerceAtLeast(1)
            var last = -1
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val cell = cellOf(a.u + du * t, a.v + dv * t)
                if (last >= 0 && cell / CHART_COLUMNS != last / CHART_COLUMNS && cell % CHART_COLUMNS != last % CHART_COLUMNS) {
                    out += a.part to (cell / CHART_COLUMNS) * CHART_COLUMNS + last % CHART_COLUMNS
                }
                if (cell != last) out += a.part to cell
                last = cell
            }
        }
        return out
    }

    private fun cellOf(u: Float, v: Float): Int = cellAt(u, v)

    private companion object {
        const val CELLS = CHART_CELLS
    }
}
