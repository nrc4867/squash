package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.build.BuildContext
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.geom.SectionAlong
import dev.chieppa.squash.geom.ShellSection
import kotlinx.serialization.Serializable

/** How much of the run's length the hollow takes to reach its full depth past either end of its stretch. */
private const val HOLLOW_EASE = 0.2f

/**
 * A thick section with a hollow dished into its front, the tube's section in place of the template's: a full round back, a rim along each
 * side, and between the rims a hollow that deepens over [u] and is flat outside it. The hollow lies over v [0.85, 0.15] at every depth, so
 * an area there follows it. See HLD: surface, Ears in the field.
 */
@Serializable
@Doc("A thick section with a hollow dished into its front: a full round back, a rim along each side and a hollow between the rims.")
data class TubeShell(
    @Doc("How deep the section is from front to back, a fraction of its half width.")
    val thickness: Param,
    @Doc("How deep the hollow dishes into the front, a fraction of the thickness, up to 0.4.")
    val hollow: Param,
    @Doc("How wide the rim along each side is, a fraction of the half width.")
    val rim: Param,
    @Doc("The stretch of u the hollow runs over, deepening from its start and shallowing toward its end.")
    val u: Span = Span(0f, 1f),
) {
    /** The flat shell, the run's own section. */
    fun flat(ctx: BuildContext, segments: Int) = ShellSection.of(segments, ctx.value(thickness), 0f, ctx.value(rim))

    /** The hollow the flat shell turns into over [u]. */
    fun along(ctx: BuildContext, segments: Int): SectionAlong? {
        val depth = ctx.value(hollow)
        if (depth <= 0f) return null
        return SectionAlong(ShellSection.of(segments, ctx.value(thickness), depth, ctx.value(rim)), u.start, u.end, HOLLOW_EASE)
    }

    fun lint(ctx: LintContext): List<Finding> =
        ctx.param("shell.thickness", thickness) + ctx.param("shell.hollow", hollow) + ctx.param("shell.rim", rim) + ctx.unit("shell.u", u)
}
