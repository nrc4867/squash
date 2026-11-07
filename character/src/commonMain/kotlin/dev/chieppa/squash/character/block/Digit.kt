package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Span
import kotlinx.serialization.Serializable

/** The widest a digit may sit from the row's middle, either way, as a share of the row's half width. */
private const val MAX_ACROSS = 1.5f

/** One digit of a toe row moved and angled apart from the fan, as a thumb is. Each field left out keeps the row's own. */
@Serializable
@Doc("One digit of a toe row moved and angled apart from the fan, as a thumb is; a field left out keeps the row's.")
data class Digit(
    @Doc("The digit's place in the row, from 0.")
    @Range(min = 0.0)
    val index: Int,
    @Doc("Where the digit runs along the axis, in place of the row's span.")
    val u: Span? = null,
    @Doc("Where across the row the digit's root sits, -1 at the first side and 1 at the last.")
    @Range(min = -1.5, max = 1.5)
    val across: Float? = null,
    @Doc("Degrees the digit turns from the axis toward the row's last side, in place of its place in the fan.")
    @Range(min = -90.0, max = 90.0)
    val angle: Float? = null,
) {
    fun lint(ctx: LintContext, field: String): List<Finding> =
        (if (index < 0) ctx.finding("$field.index", "a digit's index is 0 or more") else emptyList()) +
            (u?.let { ctx.unit("$field.u", it) } ?: emptyList()) +
            (across?.takeIf { it !in -MAX_ACROSS..MAX_ACROSS }?.let { ctx.finding("$field.across", "$it is outside -$MAX_ACROSS to $MAX_ACROSS") } ?: emptyList())
}
