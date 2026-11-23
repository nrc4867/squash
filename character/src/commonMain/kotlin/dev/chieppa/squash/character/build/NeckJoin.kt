package dev.chieppa.squash.character.build

import dev.chieppa.squash.document.NeckStyle
import kotlin.math.roundToInt

/**
 * What a head's join to its neck takes from its character's [NeckStyle], which reaches the head as its [JoinParams.NECK] placement
 * value: [blendShare], the share of its default blend it joins by; [inked], whether its join is inked where neither the part nor its
 * template says; [shade], how far below its underside the shade under its chin reaches, as a share of the head's least extent; and
 * [seamShare], the share of its seam band, over which its vertices near the socket take the neck's bone, it keeps: a head on a defined
 * neck turns whole, the bend lying in the neck under it.
 * See HLD: look, Necks.
 */
enum class NeckJoin(val style: NeckStyle, val blendShare: Float, val inked: Boolean?, val shade: Float, val seamShare: Float) {
    BLENDED(NeckStyle.BLENDED, 1f, null, 0f, 1f),
    BETWEEN(NeckStyle.BETWEEN, 0.4f, false, 0.1f, 0.5f),
    DEFINED(NeckStyle.DEFINED, 0.08f, true, 0.15f, 0f),
    ;

    /** The placement value a head carries for this style. */
    val value: Float get() = ordinal.toFloat()

    companion object {
        /** The style [style] joins by. */
        fun of(style: NeckStyle): NeckJoin = entries.first { it.style == style }

        /** The style a part's [placement] says its join has, [BLENDED] on a part that says none. */
        fun of(placement: Map<String, Float>): NeckJoin = placement[JoinParams.NECK]?.let { entries.getOrNull(it.roundToInt()) } ?: BLENDED
    }
}
