package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** The share of v either side of v = 0 that the front between the lips takes, so an area over v [1 - LIP_V, LIP_V] is the hollow. */
const val SHELL_LIP_V = 0.15f

/** The exponent of the back's superellipse, fuller than an ellipse, so the back reads round and full. */
private const val BACK_EXPONENT = 2.5f

/** The exponent of the front's superellipse, flatter still, so the rims stand forward as far as the front's middle. */
private const val FRONT_EXPONENT = 4f

/** Outline points sampled densely before control points are laid along it. */
private const val DENSE = 720

/** How far below the hollow's floor the section's centre lies, in hollow depths, at least: the floor and the lips are then seen from it. */
private const val CENTRE_BELOW_FLOOR = 1.02f

/**
 * How near the back the centre may lie, as a share of the thickness below the floor: a centre close to the back reads the back's field
 * from too near, where a small step round turns the direction a long way.
 */
private const val CENTRE_BACK_MARGIN = 0.3f

/**
 * The deepest hollow a shell takes, as a share of its thickness: deeper, no point inside far enough from the back sees the whole
 * outline.
 */
const val SHELL_MAX_HOLLOW = 0.4f

/**
 * A thick section with a hollow in its front: a superellipse [thickness] deep for a half width of 1, flat across the front (+y) and full
 * round the back,
 * the front dished in by [hollow] of the thickness between two lips a [rim] in from either side, the band outside each lip the rim.
 * The control points lie evenly along the outline in two stretches, the front between the lips over v [-[SHELL_LIP_V], [SHELL_LIP_V]]
 * and the rest round the back, so the hollow keeps the same v however deep it is and sections of one thickness and rim blend point for
 * point from flat to hollow. Its [ClosedSpline.centre] lies on the middle line below the floor, from where every direction round meets the
 * outline once. Fitted to the unit span.
 */
object ShellSection {
    fun of(segments: Int, thickness: Float, hollow: Float, rim: Float): ClosedSpline {
        val depth = thickness.coerceAtLeast(MIN_THICKNESS)
        val lip = (1f - rim).coerceIn(MIN_LIP, 1f - MIN_RIM)
        val dish = hollow.coerceIn(0f, SHELL_MAX_HOLLOW) * depth
        val outline = outline(depth, lip, dish)
        val frontCount = (2f * SHELL_LIP_V * segments).roundToInt().coerceIn(2, segments - 4).let { it - it % 2 }
        val points = lay(outline, segments, frontCount)
        val floor = -dish
        val below = maxOf(CENTRE_BELOW_FLOOR * dish, (depth - dish) * 0.5f).coerceAtMost((depth - dish) * (1f - CENTRE_BACK_MARGIN))
        return ClosedSpline(points, Vec2(0f, floor - below)).fittedToUnit()
    }

    /** The outline from the front's middle round toward +x, as dense points, and the index of the +x lip among them. */
    private fun outline(depth: Float, lip: Float, dish: Float): Pair<List<Vec2>, Int> {
        val half = depth / 2f
        fun around(t: Float): Vec2 {
            val angle = t * TWO_PI
            val s = kotlin.math.sin(angle)
            val c = kotlin.math.cos(angle)
            val exponent = if (c > 0f) FRONT_EXPONENT else BACK_EXPONENT
            val x = signedPower(s, 2f / exponent)
            val y = -half + half * signedPower(c, 2f / exponent)
            val front = if (c > 0f && abs(x) < lip) dish * (1f - (x / lip) * (x / lip)) else 0f
            return Vec2(x, y - front)
        }
        val points = List(DENSE) { around(it.toFloat() / DENSE) }
        val lipIndex = points.indices.first { points[it].x >= lip }
        return points to lipIndex
    }

    /** [segments] points along the closed [outline]: [frontCount] across the front between the lips, the rest round the back. */
    private fun lay(outline: Pair<List<Vec2>, Int>, segments: Int, frontCount: Int): List<Vec2> {
        val (points, lipIndex) = outline
        val n = points.size
        val rightLip = lipIndex
        val leftLip = n - lipIndex
        val front = points.subList(leftLip, n) + points.subList(0, rightLip + 1)
        val back = points.subList(rightLip, leftLip + 1)
        val halfFront = frontCount / 2
        val frontPoints = evenly(front, frontCount + 1)
        val backPoints = evenly(back, segments - frontCount + 1)
        return frontPoints.subList(halfFront, frontCount) + backPoints.dropLast(1) + frontPoints.subList(0, halfFront)
    }

    /** [count] points evenly along the open [path], its ends among them. */
    private fun evenly(path: List<Vec2>, count: Int): List<Vec2> {
        val lengths = FloatArray(path.size)
        for (i in 1 until path.size) lengths[i] = lengths[i - 1] + (path[i] - path[i - 1]).let { kotlin.math.hypot(it.x, it.y) }
        val total = lengths.last()
        var k = 0
        return List(count) { i ->
            val at = total * i / (count - 1)
            while (k < path.size - 2 && lengths[k + 1] < at) k++
            val span = lengths[k + 1] - lengths[k]
            val t = if (span > 0f) ((at - lengths[k]) / span).coerceIn(0f, 1f) else 0f
            path[k] + (path[k + 1] - path[k]) * t
        }
    }

    private fun signedPower(value: Float, power: Float): Float = kotlin.math.sign(value) * abs(value).pow(power)

    private const val MIN_THICKNESS = 0.05f
    private const val MIN_LIP = 0.3f
    private const val MIN_RIM = 0.05f
    private const val TWO_PI = (2.0 * kotlin.math.PI).toFloat()
}
