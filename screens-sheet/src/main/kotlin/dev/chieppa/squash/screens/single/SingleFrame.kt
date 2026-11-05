package dev.chieppa.squash.screens.single

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.SceneView
import dev.chieppa.squash.screens.sheet.FullView
import dev.chieppa.squash.screens.sheet.ShownFur
import dev.chieppa.squash.screens.sheet.shownFurDraws
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.PaneColor
import dev.chieppa.squash.screens.sheet.SheetHints
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.borderRibbons
import dev.chieppa.squash.screens.sheet.SurfaceRibbons
import dev.chieppa.squash.screens.sheet.clippedTo
import dev.chieppa.squash.screens.sheet.colored
import dev.chieppa.squash.screens.sheet.fullGround
import dev.chieppa.squash.screens.sheet.lockedDraws
import dev.chieppa.squash.screens.sheet.lockedOverlay
import dev.chieppa.squash.screens.sheet.lockedRibbons
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.squash.screens.sheet.toRect
import dev.chieppa.stretch.frame.ViewportOverlay
import dev.chieppa.stretch.gpu.CameraView

/** What one frame of Single shows, through [locked] while snapped and [free] otherwise; [singleFrame] turns it into draws. */
class SingleInput(
    val width: Int,
    val height: Int,
    val look: SheetLook,
    val selection: SheetSelection? = null,
    val locked: LockedCamera? = null,
    val free: CameraView? = null,
    val toolOverlay: ViewportOverlay = ViewportOverlay.Empty,
    val hints: SheetHints = SheetHints(),
    val color: PaneColor? = null,
    val surface: SurfaceRibbons? = null,
    val freeOverlay: ViewportOverlay = ViewportOverlay.Empty,
    val fur: ShownFur = ShownFur.NONE,
)

/**
 * One immutable frame of Single for the render thread. Snapped, it is a locked pane filling the view, with the tool's
 * overlay. Free, the selection is filled over the rest of the character at the ghost's strength, with nothing to draw on.
 */
fun singleFrame(input: SingleInput): SceneFrame {
    val selection = input.selection ?: return SceneFrame(input.look.well)
    val rect = PixelRect(0, 0, input.width, input.height)
    val locked = input.locked
    if (locked != null) {
        val body = selection.part.axes.bodyViewOf(locked.view)
        val draws = lockedDraws(locked.view, body, locked.basis, selection, input.look, input.hints, input.color, input.surface != null)
        val overlay = lockedOverlay(locked.view, locked, selection, input.look, input.hints, active = null) + input.toolOverlay
        val fur = shownFurDraws(input.fur, locked.view in selection.outlines, locked.basis.back, selection, input.look, input.hints, input.color)
        val ribbons = lockedRibbons(locked.view, locked, selection, input.look, input.hints, input.color, input.surface)
        return SceneFrame(input.look.well, listOf(SceneView(rect, locked.viewProjection(), draws, ribbons, fur.layers)), overlay.clippedTo(rect.toRect()))
    }
    val free = input.free ?: return SceneFrame(input.look.well)
    val toCamera = Vec3(free.eye.x, free.eye.y, free.eye.z) - selection.box.center
    val fur = shownFurDraws(input.fur, true, toCamera, selection, input.look, input.hints, input.color)
    val shown = SceneView(rect, free.viewProjection(input.width, input.height), freeDraws(selection, free, input), freeRibbons(selection, free, input), fur.layers)
    return SceneFrame(input.look.well, listOf(shown), input.freeOverlay.clippedTo(rect.toRect()))
}

/** The lines through the free [camera]: the whole figure's when the hints show it whole, else the selection's. */
private fun freeRibbons(selection: SheetSelection, camera: CameraView, input: SingleInput): List<RibbonDraw> {
    val surface = input.surface ?: return input.look.borderRibbons(input.color)
    val view = FullView({ camera }, input.width, input.height)
    if (input.hints.whole) return surface.figure(view)
    return selection.part.id?.let { surface.selected(it, view) }.orEmpty()
}

/** Furthest ghost first, so a nearer one blends over it. */
private fun freeDraws(selection: SheetSelection, camera: CameraView, input: SingleInput): List<MeshDraw> = buildList {
    val look = input.look
    add(fullGround(selection, look))
    val toCamera = Vec3(camera.eye.x, camera.eye.y, camera.eye.z) - selection.box.center
    val outline = if (input.surface != null) 0f else look.lockedOutline
    if (input.hints.whole) {
        (selection.others + selection.part).filter { !it.planned }.forEach { add(look.colored(it, input.color, toCamera, outline)) }
        return@buildList
    }
    add(look.colored(selection.part, input.color, toCamera, outline, layer = DepthLayer.FRONT))
    if (input.hints.ghost > 0f) {
        selection.others
            .sortedByDescending { (it.center.toGpu() - camera.eye).length }
            .forEach { add(look.colored(it, input.color, toCamera, look.ghostOutline, input.hints.ghost)) }
    }
}
