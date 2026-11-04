package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

const val MIN_BORDER_POINTS = 3

/** An area of color bounded by [border], a closed curve of anchors that may run across parts. [ink] draws the border as a line. */
@Serializable
data class Region(
    val id: RegionId,
    val border: List<Anchor>,
    val slot: SlotName,
    val ink: Boolean = false,
) {
    init {
        require(border.size >= MIN_BORDER_POINTS) { "Region ${id.value} needs at least $MIN_BORDER_POINTS anchors, not ${border.size}" }
    }

    internal fun mapAnchors(transform: (Anchor) -> Anchor): Region = copy(border = border.map(transform))
}
