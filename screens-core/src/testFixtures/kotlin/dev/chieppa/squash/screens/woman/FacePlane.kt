package dev.chieppa.squash.screens.woman

import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.PartId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** How long a step of one across the face plane is on the head, in the head's drawn units. */
private const val UNIT = 0.19f

/** The radius of the ball the front of the head is drawn round, and the head's drawn length, in the same units. */
private const val FRONT_RADIUS = 0.22f
private const val DRAWN_LENGTH = 0.5f

/**
 * Places on the front of a head that rests forward, drawn flat as if seen from ahead: x to her left, y up, the front pole at the origin,
 * a unit about the reach from the nose to the outer corner of an eye. A head resting forward wraps its v round that pole, so a place
 * at angle a from up lies at v = a / 2π, and one r from the pole lies as far back along the head as a ball of [FRONT_RADIUS] falls
 * away at that reach. The right side mirrors v.
 */
class FacePlane(private val part: PartId) {
    fun at(x: Float, y: Float, left: Boolean = true): Anchor {
        val r = sqrt(x * x + y * y)
        val turn = (atan2(x, y) / (2.0 * PI)).toFloat().let { if (it < 0f) it + 1f else it }
        val v = if (left) turn else (1f - turn) % 1f
        return Anchor(part, BODY_PATCH, uAt(r), v)
    }

    /** A closed ring of [points] places round ([x], [y]), [rx] across and [ry] up, its first repeated last. */
    fun ring(x: Float, y: Float, rx: Float, ry: Float, left: Boolean, points: Int = RING_POINTS): List<Anchor> = List(points + 1) { i ->
        val angle = 2.0 * PI * (i % points) / points
        at(x + rx * cos(angle).toFloat(), y + ry * sin(angle).toFloat(), left)
    }

    companion object {
        const val RING_POINTS = 16

        /** The u on the head of every place [r] from the front pole. */
        fun uAt(r: Float): Float {
            val reach = (r * UNIT).coerceAtMost(FRONT_RADIUS)
            return 1f - (FRONT_RADIUS - sqrt(FRONT_RADIUS * FRONT_RADIUS - reach * reach)) / DRAWN_LENGTH
        }
    }
}
