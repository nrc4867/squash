package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

/** Grid coordinates closer than this are one place. */
internal const val GRID_SNAP = 1e-9

/** Stops along an edge closer than this share of it are one stop. */
private const val STOP_SNAP = 1e-12

private const val KIND_BITS = 40
private const val RING_BITS = 20

/** Which line of the grid a stretch of grid edge lies on, for the table of where outlines touch it. */
internal enum class GridLine { RING, COLUMN, DIAGONAL }

/** A straight stretch of outline lying within one triangle of the grid, in coordinates where its column holds it. */
internal class GridPiece(val fromU: Double, val fromV: Double, val toU: Double, val toV: Double) {
    val isAlongU: Boolean get() = fromV == toV

    /** The outline's contribution where a ray along lower u from ([u], [v]) crosses this piece: 1 going down v, -1 going up. */
    fun windingAt(u: Double, v: Double): Int {
        if (isAlongU) return 0
        val low = minOf(fromV, toV)
        val high = maxOf(fromV, toV)
        if (v < low || v >= high) return 0
        val crossing = fromU + (toU - fromU) * (v - fromV) / (toV - fromV)
        if (crossing >= u) return 0
        return if (toV < fromV) 1 else -1
    }
}

/** Where an edge of an outline crosses grid lines: its share along the edge and the lines met there. */
private class Stop(val at: Double, var ring: Int? = null, var column: Int? = null, var diagonal: Int? = null)

/**
 * An outline cut along every line of a [rings] by [columns] grid: rings, columns and each cell's diagonal, the lines the mesh's
 * triangles are bounded by. Every piece then lies in one triangle, a place an outline crosses a line is computed once and is the same
 * value to both triangles sharing it, and [cutsOn] says where each stretch of grid edge is touched, which both triangles keep
 * as vertices, so their triangles meet vertex to vertex with nothing to crack. A cell against a pole, flat in the mesh, has no diagonal.
 */
internal class GridPieces(private val rings: Int, private val columns: Int, private val closed: Boolean, private val isPoleCell: (Int) -> Boolean) {
    private val byColumn = Array(columns) { ArrayList<GridPiece>() }
    private val byTriangle = HashMap<Int, ArrayList<GridPiece>>()
    private val cuts = HashMap<Long, ArrayList<Double>>()

    val columnsWithPieces: List<Int> get() = byColumn.indices.filter { byColumn[it].isNotEmpty() }

    fun piecesIn(column: Int): List<GridPiece> = byColumn[column]

    fun piecesIn(ring: Int, column: Int, upper: Boolean): List<GridPiece> = byTriangle[triangle(ring, column, upper)].orEmpty()

    /** The line of points [line] names: the seam's last line is its first on a closed patch. */
    fun columnLine(line: Int): Int = if (closed) line % columns else line

    private fun triangle(ring: Int, column: Int, upper: Boolean) = (ring * columns + column) * 2 + if (upper) 1 else 0

    fun add(path: OutlinePath) {
        for (i in 0 until path.us.size - 1) addEdge(path.us[i], path.vs[i], path.us[i + 1], path.vs[i + 1])
    }

    /** The winding of everything added around the place ([u], [v]). */
    fun windingAt(u: Double, v: Double): Int {
        val around = if (closed) v - floor(v / columns) * columns else v
        if (around < 0.0 || around >= columns) return 0
        var winding = 0
        for (piece in byColumn[floor(around).toInt().coerceAtMost(columns - 1)]) winding += piece.windingAt(u, around)
        return winding
    }

    /** The places, from 0 to 1 and in order, where an outline touches [line] [index] in [position]: a ring, or a column, or a cell's ring. */
    fun cutsOn(line: GridLine, index: Int, position: Int): List<Double> {
        val found = cuts[key(line, index, position)] ?: return emptyList()
        val sorted = found.sorted()
        val kept = ArrayList<Double>(sorted.size)
        for (cut in sorted) if (kept.isEmpty() || cut - kept.last() > GRID_SNAP) kept += cut
        return kept
    }

    private fun key(line: GridLine, index: Int, position: Int): Long =
        (line.ordinal.toLong() shl KIND_BITS) or (index.toLong() shl RING_BITS) or position.toLong()

    private fun addEdge(rawU0: Double, rawV0: Double, rawU1: Double, rawV1: Double) {
        val u0 = snappedToWhole(rawU0, GRID_SNAP)
        val v0 = snappedToWhole(rawV0, GRID_SNAP)
        val u1 = snappedToWhole(rawU1, GRID_SNAP)
        val v1 = snappedToWhole(rawV1, GRID_SNAP)
        val stops = stopsAlong(u0, v0, u1, v1)
        var fromU = u0
        var fromV = v0
        for (stop in stops) {
            val v = stop.column?.toDouble() ?: stop.ring?.let { ring -> stop.diagonal?.let { (ring - it).toDouble() } } ?: (v0 + (v1 - v0) * stop.at)
            val u = stop.ring?.toDouble() ?: stop.diagonal?.let { v + it } ?: (u0 + (u1 - u0) * stop.at)
            addPiece(fromU, fromV, u, v)
            fromU = u
            fromV = v
        }
        addPiece(fromU, fromV, u1, v1)
    }

    private fun stopsAlong(u0: Double, v0: Double, u1: Double, v1: Double): List<Stop> {
        val stops = ArrayList<Stop>()
        crossings(u0, u1) { line, at -> if (line in 0 until rings) stops += Stop(at, ring = line) }
        crossings(v0, v1) { line, at -> stops += Stop(at, column = line) }
        crossings(u0 - v0, u1 - v1) { line, at ->
            val ring = floor(u0 + (u1 - u0) * at).toInt().coerceIn(0, rings - 2)
            if (!isPoleCell(ring)) stops += Stop(at, diagonal = line)
        }
        stops.sortBy { it.at }
        val merged = ArrayList<Stop>(stops.size)
        for (stop in stops) {
            val last = merged.lastOrNull()
            if (last != null && stop.at - last.at < STOP_SNAP) {
                last.ring = last.ring ?: stop.ring
                last.column = last.column ?: stop.column
                last.diagonal = last.diagonal ?: stop.diagonal
            } else {
                merged += stop
            }
        }
        return merged
    }

    private inline fun crossings(from: Double, to: Double, found: (Int, Double) -> Unit) {
        if (from == to) return
        val first = (if (from < to) floor(from) + 1.0 else ceil(from) - 1.0).toInt()
        val last = (if (from < to) ceil(to) - 1.0 else floor(to) + 1.0).toInt()
        val step = if (from < to) 1 else -1
        var line = first
        while (if (step > 0) line <= last else line >= last) {
            found(line, (line - from) / (to - from))
            line += step
        }
    }

    private fun addPiece(fromU: Double, rawFromV: Double, toU: Double, rawToV: Double) {
        if (abs(fromU - toU) < GRID_SNAP && abs(rawFromV - rawToV) < GRID_SNAP) return
        val midV = (rawFromV + rawToV) / 2.0
        if (!closed && (midV < 0.0 || midV > columns)) return
        val turn = if (closed) floor(midV / columns) * columns else 0.0
        val fromV = rawFromV - turn
        val toV = rawToV - turn
        val piece = GridPiece(fromU, fromV, toU, toV)
        val column = floor(midV - turn).toInt().coerceIn(0, columns - 1)
        byColumn[column] += piece
        val midU = (fromU + toU) / 2.0
        if (midU < 0.0 || midU > rings - 1.0) return
        register(fromU, fromV)
        register(toU, toV)
        val ring = floor(midU).toInt().coerceAtMost(rings - 2)
        val x = midU - ring
        val y = midV - turn - column
        val fan = isPoleCell(ring)
        val onLine = x < GRID_SNAP || 1.0 - x < GRID_SNAP || y < GRID_SNAP || 1.0 - y < GRID_SNAP || (!fan && abs(x - y) < GRID_SNAP)
        if (!onLine) byTriangle.getOrPut(triangle(ring, column, !fan && y > x)) { ArrayList() } += piece
    }

    /** Notes that an outline touches whichever grid edge ([u], [v]) lies on, if it lies on one. */
    private fun register(u: Double, v: Double) {
        if (u < -GRID_SNAP || u > rings - 1.0 + GRID_SNAP) return
        val wholeU = round(u)
        val wholeV = round(v)
        val onRing = abs(u - wholeU) < GRID_SNAP
        val onColumn = abs(v - wholeV) < GRID_SNAP
        if (onRing && onColumn) return
        if (onRing) {
            val column = floor(v).toInt().coerceIn(0, columns - 1)
            cut(GridLine.RING, wholeU.toInt(), column, v - column)
            return
        }
        val ring = floor(u).toInt().coerceAtMost(rings - 2)
        if (onColumn) {
            cut(GridLine.COLUMN, columnLine(wholeV.toInt()), ring, u - ring)
            return
        }
        val column = floor(v).toInt().coerceIn(0, columns - 1)
        if (!isPoleCell(ring) && abs((u - ring) - (v - column)) < GRID_SNAP) cut(GridLine.DIAGONAL, ring, column, u - ring)
    }

    private fun cut(line: GridLine, index: Int, position: Int, at: Double) {
        cuts.getOrPut(key(line, index, position)) { ArrayList() } += at
    }
}
