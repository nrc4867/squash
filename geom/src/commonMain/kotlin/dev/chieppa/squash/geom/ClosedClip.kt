package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Points closer than this share of the curves' extent are one point. */
private const val SNAP_SHARE = 1e-5
private const val PARALLEL_CROSS = 1e-14
private const val TWO_PI = 2.0 * kotlin.math.PI

private data class Point(val x: Double, val y: Double)

/** A piece of edge between two entries of the vertex table. */
private data class Piece(val from: Int, val to: Int)

/**
 * What of closed loop [subject] lies inside closed loop [by], as counter-clockwise loops with y up. Each is one simple loop of
 * points with an implied edge from last to first. Loops that only touch along an edge or at a point share nothing; a loop
 * inside the other comes back whole, as do two that coincide. A loop with no area clips to nothing.
 */
fun clipClosed(subject: List<Vec2>, by: List<Vec2>): List<List<Vec2>> {
    val every = subject + by
    if (every.isEmpty()) return emptyList()
    val extent = maxOf(every.maxOf { it.x } - every.minOf { it.x }, every.maxOf { it.y } - every.minOf { it.y })
    if (extent <= 0f) return emptyList()
    val table = VertexTable(extent * SNAP_SHARE)
    val a = table.loopOf(subject)
    val b = table.loopOf(by)
    if (table.area(a) == 0.0 || table.area(b) == 0.0) return emptyList()
    return table.intersection(a, b).map { loop -> loop.map { table.vec(it) } }
}

/** [subject] clipped by [by], as [clipClosed] does, the curves taken as straight edges [stepsPerSegment] to a section. */
fun clipClosed(subject: BezierPath, by: BezierPath, stepsPerSegment: Int = CLIP_FLATTEN_STEPS): List<List<Vec2>> =
    clipClosed(subject.flattenedPoints(stepsPerSegment), by.flattenedPoints(stepsPerSegment))

private const val CLIP_FLATTEN_STEPS = 16

private fun BezierPath.flattenedPoints(steps: Int): List<Vec2> =
    flatten(steps).let { line -> line.xs.indices.map { Vec2(line.xs[it], line.ys[it]) } }

/** The points of both loops, each held once, so that a shared point is one index and edges compare exactly. */
private class VertexTable(private val snap: Double) {
    private val points = mutableListOf<Point>()

    fun vec(index: Int) = points[index].let { Vec2(it.x.toFloat(), it.y.toFloat()) }

    private fun indexOf(p: Point): Int {
        val found = points.indexOfFirst { abs(it.x - p.x) <= snap && abs(it.y - p.y) <= snap }
        if (found >= 0) return found
        points += p
        return points.lastIndex
    }

    /** [loop] as table indices, counter-clockwise, with repeats in a row dropped. */
    fun loopOf(loop: List<Vec2>): List<Int> {
        val indices = mutableListOf<Int>()
        for (p in loop) {
            val index = indexOf(Point(p.x.toDouble(), p.y.toDouble()))
            if (indices.lastOrNull() != index) indices += index
        }
        while (indices.size > 1 && indices.first() == indices.last()) indices.removeLast()
        return if (area(indices) < 0.0) indices.reversed() else indices
    }

    /** Twice the signed area, or 0 for a loop within the snap of having none. */
    fun area(loop: List<Int>): Double {
        if (loop.size < 3) return 0.0
        var sum = 0.0
        for (i in loop.indices) {
            val p = points[loop[i]]
            val q = points[loop[(i + 1) % loop.size]]
            sum += p.x * q.y - q.x * p.y
        }
        return if (abs(sum) <= snap * snap) 0.0 else sum
    }

    fun intersection(a: List<Int>, b: List<Int>): List<List<Int>> {
        val (piecesA, piecesB) = split(a, b)
        val inA = piecesA.toSet()
        val inB = piecesB.toSet()
        val keptA = piecesA.filter { piece ->
            when {
                piece in inB -> true
                Piece(piece.to, piece.from) in inB -> false
                else -> isInside(midpoint(piece), b)
            }
        }
        val keptB = piecesB.filter { piece ->
            piece !in inA && Piece(piece.to, piece.from) !in inA && isInside(midpoint(piece), a)
        }
        return linked(keptA + keptB).filter { area(it) > 0.0 }
    }

    private fun midpoint(piece: Piece): Point {
        val p = points[piece.from]
        val q = points[piece.to]
        return Point((p.x + q.x) / 2.0, (p.y + q.y) / 2.0)
    }

    /** Both loops' edges cut wherever the other loop crosses or touches them. */
    private fun split(a: List<Int>, b: List<Int>): Pair<List<Piece>, List<Piece>> {
        val edgesA = edgesOf(a)
        val edgesB = edgesOf(b)
        val cutsA = List(edgesA.size) { mutableSetOf<Int>() }
        val cutsB = List(edgesB.size) { mutableSetOf<Int>() }
        for (i in edgesA.indices) {
            for (j in edgesB.indices) {
                cut(edgesA[i], edgesB[j], cutsA[i], cutsB[j])
            }
        }
        return pieces(edgesA, cutsA) to pieces(edgesB, cutsB)
    }

    private fun edgesOf(loop: List<Int>) = loop.indices.map { Piece(loop[it], loop[(it + 1) % loop.size]) }

    private fun cut(e: Piece, f: Piece, cutsE: MutableSet<Int>, cutsF: MutableSet<Int>) {
        if (isOn(f.from, e)) cutsE += f.from
        if (isOn(f.to, e)) cutsE += f.to
        if (isOn(e.from, f)) cutsF += e.from
        if (isOn(e.to, f)) cutsF += e.to
        val crossing = crossing(e, f) ?: return
        val at = indexOf(crossing)
        cutsE += at
        cutsF += at
    }

    /** Whether point [index] lies on [edge], short of its ends. */
    private fun isOn(index: Int, edge: Piece): Boolean {
        if (index == edge.from || index == edge.to) return false
        val p = points[index]
        val s = points[edge.from]
        val e = points[edge.to]
        val dx = e.x - s.x
        val dy = e.y - s.y
        val along = dx * dx + dy * dy
        val t = ((p.x - s.x) * dx + (p.y - s.y) * dy) / along
        return t > 0.0 && t < 1.0 && hypot(s.x + dx * t - p.x, s.y + dy * t - p.y) <= snap
    }

    /** Where [e] and [f] cross, each strictly inside its span; parallel edges do not cross. */
    private fun crossing(e: Piece, f: Piece): Point? {
        val p = points[e.from]
        val q = points[e.to]
        val r = points[f.from]
        val s = points[f.to]
        val ex = q.x - p.x
        val ey = q.y - p.y
        val fx = s.x - r.x
        val fy = s.y - r.y
        val cross = ex * fy - ey * fx
        if (abs(cross) <= PARALLEL_CROSS * hypot(ex, ey) * hypot(fx, fy)) return null
        val te = ((r.x - p.x) * fy - (r.y - p.y) * fx) / cross
        val tf = ((r.x - p.x) * ey - (r.y - p.y) * ex) / cross
        return if (te > 0.0 && te < 1.0 && tf > 0.0 && tf < 1.0) Point(p.x + ex * te, p.y + ey * te) else null
    }

    private fun pieces(edges: List<Piece>, cuts: List<Set<Int>>): List<Piece> = edges.indices.flatMap { i ->
        val edge = edges[i]
        val s = points[edge.from]
        val stops = cuts[i].filter { it != edge.from && it != edge.to }.sortedBy {
            val p = points[it]
            hypot(p.x - s.x, p.y - s.y)
        }
        (listOf(edge.from) + stops + edge.to).zipWithNext { from, to -> Piece(from, to) }
    }

    /** Whether [p] is inside [loop] by counting crossings of a ray along +x. */
    private fun isInside(p: Point, loop: List<Int>): Boolean {
        var inside = false
        for (i in loop.indices) {
            val a = points[loop[i]]
            val b = points[loop[(i + 1) % loop.size]]
            if ((a.y > p.y) != (b.y > p.y) && p.x < a.x + (p.y - a.y) / (b.y - a.y) * (b.x - a.x)) inside = !inside
        }
        return inside
    }

    /**
     * The closed loops [kept] joins into. Kept pieces have the inside on their left, so where several leave one point the
     * walk takes the sharpest turn left, which keeps lobes that meet at a point as loops of their own. A walk that finds no way
     * on is dropped.
     */
    private fun linked(kept: List<Piece>): List<List<Int>> {
        val leaving = kept.groupBy { it.from }.mapValues { it.value.toMutableList() }
        val loops = mutableListOf<List<Int>>()
        for (start in kept) {
            if (leaving.getValue(start.from).remove(start)) {
                val loop = mutableListOf(start.from)
                var at = start
                while (at.to != start.from) {
                    val next = leaving[at.to]?.maxByOrNull { leftTurn(at, it) } ?: break
                    leaving.getValue(at.to).remove(next)
                    loop += next.from
                    at = next
                }
                if (at.to == start.from) loops += loop
            }
        }
        return loops
    }

    /** How far left [next] turns from [piece], from just past a full turn right to just short of a full turn left. */
    private fun leftTurn(piece: Piece, next: Piece): Double {
        val turn = direction(next) - direction(piece)
        return when {
            turn > kotlin.math.PI -> turn - TWO_PI
            turn <= -kotlin.math.PI -> turn + TWO_PI
            else -> turn
        }
    }

    private fun direction(piece: Piece): Double {
        val p = points[piece.from]
        val q = points[piece.to]
        return atan2(q.y - p.y, q.x - p.x)
    }
}
