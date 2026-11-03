package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

private const val TWO_PI = (2.0 * PI).toFloat()
private const val MIN_CONTROL_POINTS = 3
private const val BASIS_DIVISOR = 6f
private const val KNOT_CENTRE_WEIGHT = 4f
private const val EGG_BULGE = 0.18f
private const val KEEL_NARROWING = 0.55f
private const val LENS_FLATNESS = 0.22f
private const val MIN_SPAN = 1e-3f

/**
 * A closed uniform cubic B-spline in a slice's plane: x toward the slice's side, y toward its up. v runs 0 to 1 around
 * it, starting at control point 0.
 */
data class ClosedSpline(val controlPoints: List<Vec2>) {
    init {
        require(controlPoints.size >= MIN_CONTROL_POINTS) { "A closed spline needs at least $MIN_CONTROL_POINTS control points" }
    }

    fun pointAt(v: Float): Vec2 {
        val n = controlPoints.size
        val position = (v - floor(v)) * n
        val i = position.toInt().coerceAtMost(n - 1)
        val t = position - i
        val s = 1f - t
        val b0 = s * s * s
        val b1 = 3f * t * t * t - 6f * t * t + KNOT_CENTRE_WEIGHT
        val b2 = -3f * t * t * t + 3f * t * t + 3f * t + 1f
        val b3 = t * t * t
        fun control(k: Int) = controlPoints[(k + n) % n]
        return (control(i - 1) * b0 + control(i) * b1 + control(i + 1) * b2 + control(i + 2) * b3) * (1f / BASIS_DIVISOR)
    }

    /** [count] points at fixed v, v = 0 first. */
    fun sample(count: Int): List<Vec2> = List(count) { pointAt(it.toFloat() / count) }

    /** This spline moved to centre on its samples and scaled evenly so its longer span runs -1 to 1. */
    fun fittedToUnit(): ClosedSpline {
        val points = sample(controlPoints.size)
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val centre = Vec2((minX + maxX) / 2f, (minY + maxY) / 2f)
        val scale = 2f / max(maxX - minX, maxY - minY)
        return ClosedSpline(controlPoints.map { (it - centre) * scale })
    }

    /** The spans in x and y of this spline's samples at its own control-point count. */
    fun spans(): Vec2 {
        val points = sample(controlPoints.size)
        return Vec2(points.maxOf { it.x } - points.minOf { it.x }, points.maxOf { it.y } - points.minOf { it.y })
    }

    /** This spline moved toward [other], control point by control point. */
    fun lerp(other: ClosedSpline, t: Float): ClosedSpline {
        require(other.controlPoints.size == controlPoints.size) { "Blending splines of different sizes" }
        return ClosedSpline(controlPoints.indices.map { controlPoints[it] + (other.controlPoints[it] - controlPoints[it]) * t })
    }

    /** This spline with its sides curled toward its up by [amount], refitted to the unit span. */
    fun cupped(amount: Float): ClosedSpline =
        if (amount == 0f) this else ClosedSpline(controlPoints.map { Vec2(it.x, it.y + amount * it.x * it.x) }).fittedToUnit()

    companion object {
        /**
         * A section with exactly [controlPoints] points evenly spaced along the closed [outline], fitted to the unit
         * span: v = 0 at its highest point, running toward +x. Null if the outline has no area to speak of.
         */
        fun fromOutline(outline: Polyline2, controlPoints: Int): ClosedSpline? {
            val clockwise = outline.signedArea() <= 0f
            val around = if (clockwise) outline else outline.reversed()
            val top = around.ys.indices.maxBy { around.ys[it] }
            val spline = ClosedSpline(around.resampledClosed(top, controlPoints))
            val spans = spline.spans()
            return if (spans.x < MIN_SPAN || spans.y < MIN_SPAN) null else spline.fittedToUnit()
        }
    }
}

/** The built-in section profiles, as the point at an angle clockwise from the top. */
enum class SectionProfile(private val pointAtAngle: (Float) -> Vec2) {
    /** Round. */
    CIRCLE({ angle -> Vec2(sin(angle), cos(angle)) }),

    /** Round on top, fuller underneath. */
    EGG({ angle -> Vec2(sin(angle) * (1f - EGG_BULGE * cos(angle)), cos(angle)) }),

    /** A round back drawn to a ridge underneath. */
    KEEL({ angle ->
        val under = max(0f, -cos(angle))
        Vec2(sin(angle) * (1f - KEEL_NARROWING * under * under), cos(angle))
    }),

    /** Flat, pointed at both sides. */
    LENS({ angle -> Vec2(sin(angle), cos(angle) * LENS_FLATNESS * (1f + abs(cos(angle)))) });

    /** This profile as a spline with exactly [controlPoints] control points, fitted to the unit span. */
    fun spline(controlPoints: Int): ClosedSpline =
        ClosedSpline(List(controlPoints) { pointAtAngle(it * TWO_PI / controlPoints) }).fittedToUnit()
}
