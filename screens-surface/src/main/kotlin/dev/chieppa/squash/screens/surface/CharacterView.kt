package dev.chieppa.squash.screens.surface

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BodyColor
import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.character.build.SocketCrease
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.MarkGroup
import dev.chieppa.squash.document.MarkRole
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.ColorDetail
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.BodySide
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.ObjectShading
import dev.chieppa.squash.render.PartMaterial
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.render.SceneView
import dev.chieppa.squash.screens.sheet.coloredPartDraw
import dev.chieppa.squash.screens.sheet.colorOf
import dev.chieppa.squash.screens.sheet.ribbon
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
    val fur: FurRule,
    val inkSlot: SlotName,
    val fallback: Color,
)

/** What a view of a character reads from its settled graph. [isFur] tells a fur stroke from the other marks. */
class CharacterState(
    val character: AssembledCharacter,
    val color: BodyColor,
    val work: BodySurfaceWork,
    val palette: Palette,
    val creases: List<SocketCrease>,
    val isFur: (ResolvedMark) -> Boolean,
)

private fun PlacedPart.bodySide(): BodySide? = when {
    side == null -> null
    origin.z >= 0f -> BodySide.POSITIVE_Z
    else -> BodySide.NEGATIVE_Z
}

private fun MarkGroup.isEye() = roles.any { it.role == MarkRole.LID }

/**
 * A view of [state] through [camera]. The silhouette is every part's outline in the back of the depth buffer at the silhouette
 * weight, where only what clears the whole figure shows; each part then draws at the interior weight over it. [faceBin] says which
 * camera bin the face shows in each part holding one.
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
    val silhouette = parts.map { part ->
        MeshDraw(part.surface.mesh, PartMaterial(ink, ink = ink), translation(part.origin), look.lines.silhouettePixels, layer = DepthLayer.BACK)
    }
    val filled = parts.map { part ->
        val id = requireNotNull(part.id)
        val draw = coloredPartDraw(
            part.surface.mesh, state.color.parts[id], state.palette, fallback, shading, part.bodySide(), eye - center, translation(part.origin), look.lines.interiorPixels,
        )
        val work = state.work.parts[id] ?: return@map draw
        val marks = work.marks.associateBy { it.id }
        val eyes = work.groups.filter { it.isEye() }.map { group ->
            eyeDraws(group, marks, part.surface, faceBin(id), state.palette, shading.toLight, eye - part.origin, tolerance, look.marks, ink)
        }
        eyes.forEach { ribbons += it.ribbons }
        draw.copy(layers = draw.layers + eyes.flatMap { it.layers })
    }
    for (crease in state.creases) {
        crease.runs.forEach { run -> evenRibbon(run, look.creases, ink)?.let { ribbons += it } }
    }
    for (part in parts) {
        val work = state.work.parts[requireNotNull(part.id)] ?: continue
        val grouped = work.groups.flatMap { group -> group.roles.flatMap { it.marks } }.toSet()
        val (fur, plain) = work.marks.filter { it.id !in grouped }.partition(state.isFur)
        ribbons += furRibbons(fur, look.fur, camera, rect, ink)
        plain.mapNotNull { mark -> if (mark.ink) mark.ribbon()?.let { RibbonDraw(it, look.marks, ink) } else null }.forEach { ribbons += it }
    }
    return SceneView(rect, camera.viewProjection(rect.width, rect.height), silhouette + filled, ribbons)
}
