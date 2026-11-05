package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/** How far around its place a comb reaches, in u and v, when it does not say. */
const val DEFAULT_COMB_REACH = 0.12f

/** A combed direction at a surface point, in u and v, which holds at its place and fades to nothing at [reach] from it. */
@Serializable
data class GrainComb(
    val patch: String = BODY_PATCH,
    val u: Float,
    val v: Float,
    val du: Float,
    val dv: Float,
    val reach: Float = DEFAULT_COMB_REACH,
)

/** A part's direction field in anchor space: what its template gives, except where [combs] override it. */
@Serializable
data class Grain(val combs: List<GrainComb> = emptyList())

/**
 * How a part's fur tufts stand on the body: with room for [spacingPixels] and [lengthPixels] long, both as a view of 280 pixels to a body unit
 * shows them. See HLD: look, Marks.
 */
@Serializable
data class FurRule(val spacingPixels: Float = DEFAULT_SPACING, val lengthPixels: Float = DEFAULT_LENGTH) {
    init {
        require(spacingPixels > 0f) { "Fur spacing is above 0 pixels, not $spacingPixels" }
        require(lengthPixels > 0f) { "Fur length is above 0 pixels, not $lengthPixels" }
    }

    private companion object {
        const val DEFAULT_SPACING = 12f
        const val DEFAULT_LENGTH = 9f
    }
}
