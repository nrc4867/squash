package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable

/**
 * An eye's lid drawn as a mass of ink in place of an even line: over the upper lid [weight] of the opening's height thick at its heaviest,
 * from the middle to the back corner, thinning to [inner] of that at the front corner; a flick [flick] of the opening's half width past
 * the back corner, its tip [flickLift] of the opening's height up; under the lower lid [lower] of the upper's weight over [lowerExtent] of
 * the lower lid from the back corner. It fills [slot], the look's ink when left out. Off at a [weight] of 0.
 */
@Serializable
@Doc("The lid drawn as a tapered mass of ink in place of the even lid line; off while weight is 0. Lengths are shares of the opening.")
data class EyeLidMass(
    @Doc("The upper lid's thickness at its heaviest, from its middle to its back corner, a share of the opening's height; 0 draws no mass and keeps the lid line.") @Range(min = 0.0, max = 1.0)
    val weight: Float = 0f,
    @Doc("The upper lid's thickness at its front corner, a share of its heaviest.") @Range(min = 0.0, max = 1.0)
    val inner: Float = 0.15f,
    @Doc("How far the mass runs on past the back corner, tapering to a point, a share of the opening's half width.") @Range(min = 0.0, max = 1.5)
    val flick: Float = 0f,
    @Doc("How far the flick's tip rises above the back corner, a share of the opening's height; below 0 it droops.") @Range(min = -1.0, max = 2.0)
    val flickLift: Float = 0f,
    @Doc("The lower lid's thickness at the back corner, a share of the upper's heaviest: 0 leaves the lower lid open, 1 as heavy as the upper.") @Range(min = 0.0, max = 1.0)
    val lower: Float = 0f,
    @Doc("How much of the lower lid is drawn, from the back corner inward, thinning to nothing at its end: 1 the whole lower lid.") @Range(min = 0.0, max = 1.0)
    val lowerExtent: Float = 1f,
    @Doc("The mass's palette slot; the look's ink when left out.")
    val slot: SlotName? = null,
) {
    /** Whether a mass is drawn. */
    val isOn: Boolean get() = weight > 0f
}
