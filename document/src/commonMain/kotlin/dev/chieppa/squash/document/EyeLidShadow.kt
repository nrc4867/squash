package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable

/**
 * A shadow the upper lid casts on the white: a band under it following its curve, [depth] of the opening's height deep at its middle, in
 * [slot], or with none the white's colour taken [share] of the way to a cool grey lilac. It thins to nothing toward the corners. Off at a
 * [depth] of 0.
 */
@Serializable
@Doc("A shadow band on the white under the upper lid, following its curve and thinning toward the corners; off while depth is 0.")
data class EyeLidShadow(
    @Doc("How deep the band reaches under the upper lid's middle, a share of the opening's height: about 0.15 to 0.2 hugs the lid.") @Range(min = 0.0, max = 0.5)
    val depth: Float = 0f,
    @Doc("The band's palette slot; left out, the white's colour taken share of the way to a cool grey lilac.")
    val slot: SlotName? = null,
    @Doc("How far the band's colour goes from the white's toward a cool grey lilac, when it names no slot.") @Range(min = 0.0, max = 1.0)
    val share: Float = 0.4f,
)
