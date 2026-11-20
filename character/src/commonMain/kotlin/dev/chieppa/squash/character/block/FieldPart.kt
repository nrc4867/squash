package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.Blended
import dev.chieppa.squash.geom.field.Capsule
import dev.chieppa.squash.geom.field.Ellipsoid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val COORDINATES = 3

/** Which simple solid a [FieldPart] is. */
@Serializable
enum class FieldShape {
    @SerialName("ellipsoid") ELLIPSOID,
    @SerialName("capsule") CAPSULE,
}

/** One solid of a [Field], in the part's own axes (along, up, side) as fractions of the axis length. */
@Serializable
@Doc("One solid of a field, in the part's own axes (along, up, side) as fractions of the axis length.")
data class FieldPart(
    @Doc("A name an author can know the solid by, a palm or a heel.")
    val name: String? = null,
    @SerialName("if") val condition: ParamRef? = null,
    val shape: FieldShape,
    @Doc("The ellipsoid's centre, or the capsule's first end: along, up, side.")
    val at: List<Param>,
    @Doc("The capsule's second end.")
    val to: List<Param>? = null,
    @Doc("The ellipsoid's semi-axes along, up and side.")
    val radii: List<Param>? = null,
    @Doc("The capsule's radius at its first end.")
    val radius: Param? = null,
    @Doc("The capsule's radius at its second end; its first radius when left out.")
    val toRadius: Param? = null,
    @Doc("The radius of the smooth union joining this solid to those before it; 0 joins them by a plain union.")
    val blend: Param = Param.of(0f),
    @Doc("How much shorter the ellipsoid's side semi-axis is toward the part's start, as a share.")
    val narrows: Param = Param.of(0f),
    @Doc("Over how far either side of the centre the narrowing eases in.")
    val over: Param = Param.of(0f),
) {
    fun blended(ctx: BuildContext): Blended {
        val primitive = when (shape) {
            FieldShape.ELLIPSOID -> Ellipsoid(
                point(ctx, at), point(ctx, requireNotNull(radii) { "An ellipsoid needs radii" }), ctx.value(narrows), solid(ctx, over),
            )
            FieldShape.CAPSULE -> {
                val first = solid(ctx, requireNotNull(radius) { "A capsule needs a radius" })
                Capsule(point(ctx, at), point(ctx, requireNotNull(to) { "A capsule needs a second end" }), first, toRadius?.let { solid(ctx, it) } ?: first)
            }
        }
        return Blended(primitive, solid(ctx, blend))
    }

    fun lint(ctx: LintContext, field: String): List<Finding> = buildList {
        if (at.size != COORDINATES) addAll(ctx.finding("$field.at", "a place is three numbers"))
        when (shape) {
            FieldShape.ELLIPSOID -> if (radii?.size != COORDINATES) addAll(ctx.finding("$field.radii", "an ellipsoid needs three radii"))
            FieldShape.CAPSULE -> {
                if (to?.size != COORDINATES) addAll(ctx.finding("$field.to", "a capsule needs a second end"))
                if (radius == null) addAll(ctx.finding("$field.radius", "a capsule needs a radius"))
            }
        }
        (at + to.orEmpty() + radii.orEmpty() + listOfNotNull(radius, toRadius, blend, narrows, over)).forEach { addAll(ctx.param(field, it)) }
    }
}

/** A flat floor a [Field] is cut at. */
@Serializable
@Doc("A flat floor the field is cut at, up from the axis as a fraction of the axis length, joined by a smooth intersection.")
data class FieldFloor(
    val at: Param,
    val blend: Param = Param.of(0f),
    @Doc("Cuts across the axis at `at` along it instead, keeping what lies short of it: the sole of a part that points down, a hoof.")
    val across: Boolean = false,
)

/** A turn of a [Field]'s forward part about the part's side axis. */
@Serializable
@Doc("A turn about the side axis through a point, toward the part's up for positive degrees, eased in along the axis from `from` to `to` past the point.")
data class FieldBend(val at: List<Param>, val degrees: Param, val from: Param, val to: Param)

/** [values], along, up and side as fractions of the axis length, in body units in the part's own axes. */
internal fun point(ctx: BuildContext, values: List<Param>): Vec3 = Vec3(solid(ctx, values[0]), solid(ctx, values[1]), solid(ctx, values[2]))

/** [param], a fraction of the axis length, in body units at the part's size, whatever is drawn. */
internal fun solid(ctx: BuildContext, param: Param): Float = ctx.value(param) * ctx.solidLength
