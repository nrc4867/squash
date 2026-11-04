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
 * How a part's fur strokes show. A stroke shows where its root's facing, as the cosine between normal and view, is under [showNear],
 * at least [spacingPixels] on screen from the next, and [lengthPixels] long, less on a view too small to hold that. See HLD: look, Marks.
 */
@Serializable
data class FurRule(val showNear: Float = DEFAULT_SHOW_NEAR, val spacingPixels: Float = DEFAULT_SPACING, val lengthPixels: Float = DEFAULT_LENGTH) {
    init {
        require(showNear in 0f..1f) { "Show near is a share of the way from the edge, not $showNear" }
        require(spacingPixels > 0f) { "Fur spacing is above 0 pixels, not $spacingPixels" }
        require(lengthPixels > 0f) { "Fur length is above 0 pixels, not $lengthPixels" }
    }

    private companion object {
        const val DEFAULT_SHOW_NEAR = 0.3f
        const val DEFAULT_SPACING = 12f
        const val DEFAULT_LENGTH = 9f
    }
}
