package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/**
 * How a painted eye looks, every length a share of the head's own u or v at the eye's middle, so an eye keeps its look on a larger or
 * smaller head: an opening [width] wide and [height] high either side of its middle, its lower lid [lowerLid] of its upper's reach and its
 * front corner narrowed by [frontNarrowing]; an iris [irisSize] across in u, [irisRatio] as high, set [irisForward] toward the front and
 * [irisUp] up from the middle; a pupil [pupilSize] of the iris, round or a [slit]; a highlight [highlightSize] of the iris, shaped as the
 * iris and set [highlightForward] and [highlightUp] of the iris's radius from its middle; a [lid] line round the opening, a flick of
 * [lashes] at its back corner, and a [brow], each an ink line, the brow raised [browRaise] further in v than its place over the opening
 * and turned [browTilt] degrees about its middle, its front end up. The opening alone is turned [openingTilt] degrees about its middle,
 * the lash flick following its back corner; then the whole eye is turned [tilt] degrees in its plane, and its facing [lookOut] degrees
 * outward.
 * Each fill shows its palette slot: [white], [iris], [pupil], [highlight] and [ink] for the lines.
 */
@Serializable
data class EyeLook(
    val width: Float = 0.075f,
    val height: Float = 0.052f,
    val lowerLid: Float = 0.72f,
    val frontNarrowing: Float = 0.18f,
    val tilt: Float = 0f,
    val openingTilt: Float = 0f,
    val lookOut: Float = 0f,
    val irisSize: Float = 0.05f,
    val irisRatio: Float = 0.88f,
    val irisForward: Float = 0.018f,
    val irisUp: Float = 0.006f,
    val pupilSize: Float = 0.58f,
    val slit: Boolean = false,
    val highlightSize: Float = 0.3f,
    val highlightForward: Float = 0.25f,
    val highlightUp: Float = 0.35f,
    val lid: Boolean = true,
    val lashes: Boolean = true,
    val brow: Boolean = true,
    val browRaise: Float = 0f,
    val browTilt: Float = 0f,
    val white: SlotName? = null,
    val iris: SlotName? = null,
    val pupil: SlotName? = null,
    val highlight: SlotName? = null,
    val ink: SlotName? = null,
)

/**
 * An eye painted on a head built as one surface: its middle at [anchor], which names the head, and its [look]. A [mirrored] eye is
 * painted again on the other side of the head's plane, its anchor's v read as 1 - v. With a [front] look, a view that sees the head
 * from the front shows the eye in that look instead, as a front drawing does.
 */
@Serializable
data class PaintedEye(val id: String, val anchor: Anchor, val look: EyeLook = EyeLook(), val mirrored: Boolean = true, val front: EyeLook? = null)
