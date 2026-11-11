package dev.chieppa.squash.geom

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Below this, in a section's unit span, a ring has no keel. */
private const val MIN_KEEL = 1e-4f

/**
 * The height view's up side read as a profile: whatever stands out of it over less than [span] of its length, a nose, lips, a chin
 * or a beak, stands out of the ring as a ridge [keel] of the ring's half width across, not as a swelling of the ring's whole up side.
 * What stands out over more than [span], a forehead or a muzzle, still swells the ring, as any drawing does. The base the ridge rises
 * from is where a ball [span] across, rolled along behind the up crossing, reaches (the crossing opened by a round structuring
 * element), so a drawn curve rounder than the ball, a round face or a skull, is its own base and stands out nowhere.
 */
class ProfileKeel private constructor(private val profile: OutlineProfile, private val base: FloatArray, private val keel: Float) {
    /** How far the profile stands out of its base at drawn station [a], in the drawing's units. */
    fun depthAt(a: Float): Float {
        val position = ((a - profile.start) / (profile.end - profile.start)).coerceIn(0f, 1f) * (base.size - 1)
        val i = position.toInt().coerceAtMost(base.size - 2)
        val t = position - i
        val under = base[i] + (base[i + 1] - base[i]) * t
        return max(0f, profile.highAt(a) - under)
    }

    /**
     * [section] with the ridge standing [depth] out of its up side, both the section and the depth as shares of the ring's full
     * height [height]: the rest of the section is squeezed into what is left below, so its span stays the ring's.
     */
    fun shape(section: ClosedSpline, depth: Float, height: Float): ClosedSpline {
        if (height <= 0f) return section
        val share = (depth / height).coerceIn(0f, 1f)
        if (share < MIN_KEEL) return section
        val samples = section.sample(section.controlPoints.size)
        val low = samples.minOf { it.y }
        val high = samples.maxOf { it.y }
        val middle = (low + high) / 2f
        val left = samples.minOf { it.x }
        val right = samples.maxOf { it.x }
        val centre = (left + right) / 2f
        val halfWidth = max((right - left) / 2f, MIN_KEEL)
        val span = high - low
        val baseSpan = span * (1f - share)
        return ClosedSpline(
            section.controlPoints.map { point ->
                val squeezed = low + (point.y - low) / span * baseSpan
                val across = (point.x - centre) / (halfWidth * keel)
                val front = ((point.y - middle) / (high - middle)).coerceIn(0f, 1f)
                val ridge = if (across * across >= 1f) 0f else (1f - across * across).let { it * it }
                Vec2(point.x, squeezed + span * share * ridge * front * front)
            },
        )
    }

    companion object {
        /** The keel of [profile], features under [span] of its length standing out [keel] of the half width across, or null for a span of none. */
        fun of(profile: OutlineProfile, span: Float, keel: Float): ProfileKeel? {
            if (span <= 0f || keel <= 0f) return null
            val step = profile.stationStep
            val stations = ((profile.end - profile.start) / step).roundToInt() + 1
            val high = FloatArray(stations) { profile.highAt(profile.start + it * step) }
            val radius = span * (profile.end - profile.start) / 2f
            val reach = max(1, (radius / step).roundToInt())
            val ball = FloatArray(reach + 1) { k -> (k * step).let { d -> sqrt(max(0f, radius * radius - d * d)) - radius } }
            val eroded = FloatArray(stations) { i -> (max(0, i - reach)..min(stations - 1, i + reach)).minOf { high[it] - ball[abs(it - i)] } }
            val opened = FloatArray(stations) { i -> (max(0, i - reach)..min(stations - 1, i + reach)).maxOf { eroded[it] + ball[abs(it - i)] } }
            return ProfileKeel(profile, FloatArray(stations) { min(opened[it], high[it]) }, keel)
        }
    }
}
