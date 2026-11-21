package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.SurfaceUv
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.mixedPlace
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One place along a recess's line: [centre] on the surface midway across the opening, [down] across the opening and [inward] into the
 * solid, both unit and square to each other, the opening [half] as tall either side of [centre] and the recess [deep] behind it, and
 * the (u, v) on the run the recess is cut from of [centre], [place], and of the opening's edges against and along [down], [upper] and [lower].
 */
data class RecessStation(
    val centre: Vec3,
    val down: Vec3,
    val inward: Vec3,
    val half: Float,
    val deep: Float,
    val place: SurfaceUv = SurfaceUv(0f, 0f),
    val upper: SurfaceUv = place,
    val lower: SurfaceUv = place,
)

/** A place on a recess's wall and the wall's normal there, pointing out of the recess into the open. */
data class RecessWallPoint(val position: Vec3, val normal: Vec3)

/**
 * A recess along a line of [stations], at least two, in order along it: at each a half ellipse as tall as the opening there and as deep
 * as the recess behind it, opening straight out past the surface by [reach], swept from one station to the next. Cut out of a solid
 * ([Hollow], [CarvedSolid]), it leaves a groove whose outline on the surface follows the stations' openings, closing at the ends where
 * the opening does. Its field is a distance near the walls, which is where it is read.
 */
class RecessSolid(val stations: List<RecessStation>, val reach: Float) : Solid {
    init {
        require(stations.size >= 2) { "a recess needs two stations" }
    }

    private val most = stations.maxOf { max(it.half, it.deep) } + reach
    override val low: Vec3 = Vec3(stations.minOf { it.centre.x } - most, stations.minOf { it.centre.y } - most, stations.minOf { it.centre.z } - most)
    override val high: Vec3 = Vec3(stations.maxOf { it.centre.x } + most, stations.maxOf { it.centre.y } + most, stations.maxOf { it.centre.z } + most)

    /** The station between station [i] and the next at [t] of the way, its directions held unit and its place mixed the short way round in v. */
    private fun between(i: Int, t: Float): RecessStation {
        val a = stations[i]
        val b = stations[i + 1]
        fun mix(x: Vec3, y: Vec3) = x + (y - x) * t
        val inward = mix(a.inward, b.inward).let { if (it.length > TINY) it.normalized() else a.inward }
        val down = mix(a.down, b.down).let { it - inward * (it dot inward) }.let { if (it.length > TINY) it.normalized() else a.down }
        return RecessStation(
            mix(a.centre, b.centre), down, inward, a.half + (b.half - a.half) * t, a.deep + (b.deep - a.deep) * t,
            mixedPlace(a.place, b.place, t), mixedPlace(a.upper, b.upper, t), mixedPlace(a.lower, b.lower, t),
        )
    }

    /** The field at [p] of the piece from the nearest place on the line between station [i] and the next, and the station there. */
    private fun piece(p: Vec3, i: Int): Near {
        val a = stations[i].centre
        val ab = stations[i + 1].centre - a
        val long = ab dot ab
        val raw = if (long > TINY) ((p - a) dot ab) / long else 0f
        val t = raw.coerceIn(0f, 1f)
        val s = between(i, t)
        val q = p - s.centre
        return Near(section(q dot s.down, q dot s.inward, s.half, s.deep), s, (p - (a + ab * t)).length, sqrt(long) * max(-raw, raw - 1f))
    }

    /** What the line holds nearest [p]: the field of the piece there, past the line's ends rounded off. */
    private fun nearest(p: Vec3): Near {
        var best = piece(p, 0)
        for (i in 1 until stations.size - 1) piece(p, i).let { if (it.gap < best.gap) best = it }
        return best
    }

    private class Near(val section: Float, val station: RecessStation, val gap: Float, val past: Float)

    override fun distance(p: Vec3): Float {
        val near = nearest(p)
        val section = near.section
        val past = near.past
        if (past <= 0f) return section
        val outside = max(section, 0f)
        return sqrt(outside * outside + past * past) + min(max(section, past), 0f)
    }

    /** The section's field at [y] across the opening and [z] into the solid: a half ellipse behind the surface, a slot [reach] out in front. */
    private fun section(y: Float, z: Float, half: Float, deep: Float): Float {
        val a = max(half, LEAST)
        if (z <= 0f) return max(abs(y) - a, -z - reach)
        val b = max(deep, LEAST)
        val k0 = sqrt((y / a) * (y / a) + (z / b) * (z / b))
        val k1 = sqrt((y / (a * a)) * (y / (a * a)) + (z / (b * b)) * (z / (b * b)))
        return if (k1 < TINY) -min(a, b) else k0 * (k0 - 1f) / k1
    }

    /**
     * Writes into [out] at [PLACE_U] and [PLACE_V] the place in the opening [p], on the recess's wall, is seen through from in front: the
     * back at the opening's middle, a wall nearer the lips toward the edge on its side, [EDGE_SHARE] of the way at the lips, so every wall
     * reads as the opening's inside on the run it is cut from and paint laid in from the opening's edge lies on the walls by depth.
     */
    fun placeInOpening(p: Vec3, out: FloatArray) {
        val s = nearest(p).station
        val y = (p - s.centre) dot s.down
        val across = (abs(y) / max(s.half, LEAST)).coerceIn(0f, 1f) * EDGE_SHARE
        val place = mixedPlace(s.place, if (y < 0f) s.upper else s.lower, across)
        out[PLACE_U] = place.u
        out[PLACE_V] = place.v
    }

    /**
     * The point on the wall [along] of the way along the line (0 at the first station, 1 at the last) and [depth] of the way in (0 at the
     * surface, 1 at the back), on the upper wall, the side against [RecessStation.down], when [upper], else the lower.
     */
    fun wallAt(along: Float, depth: Float, upper: Boolean): RecessWallPoint {
        val scaled = along.coerceIn(0f, 1f) * (stations.size - 1)
        val i = scaled.toInt().coerceAtMost(stations.size - 2)
        val s = between(i, scaled - i)
        val d = depth.coerceIn(0f, 1f)
        val sign = if (upper) -1f else 1f
        val y = sign * s.half * sqrt(max(0f, 1f - d * d))
        val z = s.deep * d
        val position = s.centre + s.down * y + s.inward * z
        val gradient = s.down * (y / max(s.half * s.half, TINY)) + s.inward * (z / max(s.deep * s.deep, TINY))
        val normal = (gradient * -1f).let { if (it.length > TINY) it.normalized() else s.inward * -1f }
        return RecessWallPoint(position, normal)
    }

    /**
     * The (u, v) on the run the recess is cut from of the walls lying deeper than [share] of the way from the lips to the back, seen from in
     * front: a closed loop along the upper side of the line and back along the lower, as [placeInOpening] places the walls.
     */
    fun deeperThan(share: Float): List<SurfaceUv> {
        val d = share.coerceIn(0f, 1f)
        val across = sqrt(1f - d * d) * EDGE_SHARE
        return stations.map { mixedPlace(it.place, it.upper, across) } + stations.reversed().map { mixedPlace(it.place, it.lower, across) }
    }

    private companion object {
        const val TINY = 1e-9f

        /** How far toward the opening's edge a wall at the lips is placed, a share of the way from its middle, so it stays inside. */
        private const val EDGE_SHARE = 0.9f

        /** The least height and depth a section is read at, in body units, so a corner where the opening closes reads as a sliver and not a division by nothing. */
        private const val LEAST = 1e-4f
    }
}
