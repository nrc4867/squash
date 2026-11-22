package dev.chieppa.squash.rig

import dev.chieppa.squash.character.mouth.MouthRecess
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max

/**
 * How far from the mouth line its split holds sharp, and by how far it has given way to the turn about the hinge, as shares of the
 * mouth's width, the straight distance between its corners.
 */
private const val SHARP_REACH = 0.25f
private const val FAR_REACH = 0.6f

/** How far either side of the mouth line, as a share of the mouth's width, the turn about the hinge blends the jaw in away from the slit. */
private const val FAR_BLEND = 0.15f

/** The widest that blend reaches about the hinge, in radians, so a point near the hinge, as an eye over it, stays with the head. */
private val WIDEST_BAND = (12.0 * PI / 180.0).toFloat()

/**
 * Where, past body down about the hinge, the jaw stops taking the head, in radians: it starts to let go this far past, so the whole chin
 * and the skin under it go with it, and has let go of the throat by this far past.
 */
private val THROAT_FROM = (10.0 * PI / 180.0).toFloat()
private val THROAT_TO = (55.0 * PI / 180.0).toFloat()

/**
 * How far in from each corner of the slit, as a share of the line's length, its lower lip takes less of the jaw, so the opening closes to
 * its corners as a lens; and how far below the line, as a share of the mouth's width, the jaw takes all of the face again.
 */
private const val CORNER_TAPER = 0.3f
private const val LIP_DROP = 0.35f

/** The least half-band a split blends across, so a slit's lips part cleanly at the surface. */
private const val SHARP = 1e-5f

private const val TINY = 1e-9f

private val BODY_DOWN = Vec3(0f, -1f, 0f)

/** A station of a jaw's slit in body space: its middle on the surface, the way down across it, the way out of it, how tall and how deep it is. */
internal class SlitStation(val middle: Vec3, val down: Vec3, val out: Vec3, val half: Float, val deep: Float)

/**
 * How much of a head's own weight at a body point goes to its jaw: below the mouth line the jaw takes it, above it the head keeps it.
 * Near the line the split is the slit's: sharp at the lips, so they part, and blended down its pocket's back, which stretches as the
 * jaw drops. Away from the line, past its corners and down the face, the jaw takes a point by how far it turns about the hinge from the
 * line, so the cheeks past the corners stretch and the throat behind the chin stays with the head. See HLD: surface, The rig on the surface.
 */
internal class JawShare(
    private val stations: List<SlitStation>,
    private val pivot: Vec3,
    private val hinge: Vec3,
    private val width: Float,
) {
    private val forward: Vec3
    private val opening: Vec3
    private val throat: Float

    /** Whether the jaw's positive turn drops the lower jaw, so a jaw opened within its limits parts the mouth. */
    val opens: Boolean

    init {
        val middle = stations[stations.size / 2]
        forward = flat(middle.middle - pivot).let { if (it.length > TINY) it.normalized() else middle.out }
        opening = hinge cross forward
        opens = (opening dot middle.down) > 0f
        throat = angleOf(flat(BODY_DOWN))
    }

    private fun flat(q: Vec3): Vec3 = q - hinge * (q dot hinge)

    /** The angle of [q], square to the hinge, from the mouth's forward toward the way the jaw opens. */
    private fun angleOf(q: Vec3): Float = atan2(q dot opening, q dot forward)

    /** The jaw's share of the head at body point [p], 0 to 1. */
    fun at(p: Vec3): Float {
        if (!opens) return 0f
        val near = nearest(p)
        val offset = p - near.point
        val down = offset dot near.down
        val depth = -(offset dot near.out)
        val sharp = smoothstep(0f, max(near.deep, TINY), depth).let { SHARP + near.half * it }
        val corner = smoothstep(0f, CORNER_TAPER * lengths.last(), near.along)
        val taper = corner + (1f - corner) * smoothstep(0f, LIP_DROP * width, down)
        val local = smoothstep(-sharp, sharp, down) * taper
        val angle = angleOf(flat(p - pivot))
        val line = angleOf(flat(near.point - pivot))
        val radius = flat(p - pivot).length
        val band = minOf(atan2(FAR_BLEND * width, max(radius, TINY)), WIDEST_BAND)
        val far = smoothstep(line - band, line + band, wrapped(angle, line))
        val reach = max(smoothstep(SHARP_REACH * width, FAR_REACH * width, offset.length), smoothstep(0f, SHARP_REACH * width, near.past))
        val share = local + (far - local) * reach
        return share * (1f - smoothstep(throat + THROAT_FROM, throat + THROAT_TO, wrapped(angle, throat)))
    }

    /** [angle] taken within a half turn of [about]. */
    private fun wrapped(angle: Float, about: Float): Float {
        var a = angle
        val turn = (2.0 * PI).toFloat()
        while (a - about > PI) a -= turn
        while (a - about < -PI) a += turn
        return a
    }

    private class Near(val point: Vec3, val down: Vec3, val out: Vec3, val half: Float, val deep: Float, val past: Float, val along: Float)

    /** How far along the slit's middle line each station lies from the first. */
    private val lengths = FloatArray(stations.size).also { at ->
        for (k in 1 until stations.size) at[k] = at[k - 1] + (stations[k].middle - stations[k - 1].middle).length
    }

    /** The nearest point of the slit's middle line to [p], with the slit there and how far [p] lies past its corner along the line. */
    private fun nearest(p: Vec3): Near {
        var best = Float.MAX_VALUE
        var at = 0
        var t = 0f
        for (k in 0 until stations.size - 1) {
            val a = stations[k].middle
            val ab = stations[k + 1].middle - a
            val span = ab dot ab
            val s = if (span < TINY) 0f else (((p - a) dot ab) / span).coerceIn(0f, 1f)
            val d = (p - (a + ab * s)).let { it dot it }
            if (d < best) {
                best = d
                at = k
                t = s
            }
        }
        val a = stations[at]
        val b = stations[at + 1]
        val point = a.middle + (b.middle - a.middle) * t
        val past = when {
            at == 0 && t <= 0f -> max(0f, (p - a.middle) dot (a.middle - b.middle).unit())
            at == stations.size - 2 && t >= 1f -> max(0f, (p - b.middle) dot (b.middle - a.middle).unit())
            else -> 0f
        }
        val s = lengths[at] + (lengths[at + 1] - lengths[at]) * t
        val along = minOf(s, lengths.last() - s)
        return Near(point, mix(a.down, b.down, t).unit(), mix(a.out, b.out, t).unit(), a.half + (b.half - a.half) * t, a.deep + (b.deep - a.deep) * t, past, along)
    }

    companion object {
        /** [recess]'s slit as the jaw [bone] splits the head it is cut into, that head placed at [origin], [mirrored] or not; null with too few stations. */
        fun of(recess: MouthRecess, bone: Bone, origin: Vec3, mirrored: Boolean): JawShare? {
            if (recess.stations.size < 2) return null
            fun place(p: Vec3) = (if (mirrored) p.mirrored else p) + origin
            fun turn(d: Vec3) = if (mirrored) d.mirrored else d
            val stations = recess.stations.map { SlitStation(place(it.centre), turn(it.down), turn(it.inward) * -1f, it.half, it.deep) }
            val width = (stations.last().middle - stations.first().middle).length
            if (width <= TINY) return null
            return JawShare(stations, bone.pivot, bone.hinge.unit(), width)
        }
    }
}

private fun Vec3.unit(): Vec3 = length.let { if (it > TINY) this * (1f / it) else this }

private fun mix(a: Vec3, b: Vec3, t: Float) = a + (b - a) * t

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    if (to <= from) return if (x < from) 0f else 1f
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
