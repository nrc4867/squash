package dev.chieppa.squash.screens.edit

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.anchorOf
import dev.chieppa.squash.document.anchors
import dev.chieppa.squash.geom.BezierPath
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.stretch.frame.OverlayBuilder
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.frame.buildOverlay
import dev.chieppa.stretch.theme.Stretch

/** The colors and pixel sizes of a curve's points and handles. */
data class CurveLook(
    val ring: Color,
    val fill: Color,
    val affected: Color,
    val handleWidth: Float,
    val handleRadius: Float,
    val anchorHalf: Float,
    val anchorEdge: Float,
    val affectedWidth: Float,
    val affectedDash: Float,
    val affectedGap: Float,
    val grabReach: Float,
)

/** The curve drawing in the current theme and pixel density. */
@Composable
internal fun rememberCurveLook(): CurveLook {
    val c = Stretch.colors
    val density = LocalDensity.current
    fun px(dp: Int) = with(density) { dp.dp.toPx() }
    return CurveLook(
        ring = c.ringSelected,
        fill = c.surfaceFloat,
        affected = c.affected,
        handleWidth = px(2),
        handleRadius = px(4),
        anchorHalf = px(5),
        anchorEdge = px(2),
        affectedWidth = px(5),
        affectedDash = px(8),
        affectedGap = px(6),
        grabReach = px(12),
    )
}

/** [curve]'s anchors, the handles on show for the point [selected], and its selected sections dashed as affected. */
fun curvePoints(curve: OutlineCurve, selected: Int?, camera: LockedCamera, look: CurveLook): ViewportOverlay = buildOverlay {
    val path = curve.toPath()
    curve.selectedSections(selected).sorted().forEach { section ->
        affected(BezierPath(listOf(path.segments[section])).flatten().let { flat -> flat.xs.indices.map { CurvePoint(flat.xs[it], flat.ys[it]) } }, camera, look)
    }
    for (handle in curve.shownHandles(selected)) {
        val tip = camera.toPixel(curve.points[handle])
        line(camera.toPixel(curve.points[anchorOf(handle)]), tip, look.ring, look.handleWidth)
        dot(tip, look.handleRadius, if (handle == selected) look.fill else look.ring)
    }
    for (anchor in curve.anchors) anchorSquare(camera.toPixel(curve.points[anchor]), anchor == selected, look)
}

/** A stretch of an old curve about to change, dashed as the board marks it. */
fun OverlayBuilder.affected(points: List<CurvePoint>, camera: LockedCamera, look: CurveLook) {
    dashed(points.map(camera::toPixel), look.affected, look.affectedWidth, look.affectedDash, look.affectedGap)
}

private fun OverlayBuilder.anchorSquare(center: Offset, selected: Boolean, look: CurveLook) {
    val square = Rect(center.x - look.anchorHalf, center.y - look.anchorHalf, center.x + look.anchorHalf, center.y + look.anchorHalf)
    rect(square, if (selected) look.ring else look.fill)
    outline(square, look.ring, look.anchorEdge)
}
