package dev.chieppa.squash.character.block

import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.build.UndrawnSilhouettes
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Rides
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.BlendedField
import dev.chieppa.squash.geom.field.DigitAxis
import dev.chieppa.squash.geom.field.DigitClaims
import dev.chieppa.squash.geom.field.FieldDigits
import dev.chieppa.squash.geom.field.FieldFit
import dev.chieppa.squash.geom.field.FieldLoft
import dev.chieppa.squash.geom.field.RingFrame
import kotlin.math.sin

/** How far behind its root, in its radii, a joined digit's run takes over from the field's. */
private const val SEAM = 0.6f

/** How far either side of the seam, in radii, both runs show the surface, so they overlap. */
private const val MARGIN = 1.1f

/** How far from its axis, in its radii, a joined digit's rays reach. */
private const val REACH = 2.0f

/** How far from its axis, in its radii, a joined digit owns the surface: well inside where its rays reach, so the two runs overlap. */
private const val CLAIM = 1.2f

/** Over how many radii either side of the seam a vertex's bones pass from the field's run to a digit's. */
private const val EASE = 0.6f

/** How far from its axis, in its radii, a digit's bones still move the surface, fading from where it owns the surface. */
private const val FOLLOW_OUT = 2.6f

/** Within how many radii of the nearest digit's axis another digit's shares a vertex's bones. */
private const val ACROSS = 0.35f

/** Of a joined digit's rings, how many run from its start inside the field to its root. */
private const val ROOT_RINGS = 4

/** How many of a joined digit's rings close its tip. */
private const val TIP_RINGS = 4

/**
 * A part's [Field] with a [Digits] row joined to it: one field holding both, the digits' axes, which digit owns which part of the
 * surface, and where each vertex's bones come from, so the field's run and each digit's meet on one surface and move as one in a pose.
 * See HLD: data-model, Block reference.
 */
internal class JoinedDigits private constructor(
    val ctx: BuildContext,
    val own: Field,
    val row: Digits,
    val built: List<Int>,
    val field: BlendedField,
    /** The field with each digit only as far as just past its seam: what the field's own run is cut through, so no ray of it wraps a digit. */
    val rootsField: BlendedField,
    val axes: List<DigitAxis>,
    val claims: DigitClaims,
    val spans: List<Span>,
    /** How the solid is fitted to the part's drawing, or null when it is not. */
    val fit: FieldFit?,
) {
    private val length = ctx.solidLength

    /** The patch name of the [k]th built digit. */
    fun patchName(k: Int) = row.patchOf(k, built[k])

    /** The u of the field's run at [a] along the axis, read where that plane comes from on the solid as it stands undrawn. */
    fun fieldU(a: Float): Float = (fit?.naturalAlong(a) ?: a) / length - own.from

    /** Where along the part's axis the field's run stops open: past every digit's seam by the overlap and a ring, inside the field's own reach. */
    fun fieldEnd(): Float {
        val (_, reached) = FieldLoft.reach(rootsField.withoutDigits())
        val wanted = axes.indices.maxOf { k ->
            val axis = axes[k]
            val seamAt = axis.at(claims.seam[k])
            seamAt.x + axis.radius * (MARGIN + 1f)
        }
        return minOf(wanted, reached - length * END_INSET)
    }

    /** The shares along digit [k] its rings stand at: from inside the field to its root, along it, and closing round its tip. */
    fun shares(k: Int, rings: Int): FloatArray {
        val axis = axes[k]
        val start = claims.seam[k] - axis.radius * MARGIN * 2f / axis.length
        val tipRadius = axis.radius * ctx.value(row.taper)
        val bodyEnd = 1f - tipRadius / axis.length
        val claw = clawOf(ctx, row)
        val tipRings = if (claw > 0f) TIP_RINGS + CLAW_RINGS else TIP_RINGS
        val body = (rings - ROOT_RINGS - tipRings).coerceAtLeast(2)
        val shares = FloatArray(ROOT_RINGS + body + tipRings)
        for (i in 0 until ROOT_RINGS) shares[i] = start * (1f - i.toFloat() / ROOT_RINGS)
        for (i in 0 until body) shares[ROOT_RINGS + i] = bodyEnd * i / (body - 1)
        for (i in 0 until tipRings) {
            val angle = (kotlin.math.PI / 2.0 * (i + 1) / tipRings).toFloat()
            shares[ROOT_RINGS + body + i] = bodyEnd + (1f + claw - bodyEnd) * sin(angle)
        }
        return shares
    }

    /** The u along the part of digit [k] at [share] along it, read from the template's numbers so its joints land on its rings. */
    fun uOf(k: Int, share: Float): Float = spans[k].start + (spans[k].end - spans[k].start) * share

    /** The share before which digit [k]'s run is buried, the field's run showing the surface there. */
    fun buriedBefore(k: Int): Float = claims.seam[k] - axes[k].radius * MARGIN / axes[k].length

    /** Which of the field run's points [patch] has buried: those a digit owns. */
    fun fieldBuried(patch: Patch, partAxes: PartAxes): BooleanArray =
        BooleanArray(patch.nu * patch.nv) { claims.owner(local(patch.point(it), partAxes), MARGIN) != null }

    /** Where each vertex of [patch] takes its bones from, by its place: the field's run and each digit's, as [DigitClaims.follow] shares them. */
    fun rides(patch: Patch, partAxes: PartAxes, fieldU: (Float) -> Float): Rides {
        val names = listOf(own.name ?: BODY_PATCH) + axes.indices.map(::patchName)
        val width = names.size
        val count = patch.nu * patch.nv
        val us = FloatArray(count * width)
        val shares = FloatArray(count * width)
        for (vertex in 0 until count) {
            val p = local(patch.point(vertex), partAxes)
            val follow = claims.follow(p, EASE, ACROSS, FOLLOW_OUT)
            us[vertex * width] = fieldU(p.x)
            shares[vertex * width] = 1f - follow.sum()
            for (k in axes.indices) {
                us[vertex * width + 1 + k] = uOf(k, axes[k].share(p).coerceIn(-1f, 1f))
                shares[vertex * width + 1 + k] = follow[k]
            }
        }
        return Rides(names, us, shares)
    }

    companion object {
        private const val END_INSET = 1e-3f

        /** How far from its axis, in its radii, a joined digit's rays reach. */
        const val REACH_RADII = REACH

        /** The joined field of [ctx]'s part, or null when no digits row there joins its field. */
        fun of(ctx: BuildContext): JoinedDigits? {
            val row = ctx.blocksOn.filterIsInstance<Digits>().firstOrNull { it.join != null } ?: return null
            val own = ctx.blocksOn.filterIsInstance<Field>().firstOrNull() ?: return null
            if (row.builtDigits(ctx).isEmpty()) return null
            return ctx.shared(row) { make(ctx, own, row) }
        }

        /** How far [row]'s claws stand past each tip with [ctx]'s values, as a share of the digit's length; 0 with nails or none. */
        private fun clawOf(ctx: BuildContext, row: Digits): Float =
            row.nail?.takeIf { it.isOn(ctx.values) }?.let { ctx.value(it.claw) } ?: 0f

        private fun make(ctx: BuildContext, own: Field, row: Digits): JoinedDigits {
            val built = row.builtDigits(ctx)
            val length = ctx.solidLength
            val reach = ctx.value(row.reach)
            val thickness = ctx.value(row.thickness)
            val claw = clawOf(ctx, row)
            val natural = built.map { i ->
                val digit = row.digits[i]
                val root = point(ctx, digit.root.map { Param.of(it) })
                val digitLength = digit.length * length * reach
                DigitAxis(own.cupped(ctx, root), digit.direction(ctx.value(row.spread)), digitLength, digit.radius * length * thickness, claw * digitLength)
            }
            val views = ctx.views
            val fit = if (own.fit && views != null) UndrawnSilhouettes.of(ctx).fitTo(views) else null
            val axes = if (fit == null) natural else natural.map(fit::axis)
            val palm = own.field(ctx).fitted(fit)
            val seams = FloatArray(axes.size) { emerges(palm, axes[it]) - SEAM * axes[it].radius / axes[it].length }
            val solids = DigitSolids.of(ctx, row, built, axes, seams)
            val join = ctx.value(requireNotNull(row.join)) * length
            val field = BlendedField(palm.parts, palm.floor, palm.bend, FieldDigits(solids.whole, join, solids.wholeJoins), palm.cup, fit)
            val rootsField = BlendedField(palm.parts, palm.floor, palm.bend, FieldDigits(solids.roots, join, solids.rootJoins), palm.cup, fit)
            return JoinedDigits(ctx, own, row, built, field, rootsField, axes, DigitClaims(axes, seams, CLAIM), built.map { row.span(it) }, fit)
        }

        /** How many of a digit's tip rings more a claw takes. */
        private const val CLAW_RINGS = 4

        /**
         * The share along [axis] where the digit comes out of [palm]: the first, from a little behind its root, whose axis lies within
         * [EMERGE] radii of the palm's surface, so a digit rooted deep in a pad hands over from the palm's run where it stands clear.
         */
        private fun emerges(palm: BlendedField, axis: DigitAxis): Float {
            val step = axis.radius * EMERGE_STEP / axis.length
            var share = -EMERGE_BEHIND * axis.radius / axis.length
            while (share < 1f && palm.value(axis.at(share)) < -EMERGE * axis.radius) share += step
            return share.coerceAtMost(1f)
        }

        private const val EMERGE = 1f
        private const val EMERGE_STEP = 0.1f
        private const val EMERGE_BEHIND = 1f

        /** The direction of a digit's back, v = 0.25 round its rings, for a digit along [direction] rolled [roll] radians. */
        fun backOf(direction: Vec3, roll: Float): Vec3 = RingFrame.of(direction, roll).side

        /** [world], a point of a patch in [axes], in the part's own axes. */
        fun local(world: Vec3, axes: PartAxes): Vec3 = Vec3(world dot axes.along, world dot axes.up, world dot axes.side)
    }
}
