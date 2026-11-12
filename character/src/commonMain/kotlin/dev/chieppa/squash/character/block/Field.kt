package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.PatchName
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.field.BlendedField
import dev.chieppa.squash.geom.field.Bend
import dev.chieppa.squash.geom.field.DrawnRing
import dev.chieppa.squash.geom.field.FieldLoft
import dev.chieppa.squash.geom.field.Floor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DEGREES_TO_RADIANS = (kotlin.math.PI / 180.0).toFloat()

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
) : Block {
    override fun run() = u

    override fun patchNames() = listOf(PatchName(name ?: BODY_PATCH))

    override fun build(ctx: BuildContext): List<Patch> {
        val length = ctx.solidLength
        val field = field(ctx)
        val views = ctx.views
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

    /** The field this block builds with [ctx]'s values, in the part's own axes and in body units. */
    fun field(ctx: BuildContext): BlendedField {
        val on = parts.filter { part -> part.condition?.let { ctx.value(Param("$" + it.name)) != 0f } ?: true }
        return BlendedField(
            on.map { it.blended(ctx) },
            floor?.let { Floor(solid(ctx, it.at), solid(ctx, it.blend)) },
            bend?.let { bend ->
                val at = point(ctx, bend.at)
                Bend(at, ctx.value(bend.degrees) * DEGREES_TO_RADIANS, at.x + solid(ctx, bend.from), at.x + solid(ctx, bend.to))
            },
        )
    }

    override fun lint(ctx: LintContext): List<Finding> =
        ctx.unit("u", u) + (if (parts.isEmpty()) ctx.finding("parts", "a field needs a solid") else emptyList()) +
            parts.flatMapIndexed { i, part -> part.lint(ctx, "parts[$i]") }
}
