package dev.chieppa.squash.rig

import kotlin.math.roundToLong

/** How finely vertices are matched by place, in body units: patches of one part meet at vertices this close. */
private const val WELD = 1e-5f

/** Which vertices of a part's mesh touch: across a triangle's edges, and where two patches' vertices lie at one place. */
internal class MeshPieces(part: RigPart) {
    private val neighbours: Array<MutableSet<Int>> = Array(part.vertexCount) { mutableSetOf() }

    init {
        val indices = part.indices
        for (t in 0 until indices.size / 3) {
            val a = indices[t * 3]
            val b = indices[t * 3 + 1]
            val c = indices[t * 3 + 2]
            join(a, b)
            join(b, c)
            join(c, a)
        }
        val byPlace = HashMap<Triple<Long, Long, Long>, Int>()
        for (v in 0 until part.vertexCount) {
            val key = Triple(cell(part.rest[v * 3]), cell(part.rest[v * 3 + 1]), cell(part.rest[v * 3 + 2]))
            val first = byPlace.putIfAbsent(key, v)
            if (first != null) join(first, v)
        }
    }

    private fun cell(x: Float) = (x / WELD).roundToLong()

    private fun join(a: Int, b: Int) {
        if (a == b) return
        neighbours[a] += b
        neighbours[b] += a
    }

    fun neighboursOf(vertex: Int): Set<Int> = neighbours[vertex]

    /** Whether a vertex of [piece] touches one [other] marks. */
    fun borders(piece: List<Int>, other: BooleanArray): Boolean = piece.any { v -> neighbours[v].any { other[it] } }

    /** The pieces [inside] marks: each a set of vertices joined through others it marks. */
    fun pieces(inside: BooleanArray): List<List<Int>> {
        val seen = BooleanArray(inside.size)
        val found = ArrayList<List<Int>>()
        for (start in inside.indices) {
            if (!inside[start] || seen[start]) continue
            val piece = ArrayList<Int>()
            val queue = ArrayDeque(listOf(start))
            seen[start] = true
            while (queue.isNotEmpty()) {
                val v = queue.removeFirst()
                piece += v
                for (n in neighbours[v]) if (inside[n] && !seen[n]) {
                    seen[n] = true
                    queue += n
                }
            }
            found += piece
        }
        return found
    }
}
