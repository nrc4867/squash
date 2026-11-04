package dev.chieppa.squash.render

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.WidthProfile
import dev.chieppa.stretch.gpu.Mat4

/** Spans a ribbon's width eases over between two of its points. */
private const val SPANS_PER_GAP = 8

/** A place on a stroke as the surface gives it. */
@Immutable
data class RibbonPoint(val position: Vec3, val normal: Vec3)

/**
 * What sets a line's weight on screen, as the shader reads it: [silhouettePixels] where the surface turns edge on and
 * [interiorPixels] once its normal has come within [silhouetteBand] of the view, as the cosine of the angle between them. A
 * ribbon's width profile multiplies the result.
 */
@Immutable
data class LineRule(val interiorPixels: Float, val silhouettePixels: Float = interiorPixels, val silhouetteBand: Float = DEFAULT_BAND) {
    private companion object {
        const val DEFAULT_BAND = 0.3f
    }
}

/**
 * A stroke as the GPU reads it: two vertices per point, one for each edge of the ribbon, the shader setting how far apart on screen.
 * Each holds [FLOATS_PER_VERTEX] floats: position, the points before and after it, normal, edge (-1 or 1) and weight, the width
 * profile's value there. Never changes, so a frame can hand it to the render thread.
 */
class StrokeRibbon private constructor(val vertices: FloatArray, val indices: IntArray) {
    val pointCount: Int get() = vertices.size / FLOATS_PER_VERTEX / 2

    /** The profile's weight at point [index]. */
    fun weightAt(index: Int): Float = vertices[(index * 2 + 1) * FLOATS_PER_VERTEX - 1]

    companion object {
        const val FLOATS_PER_VERTEX = 14

        /**
         * The ribbon along [points], smooth through each, its weight at each place from [profile] read 0 to 1 along its length.
         * Null with under two distinct points.
         */
        fun along(points: List<RibbonPoint>, profile: WidthProfile): StrokeRibbon? {
            val distinct = points.filterIndexed { i, point -> i == 0 || (point.position - points[i - 1].position).length > 0f }
            if (distinct.size < 2) return null
            val smooth = smoothed(distinct)
            val places = placesAlong(smooth)
            val vertices = FloatArray(smooth.size * 2 * FLOATS_PER_VERTEX)
            var at = 0
            for (i in smooth.indices) {
                val weight = profile.at(places[i])
                for (edge in intArrayOf(-1, 1)) {
                    at = put(vertices, at, smooth[i].position)
                    at = put(vertices, at, smooth[(i - 1).coerceAtLeast(0)].position)
                    at = put(vertices, at, smooth[(i + 1).coerceAtMost(smooth.lastIndex)].position)
                    at = put(vertices, at, smooth[i].normal)
                    vertices[at++] = edge.toFloat()
                    vertices[at++] = weight
                }
            }
            val indices = IntArray((smooth.size - 1) * 6)
            var o = 0
            for (i in 0 until smooth.size - 1) {
                val low = i * 2
                indices[o++] = low
                indices[o++] = low + 1
                indices[o++] = low + 2
                indices[o++] = low + 1
                indices[o++] = low + 3
                indices[o++] = low + 2
            }
            return StrokeRibbon(vertices, indices)
        }

        private fun put(out: FloatArray, at: Int, v: Vec3): Int {
            out[at] = v.x
            out[at + 1] = v.y
            out[at + 2] = v.z
            return at + 3
        }

        private fun placesAlong(points: List<RibbonPoint>): FloatArray {
            val places = FloatArray(points.size)
            for (i in 1 until points.size) places[i] = places[i - 1] + (points[i].position - points[i - 1].position).length
            val total = places.last()
            return FloatArray(points.size) { places[it] / total }
        }

        /** [points] with others between them on a Catmull-Rom curve, so a line's width and bend change smoothly on screen. */
        private fun smoothed(points: List<RibbonPoint>): List<RibbonPoint> = buildList {
            for (i in 0 until points.size - 1) {
                val p0 = points[(i - 1).coerceAtLeast(0)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]
                for (step in 0 until SPANS_PER_GAP) {
                    val t = step.toFloat() / SPANS_PER_GAP
                    add(RibbonPoint(catmullRom(p0.position, p1.position, p2.position, p3.position, t), (p1.normal + (p2.normal - p1.normal) * t)))
                }
            }
            add(points.last())
        }

        private fun catmullRom(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, t: Float): Vec3 {
            val tt = t * t
            return (p1 * 2f + (p2 - p0) * t + (p0 * 2f - p1 * 5f + p2 * 4f - p3) * tt + (p1 * 3f - p0 - p2 * 3f + p3) * (tt * t)) * 0.5f
        }
    }
}

/** One ribbon to draw in a view, placed by [model], in [layer]'s slice of the depth buffer. */
@Immutable
data class RibbonDraw(
    val ribbon: StrokeRibbon,
    val rule: LineRule,
    val color: Color,
    val model: Mat4 = Mat4.Identity,
    val layer: DepthLayer = DepthLayer.SCENE,
)
