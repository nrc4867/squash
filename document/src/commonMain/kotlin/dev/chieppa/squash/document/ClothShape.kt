package dev.chieppa.squash.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One stroke of a garment's cloth kept as a vector shape in the (u, v) of a part's chart, [part] a pair's leader, laid as one of
 * [area]'s strokes: it paints that area, taking what it holds from every other, or with [unpaint] takes it from every area. A part whose
 * painted cells are exactly what its shapes lay, in order, reads its cloth's edge from the shapes rather than from the cells (HLD:
 * surface, Garments on the surface).
 */
@Serializable
data class ClothShape(
    val part: PartId,
    val area: GarmentAreaId,
    val outline: ClothOutline,
    val unpaint: Boolean = false,
)

/** A place on a part's chart, in its u and v. */
@Serializable
data class ClothPoint(val u: Float, val v: Float)

/** The outline of a [ClothShape] in its part's u and v; v may run below 0 or past 1 to cross the part's seam. */
@Serializable
sealed interface ClothOutline {
    /** The box [from] to [to] along the part and [fromV] to [toV] round it, the whole way round when they span 1 or more. */
    @Serializable
    @SerialName("box")
    data class Box(val from: Float, val to: Float, val fromV: Float = 0f, val toV: Float = 1f) : ClothOutline {
        init {
            require(from < to && fromV < toV) { "A cloth box runs from ($from, $fromV) to ($to, $toV)" }
        }
    }

    /** The ellipse inside the box [from] to [to] along the part and [fromV] to [toV] round it. */
    @Serializable
    @SerialName("ellipse")
    data class Ellipse(val from: Float, val to: Float, val fromV: Float, val toV: Float) : ClothOutline {
        init {
            require(from < to && fromV < toV) { "A cloth ellipse runs from ($from, $fromV) to ($to, $toV)" }
        }
    }

    /**
     * The closed outline through [points], straight between them, or with [curved] one smooth curve through them, turning sharply only at
     * the indices in [corners].
     */
    @Serializable
    @SerialName("path")
    data class Path(val points: List<ClothPoint>, val curved: Boolean = false, val corners: Set<Int> = emptySet()) : ClothOutline {
        init {
            require(points.size >= LEAST_PATH_POINTS) { "A cloth path passes through ${points.size} places, fewer than $LEAST_PATH_POINTS" }
            require(corners.all { it in points.indices }) { "A cloth path's corner lies past its points" }
        }
    }

    companion object {
        /** The fewest places a path passes through. */
        const val LEAST_PATH_POINTS = 3
    }
}

/** [outline] mirrored across a middle part's middle, v to 1 less v, as a mirrored stroke lays it. */
fun ClothOutline.mirrored(): ClothOutline = when (this) {
    is ClothOutline.Box -> copy(fromV = 1f - toV, toV = 1f - fromV)
    is ClothOutline.Ellipse -> copy(fromV = 1f - toV, toV = 1f - fromV)
    is ClothOutline.Path -> copy(points = points.map { ClothPoint(it.u, 1f - it.v) })
}
