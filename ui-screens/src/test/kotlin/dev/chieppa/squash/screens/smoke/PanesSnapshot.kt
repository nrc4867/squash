package dev.chieppa.squash.screens.smoke

import dev.chieppa.squash.render.PixelRect
import dev.chieppa.squash.render.ViewsScene
import dev.chieppa.squash.screens.sheet.LockedCamera
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

/** Where Full's camera sits from the part, as multiples of the part's radius. */
private val FULL_EYE = Vec3(1.8f, 1.2f, -2.4f)

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
    val part = selection.part
    val fit = fitSheet(part.axes, part.extents, rects)
    val cameras = SheetPane.entries.mapNotNull { pane ->
        pane.view?.let { it to LockedCamera(it, part.axes, fit, rects[pane.ordinal].width, rects[pane.ordinal].height) }
    }.toMap()
    val target = part.center.toGpu()
    val full = CameraView(eye = target + FULL_EYE * part.radius, target = target)
    val frame = sheetFrame(SheetInput(rects, width, height, look, selection, cameras, full, active = null))
    return GpuSnapshot.render(frame, width, height, options = GpuOptions(onError = onError)) { ViewsScene(it) }
}
