package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/**
 * A brush stroke in anchor space. Its edge lies [radius], a fraction of the part's axis length, from its anchors. A single anchor is a
 * dab. [softness] is the outer share of the radius it fades across: 0 is a hard edge, 1 a fade across the whole radius.
 */
@Serializable
data class BrushStroke(
    val id: BrushStrokeId,
    val points: List<Anchor>,
    val slot: SlotName,
    val radius: Float,
    val softness: Float = 0f,
) {
    init {
        require(points.isNotEmpty()) { "Brush stroke ${id.value} has no anchors" }
        require(radius > 0f) { "Brush stroke ${id.value} needs a radius, not $radius" }
        require(softness in 0f..1f) { "Brush stroke ${id.value} needs a softness from 0 to 1, not $softness" }
    }

    internal fun mapAnchors(transform: (Anchor) -> Anchor): BrushStroke = copy(points = points.map(transform))
}

/** The color brushed on a part, in the order it was laid down. */
@Serializable
data class Paint(val strokes: List<BrushStroke> = emptyList())
