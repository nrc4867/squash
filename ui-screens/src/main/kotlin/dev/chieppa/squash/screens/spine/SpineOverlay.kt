package dev.chieppa.squash.screens.spine

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.geom.ReadSpan
import dev.chieppa.squash.geom.StrokeEnds
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.screens.draw.StrokeGuide
import dev.chieppa.squash.screens.draw.StrokeLook
import dev.chieppa.squash.screens.draw.trailing
import dev.chieppa.squash.screens.draw.rememberStrokeLook
import dev.chieppa.squash.screens.edit.CurveLook
import dev.chieppa.squash.screens.edit.rememberCurveLook
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.polyline
import dev.chieppa.stretch.frame.OverlayBuilder
import dev.chieppa.stretch.theme.Stretch

private const val SHADE_ALPHA = 0.24f

/** How far the socket plane's mark reaches past the widest of the part, as a multiple of it. */
private const val PLANE_MARK_OF_HALF_WIDTH = 1.2f

/** The colors and pixel sizes of a spine and of what it will write. */
data class SpineLook(
    val stroke: StrokeLook,
    val curve: CurveLook,
    val shade: Color,
    val handleLine: Float,
    val handleHalf: Float,
    val handleEdge: Float,
    val dotRadius: Float,
    val dotRing: Float,
    val planeWidth: Float,
    val planeDash: Float,
    val planeGap: Float,
)

/** The spine's drawing in the current theme and pixel density. */
@Composable
internal fun rememberSpineLook(): SpineLook {
    val density = LocalDensity.current
    fun px(dp: Int) = with(density) { dp.dp.toPx() }
    return SpineLook(
        rememberStrokeLook(),
        rememberCurveLook(),
        Stretch.colors.affected.copy(alpha = SHADE_ALPHA),
        handleLine = px(3),
        handleHalf = px(9),
        handleEdge = px(3),
        dotRadius = px(9),
        dotRing = px(3),
        planeWidth = px(2),
        planeDash = px(7),
        planeGap = px(7),
    )
}

private fun Vec2.inPlane() = CurvePoint(x, y)

/** Each plane the build reads, as one strip across the part, so the shading is what the build will fill and strips never overlap. */
private fun OverlayBuilder.shading(spans: List<ReadSpan>, camera: LockedCamera, look: SpineLook) {
    val width = SHADE_STRIP_PIXELS
    for (span in spans) {
        line(camera.toPixel(CurvePoint(span.at, span.low)), camera.toPixel(CurvePoint(span.at, span.high)), look.shade, width)
    }
}

/** What release writes in Side: the part shaded, the outline dashed where the build reads it and dropped where it would not, the spine on top, with [string] thin between the trailing line and the pointer. */
internal fun OverlayBuilder.sidePreview(preview: SpinePreview, camera: LockedCamera, look: SpineLook, plane: StrokeEnds.CutAtPlane?, string: Pair<Vec2, Vec2>?) {
    val stroke = look.stroke
    shading(preview.sideSpans, camera, look)
    val line = preview.shape.line.points
    if (plane != null) {
        val reach = preview.shape.halfWidths.max() * PLANE_MARK_OF_HALF_WIDTH
        val mark = listOf(CurvePoint(plane.plane, line.first().y - reach), CurvePoint(plane.plane, line.first().y + reach))
        dashed(mark.map(camera::toPixel), look.curve.ring, look.planeWidth, look.planeDash, look.planeGap)
    }
    for (run in preview.unread) dashed(run.map { camera.toPixel(it.inPlane()) }, stroke.dropped, stroke.droppedWidth, stroke.droppedDash, stroke.droppedGap)
    for (run in preview.read) dashed(run.map { camera.toPixel(it.inPlane()) }, stroke.connector, stroke.connectorWidth, stroke.connectorDash, stroke.connectorGap)
    val guide = StrokeGuide(listOf(line), emptyList(), emptyList(), line.first(), false).let {
        if (string != null) it.trailing(string.first, string.second, camera.planePixel) else it
    }
    guide.drawn.forEach { run -> polyline(run.map { it.inPlane() }, camera, stroke.ink, stroke.width) }
    polyline(guide.string.map { it.inPlane() }, camera, stroke.string, stroke.stringWidth)
    val start = camera.toPixel(line.first().inPlane())
    dot(start, stroke.startRadius + stroke.startRing, stroke.ink)
    dot(start, stroke.startRadius, stroke.startFill)
}

/** What release writes in Top: the part shaded and the outline dashed. */
internal fun OverlayBuilder.topPreview(preview: SpinePreview, camera: LockedCamera, look: SpineLook) {
    val stroke = look.stroke
    shading(preview.topSpans, camera, look)
    dashed(preview.shape.top.map { camera.toPixel(it.inPlane()) }, stroke.connector, stroke.connectorWidth, stroke.connectorDash, stroke.connectorGap)
}

/** The width across the spine at the handle: [across] left then right, the square on the side being dragged, the dot opposite. */
internal fun OverlayBuilder.widthHandle(across: Pair<Vec2, Vec2>, left: Boolean, camera: LockedCamera, look: SpineLook) {
    val leftEnd = camera.toPixel(across.first.inPlane())
    val rightEnd = camera.toPixel(across.second.inPlane())
    val held = if (left) leftEnd else rightEnd
    val other = if (left) rightEnd else leftEnd
    square(held, look)
    line(other, held, look.curve.ring, look.handleLine)
    square(held, look, edgeOnly = true)
    dot(other, look.dotRadius + look.dotRing, look.stroke.ink)
    dot(other, look.dotRadius, look.stroke.tip)
}

private fun OverlayBuilder.square(center: Offset, look: SpineLook, edgeOnly: Boolean = false) {
    val half = look.handleHalf
    val box = Rect(center.x - half, center.y - half, center.x + half, center.y + half)
    if (edgeOnly) outline(box, look.curve.ring, look.handleEdge) else rect(box, look.curve.fill)
}
