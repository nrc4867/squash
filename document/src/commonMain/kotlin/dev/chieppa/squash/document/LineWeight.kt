package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/** The fewest anchors a line weight holds: one is a dab. */
const val MIN_WEIGHT_POINTS = 1

/**
 * Weight added to the lines where they cross a stretch of the surface: within [radius] of [points], a fraction of the part's axis
 * length, a line's rendered weight is multiplied by 1 plus [amount]. It lies on the surface, so it holds from any angle.
 * See HLD: look, Lines.
 */
@Serializable
data class LineWeight(
    val id: LineWeightId,
    val points: List<Anchor>,
    val radius: Float,
    val amount: Float,
) {
    init {
        require(points.size >= MIN_WEIGHT_POINTS) { "Line weight ${id.value} needs at least $MIN_WEIGHT_POINTS anchors, not ${points.size}" }
        require(radius > 0f) { "Line weight ${id.value} needs a radius, not $radius" }
        require(amount > -1f) { "Line weight ${id.value} cannot take a line below nothing: $amount" }
    }

    internal fun mapAnchors(transform: (Anchor) -> Anchor): LineWeight = copy(points = points.map(transform))
}

/**
 * What sets the weight of a character's lines before any [LineWeight]: [silhouette] and [interior] in pixels, and [taper], how much
 * of a mark's length its ends narrow over, from 0 to 1 when they meet in its middle.
 */
@Serializable
data class LineRules(
    val silhouette: Float = DEFAULT_SILHOUETTE,
    val interior: Float = DEFAULT_INTERIOR,
    val taper: Float = DEFAULT_TAPER,
) {
    init {
        require(silhouette >= 0f) { "A silhouette's weight is not negative, not $silhouette" }
        require(interior >= 0f) { "An interior line's weight is not negative, not $interior" }
        require(taper in 0f..1f) { "A taper runs from 0 to 1, not $taper" }
    }

    companion object {
        const val DEFAULT_SILHOUETTE = 4f
        const val DEFAULT_INTERIOR = 2f
        const val DEFAULT_TAPER = 0.55f
    }
}
