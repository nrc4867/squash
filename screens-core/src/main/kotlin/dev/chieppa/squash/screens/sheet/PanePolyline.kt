package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.stretch.frame.OverlayBuilder

/** Below this cosine of the turn between two lines, the gap on the outside of the joint shows. */
private const val JOINT_COSINE = 0.995f

/** Lines through [points], points in [camera]'s plane, with a round joint wherever they turn enough to show a gap. */
fun OverlayBuilder.polyline(points: List<CurvePoint>, camera: LockedCamera, color: Color, width: Float) {
    var before: Offset? = null
    var from: Offset? = null
    for (point in points) {
        val to = camera.toPixel(point)
        val start = from
        if (start != null && to != start) {
            line(start, to, color, width)
            val previous = before
            if (previous != null && turnsVisibly(previous, start, to)) dot(start, width / 2f, color)
            before = start
            from = to
        } else if (start == null) {
            from = to
        }
    }
}

private fun turnsVisibly(a: Offset, b: Offset, c: Offset): Boolean {
    val inward = b - a
    val outward = c - b
    val dot = inward.x * outward.x + inward.y * outward.y
    return dot < JOINT_COSINE * inward.getDistance() * outward.getDistance()
}
