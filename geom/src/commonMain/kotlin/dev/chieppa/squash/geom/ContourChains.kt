package dev.chieppa.squash.geom

/** Which connected piece of [edges] each point lies in. */
internal fun connectedPieces(edges: List<Pair<Int, Int>>): Map<Int, Int> {
    val parent = HashMap<Int, Int>()
    fun root(of: Int): Int {
        var at = of
        while (true) {
            val up = parent.getOrPut(at) { at }
            if (up == at) return at
            at = up
        }
    }
    for ((a, b) in edges) parent[root(a)] = root(b)
    return parent.keys.associateWith(::root)
}

internal class IndexedRun(val points: List<ContourPoint>, val piece: Int)

/** Each run along [edges], from an end where it has one, each edge taken once. */
internal fun walkedEdges(edges: List<Pair<Int, Int>>): List<List<Int>> {
    val links = HashMap<Int, MutableList<Int>>()
    for ((a, b) in edges) {
        links.getOrPut(a) { ArrayList(2) } += b
        links.getOrPut(b) { ArrayList(2) } += a
    }
    val seen = HashSet<Pair<Int, Int>>()
    fun key(a: Int, b: Int) = if (a < b) a to b else b to a
    val runs = ArrayList<List<Int>>()
    fun walk(start: Int) {
        while (links.getValue(start).any { key(start, it) !in seen }) {
            val run = arrayListOf(start)
            var at = start
            while (true) {
                val next = links.getValue(at).firstOrNull { key(at, it) !in seen } ?: break
                seen += key(at, next)
                run += next
                at = next
            }
            runs += run
        }
    }
    links.filterValues { it.size % 2 == 1 }.keys.forEach(::walk)
    links.keys.forEach(::walk)
    return runs
}

/** [runs] with those whose ends meet joined end to end, a chain whose last point meets its first being closed. */
internal fun joinedAtEnds(runs: List<IndexedRun>, tolerance: Float): List<ContourRun> {
    fun same(a: ContourPoint, b: ContourPoint) = (a.position - b.position).length <= tolerance
    val open = runs.filter { it.points.size >= 2 }.toMutableList()
    val out = ArrayList<ContourRun>()
    while (open.isNotEmpty()) {
        val start = open.removeAt(0)
        var chain = start.points
        while (!same(chain.first(), chain.last())) {
            val at = open.indices.firstOrNull { i ->
                val other = open[i].points
                same(chain.last(), other.first()) || same(chain.last(), other.last()) || same(chain.first(), other.last()) || same(chain.first(), other.first())
            } ?: break
            val other = open.removeAt(at).points
            chain = when {
                same(chain.last(), other.first()) -> chain + other.drop(1)
                same(chain.last(), other.last()) -> chain + other.dropLast(1).reversed()
                same(chain.first(), other.last()) -> other + chain.drop(1)
                else -> other.reversed() + chain.drop(1)
            }
        }
        out += ContourRun(chain, chain.size > 2 && same(chain.first(), chain.last()), start.piece)
    }
    return out
}
