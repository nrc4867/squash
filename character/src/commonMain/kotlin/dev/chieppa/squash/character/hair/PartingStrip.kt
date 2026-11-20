package dev.chieppa.squash.character.hair

import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.lockGroupPartId
import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.geom.ClumpShape
import dev.chieppa.squash.geom.LockBuilder
import dev.chieppa.squash.geom.LockProfile
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos

private const val DEGREES = (PI / 180.0).toFloat()

/** The patch name of a parting's strip of scalp. */
const val PARTING_PATCH = "parting"

/** How wide the strip of scalp along a parting shows, as a share of the gap either side of its line no lock roots in. */
private const val STRIP_SHARE = 0.75f

/**
 * How thick the strip is, as a share of its width, and how far it stands off the scalp as read, as a share of the head's reach: enough
 * that the scalp's surface between where it is read never rises over it.
 */
private const val STRIP_THICKNESS = 0.12f
private const val STRIP_LIFT = 0.02f

/** How far round each point of the strip's guide the scalp's points are read for the height it lies at, in degrees. */
private const val STRIP_READ = 4f

/** The points along a strip's guide, and its rings. */
private const val STRIP_POINTS = 14
private const val STRIP_RINGS = 28

/** How far behind the strip its contour lies, as a share of its width: behind the scalp under it, so it draws no line over the hair. */
private const val STRIP_TUCK = 1f

/** The lock-group id a head's parting strip is placed under. */
fun partingGroupId(head: PartId): LockGroupId = LockGroupId("${head.value}~parting")

/** The part id the strip of scalp along [head]'s parting is built and coloured as. */
fun partingPartId(head: PartId): PartId = lockGroupPartId(partingGroupId(head))

/**
 * The strip of scalp along [line] over [scalp]: a thin flat lock lying on the scalp from the hairline to the crown along the line, as wide
 * as the gap no lock roots in leaves bare, closing to a point at the front and rounding off at the back. With its contour [tuck] behind
 * it, it draws as a thin line of the scalp's colour between the locks lying away to each side.
 */
class PartingStrip(val patch: Patch, val tuck: Float) {
    companion object {
        /** The strip along [line] over [scalp], lying on the outermost of [outside], the head's and its mass's points, where they reach. */
        fun of(line: PartLine, scalp: Scalp, outside: List<Vec3>): PartingStrip {
            val reach = scalp.reachAt(0f, 60f)
            val width = 2f * PART_GAP * STRIP_SHARE * DEGREES * reach
            val thickness = width * STRIP_THICKNESS
            val near = cos(STRIP_READ * DEGREES)
            val ways = List(STRIP_POINTS) { k ->
                val along = line.from + (line.to - line.from) * k / (STRIP_POINTS - 1)
                PartLine.direction(along, line.acrossAt(along))
            }
            val read = ways.map { d ->
                val most = outside.maxOfOrNull { p ->
                    val from = p - scalp.middle
                    val length = from.length
                    if (length > 0f && (from dot d) >= near * length) from dot d else 0f
                } ?: 0f
                maxOf(most, scalp.reachToward(scalp.middle + d))
            }
            val heights = smoothed(read)
            val guide = ways.mapIndexed { k, d -> scalp.middle + d * (heights[k] + reach * STRIP_LIFT) }
            val up = (guide.first() - scalp.middle).normalized()
            val shape = ClumpShape(width = width, thickness = thickness, taper = 0.7f, swell = 0f, tipRound = 1f, tipReach = 0.12f, flare = 0f)
            val patch = LockBuilder.patch(PARTING_PATCH, guide, up, shape, LockProfile.RIBBON, STRIP_RINGS, LOCK_SEGMENTS, scalp.middle, 1f)
            return PartingStrip(patch, width * STRIP_TUCK)
        }

        /** [heights] with each the most of it and its neighbours, then eased with them, so the strip runs smooth over the scalp's highest. */
        private fun smoothed(heights: List<Float>): List<Float> {
            val most = heights.indices.map { k -> heights.subList((k - 1).coerceAtLeast(0), (k + 2).coerceAtMost(heights.size)).max() }
            return most.indices.map { k -> maxOf(most[k], (most[(k - 1).coerceAtLeast(0)] + 2f * most[k] + most[(k + 1).coerceAtMost(most.lastIndex)]) / 4f) }
        }
    }
}
