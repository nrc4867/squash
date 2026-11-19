package dev.chieppa.squash.geom

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** How many places a spiral takes per turn. */
private const val SPIRAL_PLACES_PER_TURN = 12

/** How many places along an axis its turning is measured at. */
private const val TURN_SAMPLES = 256

/**
 * A spiral from the origin as places (x along the rest, y toward up, z toward the side), for an axis through them: it leaves the origin
 * along x and coils [turns] times toward +y (toward -y when negative) about a line along z, its radius shrinking to [shrink] of the
 * first by its end and its coil moving [pitch] of the first coil's diameter toward +z each turn. The places are in the first coil's
 * radius; an axis through them is scaled to its own length, so only their shape counts. The origin is not among them.
 */
fun spiralPlaces(turns: Float, pitch: Float, shrink: Float): List<Vec3> {
    val count = maxOf(2, ceil(abs(turns) * SPIRAL_PLACES_PER_TURN).toInt())
    val total = turns * 2f * PI.toFloat()
    val side = if (turns < 0f) -1f else 1f
    return List(count) { i ->
        val t = (i + 1f) / count
        val angle = abs(total) * t
        val radius = 1f - (1f - shrink) * t
        Vec3(radius * sin(angle), side * (1f - radius * cos(angle)), pitch * 2f * abs(turns) * t)
    }
}

/** How far [axis] turns from its start to its end, in degrees: the sum of its direction's turns between close samples. */
fun turningDegrees(axis: AxisSpline): Float {
    var total = 0.0
    var before = axis.tangentAt(0f)
    for (i in 1..TURN_SAMPLES) {
        val after = axis.tangentAt(i.toFloat() / TURN_SAMPLES)
        total += acos((before dot after).coerceIn(-1f, 1f).toDouble())
        before = after
    }
    return (total * 180.0 / PI).toFloat()
}
