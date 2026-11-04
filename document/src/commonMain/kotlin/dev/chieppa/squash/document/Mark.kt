package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

const val MIN_MARK_POINTS = 2

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

/** The prefix of every fur stroke's id. */
const val FUR_MARK_PREFIX = "fur-"

/** Whether this mark is a fur stroke. */
val MarkId.isFur: Boolean get() = value.startsWith(FUR_MARK_PREFIX)

/** A mark's shape for one camera bin. */
@Serializable
data class MarkVersion(val points: List<Anchor>, val width: MarkWidth = MarkWidth()) {
    init {
        require(points.size >= MIN_MARK_POINTS) { "A mark version needs at least $MIN_MARK_POINTS anchors, not ${points.size}" }
    }
}

/**
 * A line on the surface: a polyline of anchors with a width along it. [versions] replace its shape in the camera bins they
 * name, and [points] and [width] hold in every other. A [cameraLocked] mark sits where the light and camera put it.
 * See HLD: look, Marks.
 */
@Serializable
data class Mark(
    val id: MarkId,
    val points: List<Anchor>,
    val width: MarkWidth = MarkWidth(),
    val ink: Boolean = true,
    val cameraLocked: Boolean = false,
    val versions: Map<CameraBin, MarkVersion> = emptyMap(),
) {
    init {
        require(points.size >= MIN_MARK_POINTS) { "Mark ${id.value} needs at least $MIN_MARK_POINTS anchors, not ${points.size}" }
    }

    internal fun mapAnchors(transform: (Anchor) -> Anchor): Mark = copy(
        points = points.map(transform),
        versions = versions.mapValues { (_, version) -> version.copy(points = version.points.map(transform)) },
    )
}
