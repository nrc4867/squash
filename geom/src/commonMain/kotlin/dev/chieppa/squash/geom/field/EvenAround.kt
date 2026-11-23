package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.Vec2
import kotlin.math.hypot

/** Quarters a ring is spaced in: its points at v 0, 0.25, 0.5 and 0.75 stay where they were. */
private const val QUARTERS = 4

/**
 * [count] points round the closed outline through [dense], spaced evenly by length along it within each quarter: the outline's points
 * at the start of each quarter of [dense] (v 0, 0.25, 0.5 and 0.75) are kept, and the points between them lie at even steps of the
 * outline's length from one to the next. So v measures distance round a ring however thin it is, and still faces up and to the side
 * at its quarters. Where [count] or [dense]'s size is not a whole number of quarters, the whole outline is spaced from its first point.
 */
fun evenAround(dense: List<Vec2>, count: Int): List<Vec2> {
    val n = dense.size
    if (n == 0 || count <= 0) return emptyList()
    val quarters = if (n % QUARTERS == 0 && count % QUARTERS == 0) QUARTERS else 1
    val perDense = n / quarters
    val perOut = count / quarters
    val out = ArrayList<Vec2>(count)
    for (q in 0 until quarters) {
        val stretch = List(perDense + 1) { dense[(q * perDense + it) % n] }
        out += evenAlong(stretch, perOut)
    }
    return out
}

/** [count] points along the open polyline [line] at even steps of its length, its first point first, its last left out. */
private fun evenAlong(line: List<Vec2>, count: Int): List<Vec2> {
    val lengths = FloatArray(line.size)
    for (i in 1 until line.size) lengths[i] = lengths[i - 1] + hypot(line[i].x - line[i - 1].x, line[i].y - line[i - 1].y)
    val total = lengths.last()
    if (total <= 0f) return List(count) { line.first() }
    val out = ArrayList<Vec2>(count)
    var segment = 1
    for (k in 0 until count) {
        val target = total * k / count
        while (segment < line.lastIndex && lengths[segment] < target) segment++
        val from = lengths[segment - 1]
        val span = lengths[segment] - from
        val t = if (span > 0f) ((target - from) / span).coerceIn(0f, 1f) else 0f
        val a = line[segment - 1]
        val b = line[segment]
        out += Vec2(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }
    return out
}
