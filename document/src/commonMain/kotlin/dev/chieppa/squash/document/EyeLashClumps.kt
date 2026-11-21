package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable

/** The most lash clumps an eye draws. */
const val MOST_CLUMPS = 5

/**
 * Lashes drawn as [count] strokes growing out of the upper lid's outer part, in the lid mass's slot, in place of the lash flicks: the
 * outermost running about twice [length] of the opening's height to its tip and each further one a little shorter, [width] heights thick either side at the root
 * and tapering to a fine point, their roots spread over [spread] of the lid in from where it turns down to the back corner, each leaving
 * the lid toward that corner and curving up, rounding into the lid where they meet it. Thin and spread wide, they stand as a few
 * separate lashes; thick and close, as clumps.
 */
@Serializable
@Doc("Lashes as tapering strokes growing out of the upper lid's outer part, curving out and up, in the lid mass's slot, in place of the lash flicks.")
data class EyeLashClumps(
    @Doc("How many lashes: 0 none.") @Range(min = 0.0, max = 5.0)
    val count: Int = 0,
    @Doc("The outermost lash's length, a share of the opening's height: its curve runs about twice that to its tip. Each further one is a little shorter.") @Range(min = 0.0, max = 2.0)
    val length: Float = 0.5f,
    @Doc("How much of the upper lid their roots spread over, from where it turns down to the back corner.") @Range(min = 0.05, max = 1.0)
    val spread: Float = 0.35f,
    @Doc("Each lash's thickness either side at its root, a share of the opening's height; it tapers to a fine point. Small and spread wide reads as a few separate lashes.") @Range(min = 0.01, max = 0.3)
    val width: Float = 0.07f,
) {
    init {
        require(count in 0..MOST_CLUMPS) { "An eye has 0 to $MOST_CLUMPS lash clumps, not $count" }
    }

    /** Whether any clump is drawn. */
    val isOn: Boolean get() = count > 0 && length > 0f
}
