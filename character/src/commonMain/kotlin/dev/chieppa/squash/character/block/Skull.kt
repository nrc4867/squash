package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.Param
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.Span
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("skull")
@Doc("A rounded block whose slices run front to back.")
data class Skull(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    val u: Span,
    val width: Param? = null,
    val depth: Param? = null,
) : Block {
    override fun lint(ctx: LintContext): List<Finding> =
        ctx.unit("u", u) + ctx.param("width", width) + ctx.param("depth", depth)
}
