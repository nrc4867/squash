package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private const val TWO_PI = (2.0 * PI).toFloat()

/** The fewest points around an ellipse and the fewest steps around a capsule's cap, however large the spacing. */
private const val MIN_RING_POINTS = 24
private const val MIN_CAP_STEPS = 6

/** The least a closed shape can shrink to under the pointer, as a share of the size it was held at. */
private const val MIN_ADJUST_SCALE = 0.1f

/** The nearest the pointer may have been held to a closed shape's centre for its distance to measure a size. */
private const val MIN_HELD_REACH = 1e-5f

/** A clean shape a held stroke became, in the plane of the stroke. */
sealed interface StrokeShape {
    /** This shape as a polyline in drawing order with points about [spacing] apart; a closed shape repeats its first point last. */
    fun outline(spacing: Float): List<Vec2>

    /** This shape with the pointer moved from [held] to [pointer]: a line's end follows it, a closed shape's size follows its distance from the centre. */
    fun adjusted(held: Vec2, pointer: Vec2): StrokeShape
}

/** A straight line drawn from [from] to [to]. */
data class LineShape(val from: Vec2, val to: Vec2) : StrokeShape {
    override fun outline(spacing: Float): List<Vec2> = resample(listOf(from, to), spacing)

    override fun adjusted(held: Vec2, pointer: Vec2) = LineShape(from, pointer)
}

/** An ellipse around [center] with [major] as one half axis and a half axis [minorRadius] long square to it, drawn from its point nearest [start]. */
data class EllipseShape(
    val center: Vec2,
    val major: Vec2,
    val minorRadius: Float,
    val start: Vec2,
    val counterClockwise: Boolean,
) : StrokeShape {
    override fun outline(spacing: Float): List<Vec2> {
        val minor = major.squareLeft().unit() * minorRadius
        val around = TWO_PI * sqrt(((major dotWith major) + minorRadius * minorRadius) / 2f)
        val count = max(MIN_RING_POINTS, ceil(around / spacing).toInt())
        val ring = List(count) {
            val angle = TWO_PI * it / count
            center + major * cos(angle) + minor * sin(angle)
        }
        return inDrawingOrder(ring, start, counterClockwise)
    }

    override fun adjusted(held: Vec2, pointer: Vec2): EllipseShape {
        val scale = sizeScale(center, held, pointer)
        return EllipseShape(center, major * scale, minorRadius * scale, center + (start - center) * scale, counterClockwise)
    }
}

/** The shape within [radius] of the segment from [from] to [to], drawn from its point nearest [start]. */
data class CapsuleShape(
    val from: Vec2,
    val to: Vec2,
    val radius: Float,
    val start: Vec2,
    val counterClockwise: Boolean,
) : StrokeShape {
    override fun outline(spacing: Float): List<Vec2> {
        val along = (to - from).unit()
        val across = along.squareLeft()
        val capSteps = max(MIN_CAP_STEPS, ceil(PI.toFloat() * radius / spacing).toInt())
        fun cap(center: Vec2, facing: Vec2, left: Vec2) = List(capSteps) {
            val angle = PI.toFloat() * it / capSteps
            center + left * (-radius * cos(angle)) + facing * (radius * sin(angle))
        }
        fun run(a: Vec2, b: Vec2) = resample(listOf(a, b), spacing).dropLast(1)
        val ring = cap(to, along, across) +
            run(to + across * radius, from + across * radius) +
            cap(from, along * -1f, across * -1f) +
            run(from - across * radius, to - across * radius)
        return inDrawingOrder(ring, start, counterClockwise)
    }

    override fun adjusted(held: Vec2, pointer: Vec2): CapsuleShape {
        val center = (from + to) * 0.5f
        val scale = sizeScale(center, held, pointer)
        fun scaled(p: Vec2) = center + (p - center) * scale
        return CapsuleShape(scaled(from), scaled(to), radius * scale, scaled(start), counterClockwise)
    }
}

/** This vector turned a quarter counter-clockwise, with y up. */
private fun Vec2.squareLeft() = Vec2(-y, x)

private fun sizeScale(center: Vec2, held: Vec2, pointer: Vec2): Float {
    val reach = (held - center).magnitude
    if (reach < MIN_HELD_REACH) return 1f
    return ((pointer - center).magnitude / reach).coerceAtLeast(MIN_ADJUST_SCALE)
}

/** [ring], counter-clockwise with no point repeated, closed and running from its point nearest [start] the way [counterClockwise] says. */
private fun inDrawingOrder(ring: List<Vec2>, start: Vec2, counterClockwise: Boolean): List<Vec2> {
    val first = ring.indices.minBy { (ring[it] - start).magnitude }
    val closed = ring.subList(first, ring.size) + ring.subList(0, first + 1)
    return if (counterClockwise) closed else closed.reversed()
}
