package dev.chieppa.squash.geom

import kotlin.math.sqrt

/** Vertices one piece of edge work takes. */
private const val EDGE_CHUNK = 4096

private const val TINY = 1e-12f

/**
 * The edges of a triangle mesh of [vertexCount] vertices and [triangles], found in batches whose pieces are independent of one
 * another, handed out by [next] until it gives null: each edge numbered among those of its lower vertex, in the order that vertex's
 * triangles first reach it, with the triangle that reaches it first on its left; each triangle's three edges in the order of its
 * corners; and each triangle's outward unit normal from [positions].
 */
class TriangleEdges(private val positions: FloatArray, private val vertexCount: Int, private val triangles: IntArray) {
    private val triangleCount = triangles.size / 3
    private var stage = 0

    /** Each vertex's triangle corners, as triangle * 3 + corner, in triangle order. */
    private val firstCorner = IntArray(vertexCount + 1)
    private val corners = IntArray(triangles.size)

    /** How many edges each vertex is the lower end of, then where its edges start. */
    private val firstEdge = IntArray(vertexCount + 1)

    internal var edgeA = IntArray(0)
    internal var edgeB = IntArray(0)
    internal var left = IntArray(0)
    internal var right = IntArray(0)
    internal val triangleEdges = IntArray(triangles.size)
    internal val faceNormals = FloatArray(triangles.size)

    /** The next batch of work, or null once every edge is found. */
    fun next(): List<() -> Unit>? {
        stage++
        return when (stage) {
            1 -> listOf({ incidence() }) + chunks(triangleCount) { from, to -> faces(from, to) }
            2 -> chunks(vertexCount) { from, to -> for (v in from until to) firstEdge[v + 1] = ownEdges(v, false) }
            3 -> {
                for (v in 0 until vertexCount) firstEdge[v + 1] += firstEdge[v]
                val edges = firstEdge[vertexCount]
                edgeA = IntArray(edges)
                edgeB = IntArray(edges)
                left = IntArray(edges)
                right = IntArray(edges) { -1 }
                chunks(vertexCount) { from, to -> for (v in from until to) ownEdges(v, true) }
            }
            else -> null
        }
    }

    /** Runs every batch here in turn. */
    fun all(): TriangleEdges = apply { while (true) (next() ?: break).forEach { it() } }

    private fun chunks(total: Int, work: (Int, Int) -> Unit): List<() -> Unit> =
        (0 until total step EDGE_CHUNK).map { from -> { work(from, minOf(total, from + EDGE_CHUNK)) } }

    private fun incidence() {
        for (v in triangles) firstCorner[v + 1]++
        for (v in 0 until vertexCount) firstCorner[v + 1] += firstCorner[v]
        val filled = IntArray(vertexCount)
        for (i in triangles.indices) {
            val v = triangles[i]
            corners[firstCorner[v] + filled[v]++] = i
        }
    }

    private fun faces(from: Int, to: Int) {
        for (t in from until to) {
            val a = triangles[t * 3] * 3
            val b = triangles[t * 3 + 1] * 3
            val c = triangles[t * 3 + 2] * 3
            val ux = positions[b] - positions[a]
            val uy = positions[b + 1] - positions[a + 1]
            val uz = positions[b + 2] - positions[a + 2]
            val vx = positions[c] - positions[a]
            val vy = positions[c + 1] - positions[a + 1]
            val vz = positions[c + 2] - positions[a + 2]
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx
            val length = sqrt(nx * nx + ny * ny + nz * nz)
            val inverse = if (length > TINY) 1f / length else 0f
            faceNormals[t * 3] = nx * inverse
            faceNormals[t * 3 + 1] = ny * inverse
            faceNormals[t * 3 + 2] = nz * inverse
        }
    }

    /**
     * The edges whose lower end is [v], each triangle edge (t, c) from corner c to the next: counted alone, and when [write] numbered from
     * [firstEdge] and written out. Each of v's triangles reaches two of its edges, the one leaving v and the one arriving.
     */
    private fun ownEdges(v: Int, write: Boolean): Int {
        var count = 0
        val base = firstEdge[v]
        for (k in firstCorner[v] until firstCorner[v + 1]) {
            val corner = corners[k]
            val t = corner / 3
            val c = corner % 3
            for (side in 0..1) {
                val edge = if (side == 0) c else (c + 2) % 3
                val p = triangles[t * 3 + edge]
                val q = triangles[t * 3 + (edge + 1) % 3]
                val other = if (p == v) q else p
                if (other <= v) continue
                var e = -1
                if (write) {
                    for (n in base until base + count) if (edgeB[n] == other) e = n
                } else {
                    for (j in firstCorner[v] until k + 1) if (seenBefore(v, j, k, edge, other)) e = 0
                }
                if (e < 0) {
                    e = base + count
                    count++
                    if (write) {
                        edgeA[e] = v
                        edgeB[e] = other
                        left[e] = t
                    }
                } else if (write && right[e] < 0 && left[e] != t) {
                    right[e] = t
                }
                if (write) triangleEdges[t * 3 + edge] = e
            }
        }
        return count
    }

    /** Whether corner [j] of [v]'s corners, at or before [k] and its [edge], already reached the edge from [v] to [other]. */
    private fun seenBefore(v: Int, j: Int, k: Int, edge: Int, other: Int): Boolean {
        val corner = corners[j]
        val t = corner / 3
        val c = corner % 3
        for (side in 0..1) {
            val e = if (side == 0) c else (c + 2) % 3
            if (j == k && e == edge) return false
            val p = triangles[t * 3 + e]
            val q = triangles[t * 3 + (e + 1) % 3]
            if ((if (p == v) q else p) == other) return true
        }
        return false
    }
}
