package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

/**
 * One stroke of a body curve, kept as drawn: joined cubic Béziers in the plane of [view] through the character's middle, in
 * character space, a start point then two handles and an end point per segment. In Side a point is body x and y; in Top,
 * body x and z.
 */
@Serializable
data class BodyStroke(val view: View, val points: List<CurvePoint>) {
    init {
        require(view != View.FRONT) { "A body curve is drawn in Side or Top" }
        require(points.size >= POINTS_PER_SEGMENT + 1 && (points.size - 1) % POINTS_PER_SEGMENT == 0) {
            "A body stroke holds 3n + 1 points, not ${points.size}"
        }
    }

    private companion object {
        const val POINTS_PER_SEGMENT = 3
    }
}

/**
 * The body curve of a pose: the [strokes] that bend tail, spine, neck and head, at most one a view, and its [length] as a
 * factor of the body's rest length, below 1 squashing and above 1 stretching. It stays a curve, so solving it again after the
 * character changes reads the same inputs. See HLD: data-model, Poses stay re-editable.
 */
@Serializable
data class BodyCurve(val strokes: List<BodyStroke>, val length: Float = 1f) {
    init {
        require(strokes.map { it.view }.toSet().size == strokes.size) { "A body curve holds one stroke a view" }
        require(length > 0f) { "A body curve's length is a positive factor, not $length" }
    }

    /** The stroke drawn in [view], or null. */
    fun stroke(view: View): BodyStroke? = strokes.firstOrNull { it.view == view }
}
