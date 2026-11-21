package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable

/** The most marks a lid carries. */
const val MOST_LID_MARKS = 6

/**
 * How an eye's lids look as they close, and the upper lid's own paint. Closed, the lids meet on a line crossing the opening's middle
 * [closedAt] of its height up, its middle [closedBend] heights above a straight line through its ends, turned [closedTilt] degrees with
 * its front end up, drawn in toward the corners. The upper lid's skin shows [slot] where the lid covers the opening, and over the skin
 * [above] heights above the open lid, fading over [fade] heights up from the lash line into [fadeTo] or the skin; the [marks] lie on the
 * lid and move with it. With no slot the lids are the head's paint the eye sits in, their marks still drawn.
 */
@Serializable
@Doc("How an eye's lids meet when closed and how the upper lid is painted: its own colour, a fade up from the lash line and marks that move with it. Lengths are shares of the opening's height.")
data class EyeLids(
    @Doc("Where the closed lids meet at the opening's middle, in its heights up from the middle: 1 the open upper lid's top, -lowerLid the lower lid's bottom.") @Range(min = -2.0, max = 1.0)
    val closedAt: Float = DEFAULT_CLOSED_AT,
    @Doc("How far the closed line's middle stands above a straight line through its ends, in heights: above 0 it arches up as a happy closed eye does, below 0 it sags, 0 straight.") @Range(min = -1.0, max = 1.0)
    val closedBend: Float = 0f,
    @Doc("Degrees the closed line turns, its front end up.") @Range(min = -45.0, max = 45.0)
    val closedTilt: Float = 0f,
    @Doc("The upper lid's own colour, an eyeshadow or a darker lid, shown where it covers the opening and, with above, over the skin above it; left out, the lid takes the colour of the face's painted area the eye sits in, the skin where none is, and fade and above do nothing.")
    val slot: SlotName? = null,
    @Doc("The colour the lid's own colour fades into going up from the lash line; left out, it fades into the skin.")
    val fadeTo: SlotName? = null,
    @Doc("How far up from the lash line the lid's colour fades out, in heights, fading toward the corners too; 0 an even colour.") @Range(min = 0.0, max = 3.0)
    val fade: Float = 0f,
    @Doc("How far the lid's colour reaches over the skin above the open lid at its middle, thinning to the corners, in heights; 0 only where the lid covers the opening.") @Range(min = 0.0, max = 3.0)
    val above: Float = 0f,
    @Doc("Marks painted on the upper lid, a liner wing or a pattern, each moving with the lid as it closes.")
    val marks: List<EyeLidMark> = emptyList(),
) {
    init {
        require(marks.size <= MOST_LID_MARKS) { "A lid carries at most $MOST_LID_MARKS marks, not ${marks.size}" }
    }

    /** Whether the lid shows a colour of its own. */
    val isPainted: Boolean get() = slot != null

    companion object {
        /** Where closed lids meet unless told, a little below the middle, since the upper lid travels further. */
        const val DEFAULT_CLOSED_AT = -0.25f
    }
}

/**
 * A mark on an eye's upper lid, moving with it: rooted [along] the lid from its back corner (-1) to its front (1) and [above] heights
 * over the lash line there, a stroke reaching [out] half widths toward the back corner and [up] heights to its tip, [width] heights thick
 * at its root and tapering to a point, its way up bent by [bend]; a round dot where it reaches nowhere. It fills [slot], the look's ink
 * when left out.
 */
@Serializable
@Doc("A mark on the upper lid, a liner wing or one piece of a pattern: a stroke from a root on the lid tapering to a point, moving with the lid as it closes and kept off the open eye.")
data class EyeLidMark(
    @Doc("Where its root sits along the lid: -1 the back corner, 1 the front, beyond them past the corners.") @Range(min = -1.5, max = 1.5)
    val along: Float = -1f,
    @Doc("How far above the lash line its root sits, in the opening's heights.") @Range(min = -0.5, max = 3.0)
    val above: Float = 0f,
    @Doc("How far its tip runs toward the back corner from its root, in the opening's half widths; below 0 toward the front.") @Range(min = -2.0, max = 2.0)
    val out: Float = 0.4f,
    @Doc("How far its tip rises above its root, in the opening's heights.") @Range(min = -2.0, max = 2.0)
    val up: Float = 0.3f,
    @Doc("Its thickness either side at its root, in the opening's heights; it tapers to a point, a round dot where it reaches nowhere.") @Range(min = 0.01, max = 1.0)
    val width: Float = 0.1f,
    @Doc("How much it runs out first and lifts at its tip: 0 straight, 1 a curve.") @Range(min = 0.0, max = 1.0)
    val bend: Float = 0.5f,
    @Doc("Its palette slot; the look's ink when left out.")
    val slot: SlotName? = null,
)
