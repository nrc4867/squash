package dev.chieppa.squash.screens.draw

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.geom.OversketchPreview
import dev.chieppa.squash.geom.StrokePreview
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.polyline
import dev.chieppa.stretch.frame.OverlayBuilder
import dev.chieppa.stretch.theme.Stretch

/** The colors and pixel sizes of a stroke in progress and of what release would do with it. */
data class StrokeLook(
    val ink: Color,
    val width: Float,
    val tip: Color,
    val tipRadius: Float,
    val tipRing: Float,
    val connector: Color,
    val connectorWidth: Float,
    val connectorDash: Float,
    val connectorGap: Float,
    val dropped: Color,
    val droppedWidth: Float,
    val droppedDash: Float,
    val droppedGap: Float,
    val startFill: Color,
    val startRadius: Float,
    val startLitRadius: Float,
    val startRing: Float,
)

/** The live stroke's drawing in the current theme and pixel density. */
@Composable
internal fun rememberStrokeLook(): StrokeLook {
    val c = Stretch.colors
    val density = LocalDensity.current
    fun px(dp: Int) = with(density) { dp.dp.toPx() }
    return StrokeLook(
        ink = c.inkStrong,
        width = px(7),
        tip = c.accent,
        tipRadius = px(8),
        tipRing = px(3),
        connector = c.inkStrong,
        connectorWidth = px(3),
        connectorDash = px(10),
        connectorGap = px(8),
        dropped = c.inkSubtle,
        droppedWidth = px(3),
        droppedDash = px(4),
        droppedGap = px(6),
        startFill = c.surfaceFloat,
        startRadius = px(7),
        startLitRadius = px(13),
        startRing = px(3),
    )
}

/** What a live stroke shows: what stays, what release adds, what it discards, and where it began, [lit] once it can be let go. */
internal class StrokeGuide(
    val drawn: List<List<Vec2>>,
    val connectors: List<List<Vec2>>,
    val dropped: List<List<Vec2>>,
    val start: Vec2,
    val lit: Boolean,
)

internal fun StrokePreview.guide() = StrokeGuide(drawn, connectors, dropped, start, closes)

/** The guide over a drawn curve; a [preview] of null is a stroke too far from it to do anything, so all of [stroke] goes. */
internal fun oversketchGuide(preview: OversketchPreview?, stroke: List<Vec2>): StrokeGuide {
    preview?.whole?.let { return it.guide() }
    if (preview == null) return StrokeGuide(emptyList(), emptyList(), listOf(stroke), stroke.first(), false)
    return StrokeGuide(listOf(preview.drawn), preview.connectors, preview.dropped, stroke.first(), preview.joins)
}

private fun List<Vec2>.inPlane() = map { CurvePoint(it.x, it.y) }

/** [guide] over a pane, with the stroke's tip at [tip]. */
internal fun OverlayBuilder.strokeGuide(guide: StrokeGuide, tip: CurvePoint, camera: LockedCamera, look: StrokeLook) {
    for (line in guide.dropped) dashed(line.inPlane().map(camera::toPixel), look.dropped, look.droppedWidth, look.droppedDash, look.droppedGap)
    for (line in guide.connectors) dashed(line.inPlane().map(camera::toPixel), look.connector, look.connectorWidth, look.connectorDash, look.connectorGap)
    for (line in guide.drawn) polyline(line.inPlane(), camera, look.ink, look.width)
    startMarker(camera.toPixel(CurvePoint(guide.start.x, guide.start.y)), guide.lit, look)
    val at = camera.toPixel(tip)
    dot(at, look.tipRadius + look.tipRing, look.ink)
    dot(at, look.tipRadius, look.tip)
}

private fun OverlayBuilder.startMarker(center: Offset, lit: Boolean, look: StrokeLook) {
    val radius = if (lit) look.startLitRadius else look.startRadius
    dot(center, radius + look.startRing, look.ink)
    dot(center, radius, if (lit) look.tip else look.startFill)
}
