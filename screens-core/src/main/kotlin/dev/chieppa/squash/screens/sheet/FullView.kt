package dev.chieppa.squash.screens.sheet

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.stretch.gpu.CameraView
import kotlin.math.tan

/** A line of sight in body space, from [origin] along [direction], which has length 1. What lies under a pixel lies along it. */
class BodyRay(val origin: Vec3, val direction: Vec3) {
    fun at(distance: Float): Vec3 = origin + direction * distance
}

/** How the Full pane shows body space, [width] by [height] pixels, through whatever [camera] holds at the moment. */
class FullView(private val camera: () -> CameraView, val width: Int, val height: Int) : SurfaceView {
    /** The camera as it stands. */
    val current: CameraView get() = camera()

    /** The pane pixel showing [point], or null behind the camera. */
    override fun toPixel(point: Vec3): Offset? = camera().project(point.toGpu(), width, height)

    /** The line of sight through [pixel], as the camera holds it now, for finding what on the surface a pointer is over. */
    fun ray(pixel: Offset): BodyRay {
        val ray = camera().ray(pixel, width, height)
        return BodyRay(Vec3(ray.origin.x, ray.origin.y, ray.origin.z), Vec3(ray.direction.x, ray.direction.y, ray.direction.z))
    }

    /** How far [point] lies from the eye. */
    override fun depthOf(point: Vec3): Float {
        val eye = camera().eye
        return Vec3(eye.x - point.x, eye.y - point.y, eye.z - point.z).length
    }

    /** How many pixels a body unit spans at [point]. */
    override fun pixelsPerUnitAt(point: Vec3): Float = height / (2f * depthOf(point) * tan(camera().fovY / 2f))

    /** From [point] towards the eye. */
    override fun toViewer(point: Vec3): Vec3 {
        val eye = camera().eye
        return Vec3(eye.x - point.x, eye.y - point.y, eye.z - point.z).normalized()
    }
}
