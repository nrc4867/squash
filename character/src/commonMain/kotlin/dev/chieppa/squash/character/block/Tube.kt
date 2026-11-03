package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.SectionSpec
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.geom.Patch
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
) : Block {
    override fun build(ctx: BuildContext): List<Patch> = listOf(
        SliceBuilder.tube(
            TubeSpec(
                name = name ?: BODY_PATCH,
                axis = ctx.axis,
                uStart = u.start,
                uEnd = u.end,
                rings = ctx.template.topology.rings,
                segments = ctx.template.topology.segments,
                section = (section ?: ctx.template.section).spline(ctx.template.topology.segments),
                radius = ctx.length(radius),
                taper = ctx.value(taper),
                startClosure = ctx.closure(End.START, u.start),
                endClosure = ctx.closure(End.END, u.end),
                jointUs = ctx.jointUs,
            ),
        ),
    )

    override fun lint(ctx: LintContext): List<Finding> =
        ctx.unit("u", u) + ctx.param("radius", radius) + ctx.param("taper", taper) + ctx.section("section", section)
}
