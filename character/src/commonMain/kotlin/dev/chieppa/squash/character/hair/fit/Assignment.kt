package dev.chieppa.squash.character.hair.fit

/**
 * The pairing of rows to columns of [cost] (rows by columns) that costs least in all, each row with at most one column and each column
 * with at most one row, as many pairs as the shorter side holds: for each row, its column, or -1. The Hungarian method, by potentials.
 */
fun cheapestPairing(cost: Array<DoubleArray>): IntArray {
    val rows = cost.size
    if (rows == 0) return IntArray(0)
    val columns = cost[0].size
    if (columns == 0) return IntArray(rows) { -1 }
    if (rows > columns) {
        val turned = Array(columns) { c -> DoubleArray(rows) { r -> cost[r][c] } }
        val byColumn = cheapestPairing(turned)
        val out = IntArray(rows) { -1 }
        byColumn.forEachIndexed { c, r -> if (r >= 0) out[r] = c }
        return out
    }
    val u = DoubleArray(rows + 1)
    val v = DoubleArray(columns + 1)
    val owner = IntArray(columns + 1)
    val way = IntArray(columns + 1)
    for (i in 1..rows) {
        owner[0] = i
        var j0 = 0
        val least = DoubleArray(columns + 1) { Double.POSITIVE_INFINITY }
        val used = BooleanArray(columns + 1)
        do {
            used[j0] = true
            val i0 = owner[j0]
            var delta = Double.POSITIVE_INFINITY
            var j1 = 0
            for (j in 1..columns) {
                if (used[j]) continue
                val here = cost[i0 - 1][j - 1] - u[i0] - v[j]
                if (here < least[j]) {
                    least[j] = here
                    way[j] = j0
                }
                if (least[j] < delta) {
                    delta = least[j]
                    j1 = j
                }
            }
            for (j in 0..columns) {
                if (used[j]) {
                    u[owner[j]] += delta
                    v[j] -= delta
                } else {
                    least[j] -= delta
                }
            }
            j0 = j1
        } while (owner[j0] != 0)
        do {
            val j1 = way[j0]
            owner[j0] = owner[j1]
            j0 = j1
        } while (j0 != 0)
    }
    val out = IntArray(rows) { -1 }
    for (j in 1..columns) if (owner[j] != 0) out[owner[j] - 1] = j - 1
    return out
}
