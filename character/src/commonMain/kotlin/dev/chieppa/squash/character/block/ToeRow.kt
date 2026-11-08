package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.DigitChain
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.PatchName
import dev.chieppa.squash.character.model.SectionSpec
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.geom.EndClosure
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.SectionProfile
import dev.chieppa.squash.geom.SliceBuilder
import dev.chieppa.squash.geom.SweepSpec
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.positionAt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

private const val MIN_TOE_RINGS = 3
private const val MIN_TOE_SEGMENTS = 3
private const val DEFAULT_ROW_NAME = "toes"
private const val V_UNDER = 0.5f
private const val V_SIDE = 0.25f
private const val DRAWN_TOE_OVERLAP = 1.15f
private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

/** The angle from the axis past which a digit points back from its root, as a bird's hind toe does. */
private const val RIGHT_ANGLE = 90f

/** How far along a digit, as a share of its span, its root flare has eased out. */
private const val FLARE_REACH = 0.35f

/** How far either side of a digit joint, as a share of the digit's span, its knuckle swells. */
private const val KNUCKLE_REACH = 0.09f

/** How much of its knuckle a digit has at [off], its distance from a joint as a share of its span: whole at the joint, gone at [KNUCKLE_REACH]. */
private fun knuckleAt(off: Float): Float {
    val x = off / KNUCKLE_REACH
    if (x <= -1f || x >= 1f) return 0f
    val left = 1f - x * x
    return left * left
}

/** The height at [along] of a digit whose root is [flare] taller than its run, as a multiple of the run's. */
private fun flareAt(flare: Float, along: Float): Float {
    if (flare == 0f || along >= FLARE_REACH) return 1f
    val left = 1f - along / FLARE_REACH
    return 1f + flare * left * left
}

@Serializable
@SerialName("toeRow")
@Doc("A row of small tubes fanned across the end, each named for the row and its place in it. A count of 0 builds nothing.")
data class ToeRow(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    val u: Span,
    val count: Param,
    @Doc("Degrees from the axis of the outermost toe.")
    val spread: Param,
    val radius: Param,
    @Doc("Where around the run before it the row sits; underneath when left out.")
    @Range(min = 0.0, max = 1.0)
    val v: Float? = null,
    @Doc("Rings along each toe.")
    @Range(min = 3.0)
    val rings: Int,
    @Doc("Control points of each toe's section.")
    @Range(min = 3.0)
    val segments: Int,
    @Doc("Digits moved and angled apart from the fan, by their place in the row.")
    val digits: List<Digit> = emptyList(),
    @Doc("How much taller each digit is at its root than along its run, as a fraction of its thickness, easing out over the root's third; a drawn Side's heights are kept.")
    val flare: Param = Param.of(0f),
    @Doc("Joints along every digit, which bend it toward the row's underside; a digit with none follows the part's bone at its root.")
    val joints: DigitJoints? = null,
    @Doc("Each digit's cross-section, a named profile or control points; round when left out. It shapes drawn digits too.")
    val section: SectionSpec? = null,
    @Doc("Each digit's width as a multiple of its thickness; a drawn Top's widths are kept.")
    val width: Param = Param.of(1f),
    @Doc("Each digit's thickness at its tip as a share of its root's; a drawn Side's heights are kept.")
    val taper: Param = Param.of(1f),
    @Doc("How much each digit swells at each of its joints, as a fraction of its thickness; a drawn digit's sizes are kept.")
    val knuckle: Param = Param.of(0f),
) : Block {
    override fun patchNames() = listOf(PatchName(rowName, repeats = true))

    private val rowName get() = name ?: DEFAULT_ROW_NAME

    /**
     * Each built digit's joints, from the row's [joints] or a [Digit]'s own, at those shares of the digit's span: digit `i` is the
     * patch `name:i`, and its joints are `name:i.1` on from its root.
     */
    override fun digitChains(patches: Set<String>): List<DigitChain> {
        val spec = joints ?: return emptyList()
        val overrides = digits.associateBy { it.index }
        return generateSequence(0) { it + 1 }.takeWhile { "$rowName:$it" in patches }.map { i ->
            val digit = overrides[i]
            val span = digit?.u ?: u
            val patch = "$rowName:$i"
            val shares = digit?.joints ?: spec.at
            val specs = shares.mapIndexed { k, share ->
                JointSpec("$patch.${k + 1}", span.start + (span.end - span.start) * share, null, spec.hinge, digit?.limits ?: spec.limits, stretch = false)
            }
            DigitChain(patch, i, span, pointsBack(i), specs)
        }.toList()
    }

    /** Whether digit [index] is turned past a right angle, pointing back from its root. */
    fun pointsBack(index: Int): Boolean = digits.firstOrNull { it.index == index }?.angle?.let { abs(it) > RIGHT_ANGLE } == true

    /** Where along digit [index] its joints sit, as shares of its span from its root. */
    private fun jointShares(index: Int): List<Float> = digits.firstOrNull { it.index == index }?.joints ?: joints?.at.orEmpty()

    /** How many toes this build makes, none when the row is switched off. */
    fun toeCount(ctx: BuildContext): Int = if (ctx.isOn(this)) ctx.value(count).roundToInt().coerceAtLeast(0) else 0

    /**
     * A drawn Side fills each toe to the outline; a drawn Top shares its width among the toes. A [Digit] runs along its own span, from
     * its own place across the row, at its own angle and thickness, whatever is drawn, and the drawn width is shared among the others.
     * A digit turned past a right angle points back from its root, at its span's start, as far as its span is long, at its own thickness.
     */
    override fun build(ctx: BuildContext): List<Patch> {
        val toes = toeCount(ctx)
        val thickness = ctx.length(radius) * 2f
        val fan = ctx.value(spread) * DEGREES_TO_RADIANS
        val rootFlare = ctx.value(flare)
        val tipShare = ctx.value(taper)
        val swell = ctx.value(knuckle)
        val wide = ctx.value(width)
        val profile = (section ?: SectionSpec.Named(SectionProfile.CIRCLE)).spline(segments)
        val overrides = digits.associateBy { it.index }
        val sharing = (0 until toes).filter { it !in overrides }
        return List(toes) { i ->
            val digit = overrides[i]
            val span = digit?.u ?: u
            val root = ctx.axis.pointAt(span.start)
            val run = ctx.runAt(span.start)
            val toward = run?.positionAt(span.start, v ?: V_UNDER)?.minus(root)
            val rest = toward?.let { it * (1f - thickness / 2f / it.length).coerceAtLeast(0f) } ?: Vec3.ZERO
            val rowHalf = run?.let { (it.positionAt(span.start, V_SIDE) - root).length - thickness / 2f } ?: (toes * thickness / 2f)
            val us = FloatArray(rings) { span.start + (span.end - span.start) * it / (rings - 1) }
            val place = digit?.across ?: if (toes == 1) 0f else 2f * i / (toes - 1) - 1f
            val angle = digit?.angle?.let { it * DEGREES_TO_RADIANS } ?: (fan * place)
            val back = pointsBack(i)
            val fanOut = tan(angle) * ctx.axisLength
            ctx.checkCancelled()
            val knuckles = jointShares(i)
            val shaped = FloatArray(rings) { ring ->
                val along = (us[ring] - span.start) / (span.end - span.start)
                thickness * (1f + (tipShare - 1f) * along) * (1f + swell * knuckles.sumOf { knuckleAt(along - it).toDouble() }.toFloat())
            }
            val heights = FloatArray(rings) { ring ->
                ctx.views?.heightAt(us[ring])?.takeIf { !back } ?: (shaped[ring] * flareAt(rootFlare, (us[ring] - span.start) / (span.end - span.start)))
            }
            val widths = FloatArray(rings) { ring ->
                ctx.views?.widthAt(us[ring])?.takeIf { digit == null }?.let { it / sharing.size * DRAWN_TOE_OVERLAP } ?: (shaped[ring] * wide)
            }
            val centres = if (back) {
                val toward = ctx.axes.along * cos(angle) + ctx.axes.side * sin(angle)
                val from = ctx.axis.pointAt(span.start) + ctx.axes.side * (rowHalf * place) + rest
                us.map { at -> from + toward * (ctx.axisLength * (at - span.start)) }
            } else {
                us.map { at ->
                    val share = ctx.views?.widthAt(at)?.takeIf { digit == null }?.let { it * ((sharing.indexOf(i) + 0.5f) / sharing.size - 0.5f) }
                    val across = share ?: (rowHalf * place + fanOut * (at - span.start))
                    val base = (ctx.views?.centreAt(at) ?: ctx.axis.pointAt(at)) + ctx.axes.side * across
                    if (ctx.views?.heightAt(at) != null) base else base + rest
                }
            }
            val up = if (back) ctx.axes.up * -1f else ctx.axes.up
            val facing = ctx.axes.along.takeIf { ctx.views != null }?.let { if (back) it * -1f else it }
            SliceBuilder.sweep(SweepSpec("$rowName:$i", us, centres, heights, widths, profile, up, EndClosure.ROUND, EndClosure.ROUND, facing))
        }
    }

    override fun lint(ctx: LintContext): List<Finding> =
        ctx.unit("u", u) + ctx.param("count", count) + ctx.param("spread", spread) + ctx.param("radius", radius) + ctx.param("flare", flare) +
            ctx.param("width", width) + ctx.param("taper", taper) + ctx.param("knuckle", knuckle) +
            (section?.controlPointCount?.takeIf { it != segments }?.let { ctx.finding("section", "$it control points for $segments segments") } ?: emptyList()) +
            ctx.unit("v", v) + ctx.atLeast("rings", rings, MIN_TOE_RINGS) + ctx.atLeast("segments", segments, MIN_TOE_SEGMENTS) +
            (joints?.lint(ctx, "joints") ?: emptyList()) +
            digits.flatMapIndexed { i, digit -> digit.lint(ctx, "digits[$i]") } +
            digits.groupBy { it.index }.filter { it.value.size > 1 }.keys.flatMap { ctx.finding("digits", "two digits at index $it") }
}
