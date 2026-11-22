package dev.chieppa.squash.recipe.figure

import kotlin.math.abs
import kotlin.math.acos

/** Bisections a boundary crossing between two grid corners is narrowed by. */
private const val CROSSING_BISECTIONS = 10

/** How far, in body units, a traced stroke's curve may lie off the boundary it follows before another place is added. */
private const val TRACE_TOLERANCE = 0.0008

/** A turn sharper than this many degrees over [CORNER_REACH] grid steps either way is a corner. */
private const val CORNER_DEGREES = 55.0
private const val CORNER_REACH = 3

/** The fewest boundary points a traced stroke keeps. */
private const val LEAST_POINTS = 4

/** One stretch of a traced boundary, as dense points in order with the figure on its right. */
class TracedRun(val points: List<P2>)

/**
 * The boundary of the region [inside] reports, traced on a grid of [step] over the box from [low] to [high] by marching squares, each
 * crossing narrowed to the boundary itself: closed loops of points, each running with the region on its right.
 */
fun traceBoundary(low: P2, high: P2, step: Double, inside: (P2) -> Boolean): List<List<P2>> {
    val nx = Math.ceil((high.x - low.x) / step).toInt() + 1
    val ny = Math.ceil((high.y - low.y) / step).toInt() + 1
    fun at(i: Int, j: Int) = P2(low.x + i * step, low.y + j * step)
    val grid = Array(nx) { i -> BooleanArray(ny) { j -> i in 1 until nx - 1 && j in 1 until ny - 1 && inside(at(i, j)) } }
    // Each boundary edge of a cell, as from-to between crossing points keyed by the grid edge they lie on, walked with the inside on its right.
    val next = HashMap<Long, Long>()
    val crossing = HashMap<Long, P2>()
    fun edgeKey(i: Int, j: Int, horizontal: Boolean): Long = ((i.toLong() * (ny + 1) + j) shl 1) or (if (horizontal) 1L else 0L)
    fun cross(i: Int, j: Int, horizontal: Boolean): Long {
        val key = edgeKey(i, j, horizontal)
        crossing.getOrPut(key) {
            var a = at(i, j)
            var b = if (horizontal) at(i + 1, j) else at(i, j + 1)
            if (!grid[i][j]) a = b.also { b = a }
            repeat(CROSSING_BISECTIONS) {
                val mid = (a + b) * 0.5
                if (inside(mid)) a = mid else b = mid
            }
            (a + b) * 0.5
        }
        return key
    }
    for (i in 0 until nx - 1) for (j in 0 until ny - 1) {
        // Corners counter-clockwise from the low corner, and the edges between them.
        val corners = listOf(grid[i][j], grid[i + 1][j], grid[i + 1][j + 1], grid[i][j + 1])
        val edges = listOf(Triple(i, j, true), Triple(i + 1, j, false), Triple(i, j + 1, true), Triple(i, j, false))
        // Edge k joins corner k to corner k + 1. Walking from the crossing where the walk round enters the inside to the next where it
        // leaves keeps the inside on the right.
        for (enter in 0 until 4) {
            if (corners[enter] || !corners[(enter + 1) % 4]) continue
            val leave = (1..3).map { (enter + it) % 4 }.first { corners[it] && !corners[(it + 1) % 4] }
            val from = edges[enter].let { (a, b, h) -> cross(a, b, h) }
            val to = edges[leave].let { (a, b, h) -> cross(a, b, h) }
            next[from] = to
        }
    }
    val loops = mutableListOf<List<P2>>()
    val seen = HashSet<Long>()
    for (start in next.keys) {
        if (start in seen) continue
        val loop = mutableListOf<P2>()
        var key = start
        while (key !in seen) {
            seen += key
            loop += crossing.getValue(key)
            key = next[key] ?: break
        }
        if (loop.size >= LEAST_POINTS) loops += loop
    }
    return loops
}

/** Where [line] turns sharper than a corner: the indices of its sharpest turn in each run of sharp turns. */
fun cornersOf(line: List<P2>): List<Int> {
    val sharp = (CORNER_REACH until line.size - CORNER_REACH).filter { i ->
        val a = (line[i] - line[i - CORNER_REACH]).unit()
        val b = (line[i + CORNER_REACH] - line[i]).unit()
        Math.toDegrees(acos((a dot b).coerceIn(-1.0, 1.0))) > CORNER_DEGREES
    }
    val out = mutableListOf<Int>()
    var run = mutableListOf<Int>()
    fun close() {
        if (run.isNotEmpty()) out += run[run.size / 2]
        run = mutableListOf()
    }
    for (i in sharp) {
        if (run.isNotEmpty() && i - run.last() > 1) close()
        run += i
    }
    close()
    return out
}

/**
 * The fewest of [line]'s points, with [corners] kept sharp, that a stroke through them stays within a share of the figure's [height] of the line: each run
 * between corners starts from its ends and takes the worst-followed point until none lies further off. The places and the corners' indices
 * among them.
 */
fun fewestStrokePlaces(line: List<P2>, corners: List<Int>, height: Double): Pair<List<P2>, List<Int>> {
    val tolerance = TRACE_TOLERANCE * height
    val cuts = (listOf(0) + corners + line.lastIndex).distinct().sorted()
    val chosen = sortedSetOf<Int>().apply { addAll(cuts) }
    while (true) {
        val places = chosen.map { line[it] }
        val stroke = FigureStroke.through("trace", places, chosen.withIndex().filter { it.value in corners }.map { it.index }.toSet()).polyline()
        var worst = -1
        var worstError = tolerance
        line.forEachIndexed { i, p ->
            if (i in chosen) return@forEachIndexed
            val error = stroke.minOf { (it - p).length }
            if (error > worstError) {
                worst = i
                worstError = error
            }
        }
        if (worst < 0) break
        chosen += worst
    }
    val list = chosen.toList()
    return list.map { line[it] } to list.withIndex().filter { it.value in corners }.map { it.index }
}

/** Whether two places lie within [reach] of each other. */
fun near(a: P2, b: P2, reach: Double): Boolean = abs(a.x - b.x) <= reach && abs(a.y - b.y) <= reach
