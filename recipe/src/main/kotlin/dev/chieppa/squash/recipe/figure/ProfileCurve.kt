package dev.chieppa.squash.recipe.figure

import kotlin.math.abs

/** How far, in body units, a profile's curve may lie off the radii it is drawn through before another place is added. */
private const val CURVE_TOLERANCE = 0.0004

/** The most places a profile's curve takes. */
private const val MOST_PLACES = 28

/** Steps each span of a profile's curve is cut into to read it. */
private const val SPAN_STEPS = 12

/** A share of the span between a place's neighbours that its handles reach, as the recipe's pen curves draw them. */
private const val HANDLE_SHARE = 6.0

/** Radii read densely along a part: x along it, r from its bone, sorted by x. */
typealias Radii = List<P2>

/** The smooth curve through [places] as the recipe's pen draws it, as a dense polyline. */
fun penCurve(places: List<P2>): List<P2> {
    if (places.size < 2) return places
    val n = places.size
    val out = mutableListOf(places[0])
    for (i in 0 until n - 1) {
        val a = places[i]
        val b = places[i + 1]
        val before = places[(i - 1).coerceAtLeast(0)]
        val after = places[(i + 2).coerceAtMost(n - 1)]
        val h1 = a + (b - before) * (1 / HANDLE_SHARE)
        val h2 = b - (after - a) * (1 / HANDLE_SHARE)
        for (s in 1..SPAN_STEPS) {
            val t = s.toDouble() / SPAN_STEPS
            val u = 1 - t
            out += a * (u * u * u) + h1 * (3 * u * u * t) + h2 * (3 * u * t * t) + b * (t * t * t)
        }
    }
    return out
}

/** A polyline running along x read at [x], held level past its ends. */
fun readAt(line: List<P2>, x: Double): Double {
    if (x <= line.first().x) return line.first().y
    for (i in 1 until line.size) {
        if (x <= line[i].x) {
            val a = line[i - 1]
            val b = line[i]
            return if (b.x > a.x) a.y + (b.y - a.y) * (x - a.x) / (b.x - a.x) else b.y
        }
    }
    return line.last().y
}

/**
 * The fewest of [radii] a smooth curve through them needs to stay within a tolerance of them all: both ends, then the worst-followed
 * place added until none lies further off, so the drawn curve is the built one.
 */
fun fewestPlaces(radii: Radii): List<P2> {
    if (radii.size <= 2) return radii
    val chosen = sortedSetOf(0, radii.lastIndex)
    while (chosen.size < MOST_PLACES) {
        val line = penCurve(chosen.map { radii[it] })
        var worst = -1
        var worstError = CURVE_TOLERANCE
        radii.forEachIndexed { i, p ->
            if (i in chosen) return@forEachIndexed
            val error = abs(readAt(line, p.x) - p.y)
            if (error > worstError) {
                worst = i
                worstError = error
            }
        }
        if (worst < 0) break
        chosen += worst
    }
    return chosen.map { radii[it] }
}
