package dev.chieppa.squash.character.shape

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
            cells.flatMapTo(HashSet()) { (part, cell) -> neighbours(part, cell).mapNotNull { (p, c) -> held[p]?.get(c)?.takeIf { it != ON_CURVE } } }
        }
    }

    /** The region at [anchor], or null on a wall or off the chart. */
    fun regionAt(anchor: Anchor): Int? {
        val part = chart.partOf(anchor) ?: return null
        return labels.getValue(part.id)[rowAt(anchor.u) * CHART_COLUMNS + columnAt(anchor.v)].takeIf { it != ON_CURVE }
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
            return Anchor(part.id, part.patch, (cell / CHART_COLUMNS + 0.5f) / CHART_ROWS, (cell % CHART_COLUMNS + 0.5f) / CHART_COLUMNS)
        }
        return null
    }

    /** What of each part [regions] hold, as boxes (see [ChartBox]); a part they hold none of is left out. */
    fun boxes(regions: Set<Int>): Map<PartId, ChartBox> = chart.parts.mapNotNull { part ->
        val cells = labels.getValue(part.id)
        var low = CHART_ROWS
        var high = -1
        val columns = BooleanArray(CHART_COLUMNS)
        var held = 0
        for (cell in cells.indices) if (cells[cell] in regions) {
            val row = cell / CHART_COLUMNS
            low = minOf(low, row)
            high = max(high, row)
            columns[cell % CHART_COLUMNS] = true
            held++
        }
        if (high < 0) null else part.id to ChartBox.of(low, high, columns, held)
    }.toMap()

    private fun flood(part: PartId, start: Int, label: Int, walls: Map<PartId, BooleanArray>, held: Map<PartId, IntArray>): Float {
        val queue = ArrayDeque(listOf(part to start))
        held.getValue(part)[start] = label
        var area = 0f
        while (queue.isNotEmpty()) {
            val (p, cell) = queue.removeFirst()
            area += chart.part(p)?.cellArea ?: 0f
            for ((q, other) in neighbours(p, cell)) {
                val cells = held[q] ?: continue
                if (walls.getValue(q)[other] || cells[other] != ON_CURVE) continue
                cells[other] = label
                queue += q to other
            }
        }
        return area
    }

    /** The cells beside [cell] of [part]: along and round it, and from its root row to the cell of its parent it sits on, and back. */
    private fun neighbours(part: PartId, cell: Int): List<Pair<PartId, Int>> {
        val row = cell / CHART_COLUMNS
        val column = cell % CHART_COLUMNS
        val out = ArrayList<Pair<PartId, Int>>(6)
        if (row > 0) out += part to cell - CHART_COLUMNS
        if (row < CHART_ROWS - 1) out += part to cell + CHART_COLUMNS
        out += part to row * CHART_COLUMNS + (column + 1) % CHART_COLUMNS
        out += part to row * CHART_COLUMNS + (column + CHART_COLUMNS - 1) % CHART_COLUMNS
        val chartPart = chart.part(part)
        val parent = chartPart?.parent
        if (row == 0 && parent != null) out += parent to socketCell(chartPart)
        for (child in chart.childrenOf(part)) if (socketCell(child) == cell) for (c in 0 until CHART_COLUMNS) out += child.id to c
        return out
    }

    private fun socketCell(part: ChartPart): Int = rowAt(part.socketU) * CHART_COLUMNS + columnAt(part.socketV)

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

    private fun cellOf(u: Float, v: Float): Int = rowAt(u) * CHART_COLUMNS + columnAt(v)

    private companion object {
        const val CELLS = CHART_ROWS * CHART_COLUMNS
    }
}

/**
 * The cells a set of regions holds of one part as a box reaching half a cell onto the walls beside it: along it [from] to [to] in u,
 * from 0 or to 1 where it reaches a row of the part's ends or the one beside it, and round it [fromV] to [toV], 0 to 1 the whole way round, else starting below
 * 0 where it crosses v 0; [cells] is how many it holds.
 */
data class ChartBox(val from: Float, val to: Float, val fromV: Float, val toV: Float, val cells: Int) {
    val allRound: Boolean get() = toV - fromV >= 1f

    /** Whether it holds the whole part. */
    val whole: Boolean get() = from <= 0f && to >= 1f && allRound

    companion object {
        internal fun of(low: Int, high: Int, columns: BooleanArray, cells: Int): ChartBox {
            val from = if (low <= 1) 0f else (low - 0.5f) / CHART_ROWS
            val to = if (high >= CHART_ROWS - 2) 1f else (high + 1.5f) / CHART_ROWS
            if (columns.all { it }) return ChartBox(from, to, 0f, 1f, cells)
            var gapStart = 0
            var gapLength = 0
            for (start in 0 until CHART_COLUMNS) {
                if (columns[start] || !columns[(start + CHART_COLUMNS - 1) % CHART_COLUMNS]) continue
                var length = 0
                while (length < CHART_COLUMNS && !columns[(start + length) % CHART_COLUMNS]) length++
                if (length > gapLength) {
                    gapLength = length
                    gapStart = start
                }
            }
            val first = (gapStart + gapLength) % CHART_COLUMNS
            var fromV = (first - 0.5f) / CHART_COLUMNS
            var toV = (first + CHART_COLUMNS - gapLength + 0.5f) / CHART_COLUMNS
            if (toV - fromV >= 1f) return ChartBox(from, to, 0f, 1f, cells)
            if (toV > 1f) {
                fromV -= 1f
                toV -= 1f
            }
            return ChartBox(from, to, fromV, toV, cells)
        }
    }
}
