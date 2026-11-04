package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.screens.sheet.ribbon
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.stretch.gpu.CameraView

/** Which fur strokes a view shows: those whose root faces the camera by under [showNear], as a cosine, at least [spacingPixels] apart on screen. */
@Immutable
data class FurRule(val showNear: Float, val spacingPixels: Float, val line: LineRule)

/**
 * The strokes of [marks] that [rule] shows in a view through [camera] of [rect], as ribbons. A stroke further along [marks] gives
 * way to any kept before it, so the choice holds while the view holds.
 */
fun furRibbons(marks: List<ResolvedMark>, rule: FurRule, camera: CameraView, rect: PixelRect, color: Color): List<RibbonDraw> {
    val kept = ArrayList<Offset>()
    val eye = camera.eye
    return marks.mapNotNull { mark ->
        val root = mark.points.first()
        val toEye = Vec3(eye.x, eye.y, eye.z) - root.position
        val facing = root.normal dot toEye.normalized()
        if (facing <= 0f || facing >= rule.showNear) return@mapNotNull null
        val at = camera.project(root.position.toGpu(), rect.width, rect.height) ?: return@mapNotNull null
        if (kept.any { (it - at).getDistanceSquared() < rule.spacingPixels * rule.spacingPixels }) return@mapNotNull null
        kept += at
        mark.ribbon()?.let { RibbonDraw(it, rule.line, color) }
    }
}
