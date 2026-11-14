package dev.chieppa.squash.screens.surface

import dev.chieppa.squash.geom.laidOn

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.character.build.BodyColor
import dev.chieppa.squash.character.build.BodyLines
import dev.chieppa.squash.character.build.BodySurfaceWork
import dev.chieppa.squash.character.build.FurView
import dev.chieppa.squash.character.build.PartLines
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.SurfaceDress
import dev.chieppa.squash.character.surface.SurfacePaint
import dev.chieppa.squash.character.surface.PosedSurface
import dev.chieppa.squash.character.build.lineWeightStrokes
import dev.chieppa.squash.character.surface.isRun
import dev.chieppa.squash.document.CameraBin
import dev.chieppa.squash.document.ExpressionId
import dev.chieppa.squash.document.GazeCheats
import dev.chieppa.squash.document.LineRules
import dev.chieppa.squash.document.MarkKind
import dev.chieppa.squash.document.Palette
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.SlotName
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
import dev.chieppa.squash.screens.sheet.garmentDraws
import dev.chieppa.squash.screens.sheet.piecesDraw
import dev.chieppa.squash.screens.sheet.surfaceDraw
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
    val shown: ExpressionId? = null,
    val target: Vec3? = null,
    val cheats: GazeCheats = GazeCheats(),
    val surface: CharacterSurface? = null,
    val paint: SurfacePaint? = null,
    val dress: SurfaceDress? = null,
)

/**
 * A view of [state] through [camera]. Every part's contour draws as a ribbon at the silhouette weight in the back of the depth
 * buffer, where only what clears the whole figure shows, and at the interior weight in the scene. [faceBin] says which camera bin
 * the face shows in each part holding one. Color layers lie on each part as it shows, posed or not. A character built as one surface
 * draws that surface, its paint, its contour, its joins' ink and its parts' ink marks resolved on it in place of the parts it
 * holds, which keep only their pieces that are not runs.
 */
fun characterView(
    rect: PixelRect,
    camera: CameraView,
    state: CharacterState,
    look: CharacterLook,
    shading: ObjectShading,
    @Suppress("UNUSED_PARAMETER") pixelsPerUnit: Float,
    faceBin: (PartId) -> CameraBin,
): SceneView {
    val ink = state.palette.colorOf(look.inkSlot) ?: look.fallback
    val fallback = PartMaterial(look.fallback, ink = ink)
    val parts = state.character.parts.filter { it.id != null }
    val eye = Vec3(camera.eye.x, camera.eye.y, camera.eye.z)
    val view = FullView({ camera }, rect.width, rect.height)
    val faces = FaceWork(
        FaceInput(parts.associateBy { requireNotNull(it.id) }, state.work, state.lines, state.palette, shading.toLight, state.shown, state.target, state.cheats),
        look.marks, ink, state.taper,
    ) { part, _, _ -> faceBin(requireNotNull(part.id)) }
    val center = state.character.box?.center ?: Vec3.ZERO
    val ribbons = ArrayList<RibbonDraw>()
    val surface = state.surface
    val inSurface = surface?.parts.orEmpty()
    val filled = parts.mapNotNull { part ->
        val id = requireNotNull(part.id)
        if (id in inSurface) return@mapNotNull piecesDraw(part, state.color.parts[id], state.palette, fallback, shading, eye - center)
        val draw = coloredPartDraw(
            part.surface.mesh, state.color.parts[id], state.palette, fallback, shading, part.bodySide(), eye - center, translation(part.origin), 0f,
            cutMesh = { cut -> part.surface.grid(cut.patch)?.let(cut::laidOn) ?: cut.mesh },
        )
        val face = faces.draws(id, view)
        ribbons += face.ribbons
        draw.copy(layers = draw.layers + face.layers)
    } + state.dress?.let { garmentDraws(it, state.palette, fallback, shading, eye - center) }.orEmpty() + listOfNotNull(surface?.let { surfaceDraw(it, state.paint, state.dress?.skin, state.color, state.palette, fallback, shading, eye - center) })
    val lined = state.lines.parts.values.map { lines -> if (lines.part.id in inSurface) lines.withoutRuns() else lines }
    val oneLines = surface?.let { OneSurfaceLines(PosedSurface(it, state.paint, state.dress), lineWeightStrokes(state.character, state.work)) }
    val contour = if (oneLines != null) oneLines.contour(lined, view) else contourRibbon(lined, view)
    contour?.let { ribbons += contourDraws(it, look.lines, ink, LineLayers.FIGURE, state.character.box?.radius ?: 0f) }
    lined.forEach { ribbons += creaseDraws(it, look.creases, ink, DepthLayer.SCENE) }
    oneLines?.let { ribbons += it.inks(view, look, ink) }
    state.paint?.seenFrom(view::toViewer)?.marks?.forEach { (id, marks) ->
        marks.mapNotNull { it.lineDraw(state.lines.parts[id], look.marks, it.slot?.let(state.palette::colorOf) ?: ink, state.taper, DepthLayer.SCENE) }.forEach { ribbons += it }
    }
    for (part in parts) {
        if (part.id in inSurface) continue
        val work = state.work.parts[requireNotNull(part.id)] ?: continue
        work.looseMarks().filter { it.kind != MarkKind.FUR }.mapNotNull { it.lineDraw(state.lines.parts[part.id], look.marks, it.slot?.let(state.palette::colorOf) ?: ink, state.taper, DepthLayer.SCENE) }.forEach { ribbons += it }
    }
    val sides = parts.associate { requireNotNull(it.id) to it.bodySide() }
    val fur = furDraws(ShownFur(state.fur, look.lines), ink) { id, slot -> furMaterial(state.palette, slot, look.fallback, ink, shading, sides[id], eye - center) }
    return SceneView(rect, camera.viewProjection(rect.width, rect.height), filled, ribbons, fur.layers)
}
