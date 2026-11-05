package dev.chieppa.squash.screens.surface

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.document.WidthStop
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.sheet.SurfaceView
import kotlin.math.abs

/** How a pen's pressure multiplies a line's weight: half pressure draws the line rule's weight. */
private const val PRESSURE_SCALE = 2f
private const val LEAST_PRESSED_SCALE = 0.15f

/** The most width stops a stroke keeps of a pen's pressure. */
private const val MOST_STOPS = 16

/** Past this share of the view running along up, the directions across the view are found from another axis. */
private const val SIDE_FROM_UP = 0.9f

/** How many steps find the point under a pixel, which perspective leaves only near the first. */
private const val FACING_STEPS = 3

/** How far off level two directions on screen must be to tell them apart. */
private const val LEAST_AREA = 1e-6f

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

/** The point showing at [pixel] on the plane through [root] that faces the viewer there, or null when it lies behind the camera. */
fun pointFacingViewer(root: Vec3, pixel: Offset, sight: SurfaceView): Vec3? {
    val toViewer = sight.toViewer(root)
    val other = if (abs(toViewer.y) < SIDE_FROM_UP) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
    val right = (other cross toViewer).normalized()
    val up = (toViewer cross right).normalized()
    val unit = 1f / sight.pixelsPerUnitAt(root)
    var point = root
    repeat(FACING_STEPS) {
        val at = sight.toPixel(point) ?: return null
        val alongRight = (sight.toPixel(point + right * unit) ?: return null) - at
        val alongUp = (sight.toPixel(point + up * unit) ?: return null) - at
        val area = alongRight.x * alongUp.y - alongRight.y * alongUp.x
        if (abs(area) < LEAST_AREA) return null
        val left = pixel - at
        val x = (left.x * alongUp.y - left.y * alongUp.x) / area
        val y = (alongRight.x * left.y - alongRight.y * left.x) / area
        point += right * (x * unit) + up * (y * unit)
    }
    return point
}
