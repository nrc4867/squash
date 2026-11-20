package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.build.UndrawnSilhouettes
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.PatchName
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.field.BlendedField
import dev.chieppa.squash.geom.field.Blended
import dev.chieppa.squash.geom.field.DrawnSolid
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.Bend
import dev.chieppa.squash.geom.field.Cup
import dev.chieppa.squash.geom.field.DrawnRing
import dev.chieppa.squash.geom.field.FieldLoft
import dev.chieppa.squash.geom.ViewSizing
import dev.chieppa.squash.geom.OutlineProfile
import dev.chieppa.squash.geom.field.Floor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DEGREES_TO_RADIANS = (kotlin.math.PI / 180.0).toFloat()

/** How far out, in half widths, the cup keeps growing; [BlendedField]'s own. */
private const val CUP_REACH = 1.5f

/** The smooth intersection a drawn Side's lowest point cuts the kept solids at, as a share of the axis length. */
private const val SOLE_BLEND = 0.01f

/** Points round the template's section a drawn solid reads it at. */
private const val DRAWN_SECTION_POINTS = 96

/**
 * A run built from a smooth union of ellipsoids and capsules: planar rings across the axis, each the star-shaped section of the
 * field, closing round at both ends. See HLD: data-model, Block reference.
 */
@Serializable
@SerialName("field")
@Doc("A run of rings cut through a smooth union of ellipsoids and capsules, placed in the part's own axes as fractions of the axis length.")
data class Field(
    override val name: String? = null,
    @SerialName("if") override val condition: dev.chieppa.squash.character.model.ParamRef? = null,
    @Doc("The stretch of u the rings cover with the template's defaults.")
    val u: Span,
    @Doc("Where u = 0 lies along the axis from the socket plane, as a fraction of the axis length; negative behind it.")
    val from: Float = 0f,
    @Doc("The solids, each joined to those before it by a smooth union of its blend radius.")
    val parts: List<FieldPart>,
    @Doc("A flat floor the solid is cut at, a sole.")
    val floor: FieldFloor? = null,
    @Doc("A turn of everything past a point about the part's side axis, eased in along the axis: a forefoot kept flat on a pointed foot.")
    val bend: FieldBend? = null,
    @Doc("How far the first solid's edges come toward the part's negative side, as a fraction of the axis length, growing as the square of how far they lie from its middle: a cupped palm. Digits joined to the field keep their roots on it.")
    val cup: Param = Param.of(0f),
    @Doc("Whether drawn Side and Top outlines fit the whole solid, digits and all: its reach along the axis, and its height, width and midline at each point along it, take the drawing's, the template's numbers shaping what lies within.")
    val fit: Boolean = false,
    @Doc("With fit, whether the drawn outlines are the solid itself: Side and Top its silhouettes and Front its section, the template's section where Front is not drawn, joined to the solids marked kept; the template's solids then only place the digits, joints, areas and sockets on it, and a row of digits standing apart cuts it back at their roots.")
    val drawn: Boolean = false,
) : Block {
    override fun run() = u

    override fun patchNames() = listOf(PatchName(name ?: BODY_PATCH))

    override fun build(ctx: BuildContext): List<Patch> {
        val length = ctx.solidLength
        JoinedDigits.of(ctx)?.let { joined -> return listOf(joinedRun(ctx, joined)) }
        val views = ctx.views
        drawnField(ctx)?.let { solid -> return listOf(drawnRun(ctx, views!!, solid)) }
        if (fit && views != null) return listOf(fittedRun(ctx, views))
        val field = field(ctx)
        val drawn: (Float) -> DrawnRing? = if (views == null) { _ -> null } else { u ->
            val at = views.uAt((u + from) * length)
            if (at <= 0f || at >= 1f) null else views.heightAt(at).let { height -> DrawnRing(height, views.widthAt(at), height?.let { views.centreAt(at) }) }
        }
        val patch = FieldLoft.loft(
            name ?: BODY_PATCH, field, ctx.axes, ctx.template.topology.rings, ctx.template.topology.segments,
            uOf = { a -> a / length - from }, drawn = drawn, checkCancelled = ctx.checkCancelled,
        )
        return listOf(patch)
    }

    /** The run over the field fitted to the drawing, its u read where each ring's plane comes from on the field as it stands undrawn. */
    private fun fittedRun(ctx: BuildContext, views: ViewSizing): Patch {
        val length = ctx.solidLength
        val natural = field(ctx)
        val fitted = natural.fitted(UndrawnSilhouettes.of(ctx).fitTo(views))
        val toNatural = { a: Float -> fitted.fit?.naturalAlong(a) ?: a }
        return FieldLoft.loft(
            name ?: BODY_PATCH, fitted, ctx.axes, ctx.template.topology.rings, ctx.template.topology.segments,
            uOf = { a -> toNatural(a) / length - from }, checkCancelled = ctx.checkCancelled,
        )
    }

    /** The run over the drawn solid, its u read where each ring's plane comes from on the field as it stands undrawn. */
    private fun drawnRun(ctx: BuildContext, views: ViewSizing, solid: BlendedField): Patch {
        val length = ctx.solidLength
        val fit = UndrawnSilhouettes.of(ctx).fitTo(views)
        return FieldLoft.loft(
            name ?: BODY_PATCH, solid, ctx.axes, ctx.template.topology.rings, ctx.template.topology.segments,
            uOf = { a -> (fit?.naturalAlong(a) ?: a) / length - from }, checkCancelled = ctx.checkCancelled,
        )
    }

    /**
     * The solid the drawing makes where this field takes it as its own ([fit] and [drawn], with Side or Top drawn, the view not drawn
     * taking the template's own silhouette as the drawn one stretches it, and each section the template's own there where Front is not
     * drawn), joined to the parts
     * that are [FieldPart.kept], on a field with a floor cut flat at Side's lowest point so nothing kept reaches below the drawn sole, and cupped as the template
     * cups it; null where the drawing does not make it.
     */
    fun drawnField(ctx: BuildContext): BlendedField? {
        if (!fit || !drawn) return null
        val views = ctx.views ?: return null
        val drawnHeight = ctx.drawnHeight
        val drawnWidth = ctx.drawnWidth
        if (drawnHeight == null && drawnWidth == null) return null
        val silhouettes = UndrawnSilhouettes.of(ctx)
        val fit = silhouettes.fitTo(views)
        val height = drawnHeight ?: fit?.let { silhouettes.carriedHeight(it, ctx.drawingScale) }
        val width = drawnWidth ?: fit?.let { silhouettes.carriedWidth(it, ctx.drawingScale) }
        val section = ctx.drawnFront ?: ctx.template.section.spline(ctx.template.topology.segments).sample(DRAWN_SECTION_POINTS)
        val natural = field(ctx)
        val sectionsAlong = if (ctx.drawnFront != null) null else { x: Float -> FieldLoft.sectionAt(natural, fit?.naturalAlong(x) ?: x, DRAWN_SECTION_POINTS) }
        val solid = DrawnSolid(height, width, ctx.drawingScale, section, sectionsAlong)
        val kept = parts.filter { part -> part.kept && (part.condition?.let { ctx.value(Param("$" + it.name)) != 0f } ?: true) }
        val sole = height?.takeIf { floor != null }?.let { profile -> Floor(profile.run().minOf { it.y } * ctx.drawingScale, ctx.solidLength * SOLE_BLEND) }
        val cup = cupOf(ctx)?.let { natural -> height?.let { drawnCup(natural, it, ctx.drawingScale) } ?: natural }
        return BlendedField(listOf(Blended(solid)) + kept.map { it.blended(ctx) }, floor = sole, cup = cup)
    }

    /**
     * The run over the field with digits joined to it, each digit only as far as just past its seam: rays stop where they first leave
     * the solid, and the run stops open past the digits' seams; what the digits own is buried, and every vertex takes its bones by its place.
     */
    private fun joinedRun(ctx: BuildContext, joined: JoinedDigits): Patch {
        val uOf = joined::fieldU
        val patch = FieldLoft.loft(
            name ?: BODY_PATCH, joined.rootsField, ctx.axes, ctx.template.topology.rings, ctx.template.topology.segments,
            uOf = uOf, checkCancelled = ctx.checkCancelled, end = joined.fieldEnd(), firstCrossing = true, shadedBy = joined.field,
        )
        return patch.withBuried(joined.fieldBuried(patch, ctx.axes), joined.rides(patch, ctx.axes, uOf)).withOpenEnds()
    }

    /** The field this block builds with [ctx]'s values, in the part's own axes and in body units. */
    fun field(ctx: BuildContext): BlendedField {
        val on = parts.filter { part -> part.condition?.let { ctx.value(Param("$" + it.name)) != 0f } ?: true }
        return BlendedField(
            on.map { it.blended(ctx) },
            floor?.let { Floor(solid(ctx, it.at), solid(ctx, it.blend), it.across) },
            bend?.let { bend ->
                val at = point(ctx, bend.at)
                Bend(at, ctx.value(bend.degrees) * DEGREES_TO_RADIANS, at.x + solid(ctx, bend.from), at.x + solid(ctx, bend.to))
            },
            cup = cupOf(ctx),
        )
    }

    /** The cup of the first solid, about its middle and across its half width up, or null with none. */
    private fun cupOf(ctx: BuildContext): Cup? {
        val depth = solid(ctx, cup)
        if (depth == 0f) return null
        val first = parts.firstOrNull() ?: return null
        val middle = solid(ctx, first.at[1])
        val halfWidth = first.radii?.let { solid(ctx, it[1]) } ?: first.radius?.let { solid(ctx, it) } ?: return null
        return Cup(depth, middle, halfWidth)
    }

    /** [natural]'s depth across the drawn solid: about the middle and over the half width of Side at its widest, times [scale]. */
    private fun drawnCup(natural: Cup, side: OutlineProfile, scale: Float): Cup {
        val run = side.run()
        val widest = run.map { it.x }.distinct().maxBy { side.extentAt(it) }
        return Cup(natural.depth, side.midAt(widest) * scale, side.extentAt(widest) * scale / 2f)
    }

    /** Where [p], a point on the uncupped solid, lies once the field is cupped, as a digit's root rides the palm. */
    fun cupped(ctx: BuildContext, p: Vec3): Vec3 {
        val c = cupOf(ctx) ?: return p
        val across = ((p.y - c.middle) / c.halfWidth).coerceIn(-CUP_REACH, CUP_REACH)
        return Vec3(p.x, p.y, p.z - c.depth * across * across)
    }

    override fun lint(ctx: LintContext): List<Finding> =
        ctx.unit("u", u) + ctx.param("cup", cup) + (if (parts.isEmpty()) ctx.finding("parts", "a field needs a solid") else emptyList()) +
            parts.flatMapIndexed { i, part -> part.lint(ctx, "parts[$i]") } + lintFloor(ctx)

    private fun lintFloor(ctx: LintContext): List<Finding> {
        val floor = floor ?: return emptyList()
        return when {
            floor.hollow !in 0f..1f -> ctx.finding("floor.hollow", "${floor.hollow} is outside 0 to 1")
            floor.rim !in 0.05f..0.9f -> ctx.finding("floor.rim", "${floor.rim} is outside 0.05 to 0.9")
            floor.hollow > 0f && !floor.across -> ctx.finding("floor.hollow", "only a floor across the axis dishes its soles")
            else -> emptyList()
        }
    }
}
