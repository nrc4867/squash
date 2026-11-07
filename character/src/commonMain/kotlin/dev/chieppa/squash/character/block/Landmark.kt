package dev.chieppa.squash.character.block

import dev.chieppa.squash.annotations.Doc
import dev.chieppa.squash.annotations.Range
import dev.chieppa.squash.character.lint.Finding
import dev.chieppa.squash.character.lint.LintContext
import dev.chieppa.squash.character.model.LandmarkSpec
import dev.chieppa.squash.character.model.Mirror
import dev.chieppa.squash.character.model.ParamRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("landmark")
@Doc("A named surface point face marks and sets anchor to.")
data class Landmark(
    override val name: String,
    @SerialName("if") override val condition: ParamRef? = null,
    @Range(min = 0.0, max = 1.0)
    val u: Float,
    @Range(min = 0.0, max = 1.0)
    val v: Float,
    val pair: Mirror? = null,
) : Block {
    override fun landmarks() = listOf(LandmarkSpec(name, u, v))

    override fun lint(ctx: LintContext): List<Finding> = ctx.unit("u", u) + ctx.unit("v", v) + ctx.paired("v", v, pair)
}
