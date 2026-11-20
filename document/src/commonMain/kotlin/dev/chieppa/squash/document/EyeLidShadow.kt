package dev.chieppa.squash.document

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import kotlinx.serialization.Serializable

/**
 * A shadow the upper lid casts on the white: a band under it following its curve, [depth] of the opening's height deep at its middle, in
 * [slot], or with none the white's colour taken [share] of the way to the skin's shadow tone. Off at a [depth] of 0.
 */
@Serializable
@Doc("A shadow band on the white under the upper lid, following its curve; off while depth is 0.")
data class EyeLidShadow(
    @Doc("How deep the band reaches under the upper lid, a share of the opening's height.") @Range(min = 0.0, max = 1.0)
    val depth: Float = 0f,
    @Doc("The band's palette slot; left out, the white's colour taken share of the way to the skin's shadow tone.")
    val slot: SlotName? = null,
    @Doc("How far the band's colour goes from the white's toward the skin's shadow tone, when it names no slot.") @Range(min = 0.0, max = 1.0)
    val share: Float = 0.45f,
)
