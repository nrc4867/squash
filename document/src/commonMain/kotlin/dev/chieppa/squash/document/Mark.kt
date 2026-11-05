package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val MIN_MARK_POINTS = 2

/** The one anchor a fur stroke needs: its root. */
const val MIN_FUR_POINTS = 1

/**
 * What a mark draws. A [LINE] is an inked polyline along its anchors. A [FUR] stroke is a tapered wedge from its first anchor, the
 * root, along the part's grain, filled in the slot under it. A [FLOATING] line is inked from its first anchor, the root, on through its flight off the
 * surface. See HLD: look, Marks.
 */
@Serializable
enum class MarkKind(val minPoints: Int) {
    @SerialName("line") LINE(MIN_MARK_POINTS),
    @SerialName("fur") FUR(MIN_FUR_POINTS),
    @SerialName("floating") FLOATING(MIN_FUR_POINTS),
}

/** A place a floating mark passes through, in body units from its root: [along] the surface as u grows, [across] it as v grows, and [out] of it. */
@Serializable
data class MarkFlight(val along: Float, val across: Float, val out: Float)

/** A share of the line rule's weight at a place along a mark, 0 to 1 from its start. */
@Serializable
data class WidthStop(val at: Float, val scale: Float) {
    init {
        require(at in 0f..1f) { "A width stop lies 0 to 1 along its mark, not at $at" }
        require(scale >= 0f) { "A width stop's scale is not negative, not $scale" }
    }
}

/** How a mark's weight runs along it, between [stops] in order. With no stops it keeps the line rule's weight. */
@Serializable
data class MarkWidth(val stops: List<WidthStop> = emptyList()) {
    init {
        require(stops.zipWithNext().all { (a, b) -> a.at <= b.at }) { "Width stops run along the mark in order" }
    }
}

/** A mark's shape for one camera bin. */
@Serializable
data class MarkVersion(val points: List<Anchor>, val width: MarkWidth = MarkWidth()) {
    init {
        require(points.size >= MIN_MARK_POINTS) { "A mark version needs at least $MIN_MARK_POINTS anchors, not ${points.size}" }
    }
}

/**
 * A line on the surface: a polyline of anchors with a width along it. [versions] replace its shape in the camera bins they
 * name, and [points] and [width] hold in every other. A [cameraLocked] mark sits where the light and camera put it. A [mirrored] mark on a part in the
 * middle of the body shows on both sides of the middle. [slot] is its color, the ink's if none. See HLD: look, Marks.
 */
@Serializable
data class Mark(
    val id: MarkId,
    val points: List<Anchor>,
    val width: MarkWidth = MarkWidth(),
    val ink: Boolean = true,
    val cameraLocked: Boolean = false,
    val versions: Map<CameraBin, MarkVersion> = emptyMap(),
    val kind: MarkKind = MarkKind.LINE,
    val flight: List<MarkFlight> = emptyList(),
    val mirrored: Boolean = false,
    val slot: SlotName? = null,
) {
    init {
        require(kind != MarkKind.FLOATING || flight.isNotEmpty()) { "Floating mark ${id.value} goes nowhere" }
        require(points.size >= kind.minPoints) { "Mark ${id.value} needs at least ${kind.minPoints} anchors, not ${points.size}" }
    }

    internal fun mapAnchors(transform: (Anchor) -> Anchor): Mark = copy(
        points = points.map(transform),
        versions = versions.mapValues { (_, version) -> version.copy(points = version.points.map(transform)) },
    )
}
