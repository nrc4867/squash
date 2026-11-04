package dev.chieppa.squash.screens.draw

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.geom.OutlineReading
import dev.chieppa.squash.geom.OversketchPreview
import dev.chieppa.squash.geom.StrokePreview
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneLine
import dev.chieppa.squash.screens.sheet.polyline
import dev.chieppa.stretch.frame.OverlayBuilder
import dev.chieppa.stretch.theme.Stretch

/** How strongly what the build reads shades, as the board draws it. */
private const val READ_SHADE_ALPHA = 0.24f

/** The width of each strip the shading is laid down in, in screen pixels: they meet edge to edge. */
private const val READ_STRIP_PIXELS = 2f

/** How near the end of a drawn stretch the pointer is when the stretch runs on to it, in screen pixels: a mirrored edge is smoothed across its end. */
private const val TIP_ON_LINE_PIXELS = 3f

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
    val read: Color,
    val caught: Color,
    val caughtWidth: Float,
    val string: Color,
    val stringWidth: Float,
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
        read = c.affected.copy(alpha = READ_SHADE_ALPHA),
        caught = c.accent,
        caughtWidth = px(2),
        string = c.inkMuted,
        stringWidth = px(2),
    )
}

/**
 * What a live stroke shows: what stays, what release adds, what it discards, and where it began, [lit] once it can be let
 * go. [string] runs from where the trailing line has got to on to the pointer, where release finishes it.
 */
internal class StrokeGuide(
    val drawn: List<List<Vec2>>,
    val connectors: List<List<Vec2>>,
    val dropped: List<List<Vec2>>,
    val start: Vec2,
    val lit: Boolean,
    val string: List<Vec2> = emptyList(),
)

internal fun StrokePreview.guide() = StrokeGuide(drawn, connectors, dropped, start, closes)

/** The guide over a drawn curve; a [preview] of null is a stroke too far from it to do anything, so all of [stroke] goes. */
internal fun oversketchGuide(preview: OversketchPreview?, stroke: List<Vec2>): StrokeGuide {
    preview?.whole?.let { return it.guide() }
    if (preview == null) return StrokeGuide(emptyList(), emptyList(), listOf(stroke), stroke.first(), false)
    val image = preview.mirrored?.let { listOf(it.drawn) + it.connectors }.orEmpty()
    return StrokeGuide(listOf(preview.drawn), preview.connectors + image, preview.dropped, stroke.first(), preview.joins)
}

/** This guide with the stretch from [lineEnd], where the trailing line has got to, on to the pointer at [tip] shown as the string. */
internal fun StrokeGuide.trailing(lineEnd: Vec2, tip: Vec2, pixel: Float): StrokeGuide {
    if (lineEnd == tip) return this
    val reach = TIP_ON_LINE_PIXELS * pixel
    fun distance(a: Vec2, b: Vec2) = kotlin.math.hypot(a.x - b.x, a.y - b.y)
    val trimmed = drawn.map { line ->
        when {
            line.size >= 2 && distance(line.last(), tip) <= reach -> line.subList(0, line.indexOfLastNearest(lineEnd) + 1)
            line.size >= 2 && distance(line.first(), tip) <= reach -> line.subList(line.indexOfLastNearest(lineEnd), line.size)
            else -> line
        }
    }
    return StrokeGuide(trimmed, connectors, dropped, start, lit, listOf(lineEnd, tip))
}

private fun List<Vec2>.indexOfLastNearest(to: Vec2): Int =
    indices.minBy { kotlin.math.hypot(this[it].x - to.x, this[it].y - to.y) }

/** This guide with what [reading] does not take from what it draws or adds shown as discarded. */
internal fun StrokeGuide.readBy(reading: OutlineReading?): StrokeGuide {
    if (reading == null) return this
    val drawnParts = drawn.map(reading::split)
    val connectorParts = connectors.map(reading::split)
    return StrokeGuide(
        drawnParts.flatMap { it.read },
        connectorParts.flatMap { it.read },
        dropped + drawnParts.flatMap { it.unread } + connectorParts.flatMap { it.unread },
        start,
        lit,
        string,
    )
}

/** What the build fills, shaded under the stroke. */
internal fun OverlayBuilder.readShade(reading: OutlineReading, camera: LockedCamera, look: StrokeLook) {
    val pixel = camera.planePixel
    for (span in reading.spans(READ_STRIP_PIXELS * pixel)) {
        line(camera.toPixel(CurvePoint(span.x, span.low)), camera.toPixel(CurvePoint(span.x, span.high)), look.read, span.width / pixel)
    }
}

/** The guides a stroke is holding to. */
internal fun OverlayBuilder.caughtGuides(lines: List<PaneLine>, look: StrokeLook) {
    for (caught in lines) line(caught.start, caught.end, look.caught, look.caughtWidth)
}

private fun List<Vec2>.inPlane() = map { CurvePoint(it.x, it.y) }

/** [guide] over a pane, with the stroke's tip at [tip]. */
internal fun OverlayBuilder.strokeGuide(guide: StrokeGuide, tip: CurvePoint, camera: LockedCamera, look: StrokeLook) {
    for (line in guide.dropped) dashed(line.inPlane().map(camera::toPixel), look.dropped, look.droppedWidth, look.droppedDash, look.droppedGap)
    for (line in guide.connectors) dashed(line.inPlane().map(camera::toPixel), look.connector, look.connectorWidth, look.connectorDash, look.connectorGap)
    for (line in guide.drawn) polyline(line.inPlane(), camera, look.ink, look.width)
    polyline(guide.string.inPlane(), camera, look.string, look.stringWidth)
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
