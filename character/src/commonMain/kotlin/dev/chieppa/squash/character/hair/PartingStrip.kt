package dev.chieppa.squash.character.hair

import dev.chieppa.squash.document.LockGroupId
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.document.lockGroupPartId
import dev.chieppa.squash.geom.AxisSpline
import dev.chieppa.squash.geom.Frame
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.SectionProfile
import dev.chieppa.squash.geom.Slice
import dev.chieppa.squash.geom.SliceBuilder
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

private const val DEGREES = (PI / 180.0).toFloat()

/** The patch name of a parting's strip of scalp. */
const val PARTING_PATCH = "parting"

/** How wide the strip shows at the hairline, as a share of the gap either side of its line no lock roots in. */
private const val STRIP_SHARE = 0.25f

/** How wide the strip still is at its back end, as a share of its width at the hairline, and how fast it thins toward there. */
private const val STRIP_END = 0.12f
private const val STRIP_THINNING = 1.4f

/** How thick the strip is, as a share of its width at the hairline. */
private const val STRIP_THICKNESS = 0.2f

/**
 * How far the strip's middle lies off the scalp as read, as a share of the head's reach: about the scalp's own surface, so it shows
 * between the locks and never stands over the hair round it.
 */
private const val STRIP_LIFT = 0.006f

/** How far round each point of the strip's line the scalp's points are read for the height it lies at, in degrees. */
private const val STRIP_READ = 4f

/** The points along the strip's line read for its height, its rings and the points round each ring. */
private const val STRIP_POINTS = 14
private const val STRIP_RINGS = 24
private const val STRIP_SEGMENTS = 8

/** Where along the strip its hairline end has rounded to its full width, as a share of its length. */
private const val STRIP_FRONT = 0.04f

/** How far behind the strip its contour lies, as a share of the head's reach: behind the scalp under it, so it draws no line over the hair. */
private const val STRIP_TUCK = 0.03f

/** The lock-group id a head's parting strip is placed under. */
fun partingGroupId(head: PartId): LockGroupId = LockGroupId("${head.value}~parting")

/** The part id the strip of scalp along [head]'s parting is built and coloured as. */
fun partingPartId(head: PartId): PartId = lockGroupPartId(partingGroupId(head))

/**
 * The strip of scalp along [line] over [scalp]: a thin flat strip lying on the scalp from the hairline back along the line, widest at the
 * hairline, a narrow V of scalp, and thinning to almost nothing toward its end. With its contour [tuck] behind it, it draws as a thin line
 * of the scalp's tone between the locks lying away to each side.
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
            val axis = AxisSpline(ways.mapIndexed { k, d -> scalp.middle + d * (heights[k] + reach * STRIP_LIFT) })
            val section = SectionProfile.CIRCLE.spline(STRIP_SEGMENTS)
            val slices = List(STRIP_RINGS) { ring ->
                val u = ring.toFloat() / (STRIP_RINGS - 1)
                val center = axis.pointAt(u)
                val ahead = axis.pointAt((u + 0.01f).coerceAtMost(1f)) - axis.pointAt((u - 0.01f).coerceAtLeast(0f))
                val tangent = ahead.normalized()
                val out = (center - scalp.middle).let { it - tangent * (it dot tangent) }.normalized()
                val share = widthAt(u)
                val frame = Frame(center, tangent, out)
                Slice(u, frame, thickness * share, width * share, section, frame.rotation)
            }
            return PartingStrip(SliceBuilder.loft(PARTING_PATCH, slices, STRIP_SEGMENTS), reach * STRIP_TUCK)
        }

        /** The strip's width at [u] along it from the hairline, a share of its widest: rounding up from a point, then thinning back to its end. */
        private fun widthAt(u: Float): Float = when {
            u <= 0f || u >= 1f -> 0f
            u < STRIP_FRONT -> sqrt(u / STRIP_FRONT)
            else -> STRIP_END + (1f - STRIP_END) * (1f - (u - STRIP_FRONT) / (1f - STRIP_FRONT)).pow(STRIP_THINNING)
        }

        /** [heights] with each the most of it and its neighbours, then eased with them, so the strip runs smooth over the scalp's highest. */
        private fun smoothed(heights: List<Float>): List<Float> {
            val most = heights.indices.map { k -> heights.subList((k - 1).coerceAtLeast(0), (k + 2).coerceAtMost(heights.size)).max() }
            return most.indices.map { k -> maxOf(most[k], (most[(k - 1).coerceAtLeast(0)] + 2f * most[k] + most[(k + 1).coerceAtMost(most.lastIndex)]) / 4f) }
        }
    }
}
