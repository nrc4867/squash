package dev.chieppa.squash.character.mouth

import dev.chieppa.squash.geom.Vec2
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Whether [p] lies inside the closed loop [loop], by the even-odd rule. */
internal fun inside(p: Vec2, loop: List<Vec2>): Boolean {
    var crossings = false
    for (i in loop.indices) {
        val a = loop[i]
        val b = loop[(i + 1) % loop.size]
        if ((a.y > p.y) != (b.y > p.y) && p.x < a.x + (b.x - a.x) * (p.y - a.y) / (b.y - a.y)) crossings = !crossings
    }
    return crossings
}

/** The fewest places a run of a broken line keeps, so no stub too short to read as a line is inked. */
private const val MIN_RUN = 3

/** [line] broken where it passes inside any of [covers]: the runs of [MIN_RUN] places or more left outside them. */
internal fun brokenUnder(line: List<Vec2>, covers: List<List<Vec2>>): List<List<Vec2>> = brokenRuns(line, covers).map { line.subList(it.first, it.last + 1) }

/** The index runs of [line] left outside every one of [covers], each of [MIN_RUN] places or more. */
internal fun brokenRuns(line: List<Vec2>, covers: List<List<Vec2>>): List<IntRange> {
    if (covers.isEmpty()) return if (line.isEmpty()) emptyList() else listOf(line.indices)
    val runs = ArrayList<IntRange>()
    var start = 0
    for (i in line.indices) {
        if (covers.any { inside(line[i], it) }) {
            if (i - start >= MIN_RUN) runs += start until i
            start = i + 1
        }
    }
    if (line.size - start >= MIN_RUN) runs += start until line.size
    return runs
}

/** How many places a lip mark's curve is read at between two of its own. */
private const val CURVE_STEPS = 6

/** An open smooth curve through [places], as a mark's line runs through them, read at [CURVE_STEPS] places a gap. */
internal fun curveThrough(places: List<Vec2>): List<Vec2> {
    if (places.size < 3) return places
    val n = places.size
    fun at(i: Int) = places[i.coerceIn(0, n - 1)]
    return (0 until n - 1).flatMap { i ->
        val p0 = at(i - 1)
        val p1 = at(i)
        val p2 = at(i + 1)
        val p3 = at(i + 2)
        List(CURVE_STEPS) { step ->
            val t = step.toFloat() / CURVE_STEPS
            val tt = t * t
            (p1 * 2f + (p2 - p0) * t + (p0 * 2f - p1 * 5f + p2 * 4f - p3) * tt + (p1 * 3f - p0 - p2 * 3f + p3) * (tt * t)) * 0.5f
        }
    } + places.last()
}

/** The middle of [places]' extent across, and their top and bottom. */
internal class Extent(places: List<Vec2>) {
    val left = places.minOf { it.x }
    val right = places.maxOf { it.x }
    val top = places.minOf { it.y }
    val bottom = places.maxOf { it.y }
    val middle get() = (left + right) / 2f
    val height get() = bottom - top
}

/** A closed oval [width] across and [height] down about [center], in [steps] places. */
internal fun oval(center: Vec2, width: Float, height: Float, steps: Int): List<Vec2> = List(steps) { i ->
    val angle = 2f * PI.toFloat() * i / steps
    Vec2(center.x + width / 2f * cos(angle), center.y + height / 2f * sin(angle))
}
