package dev.chieppa.squash.character.block

import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.geom.field.Blended
import dev.chieppa.squash.geom.field.Capsule
import dev.chieppa.squash.geom.field.DigitAxis
import kotlin.math.abs

/** A claw's thickness where it leaves the tip and at its point, in the tip's radii, and how far toward the back it grows from. */
private const val CLAW_ROOT = 0.75f
private const val CLAW_POINT = 0.12f
private const val CLAW_BACK = 0.2f

/** How far a toe bean's ball sinks into its digit's underside, as a share of each radius. */
private const val BEAN_SINK = 0.5f

/** How far toward its back, in its radii, a digit's knuckle ball sits from its root. */
private const val KNUCKLE_BACK = 0.3f

/** How far past its seam, in its radii, a digit reaches in the field the field's own run is cut through. */
private const val ROOT_REACH = 2.2f

/** Where two joint shares count as one. */
private const val SAME_SHARE = 1e-4f

private const val DEGREES_TO_RADIANS = (kotlin.math.PI / 180.0).toFloat()

/**
 * The solids a [Digits] row joins to its part's field, each with the radius joining it to the field: [whole], each digit's segments
 * swelling at its joints, its claw, toe bean and knuckle ball; and [roots], each digit only as far as just past its seam, with its
 * knuckle ball, which the field's own run is cut through.
 */
internal class DigitSolids private constructor(
    val whole: List<Blended>,
    val wholeJoins: FloatArray,
    val roots: List<Blended>,
    val rootJoins: FloatArray,
) {
    companion object {
        /** The solids of [row]'s digits [built], laid along [axes], with each digit's seam at its share of [seams]. */
        fun of(ctx: BuildContext, row: Digits, built: List<Int>, axes: List<DigitAxis>, seams: FloatArray): DigitSolids {
            val claw = row.nail?.takeIf { it.isOn(ctx.values) }?.let { ctx.value(it.claw) } ?: 0f
            val clawCurl = row.nail?.let { ctx.value(it.curl) } ?: 0f
            val bean = row.pad?.takeIf { it.isOn(ctx.values) }?.let { ctx.value(it.rise) } ?: 0f
            val taper = ctx.value(row.taper)
            val swell = ctx.value(row.knuckle)
            val knuckles = ctx.value(row.knuckles)
            val merge = ctx.value(row.merge)
            val join = ctx.value(requireNotNull(row.join)) * ctx.solidLength
            val whole = ArrayList<Blended>()
            val wholeJoins = ArrayList<Float>()
            val roots = ArrayList<Blended>()
            val rootJoins = ArrayList<Float>()
            built.forEachIndexed { k, i ->
                val digit = row.digits[i]
                val axis = axes[k]
                val blend = axis.radius * merge
                val joined = join * digit.join
                val back = JoinedDigits.backOf(axis.direction, digit.roll * DEGREES_TO_RADIANS)
                fun radiusAt(s: Float) = axis.radius * (1f + (taper - 1f) * s) * (if (s > 0f && digit.joints.any { abs(it - s) < SAME_SHARE }) 1f + swell else 1f)
                fun add(solid: Blended) {
                    whole += solid
                    wholeJoins += joined
                }
                val tipRadius = radiusAt(1f)
                val bodyEnd = 1f - tipRadius / axis.length
                val stops = (listOf(0f) + digit.joints.filter { it > 0f && it < bodyEnd } + bodyEnd).distinct()
                stops.zipWithNext().forEach { (a, b) ->
                    add(Blended(Capsule(axis.at(a), axis.at(b), radiusAt(a), if (b == bodyEnd) tipRadius else radiusAt(b)), blend))
                }
                if (claw > 0f) {
                    val clawLength = claw * axis.length
                    val base = axis.at(bodyEnd) + back * (tipRadius * CLAW_BACK)
                    val point = axis.at(1f + claw) - back * (clawLength * clawCurl)
                    add(Blended(Capsule(base, point, tipRadius * CLAW_ROOT, tipRadius * CLAW_POINT), blend))
                }
                row.pad?.takeIf { bean > 0f }?.let { pad ->
                    val r = axis.radius * bean
                    val at = axis.at(pad.at) - back * (axis.radius - r * BEAN_SINK)
                    add(Blended(Capsule(at, at, r), blend))
                }
                if (knuckles * digit.knuckle > 0f) {
                    val at = axis.root + back * (axis.radius * KNUCKLE_BACK)
                    add(Blended(Capsule(at, at, axis.radius * knuckles * digit.knuckle), blend))
                    roots += whole.last()
                    rootJoins += joined
                }
            }
            axes.forEachIndexed { k, axis ->
                val clip = seams[k] + ROOT_REACH * axis.radius / axis.length
                roots += Blended(Capsule(axis.at(0f), axis.at(clip), axis.radius, axis.radius * (1f + (taper - 1f) * clip)), axis.radius * merge)
                rootJoins += join * row.digits[built[k]].join
            }
            return DigitSolids(whole, wholeJoins.toFloatArray(), roots, rootJoins.toFloatArray())
        }
    }
}
