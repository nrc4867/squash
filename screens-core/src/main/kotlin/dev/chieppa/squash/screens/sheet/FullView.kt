package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.stretch.gpu.CameraView

/** How the Full pane shows body space, [width] by [height] pixels, through whatever [camera] holds at the moment. */
class FullView(private val camera: () -> CameraView, val width: Int, val height: Int) {
    /** The pane pixel showing [point], or null behind the camera. */
    fun toPixel(point: Vec3): Offset? = camera().project(point.toGpu(), width, height)

    /** From [point] towards the eye. */
    fun toViewer(point: Vec3): Vec3 {
        val eye = camera().eye
        return Vec3(eye.x - point.x, eye.y - point.y, eye.z - point.z).normalized()
    }
}
