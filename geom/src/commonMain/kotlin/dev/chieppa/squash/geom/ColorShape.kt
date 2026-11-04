package dev.chieppa.squash.geom

/** A place on a patch, in its own u and v. */
data class SurfaceUv(val u: Float, val v: Float)

/** How a border that holds only part of a closed curve on this patch closes there. */
enum class BorderClosing {
    /** Its ends join: it is the whole curve, or a stretch that the curve carries round from its last point to its first. */
    ON_ITSELF,

    /** Its ends run off the patch's first ring and join past it, so the shape holds what lies toward that ring. */
    PAST_FIRST_RING,

    /** Its ends run off the patch's last ring and join past it, so the shape holds what lies toward that ring. */
    PAST_LAST_RING,
}

/** A shape in a patch's u and v space, which is what a color section is. */
sealed interface ColorShape {
    /** Whether the shape's edges depend on how finely it is flattened. */
    val isCurved: Boolean

    /**
     * A brush stroke: everything within [radius], in the surface's own units, of the curve through [points]. One point is a dab.
     * [softness] is the outer share of the radius it fades across, from 0, a hard edge, to 1, a fade from the curve itself. [openStart]
     * and [openEnd] say the stroke goes on past that end onto another surface, so a fade keeps its strength there instead of rounding off.
     */
    data class Brush(
        val points: List<SurfaceUv>,
        val radius: Float,
        val softness: Float = 0f,
        val openStart: Boolean = false,
        val openEnd: Boolean = false,
    ) : ColorShape {
        init {
            require(softness in 0f..1f) { "A brush's softness lies from 0 to 1, not $softness" }
        }

        override val isCurved: Boolean get() = true
    }

    /**
     * What a closed curve through [points] holds. A curve that goes once round a closed patch, enclosing nothing on it, holds
     * the side toward the patch's last ring.
     */
    data class Border(val points: List<SurfaceUv>, val closing: BorderClosing = BorderClosing.ON_ITSELF) : ColorShape {
        override val isCurved: Boolean get() = true
    }

    /** A template's area: the rectangle from [uStart] to [uEnd] and [vStart] to [vEnd]. A v span of 1 goes all the way round. */
    data class Rectangle(val uStart: Float, val uEnd: Float, val vStart: Float, val vEnd: Float) : ColorShape {
        override val isCurved: Boolean get() = false
    }

    /** All of the patch. */
    data object WholePatch : ColorShape {
        override val isCurved: Boolean get() = false
    }
}
