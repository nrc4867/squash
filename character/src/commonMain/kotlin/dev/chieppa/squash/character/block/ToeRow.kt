package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
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
import kotlin.math.roundToInt
import kotlin.math.tan

private const val TOE_RINGS = 10
private const val TOE_SEGMENTS = 8
private const val DEFAULT_ROW_NAME = "toes"
private const val V_UNDER = 0.5f
private const val V_SIDE = 0.25f
private const val DRAWN_TOE_OVERLAP = 1.15f
private const val DEGREES_TO_RADIANS = (PI / 180.0).toFloat()

@Serializable
@SerialName("toeRow")
@Doc("A row of small tubes fanned across the end, each named for the row and its place in it.")
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
) : Block {
    /** A drawn Side fills each toe to the outline; a drawn Top shares its width among the toes. */
    override fun build(ctx: BuildContext): List<Patch> {
        val toes = ctx.value(count).roundToInt().coerceAtLeast(1)
        val thickness = ctx.length(radius) * 2f
        val root = ctx.axis.pointAt(u.start)
        val run = ctx.runAt(u.start)
        val toward = run?.positionAt(u.start, v ?: V_UNDER)?.minus(root)
        val rest = toward?.let { it * (1f - thickness / 2f / it.length).coerceAtLeast(0f) } ?: Vec3.ZERO
        val rowHalf = run?.let { (it.positionAt(u.start, V_SIDE) - root).length - thickness / 2f } ?: (toes * thickness / 2f)
        val fan = ctx.value(spread) * DEGREES_TO_RADIANS
        val section = SectionProfile.CIRCLE.spline(TOE_SEGMENTS)
        val us = FloatArray(TOE_RINGS) { u.start + (u.end - u.start) * it / (TOE_RINGS - 1) }
        return List(toes) { i ->
            val place = if (toes == 1) 0f else 2f * i / (toes - 1) - 1f
            val fanOut = tan(fan * place) * ctx.axisLength
            val heights = FloatArray(TOE_RINGS) { ctx.views?.heightAt(us[it]) ?: thickness }
            val widths = FloatArray(TOE_RINGS) { ring ->
                ctx.views?.widthAt(us[ring])?.let { it / toes * DRAWN_TOE_OVERLAP } ?: thickness
            }
            val centres = us.map { at ->
                val share = ctx.views?.widthAt(at)?.let { it * ((i + 0.5f) / toes - 0.5f) }
                val across = share ?: (rowHalf * place + fanOut * (at - u.start))
                val base = ctx.axis.pointAt(at) + ctx.axes.side * across
                if (ctx.views?.heightAt(at) != null) base else base + rest
            }
            SliceBuilder.sweep(
                SweepSpec(
                    "${name ?: DEFAULT_ROW_NAME}:$i", us, centres, heights, widths, section, ctx.axes.up,
                    EndClosure.ROUND, EndClosure.ROUND,
                ),
            )
        }
    }

    override fun lint(ctx: LintContext): List<Finding> =
        ctx.unit("u", u) + ctx.param("count", count) + ctx.param("spread", spread) + ctx.param("radius", radius) +
            ctx.unit("v", v)
}
