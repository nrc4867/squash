package dev.chieppa.squash.character.hair

import dev.chieppa.squash.document.Cowlick
import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.ScalpFlow
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

private const val DEGREES = (PI / 180.0).toFloat()

/** How sharply a primitive's pull peaks at its middle: its weight there over its weight a radius's tenth away is about this squared. */
private const val PEAK = 0.04f

/** The most a whorl turns hair round it at its middle, in degrees, at turn 1; the turn eases out to none at its edge. */
private const val WHORL_MOST_TURN = 80f

/** How far a parting's pull reaches either side of it, in degrees, at cleanness 1 and at cleanness 0. */
private const val PART_REACH_CLEAN = 16f
private const val PART_REACH_SOFT = 24f

/**
 * What share of the way hair on a parting's line takes from the parting, lying straight away from it, at cleanness 0, and how much more
 * at cleanness 1; the share falls to none at the parting's reach.
 */
private const val PART_HOLD = 0.6f
private const val PART_HOLD_CLEAN = 0.4f

/** How far past its ends a parting's pull fades out, in degrees. */
private const val PART_FADE = 10f

/**
 * Over how many degrees from its front a parting's pull eases in, and how far into that ease it starts at the front itself, so a fringe
 * rooted under the parting's start falls forward as much as it parts.
 */
private const val PART_FRONT_EASE = 26f
private const val PART_FRONT_START = 0f

/** How strongly a sweep of 1 combs the top of the head, over the fall down it. */
private const val SWEEP_STRENGTH = 2.5f

/** Where the head counts as its top for a sweep: from this height of the way out (the sine of the elevation) up, fully from [TOP_FULL]. */
private const val TOP_FROM = 0.15f
private const val TOP_FULL = 0.75f

/** How strongly a cowlick of strength 1 rules the hair over it. */
private const val COWLICK_STRENGTH = 1.5f

/**
 * How far a lock at a whorl's middle bends round it over its length, at turn and strength 1, as a lock group's sweep; it eases out to
 * none at the whorl's edge. The same for a swirling cowlick.
 */
private const val WHORL_BEND = 1.3f
private const val COWLICK_BEND = 0.7f

/** How far either side of a parting, in its reaches, no lock grows toward it. */
private const val PART_GUARD = 2.2f

/**
 * The flow field over a scalp, after Yu's vector-field hairstyles: a sum of simple primitives on the head, each weighted by how near it
 * is, projected onto the scalp. The fall down the head along its meridians is the background; the [ScalpFlow]'s whorl is a source with a
 * vortex round it, its parting a pull away from its line to each side, its sweep a uniform comb over the top, and each cowlick a small
 * flip or vortex. Each primitive but the fall reaches only so far, so a change to one turns only the hair it reaches.
 *
 * As a [LockGrowth] it gives every lock root its way, blended with its kind's own by its group's flow, lifts the locks over a cowlick,
 * and keeps roots off the parting's line.
 */
class ScalpField(val flow: ScalpFlow) : LockGrowth {
    /** The parting's line, or null for none. */
    val partLine: PartLine? = if (flow.parting.shown) PartLine(flow.parting) else null

    private val whorlAt = Scalp.direction(flow.whorl.around, flow.whorl.up)
    private val partReach = PART_REACH_SOFT + (PART_REACH_CLEAN - PART_REACH_SOFT) * flow.parting.clean
    private val partHold = PART_HOLD + PART_HOLD_CLEAN * flow.parting.clean
    private val comb = Vec3(-flow.sweepBack, 0f, flow.sweep)
    private val cowlicks = flow.cowlicks.map { c ->
        val at = Scalp.direction(c.around, c.up)
        HeldCowlick(c, at, tangent(at, under(place(at))).let { if (it.length < 1e-4f) Vec3.ZERO else it.normalized() * -1f })
    }

    private class HeldCowlick(val cowlick: Cowlick, val at: Vec3, val flipped: Vec3)

    /** The way hair grows at [place] along the scalp: a unit direction in body space. */
    fun at(place: ScalpPlace): Vec3 {
        var sum = under(place)
        val p = place.out
        for (c in cowlicks) {
            val t = angle(p, c.at) / c.cowlick.radius
            val w = kernel(t)
            if (w == 0f) continue
            val away = tangent(p, p - c.at)
            val round = if (away.length < 1e-5f) Vec3.ZERO else away.normalized() cross p
            val flip = tangent(p, c.flipped)
            val turn = c.cowlick.turn * 90f * DEGREES
            val way = flip * cos(turn) + round * sin(turn)
            sum += way * (w * COWLICK_STRENGTH * c.cowlick.strength)
        }
        return directionOf(guarded(p, tangent(p, sum)), place)
    }

    /**
     * How far a lock rooted at [place] bends round as it grows, as a lock group's sweep is: round a whorl's middle the way it turns, more
     * the nearer the middle, and round a swirling cowlick, so locks there lie in a spiral.
     */
    fun bendAt(place: ScalpPlace): Float {
        val p = place.out
        val tw = angle(p, whorlAt) / flow.whorl.radius
        var bend = if (tw < 1f) -flow.whorl.turn * flow.whorl.strength * WHORL_BEND * (1f - tw) * (1f - partNear(p)) else 0f
        for (c in cowlicks) {
            val t = angle(p, c.at) / c.cowlick.radius
            if (t < 1f) bend -= c.cowlick.turn * c.cowlick.strength * COWLICK_BEND * (1f - t)
        }
        return bend
    }

    /**
     * [v], a way along the scalp at [p], with any part of it toward the parting's line near it taken out and turned further away from the
     * line the nearer it roots, so no lock crosses the line or lies along it over it.
     */
    private fun guarded(p: Vec3, v: Vec3): Vec3 {
        val line = partLine ?: return v
        val along = PartLine.alongOf(p)
        if (line.beyond(along) > 0f) return v
        val off = line.offset(p)
        val reach = partReach * PART_GUARD
        if (abs(off) >= reach) return v
        val away = line.away(p, off)
        val toward = v dot away
        val kept = if (toward >= 0f) v else v - away * toward
        val turn = (1f - abs(off) / reach) * frontEase(along, line)
        return kept * (1f - turn) + away * (kept.length * turn)
    }

    /** How far into its pull a parting is at [along] degrees over the head: eased in from its front, so a fringe under it falls forward. */
    private fun frontEase(along: Float, line: PartLine): Float = smooth((along - line.from) / PART_FRONT_EASE + PART_FRONT_START)

    /** How many degrees the locks rooted at [place] lift off the head, over their group's rise: most over a cowlick's middle. */
    fun liftAt(place: ScalpPlace): Float = cowlicks.sumOf { c ->
        val t = angle(place.out, c.at) / c.cowlick.radius
        if (t >= 1f) 0.0 else (c.cowlick.lift * (1f - t * t)).toDouble()
    }.toFloat()

    override fun at(place: ScalpPlace, group: LockGroup): Vec3 {
        if (group.flow <= 0f) return KindGrowth.at(place, group)
        val own = at(place)
        if (group.flow >= 1f) return own
        val kind = KindGrowth.at(place, group)
        return (kind * (1f - group.flow) + own * group.flow).let { if (it.length < 1e-4f) own else it.normalized() }
    }

    override fun rise(place: ScalpPlace, group: LockGroup): Float = liftAt(place) * group.flow

    /** The flow's bend at [place], and near a parting, the group's own sweep taken back where it would turn the lock across the line. */
    override fun sweep(place: ScalpPlace, group: LockGroup): Float {
        val bend = bendAt(place) * group.flow
        val line = partLine ?: return bend
        if (group.sweep == 0f || group.flow <= 0f) return bend
        val p = place.out
        val along = PartLine.alongOf(p)
        if (line.beyond(along) > 0f) return bend
        val off = line.offset(p)
        val reach = partReach * PART_GUARD
        if (abs(off) >= reach) return bend
        val turning = (p cross at(place, group)) dot line.away(p, off)
        if (group.sweep * turning >= 0f) return bend
        return bend - group.sweep * group.flow * frontEase(along, line)
    }

    override fun roots(group: LockGroup): List<LockRoot> {
        val laid = rootsOf(group, partLine)
        val line = partLine ?: return laid
        return laid.map { root ->
            val before = Scalp.direction(root.azimuth, root.elevation)
            val d = line.keptOff(before)
            if (d === before) return@map root
            val (azimuth, elevation) = Scalp.anglesOf(d)
            LockRoot(near(azimuth, root.azimuth), elevation, root.row)
        }
    }

    /** The field without its cowlicks at [place], summed but not yet made a direction. */
    private fun under(place: ScalpPlace): Vec3 {
        val p = place.out
        var sum = place.down
        val near = partNear(p)
        val whorl = flow.whorl
        val tw = angle(p, whorlAt) / whorl.radius
        val ww = kernel(tw) * whorl.strength * (1f - near)
        if (ww > 0f) {
            val away = tangent(p, p - whorlAt)
            if (away.length > 1e-5f) {
                val radial = away.normalized()
                val round = radial cross p
                val turn = whorl.turn * WHORL_MOST_TURN * DEGREES * (1f - tw)
                sum += (radial * cos(turn) + round * sin(turn)) * ww
            }
        }
        if (comb.length > 0f) {
            val top = ((p.y - TOP_FROM) / (TOP_FULL - TOP_FROM)).coerceIn(0f, 1f) * (1f - near)
            if (top > 0f) sum += tangent(p, comb) * (SWEEP_STRENGTH * top)
        }
        if (near <= 0f) return sum
        val line = partLine ?: return sum
        val rest = tangent(p, sum).let { if (it.length < 1e-5f) it else it.normalized() }
        val hold = near * partHold
        return rest * (1f - hold) + line.away(p, line.offset(p)) * hold
    }

    /**
     * How near the parting's line [p] lies, 1 on it to 0 at its reach and past it: faded out past its ends, and eased in from its front so
     * a fringe under its start falls forward as it parts.
     */
    private fun partNear(p: Vec3): Float {
        val line = partLine ?: return 0f
        val along = PartLine.alongOf(p)
        val fade = (1f - line.beyond(along) / PART_FADE).coerceAtLeast(0f) * frontEase(along, line)
        if (fade <= 0f) return 0f
        val t = abs(line.offset(p)) / partReach
        if (t >= 1f) return 0f
        val left = 1f - t * t
        return left * left * fade
    }

    private fun directionOf(sum: Vec3, place: ScalpPlace): Vec3 {
        val along = tangent(place.out, sum)
        return if (along.length < 1e-4f) place.down else along.normalized()
    }

    private fun place(at: Vec3): ScalpPlace {
        val (azimuth, elevation) = Scalp.anglesOf(at)
        val round = Vec3(-sin(azimuth * DEGREES), 0f, cos(azimuth * DEGREES))
        return ScalpPlace(at, at, (at cross round).normalized(), round, azimuth, elevation)
    }

    companion object {
        /** A primitive's weight [t] of its radius from its middle: high at the middle, falling to none at the radius and past it. */
        fun kernel(t: Float): Float {
            if (t >= 1f) return 0f
            val left = 1f - t * t
            return left * left / (t * t + PEAK)
        }

        /** 0 at or below 0, 1 at or above 1, easing in and out between. */
        private fun smooth(x: Float): Float {
            val a = x.coerceIn(0f, 1f)
            return a * a * (3f - 2f * a)
        }

        /** [v] with its part along [n], a unit vector, taken out: the part of it along the scalp. */
        private fun tangent(n: Vec3, v: Vec3): Vec3 = v - n * (v dot n)

        /** The angle between unit vectors [a] and [b], in degrees. */
        private fun angle(a: Vec3, b: Vec3): Float = acos((a dot b).coerceIn(-1f, 1f)) / DEGREES

        /** [azimuth] turned by whole turns to lie nearest [to]. */
        private fun near(azimuth: Float, to: Float): Float {
            var a = azimuth
            while (a - to > 180f) a -= 360f
            while (to - a > 180f) a += 360f
            return a
        }
    }
}
