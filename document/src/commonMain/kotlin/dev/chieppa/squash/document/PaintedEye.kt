package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable

/** What a look's lengths are shares of, as the schema and reference say it. */
private const val UNITS = "Lengths are shares of the head's own coordinates at the eye's middle: across (width, irisSize, irisForward) of the one that " +
    "runs toward the face's front, down (height, irisUp, browRaise) of the one that runs down it. On an upright head (head-round) across is v, " +
    "once round the head at the eye, and down is u, the head's length from chin to crown, so a unit across spans about 2.2 times a unit down; on " +
    "a head resting forward (head, head-bird) across is u, along the head, and down is v, once round it. face-and-clothes.md gives both in body units for each head template."

/**
 * How a painted eye looks, every length a share of the head's own u or v at the eye's middle, so an eye keeps its look on a larger or
 * smaller head: an opening [width] wide and [height] high either side of its middle, its lower lid [lowerLid] of its upper's reach and its
 * back corner narrowed by [frontNarrowing]; an iris [irisSize] across, [irisRatio] as high, set [irisForward] toward the front and
 * [irisUp] up from the middle; a pupil [pupilSize] of the iris, round or a [slit]; a highlight [highlightSize] of the iris, shaped as the
 * iris and set [highlightForward] and [highlightUp] of the iris's radius from its middle; a [lid] line round the opening, [lashes] flicks
 * along its upper lid from its back corner, and a [brow] [browWidth] times the line weight, each an ink line, the brow raised [browRaise]
 * further than its place over the opening and turned [browTilt] degrees about its middle, its front end up. The opening alone is turned
 * [openingTilt] degrees about its middle, the lashes following its back corner; then the whole eye is turned [tilt] degrees in its plane,
 * and its facing [lookOut] degrees outward.
 * Each fill shows its palette slot: [white], [iris], [pupil], [highlight] and [ink] for the lines. The upper lid runs [topFlatness] flat
 * and the back corner [outerCorner] sharp. Off by default: a [lidMass] in place of the lid line, [lashClumps], an [irisShade] and a
 * [lidShadow] on the white.
 */
@Serializable
@Doc("How a painted eye looks. $UNITS")
data class EyeLook(
    @Doc("Half the opening's width, from its middle to each corner, in units across.") @Range(min = 0.005, max = 0.4)
    val width: Float = 0.075f,
    @Doc("How far the upper lid rises above the opening's middle, in units down.") @Range(min = 0.005, max = 0.4)
    val height: Float = 0.052f,
    @Doc("How far the lower lid reaches below the middle, a share of height: 0 a flat lower edge, 1 as deep as the top.") @Range(min = 0.0, max = 2.0)
    val lowerLid: Float = 0.72f,
    @Doc("How much the opening narrows toward its back corner and widens toward its front, a share of its height: 0 an even almond.") @Range(min = -0.9, max = 0.9)
    val frontNarrowing: Float = 0.18f,
    @Doc("How flat the upper lid runs, closing steeply at the corners: 0 the lower lid's round curve, 1 nearly straight, so with frontNarrowing or openingTilt it runs slanted.") @Range(min = 0.0, max = 1.0)
    val topFlatness: Float = 0f,
    @Doc("How sharp the back corner is where the lids meet: 0 round, 1 a point.") @Range(min = 0.0, max = 1.0)
    val outerCorner: Float = 0f,
    @Doc("Degrees the whole eye turns in its plane about its middle, its front end up.") @Range(min = -90.0, max = 90.0)
    val tilt: Float = 0f,
    @Doc("Degrees the opening alone turns about its middle, its front end up, before tilt; the iris stays level.") @Range(min = -90.0, max = 90.0)
    val openingTilt: Float = 0f,
    @Doc("Degrees the eye's facing turns outward, away from the body's middle plane.") @Range(min = -60.0, max = 60.0)
    val lookOut: Float = 0f,
    @Doc("Half the iris's width, in units across.") @Range(min = 0.0, max = 0.4)
    val irisSize: Float = 0.05f,
    @Doc("The iris's half height as a share of irisSize, read in units down: its height is irisSize times irisRatio units down. A round iris on an undrawn head-round takes about 2.2, on a head resting forward about 0.4.") @Range(min = 0.05, max = 6.0)
    val irisRatio: Float = 0.88f,
    @Doc("How far the iris's middle sits toward the front corner from the opening's middle, in units across.") @Range(min = -0.3, max = 0.3)
    val irisForward: Float = 0.018f,
    @Doc("How far the iris's middle sits up from the opening's middle, in units down.") @Range(min = -0.3, max = 0.3)
    val irisUp: Float = 0.006f,
    @Doc("The pupil's size, a share of the iris's.") @Range(min = 0.0, max = 1.0)
    val pupilSize: Float = 0.58f,
    @Doc("Whether the pupil is an upright slit, a fifth of the iris's width, in place of a round one.")
    val slit: Boolean = false,
    @Doc("The highlight's size, a share of the iris's.") @Range(min = 0.0, max = 1.0)
    val highlightSize: Float = 0.3f,
    @Doc("How far the highlight's middle sits toward the front from the iris's middle, a share of irisSize.") @Range(min = -1.0, max = 1.0)
    val highlightForward: Float = 0.25f,
    @Doc("How far the highlight's middle sits up from the iris's middle, a share of the iris's half height.") @Range(min = -1.0, max = 1.0)
    val highlightUp: Float = 0.35f,
    @Doc("Whether a lid line is inked round the opening.")
    val lid: Boolean = true,
    @Doc("How many lash flicks rise from the upper lid: 0 none, 1 at the back corner, 2 and 3 spread forward along the lid from there.") @Range(min = 0.0, max = 3.0)
    @Serializable(with = LashCountSerializer::class)
    val lashes: Int = 1,
    @Doc("Whether a brow is inked over the opening.")
    val brow: Boolean = true,
    @Doc("How far the brow rises above its place over the opening, in units down (about 2 heights above the middle).") @Range(min = -0.2, max = 0.4)
    val browRaise: Float = 0f,
    @Doc("Degrees the brow turns about its middle, its front end up.") @Range(min = -90.0, max = 90.0)
    val browTilt: Float = 0f,
    @Doc("The brow's thickness, a multiple of the line weight, thinning toward its ends above 1: 1 an ink line, 3 a thick brow.") @Range(min = 0.25, max = 5.0)
    val browWidth: Float = 1f,
    @Doc("The white's palette slot; none fills no white.")
    val white: SlotName? = null,
    @Doc("The iris's palette slot.")
    val iris: SlotName? = null,
    @Doc("The pupil's palette slot.")
    val pupil: SlotName? = null,
    @Doc("The highlight's palette slot.")
    val highlight: SlotName? = null,
    @Doc("The lines' palette slot; the ink's when left out.")
    val ink: SlotName? = null,
    @Doc("The lid drawn as a tapered mass of ink with a flick past the back corner and a lighter lower lid, in place of the even lid line.")
    val lidMass: EyeLidMass = EyeLidMass(),
    @Doc("Lashes as thick wedges growing out of the upper lid near the back corner.")
    val lashClumps: EyeLashClumps = EyeLashClumps(),
    @Doc("The iris in cel tones: a darker top, a lighter crescent along its bottom and a darker rim.")
    val irisShade: EyeIrisShade = EyeIrisShade(),
    @Doc("A shadow band on the white under the upper lid.")
    val lidShadow: EyeLidShadow = EyeLidShadow(),
) {
    init {
        require(lashes in 0..MOST_LASHES) { "An eye has 0 to $MOST_LASHES lashes, not $lashes" }
    }
}

/**
 * An eye painted on a head: its middle at [anchor], which names the head, and its [look]. A [mirrored] eye is
 * painted again on the other side of the head's plane, its anchor's v read as 1 - v. With a [front] look, a view that sees the head
 * from the front shows the eye [fromFront] of the way from its look to that one (see [shownFront]). Its iris, pupil and highlight look
 * [gazeForward] of the opening's half width toward the head's front and [gazeUp] of its half height up, clipped by the opening.
 * A [flat] eye faces the way its head faces, turned outward by its look-out, and is laid on the head as a decal projected from there, so
 * it reads as drawn when seen from its facing however round the head is; otherwise it faces the surface's normal at its middle and
 * follows the head's curve round from there.
 */
@Serializable
data class PaintedEye(
    val id: String,
    val anchor: Anchor,
    val look: EyeLook = EyeLook(),
    val mirrored: Boolean = true,
    val front: EyeLook? = null,
    val fromFront: Float = 1f,
    val gazeForward: Float = 0f,
    val gazeUp: Float = 0f,
    val flat: Boolean = false,
) {
    init {
        require(fromFront in 0f..1f) { "From front is between 0 and 1, not $fromFront" }
    }
}
