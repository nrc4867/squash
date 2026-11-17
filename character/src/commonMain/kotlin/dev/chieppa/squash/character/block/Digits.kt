package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.AreaSpec
import dev.chieppa.squash.character.model.AreaTag
import dev.chieppa.squash.character.model.DigitChain
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.PatchName
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.character.model.SurfaceMethod
import dev.chieppa.squash.geom.ClosedSpline
import dev.chieppa.squash.geom.EndClosure
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.SectionProfile
import dev.chieppa.squash.geom.SliceBuilder
import dev.chieppa.squash.geom.SweepSpec
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.DigitLoft
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

/** How far digits joined to a field blend into their neighbours by default, as a share of their radius. */
private const val DEFAULT_MERGE = 0.2f

/** Where round a digit its back lies, in v, and its underside. */
private const val BACK = 0.25f
private const val UNDER = 0.75f

private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

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
    @Doc("The radius, a fraction of the axis length, of the smooth union joining the digits to the part's field, so roots, webs and knuckles come out of one surface; unset, each digit is a run of its own.")
    val join: Param? = null,
    @Doc("On digits joined to a field, the radius of the smooth union joining each digit to its neighbours, as a share of a digit's radius: small for fingers apart to their roots, 1 or more for toes that only part a rounded mass as lobes.")
    val merge: Param = Param.of(DEFAULT_MERGE),
    @Doc("On digits joined to a field, the radius of a ball at each digit's root toward its back, a knuckle, as a share of the digit's radius; 0 for none.")
    val knuckles: Param = Param.of(0f),
    @Doc("A nail or a claw at each digit's tip: painted on its back, and on digits joined to a field standing out past the tip as a point.")
    val nail: DigitNail? = null,
    @Doc("A pad under each digit's tip, a toe bean: painted on its underside, and on digits joined to a field raised from it.")
    val pad: DigitPad? = null,
    @Doc("Whether the digits are in the body's field or each its own mesh sunk into the run before them.")
    val surface: SurfaceMethod = SurfaceMethod.FIELD,
) : Block {
    internal val rowName get() = name ?: DEFAULT_ROW_NAME

    override fun patchNames() = listOf(PatchName(rowName, repeats = true))

    override fun surfaceMethod() = surface

    override fun countedPatches(values: Map<String, Float>): Set<String> {
        if (condition?.let { values[it.name] == 0f } == true) return emptySet()
        return builtOf(count?.resolve(values)?.roundToInt() ?: digits.size).indices.map(::patchOf).toSet()
    }

    /** The patch of the [k]th digit the row builds, counted from 0 whichever of [digits] it is. */
    internal fun patchOf(k: Int) = "$rowName:$k"

    /**
     * The places in [digits] of the digits a row of [count] builds, in order: those that list [count] among their counts, or the first
     * [count] when no digit lists any.
     */
    private fun builtOf(count: Int): List<Int> =
        if (digits.any { it.counts != null }) digits.indices.filter { digits[it].counts?.contains(count) == true }
        else (0 until count.coerceIn(0, digits.size)).toList()

    /** The places in [digits] of the digits [ctx]'s build makes. */
    internal fun builtDigits(ctx: BuildContext): List<Int> = builtOf(count?.let { ctx.value(it).roundToInt() } ?: digits.size)

    override fun areas(values: Map<String, Float>): List<AreaSpec> {
        val built = builtOf(count?.or(values)?.roundToInt() ?: digits.size)
        val nails = nail?.takeIf { it.isOn(values) }?.let { nail ->
            val claw = nail.claw.or(values)
            built.mapIndexed { k, i ->
                val end = if (claw > 0f) 1f + claw else nail.at + nail.length / 2f
                oval("${nail.name}:$k", k, i, nail.at - nail.length / 2f, end, BACK, nail.width, nail.color, round = claw == 0f)
            }
        }.orEmpty()
        val pads = pad?.takeIf { it.isOn(values) }?.let { pad ->
            built.mapIndexed { k, i -> oval("${pad.name}:$k", k, i, pad.at - pad.length / 2f, pad.at + pad.length / 2f, UNDER, pad.width, pad.color, round = true) }
        }.orEmpty()
        return nails + pads
    }

    /** An area on the [k]th built digit, [digits]' [i]th, from [from] to [to] along it, [width] of a turn round it about [around]. */
    private fun oval(name: String, k: Int, i: Int, from: Float, to: Float, around: Float, width: Float, color: String, round: Boolean): AreaSpec {
        val span = span(i)
        fun u(share: Float) = span.start + (span.end - span.start) * share
        return AreaSpec(
            name, Span(u(from), u(to)), Span((around - width / 2f).mod(1f), (around + width / 2f).mod(1f)), AreaTag.FILL, patchOf(k), color, round = round,
        )
    }

    /** Digit [i]'s u along the part from its root to its tip, fixed by the template's numbers so a pose's joints stay where the rings are. */
    internal fun span(i: Int): Span {
        val digit = digits[i]
        val direction = digit.direction()
        return Span(digit.root[0] - from, digit.root[0] + direction.x * digit.length - from)
    }

    /** Each built digit's chain, the row's count read from how many of its patches [patches] holds, which picks the digits a count builds. */
    override fun digitChains(patches: Set<String>): List<DigitChain> {
        val built = builtOf(patches.count { it.startsWith("$rowName:") && it.removePrefix("$rowName:").toIntOrNull() != null })
        return built.indices.filter { patchOf(it) in patches }.map { k ->
            val digit = digits[built[k]]
            val span = span(built[k])
            val specs = digit.joints.mapIndexed { j, share ->
                JointSpec("${patchOf(k)}.${j + 1}", span.start + (span.end - span.start) * share, null, digit.hinge ?: hinge, digit.limits ?: limits, stretch = false)
            }
            DigitChain(patchOf(k), k, span, false, specs)
        }
    }

    override fun build(ctx: BuildContext): List<Patch> {
        JoinedDigits.of(ctx)?.let { joined -> return joinedRuns(ctx, joined) }
        val section = SectionProfile.CIRCLE.spline(segments)
        return builtDigits(ctx).mapIndexed { k, i ->
            ctx.checkCancelled()
            segment(ctx, i, patchOf(k), section)
        }
    }

    /** Each digit as a run cut against the field it is joined to, meeting the field's run on one surface. */
    private fun joinedRuns(ctx: BuildContext, joined: JoinedDigits): List<Patch> = joined.axes.indices.map { k ->
        ctx.checkCancelled()
        val shares = joined.shares(k, rings)
        val us = FloatArray(shares.size) { joined.uOf(k, shares[it]) }
        val digit = digits[joined.built[k]]
        val patch = DigitLoft.loft(
            joined.patchName(k), joined.field, ctx.axes, joined.axes, k, shares, us, segments, JoinedDigits.REACH_RADII,
            roll = digit.roll * DEGREES_TO_RADIANS, buriedBefore = joined.buriedBefore(k),
            begins = FloatArray(joined.axes.size) { joined.buriedBefore(it) }, checkCancelled = ctx.checkCancelled,
        )
        val length = ctx.solidLength
        patch.withBuried(patch.buried, joined.rides(patch, ctx.axes) { a -> a / length - joined.own.from })
    }

    /**
     * Digit [i] as a swept run named [patch]: a round end behind its root as deep as the root is thick, a body along its length swelling at its
     * knuckles and thinning to its tip, and a round end at the tip as long as the tip is thick, every ring's u read along the digit
     * so its joints land where their shares put them.
     */
    private fun segment(ctx: BuildContext, i: Int, patch: String, section: ClosedSpline): Patch {
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
        return SliceBuilder.sweep(SweepSpec(patch, us, centres, heights, widths, section, ctx.axes.up, EndClosure.OPEN, EndClosure.OPEN))
    }

    override fun lint(ctx: LintContext): List<Finding> =
        ctx.param("join", join) + ctx.param("knuckles", knuckles) + ctx.param("merge", merge) +
        ctx.atLeast("rings", rings, MIN_RINGS) + ctx.atLeast("segments", segments, MIN_SEGMENTS) + ctx.param("count", count) +
            ctx.param("taper", taper) + ctx.param("knuckle", knuckle) + ctx.param("flat", flat) + ctx.param("reach", reach) + ctx.param("thickness", thickness) +
            nail?.let { ctx.param("nail.claw", it.claw) + ctx.param("nail.curl", it.curl) }.orEmpty() +
            pad?.let { ctx.param("pad.rise", it.rise) }.orEmpty() +
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
    @Doc("Degrees its rings turn about it, so v = 0.25 round it, where its nail lies, faces its back.")
    val roll: Float = 0f,
    @Doc("On digits joined to a field, how much wider than the row's its own join to the field is, as a factor: a thumb rising out of a pad.")
    val join: Float = 1f,
    @Doc("On digits joined to a field, its knuckle ball as a share of the row's knuckles: 0 for a digit rooted deep in a pad.")
    val knuckle: Float = 1f,
    @Doc("The row counts it is built at, so a row holds a spread of digits for each count; unset on every digit, a count builds the first digits.")
    val counts: List<Int>? = null,
) {
    fun direction(): Vec3 = Vec3(toward[0], toward[1], toward[2]).normalized()

    fun lint(ctx: LintContext, field: String): List<Finding> =
        (if (root.size != COORDINATES) ctx.finding("$field.root", "a place is three numbers") else emptyList()) +
            (if (toward.size != COORDINATES) ctx.finding("$field.toward", "a direction is three numbers") else emptyList()) +
            (if (toward.size == COORDINATES && direction().x <= 0f) ctx.finding("$field.toward", "a digit runs forward along the part") else emptyList()) +
            DigitJoints.lintShares(ctx, "$field.joints", joints)
}

