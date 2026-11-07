package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a seam stroke does to the weights under it. */
@Serializable
enum class SeamBrush {
    /** Gives the weight to the bone on the stroke's [SeamStroke.toward] side. */
    @SerialName("paint") PAINT,

    /** Hands the weight back to what the seam's band guesses. */
    @SerialName("erase") ERASE,

    /** Evens the weight out across the brush. */
    @SerialName("smooth") SMOOTH,
}

/** A side of a seam: the part a socket is on, or the part sitting on it. */
@Serializable
enum class SeamSide {
    @SerialName("parent") PARENT,
    @SerialName("child") CHILD,
}

/**
 * A stroke painted on the seam where the part holding it meets its parent, in anchor space, replayed on the rig's guessed band.
 * A [SeamBrush.PAINT] stroke gives weight to the seam's bone on its [toward] side: the parent's bone carrying the socket or the
 * part's own first bone. Its edge lies [radius], a fraction of the holding part's axis length, from its anchors, and it acts at
 * [strength] from 0 to 1 at its middle, fading to nothing at its edge. With [dabs] each anchor is a brush of its own, with no
 * line between them. See HLD: data-model, The rig.
 */
@Serializable
data class SeamStroke(
    val id: SeamStrokeId,
    val points: List<Anchor>,
    val brush: SeamBrush = SeamBrush.PAINT,
    val toward: SeamSide? = null,
    val radius: Float,
    val strength: Float = 1f,
    val dabs: Boolean = false,
) {
    init {
        require(points.isNotEmpty()) { "Seam stroke ${id.value} has no anchors" }
        require(radius > 0f) { "Seam stroke ${id.value} needs a radius, not $radius" }
        require(strength in 0f..1f) { "Seam stroke ${id.value} needs a strength from 0 to 1, not $strength" }
        require((brush == SeamBrush.PAINT) == (toward != null)) { "Seam stroke ${id.value}: a paint stroke names its side, and only it" }
    }

    internal fun mapAnchors(transform: (Anchor) -> Anchor): SeamStroke = copy(points = points.map(transform))
}
