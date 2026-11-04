package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/** A brush stroke in anchor space. Its edge lies [radius], a fraction of the part's axis length, from its anchors. A single anchor is a dab. */
@Serializable
data class BrushStroke(
    val id: BrushStrokeId,
    val points: List<Anchor>,
    val slot: SlotName,
    val radius: Float,
) {
    init {
        require(points.isNotEmpty()) { "Brush stroke ${id.value} has no anchors" }
        require(radius > 0f) { "Brush stroke ${id.value} needs a radius, not $radius" }
    }

    internal fun mapAnchors(transform: (Anchor) -> Anchor): BrushStroke = copy(points = points.map(transform))
}

/** The color brushed on a part, in the order it was laid down. */
@Serializable
data class Paint(val strokes: List<BrushStroke> = emptyList())
