package dev.chieppa.squash.character.hair

import dev.chieppa.squash.document.Parting
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val DEGREES = (PI / 180.0).toFloat()

/** The most a parting sits off the middle of the head, in degrees across the top: about over the outer corner of the eye. */
private const val MOST_ACROSS = 42f

/** Where a parting starts, in degrees over the head from straight ahead: the hairline, just under the fringe's roots. */
private const val PART_FRONT = 40f

/** How far back over the head a parting of length 1 runs from its start, in degrees: to the crown. */
private const val PART_REACH = 72f

/** How far a parting of cleanness 0 wanders either way across, in degrees, and how many times it wanders along a degree of its way. */
private const val WANDER = 3.5f
private const val WANDER_RATE = 0.22f

/** How far either side of a parting's line no lock roots, in degrees: the scalp shows there and nowhere else. */
const val PART_GAP = 3.2f

/**
 * A parting's line over the head, in degrees: [along] over the top from straight ahead (0) through straight up (90) toward the back, and
 * [across] it, the angle off the head's middle plane toward the character's right. It runs [from] the hairline [to] the crown for one of
 * length 1, wandering across as its cleanness lets it.
 */
class PartLine(parting: Parting) {
    private val across = parting.at * MOST_ACROSS
    private val wander = (1f - parting.clean) * WANDER
    val from: Float = PART_FRONT
    val to: Float = PART_FRONT + PART_REACH * parting.length

    /** Where the line lies across the head at [along] degrees over it. */
    fun acrossAt(along: Float): Float = across + wander * sin(along * WANDER_RATE)

    /** How far [along] lies outside the line's run, in degrees; 0 within it. */
    fun beyond(along: Float): Float = maxOf(from - along, along - to, 0f)

    /** How far [d], a unit direction from the head's middle, lies across from the line where it passes it, in degrees: positive on its right. */
    fun offset(d: Vec3): Float = acrossOf(d) - acrossAt(alongOf(d))

    /** The way along the scalp at [d] straight away from the line toward [side] (positive the character's right). */
    fun away(d: Vec3, side: Float): Vec3 {
        val along = alongOf(d) * DEGREES
        val across = acrossOf(d) * DEGREES
        val sign = if (side >= 0f) 1f else -1f
        return Vec3(-sin(across) * cos(along), -sin(across) * sin(along), cos(across)) * sign
    }

    /** [d] moved straight across until it lies at least [gap] degrees off the line, where it lies along the line's run. */
    fun keptOff(d: Vec3, gap: Float = PART_GAP): Vec3 {
        val along = alongOf(d)
        if (beyond(along) > 0f) return d
        val off = offset(d)
        if (abs(off) >= gap) return d
        val side = if (off >= 0f) 1f else -1f
        return direction(along, acrossAt(along) + side * gap)
    }

    companion object {
        /** The unit direction from the head's middle at [along] and [across] degrees. */
        fun direction(along: Float, across: Float): Vec3 {
            val a = along * DEGREES
            val c = across * DEGREES
            return Vec3(cos(c) * cos(a), cos(c) * sin(a), sin(c))
        }

        /** How far over the head [d] lies from straight ahead, in degrees. */
        fun alongOf(d: Vec3): Float = atan2(d.y, d.x) / DEGREES

        /** How far across the head [d] lies off its middle plane, in degrees, positive toward the character's right. */
        fun acrossOf(d: Vec3): Float = asin((d.z / d.length.coerceAtLeast(1e-6f)).coerceIn(-1f, 1f)) / DEGREES
    }
}
