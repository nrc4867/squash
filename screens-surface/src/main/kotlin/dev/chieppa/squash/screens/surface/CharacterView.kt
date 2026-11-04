package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BodyColor
import dev.chieppa.squash.character.build.BodyLines
import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.FurView
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.LineRules
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.ColorDetail
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.render.SceneView
import dev.chieppa.squash.screens.sheet.bodySide
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.coloredPartDraw
import dev.chieppa.squash.screens.sheet.furDraws
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.screens.sheet.ShownFur
import dev.chieppa.squash.screens.sheet.furDraws
import dev.chieppa.squash.screens.sheet.furMaterial
import dev.chieppa.squash.screens.sheet.translation
import dev.chieppa.stretch.gpu.CameraView

/**
 * How a character is drawn: [lines] sets the outline, its silhouette weight round the whole figure and its interior weight where a
 * part stands clear of another; [marks] sets the lines drawn on the surface, [creases] the ink where a part leaves its parent.
 */
@Immutable
data class CharacterLook(
    val lines: LineRule,
    val marks: LineRule,
    val creases: LineRule,
    val inkSlot: SlotName,
    val fallback: Color,
)

/** What a view of a character reads from its settled graph. [fur] is what the fur node for this view's camera holds. */
class CharacterState(
    val character: AssembledCharacter,
    val color: BodyColor,
    val work: BodySurfaceWork,
    val palette: Palette,
    val lines: BodyLines,
    val fur: FurView,
    val taper: Float = LineRules.DEFAULT_TAPER,
)

private fun MarkGroup.isEye() = roles.any { it.role == MarkRole.LID }

/**
 * A view of [state] through [camera]. Every part's contour draws as a ribbon at the silhouette weight in the back of the depth
 * buffer, where only what clears the whole figure shows, and at the interior weight in the scene. [faceBin] says which camera bin
 * the face shows in each part holding one.
 */
fun characterView(
    rect: PixelRect,
    camera: CameraView,
    state: CharacterState,
    look: CharacterLook,
    shading: ObjectShading,
    pixelsPerUnit: Float,
    faceBin: (PartId) -> CameraBin,
): SceneView {
    val ink = state.palette.colorOf(look.inkSlot) ?: look.fallback
    val fallback = PartMaterial(look.fallback, ink = ink)
    val eye = Vec3(camera.eye.x, camera.eye.y, camera.eye.z)
    val tolerance = ColorDetail.tolerance(ColorDetail.stepFor(pixelsPerUnit, ColorDetail.COARSEST))
    val parts = state.character.parts.filter { it.id != null }
    val center = state.character.box?.center ?: Vec3.ZERO
    val ribbons = ArrayList<RibbonDraw>()
    val filled = parts.map { part ->
        val id = requireNotNull(part.id)
        val draw = coloredPartDraw(
            part.surface.mesh, state.color.parts[id], state.palette, fallback, shading, part.bodySide(), eye - center, translation(part.origin), 0f,
        )
        val work = state.work.parts[id] ?: return@map draw
        val marks = work.marks.associateBy { it.id }
        val eyes = work.groups.filter { it.isEye() }.map { group ->
            eyeDraws(group, marks, part.surface, faceBin(id), state.palette, shading.toLight, eye - part.origin, tolerance, look.marks, ink)
        }
        eyes.forEach { ribbons += it.ribbons }
        draw.copy(layers = draw.layers + eyes.flatMap { it.layers })
    }
    val view = FullView({ camera }, rect.width, rect.height)
    contourRibbon(state.lines.parts.values, view)?.let { ribbons += contourDraws(it, look.lines, ink, LineLayers.FIGURE, state.character.box?.radius ?: 0f) }
    state.lines.parts.values.forEach { ribbons += creaseDraws(it, look.creases, ink, DepthLayer.SCENE) }
    for (part in parts) {
        val work = state.work.parts[requireNotNull(part.id)] ?: continue
        work.looseMarks().filter { it.kind != MarkKind.FUR }.mapNotNull { it.lineDraw(state.lines.parts[part.id], look.marks, ink, state.taper, DepthLayer.SCENE) }.forEach { ribbons += it }
    }
    val sides = parts.associate { requireNotNull(it.id) to it.bodySide() }
    val fur = furDraws(ShownFur(state.fur, look.lines), ink) { id, slot -> furMaterial(state.palette, slot, look.fallback, ink, shading, sides[id], eye - center) }
    return SceneView(rect, camera.viewProjection(rect.width, rect.height), filled + fur.draws, ribbons + fur.ribbons)
}
