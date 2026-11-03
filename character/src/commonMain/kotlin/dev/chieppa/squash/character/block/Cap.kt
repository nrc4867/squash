package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.CapShape
import dev.chieppa.squash.character.model.End
import dev.chieppa.squash.character.model.ParamRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("cap")
@Doc("Closes an end that is not a socket.")
data class Cap(
    override val name: String? = null,
    @SerialName("if") override val condition: ParamRef? = null,
    val end: End,
    val shape: CapShape = CapShape.ROUND,
) : Block {
    override fun capShape(end: End): CapShape? = shape.takeIf { end == this.end }

    override fun lint(ctx: LintContext): List<Finding> =
        if (ctx.template.openEnd == end) ctx.finding("end", "caps the template's open end") else emptyList()
}
