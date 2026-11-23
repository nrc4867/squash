package dev.chieppa.squash.geom

import kotlin.math.floor

/** Ends of two runs this close, as a share of the mesh's own reach, are one place. */
private const val JOIN_SHARE = 1e-4f

private const val TINY = 1e-12f

/** The curvature along the view, in turns of the normal per unit of length, below which a contour lies in a hollow and is hidden. */
private const val HOLLOW_CURVATURE = -4f

/** How many of the mesh's mean edge lengths a contour runs at least to be drawn. */
private const val SHORTEST_EDGES = 4f

/** How many of the mesh's mean edge lengths from the smooth contour the faces' own contour lies, at least, where it is a fold. */
private const val FOLD_EDGES = 1.5f

/** A place on a curve across a triangle mesh, with the surface's normal there. */
class CurvePoint(val position: Vec3, val normal: Vec3)

/** A curve across a triangle mesh, its [points] with a value read at each, [carried]. */
class CarriedCurve(val points: List<CurvePoint>, val carried: FloatArray)

/**
 * A triangle mesh read for its lines: vertices at [positions], three values each, [normals] beside them, and [triangles]; each edge
 * with the triangles either side of it, and each triangle's outward normal. A contour is found along its edges and a zero curve across
 * its faces. With [kept], one value a vertex, its smooth contours are kept to where that is at least zero, cut across the faces where it
 * crosses zero, as a fill cut along the same values ends. Never changes once made.
 */
class TriangleLines(
    private val positions: FloatArray,
    private val normals: FloatArray,
    val triangles: IntArray,
    edges: TriangleEdges,
    private val kept: FloatArray? = null,
) {
    /** The lines of a mesh whose edges are found here. */
    constructor(positions: FloatArray, normals: FloatArray, triangles: IntArray, kept: FloatArray? = null) :
        this(positions, normals, triangles, TriangleEdges(positions, positions.size / 3, triangles).all(), kept)

    private val vertexCount = positions.size / 3
    private val triangleCount = triangles.size / 3

    /** Each edge's two vertices and the triangles on either side, -1 where it has one. */
    private val edgeA: IntArray = edges.edgeA
    private val edgeB: IntArray = edges.edgeB
    private val left: IntArray = edges.left
    private val right: IntArray = edges.right

    /** Each triangle's three edges, in the order of its corners. */
    private val triangleEdges = edges.triangleEdges
    private val faceNormals = edges.faceNormals
    private val reach: Float

    init {
        var lx = Float.MAX_VALUE
        var ly = Float.MAX_VALUE
        var lz = Float.MAX_VALUE
        var hx = -Float.MAX_VALUE
        var hy = -Float.MAX_VALUE
        var hz = -Float.MAX_VALUE
        for (v in 0 until vertexCount) {
            lx = minOf(lx, positions[v * 3])
            ly = minOf(ly, positions[v * 3 + 1])
            lz = minOf(lz, positions[v * 3 + 2])
            hx = maxOf(hx, positions[v * 3])
            hy = maxOf(hy, positions[v * 3 + 1])
            hz = maxOf(hz, positions[v * 3 + 2])
        }
        reach = if (vertexCount == 0) 0f else (Vec3(hx, hy, hz) - Vec3(lx, ly, lz)).length * 0.5f
    }

    private fun at(v: Int) = Vec3(positions[v * 3], positions[v * 3 + 1], positions[v * 3 + 2])
    private fun normalAt(v: Int) = Vec3(normals[v * 3], normals[v * 3 + 1], normals[v * 3 + 2])

    /**
     * Where the mesh turns edge on to a viewer that [toViewer] gives the direction of from any point: its edges between a triangle facing
     * the viewer and one facing away, so a line along them lies on the edge the fill shows. Edges with one triangle are no contour.
     */
    fun contours(toViewer: (Vec3) -> Vec3): List<ContourRun> {
        val facing = BooleanArray(triangleCount) { t ->
            val n = Vec3(faceNormals[t * 3], faceNormals[t * 3 + 1], faceNormals[t * 3 + 2])
            (n dot toViewer(at(triangles[t * 3]))) > 0f
        }
        val edges = ArrayList<Pair<Int, Int>>()
        for (e in edgeA.indices) {
            val r = right[e]
            if (r >= 0 && facing[left[e]] != facing[r]) edges += edgeA[e] to edgeB[e]
        }
        val pieces = connectedPieces(edges)
        val runs = walkedEdges(edges).map { run -> IndexedRun(run.map { ContourPoint(at(it), 1f) }, pieces.getValue(run.first())) }
        return joinedAtEnds(runs, reach * JOIN_SHARE)
    }

    /**
     * Where the surface turns edge on to a viewer that [toViewer] gives the direction of from any point, read from the vertex normals
     * rather than the faces: the curves across the triangles where a vertex's normal turns from facing the viewer to facing away, placed
     * along each edge in proportion (Hertzmann and Zorin's interpolated contour), so the line runs smoothly across faces however the
     * mesh's edges lie. Where the surface plainly bends toward the viewer across the line, its curvature along the view below
     * [HOLLOW_CURVATURE], the line is left out: on a closed surface a contour in a hollow is always hidden behind the surface itself. A
     * line shorter than [SHORTEST_EDGES] of the mesh's edges is a fleck where the surface only grazes edge on, and is left out too.
     */
    fun smoothContours(toViewer: (Vec3) -> Vec3): List<ContourRun> {
        val facing = FloatArray(vertexCount) { v -> normalAt(v) dot toViewer(at(v)) }
        val bend = FloatArray(vertexCount)
        val weight = FloatArray(vertexCount)
        for (e in edgeA.indices) {
            val a = edgeA[e]
            val b = edgeB[e]
            val d = at(b) - at(a)
            val squared = d dot d
            if (squared <= TINY) continue
            val bent = ((normalAt(b) - normalAt(a)) dot d) / squared
            for (v in intArrayOf(a, b)) {
                val along = (d dot toViewer(at(v))).let { it * it } / squared
                bend[v] += bent * along
                weight[v] += along
            }
        }
        fun hollow(v: Int) = weight[v] > TINY && bend[v] / weight[v] < HOLLOW_CURVATURE
        val runs = zeroRuns(facing) { t -> !(hollow(triangles[t * 3]) && hollow(triangles[t * 3 + 1]) && hollow(triangles[t * 3 + 2])) }
        val curves = if (kept == null) runs.map { run -> run.map { crossing(it, facing) } } else runs.flatMap { keptPieces(it, facing, kept) }
        val shortest = SHORTEST_EDGES * meanEdge
        return curves.filter { curve -> curve.zipWithNext { a, b -> (b.position - a.position).length }.sum() >= shortest }.mapIndexed { piece, curve ->
            val closed = curve.size > 2 && curve.first().position == curve.last().position
            ContourRun(curve.map { ContourPoint(it.position, 1f) }, closed, piece)
        }
    }

    /**
     * Where the faces turn edge on to a viewer that [toViewer] gives the direction of, along their edges as [contours] finds them, only
     * where that lies further than [FOLD_EDGES] of the mesh's edges from every place of [smooth], the smooth contours ([smoothContours])
     * through the same view, and only where both ends of an edge are at least zero in [shown], one value a vertex, [kept] when it gives
     * none for each: where the fill is drawn. That is a fold: the surface laps over itself
     * where it bunches, as cloth does in a crease, and makes the edge the fill shows, while its vertex normals, averaged across the fold,
     * never turn edge on there, so the smooth contour runs inside the fill or not at all. A fold shorter than [SHORTEST_EDGES] of the
     * mesh's edges is a fleck, and left out as a smooth contour's is.
     */
    fun foldContours(toViewer: (Vec3) -> Vec3, smooth: List<ContourRun>, shown: FloatArray? = kept): List<ContourRun> {
        val apart = FOLD_EDGES * meanEdge
        if (apart <= 0f) return emptyList()
        val gate = shown?.takeIf { it.size == vertexCount } ?: kept
        val near = HashMap<Long, MutableList<Vec3>>()
        fun key(x: Int, y: Int, z: Int): Long = ((x.toLong() and 0x1FFFFF) shl 42) or ((y.toLong() and 0x1FFFFF) shl 21) or (z.toLong() and 0x1FFFFF)
        fun cellOf(v: Float): Int = floor(v / apart).toInt()
        for (run in smooth) for (point in run.points) {
            val p = point.position
            near.getOrPut(key(cellOf(p.x), cellOf(p.y), cellOf(p.z))) { ArrayList() } += p
        }
        fun clear(p: Vec3): Boolean {
            val cx = cellOf(p.x)
            val cy = cellOf(p.y)
            val cz = cellOf(p.z)
            for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                val points = near[key(cx + dx, cy + dy, cz + dz)] ?: continue
                for (q in points) if ((q - p).length < apart) return false
            }
            return true
        }
        val facing = BooleanArray(triangleCount) { t ->
            val n = Vec3(faceNormals[t * 3], faceNormals[t * 3 + 1], faceNormals[t * 3 + 2])
            (n dot toViewer(at(triangles[t * 3]))) > 0f
        }
        val edges = ArrayList<Pair<Int, Int>>()
        for (e in edgeA.indices) {
            val r = right[e]
            if (r < 0 || facing[left[e]] == facing[r]) continue
            val a = edgeA[e]
            val b = edgeB[e]
            if (gate != null && (gate[a] < 0f || gate[b] < 0f)) continue
            if (clear((at(a) + at(b)) * 0.5f)) edges += a to b
        }
        val pieces = connectedPieces(edges)
        val lengths = HashMap<Int, Float>()
        for ((a, b) in edges) pieces.getValue(a).let { lengths[it] = (lengths[it] ?: 0f) + (at(b) - at(a)).length }
        val shortest = SHORTEST_EDGES * meanEdge
        val runs = walkedEdges(edges).map { run -> IndexedRun(run.map { ContourPoint(at(it), 1f) }, pieces.getValue(run.first())) }
            .filter { (lengths[it.piece] ?: 0f) >= shortest }
        return joinedAtEnds(runs, reach * JOIN_SHARE)
    }

    /** How long the mesh's edges are on average. */
    private val meanEdge: Float by lazy {
        if (edgeA.isEmpty()) 0f else edgeA.indices.sumOf { (at(edgeB[it]) - at(edgeA[it])).length.toDouble() }.toFloat() / edgeA.size
    }

    /**
     * The curves where [values], one per vertex, cross zero over the triangles [included] keeps, each placed along an edge in
     * proportion to the values at its ends, with the normal eased the same way. A closed curve repeats its first place last.
     */
    fun zeroCurves(values: FloatArray, included: (Int) -> Boolean): List<List<CurvePoint>> = zeroRuns(values, included).map { run -> run.map { crossing(it, values) } }

    /** [zeroCurves], each with [carry], one value a vertex, read where the curve crosses each edge, eased along the edge as its place is. */
    fun zeroCurvesCarrying(values: FloatArray, carry: FloatArray, included: (Int) -> Boolean): List<CarriedCurve> = zeroRuns(values, included).map { run ->
        CarriedCurve(run.map { crossing(it, values) }, FloatArray(run.size) { carried(run[it], values, carry) })
    }

    /**
     * The pieces of the zero curve of [values] along [run], a run of the edges it crosses, where [keep] read at its crossings is at least
     * zero, each ending where [keep] crosses zero between two of them.
     */
    private fun keptPieces(run: List<Int>, values: FloatArray, keep: FloatArray): List<List<CurvePoint>> {
        val pieces = ArrayList<List<CurvePoint>>()
        var piece = ArrayList<CurvePoint>()
        var last: CurvePoint? = null
        var lastKeep = 0f
        for (e in run) {
            val point = crossing(e, values)
            val here = carried(e, values, keep)
            val before = last
            if (before != null && (here >= 0f) != (lastKeep >= 0f)) {
                val t = lastKeep / (lastKeep - here)
                val n = before.normal * (1f - t) + point.normal * t
                val edge = CurvePoint(before.position + (point.position - before.position) * t, if (n.length > TINY) n.normalized() else n)
                piece.add(edge)
                if (here < 0f) {
                    pieces += piece
                    piece = ArrayList()
                }
            }
            if (here >= 0f) piece.add(point)
            last = point
            lastKeep = here
        }
        if (piece.size > 1) pieces += piece
        return pieces.filter { it.size > 1 }
    }

    /** [carry] read where the zero curve of [values] crosses edge [e]. */
    private fun carried(e: Int, values: FloatArray, carry: FloatArray): Float {
        val a = values[edgeA[e]]
        val b = values[edgeB[e]]
        val t = if (a != b) a / (a - b) else 0.5f
        return carry[edgeA[e]] * (1f - t) + carry[edgeB[e]] * t
    }

    /** The runs of edges the zero curve of [values] crosses over the triangles [included] keeps. */
    private fun zeroRuns(values: FloatArray, included: (Int) -> Boolean): List<List<Int>> {
        val edges = edgeA.size
        val links = IntArray(edges * 2)
        val linked = ByteArray(edges)
        for (t in 0 until triangleCount) {
            if (!included(t)) continue
            var first = -1
            for (c in 0 until 3) {
                val e = triangleEdges[t * 3 + c]
                if ((values[edgeA[e]] < 0f) == (values[edgeB[e]] < 0f)) continue
                if (first < 0) {
                    first = e
                } else {
                    link(links, linked, first, e)
                    link(links, linked, e, first)
                }
            }
        }
        return walkedLinks(links, linked)
    }

    private fun link(links: IntArray, linked: ByteArray, from: Int, to: Int) {
        val n = linked[from].toInt()
        if (n < 2) links[from * 2 + n] = to
        linked[from] = (n + 1).toByte()
    }

    private fun crossing(e: Int, values: FloatArray): CurvePoint {
        val a = values[edgeA[e]]
        val b = values[edgeB[e]]
        val t = if (a != b) a / (a - b) else 0.5f
        val p = at(edgeA[e]) + (at(edgeB[e]) - at(edgeA[e])) * t
        val n = normalAt(edgeA[e]) * (1f - t) + normalAt(edgeB[e]) * t
        return CurvePoint(p, if (n.length > TINY) n.normalized() else n)
    }

    /**
     * Each run through [links], two an edge with [linked] saying how many it holds, from an end where it has one; a run that comes back
     * to its start repeats it.
     */
    private fun walkedLinks(links: IntArray, linked: ByteArray): List<List<Int>> {
        val seen = BooleanArray(linked.size)
        val runs = ArrayList<List<Int>>()
        fun next(at: Int): Int {
            for (k in 0 until minOf(2, linked[at].toInt())) if (!seen[links[at * 2 + k]]) return links[at * 2 + k]
            return -1
        }
        fun walk(start: Int) {
            val run = arrayListOf(start)
            seen[start] = true
            var at = start
            while (true) {
                val step = next(at)
                if (step < 0) break
                seen[step] = true
                run += step
                at = step
            }
            val back = (0 until minOf(2, linked[at].toInt())).any { links[at * 2 + it] == start }
            if (run.size > 2 && back) run += start
            runs += run
        }
        for (e in linked.indices) if (linked[e].toInt() == 1 && !seen[e]) walk(e)
        for (e in linked.indices) if (linked[e] > 0 && !seen[e]) walk(e)
        return runs
    }
}
