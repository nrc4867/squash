package dev.chieppa.squash.geom

import kotlin.math.cos
import kotlin.math.sin

/** A pixel, in plane units, at 200 pixels to the unit. */
internal const val TEST_PIXEL = 0.005f

/** The fit's tolerance and the simplifier's, and the line the fit rounds a corner by. */
internal const val TEST_MATCH_PIXELS = 3f

/** A stroke along an arc around ([cx], [cy]). */
internal fun arc(cx: Float, cy: Float, radius: Float, sweep: Double, from: Double = 0.0, samples: Int = 300) = List(samples) {
    val angle = from + sweep * it / (samples - 1)
    Vec2(cx + radius * cos(angle).toFloat(), cy + radius * sin(angle).toFloat())
}

/** A stroke straight through [points], a pixel between samples. */
internal fun through(vararg points: Pair<Float, Float>) = resample(points.map { Vec2(it.first, it.second) }, TEST_PIXEL)

internal fun distanceToLine(p: Vec2, line: List<Vec2>) = (1 until line.size).minOf { distanceToSegment(p, line[it - 1], line[it]) }
