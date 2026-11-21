package dev.chieppa.squash.character.hair

import dev.chieppa.squash.character.build.PlacedPart
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private const val DEGREES = (PI / 180.0).toFloat()

/** How many bins round the head (azimuth) and up it (elevation) the scalp's reach is read into. */
private const val AZIMUTH_BINS = 48
private const val ELEVATION_BINS = 25

/** How far above the middle of the head's box its skull's middle lies, as a share of the box's height: the jaw and chin hang below it. */
private const val SKULL_RISE = 0.15f

/** The rounds an empty bin takes its neighbours' reach in. */
private const val FILL_ROUNDS = 8

/**
 * A place on the scalp: [point] on the outermost surface there, the way out from the head's middle ([out]), the way down the head along its
 * meridian ([down]), and the way round it toward the character's right ([round]), all in body space.
 */
data class ScalpPlace(val point: Vec3, val out: Vec3, val down: Vec3, val round: Vec3, val azimuth: Float, val elevation: Float)

/**
 * The outside of a head and the hair mass over it, read as a reach from the head's [middle] in every direction: azimuth in degrees round
 * the body's up from the front (0) toward the character's right (90), elevation from level (0) to straight up (90). A lock roots where a
 * direction meets the outermost of the two, and keeps out of it as it grows.
 */
class Scalp private constructor(val middle: Vec3, private val reach: FloatArray) {
    /** How far the outside lies from [middle] along the direction at [azimuth] and [elevation], read between bins. */
    fun reachAt(azimuth: Float, elevation: Float): Float {
        val a = ((azimuth / 360f) - floor(azimuth / 360f)) * AZIMUTH_BINS
        val e = ((elevation + 90f) / 180f).coerceIn(0f, 1f) * (ELEVATION_BINS - 1)
        val a0 = a.toInt() % AZIMUTH_BINS
        val a1 = (a0 + 1) % AZIMUTH_BINS
        val e0 = e.toInt().coerceAtMost(ELEVATION_BINS - 2)
        val fa = a - floor(a)
        val fe = e - e0
        fun at(i: Int, j: Int) = reach[j * AZIMUTH_BINS + i]
        val low = at(a0, e0) + (at(a1, e0) - at(a0, e0)) * fa
        val high = at(a0, e0 + 1) + (at(a1, e0 + 1) - at(a0, e0 + 1)) * fa
        return low + (high - low) * fe
    }

    /** How far the outside lies from [middle] toward [point]. */
    fun reachToward(point: Vec3): Float {
        val (azimuth, elevation) = anglesOf(point - middle)
        return reachAt(azimuth, elevation)
    }

    /** The place at [azimuth] and [elevation]. */
    fun placeAt(azimuth: Float, elevation: Float): ScalpPlace {
        val out = direction(azimuth, elevation)
        val point = middle + out * reachAt(azimuth, elevation)
        val round = Vec3(-sin(azimuth * DEGREES), 0f, cos(azimuth * DEGREES))
        val down = (out cross round).normalized()
        return ScalpPlace(point, out, down, round, azimuth, elevation)
    }

    override fun equals(other: Any?): Boolean = other is Scalp && middle == other.middle && reach.contentEquals(other.reach)

    override fun hashCode(): Int = 31 * middle.hashCode() + reach.contentHashCode()

    companion object {
        /** The scalp of [head], with the hair [mass] over it where there is one, read from their vertices where they show at rest. */
        fun of(head: PlacedPart, mass: PlacedPart?): Scalp = over(head, mass?.shownPoints())

        /** The scalp of [head], with a hair mass over it whose points in body space are [massPoints], where there is one. */
        fun over(head: PlacedPart, massPoints: List<Vec3>?): Scalp {
            val headPoints = head.shownPoints()
            val box = head.box
            val middle = box?.let { it.center + Vec3(0f, it.size.y * SKULL_RISE, 0f) } ?: headPoints.fold(Vec3.ZERO) { s, p -> s + p } * (1f / headPoints.size.coerceAtLeast(1))
            val reach = FloatArray(AZIMUTH_BINS * ELEVATION_BINS)
            fun take(points: List<Vec3>) {
                for (p in points) {
                    val d = p - middle
                    val length = d.length
                    if (length == 0f) continue
                    val (azimuth, elevation) = anglesOf(d)
                    val i = ((azimuth / 360f) * AZIMUTH_BINS + 0.5f).toInt() % AZIMUTH_BINS
                    val j = (((elevation + 90f) / 180f) * (ELEVATION_BINS - 1) + 0.5f).toInt().coerceIn(0, ELEVATION_BINS - 1)
                    val k = j * AZIMUTH_BINS + i
                    if (length > reach[k]) reach[k] = length
                }
            }
            take(headPoints)
            massPoints?.let(::take)
            filled(reach)
            return Scalp(middle, reach)
        }

        /** The unit direction at [azimuth] and [elevation], in degrees. */
        fun direction(azimuth: Float, elevation: Float): Vec3 {
            val a = azimuth * DEGREES
            val e = elevation * DEGREES
            return Vec3(cos(e) * cos(a), sin(e), cos(e) * sin(a))
        }

        /** The azimuth and elevation, in degrees, of the direction [d]. */
        fun anglesOf(d: Vec3): Pair<Float, Float> {
            val length = d.length
            if (length == 0f) return 0f to 0f
            val azimuth = (atan2(d.z, d.x) / DEGREES).let { if (it < 0f) it + 360f else it }
            val elevation = asin((d.y / length).coerceIn(-1f, 1f)) / DEGREES
            return azimuth to elevation
        }

        private fun filled(reach: FloatArray) {
            repeat(FILL_ROUNDS) {
                val before = reach.copyOf()
                var empty = 0
                for (j in 0 until ELEVATION_BINS) for (i in 0 until AZIMUTH_BINS) {
                    val k = j * AZIMUTH_BINS + i
                    if (before[k] > 0f) continue
                    var sum = 0f
                    var n = 0
                    for ((di, dj) in NEIGHBOURS) {
                        val jj = j + dj
                        if (jj !in 0 until ELEVATION_BINS) continue
                        val v = before[jj * AZIMUTH_BINS + (i + di + AZIMUTH_BINS) % AZIMUTH_BINS]
                        if (v > 0f) {
                            sum += v
                            n++
                        }
                    }
                    if (n > 0) reach[k] = sum / n else empty++
                }
                if (empty == 0) return
            }
            val known = reach.filter { it > 0f }
            val mean = if (known.isEmpty()) 0f else known.average().toFloat()
            for (k in reach.indices) if (reach[k] == 0f) reach[k] = mean
        }

        private val NEIGHBOURS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    }
}
