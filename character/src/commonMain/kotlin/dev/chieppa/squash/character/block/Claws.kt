package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.AreaSpec
import dev.chieppa.squash.character.model.AreaTag
import dev.chieppa.squash.character.model.DigitChain
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.JointSpec
import dev.chieppa.squash.character.model.Limits
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.PatchName
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.character.model.SurfaceMethod
import dev.chieppa.squash.geom.EndClosure
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.SectionProfile
import dev.chieppa.squash.geom.SliceBuilder
import dev.chieppa.squash.geom.SweepSpec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val DEFAULT_NAME = "claws"
private const val SEPARATOR = ":"
private const val MIN_SEGMENTS = 6
private const val DEGREES_TO_RADIANS = (kotlin.math.PI / 180.0).toFloat()

/** The full u span of a claw's patch, anchor to point. */
private val CLAW_SPAN = Span(ClawU.ANCHOR, ClawU.POINT)

/**
 * Hard claws on the tips of a [Digits] row joined to the part's field: each its own small mesh, not in the field, named for the
 * row's digit it sits on (`claws:0`, `claws:thumb`), with its own two bones hanging from that digit's last joint: `<patch>.1` at a
 * hidden anchor inside the toe and `<patch>.2` at the claw's root, so a stretch of the second toward the first slides the claw back
 * into the toe and the pose stays plain skinning. See HLD: parts, Claws.
 */
@Serializable
@SerialName("claws")
@Doc("Hard claws on the tips of a digits row joined to the field listed before it: each its own mesh past the digit's tip, hooked to a sharp point with a collar round its root, inked and coloured on its own, riding two bones of its own from the digit's last joint so a pose slides it back into the toe.")
data class Claws(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    @Doc("The digits row whose tips it sits on. The row is listed before the claws.")
    val on: String,
    @Doc("How far each claw reaches past its tip, as a share of its digit's length.")
    val length: Param,
    @Doc("Each claw's height at its root, as a share of the tip's thickness.")
    val thickness: Param = Param.of(DEFAULT_THICKNESS),
    @Doc("How far each claw hooks toward the digit's underside from root to point: 0 straight, 1 a full hook.")
    val hook: Param = Param.of(DEFAULT_HOOK),
    @Doc("How wide the collar round each claw's root swells past it, 0 to 1: the skin it leaves the toe through.")
    val sheath: Param = Param.of(DEFAULT_SHEATH),
    @Doc("Points round each claw's section, an even number.")
    val segments: Int = DEFAULT_SEGMENTS,
    @Doc("The palette slot the claws show until filled; the collar shows the part's own colour.")
    val color: String = DEFAULT_COLOR,
    @Doc("Which of the part's axes each claw's bones turn about, as the row's joints do.")
    val hinge: Hinge = Hinge.X,
    @Doc("Degrees each claw's root joint turns within.")
    val limits: Limits? = null,
) : Block {
    private val patchName get() = name ?: DEFAULT_NAME

    override fun patchNames() = listOf(PatchName(patchName, repeats = true))

    override fun surfaceMethod() = SurfaceMethod.SEPARATE

    override fun areas(values: Map<String, Float>): List<AreaSpec> = listOf(
        AreaSpec(patchName, Span(ClawU.ROOT, ClawU.POINT), Span(0f, 1f), AreaTag.FILL, patchName, color),
        AreaSpec(SHEATH_AREA, Span(ClawU.ANCHOR, ClawU.ROOT), Span(0f, 1f), AreaTag.FILL, patchName),
    )

    /** The claw on each digit of the row: a chain of two joints hanging from the digit's last, rigid so the claw stays hard. */
    override fun digitChains(patches: Set<String>): List<DigitChain> {
        val prefix = patchName + SEPARATOR
        val claws = patches.filter { it.startsWith(prefix) }.sortedWith(compareBy({ it.removePrefix(prefix).toIntOrNull() ?: Int.MAX_VALUE }, { it }))
        val counted = claws.count { it.removePrefix(prefix).toIntOrNull() != null }
        return claws.map { patch ->
            val digit = patch.removePrefix(prefix)
            val joints = listOf(ClawU.ANCHOR, ClawU.COLLAR).mapIndexed { j, u -> JointSpec("$patch.${j + 1}", u, null, hinge, limits, stretch = false) }
            DigitChain(patch, digit.toIntOrNull() ?: counted, CLAW_SPAN, false, joints, on = on + SEPARATOR + digit, rigid = true)
        }
    }

    override fun build(ctx: BuildContext): List<Patch> {
        val joined = JoinedDigits.of(ctx)?.takeIf { it.row.rowName == on } ?: return emptyList()
        val shape = ClawShape(ctx.value(length), ctx.value(thickness), ctx.value(hook), ctx.value(sheath).coerceIn(0f, 1f))
        val taper = ctx.value(joined.row.taper)
        val section = SectionProfile.CIRCLE.spline(segments)
        return joined.axes.indices.map { k ->
            ctx.checkCancelled()
            val digit = joined.row.digits[joined.built[k]]
            val back = JoinedDigits.backOf(joined.axes[k].direction, digit.roll * DEGREES_TO_RADIANS)
            val rings = shape.rings(joined.axes[k], taper, back)
            val world = { p: dev.chieppa.squash.geom.Vec3 -> ctx.axes.point(p.x, p.y, p.z) }
            val patch = SliceBuilder.sweep(
                SweepSpec(
                    patchName + SEPARATOR + joined.patchName(k).substringAfter(SEPARATOR), rings.us, rings.centres.map(world),
                    rings.breadths, rings.heights, section, world(rings.side), EndClosure.OPEN, EndClosure.OPEN,
                ),
            )
            patch.withBuried(BooleanArray(patch.nu * patch.nv) { it < patch.nv }, null)
        }
    }

    override fun lint(ctx: LintContext): List<Finding> {
        val row = ctx.template.blocks.firstOrNull { it is Digits && it.rowName == on }
        val placed = when {
            row !is Digits -> ctx.finding("on", "'$on' is not a digits row")
            row.join == null -> ctx.finding("on", "'$on' is not joined to a field")
            ctx.template.blocks.indexOf(row) > ctx.template.blocks.indexOf(this) -> ctx.finding("on", "'$on' comes after these claws")
            else -> emptyList()
        }
        return placed + ctx.param("length", length) + ctx.param("thickness", thickness) + ctx.param("hook", hook) + ctx.param("sheath", sheath) +
            ctx.atLeast("segments", segments, MIN_SEGMENTS) +
            (if (segments % 2 != 0) ctx.finding("segments", "a claw's segments are an even number") else emptyList())
    }

    companion object {
        /** The area of each claw's collar, which shows the part's own colour until filled. */
        const val SHEATH_AREA = "sheath"
        private const val DEFAULT_THICKNESS = 0.5f
        private const val DEFAULT_HOOK = 0.5f
        private const val DEFAULT_SHEATH = 0.5f
        private const val DEFAULT_SEGMENTS = 12
        private const val DEFAULT_COLOR = "claw"
    }
}
