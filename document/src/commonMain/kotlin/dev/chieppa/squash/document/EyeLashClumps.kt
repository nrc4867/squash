package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable

/** The most lash clumps an eye draws. */
const val MOST_CLUMPS = 5

/**
 * Lashes drawn as [count] thick wedges growing out of the upper lid, in the lid mass's slot: the outermost [length] of the opening's height
 * long and each further one a little shorter, their roots spread over [spread] of the lid from the back corner, each swept toward it.
 */
@Serializable
@Doc("Lashes as thick wedges growing out of the upper lid near the back corner, in the lid mass's slot, beside the lash flicks.")
data class EyeLashClumps(
    @Doc("How many wedges: 0 none.") @Range(min = 0.0, max = 5.0)
    val count: Int = 0,
    @Doc("The outermost wedge's length, a share of the opening's height; each further one is a little shorter.") @Range(min = 0.0, max = 2.0)
    val length: Float = 0.5f,
    @Doc("How much of the upper lid their roots spread over, from the back corner.") @Range(min = 0.05, max = 1.0)
    val spread: Float = 0.35f,
) {
    init {
        require(count in 0..MOST_CLUMPS) { "An eye has 0 to $MOST_CLUMPS lash clumps, not $count" }
    }

    /** Whether any clump is drawn. */
    val isOn: Boolean get() = count > 0 && length > 0f
}
