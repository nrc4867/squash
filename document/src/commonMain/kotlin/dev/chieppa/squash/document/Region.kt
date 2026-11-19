package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

const val MIN_BORDER_POINTS = 3

/**
 * An area of color bounded by [border], a closed curve of anchors that may run across parts, smooth through each anchor but those
 * in [corners], indices into [border]. On the one surface the border is the smooth curve through its anchors unless [curved] is off, when it runs straight between them. [ink] draws
 * the border as a line. A [mirrored] region on a part in the middle of the body shows on both sides of the middle, and on a garment
 * shows on the other side of the body too.
 */
@Serializable
data class Region(
    val id: RegionId,
    val border: List<Anchor>,
    val slot: SlotName,
    val ink: Boolean = false,
    val corners: Set<Int> = emptySet(),
    val curved: Boolean = true,
    val mirrored: Boolean = false,
) {
    init {
        require(border.size >= MIN_BORDER_POINTS) { "Region ${id.value} needs at least $MIN_BORDER_POINTS anchors, not ${border.size}" }
        require(corners.all { it in border.indices }) { "Region ${id.value} has corners $corners outside its ${border.size} anchors" }
    }

    internal fun mapAnchors(transform: (Anchor) -> Anchor): Region = copy(border = border.map(transform))
}
