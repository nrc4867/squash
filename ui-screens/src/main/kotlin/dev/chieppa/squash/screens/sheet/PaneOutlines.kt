package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.stretch.frame.OverlayBuilder
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import dev.chieppa.stretch.theme.Stretch

/** Two views further apart than this on screen disagree; hand-drawn ends never meet exactly. */
private const val DISAGREE_TOLERANCE_PIXELS = 2f

/** Below this cosine of the turn between two lines, the gap on the outside of the joint shows. */
private const val JOINT_COSINE = 0.995f

/** The colors and pixel widths of drawn outlines and their disagreements. */
data class OutlineLook(
    val curve: Color,
    val curveWidth: Float,
    val warn: Color,
    val limitWidth: Float,
    val spanWidth: Float,
)

/** The outlines' drawing in the current theme and pixel density. */
@Composable
internal fun rememberOutlineLook(): OutlineLook {
    val c = Stretch.colors
    val density = LocalDensity.current
    fun px(dp: Int) = with(density) { dp.dp.toPx() }
    return OutlineLook(curve = c.inkMuted, curveWidth = px(4), warn = c.warnEdge, limitWidth = px(4), spanWidth = px(8))
}

/**
 * What each locked pane shows over its guides: its stored outline, then [tool]'s overlay, then where the views disagree,
 * so the highlight stays on top of a live stroke and shows under any tool.
 */
fun paneOutlineOverlays(
    selection: SheetSelection?,
    cameras: Map<View, LockedCamera>,
    tool: PaneTool?,
    look: OutlineLook,
): Map<View, ViewportOverlay> {
    if (selection == null || cameras.isEmpty()) return emptyMap()
    val tolerance = DISAGREE_TOLERANCE_PIXELS * cameras.values.first().planePixel
    val disagreements = viewsDisagree(selection.outlines, tool?.liveStroke, tolerance)
    return cameras.mapValues { (view, camera) ->
        val tooled = tool?.overlay(view, camera) ?: ViewportOverlay.Empty
        storedOutline(selection.outlines[view], camera, look) + tooled + disagreeOverlay(view, camera, disagreements, look)
    }
}

private fun storedOutline(outline: OutlineCurve?, camera: LockedCamera, look: OutlineLook): ViewportOverlay {
    if (outline == null) return ViewportOverlay.Empty
    return buildOverlay { polyline(outlineSamples(outline), camera, look.curve, look.curveWidth) }
}

/** In [view]'s pane: the line a disagreement crosses, where [view] is either side of it, and its own stretches past that. */
fun disagreeOverlay(view: View, camera: LockedCamera, disagreements: List<Disagreement>, look: OutlineLook): ViewportOverlay {
    if (disagreements.isEmpty()) return ViewportOverlay.Empty
    return buildOverlay {
        for (found in disagreements) {
            if (view == found.view || view == found.against) limitLine(found, camera, look)
            if (view == found.view) found.spans.forEach { polyline(it, camera, look.warn, look.spanWidth) }
        }
    }
}

private fun OverlayBuilder.limitLine(found: Disagreement, camera: LockedCamera, look: OutlineLook) {
    val across = camera.lineAcross(camera.axes.direction(found.axis), found.limit * camera.drawingScale)
    line(across.start, across.end, look.warn, look.limitWidth)
}

private fun PartAxes.direction(axis: PartAxis): Vec3 = when (axis) {
    PartAxis.ALONG -> along
    PartAxis.UP -> up
    PartAxis.SIDE -> side
}

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
