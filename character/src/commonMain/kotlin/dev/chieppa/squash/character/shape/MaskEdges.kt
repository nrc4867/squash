package dev.chieppa.squash.character.shape

import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SurfaceCurve
import kotlin.math.floor

/** How many rounds of shrink-and-grow smoothing round off a mask edge's steps from cell to cell. */
private const val SMOOTH_ROUNDS = 10

/** How far each round pulls a point toward its neighbours' middle, then pushes it back, as Taubin's smoothing does. */
private const val PULL = 0.5f
private const val PUSH = -0.53f

/**
 * One edge of a mask: a closed [curve] on [part], the held cells on its left as it runs round, which meets cells another mask holds
 * along most of its length when it is a [seam].
 */
class MaskEdge(val part: PartId, val curve: SurfaceCurve, val seam: Boolean = false)

/**
 * The edges of [mask]: each closed run of cell sides between a held cell and one not held, on one part, with the side of a part's root
 * ring against the parent cell it sits on counted as a cell side, smoothed from step to step but held on the root ring there. An edge
 * along cells [besides] holds is a seam.
 */
fun edgesOf(mask: SurfaceMask, besides: SurfaceMask? = null): List<MaskEdge> = mask.chart.parts.flatMap { part -> PartEdges(mask, part, besides).loops() }

/** One side of a cell an edge runs along, from lattice point ([x], [y]) one step by ([dx], [dy]), the cell beyond it [outside]. */
private class Side(val x: Int, val y: Int, val dx: Int, val dy: Int, val outside: Pair<PartId, Int>?)

private class PartEdges(val mask: SurfaceMask, val part: ChartPart, val besides: SurfaceMask?) {
    private val parent = part.parent?.let { it to mask.chart.socketCell(part) }

    private fun held(row: Int, column: Int): Boolean = when {
        row < 0 -> if (parent != null) mask.held(parent.first, parent.second) else held(0, column)
        row >= CHART_ROWS -> held(CHART_ROWS - 1, column)
        else -> mask.held(part.id, row * CHART_COLUMNS + wrapColumn(column))
    }

    private fun cell(row: Int, column: Int): Pair<PartId, Int>? = when {
        row < 0 -> parent
        else -> part.id to row * CHART_COLUMNS + wrapColumn(column)
    }

    private fun sides(): List<Side> {
        val out = ArrayList<Side>()
        for (row in 0 until CHART_ROWS) for (column in 0 until CHART_COLUMNS) {
            val here = held(row, column)
            val below = held(row - 1, column)
            if (here != below) {
                out += if (here) Side(column, row, 1, 0, cell(row - 1, column)) else Side(column + 1, row, -1, 0, cell(row, column))
            }
            val left = held(row, column - 1)
            if (here != left) {
                out += if (here) Side(column, row + 1, 0, -1, cell(row, column - 1)) else Side(column, row, 0, 1, cell(row, column))
            }
        }
        return out
    }

    fun loops(): List<MaskEdge> {
        val sides = sides()
        if (sides.isEmpty()) return emptyList()
        val from = HashMap<Long, MutableList<Side>>()
        for (side in sides) from.getOrPut(key(side.x, side.y)) { ArrayList(2) } += side
        val used = HashSet<Side>()
        val out = ArrayList<MaskEdge>()
        for (start in sides) {
            if (start in used) continue
            val xs = ArrayList<Float>()
            val ys = ArrayList<Float>()
            var x = start.x
            var seams = 0
            var count = 0
            var at = start
            while (true) {
                used += at
                xs += x.toFloat()
                ys += at.y.toFloat()
                count++
                if (besides != null && at.outside?.let { (p, c) -> besides.held(p, c) } == true) seams++
                x += at.dx
                val next = from[key(x, at.y + at.dy)].orEmpty().filter { it !in used }
                if (next.isEmpty()) break
                at = turnOf(at, next)
            }
            if (xs.size < 3) continue
            val points = smoothed(xs, ys, (x - start.x).toFloat()).map { (px, py) ->
                Anchor(part.id, part.patch, py / CHART_ROWS, unit(px / CHART_COLUMNS))
            }
            out += MaskEdge(part.id, SurfaceCurve(points, closed = true), seam = seams * 2 > count)
        }
        return out
    }

    /** Of the sides going on from where [at] ends, the one turning most to the left, so cells meeting at a corner stay apart. */
    private fun turnOf(at: Side, next: List<Side>): Side = next.maxBy { side ->
        val cross = at.dx * side.dy - at.dy * side.dx
        val dot = at.dx * side.dx + at.dy * side.dy
        if (cross > 0) 2 else if (dot > 0) 1 else 0
    }

    private fun key(x: Int, y: Int): Long = wrapColumn(x).toLong() * (CHART_ROWS + 2) + y

    /** The loop through ([xs], [ys]) smoothed, the points on the root ring kept on it; it runs [winding] columns round its part. */
    private fun smoothed(xs: List<Float>, ys: List<Float>, winding: Float): List<Pair<Float, Float>> {
        val n = xs.size
        val x = xs.toFloatArray()
        val y = ys.toFloatArray()
        val pinned = BooleanArray(n) { ys[it] == 0f && parent != null }
        repeat(SMOOTH_ROUNDS) {
            for (weight in floatArrayOf(PULL, PUSH)) {
                val nx = x.copyOf()
                val ny = y.copyOf()
                for (i in 0 until n) {
                    val a = (i + n - 1) % n
                    val b = (i + 1) % n
                    val before = if (i == 0) x[a] - winding else x[a]
                    val after = if (i == n - 1) x[b] + winding else x[b]
                    nx[i] = x[i] + weight * ((before + after) / 2f - x[i])
                    if (!pinned[i]) ny[i] = y[i] + weight * ((y[a] + y[b]) / 2f - y[i])
                }
                nx.copyInto(x)
                ny.copyInto(y)
            }
        }
        return (0 until n).map { x[it] to y[it].coerceIn(0f, CHART_ROWS.toFloat()) }
    }
}

private fun wrapColumn(column: Int): Int = ((column % CHART_COLUMNS) + CHART_COLUMNS) % CHART_COLUMNS

/** [v] read round, from 0 up to 1. */
private fun unit(v: Float): Float = v - floor(v)
