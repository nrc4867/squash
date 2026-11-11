package dev.chieppa.squash.screens.look.shots

import dev.chieppa.squash.geom.Vec3

/** How a shot frames the character, which says which body bands the compare command scores it by. */
enum class ShotView { FRONT, THREE_QUARTER, SIDE, FACE, HAND, FOOT }

/**
 * One shot of the reference set: the image [name] without its extension, the [outfit] and [pose] shown (`rest` for the display pose alone),
 * whether the shades are tipped up, and the [camera].
 */
data class ReferenceShot(val name: String, val outfit: String, val pose: String, val shadesUp: Boolean, val view: ShotView, val camera: ShotCamera) {
    /** The pose name an example lists for this shot: the shot's own, or `shades-up` for the shades tipped up at rest. */
    val examplePose: String get() = if (shadesUp) SHADES_UP else pose

    companion object {
        const val REST = "rest"
        const val SHADES_UP = "shades-up"
    }
}

private const val BODY_W = 800
private const val BODY_H = 1000
private const val CLOSE = 800

private val BODY_TARGET = Vec3(0f, 0.92f, 0f)
private val FRONT_EYE = Vec3(0f, 1.038f, 3.948f)
private val THREE_QUARTER_EYE = Vec3(2.446f, 1.314f, 3.077f)
private val SIDE_EYE = Vec3(3.947f, 1.078f, 0f)

private fun body(name: String, outfit: String, pose: String, view: ShotView): ReferenceShot {
    val eye = when (view) {
        ShotView.FRONT -> FRONT_EYE
        ShotView.SIDE -> SIDE_EYE
        else -> THREE_QUARTER_EYE
    }
    return ReferenceShot(name, outfit, pose, false, view, ShotCamera(eye, BODY_TARGET, ShotCamera.REFERENCE_FOV, BODY_W, BODY_H))
}

private fun close(name: String, outfit: String, pose: String, view: ShotView, target: Vec3, eye: Vec3, shadesUp: Boolean = false) =
    ReferenceShot(name, outfit, pose, shadesUp, view, ShotCamera(eye, target, ShotCamera.REFERENCE_FOV, CLOSE, CLOSE))

private const val REST = ReferenceShot.REST

/**
 * The reference's final batch of 24 shots, by the file names its renders carry, with their cameras in the reference frame. The close-ups'
 * targets follow the reference's grounded hips in each outfit, so they are where its face, hand or foot stands.
 */
val REFERENCE_SHOTS: List<ReferenceShot> = listOf(
    body("beach-threeq", "beach", REST, ShotView.THREE_QUARTER),
    body("lawyer-threeq", "lawyer", REST, ShotView.THREE_QUARTER),
    body("dress-threeq", "dress", REST, ShotView.THREE_QUARTER),
    body("goth-threeq", "goth", REST, ShotView.THREE_QUARTER),
    body("beach-front", "beach", REST, ShotView.FRONT),
    body("lawyer-side", "lawyer", REST, ShotView.SIDE),
    body("dress-side", "dress", REST, ShotView.SIDE),
    body("goth-front", "goth", REST, ShotView.FRONT),
    close("face-natural", "dress", REST, ShotView.FACE, Vec3(0f, 1.53f, 0.029f), Vec3(0.262f, 1.555f, 0.59f)),
    close("face-goth", "goth", REST, ShotView.FACE, Vec3(0f, 1.558f, 0.029f), Vec3(0.262f, 1.583f, 0.59f)),
    close("face-lawyer", "lawyer", REST, ShotView.FACE, Vec3(0f, 1.575f, 0.029f), Vec3(0.262f, 1.6f, 0.59f)),
    close("face-beach-shades", "beach", REST, ShotView.FACE, Vec3(0f, 1.542f, 0.029f), Vec3(0.262f, 1.567f, 0.59f)),
    close("face-beach-shadesup", "beach", REST, ShotView.FACE, Vec3(0f, 1.542f, 0.029f), Vec3(0.262f, 1.567f, 0.59f), shadesUp = true),
    close("hand-rest", "dress", REST, ShotView.HAND, Vec3(0.207f, 0.764f, 0.027f), Vec3(0.635f, 0.824f, 0.278f)),
    close("foot-beach", "beach", REST, ShotView.FOOT, Vec3(0.08f, 0.048f, 0.046f), Vec3(0.421f, 0.309f, 0.493f)),
    close("foot-lawyer", "lawyer", REST, ShotView.FOOT, Vec3(0.08f, 0.061f, 0.02f), Vec3(0.421f, 0.322f, 0.467f)),
    close("foot-dress", "dress", REST, ShotView.FOOT, Vec3(0.08f, 0.036f, 0.046f), Vec3(0.421f, 0.297f, 0.493f)),
    close("foot-goth", "goth", REST, ShotView.FOOT, Vec3(0.08f, 0.064f, 0.046f), Vec3(0.421f, 0.325f, 0.493f)),
    close("hand-wave", "beach", "wave", ShotView.HAND, Vec3(-0.266f, 1.808f, 0.137f), Vec3(-0.387f, 1.832f, 0.621f)),
    body("pose-wave-lawyer", "lawyer", "wave", ShotView.THREE_QUARTER),
    body("pose-walk-dress", "dress", "walk", ShotView.THREE_QUARTER),
    body("pose-walk-dress-side", "dress", "walk", ShotView.SIDE),
    body("pose-walk-goth", "goth", "walk", ShotView.THREE_QUARTER),
    body("pose-walk-beach", "beach", "walk", ShotView.SIDE),
)

/** The folder under an example's look images that its shots go into. */
const val SHOTS_FOLDER = "shots"

/** The shot list called [name]. */
fun shotList(name: String): List<ReferenceShot> = when (name) {
    "reference" -> REFERENCE_SHOTS
    else -> error("No shot list $name; known: reference")
}
