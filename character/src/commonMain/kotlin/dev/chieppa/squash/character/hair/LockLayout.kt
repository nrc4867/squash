package dev.chieppa.squash.character.hair

import dev.chieppa.squash.document.LockGroup
import dev.chieppa.squash.document.LockGroupKind
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val DEGREES = (PI / 180.0).toFloat()

/** Where a lock roots on the scalp, in degrees round and up the head, and which row of its group it lies in, 0 the highest. */
data class LockRoot(val azimuth: Float, val elevation: Float, val row: Int)

/**
 * A group kind's own place on the head: its roots run round from [fromAzimuth] to [toAzimuth] at [elevation] in [rows] rows [rowGap]
 * degrees apart, the lower rows staggered; [heightReach] is how far a group's height moves them up or down, in degrees.
 */
private class KindPlace(val fromAzimuth: Float, val toAzimuth: Float, val elevation: Float, val rows: Int, val rowGap: Float, val heightReach: Float)

private val PLACES = mapOf(
    LockGroupKind.FRINGE to KindPlace(-48f, 48f, 26f, 1, 0f, 20f),
    LockGroupKind.LEFT to KindPlace(-62f, -128f, 34f, 2, 16f, 20f),
    LockGroupKind.RIGHT to KindPlace(62f, 128f, 34f, 2, 16f, 20f),
    LockGroupKind.BACK to KindPlace(122f, 238f, 48f, 3, 22f, 20f),
    LockGroupKind.STRAYS to KindPlace(-160f, 160f, 68f, 1, 0f, 15f),
)

/** The crown's middle, in degrees round and up the head, before a group turns or raises it. */
private const val CROWN_AZIMUTH = 180f
private const val CROWN_ELEVATION = 70f

/** How far round the crown a crown group's roots lie from its middle, in degrees, at spread 1. */
private const val CROWN_REACH = 20f

/** The turn between one crown root and the next, in degrees, which spreads them evenly over a disc. */
private const val GOLDEN_ANGLE = 137.5f

/** How far a root wanders from its even place, as a share of the gap to the next. */
private const val ROOT_JITTER = 0.25f

/** How far strays wander up and down, in degrees. */
private const val STRAY_RISE = 18f

/** The middle of [group]'s roots, in degrees round and up the head. */
fun centreOf(group: LockGroup): Pair<Float, Float> = when (group.kind) {
    LockGroupKind.CROWN -> (CROWN_AZIMUTH + group.around) to (CROWN_ELEVATION + group.height * 15f)
    else -> {
        val place = PLACES.getValue(group.kind)
        ((place.fromAzimuth + place.toAzimuth) / 2f + group.around) to (place.elevation + group.height * place.heightReach)
    }
}

/**
 * Where each of [group]'s locks roots: its kind's place, spread over [LockGroup.spread] of its reach, turned [LockGroup.around] and raised
 * by [LockGroup.height], each root wandering a little by the group's seed, and gathered toward the middle by its tie.
 */
fun rootsOf(group: LockGroup): List<LockRoot> {
    val random = Random(group.seed)
    val (centreAzimuth, centreElevation) = centreOf(group)
    val count = group.count
    val laid = if (group.kind == LockGroupKind.CROWN) {
        List(count) { i ->
            val angle = GOLDEN_ANGLE * i + random.nextFloat() * 10f
            val reach = CROWN_REACH * group.spread * sqrt((i + 0.5f) / count) * (0.9f + 0.2f * random.nextFloat())
            val azimuth = centreAzimuth + reach * sin(angle * DEGREES) / cos(centreElevation * DEGREES).coerceAtLeast(0.3f)
            LockRoot(azimuth, centreElevation + reach * cos(angle * DEGREES), 0)
        }
    } else {
        val place = PLACES.getValue(group.kind)
        val rows = place.rows.coerceAtMost(ceil(count / 3f).toInt()).coerceAtLeast(1)
        val half = (place.toAzimuth - place.fromAzimuth) / 2f * group.spread
        buildList {
            for (row in 0 until rows) {
                val inRow = count / rows + if (row < count % rows) 1 else 0
                val gap = if (inRow > 1) 2f * half / (inRow - 1) else 0f
                for (k in 0 until inRow) {
                    val even = if (inRow == 1) 0f else -half + gap * k + if (row % 2 == 1) gap / 2f * (if (k == inRow - 1) 0f else 1f) else 0f
                    val wander = if (gap == 0f) 0f else (random.nextFloat() * 2f - 1f) * gap * ROOT_JITTER
                    val rise = if (group.kind == LockGroupKind.STRAYS) (random.nextFloat() * 2f - 1f) * STRAY_RISE else (random.nextFloat() * 2f - 1f) * 2f
                    add(LockRoot(centreAzimuth + even + wander, centreElevation - place.rowGap * row + rise, row))
                }
            }
        }
    }
    if (group.tie <= 0f) return laid
    return laid.map { root ->
        LockRoot(root.azimuth + (centreAzimuth - root.azimuth) * group.tie, root.elevation + (centreElevation - root.elevation) * group.tie, root.row)
    }
}

/**
 * Which way a lock grows along the scalp from where it roots, before it rises off it: a flow field over the head plugs in here. The way
 * is a unit direction along the scalp at [place], in body space.
 */
fun interface LockGrowth {
    fun at(place: ScalpPlace, group: LockGroup): Vec3
}

/**
 * Each kind's own way: a crown's locks leave the crown's middle and swirl a little round it; every other kind's fall down the head along
 * its meridian.
 */
object KindGrowth : LockGrowth {
    private const val CROWN_SWIRL = 25f

    override fun at(place: ScalpPlace, group: LockGroup): Vec3 {
        if (group.kind != LockGroupKind.CROWN) return place.down
        val (azimuth, elevation) = centreOf(group)
        val centre = Scalp.direction(azimuth, elevation)
        val away = (place.out - centre).let { it - place.out * (it dot place.out) }
        if (away.length < 1e-4f) return place.down
        val radial = away.normalized()
        val round = place.out cross radial
        val t = CROWN_SWIRL * DEGREES
        return (radial * cos(t) + round * sin(t)).normalized()
    }
}
