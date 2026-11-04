package dev.chieppa.squash.screens.single

import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.MeshDraw
import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.SceneFrame
import dev.chieppa.squash.render.SceneView
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetHints
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.clippedTo
import dev.chieppa.squash.screens.sheet.draw
import dev.chieppa.squash.screens.sheet.fullGround
import dev.chieppa.squash.screens.sheet.lockedDraws
import dev.chieppa.squash.screens.sheet.lockedOverlay
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
        val draws = lockedDraws(locked.view, body, selection, input.look, input.hints)
        val overlay = lockedOverlay(locked.view, locked, selection, input.look, input.hints, active = null) + input.toolOverlay
        return SceneFrame(input.look.well, listOf(SceneView(rect, locked.viewProjection(), draws)), overlay.clippedTo(rect.toRect()))
    }
    val free = input.free ?: return SceneFrame(input.look.well)
    return SceneFrame(input.look.well, listOf(SceneView(rect, free.viewProjection(input.width, input.height), freeDraws(selection, free, input))))
}

/** Furthest ghost first, so a nearer one blends over it. */
private fun freeDraws(selection: SheetSelection, camera: CameraView, input: SingleInput): List<MeshDraw> = buildList {
    val look = input.look
    add(fullGround(selection, look))
    add(look.draw(selection.part, look.lockedOutline, layer = DepthLayer.FRONT))
    if (input.hints.ghost > 0f) {
        selection.others
            .sortedByDescending { (it.center.toGpu() - camera.eye).length }
            .forEach { add(look.draw(it, look.ghostOutline, input.hints.ghost)) }
    }
}
