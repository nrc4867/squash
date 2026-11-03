package dev.chieppa.squash.document

import kotlinx.serialization.Serializable

@Serializable
data class CurvePoint(val x: Float, val y: Float)

/** Joined cubic Béziers in a part's own axes: a start point, then two handles and an end point per segment. */
@Serializable
data class OutlineCurve(val points: List<CurvePoint>) {
    init {
        require(points.size >= POINTS_PER_SEGMENT + 1 && (points.size - 1) % POINTS_PER_SEGMENT == 0) {
            "An outline holds 3n + 1 points, not ${points.size}"
        }
    }

    private companion object {
        const val POINTS_PER_SEGMENT = 3
    }
}
