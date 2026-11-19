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
import dev.chieppa.squash.geom.ClumpBuilder
import dev.chieppa.squash.geom.ClumpShape
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.character.model.AreaSpec
import dev.chieppa.squash.character.model.AreaTag
import dev.chieppa.squash.geom.Slice
import dev.chieppa.squash.geom.SliceBuilder
import dev.chieppa.squash.geom.TubeSpec
import kotlinx.serialization.SerialName
import kotlin.math.ceil
import kotlinx.serialization.Serializable

/** The area a clump's tip colour fills. */
const val TIP_AREA = "tip"

/** The fewest rings a curved run's round tip closes over. */
private const val MIN_CURVED_CAP_RINGS = 2

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
    @Doc("The undrawn width as a fraction of the height: below 1 a run flat from side to side, above 1 one flat from top to bottom.")
    val width: Param = Param.of(1f),
    @Doc("Overrides the template's section.")
    val section: SectionSpec? = null,
    @Doc("How far the section's sides curl toward v = 0, as a fraction of its half span.")
    val cup: Param = Param.of(0f),
    @Doc("How much the section's sides thicken toward their edges, as a fraction of its half span: a flat section's rounded rim.")
    val rim: Param = Param.of(0f),
    @Doc("How far a closed end, round or pointed, reaches along the run when nothing is drawn, a fraction of its length: a round end reaching the run's half width there is a semicircular tip. A few rings when left out.")
    val capReach: Param? = null,
    @Doc("How far the run sits toward the underside when nothing is drawn, as a fraction of the axis length.")
    val drop: Param = Param.of(0f),
    @Doc("A toe row whose end this run reaches when the row has no toes.")
    val fills: String? = null,
    @Doc("Cut open at v = 0, so the run is a sheet: two faces meeting at an edge where v = 0 and v = 1 lie together.")
    val openV: Boolean = false,
    @Doc("A thick section with a hollow in its front in place of the template's section, its cup and its rim.")
    val shell: TubeShell? = null,
    @Doc("A clump of hair's shape in place of radius and taper when nothing is drawn.")
    val clump: TubeClump? = null,
) : Block {
    override fun run() = u

    override fun isSheet() = openV

    /** The stretch this build's slices cover: the file's u, out to the end of a row it fills that has no toes. */
    private fun span(ctx: BuildContext): Span {
        val row = fills?.let { name -> ctx.template.blocks.firstOrNull { it.name == name } } as? ToeRow ?: return u
        return if (row.toeCount(ctx) == 0) Span(u.start, row.u.end) else u
    }

    override fun patchNames() = listOf(PatchName(name ?: BODY_PATCH))

    override fun build(ctx: BuildContext): List<Patch> {
        val shaped = clumpOf(ctx)
        val patch = if (shaped != null) ClumpBuilder.patch(spec(ctx), shaped) else SliceBuilder.tube(spec(ctx))
        return listOf(if (openV) patch.openedInV() else patch)
    }

    override fun ringAt(ctx: BuildContext, u: Float): Slice? {
        val span = span(ctx)
        if (u < span.start || u > span.end) return null
        val shaped = clumpOf(ctx)
        return if (shaped != null) ClumpBuilder.ringAt(spec(ctx), shaped, u) else SliceBuilder.ringAt(spec(ctx), u)
    }

    /** The clump's shape where this run is a clump of hair and nothing is drawn, else null. */
    private fun clumpOf(ctx: BuildContext): ClumpShape? = clump?.takeIf { ctx.views == null }?.shape(ctx)

    override fun clumpInk(values: Map<String, Float>): ClumpInk? = clump?.ink(values)

    override fun areas(values: Map<String, Float>): List<AreaSpec> =
        clump?.let { listOf(AreaSpec(TIP_AREA, Span(it.tipStart(values), 1f), Span(0f, 1f), AreaTag.FILL, name ?: BODY_PATCH)) }.orEmpty()

    /** The least share of their size this run's rings keep where the axis bends tighter than they are wide. */
    internal fun bendHold(ctx: BuildContext): Float = SliceBuilder.bendHold(spec(ctx))

    private fun spec(ctx: BuildContext): TubeSpec {
        val span = span(ctx)
        return TubeSpec(
            name = name ?: BODY_PATCH,
            axis = ctx.axis,
            uStart = span.start,
            uEnd = span.end,
            rings = ctx.rings,
            roundRings = capRings(ctx),
            segments = ctx.template.topology.segments,
            section = shell?.flat(ctx, ctx.template.topology.segments)
                ?: (section ?: ctx.template.section).spline(ctx.template.topology.segments).rimmed(ctx.value(rim)).cupped(ctx.value(cup) + (clump?.let { ctx.value(it.cup) } ?: 0f)),
            radius = ctx.length(radius),
            taper = ctx.value(taper),
            widthRatio = ctx.value(width),
            startClosure = ctx.closure(End.START, span.start),
            endClosure = ctx.closure(End.END, span.end),
            jointUs = ctx.jointUs,
            drop = ctx.length(drop),
            socketNormal = ctx.axes.along,
            views = ctx.views,
            front = ctx.front,
            startHandover = ctx.handover(span.start, u),
            endHandover = ctx.handover(span.end, u),
            up = ctx.template.axis.up?.toVec3() ?: ctx.axes.up.takeIf { ctx.followsAxis },
            along = shell?.along(ctx, ctx.template.topology.segments),
            followsAxis = ctx.followsAxis,
        )
    }

    /**
     * How many rings a closed end takes to close when nothing is drawn: [capReach]'s share of them, else on a curved axis as many as
     * reach the run's half width at its tip, so a curl ends in a round tip as long as it is wide and keeps its curl to there; else the
     * builder's own few.
     */
    private fun capRings(ctx: BuildContext): Int? {
        val rings = ctx.rings
        val share = capReach?.let { ctx.value(it) }
            ?: if (ctx.curved) ctx.length(radius) * ctx.value(taper) / ctx.axisLength.coerceAtLeast(1e-6f) else return null
        return ceil(share * (rings - 1)).toInt().coerceIn(MIN_CURVED_CAP_RINGS.takeIf { capReach == null } ?: 1, rings - 1)
    }

    override fun lint(ctx: LintContext): List<Finding> =
        ctx.unit("u", u) + ctx.param("radius", radius) + ctx.param("taper", taper) + ctx.param("width", width) + ctx.section("section", section) +
            ctx.param("cup", cup) + ctx.param("rim", rim) + (capReach?.let { ctx.param("capReach", it) } ?: emptyList()) + ctx.param("drop", drop) + lintFills(ctx) +
            (shell?.lint(ctx) ?: emptyList()) + (clump?.lint(ctx) ?: emptyList())

    private fun lintFills(ctx: LintContext): List<Finding> {
        val name = fills ?: return emptyList()
        val row = ctx.block(name) as? ToeRow ?: return ctx.finding("fills", "'$name' is not a toe row")
        return if (row.u.end > u.end) emptyList() else ctx.finding("fills", "'$name' ends before this run does")
    }
}
