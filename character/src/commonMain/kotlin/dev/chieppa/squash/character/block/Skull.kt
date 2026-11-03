package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.geom.EndClosure
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.SliceBuilder
import dev.chieppa.squash.geom.TubeSpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("skull")
@Doc("A rounded block whose slices run front to back.")
data class Skull(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    val u: Span,
    @Doc("Full side to side extent, as a fraction of the axis length; the skull's own length when left out.")
    val width: Param? = null,
    @Doc("Full top to bottom extent, as a fraction of the axis length; the skull's own length when left out.")
    val depth: Param? = null,
) : Block {
    /** Round over half its rings each way, so with nothing drawn it is an ellipsoid. */
    override fun build(ctx: BuildContext): List<Patch> {
        val rings = ctx.template.topology.rings
        val own = (u.end - u.start) * ctx.axisLength
        val tall = depth?.let { ctx.length(it) } ?: own
        val wide = width?.let { ctx.length(it) } ?: own
        return listOf(
            SliceBuilder.tube(
                TubeSpec(
                    name = name ?: BODY_PATCH,
                    axis = ctx.axis,
                    uStart = u.start,
                    uEnd = u.end,
                    rings = rings,
                    segments = ctx.template.topology.segments,
                    section = ctx.template.section.spline(ctx.template.topology.segments),
                    radius = tall / 2f,
                    widthRatio = wide / tall,
                    roundRings = rings / 2,
                    startClosure = rounded(ctx.closure(End.START, u.start)),
                    endClosure = rounded(ctx.closure(End.END, u.end)),
                    jointUs = ctx.jointUs,
                    socketNormal = ctx.axes.along,
                    views = ctx.views,
                    front = ctx.front,
                ),
            ),
        )
    }

    private fun rounded(closure: EndClosure) = if (closure == EndClosure.FLAT) EndClosure.ROUND else closure

    override fun lint(ctx: LintContext): List<Finding> =
        ctx.unit("u", u) + ctx.param("width", width) + ctx.param("depth", depth)
}
