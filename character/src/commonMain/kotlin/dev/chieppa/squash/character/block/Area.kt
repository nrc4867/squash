package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.AreaSpec
import dev.chieppa.squash.character.model.AreaTag
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.Span
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("area")
@Doc("A tagged region weights and shading can read.")
data class Area(
    override val name: String,
    @SerialName("if") override val condition: ParamRef? = null,
    val u: Span,
    val v: Span,
    val tag: AreaTag,
) : Block {
    override fun areas() = listOf(AreaSpec(name, u, v, tag))

    override fun lint(ctx: LintContext): List<Finding> = ctx.unit("u", u) + ctx.unit("v", v)
}
