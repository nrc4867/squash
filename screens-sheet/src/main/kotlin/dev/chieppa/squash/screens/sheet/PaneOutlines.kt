package dev.chieppa.squash.screens.sheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.stretch.frame.OverlayBuilder
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import dev.chieppa.stretch.theme.Stretch

/** Two views further apart than this on screen disagree; hand-drawn ends never meet exactly. */
private const val DISAGREE_TOLERANCE_PIXELS = 2f

/** The colors and pixel widths of drawn outlines and their disagreements. */
data class OutlineLook(
    val curve: Color,
    val curveWidth: Float,
    val warn: Color,
    val limitWidth: Float,
    val spanWidth: Float,
    val hiddenWidth: Float = limitWidth,
    val hiddenDash: Float = spanWidth,
    val hiddenGap: Float = spanWidth,
)

/** The outlines' drawing in the current theme and pixel density. */
@Composable
fun rememberOutlineLook(): OutlineLook {
    val c = Stretch.colors
    val density = LocalDensity.current
    fun px(dp: Int) = with(density) { dp.dp.toPx() }
    return OutlineLook(curve = c.inkMuted, curveWidth = px(4), warn = c.warnEdge, limitWidth = px(4), spanWidth = px(8))
}

/** Where the selected part's views disagree on screen, or nothing while no pane has a camera. */
fun paneDisagreements(selection: SheetSelection?, cameras: Map<View, LockedCamera>, tool: PaneTool?): List<Disagreement> {
    if (selection == null || cameras.isEmpty() || tool?.showsWhole == true) return emptyList()
    val replaced = tool?.liveAlongside.orEmpty().map { it.view }
    return viewsDisagree(selection.outlines - replaced, tool?.liveStroke, DISAGREE_TOLERANCE_PIXELS * cameras.values.first().planePixel)
}

/**
 * What each locked pane shows over its guides: in the [active] pane its stored outline, then what an edit would land on
 * under this side's override, then [tool]'s overlay, then where the views disagree, so the highlight stays on top of a live
 * stroke and shows under any tool. With no [active] pane, every pane shows its outline.
 */
fun paneOutlineOverlays(
    selection: SheetSelection?,
    cameras: Map<View, LockedCamera>,
    tool: PaneTool?,
    look: OutlineLook,
    disagreements: List<Disagreement> = paneDisagreements(selection, cameras, tool),
    active: View? = null,
): Map<View, ViewportOverlay> {
    if (selection == null || cameras.isEmpty()) return emptyMap()
    return cameras.mapValues { (view, camera) ->
        val tooled = tool?.overlay(view, camera) ?: ViewportOverlay.Empty
        if (tool?.showsWhole == true) return@mapValues tooled
        val stored = if (active == null || view == active) storedOutline(selection.outlines[view], camera, look) else ViewportOverlay.Empty
        stored + hiddenOutline(selection.hidden[view], camera, look) + tooled + disagreeOverlay(view, camera, disagreements, look)
    }
}

/** The pair's outline under this side's override, dashed, so an edit landing on it shows though the side's own does not move. */
private fun hiddenOutline(outline: OutlineCurve?, camera: LockedCamera, look: OutlineLook): ViewportOverlay {
    if (outline == null) return ViewportOverlay.Empty
    return buildOverlay { dashed(outlineSamples(outline).map(camera::toPixel), look.warn, look.hiddenWidth, look.hiddenDash, look.hiddenGap) }
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
    val scale = if (found.axis == PartAxis.ALONG) camera.alongScale else camera.drawingScale
    val across = camera.lineAcross(camera.axes.direction(found.axis), found.limit * scale)
    line(across.start, across.end, look.warn, look.limitWidth)
}

private fun PartAxes.direction(axis: PartAxis): Vec3 = when (axis) {
    PartAxis.ALONG -> along
    PartAxis.UP -> up
    PartAxis.SIDE -> side
}
