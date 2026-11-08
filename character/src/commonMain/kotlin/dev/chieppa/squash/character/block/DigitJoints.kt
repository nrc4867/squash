package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Hinge
import dev.chieppa.squash.character.model.Limits
import kotlinx.serialization.Serializable

/** The joints along every digit of a toe row. See HLD: data-model, The rig. */
@Serializable
@Doc("Joints along every digit of the row: where each sits as a share of the digit's span from its root, about which hinge, within which limits.")
data class DigitJoints(
    @Doc("Each joint's place along a digit, from 0 at its root to 1 at its tip, ascending.")
    val at: List<Float>,
    val hinge: Hinge = Hinge.X,
    @Doc("Degrees; a positive tip turns the digit toward the row's underside.")
    val limits: Limits? = null,
) {
    fun lint(ctx: LintContext, field: String): List<Finding> =
        lintShares(ctx, "$field.at", at) +
            (limits?.takeIf { it.min > it.max }?.let { ctx.finding("$field.limits", "minimum is above maximum") } ?: emptyList())

    companion object {
        /** Findings for joint shares [at] that leave 0 to 1 or do not ascend. */
        fun lintShares(ctx: LintContext, field: String, at: List<Float>): List<Finding> =
            at.flatMapIndexed { i, share -> ctx.unit("$field[$i]", share) } +
                (if (at.zipWithNext().any { (a, b) -> b <= a }) ctx.finding(field, "joints are not in ascending order") else emptyList())
    }
}
