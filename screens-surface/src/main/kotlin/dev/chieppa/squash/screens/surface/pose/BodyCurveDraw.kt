package dev.chieppa.squash.screens.surface.pose

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.document.BodyStroke
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.pose.CurveSpot
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.frame.OverlayBuilder

/** How wide the body curve draws against its halo, as multiples of a handle's line. */
private const val CURVE_WIDTH = 2.5f
private const val HALO_WIDTH = 5f

/** How large a curve handle is, in pixels at density 1. */
private const val CURVE_HANDLE = 6f

/** How many straight steps the curve is first measured in, and how far apart its drawn points stand, as a share of its line's width. */
private const val MEASURE_STEPS = 64
private const val STEP_OF_WIDTH = 0.4f
private const val MAX_STEPS = 4000

/** The line [points] as the body curve draws, in pixels: the ring's colour over a halo of the panel's float. */
fun OverlayBuilder.curveLine(points: List<Offset>, look: PoseHandleLook) {
    if (points.size < 2) return
    val halo = look.width * HALO_WIDTH
    val line = look.width * CURVE_WIDTH
    points.zipWithNext().forEach { (a, b) -> line(a, b, look.fill, halo) }
    points.forEach { dot(it, halo / 2f, look.fill) }
    points.zipWithNext().forEach { (a, b) -> line(a, b, look.ring, line) }
    points.forEach { dot(it, line / 2f, look.ring) }
}

/** [stroke] drawn through [sight] on its view's plane through [middle], with a handle at each end and the middle, [lit] in the accent. */
fun OverlayBuilder.bodyStroke(stroke: BodyStroke, sight: SurfaceView, middle: Vec3, look: PoseHandleLook, lit: CurveSpot?) {
    val path = stroke.arcPath()
    fun shown(steps: Int) = (0..steps).mapNotNull { sight.toPixel(strokeWorld(path.pointAt(path.length * it / steps), stroke.view, middle)) }
    val across = shown(MEASURE_STEPS).zipWithNext().sumOf { (a, b) -> (b - a).getDistance().toDouble() }.toFloat()
    curveLine(shown((across / (look.width * CURVE_WIDTH * STEP_OF_WIDTH)).toInt().coerceIn(MEASURE_STEPS, MAX_STEPS)), look)
    stroke.spots().forEach { (spot, at) ->
        val pixel = sight.toPixel(strokeWorld(at, stroke.view, middle)) ?: return@forEach
        val radius = CURVE_HANDLE * look.density
        dot(pixel, radius, if (spot == lit) look.edge else look.ring)
        dot(pixel, radius - look.thin, if (spot == lit) look.accent else look.fill)
    }
}
