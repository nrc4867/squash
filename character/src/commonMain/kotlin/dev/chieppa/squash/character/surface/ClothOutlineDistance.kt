package dev.chieppa.squash.character.surface

import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.document.ClothOutline
import dev.chieppa.squash.geom.BorderClosing
import dev.chieppa.squash.geom.ColorShape
import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.UvShapeDistance
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sqrt

/** How far past an end of its part a box reaching within half a row of that end is read to run, in u, so its cloth goes on over the end. */
private const val PAST_END = 1f

/**
 * [outline], a shape in a part's chart, read as a signed distance at any (u, v): positive inside, negative outside, a stretch of u
 * counting uScale times and of v vScale times its length, so with each set to how long a unit of it is there the distance is in body
 * units, and with the chart's rows and columns in cells. v wraps once round. A box reaching within half a row of an end of the part
 * runs on past it, as the cells it paints hold to that end.
 */
class ClothOutlineDistance(outline: ClothOutline) {
    private val box: ClothOutline.Box? = (outline as? ClothOutline.Box)?.let { b ->
        val half = 0.5f / CHART_ROWS
        b.copy(from = if (b.from <= half) -PAST_END else b.from, to = if (b.to >= 1f - half) 1f + PAST_END else b.to)
    }
    private val ellipse = outline as? ClothOutline.Ellipse
    private val distance: UvShapeDistance = UvShapeDistance(
        when (outline) {
            is ClothOutline.Box -> checkNotNull(box).let { ColorShape.Rectangle(it.from, it.to, it.fromV, it.toV) }
            is ClothOutline.Ellipse -> ColorShape.WholePatch
            is ClothOutline.Path -> ColorShape.Border(outline.points.map { SurfaceUv(it.u, it.v) }, BorderClosing.ON_ITSELF, outline.corners, outline.curved)
        },
        closedV = true,
    )

    /** The box in u and v the shape lies in, its v unwrapped from its first point; a box round the whole way spans 1 or more. */
    private val bounds: FloatArray = when (outline) {
        is ClothOutline.Box -> checkNotNull(box).let { floatArrayOf(it.from, it.to, it.fromV, it.toV) }
        is ClothOutline.Ellipse -> floatArrayOf(outline.from, outline.to, outline.fromV, outline.toV)
        is ClothOutline.Path -> distance.edge().let { edge ->
            floatArrayOf(edge.minOf { it.u }, edge.maxOf { it.u }, edge.minOf { it.v }, edge.maxOf { it.v })
        }
    }

    /** Where along the part the shape starts, in u. */
    val lowU: Float get() = bounds[0]

    /** Where along the part the shape ends, in u. */
    val highU: Float get() = bounds[1]

    /** The signed distance at ([u], [v]). */
    fun at(u: Float, v: Float, uScale: Float, vScale: Float): Float {
        val e = ellipse ?: return distance.at(u, v, uScale, vScale)
        return ellipseDistance(e, u, v, uScale, vScale)
    }

    /** How far outside the shape's box ([u], [v]) lies, measured as [at] measures: never more than the shape's own distance outside. */
    fun outsideBounds(u: Float, v: Float, uScale: Float, vScale: Float): Float {
        val du = maxOf(bounds[0] - u, u - bounds[1], 0f) * uScale
        val span = bounds[3] - bounds[2]
        val dv = if (span >= 1f) {
            0f
        } else {
            val middle = (bounds[2] + bounds[3]) / 2f
            val off = v - middle - round(v - middle)
            maxOf(abs(off) - span / 2f, 0f) * vScale
        }
        return sqrt(du * du + dv * dv)
    }

    private fun ellipseDistance(e: ClothOutline.Ellipse, u: Float, v: Float, uScale: Float, vScale: Float): Float {
        val middleV = (e.fromV + e.toV) / 2f
        val a = (e.to - e.from) / 2f * uScale
        val b = (e.toV - e.fromV) / 2f * vScale
        val x = (u - (e.from + e.to) / 2f) * uScale
        val y = (v - middleV - floor(v - middleV + 0.5f)) * vScale
        val k0 = sqrt((x / a) * (x / a) + (y / b) * (y / b))
        val k1 = sqrt((x / (a * a)) * (x / (a * a)) + (y / (b * b)) * (y / (b * b)))
        if (k1 <= 0f) return minOf(a, b)
        return -k0 * (k0 - 1f) / k1
    }
}
