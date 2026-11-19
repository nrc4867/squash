package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.WidthStop

/** How a pen's pressure multiplies a line's weight: half pressure draws the line rule's weight. */
private const val PRESSURE_SCALE = 2f
private const val LEAST_PRESSED_SCALE = 0.15f

/** The most width stops a stroke keeps of a pen's pressure. */
private const val MOST_STOPS = 16

/** The width a stroke holds from a pen's pressure at each of its points, spread evenly along it. A point with none, as from a mouse, leaves the line rule's weight alone. */
fun pressureWidth(pressures: List<Float?>): MarkWidth {
    val pressed = pressures.filterNotNull()
    if (pressed.size < 2 || pressed.size != pressures.size) return MarkWidth()
    val stops = minOf(pressed.size, MOST_STOPS)
    return MarkWidth(
        List(stops) { stop ->
            val at = stop.toFloat() / (stops - 1)
            WidthStop(at, (pressed[(at * pressed.lastIndex).toInt()] * PRESSURE_SCALE).coerceAtLeast(LEAST_PRESSED_SCALE))
        },
    )
}
