package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.Span
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("toeRow")
@Doc("A row of small tubes fanned across the end, each with its own bone.")
data class ToeRow(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    val u: Span,
    val count: Param,
    @Doc("Degrees.")
    val spread: Param,
    val radius: Param,
    @Range(min = 0.0, max = 1.0)
    val v: Float? = null,
) : Block {
    override fun lint(ctx: LintContext): List<Finding> =
        ctx.unit("u", u) + ctx.param("count", count) + ctx.param("spread", spread) + ctx.param("radius", radius) +
            ctx.unit("v", v)
}
