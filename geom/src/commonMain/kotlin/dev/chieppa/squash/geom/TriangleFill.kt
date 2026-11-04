package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Which part of a grid cell a [TriangleFill] covers: a triangle either side of the diagonal, or all of a cell against a pole. */
internal enum class CellPart { LOWER, UPPER, WHOLE }

/** A straight stretch between two of [TriangleFill]'s points. */
private data class Stretch(val from: Int, val to: Int)

/**
 * What of one triangle of the grid, or of a cell against a pole, an outline fills. The triangle's border, split at every place an outline touches it, and the
 * outline's pieces inside it, split where they meet, make a planar graph. Each of its faces is walked out and triangulated,
 * and kept when a point well inside it is filled under the non-zero rule, so any overlap of an outline with itself is one fill
 * and no fine detail can leave an edge with no way on.
 */
internal class TriangleFill(private val pieces: GridPieces, private val ring: Int, private val column: Int, private val part: CellPart) {
    private val upper get() = part == CellPart.UPPER
    val xs = ArrayList<Double>()
    val ys = ArrayList<Double>()
    private val border = ArrayList<Int>()

    /** One face of the graph: its triangles, whether they are filled, and the half-edges around it. */
    private class Face(val triangles: List<Int>, val filled: Boolean, val around: List<List<Int>>)

    /** The filled triangles, as triples of indices into [xs] and [ys], counter-clockwise. */
    fun filledTriangles(): List<Int> {
        addBorder()
        val stretches = ArrayList<Stretch>(border.size)
        for (i in border.indices) stretches += Stretch(border[i], border[(i + 1) % border.size])
        val inner = innerStretches()
        stretches += inner
        val faces = faces(stretches)
        if (inner.isEmpty()) return faces.filter { it.filled }.flatMap { it.triangles }
        val filledLeft = HashSet<Stretch>()
        for (face in faces) {
            if (!face.filled) continue
            for (cycle in face.around) for (i in cycle.indices) filledLeft += Stretch(cycle[i], cycle[(i + 1) % cycle.size])
        }
        val edges = stretches.filter { (it in filledLeft) != (Stretch(it.to, it.from) in filledLeft) }
        return faces(edges).filter { it.filled }.flatMap { it.triangles }
    }

    private fun faces(stretches: List<Stretch>): List<Face> {
        val cycles = faceCycles(stretches)
        val outers = cycles.filter { loopArea(it, xs, ys) > 0.0 }
        val holes = cycles.filter { loopArea(it, xs, ys) < 0.0 && !encloses(it) }
        return outers.map { outer ->
            val inside = holes.filter { hole ->
                contains(outer, xs[hole[0]], ys[hole[0]], xs, ys) && outers.none { it !== outer && loopArea(it, xs, ys) < loopArea(outer, xs, ys) && contains(it, xs[hole[0]], ys[hole[0]], xs, ys) }
            }
            val triangles = triangulated(listOf(outer) + inside, xs, ys)
            Face(triangles, holdsFill(triangles), listOf(outer) + inside)
        }
    }

    /** Whether this clockwise cycle is the outside of what touches the triangle's border, rather than a hole within a face. */
    private fun encloses(cycle: List<Int>): Boolean {
        val area = -loopArea(cycle, xs, ys)
        return area >= 0.0 && cycle.any { it in border }
    }

    /** Whether the middle of the largest of [triangles] is filled: the point of them furthest from any edge, near enough. */
    private fun holdsFill(triangles: List<Int>): Boolean {
        var best = -1.0
        var x = 0.0
        var y = 0.0
        for (t in 0 until triangles.size / 3) {
            val a = triangles[t * 3]
            val b = triangles[t * 3 + 1]
            val c = triangles[t * 3 + 2]
            val area = (xs[b] - xs[a]) * (ys[c] - ys[a]) - (ys[b] - ys[a]) * (xs[c] - xs[a])
            if (area > best) {
                best = area
                x = (xs[a] + xs[b] + xs[c]) / 3.0
                y = (ys[a] + ys[b] + ys[c]) / 3.0
            }
        }
        return best > 0.0 && pieces.windingAt(x, y) != 0
    }

    /** The triangle's corners and the cuts between them, counter-clockwise. */
    private fun addBorder() {
        val r = ring.toDouble()
        val c = column.toDouble()
        if (part == CellPart.WHOLE) {
            addCorner(r, c)
            pieces.cutsOn(GridLine.COLUMN, pieces.columnLine(column), ring).forEach { addCorner(r + it, c) }
            addCorner(r + 1.0, c)
            pieces.cutsOn(GridLine.RING, ring + 1, column).forEach { addCorner(r + 1.0, c + it) }
            addCorner(r + 1.0, c + 1.0)
            pieces.cutsOn(GridLine.COLUMN, pieces.columnLine(column + 1), ring).asReversed().forEach { addCorner(r + it, c + 1.0) }
            addCorner(r, c + 1.0)
            pieces.cutsOn(GridLine.RING, ring, column).asReversed().forEach { addCorner(r, c + it) }
        } else if (!upper) {
            addCorner(r, c)
            pieces.cutsOn(GridLine.COLUMN, pieces.columnLine(column), ring).forEach { addCorner(r + it, c) }
            addCorner(r + 1.0, c)
            pieces.cutsOn(GridLine.RING, ring + 1, column).forEach { addCorner(r + 1.0, c + it) }
            addCorner(r + 1.0, c + 1.0)
            pieces.cutsOn(GridLine.DIAGONAL, ring, column).asReversed().forEach { addCorner(r + it, c + it) }
        } else {
            addCorner(r, c)
            pieces.cutsOn(GridLine.DIAGONAL, ring, column).forEach { addCorner(r + it, c + it) }
            addCorner(r + 1.0, c + 1.0)
            pieces.cutsOn(GridLine.COLUMN, pieces.columnLine(column + 1), ring).asReversed().forEach { addCorner(r + it, c + 1.0) }
            addCorner(r, c + 1.0)
            pieces.cutsOn(GridLine.RING, ring, column).asReversed().forEach { addCorner(r, c + it) }
        }
    }

    private fun addCorner(x: Double, y: Double) {
        xs += x
        ys += y
        border += xs.lastIndex
    }

    private fun indexOf(x: Double, y: Double): Int {
        for (i in xs.indices) if (abs(xs[i] - x) <= 2.0 * GRID_SNAP && abs(ys[i] - y) <= 2.0 * GRID_SNAP) return i
        xs += x
        ys += y
        return xs.lastIndex
    }

    /** The pieces inside the triangle, split wherever they cross or touch one another. */
    private fun innerStretches(): List<Stretch> {
        val raw = pieces.piecesIn(ring, column, upper).map { Stretch(indexOf(it.fromU, it.fromV), indexOf(it.toU, it.toV)) }.filter { it.from != it.to }
        val cuts = List(raw.size) { mutableSetOf<Int>() }
        for (i in raw.indices) {
            for (j in i + 1 until raw.size) {
                val a = raw[i]
                val b = raw[j]
                addTouches(a, b, cuts[i], cuts[j])
            }
        }
        val out = LinkedHashSet<Stretch>()
        for (i in raw.indices) {
            val stretch = raw[i]
            val stops = cuts[i].filter { it != stretch.from && it != stretch.to }.sortedBy { hypot(xs[it] - xs[stretch.from], ys[it] - ys[stretch.from]) }
            var from = stretch.from
            for (stop in stops + stretch.to) {
                if (stop != from && Stretch(stop, from) !in out) out += Stretch(from, stop)
                from = stop
            }
        }
        return out.toList()
    }

    private fun addTouches(a: Stretch, b: Stretch, cutsA: MutableSet<Int>, cutsB: MutableSet<Int>) {
        if (lies(b.from, a)) cutsA += b.from
        if (lies(b.to, a)) cutsA += b.to
        if (lies(a.from, b)) cutsB += a.from
        if (lies(a.to, b)) cutsB += a.to
        val px = xs[a.from]
        val py = ys[a.from]
        val ax = xs[a.to] - px
        val ay = ys[a.to] - py
        val rx = xs[b.from]
        val ry = ys[b.from]
        val bx = xs[b.to] - rx
        val by = ys[b.to] - ry
        val cross = ax * by - ay * bx
        if (abs(cross) <= 1e-14 * hypot(ax, ay) * hypot(bx, by)) return
        val ta = ((rx - px) * by - (ry - py) * bx) / cross
        val tb = ((rx - px) * ay - (ry - py) * ax) / cross
        if (ta <= 0.0 || ta >= 1.0 || tb <= 0.0 || tb >= 1.0) return
        val at = indexOf(px + ax * ta, py + ay * ta)
        cutsA += at
        cutsB += at
    }

    private fun lies(point: Int, on: Stretch): Boolean {
        if (point == on.from || point == on.to) return false
        val sx = xs[on.from]
        val sy = ys[on.from]
        val dx = xs[on.to] - sx
        val dy = ys[on.to] - sy
        val square = dx * dx + dy * dy
        if (square == 0.0) return false
        val t = ((xs[point] - sx) * dx + (ys[point] - sy) * dy) / square
        return t > 0.0 && t < 1.0 && hypot(sx + dx * t - xs[point], sy + dy * t - ys[point]) <= 2.0 * GRID_SNAP
    }

    /** Every cycle of half-edges around a face of the graph: bounded faces counter-clockwise, the outside of each connected part clockwise. */
    private fun faceCycles(stretches: List<Stretch>): List<List<Int>> {
        val leaving = HashMap<Int, ArrayList<Stretch>>()
        for (stretch in stretches) {
            leaving.getOrPut(stretch.from) { ArrayList() } += stretch
            leaving.getOrPut(stretch.to) { ArrayList() } += Stretch(stretch.to, stretch.from)
        }
        for (around in leaving.values) around.sortBy(::direction)
        val walked = HashSet<Stretch>()
        val cycles = ArrayList<List<Int>>()
        for (around in leaving.values) {
            for (start in around) {
                if (start in walked) continue
                val cycle = ArrayList<Int>()
                var at = start
                while (walked.add(at)) {
                    cycle += at.from
                    val turning = leaving.getValue(at.to)
                    val back = turning.indexOf(Stretch(at.to, at.from))
                    at = turning[(back + turning.size - 1) % turning.size]
                }
                cycles += cycle
            }
        }
        return cycles
    }

    private fun direction(stretch: Stretch) = atan2(ys[stretch.to] - ys[stretch.from], xs[stretch.to] - xs[stretch.from])
}
