package dev.chieppa.squash.screens.move

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.stretch.frame.OverlayBuilder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** How many steps the dashed axis takes along a part. */
private const val AXIS_STEPS = 32
private const val AXIS_DASH = 4f
private const val AXIS_WIDTH = 2f
private const val AXIS_ALPHA = 0.6f

/** How far outside the accent ring a joint about to be placed shows its thin ink ring, and how wide each draws. */
private const val OUTER_RING = 3f
private const val RING_STROKE = 3f
private const val THIN_STROKE = 1f

/** The hinge arc over a joint about to be placed: how far past the accent ring it runs, and the angles it spans, up being -90. */
private const val ARC_GAP = 9f
private const val ARC_FROM = -150.0
private const val ARC_TO = -30.0
private const val ARC_STEPS = 16
private const val ARROW = 6f
private const val CIRCLE_STEPS = 32

/** A joint's dot, this share of a socket's, as the board draws them smaller. */
private const val JOINT_SHARE = 0.75f

/** A landmark's dot, this share of a socket's. */
private const val LANDMARK_SHARE = 0.6f

/** The middle of [part] at [u], between its surface's top and underside, in body space. */
internal fun axisMiddle(part: PlacedPart, u: Float) = part.pointOn(u, 0f)?.let { top -> part.pointOn(u, 0.5f)?.let { (top + it) * 0.5f } }

/** [part]'s axis as a dashed line through [sight], as Add joint shows the part it adds to. */
fun OverlayBuilder.dashedAxis(part: PlacedPart, sight: SurfaceView, look: SheetLook) {
    val points = (0..AXIS_STEPS).mapNotNull { i -> axisMiddle(part, i.toFloat() / AXIS_STEPS)?.let(sight::toPixel) }
    if (points.size > 1) dashed(points, look.ring.copy(alpha = look.ring.alpha * AXIS_ALPHA), AXIS_WIDTH, AXIS_DASH, AXIS_DASH)
}

/** A joint's dot at [u] on [part], as the Rig stage draws them: [picked] larger and in the accent. */
fun OverlayBuilder.jointDot(part: PlacedPart, u: Float, sight: SurfaceView, look: SheetLook, picked: Boolean) {
    val at = axisMiddle(part, u)?.let(sight::toPixel) ?: return
    val radius = if (picked) look.pickedDotRadius else look.dotRadius * JOINT_SHARE
    dot(at, radius + look.dotEdge, look.ring)
    dot(at, radius, if (picked) look.active else look.dotFill)
}

/** A joint about to be placed at [u] on [part]: an accent ring in a thin ink one on the axis, with the hinge's arc over it. */
fun OverlayBuilder.jointMark(part: PlacedPart, u: Float, sight: SurfaceView, look: SheetLook) {
    val at = axisMiddle(part, u)?.let(sight::toPixel) ?: return
    val radius = look.pickedDotRadius
    ring(at, radius + OUTER_RING, look.ring, THIN_STROKE)
    ring(at, radius, look.active, RING_STROKE)
    val arc = List(ARC_STEPS + 1) { i ->
        val angle = (ARC_FROM + (ARC_TO - ARC_FROM) * i / ARC_STEPS) * PI / 180.0
        at + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * (radius + ARC_GAP)
    }
    arc.zipWithNext().forEach { (a, b) -> line(a, b, look.active, RING_STROKE - 0.5f) }
    val tip = arc.last()
    line(tip, tip + Offset(0f, -ARROW), look.active, RING_STROKE - 0.5f)
    line(tip, tip + Offset(-ARROW, 0f), look.active, RING_STROKE - 0.5f)
}

/** A landmark's small dot at [u] and [v] on [part]; [fill] in the accent for one about to be placed. */
fun OverlayBuilder.landmarkDot(part: PlacedPart, u: Float, v: Float, sight: SurfaceView, look: SheetLook, fill: Color) {
    val at = part.pointOn(u, v)?.let(sight::toPixel) ?: return
    val radius = look.dotRadius * LANDMARK_SHARE
    dot(at, radius + look.dotEdge, look.ring)
    dot(at, radius, fill)
}

private fun OverlayBuilder.ring(center: Offset, radius: Float, color: Color, width: Float) =
    List(CIRCLE_STEPS + 1) { i -> (2.0 * PI * i / CIRCLE_STEPS).let { center + Offset(cos(it).toFloat(), sin(it).toFloat()) * radius } }
        .zipWithNext().forEach { (a, b) -> line(a, b, color, width) }
