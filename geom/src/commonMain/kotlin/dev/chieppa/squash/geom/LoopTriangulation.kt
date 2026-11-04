package dev.chieppa.squash.geom

import kotlin.math.abs

private const val MIN_DOUBLE_AREA = 1e-18

/** Twice the signed area of a loop of the points [xs] and [ys]: positive when it runs counter-clockwise. */
internal fun loopArea(loop: List<Int>, xs: List<Double>, ys: List<Double>): Double {
    var sum = 0.0
    for (i in loop.indices) {
        val a = loop[i]
        val b = loop[(i + 1) % loop.size]
        sum += xs[a] * ys[b] - xs[b] * ys[a]
    }
    return sum
}

/**
 * Triangles covering [loops], counter-clockwise loops with clockwise holes inside them, as triples of indices into [xs] and
 * [ys], counter-clockwise. No point is added, so a point on a straight stretch stays a corner of the triangles next to it, and
 * a neighbour sharing that stretch meets them vertex to vertex.
 */
internal fun triangulated(loops: List<List<Int>>, xs: List<Double>, ys: List<Double>): List<Int> {
    val outers = loops.filter { loopArea(it, xs, ys) > 0.0 }.toMutableList()
    val holes = loops.filter { loopArea(it, xs, ys) < 0.0 }.sortedByDescending { hole -> hole.maxOf { xs[it] } }
    for (hole in holes) {
        val inside = outers.indices.filter { contains(outers[it], xs[hole[0]], ys[hole[0]], xs, ys) }
            .minByOrNull { abs(loopArea(outers[it], xs, ys)) } ?: continue
        outers[inside] = bridged(outers[inside], hole, xs, ys)
    }
    return outers.flatMap { clippedEars(it, xs, ys) }
}

internal fun contains(loop: List<Int>, x: Double, y: Double, xs: List<Double>, ys: List<Double>): Boolean {
    var inside = false
    for (i in loop.indices) {
        val a = loop[i]
        val b = loop[(i + 1) % loop.size]
        if ((ys[a] > y) != (ys[b] > y) && x < xs[a] + (y - ys[a]) / (ys[b] - ys[a]) * (xs[b] - xs[a])) inside = !inside
    }
    return inside
}

/** [outer] with [hole] joined in by a pair of edges from the hole's rightmost point to the nearest outer point it sees. */
private fun bridged(outer: List<Int>, hole: List<Int>, xs: List<Double>, ys: List<Double>): List<Int> {
    val start = hole.indices.maxBy { xs[hole[it]] }
    val from = hole[start]
    val candidates = outer.indices.sortedBy { i ->
        val dx = xs[outer[i]] - xs[from]
        val dy = ys[outer[i]] - ys[from]
        dx * dx + dy * dy
    }
    val at = candidates.firstOrNull { sees(from, outer[it], outer, hole, xs, ys) } ?: candidates.first()
    val around = hole.indices.map { hole[(start + it) % hole.size] }
    return outer.subList(0, at + 1) + around + from + outer.subList(at, outer.size)
}

private fun sees(from: Int, to: Int, outer: List<Int>, hole: List<Int>, xs: List<Double>, ys: List<Double>): Boolean =
    !crossesLoop(from, to, outer, xs, ys) && !crossesLoop(from, to, hole, xs, ys)

private fun crossesLoop(from: Int, to: Int, loop: List<Int>, xs: List<Double>, ys: List<Double>): Boolean {
    for (i in loop.indices) {
        val a = loop[i]
        val b = loop[(i + 1) % loop.size]
        if (a == from || a == to || b == from || b == to) continue
        if (side(from, to, a, xs, ys) * side(from, to, b, xs, ys) < 0.0 && side(a, b, from, xs, ys) * side(a, b, to, xs, ys) < 0.0) return true
    }
    return false
}

/** Twice the signed area of the triangle [a], [b], [c]. */
private fun side(a: Int, b: Int, c: Int, xs: List<Double>, ys: List<Double>): Double =
    (xs[b] - xs[a]) * (ys[c] - ys[a]) - (ys[b] - ys[a]) * (xs[c] - xs[a])

/** Ear clipping that keeps every point: a point on a straight stretch is never an ear's tip, only ever another ear's corner. */
private fun clippedEars(loop: List<Int>, xs: List<Double>, ys: List<Double>): List<Int> {
    val left = loop.toMutableList()
    val out = ArrayList<Int>((loop.size - 2) * 3)
    while (left.size > 3) {
        val ear = left.indices.firstOrNull { isEar(left, it, xs, ys) } ?: flattestPoint(left, xs, ys)
        val previous = left[(ear + left.size - 1) % left.size]
        val next = left[(ear + 1) % left.size]
        if (side(previous, left[ear], next, xs, ys) > MIN_DOUBLE_AREA) {
            out += previous
            out += left[ear]
            out += next
        }
        left.removeAt(ear)
    }
    if (left.size == 3 && side(left[0], left[1], left[2], xs, ys) > MIN_DOUBLE_AREA) out += left
    return out
}

private fun isEar(left: List<Int>, i: Int, xs: List<Double>, ys: List<Double>): Boolean {
    val previous = left[(i + left.size - 1) % left.size]
    val tip = left[i]
    val next = left[(i + 1) % left.size]
    if (side(previous, tip, next, xs, ys) <= MIN_DOUBLE_AREA) return false
    for (point in left) {
        if (point == previous || point == tip || point == next) continue
        val onLeftOfAll = side(previous, tip, point, xs, ys) >= 0.0 && side(tip, next, point, xs, ys) >= 0.0 && side(next, previous, point, xs, ys) >= 0.0
        if (onLeftOfAll) return false
    }
    return true
}

/** Where no ear is left, as rounding can leave a sliver, the point whose removal changes the area least. */
private fun flattestPoint(left: List<Int>, xs: List<Double>, ys: List<Double>): Int = left.indices.minBy { i ->
    abs(side(left[(i + left.size - 1) % left.size], left[i], left[(i + 1) % left.size], xs, ys))
}
