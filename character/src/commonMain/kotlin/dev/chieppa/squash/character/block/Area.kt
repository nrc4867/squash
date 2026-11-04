package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.AreaSpec
import dev.chieppa.squash.character.model.AreaTag
import dev.chieppa.squash.character.model.ParamRef
import dev.chieppa.squash.character.model.Span
import dev.chieppa.squash.document.BODY_PATCH
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("area")
@Doc("A tagged region weights and shading can read, and that Fill colors whole.")
data class Area(
    override val name: String,
    @SerialName("if") override val condition: ParamRef? = null,
    val u: Span,
    @Doc("Around the surface. An end below its start runs on past 1 to it, crossing the seam at v = 0.")
    val v: Span,
    val tag: AreaTag,
    @Doc("The patch it lies on, or every one of a row of patches.")
    val patch: String = BODY_PATCH,
    @Doc("A palette slot it shows until it is filled.")
    val color: String? = null,
) : Block {
    override fun areas() = listOf(AreaSpec(name, u, v, tag, patch, color))

    override fun lint(ctx: LintContext): List<Finding> = ctx.unit("u", u) + around(ctx)

    private fun around(ctx: LintContext): List<Finding> = when {
        v.start !in 0f..1f || v.end !in 0f..1f -> ctx.finding("v", "$v is outside 0 to 1")
        v.start == v.end -> ctx.finding("v", "$v holds nothing")
        else -> emptyList()
    }
}
