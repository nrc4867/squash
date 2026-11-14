package dev.chieppa.squash.geom.surface

/**
 * A grid cell's corners, edges and faces, and which of its edge crossings belong to one sheet of surface. Corner c lies at
 * (c and 1, c shr 1 and 1, c shr 2 and 1); edges 0 to 3 run along x, 4 to 7 along y and 8 to 11 along z.
 */
internal object CellTables {
    const val CORNERS = 8
    const val EDGES = 12
    const val FACES = 6

    /** The two corners of each edge, the lower first. */
    val EDGE_CORNERS: Array<IntArray> = arrayOf(
        intArrayOf(0, 1), intArrayOf(2, 3), intArrayOf(4, 5), intArrayOf(6, 7),
        intArrayOf(0, 2), intArrayOf(1, 3), intArrayOf(4, 6), intArrayOf(5, 7),
        intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7),
    )

    /** Each face's corners in order round it. */
    private val FACE_CORNERS: Array<IntArray> = arrayOf(
        intArrayOf(0, 2, 6, 4), intArrayOf(1, 3, 7, 5),
        intArrayOf(0, 1, 5, 4), intArrayOf(2, 3, 7, 6),
        intArrayOf(0, 1, 3, 2), intArrayOf(4, 5, 7, 6),
    )

    /** Each face's edges in order round it: edge i runs from its corner i to corner i + 1. */
    private val FACE_EDGES: Array<IntArray> = Array(FACES) { f ->
        IntArray(4) { i -> edgeBetween(FACE_CORNERS[f][i], FACE_CORNERS[f][(i + 1) % 4]) }
    }

    private fun edgeBetween(a: Int, b: Int): Int = EDGE_CORNERS.indexOfFirst { (it[0] == a && it[1] == b) || (it[0] == b && it[1] == a) }

    fun cornerX(c: Int) = c and 1
    fun cornerY(c: Int) = (c shr 1) and 1
    fun cornerZ(c: Int) = (c shr 2) and 1

    /** Which axis edge [e] runs along: 0 for x, 1 for y, 2 for z. */
    fun axisOf(e: Int) = e / 4

    /** The edge along [axis] whose lower corner has the other two coordinates [a] and [b], each 0 or 1, in x, y, z order. */
    fun edgeAlong(axis: Int, a: Int, b: Int): Int = axis * 4 + a + 2 * b

    /**
     * Writes into [out], for each edge of a cell whose corners hold [values], which sheet of surface crossing it the edge's crossing
     * belongs to, or -1 where the edge crosses none, and returns how many sheets there are. On each face the crossings pair up round it;
     * a face crossed four times, its corners alternating, joins its inside corners when the mean of its corners is inside and its
     * outside corners otherwise, so the two cells either side of a face always agree. A cell holding two sheets gives each its own vertex.
     */
    fun components(values: FloatArray, out: ByteArray, at: Int): Int {
        var crossed = 0
        for (e in 0 until EDGES) {
            val pair = EDGE_CORNERS[e]
            if ((values[pair[0]] < 0f) != (values[pair[1]] < 0f)) crossed = crossed or (1 shl e)
        }
        if (crossed == 0) {
            for (e in 0 until EDGES) out[at + e] = -1
            return 0
        }
        val parent = IntArray(EDGES) { it }
        fun root(e: Int): Int {
            var r = e
            while (parent[r] != r) r = parent[r]
            return r
        }
        fun join(a: Int, b: Int) {
            val ra = root(a)
            val rb = root(b)
            if (ra != rb) parent[ra] = rb
        }
        for (f in 0 until FACES) {
            val edges = FACE_EDGES[f]
            val corners = FACE_CORNERS[f]
            var count = 0
            for (e in edges) if (crossed and (1 shl e) != 0) count++
            when (count) {
                2 -> {
                    var first = -1
                    for (e in edges) if (crossed and (1 shl e) != 0) {
                        if (first < 0) first = e else join(first, e)
                    }
                }
                4 -> {
                    var mean = 0f
                    for (c in corners) mean += values[c]
                    val joinInside = mean < 0f
                    for (i in 0 until 4) {
                        val inside = values[corners[i]] < 0f
                        if (inside != joinInside) join(edges[(i + 3) % 4], edges[i])
                    }
                }
            }
        }
        val label = IntArray(EDGES) { -1 }
        var sheets = 0
        for (e in 0 until EDGES) {
            if (crossed and (1 shl e) == 0) {
                out[at + e] = -1
                continue
            }
            val r = root(e)
            if (label[r] < 0) label[r] = sheets++
            out[at + e] = label[r].toByte()
        }
        return sheets
    }
}
