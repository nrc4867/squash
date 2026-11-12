package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.DigitChain
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.PatchName
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.geom.ClosedSpline
import dev.chieppa.squash.geom.EndClosure
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.SectionProfile
import dev.chieppa.squash.geom.SliceBuilder
import dev.chieppa.squash.geom.SweepSpec
import dev.chieppa.squash.geom.Vec3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DEFAULT_ROW_NAME = "digits"
private const val COORDINATES = 3
private const val MIN_RINGS = 8
private const val MIN_SEGMENTS = 3

/** Of a digit's rings, the share in each round end. */
private const val CAP_SHARE = 0.2f

/** How far along a digit, as a share of its length, a knuckle's swelling spreads either side of its joint. */
private const val KNUCKLE_SPREAD = 0.05f

/**
 * Digits placed one by one: each a straight rounded segment from its own root in its own direction, closed round at both ends, with
 * named joints along it, so a pose can curl each. See HLD: data-model, Block reference.
 */
@Serializable
@SerialName("digits")
@Doc("Digits placed one by one, each a straight rounded segment from its own root in its own direction with joints along it; named for the row and its place in it.")
data class Digits(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    @Doc("Where u = 0 lies along the axis from the socket plane, as a fraction of the axis length, as on the part's field.")
    val from: Float = 0f,
    val digits: List<DigitSegment>,
    @Doc("How many of the digits, from the first, the part builds.")
    val count: Param? = null,
    @Doc("Rings along each digit.")
    @Range(min = 8.0)
    val rings: Int,
    @Doc("Points round each digit's section.")
    @Range(min = 3.0)
    val segments: Int,
    @Doc("Each digit's thickness at its tip as a share of its root's.")
    val taper: Param = Param.of(1f),
    @Doc("How much each digit swells at each joint past its root, as a share of its thickness.")
    val knuckle: Param = Param.of(0f),
    @Doc("How much thinner each digit is toward its neighbours than toward the back and the palm, as a share.")
    val flat: Param = Param.of(0f),
    @Doc("Every digit's length is multiplied by this.")
    val reach: Param = Param.of(1f),
    @Doc("Every digit's thickness is multiplied by this.")
    val thickness: Param = Param.of(1f),
    @Doc("Which of the part's axes the joints turn about, unless a digit names its own: x across, y about its up, z along it.")
    val hinge: Hinge = Hinge.X,
    @Doc("Degrees the joints turn within.")
    val limits: Limits? = null,
) : Block {
    private val rowName get() = name ?: DEFAULT_ROW_NAME

    override fun patchNames() = listOf(PatchName(rowName, repeats = true))

    override fun countedPatches(values: Map<String, Float>): Set<String> {
        if (condition?.let { values[it.name] == 0f } == true) return emptySet()
        return (0 until built(values)).map { "$rowName:$it" }.toSet()
    }

    private fun built(values: Map<String, Float>): Int =
        (count?.resolve(values)?.roundToInt() ?: digits.size).coerceIn(0, digits.size)

    /** Digit [i]'s u along the part from its root to its tip, fixed by the template's numbers so a pose's joints stay where the rings are. */
    private fun span(i: Int): Span {
        val digit = digits[i]
        val direction = digit.direction()
        return Span(digit.root[0] - from, digit.root[0] + direction.x * digit.length - from)
    }

    override fun digitChains(patches: Set<String>): List<DigitChain> =
        digits.indices.filter { "$rowName:$it" in patches }.map { i ->
            val digit = digits[i]
            val span = span(i)
            val specs = digit.joints.mapIndexed { k, share ->
                JointSpec("$rowName:$i.${k + 1}", span.start + (span.end - span.start) * share, null, digit.hinge ?: hinge, digit.limits ?: limits, stretch = false)
            }
            DigitChain("$rowName:$i", i, span, false, specs)
        }

    override fun build(ctx: BuildContext): List<Patch> {
        val count = count?.let { ctx.value(it).roundToInt().coerceIn(0, digits.size) } ?: digits.size
        val section = SectionProfile.CIRCLE.spline(segments)
        return List(count) { i ->
            ctx.checkCancelled()
            segment(ctx, i, section)
        }
    }

    /**
     * Digit [i] as a swept run: a round end behind its root as deep as the root is thick, a body along its length swelling at its
     * knuckles and thinning to its tip, and a round end at the tip as long as the tip is thick, every ring's u read along the digit
     * so its joints land where their shares put them.
     */
    private fun segment(ctx: BuildContext, i: Int, section: ClosedSpline): Patch {
        val digit = digits[i]
        val span = span(i)
        val length = digit.length * ctx.solidLength * ctx.value(reach)
        val radius = digit.radius * ctx.solidLength * ctx.value(thickness)
        val direction = digit.direction()
        val root = point(ctx, digit.root.map { Param.of(it) })
        val toward = ctx.axes.point(direction.x, direction.y, direction.z)
        val rootAt = ctx.axes.point(root.x, root.y, root.z)
        val tipShare = ctx.value(taper)
        val swell = ctx.value(knuckle)
        val narrow = 1f - ctx.value(flat)
        fun radiusAt(s: Float): Float {
            val along = s.coerceIn(0f, 1f)
            val knuckles = digit.joints.filter { it > 0f }.sumOf { exp(-((along - it) * (along - it)) / (KNUCKLE_SPREAD * KNUCKLE_SPREAD)).toDouble() }.toFloat()
            return radius * (1f + (tipShare - 1f) * along) * (1f + swell * knuckles)
        }
        val caps = max(3, (rings * CAP_SHARE).roundToInt())
        val body = rings - 2 * caps
        val tipRadius = radiusAt(1f)
        val bodyEnd = 1f - tipRadius / length
        val shares = FloatArray(rings)
        val scales = FloatArray(rings)
        for (ring in 0 until caps) {
            val angle = (PI / 2.0 * ring / caps).toFloat()
            shares[ring] = -radiusAt(0f) * cos(angle) / length
            scales[ring] = sin(angle)
        }
        for (ring in 0 until body) {
            shares[caps + ring] = bodyEnd * ring / (body - 1).coerceAtLeast(1)
            scales[caps + ring] = 1f
        }
        for (ring in 0 until caps) {
            val angle = (PI / 2.0 * (ring + 1) / caps).toFloat()
            shares[caps + body + ring] = bodyEnd + (1f - bodyEnd) * sin(angle)
            scales[caps + body + ring] = if (ring == caps - 1) 0f else cos(angle)
        }
        val us = FloatArray(rings) { span.start + (span.end - span.start) * shares[it] }
        val centres = List(rings) { rootAt + toward * (length * shares[it]) }
        val widths = FloatArray(rings) { 2f * radiusAt(shares[it]) * scales[it] }
        val heights = FloatArray(rings) { widths[it] * narrow }
        return SliceBuilder.sweep(SweepSpec("$rowName:$i", us, centres, heights, widths, section, ctx.axes.up, EndClosure.OPEN, EndClosure.OPEN))
    }

    override fun lint(ctx: LintContext): List<Finding> =
        ctx.atLeast("rings", rings, MIN_RINGS) + ctx.atLeast("segments", segments, MIN_SEGMENTS) + ctx.param("count", count) +
            ctx.param("taper", taper) + ctx.param("knuckle", knuckle) + ctx.param("flat", flat) + ctx.param("reach", reach) + ctx.param("thickness", thickness) +
            digits.flatMapIndexed { i, digit -> digit.lint(ctx, "digits[$i]") }
}

/** One digit of [Digits]. */
@Serializable
@Doc("One digit: its root in the part's own axes (along, up, side) as fractions of the axis length, its direction, length and thickness.")
data class DigitSegment(
    val root: List<Float>,
    @Doc("Along, up and side; it must run forward along the part.")
    val toward: List<Float>,
    @Doc("A fraction of the axis length.")
    val length: Float,
    @Doc("Its radius at the root, a fraction of the axis length.")
    val radius: Float,
    @Doc("Each joint's place along the digit, from 0 at its root to 1 at its tip, ascending.")
    val joints: List<Float> = emptyList(),
    @Doc("The axis its joints turn about, in place of the row's.")
    val hinge: Hinge? = null,
    @Doc("Degrees its joints turn within, in place of the row's.")
    val limits: Limits? = null,
) {
    fun direction(): Vec3 = Vec3(toward[0], toward[1], toward[2]).normalized()

    fun lint(ctx: LintContext, field: String): List<Finding> =
        (if (root.size != COORDINATES) ctx.finding("$field.root", "a place is three numbers") else emptyList()) +
            (if (toward.size != COORDINATES) ctx.finding("$field.toward", "a direction is three numbers") else emptyList()) +
            (if (toward.size == COORDINATES && direction().x <= 0f) ctx.finding("$field.toward", "a digit runs forward along the part") else emptyList()) +
            DigitJoints.lintShares(ctx, "$field.joints", joints)
}
