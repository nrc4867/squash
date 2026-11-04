package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.sheet.LockedCamera
import dev.chieppa.squash.screens.sheet.SheetHints
import dev.chieppa.squash.screens.sheet.SheetInput
import dev.chieppa.squash.screens.sheet.SheetLook
import dev.chieppa.squash.screens.sheet.SheetPane
import dev.chieppa.squash.screens.sheet.SheetSelection
import dev.chieppa.squash.screens.sheet.fitSheet
import dev.chieppa.squash.screens.sheet.sheetFrame
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.stretch.gpu.CameraView
import dev.chieppa.stretch.gpu.GpuImage
import dev.chieppa.stretch.gpu.GpuOptions
import dev.chieppa.stretch.gpu.GpuSnapshot
import dev.chieppa.stretch.gpu.Vec3

/** Where Full's camera sits from the character, as multiples of its radius. */
private val FULL_EYE = Vec3(1.35f, 0.9f, -1.8f)

/** The panes' input for [selection], framed as the running app frames them. */
internal fun panesInput(selection: SheetSelection, look: SheetLook, rects: List<PixelRect>, width: Int, height: Int, hints: SheetHints = SheetHints()): SheetInput {
    val part = selection.part
    val fit = fitSheet(part.box, rects, (selection.box.center - part.box.center).length + selection.box.radius)
    val cameras = SheetPane.entries.mapNotNull { pane ->
        val body = pane.body ?: return@mapNotNull null
        val view = part.axes.framing.getValue(body)
        view to LockedCamera(view, part.axes, fit, rects[pane.ordinal].width, rects[pane.ordinal].height, part.origin)
    }.toMap()
    val target = selection.box.center.toGpu()
    val full = CameraView(eye = target + FULL_EYE * selection.box.radius, target = target)
    return SheetInput(rects, width, height, look, selection, cameras, full, active = null, hints = hints)
}

/**
 * Renders the panes' frame for [selection] once with the real scene on a real GPU device, into an image [width] by
 * [height]. WebGPU errors go to [onError].
 */
internal fun renderPanes(
    selection: SheetSelection,
    look: SheetLook,
    rects: List<PixelRect>,
    width: Int,
    height: Int,
    onError: (String) -> Unit,
): GpuImage {
    val frame = sheetFrame(panesInput(selection, look, rects, width, height))
    return GpuSnapshot.render(frame, width, height, options = GpuOptions(onError = onError)) { ViewsScene(it) }
}
