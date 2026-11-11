package dev.chieppa.squash.screens.look.shots

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.sheet.toGpu
import dev.chieppa.stretch.gpu.CameraView

/**
 * A perspective camera in the reference frame: metres, +Y up, the character facing +Z with her left at +X and her soles on y = 0. It looks
 * from [eye] at [target] with a vertical field of [fovDegrees] onto a [width] by [height] pixel image.
 */
data class ShotCamera(val eye: Vec3, val target: Vec3, val fovDegrees: Float, val width: Int, val height: Int) {
    /** This camera in Squash's body frame, where [frame] puts the reference's points. */
    fun inBody(frame: ReferenceFrame): CameraView =
        CameraView(eye = frame.toBody(eye).toGpu(), target = frame.toBody(target).toGpu(), fovY = Math.toRadians(fovDegrees.toDouble()).toFloat(), near = NEAR * frame.unitsPerMetre)

    /** The direction from the target toward the eye, in the reference frame. */
    val toViewer: Vec3 get() = (eye - target).normalized()

    /** The same camera as Stretch draws it, kept in the reference frame, for projecting reference points onto the image. */
    fun inReference(): CameraView = CameraView(eye = eye.toGpu(), target = target.toGpu(), fovY = Math.toRadians(fovDegrees.toDouble()).toFloat(), near = NEAR)

    companion object {
        /** The reference cameras' vertical field of view, in degrees. */
        const val REFERENCE_FOV = 30f
        private const val NEAR = 0.02f

        /** A camera from `x,y,z` [eye] and [target] text, a field of view in degrees and a `WxH` [size], as the look command takes them. */
        fun parse(eye: String, target: String, fov: String?, size: String?): ShotCamera {
            fun vec(text: String) = text.split(',').map(String::toFloat).let { (x, y, z) -> Vec3(x, y, z) }
            val (width, height) = (size ?: "800x1000").split('x').map(String::toInt)
            return ShotCamera(vec(eye), vec(target), fov?.toFloat() ?: REFERENCE_FOV, width, height)
        }
    }
}

/**
 * Where the reference frame lies in Squash's body frame: the reference's +Z forward is Squash's +X, its +X (her left) is Squash's -Z, up
 * is up; [unitsPerMetre] body units to a metre, and [ground] the body's height under its soles, which the reference's y = 0 lands on.
 * The reference's x = z = 0 is the body's own: the midline through the hips.
 */
data class ReferenceFrame(val ground: Float, val unitsPerMetre: Float = 1f) {
    fun toBody(p: Vec3): Vec3 = Vec3(p.z * unitsPerMetre, p.y * unitsPerMetre + ground, -p.x * unitsPerMetre)

    /** A direction in the reference frame turned into the body frame. */
    fun directionToBody(d: Vec3): Vec3 = Vec3(d.z, d.y, -d.x)
}
