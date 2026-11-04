package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.PatchName
import dev.chieppa.squash.character.model.SectionSpec
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Slice
import dev.chieppa.squash.geom.SliceBuilder
import dev.chieppa.squash.geom.TubeSpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("tube")
@Doc("A run of slices; the views resize them.")
data class Tube(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    val u: Span,
    @Doc("A fraction of the axis length.")
    val radius: Param,
    @Doc("The radius at the far end, as a fraction of the radius at the near end.")
    val taper: Param = Param.of(1f),
    @Doc("Overrides the template's section.")
    val section: SectionSpec? = null,
    @Doc("How far the section's sides curl toward v = 0, as a fraction of its half span.")
    val cup: Param = Param.of(0f),
    @Doc("How far the run sits toward the underside when nothing is drawn, as a fraction of the axis length.")
    val drop: Param = Param.of(0f),
    @Doc("A toe row whose end this run reaches when the row has no toes.")
    val fills: String? = null,
) : Block {
    override fun run() = u

    /** The stretch this build's slices cover: the file's u, out to the end of a row it fills that has no toes. */
    private fun span(ctx: BuildContext): Span {
        val row = fills?.let { name -> ctx.template.blocks.firstOrNull { it.name == name } } as? ToeRow ?: return u
        return if (row.toeCount(ctx) == 0) Span(u.start, row.u.end) else u
    }

    override fun patchNames() = listOf(PatchName(name ?: BODY_PATCH))

    override fun build(ctx: BuildContext): List<Patch> = listOf(SliceBuilder.tube(spec(ctx)))

    override fun ringAt(ctx: BuildContext, u: Float): Slice? {
        val span = span(ctx)
        return if (u >= span.start && u <= span.end) SliceBuilder.ringAt(spec(ctx), u) else null
    }

    private fun spec(ctx: BuildContext): TubeSpec {
        val span = span(ctx)
        return TubeSpec(
            name = name ?: BODY_PATCH,
            axis = ctx.axis,
            uStart = span.start,
            uEnd = span.end,
            rings = ctx.template.topology.rings,
            segments = ctx.template.topology.segments,
            section = (section ?: ctx.template.section).spline(ctx.template.topology.segments).cupped(ctx.value(cup)),
            radius = ctx.length(radius),
            taper = ctx.value(taper),
            startClosure = ctx.closure(End.START, span.start),
            endClosure = ctx.closure(End.END, span.end),
            jointUs = ctx.jointUs,
            drop = ctx.length(drop),
            socketNormal = ctx.axes.along,
            views = ctx.views,
            front = ctx.front,
            startCovered = ctx.covered(span.start, u),
            endCovered = ctx.covered(span.end, u),
            up = ctx.template.axis.up?.toVec3(),
        )
    }

    override fun lint(ctx: LintContext): List<Finding> =
        ctx.unit("u", u) + ctx.param("radius", radius) + ctx.param("taper", taper) + ctx.section("section", section) +
            ctx.param("cup", cup) + ctx.param("drop", drop) + lintFills(ctx)

    private fun lintFills(ctx: LintContext): List<Finding> {
        val name = fills ?: return emptyList()
        val row = ctx.block(name) as? ToeRow ?: return ctx.finding("fills", "'$name' is not a toe row")
        return if (row.u.end > u.end) emptyList() else ctx.finding("fills", "'$name' ends before this run does")
    }
}
