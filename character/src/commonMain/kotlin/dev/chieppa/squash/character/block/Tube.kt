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
) : Block {
    override fun run() = u

    override fun patchNames() = listOf(PatchName(name ?: BODY_PATCH))

    override fun build(ctx: BuildContext): List<Patch> = listOf(
        SliceBuilder.tube(
            TubeSpec(
                name = name ?: BODY_PATCH,
                axis = ctx.axis,
                uStart = u.start,
                uEnd = u.end,
                rings = ctx.template.topology.rings,
                segments = ctx.template.topology.segments,
                section = (section ?: ctx.template.section).spline(ctx.template.topology.segments).cupped(ctx.value(cup)),
                radius = ctx.length(radius),
                taper = ctx.value(taper),
                startClosure = ctx.closure(End.START, u.start),
                endClosure = ctx.closure(End.END, u.end),
                jointUs = ctx.jointUs,
                drop = ctx.length(drop),
                socketNormal = ctx.axes.along,
                views = ctx.views,
                front = ctx.front,
                startCovered = ctx.covered(u.start, u),
                endCovered = ctx.covered(u.end, u),
            ),
        ),
    )

    override fun lint(ctx: LintContext): List<Finding> =
        ctx.unit("u", u) + ctx.param("radius", radius) + ctx.param("taper", taper) + ctx.section("section", section) +
            ctx.param("cup", cup) + ctx.param("drop", drop)
}
